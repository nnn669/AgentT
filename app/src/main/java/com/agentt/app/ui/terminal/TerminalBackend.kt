package com.agentt.app.ui.terminal

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
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

        var stdout = ""
        var stderr = ""
        val outThread = Thread { stdout = process.inputStream.bufferedReader().use { it.readText() } }
        val errThread = Thread { stderr = process.errorStream.bufferedReader().use { it.readText() } }
        outThread.isDaemon = true
        errThread.isDaemon = true
        outThread.start()
        errThread.start()

        val finished = process.waitFor(request.timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) process.destroyForcibly()
        outThread.join(1_000)
        errThread.join(1_000)
        running.remove(request.sessionId, process)

        val combinedLength = stdout.length + stderr.length
        val budget = request.maxOutputChars.coerceAtLeast(1)
        val safeStdout = stdout.take(budget)
        val safeStderr = stderr.take((budget - safeStdout.length).coerceAtLeast(0))
        TerminalResult(
            stdout = safeStdout,
            stderr = safeStderr,
            exitCode = if (finished) process.exitValue() else -1,
            timedOut = !finished,
            truncated = combinedLength > budget
        )
    }

    override fun cancel(sessionId: String) {
        running.remove(sessionId)?.destroyForcibly()
    }
}
