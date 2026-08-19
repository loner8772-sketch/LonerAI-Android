package com.example.groqchat

import kotlinx.coroutines.delay
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Orchestrates: generate project -> push to GitHub -> wait for Actions run ->
 * on failure, fetch logs and ask Groq to fix -> push again -> repeat, up to
 * maxAttempts -> on success, download and unzip the APK artifact.
 *
 * This makes real API calls to Groq and GitHub on every attempt, and every
 * push consumes GitHub Actions minutes. Keep maxAttempts modest.
 */
class BuildPipeline(
    private val groqApiKey: String,
    private val githubOwner: String,
    private val githubRepo: String,
    private val githubToken: String,
    private val onStatus: (String) -> Unit
) {
    private val github = GitHubClient(githubOwner, githubRepo, githubToken)
    private val maxAttempts = 5
    private val pollIntervalMs = 15_000L
    private val maxWaitMs = 10 * 60_000L // 10 minutes per run

    suspend fun run(appDescription: String, outputDir: File): File? {
        onStatus("Asking Groq to generate the app…")
        var files = generateOrFail(appDescription) ?: return null

        for (attempt in 1..maxAttempts) {
            onStatus("Attempt $attempt/$maxAttempts: pushing files to GitHub…")
            try {
                github.pushFiles(files, "Generated app attempt $attempt")
            } catch (e: Exception) {
                onStatus("Push failed: ${e.message}")
                return null
            }

            // give GitHub a moment to register the push and start the workflow
            delay(5_000)
            val runId = findNewRunId() ?: run {
                onStatus("Couldn't find the triggered workflow run — check repo/workflow setup.")
                return null
            }

            onStatus("Build started (run $runId). Waiting for it to finish…")
            val conclusion = waitForRun(runId)

            when (conclusion) {
                "success" -> {
                    onStatus("Build succeeded! Downloading APK…")
                    return downloadApk(runId, outputDir)
                }
                null -> {
                    onStatus("Timed out waiting for the build.")
                    return null
                }
                else -> {
                    onStatus("Build failed ($conclusion). Fetching logs and asking Groq to fix it…")
                    val log = github.getRunLogText(runId)
                    val fixed = fixOrFail(files, log) ?: return null
                    files = fixed
                }
            }
        }

        onStatus("Gave up after $maxAttempts attempts. Check the repo's Actions tab for details.")
        return null
    }

    private suspend fun generateOrFail(description: String): Map<String, String>? {
        var result: Map<String, String>? = null
        var error: String? = null
        val latch = java.util.concurrent.CountDownLatch(1)
        CodeGenClient.generate(groqApiKey, description, object : CodeGenClient.Callback2 {
            override fun onSuccess(files: Map<String, String>) { result = files; latch.countDown() }
            override fun onError(message: String) { error = message; latch.countDown() }
        })
        latch.await()
        if (error != null) { onStatus("Generation failed: $error"); return null }
        return result
    }

    private suspend fun fixOrFail(previous: Map<String, String>, log: String): Map<String, String>? {
        var result: Map<String, String>? = null
        var error: String? = null
        val latch = java.util.concurrent.CountDownLatch(1)
        CodeGenClient.fix(groqApiKey, previous, log, object : CodeGenClient.Callback2 {
            override fun onSuccess(files: Map<String, String>) { result = files; latch.countDown() }
            override fun onError(message: String) { error = message; latch.countDown() }
        })
        latch.await()
        if (error != null) { onStatus("Fix attempt failed: $error"); return null }
        return result
    }

    private suspend fun findNewRunId(): Long? {
        // simple retry in case the run hasn't registered yet
        repeat(6) {
            val id = github.getLatestRunId()
            if (id != null) return id
            delay(5_000)
        }
        return null
    }

    private suspend fun waitForRun(runId: Long): String? {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < maxWaitMs) {
            val status = github.getRunStatus(runId)
            if (status.status == "completed") return status.conclusion
            delay(pollIntervalMs)
        }
        return null
    }

    private fun downloadApk(runId: Long, outputDir: File): File? {
        val artifacts = github.listArtifacts(runId)
        val artifact = artifacts.firstOrNull() ?: run {
            onStatus("Build succeeded but no artifact was found.")
            return null
        }
        val zipBytes = github.downloadArtifactZip(artifact.downloadUrl)
        val zipIn = ZipInputStream(zipBytes.inputStream())
        var entry = zipIn.nextEntry
        while (entry != null) {
            if (entry.name.endsWith(".apk")) {
                val outFile = File(outputDir, "app-generated.apk")
                outFile.outputStream().use { out -> zipIn.copyTo(out) }
                return outFile
            }
            entry = zipIn.nextEntry
        }
        onStatus("Downloaded artifact but no .apk file was inside it.")
        return null
    }
}
