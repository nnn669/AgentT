package com.agentt.data.model

import java.util.UUID

data class AIProvider(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: ProviderType,
    val apiKey: String = "",
    val baseUrl: String = "",
    val models: List<String> = emptyList(),
    val isEnabled: Boolean = true
)

enum class ProviderType(val displayName: String, val defaultBaseUrl: String) {
    OPEN_AI("OpenAI", "https://api.openai.com/v1"),
    ANTHROPIC("Anthropic", "https://api.anthropic.com/v1"),
    GOOGLE("Google Gemini", "https://generativelanguage.googleapis.com/v1beta"),
    OPEN_ROUTER("OpenRouter", "https://openrouter.ai/api/v1"),
    CUSTOM("自定义", "")
}

fun ProviderType.defaultModels(): List<String> = when (this) {
    ProviderType.OPEN_AI -> listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo")
    ProviderType.ANTHROPIC -> listOf("claude-3-opus-20240229", "claude-3-sonnet-20240229", "claude-3-haiku-20240307")
    ProviderType.GOOGLE -> listOf("gemini-pro", "gemini-1.5-pro", "gemini-1.5-flash")
    ProviderType.OPEN_ROUTER -> listOf("openrouter/auto")
    ProviderType.CUSTOM -> emptyList()
}
