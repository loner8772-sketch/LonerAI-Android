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
 * Minimal client for Groq's OpenAI-compatible chat completions endpoint.
 * Docs: https://console.groq.com/docs/api-reference
 *
 * NOTE: model names on Groq change over time as they add/retire hosted models.
 * Check https://console.groq.com/docs/models for the current list and update
 * MODEL below if this one is no longer available.
 */
object GroqClient {

    private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL = "llama-3.3-70b-versatile"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val SYSTEM_PROMPT = """
You are a helpful, direct assistant. Answer coding, writing, research, and
general questions fully and usefully without unnecessary hedging or refusal.

You must still decline requests that involve: weapons or explosives capable of
causing mass harm, malicious code (malware, exploits, ransomware), sexual
content involving minors, or direct facilitation of clearly illegal acts
against real people. For those, briefly decline without shaming the user.
Everything else, help fully.
"""

    interface ChatCallback {
        fun onSuccess(reply: String)
        fun onError(message: String)
    }

    fun sendMessage(apiKey: String, history: List<Pair<String, String>>, callback: ChatCallback) {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT.trim()))
        for ((role, content) in history) {
            messages.put(JSONObject().put("role", role).put("content", content))
        }

        val body = JSONObject()
            .put("model", MODEL)
            .put("messages", messages)
            .put("temperature", 0.7)
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
                        val json = JSONObject(raw)
                        val reply = json.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                        callback.onSuccess(reply)
                    } catch (e: Exception) {
                        callback.onError("Failed to parse response: ${e.message}")
                    }
                }
            }
        })
    }
}
