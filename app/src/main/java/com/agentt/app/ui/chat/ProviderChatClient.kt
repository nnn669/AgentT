package com.agentt.app.ui.chat

import com.agentt.app.ui.providers.ProviderConfig
import com.agentt.app.ui.providers.TestResult
import com.agentt.app.ui.providers.httpJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 供应商实际对话客户端。
 *
 * 这里集中处理端点拼接、各协议的 system 消息格式、工具结果角色兼容和响应解析，
 * 确保供应商页的“测试连接”与聊天页使用完全相同的请求链路。
 */
suspend fun chatWithProviderReliable(
    provider: ProviderConfig,
    history: List<ChatMessage>,
    systemPrompt: String
): ChatReply = withContext(Dispatchers.IO) {
    try {
        require(provider.mainModel.isNotBlank()) { "未选择模型" }
        val content = when (provider.protocol) {
            "anthropic" -> requestAnthropic(provider, history, systemPrompt)
            "gemini" -> requestGemini(provider, history, systemPrompt)
            "ollama" -> requestOllama(provider, history, systemPrompt)
            else -> requestOpenAiCompatible(provider, history, systemPrompt)
        }
        if (content.isBlank()) ChatReply(false, "", "供应商返回了空内容")
        else ChatReply(true, content, null)
    } catch (e: Exception) {
        ChatReply(false, "", e.message ?: e.javaClass.simpleName)
    }
}

/** 使用与真实聊天完全相同的请求和响应解析来测试供应商。 */
suspend fun testProviderConversation(provider: ProviderConfig): TestResult {
    val result = chatWithProviderReliable(
        provider = provider,
        history = listOf(ChatMessage("provider-test", "user", "仅回复 pong")),
        systemPrompt = "你正在执行连接测试，请仅回复 pong。"
    )
    return if (result.ok) {
        TestResult(true, "连接及对话测试成功")
    } else {
        TestResult(false, "对话测试失败：${result.error ?: "未知错误"}")
    }
}

/** 让助手绑定的模型优先于供应商默认模型。 */
fun prioritizeAssistantModel(
    providers: List<ProviderConfig>,
    assistant: AgentAssistant?
): List<ProviderConfig> {
    val targetProviderId = assistant?.providerId.orEmpty()
    val targetModel = assistant?.modelId.orEmpty().trim()
    if (targetProviderId.isBlank() || targetModel.isBlank()) return providers
    return providers.map { provider ->
        if (provider.id == targetProviderId) {
            provider.copy(models = listOf(targetModel) + provider.models.filterNot { it == targetModel })
        } else provider
    }
}

internal fun openAiChatEndpoint(baseUrl: String): String =
    appendUnlessPresent(baseUrl, "chat/completions")

internal fun anthropicMessagesEndpoint(baseUrl: String): String {
    val base = cleanBaseUrl(baseUrl)
    return when {
        base.endsWith("/v1/messages", ignoreCase = true) -> base
        base.endsWith("/messages", ignoreCase = true) -> base
        base.endsWith("/v1", ignoreCase = true) -> "$base/messages"
        else -> "$base/v1/messages"
    }
}

internal fun geminiGenerateEndpoint(baseUrl: String, model: String): String {
    val base = cleanBaseUrl(baseUrl)
    val modelId = model.substringAfterLast('/').trim()
    return when {
        base.contains(":generateContent", ignoreCase = true) -> base
        base.endsWith("/models", ignoreCase = true) -> "$base/$modelId:generateContent"
        base.endsWith("/v1beta", ignoreCase = true) || base.endsWith("/v1", ignoreCase = true) ->
            "$base/models/$modelId:generateContent"
        else -> "$base/v1beta/models/$modelId:generateContent"
    }
}

internal fun ollamaChatEndpoint(baseUrl: String): String =
    appendUnlessPresent(baseUrl, "api/chat")

private fun cleanBaseUrl(baseUrl: String): String {
    val cleaned = baseUrl.trim().trimEnd('/')
    require(cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
        "Base URL 必须以 http:// 或 https:// 开头"
    }
    return cleaned
}

private fun appendUnlessPresent(baseUrl: String, endpoint: String): String {
    val base = cleanBaseUrl(baseUrl)
    val suffix = "/${endpoint.trimStart('/')}"
    return if (base.endsWith(suffix, ignoreCase = true)) base else base + suffix
}

private fun requestOpenAiCompatible(
    provider: ProviderConfig,
    history: List<ChatMessage>,
    systemPrompt: String
): String {
    val headers = buildMap {
        if (provider.apiKey.isNotBlank()) put("Authorization", "Bearer ${provider.apiKey}")
        put("Accept", "application/json")
    }
    val baseBody = JSONObject()
        .put("model", provider.mainModel)
        .put("messages", openAiMessages(history, systemPrompt))

    var response = httpJson(
        "POST",
        openAiChatEndpoint(provider.baseUrl),
        headers,
        JSONObject(baseBody.toString()).put("max_tokens", 4096)
    )

    // 部分新 OpenAI 模型只接受 max_completion_tokens。
    if (response.code == 400 && response.body.contains("max_tokens", ignoreCase = true)) {
        response = httpJson(
            "POST",
            openAiChatEndpoint(provider.baseUrl),
            headers,
            JSONObject(baseBody.toString()).put("max_completion_tokens", 4096)
        )
    }
    ensureSuccess(response.code, response.body)
    val message = JSONObject(response.body)
        .optJSONArray("choices")
        ?.optJSONObject(0)
        ?.optJSONObject("message")
        ?: throw RuntimeException("响应中缺少 choices[0].message")
    return parseFlexibleText(message.opt("content"))
        .ifBlank { throw RuntimeException("响应中无内容") }
}

