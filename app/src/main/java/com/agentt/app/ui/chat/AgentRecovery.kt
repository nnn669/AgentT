package com.agentt.app.ui.chat

import com.agentt.app.ui.providers.ProviderConfig
import kotlinx.coroutines.delay

private const val ATTEMPTS_PER_MODEL = 2

data class RecoveryAttempt(
    val provider: String,
    val model: String,
    val attempt: Int,
    val error: String? = null
)

data class RecoveryReply(
    val reply: ChatReply,
    val provider: ProviderConfig,
    val model: String,
    val attempts: List<RecoveryAttempt>
)

/**
 * Tries every configured model in provider order. Transient faults are retried
 * once with a short backoff; permanent request/configuration errors move on.
 */
suspend fun recoverChatWithProviders(
    providers: List<ProviderConfig>,
    history: List<ChatMessage>,
    onAttempt: (RecoveryAttempt) -> Unit = {}
): RecoveryReply? {
    val attempts = mutableListOf<RecoveryAttempt>()
    val candidates = providers.flatMap { provider ->
        provider.models.filter { it.isNotBlank() }.map { model ->
            provider.copy(models = listOf(model))
        }
    }
    for (candidate in candidates) {
        for (attempt in 1..ATTEMPTS_PER_MODEL) {
            val started = RecoveryAttempt(candidate.name, candidate.mainModel, attempt)
            onAttempt(started)
            val reply = chatWithProvider(candidate, history)
            if (reply.ok) {
                attempts += started
                return RecoveryReply(reply, candidate, candidate.mainModel, attempts)
            }
            val failed = started.copy(error = safeError(reply.error))
            attempts += failed
            onAttempt(failed)
            if (!isTransientModelFailure(reply.error) || attempt == ATTEMPTS_PER_MODEL) break
            delay(600L * attempt)
        }
    }
    return null
}

fun isTransientModelFailure(error: String?): Boolean {
    val value = error.orEmpty().lowercase()
    if (value.contains("http 400") || value.contains("http 401") ||
        value.contains("http 403") || value.contains("http 404") ||
        value.contains("http 422")) return false
    return value.isNotBlank()
}

fun safeError(error: String?): String {
    val value = error.orEmpty()
        .replace(Regex("(?i)(api[_ -]?key|authorization|bearer)\\s*[:=]?\\s*[^\\s,}]+"), "$1=***")
    return when {
        value.contains("SocketException", ignoreCase = true) ||
            value.contains("connection abort", ignoreCase = true) ||
            value.contains("connection reset", ignoreCase = true) -> "网络连接中断"
        value.contains("timeout", ignoreCase = true) || value.contains("timed out", ignoreCase = true) -> "请求超时"
        value.contains("HTTP 429", ignoreCase = true) -> "请求过于频繁"
        value.contains("HTTP 401", ignoreCase = true) || value.contains("HTTP 403", ignoreCase = true) -> "凭据无效或无权限"
        value.contains("HTTP 404", ignoreCase = true) -> "模型或服务地址不存在"
        value.contains("HTTP 5") -> "服务暂时不可用"
        else -> value.take(120).ifBlank { "未知调用错误" }
    }
}