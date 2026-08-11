package com.agentt.app.ui.chat

import com.agentt.app.ui.providers.ProviderConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRecoveryTest {
    private fun provider(name: String, models: List<String>) = ProviderConfig(
        id = name, name = name, protocol = "openai",
        baseUrl = "https://example.com/v1", apiKey = "secret", models = models
    )

    @Test
    fun retriesTransientThenFallsBackToNextModel() = runBlocking {
        val calls = mutableListOf<String>()
        val result = recoverChatWithProviders(
            providers = listOf(provider("主供应商", listOf("broken", "backup"))),
            history = emptyList(),
            request = { p, _ ->
                calls += p.mainModel
                if (p.mainModel == "backup") ChatReply(true, "ok", null)
                else ChatReply(false, "", "HTTP 503 unavailable")
            },
            wait = {}
        )
        assertNotNull(result)
        assertEquals("backup", result!!.model)
        assertEquals(listOf("broken", "broken", "backup"), calls)
    }

    @Test
    fun permanentCredentialFailureDoesNotWasteRetry() = runBlocking {
        val calls = mutableListOf<String>()
        val result = recoverChatWithProviders(
            providers = listOf(provider("A", listOf("first")), provider("B", listOf("second"))),
            history = emptyList(),
            request = { p, _ ->
                calls += p.mainModel
                if (p.mainModel == "second") ChatReply(true, "ok", null)
                else ChatReply(false, "", "HTTP 401 key=do-not-leak")
            },
            wait = {}
        )
        assertEquals(listOf("first", "second"), calls)
        assertEquals("B", result!!.provider.name)
    }

    @Test
    fun classifiesRetryableHttpStatuses() {
        assertTrue(isTransientModelFailure("HTTP 429"))
        assertTrue(isTransientModelFailure("HTTP 503"))
        assertFalse(isTransientModelFailure("HTTP 400"))
        assertFalse(isTransientModelFailure("HTTP 401"))
    }

    @Test
    fun redactsCredentialsAndBuildsReadableFailure() {
        val safe = safeError("HTTP 401 https://x.test?key=abc123 Authorization: Bearer token456")
        assertFalse(safe.contains("abc123"))
        assertFalse(safe.contains("token456"))
        val message = recoveryFailureMessage(listOf(RecoveryAttempt("P", "M", 1, "请求超时")))
        assertTrue(message.contains("已自动重试"))
        assertTrue(message.contains("P / M"))
    }
}