private fun requestAnthropic(
    provider: ProviderConfig,
    history: List<ChatMessage>,
    systemPrompt: String
): String {
    val body = JSONObject()
        .put("model", provider.mainModel)
        .put("max_tokens", 4096)
        .put("system", resolvedSystemPrompt(history, systemPrompt))
        .put("messages", anthropicMessages(history))
    val response = httpJson(
        "POST",
        anthropicMessagesEndpoint(provider.baseUrl),
        mapOf(
            "x-api-key" to provider.apiKey,
            "anthropic-version" to "2023-06-01",
            "Accept" to "application/json"
        ),
        body
    )
    ensureSuccess(response.code, response.body)
    val blocks = JSONObject(response.body).optJSONArray("content")
        ?: throw RuntimeException("响应中缺少 content")
    return buildString {
        for (i in 0 until blocks.length()) {
            val block = blocks.optJSONObject(i) ?: continue
            if (block.optString("type") == "text") append(block.optString("text"))
        }
    }.ifBlank { throw RuntimeException("响应中无内容") }
}

private fun requestGemini(
    provider: ProviderConfig,
    history: List<ChatMessage>,
    systemPrompt: String
): String {
    val body = JSONObject()
        .put(
            "systemInstruction",
            JSONObject().put(
                "parts",
                JSONArray().put(JSONObject().put("text", resolvedSystemPrompt(history, systemPrompt)))
            )
        )
        .put("contents", geminiContents(history))
        .put("generationConfig", JSONObject().put("maxOutputTokens", 4096))
    val response = httpJson(
        "POST",
        geminiGenerateEndpoint(provider.baseUrl, provider.mainModel),
        mapOf("x-goog-api-key" to provider.apiKey, "Accept" to "application/json"),
        body
    )
    ensureSuccess(response.code, response.body)
    val parts = JSONObject(response.body)
        .optJSONArray("candidates")
        ?.optJSONObject(0)
        ?.optJSONObject("content")
        ?.optJSONArray("parts")
        ?: throw RuntimeException("响应中缺少 candidates[0].content.parts")
    return buildString {
        for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text").orEmpty())
    }.ifBlank { throw RuntimeException("响应中无内容") }
}

private fun requestOllama(
    provider: ProviderConfig,
    history: List<ChatMessage>,
    systemPrompt: String
): String {
    val response = httpJson(
        "POST",
        ollamaChatEndpoint(provider.baseUrl),
        emptyMap(),
        JSONObject()
            .put("model", provider.mainModel)
            .put("stream", false)
            .put("messages", openAiMessages(history, systemPrompt))
    )
    ensureSuccess(response.code, response.body)
    return JSONObject(response.body).optJSONObject("message")?.optString("content")
        ?.takeIf { it.isNotBlank() }
        ?: throw RuntimeException("响应中无内容")
}

internal fun openAiMessages(history: List<ChatMessage>, systemPrompt: String): JSONArray =
    JSONArray().apply {
        put(JSONObject().put("role", "system").put("content", resolvedSystemPrompt(history, systemPrompt)))
        history.filterNot { it.role == "system" }.forEach { message ->
            val role = when (message.role) {
                "assistant" -> "assistant"
                // 没有 tool_call_id 的 tool 消息不能直接提交给 OpenAI 兼容接口。
                else -> "user"
            }
            val content = if (message.role == "tool") "[工具结果]\n${message.content}" else message.content
            put(JSONObject().put("role", role).put("content", content))
        }
    }

internal fun anthropicMessages(history: List<ChatMessage>): JSONArray =
    JSONArray().apply {
        history.filterNot { it.role == "system" }.forEach { message ->
            val role = if (message.role == "assistant") "assistant" else "user"
            val content = if (message.role == "tool") "[工具结果]\n${message.content}" else message.content
            put(JSONObject().put("role", role).put("content", content))
        }
    }

internal fun geminiContents(history: List<ChatMessage>): JSONArray =
    JSONArray().apply {
        history.filterNot { it.role == "system" }.forEach { message ->
            val role = if (message.role == "assistant") "model" else "user"
            val content = if (message.role == "tool") "[工具结果]\n${message.content}" else message.content
            put(
                JSONObject()
                    .put("role", role)
                    .put("parts", JSONArray().put(JSONObject().put("text", content)))
            )
        }
    }

private fun resolvedSystemPrompt(history: List<ChatMessage>, fallback: String): String =
    history.firstOrNull { it.role == "system" }?.content?.takeIf { it.isNotBlank() } ?: fallback

private fun parseFlexibleText(value: Any?): String = when (value) {
    is String -> value
    is JSONArray -> buildString {
        for (i in 0 until value.length()) {
            val item = value.opt(i)
            when (item) {
                is String -> append(item)
                is JSONObject -> append(item.optString("text"))
            }
        }
    }
    else -> ""
}

private fun ensureSuccess(code: Int, body: String) {
    if (code !in 200..299) {
        val detail = try {
            val error = JSONObject(body).opt("error")
            when (error) {
                is JSONObject -> error.optString("message").ifBlank { error.toString() }
                is String -> error
                else -> body
            }
        } catch (_: Exception) {
            body
        }.replace(Regex("[\\r\\n]+"), " ").take(300)
        throw RuntimeException("HTTP $code${if (detail.isBlank()) "" else "：$detail"}")
    }
}
