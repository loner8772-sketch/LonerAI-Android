package com.example.groqchat

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Asks Groq to produce (or fix) a full set of Android project files as
 * strict JSON: {"files": {"path/to/File.kt": "...content...", ...}}
 *
 * This is inherently a bit fragile — the model has to both write correct
 * Kotlin/Gradle and stick to valid JSON. Keep requested apps simple
 * (single-screen utilities, calculators, converters, small games) for
 * decent reliability; complex multi-screen apps will need more fix loops.
 */
object CodeGenClient {

    private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL = "llama-3.3-70b-versatile"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    interface Callback2 {
        fun onSuccess(files: Map<String, String>)
        fun onError(message: String)
    }

    private const val BASE_INSTRUCTIONS = """
You generate complete, buildable Android app projects (Kotlin + Jetpack
Compose, Gradle Kotlin DSL, compileSdk 34, minSdk 26, applicationId
com.example.generatedapp, no custom launcher icon — omit the icon attribute
or rely on defaults).

Unless the user's description clearly calls for a single screen, structure
the app with multiple real screens (e.g. a list screen + detail screen, or
a home screen + settings screen) using simple state-based navigation in
Compose (a `when` on a screen-state enum/int is fine — no need for the
Navigation library). Keep each screen's logic simple and avoid unnecessary
external dependencies.

Respond with STRICT JSON ONLY, no markdown fences, no commentary, in this
exact shape:
{"files": {"app/build.gradle.kts": "...", "app/src/main/AndroidManifest.xml": "...", "app/src/main/java/com/example/generatedapp/MainActivity.kt": "..."}}

Always include a root build.gradle.kts, settings.gradle.kts, gradle.properties,
app/build.gradle.kts, AndroidManifest.xml, and at least one Activity. Escape
all strings properly for valid JSON. Do not include explanations outside the
JSON object.

Refuse (respond with {"error": "reason"} instead of files) if asked to build
something whose primary purpose is malware, exploiting systems without
authorization, or sexual content involving minors.
"""

    fun generate(apiKey: String, appDescription: String, callback: Callback2) {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", BASE_INSTRUCTIONS.trim()))
            .put(JSONObject().put("role", "user").put("content", "Build this app: $appDescription"))
        call(apiKey, messages, callback)
    }

    /** Sends the previous files plus a build error and asks for corrected files. */
    fun fix(apiKey: String, previousFiles: Map<String, String>, errorLog: String, callback: Callback2) {
        val filesJson = JSONObject()
        val inner = JSONObject()
        previousFiles.forEach { (k, v) -> inner.put(k, v) }
        filesJson.put("files", inner)

        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", BASE_INSTRUCTIONS.trim()))
            .put(JSONObject().put("role", "user").put("content",
                "Here is the current project JSON:\n${filesJson}\n\n" +
                "The GitHub Actions build failed with this log (last portion):\n$errorLog\n\n" +
                "Return the corrected full project as the same JSON shape, fixing the error. " +
                "Only change what's needed to fix the build."
            ))
        call(apiKey, messages, callback)
    }

    private fun call(apiKey: String, messages: JSONArray, callback: Callback2) {
        val body = JSONObject()
            .put("model", MODEL)
            .put("messages", messages)
            .put("temperature", 0.2)
            .put("response_format", JSONObject().put("type", "json_object"))
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError("Network error: ${e.message}")
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        callback.onError("API error (${it.code}): $raw")
                        return
                    }
                    try {
                        val content = JSONObject(raw).getJSONArray("choices")
                            .getJSONObject(0).getJSONObject("message").getString("content")
                        val parsed = JSONObject(content)
                        if (parsed.has("error")) {
                            callback.onError(parsed.getString("error"))
                            return
                        }
                        val filesObj = parsed.getJSONObject("files")
                        val result = mutableMapOf<String, String>()
                        filesObj.keys().forEach { key -> result[key] = filesObj.getString(key) }
                        callback.onSuccess(result)
                    } catch (e: Exception) {
                        callback.onError("Failed to parse generated project: ${e.message}")
                    }
                }
            }
        })
    }
}
