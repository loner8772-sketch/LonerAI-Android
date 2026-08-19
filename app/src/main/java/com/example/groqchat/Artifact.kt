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

enum class ArtifactType { TEXT, CODE, JSON, MARKDOWN }

data class Artifact(
    val id: String,
    var title: String,
    val type: ArtifactType,
    var content: String,
    var language: String? = null // e.g. "kotlin", "python" — for display/extension only
)

/**
 * Generates a single non-app artifact (a document, script, JSON config,
 * list, etc.) as structured JSON via Groq. Separate from CodeGenClient,
 * which handles full multi-file Android app projects specifically.
 */
object ArtifactClient {

    private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL = "llama-3.3-70b-versatile"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    interface Callback2 {
        fun onSuccess(artifact: Artifact)
        fun onError(message: String)
    }

    private const val SYSTEM = """
You produce a single standalone piece of content the user asked for (a
document, script, code snippet, JSON/config file, list, plan, story, etc).

Respond with STRICT JSON ONLY, no markdown fences, no commentary, shaped as:
{"title": "short descriptive title", "type": "text|code|json|markdown", "language": "optional, e.g. python, kotlin, null if not code", "content": "the full content"}

Write the actual complete content the user asked for in "content" — not a
description of it. Escape it properly for valid JSON.

Refuse (respond with {"error": "reason"}) only for content whose primary
purpose is malware, exploiting systems without authorization, or sexual
content involving minors.
"""

    fun generate(apiKey: String, request: String, callback: Callback2) {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", SYSTEM.trim()))
            .put(JSONObject().put("role", "user").put("content", request))

        val body = JSONObject()
            .put("model", MODEL)
            .put("messages", messages)
            .put("temperature", 0.5)
            .put("response_format", JSONObject().put("type", "json_object"))
            .toString()
            .toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        client.newCall(req).enqueue(object : Callback {
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
                        val type = when (parsed.optString("type", "text")) {
                            "code" -> ArtifactType.CODE
                            "json" -> ArtifactType.JSON
                            "markdown" -> ArtifactType.MARKDOWN
                            else -> ArtifactType.TEXT
                        }
                        val artifact = Artifact(
                            id = System.currentTimeMillis().toString(),
                            title = parsed.optString("title", "Untitled"),
                            type = type,
                            content = parsed.getString("content"),
                            language = parsed.optString("language", null).takeIf { it != "null" && !it.isNullOrBlank() }
                        )
                        callback.onSuccess(artifact)
                    } catch (e: Exception) {
                        callback.onError("Failed to parse generated content: ${e.message}")
                    }
                }
            }
        })
    }
}
