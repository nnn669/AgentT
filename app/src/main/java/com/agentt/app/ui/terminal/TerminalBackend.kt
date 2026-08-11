package com.agentt.app.ui.terminal

import android.content.Context
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class TerminalBackendId { LOCAL, SHIZUKU_ADB }

enum class CommandRisk { READ_ONLY, WRITE, PRIVILEGED }

data class TerminalRequest(
    val sessionId: String,
    val command: String,
    val timeoutMs: Long = 30_000,
    val maxOutputChars: Int = 256_000
)

data class TerminalResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val timedOut: Boolean,
    val truncated: Boolean
)

interface TerminalBackend {
    val id: TerminalBackendId
    val displayName: String
    val available: Boolean
    suspend fun execute(request: TerminalRequest): TerminalResult
    fun cancel(sessionId: String)
}

object TerminalPolicy {
    private val privileged = listOf(
        "pm clear", "pm uninstall", "pm disable", "settings put", "settings delete",
        "appops set", "reboot", "svc ", "cmd package"
    )
    private val writes = listOf(
        "rm ", "mv ", "cp ", "mkdir ", "touch ", "chmod ", "sed -i", ">", "tee "
    )

    fun classify(command: String): CommandRisk {
        val normalized = command.trim().lowercase()
        return when {
            privileged.any(normalized::contains) -> CommandRisk.PRIVILEGED
            writes.any(normalized::contains) -> CommandRisk.WRITE
            else -> CommandRisk.READ_ONLY
        }
    }
}

class LocalTerminalBackend(context: Context) : TerminalBackend {
    override val id = TerminalBackendId.LOCAL
    override val displayName = "本地终端"
    override val available = true

    private val workspace = File(context.filesDir, "terminal").apply { mkdirs() }.canonicalFile
    private val running = ConcurrentHashMap<String, Process>()

    override suspend fun execute(request: TerminalRequest): TerminalResult = withContext(Dispatchers.IO) {
        require(request.command.isNotBlank()) { "命令不能为空" }
        val process = ProcessBuilder("/system/bin/sh", "-c", request.command)
            .directory(workspace)
            .apply {
                environment()["HOME"] = workspace.path
                environment()["TMPDIR"] = File(workspace, "tmp").apply { mkdirs() }.path
                environment()["PATH"] = "/system/bin:/system/xbin:/vendor/bin"
                redirectErrorStream(false)
            }
            .start()
        running.put(request.sessionId, process)?.destroyForcibly()

        val budget = request.maxOutputChars.coerceAtLeast(1)
        val truncated = AtomicBoolean(false)
        var stdout = ""
        var stderr = ""
        val outThread = Thread {
            stdout = process.inputStream.readLimited(budget, truncated)
        }
        val errThread = Thread {
            stderr = process.errorStream.readLimited(budget, truncated)
        }
        outThread.isDaemon = true
        errThread.isDaemon = true
        outThread.start()
        errThread.start()

        val finished = process.waitFor(request.timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) process.destroyForcibly()
        outThread.join(1_000)
        errThread.join(1_000)
        running.remove(request.sessionId, process)

        val safeStdout = stdout.take(budget)
        val safeStderr = stderr.take((budget - safeStdout.length).coerceAtLeast(0))
        TerminalResult(
            stdout = safeStdout,
            stderr = safeStderr,
            exitCode = if (finished) process.exitValue() else -1,
            timedOut = !finished,
            truncated = truncated.get() || stdout.length + stderr.length > budget
        )
    }

    override fun cancel(sessionId: String) {
        running.remove(sessionId)?.destroyForcibly()
    }
}

private fun InputStream.readLimited(limit: Int, truncated: AtomicBoolean): String {
    val result = StringBuilder(minOf(limit, 8_192))
    bufferedReader().use { reader ->
        val buffer = CharArray(4_096)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            val remaining = limit - result.length
            if (remaining > 0) result.append(buffer, 0, minOf(count, remaining))
            if (count > remaining) truncated.set(true)
        }
    }
    return result.toString()
}