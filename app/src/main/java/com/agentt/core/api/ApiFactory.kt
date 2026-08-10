package com.agentt.core.api

import com.agentt.data.model.ProviderType

/**
 * API 客户端工厂
 */
object ApiFactory {

    fun createClient(type: ProviderType): AiApiClient = when (type) {
        ProviderType.ANTHROPIC -> AnthropicClient()
        ProviderType.GOOGLE -> GeminiClient()
        // OpenAI、OpenRouter、自定义均使用 OpenAI 兼容格式
        else -> OpenAiCompatibleClient()
    }
}
