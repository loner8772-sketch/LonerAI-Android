package com.example.groqchat

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Talks to GitHub's REST API to push generated project files, watch the
 * Actions build triggered by that push, and pull logs / the final APK.
 *
 * Needs a GitHub Personal Access Token with `repo` + `workflow` scope
 * (classic token) stored via Settings. This token can write to your repo —
 * treat it like a password, and prefer a token scoped to a single repo if
 * you can.
 */
class GitHubClient(private val owner: String, private val repo: String, private val token: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun authedRequest(url: String) = Request.Builder()
        .url(url)
        .addHeader("Authorization", "Bearer $token")
        .addHeader("Accept", "application/vnd.github+json")

    /** Creates or updates a single file in the repo. */
    fun putFile(path: String, content: String, message: String) {
        val getUrl = "https://api.github.com/repos/$owner/$repo/contents/$path"
        var sha: String? = null
        client.newCall(authedRequest(getUrl).get().build()).execute().use { resp ->
            if (resp.isSuccessful) {
                sha = JSONObject(resp.body?.string().orEmpty()).optString("sha", null)
            }
        }

        val body = JSONObject().apply {
            put("message", message)
            put("content", Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP))
            put("branch", "main")
            if (sha != null) put("sha", sha)
        }.toString().toRequestBody("application/json".toMediaType())

        client.newCall(authedRequest(getUrl).put(body).build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                if (resp.code == 401) {
                    throw RuntimeException(
                        "GitHub rejected the token (401 Unauthorized) — it's likely expired or revoked. " +
                        "Generate a new one at github.com/settings/tokens and update it in Settings."
                    )
                }
                throw RuntimeException("Failed to push $path: ${resp.code} ${resp.body?.string()}")
            }
        }
    }

    fun pushFiles(files: Map<String, String>, commitMessage: String) {
        for ((path, content) in files) {
            putFile(path, content, commitMessage)
        }
    }

    /** Returns the latest workflow run id for the main branch, or null. */
    fun getLatestRunId(): Long? {
        val url = "https://api.github.com/repos/$owner/$repo/actions/runs?branch=main&per_page=1"
        client.newCall(authedRequest(url).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val runs = JSONObject(resp.body?.string().orEmpty()).optJSONArray("workflow_runs") ?: return null
            if (runs.length() == 0) return null
            return runs.getJSONObject(0).getLong("id")
        }
    }

    data class RunStatus(val status: String, val conclusion: String?)

    fun getRunStatus(runId: Long): RunStatus {
        val url = "https://api.github.com/repos/$owner/$repo/actions/runs/$runId"
        client.newCall(authedRequest(url).get().build()).execute().use { resp ->
            val json = JSONObject(resp.body?.string().orEmpty())
            return RunStatus(json.getString("status"), json.optString("conclusion", null))
        }
    }

    /** Pulls the plain-text logs for the first job of a run (good enough to find the error). */
    fun getRunLogText(runId: Long): String {
        val jobsUrl = "https://api.github.com/repos/$owner/$repo/actions/runs/$runId/jobs"
        val jobId: Long
        client.newCall(authedRequest(jobsUrl).get().build()).execute().use { resp ->
            val jobs = JSONObject(resp.body?.string().orEmpty()).getJSONArray("jobs")
            if (jobs.length() == 0) return "(no job logs available)"
            jobId = jobs.getJSONObject(0).getLong("id")
        }
        val logUrl = "https://api.github.com/repos/$owner/$repo/actions/jobs/$jobId/logs"
        client.newCall(authedRequest(logUrl).get().build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            // Trim to the last ~4000 chars — that's usually where the actual error is.
            return if (text.length > 4000) text.takeLast(4000) else text
        }
    }

    data class Artifact(val id: Long, val name: String, val downloadUrl: String)

    fun listArtifacts(runId: Long): List<Artifact> {
        val url = "https://api.github.com/repos/$owner/$repo/actions/runs/$runId/artifacts"
        client.newCall(authedRequest(url).get().build()).execute().use { resp ->
            val arr: JSONArray = JSONObject(resp.body?.string().orEmpty()).getJSONArray("artifacts")
            return (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Artifact(o.getLong("id"), o.getString("name"), o.getString("archive_download_url"))
            }
        }
    }

    /** Downloads an artifact zip's raw bytes. */
    fun downloadArtifactZip(downloadUrl: String): ByteArray {
        client.newCall(authedRequest(downloadUrl).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Artifact download failed: ${resp.code}")
            return resp.body?.bytes() ?: ByteArray(0)
        }
    }
}
