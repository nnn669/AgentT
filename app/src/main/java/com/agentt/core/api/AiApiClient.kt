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
 * AI API 客户端接口
 */
interface AiApiClient {
    suspend fun chatCompletion(
        apiKey: String,
        baseUrl: String,
        model: String,
        messages: List<Message>
    ): String
}

/**
 * OpenAI 兼容格式客户端（支持 OpenAI、OpenRouter、自定义、本地模型等）
 */
class OpenAiCompatibleClient : AiApiClient {

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
        val url = baseUrl.trimEnd('/') + "/chat/completions"

        val body = JSONObject().apply {
            put("model", model)
            put("stream", false)
            put("messages", JSONArray().apply {
                messages.forEach { msg ->
                    put(
                        JSONObject().apply {
                            put("role", when (msg.role) {
                                MessageRole.USER -> "user"
                                MessageRole.ASSISTANT -> "assistant"
                                MessageRole.SYSTEM -> "system"
                            })
                            put("content", msg.content)
                        }
                    )
                }
            })
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string()?.take(300) ?: ""
                throw Exception("HTTP ${response.code}: $errBody")
            }
            val json = JSONObject(response.body?.string() ?: "{}")
            json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }
}
