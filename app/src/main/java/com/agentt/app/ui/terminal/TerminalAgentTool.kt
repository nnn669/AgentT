package com.agentt.app.ui.terminal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import java.util.UUID

/** Structured tool boundary shared by the chat agent and terminal UI. */
data class TerminalToolCall(
    val id: String = UUID.randomUUID().toString(),
    val command: String,
    val timeoutMs: Long = 30_000,
    val maxOutputChars: Int = 256_000,
    val backend: TerminalBackendId = TerminalBackendId.LOCAL
)

enum class TerminalEventKind { STARTED, OUTPUT, CONFIRMATION_REQUIRED, COMPLETED, FAILED, CANCELLED }

data class TerminalEvent(
    val kind: TerminalEventKind,
    val callId: String,
    val text: String = "",
    val stdout: Boolean = true,
    val result: TerminalResult? = null,
    val risk: CommandRisk? = null
)

enum class ConfirmationDecision { ALLOW_ONCE, DENY }

class TerminalConfirmationGate {
    private val pending = LinkedHashMap<String, TerminalToolCall>()

    fun request(call: TerminalToolCall): TerminalEvent? {
        val risk = TerminalPolicy.classify(call.command)
        if (risk == CommandRisk.READ_ONLY) return null
        pending[call.id] = call
        return TerminalEvent(TerminalEventKind.CONFIRMATION_REQUIRED, call.id, call.command, risk = risk)
    }

    fun decide(callId: String, decision: ConfirmationDecision): TerminalToolCall? {
        val call = pending.remove(callId) ?: return null
        return call.takeIf { decision == ConfirmationDecision.ALLOW_ONCE }
    }

    fun isPending(callId: String): Boolean = pending.containsKey(callId)
}

class TerminalAgentTool(
    private val local: TerminalBackend,
    private val confirmation: TerminalConfirmationGate = TerminalConfirmationGate(),
    private val shizuku: TerminalBackend? = null
) {
    fun execute(call: TerminalToolCall, confirmed: Boolean = false): Flow<TerminalEvent> = flow {
        val risk = TerminalPolicy.classify(call.command)
        if (risk != CommandRisk.READ_ONLY && !confirmed) {
            emit(TerminalEvent(TerminalEventKind.CONFIRMATION_REQUIRED, call.id, call.command, risk = risk))
            return@flow
        }
        val backend = when (call.backend) {
            TerminalBackendId.LOCAL -> local
            TerminalBackendId.SHIZUKU_ADB -> shizuku
        }
        if (backend == null || !backend.available) {
            emit(TerminalEvent(TerminalEventKind.FAILED, call.id, "终端后端不可用", risk = risk))
            return@flow
        }
        emit(TerminalEvent(TerminalEventKind.STARTED, call.id, call.command, risk = risk))
        try {
            val result = backend.execute(TerminalRequest(call.id, call.command, call.timeoutMs, call.maxOutputChars))
            if (result.stdout.isNotBlank()) emit(TerminalEvent(TerminalEventKind.OUTPUT, call.id, result.stdout, true, risk = risk))
            if (result.stderr.isNotBlank()) emit(TerminalEvent(TerminalEventKind.OUTPUT, call.id, result.stderr, false, risk = risk))
            emit(TerminalEvent(TerminalEventKind.COMPLETED, call.id, result = result, risk = risk))
        } catch (e: Exception) {
            emit(TerminalEvent(TerminalEventKind.FAILED, call.id, e.message ?: "执行失败", risk = risk))
        }
    }

    fun cancel(callId: String, backend: TerminalBackendId = TerminalBackendId.LOCAL) {
        when (backend) {
            TerminalBackendId.LOCAL -> local.cancel(callId)
            TerminalBackendId.SHIZUKU_ADB -> shizuku?.cancel(callId)
        }
    }

    companion object {
        fun parse(json: String): TerminalToolCall? = runCatching {
            val o = JSONObject(json)
            require(o.optString("type") == "terminal")
            TerminalToolCall(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                command = o.getString("command"),
                timeoutMs = o.optLong("timeout_ms", 30_000).coerceIn(1_000, 120_000),
                maxOutputChars = o.optInt("max_output_chars", 256_000).coerceIn(1_024, 512_000),
                backend = runCatching { TerminalBackendId.valueOf(o.optString("backend", "LOCAL")) }.getOrDefault(TerminalBackendId.LOCAL)
            )
        }.getOrNull()
    }
}