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
 * Google Gemini API 客户端
 */
class GeminiClient : AiApiClient {

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
        val url = "${baseUrl.trimEnd('/')}/models/$model:generateContent?key=$apiKey"

        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                messages.forEach { msg ->
                    put(
                        JSONObject().apply {
                            put("role", if (msg.role == MessageRole.USER) "user" else "model")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", msg.content) })
                            })
                        }
                    )
                }
            })
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string()?.take(300) ?: ""
                throw Exception("HTTP ${response.code}: $errBody")
            }
            val json = JSONObject(response.body?.string() ?: "{}")
            val candidates = json.getJSONArray("candidates")
            if (candidates.length() == 0) {
                throw Exception("API 未返回内容")
            }
            candidates.getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        }
    }
}
