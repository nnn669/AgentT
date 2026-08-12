package com.agentt.app.ui.chat

import com.agentt.app.ui.providers.ProviderConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderChatClientTest {
    @Test
    fun geminiDefaultVersionIsNotDuplicated() {
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
            geminiGenerateEndpoint(
                "https://generativelanguage.googleapis.com/v1beta",
                "models/gemini-2.5-flash"
            )
        )
    }

    @Test
    fun anthropicAcceptsRootOrVersionedBaseUrl() {
        assertEquals(
            "https://api.anthropic.com/v1/messages",
            anthropicMessagesEndpoint("https://api.anthropic.com")
        )
        assertEquals(
            "https://api.anthropic.com/v1/messages",
            anthropicMessagesEndpoint("https://api.anthropic.com/v1/")
        )
    }

    @Test
    fun existingOpenAiEndpointIsNotAppendedTwice() {
        assertEquals(
            "https://example.com/v1/chat/completions",
            openAiChatEndpoint("https://example.com/v1/chat/completions/")
        )
    }

    @Test
    fun anthropicMessagesExcludeSystemAndNormalizeToolRole() {
        val messages = anthropicMessages(
            listOf(
                ChatMessage("1", "system", "system"),
                ChatMessage("2", "user", "hello"),
                ChatMessage("3", "tool", "result")
            )
        )
        assertEquals(2, messages.length())
        assertFalse(messages.toString().contains("\"role\":\"system\""))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        assertTrue(messages.getJSONObject(1).getString("content").contains("工具结果"))
    }

    @Test
    fun assistantModelBecomesFirstModel() {
        val provider = ProviderConfig("p", "P", "openai", "https://example.com/v1", "k", listOf("a", "b"))
        val assistant = AgentAssistant("a1", "A", providerId = "p", modelId = "b")
        val routed = prioritizeAssistantModel(listOf(provider), assistant)
        assertEquals(listOf("b", "a"), routed.single().models)
    }
}
