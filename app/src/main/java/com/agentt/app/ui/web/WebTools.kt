package com.agentt.app.ui.web

import android.content.Context
import com.agentt.app.ui.chat.AgentPackageRecovery
import com.agentt.app.ui.chat.AgentToolRegistry
import com.agentt.app.ui.settings.SandboxEnvironmentStore
import com.agentt.app.ui.terminal.LocalTerminalBackend
import com.agentt.app.ui.terminal.TerminalAgentTool
import com.agentt.app.ui.terminal.TerminalBackendId
import com.agentt.app.ui.terminal.TerminalEventKind
import com.agentt.app.ui.terminal.TerminalToolCall
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup

object WebTools {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    @Volatile private var terminalTool: TerminalAgentTool? = null
    @Volatile private var sandboxEnvironment: SandboxEnvironmentStore? = null

    fun initialize(context: Context) {
        val applicationContext = context.applicationContext
        if (terminalTool == null) synchronized(this) {
            if (terminalTool == null) {
                terminalTool = TerminalAgentTool(LocalTerminalBackend(applicationContext))
                sandboxEnvironment = SandboxEnvironmentStore.from(applicationContext)
            }
        }
    }

    suspend fun runTool(tool: String, url: String?, query: String?): String = when (AgentToolRegistry.canonicalId(tool)) {
        "browser.extract" -> extract(url.orEmpty())
        "browser.title" -> title(url.orEmpty())
        "browser.links" -> links(url.orEmpty())
        "browser.search" -> search(query ?: url.orEmpty())
        "browser.open" -> "将在内置浏览器中打开：${url ?: "无地址"}"
        "terminal.exec" -> runTerminal(query.orEmpty())
        else -> "未知工具：$tool"
    }

    private suspend fun runTerminal(payload: String): String = withContext(Dispatchers.IO) {
        val tool = terminalTool ?: return@withContext "终端未初始化"
        val parsed = runCatching { JSONObject(payload) }.getOrNull()
        val command = parsed?.optString("command").orEmpty().ifBlank { payload }
        if (command.isBlank()) return@withContext "终端命令为空"
        val call = TerminalToolCall(
            command = command,
            timeoutMs = parsed?.optLong("timeout_ms", 30_000)?.coerceIn(1_000, 120_000) ?: 30_000,
            maxOutputChars = parsed?.optInt("max_output_chars", 256_000)?.coerceIn(1_024, 512_000) ?: 256_000,
            backend = runCatching { TerminalBackendId.valueOf(parsed?.optString("backend", "LOCAL")?.uppercase() ?: "LOCAL") }.getOrDefault(TerminalBackendId.LOCAL)
        )
        fun execute(value: TerminalToolCall): String {
            val output = StringBuilder()
            kotlinx.coroutines.runBlocking {
                tool.execute(value, confirmed = true).collect { event ->
                    when (event.kind) {
                        TerminalEventKind.OUTPUT -> output.append(if (event.stdout) event.text else "[stderr] ${event.text}")
                        TerminalEventKind.COMPLETED -> event.result?.let { output.append("\n[exit=${it.exitCode}${if (it.timedOut) ", timeout" else ""}${if (it.truncated) ", truncated" else ""}]") }
                        TerminalEventKind.FAILED -> output.append("\n[failed] ${event.text}")
                        else -> Unit
                    }
                }
            }
            return output.toString().trim().ifBlank { "命令执行完成，无输出" }
        }
        val first = execute(call)
        val missing = AgentPackageRecovery.missingCommand(first)
        if (missing == null) return@withContext redact(first)
        val managerOutput = execute(call.copy(command = "command -v apk || command -v pkg || command -v apt-get || true"))
        val manager = managerOutput.lineSequence().map(String::trim).firstOrNull { it.endsWith("/apk") || it.endsWith("/pkg") || it.endsWith("/apt-get") }?.substringAfterLast('/')
        val install = manager?.let { AgentPackageRecovery.installCommand(it, missing) }
        if (install == null) return@withContext redact(first) + "\n" + AgentToolRegistry.requirementMarker(AgentPackageRecovery.runtimeRequirement(missing))
        val installed = execute(call.copy(command = install))
        if (installed.contains("[exit=") && !installed.contains("[exit=0")) return@withContext redact(first + "\n[自动安装 $missing 失败]\n$installed") + "\n" + AgentToolRegistry.requirementMarker(AgentPackageRecovery.runtimeRequirement(missing))
        redact("[已自动安装 $missing 并重试]\n${execute(call)}")
    }

    private fun redact(value: String): String = sandboxEnvironment?.redactForModel(value) ?: value

    suspend fun extract(url: String): String = withContext(Dispatchers.IO) { try { val d = Jsoup.connect(url).userAgent(UA).timeout(15000).get(); val t = d.body().text().trim(); "${d.title()}\n\n${if (t.length > 6000) t.take(6000) + "\n…(已截断)" else t}" } catch (e: Exception) { "抓取失败：${e.message ?: e.javaClass.simpleName}" } }
    suspend fun title(url: String): String = withContext(Dispatchers.IO) { try { Jsoup.connect(url).userAgent(UA).timeout(15000).get().title().ifBlank { "（无标题）" } } catch (e: Exception) { "获取失败：${e.message ?: e.javaClass.simpleName}" } }
    suspend fun links(url: String): String = withContext(Dispatchers.IO) { try { val d = Jsoup.connect(url).userAgent(UA).timeout(15000).get(); val links = d.select("a[href]").mapNotNull { a -> val h = a.absUrl("href").ifBlank { null }; if (h == null || h.startsWith("javascript:") || h.startsWith("mailto:")) null else "${a.text().ifBlank { h }} → $h" }.distinct().take(20); if (links.isEmpty()) "未找到链接" else links.joinToString("\n") } catch (e: Exception) { "获取失败：${e.message ?: e.javaClass.simpleName}" } }
    suspend fun search(query: String): String = withContext(Dispatchers.IO) { try { val url = "https://www.bing.com/search?q=${URLEncoder.encode(query, "UTF-8")}"; val d = Jsoup.connect(url).userAgent(UA).timeout(15000).get(); val results = d.select("li.b_algo").take(6).map { i -> val a = i.selectFirst("h2 a"); val p = i.selectFirst(".b_caption p") ?: i.selectFirst("p"); "${a?.text()?.ifBlank { "（无标题）" }}\n${a?.absUrl("href")}\n${p?.text()?.ifBlank { "" }}" }; if (results.isEmpty()) "未搜索到结果" else results.joinToString("\n---\n") } catch (e: Exception) { "搜索失败：${e.message ?: e.javaClass.simpleName}" } }
}