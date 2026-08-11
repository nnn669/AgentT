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

/** Tries configured models in provider order with bounded transient retries. */
suspend fun recoverChatWithProviders(
    providers: List<ProviderConfig>,
    history: List<ChatMessage>,
    onAttempt: (RecoveryAttempt) -> Unit = {},
    request: suspend (ProviderConfig, List<ChatMessage>) -> ChatReply = ::chatWithProvider,
    wait: suspend (Long) -> Unit = { delay(it) }
): RecoveryReply? {
    val attempts = mutableListOf<RecoveryAttempt>()
    val candidates = providers.flatMap { provider ->
        provider.models.asSequence()
            .filter { it.isNotBlank() }
            .distinct()
            .map { model -> provider.copy(models = listOf(model)) }
            .toList()
    }
    for (candidate in candidates) {
        for (attempt in 1..ATTEMPTS_PER_MODEL) {
            val started = RecoveryAttempt(candidate.name, candidate.mainModel, attempt)
            onAttempt(started)
            val reply = request(candidate, history)
            if (reply.ok) {
                attempts += started
                return RecoveryReply(reply, candidate, candidate.mainModel, attempts)
            }
            val failed = started.copy(error = safeError(reply.error))
            attempts += failed
            onAttempt(failed)
            if (!isTransientModelFailure(reply.error) || attempt == ATTEMPTS_PER_MODEL) break
            wait(600L * attempt)
        }
    }
    return null
}

fun isTransientModelFailure(error: String?): Boolean {
    val value = error.orEmpty().lowercase()
    if (value.isBlank()) return false
    val status = Regex("http\\s+(\\d{3})", RegexOption.IGNORE_CASE)
        .find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
    if (status != null) return status in setOf(408, 409, 425, 429) || status >= 500
    return true
}

fun safeError(error: String?): String {
    val value = error.orEmpty()
        .replace(Regex("(?i)([?&](?:key|api_key)=)[^&\\s]+"), "$1***")
        .replace(Regex("(?i)(\\\"?(?:api[_ -]?key|authorization|x-api-key)\\\"?\\s*[:=]\\s*\\\"?)[^\\\"\\s,}]+"), "$1***")
        .replace(Regex("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]+"), "$1***")
    return when {
        value.contains("SocketException", ignoreCase = true) ||
            value.contains("connection abort", ignoreCase = true) ||
            value.contains("connection reset", ignoreCase = true) ||
            value.contains("unexpected end", ignoreCase = true) -> "网络连接中断"
        value.contains("timeout", ignoreCase = true) || value.contains("timed out", ignoreCase = true) -> "请求超时"
        value.contains("HTTP 429", ignoreCase = true) -> "请求过于频繁"
        value.contains("HTTP 401", ignoreCase = true) || value.contains("HTTP 403", ignoreCase = true) -> "凭据无效或无权限"
        value.contains("HTTP 404", ignoreCase = true) -> "模型或服务地址不存在"
        Regex("(?i)HTTP 5\\d\\d").containsMatchIn(value) -> "服务暂时不可用"
        else -> value.take(120).ifBlank { "未知调用错误" }
    }
}

fun recoveryFailureMessage(attempts: List<RecoveryAttempt>): String {
    val details = attempts.filter { it.error != null }
        .distinctBy { Triple(it.provider, it.model, it.error) }
        .take(4)
        .joinToString("\\n") { "• ${it.provider} / ${it.model}：${it.error}" }
    val header = "⚠️ AgentT 已自动重试并尝试备用模型/供应商，但暂时仍无法完成请求。"
    return if (details.isBlank()) "$header\\n请检查网络和供应商配置后重试。" else "$header\\n$details"
}
