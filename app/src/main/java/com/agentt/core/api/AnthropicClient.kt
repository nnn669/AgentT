package com.agentt.core.api

import com.agentt.data.model.Message
import com.agentt.data.model.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Anthropic Claude API 客户端
 */
class AnthropicClient : AiApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    override suspend fun chatCompletion(
        apiKey: String,
        baseUrl: String,
        model: String,
        messages: List<Message>
    ): String = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/messages"

        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", 4096)
            put("messages", JSONArray().apply {
                messages.filter { it.role != MessageRole.SYSTEM }.forEach { msg ->
                    put(
                        JSONObject().apply {
                            put("role", if (msg.role == MessageRole.USER) "user" else "assistant")
                            put("content", msg.content)
                        }
                    )
                }
            })
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string()?.take(300) ?: ""
                throw Exception("HTTP ${response.code}: $errBody")
            }
            val json = JSONObject(response.body?.string() ?: "{}")
            json.getJSONArray("content")
                .getJSONObject(0)
                .getString("text")
        }
    }
}
