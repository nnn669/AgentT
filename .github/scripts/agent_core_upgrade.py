from pathlib import Path
import re

root = Path('.')

def write(path, content):
    p = root / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content.strip() + '\n', encoding='utf-8')

def replace(path, old, new, count=1):
    p = root / path
    text = p.read_text(encoding='utf-8')
    if old not in text:
        raise RuntimeError(f'missing patch anchor in {path}: {old[:100]!r}')
    p.write_text(text.replace(old, new, count), encoding='utf-8')

write('app/src/main/java/com/agentt/app/ui/chat/AgentToolRegistry.kt', r'''
package com.agentt.app.ui.chat

import org.json.JSONObject

data class AgentToolSpec(val id: String, val description: String, val arguments: String, val executor: String)

data class AgentRequirement(val kind: String, val key: String = "", val title: String, val reason: String) {
    fun encode(): String = JSONObject().put("kind", kind).put("key", key).put("title", title).put("reason", reason).toString()
    companion object {
        fun decode(value: String): AgentRequirement? = runCatching {
            val o = JSONObject(value)
            AgentRequirement(o.getString("kind"), o.optString("key"), o.optString("title").ifBlank { "需要你的操作" }, o.optString("reason").ifBlank { "完成配置后，AgentT 会继续当前任务。" })
        }.getOrNull()
    }
}

object AgentToolRegistry {
    private const val REQUIREMENT_MARKER = "[[AGENT_REQUIREMENT]]"
    val tools = listOf(
        AgentToolSpec("browser.search", "搜索互联网并返回结果", "query:string", "search"),
        AgentToolSpec("browser.extract", "读取网页正文", "url:string", "extract"),
        AgentToolSpec("browser.title", "读取网页标题", "url:string", "title"),
        AgentToolSpec("browser.links", "提取网页链接", "url:string", "links"),
        AgentToolSpec("browser.open", "在应用内浏览器打开网页", "url:string", "open"),
        AgentToolSpec("terminal.exec", "在 AgentT 私有沙盒执行命令；缺少白名单软件包时运行时会尝试自动安装并重试", "command:string, backend?:LOCAL, timeout_ms?:1000..120000, max_output_chars?:1024..512000", "terminal")
    )
    fun systemPrompt(): String = buildString {
        appendLine("你是 AgentT 自主智能体，运行在安卓手机上。用户只需要描述目标，不需要也不应该指定工具。")
        appendLine("你必须自行理解目标、规划步骤、从当前工具目录选择能力、执行、检查结果，并持续推进到任务完成。")
        appendLine("当前应用实际内置工具（这是唯一可信工具目录）：")
        tools.forEach { appendLine("- ${it.id}(${it.arguments})：${it.description}") }
        appendLine("输出必须是 JSON 动作流，不能夹带其它文字：")
        appendLine("{\"actions\":[{\"type\":\"think\",\"content\":\"简短的下一步\"},{\"type\":\"tool\",\"tool\":\"browser.search\",\"query\":\"...\"},{\"type\":\"reply\",\"content\":\"最终结果\"}]}")
        appendLine("规则：自主判断工具；工具结果自动回传；未完成时继续；仅在必须由用户提供供应商、密钥、权限或输入时输出 require；requirement 只能是 provider、secret、permission、runtime、input。")
        appendLine("require 示例：{\"actions\":[{\"type\":\"require\",\"requirement\":\"secret\",\"key\":\"GITHUB_TOKEN\",\"title\":\"需要 GitHub Token\",\"reason\":\"用于访问私有仓库\"}]}")
        appendLine("reply 必须基于真实结果，使用与用户相同的语言。")
    }.trim()
    fun executorName(id: String): String = tools.firstOrNull { it.id == canonicalId(id) }?.executor ?: id.substringAfterLast('.').lowercase()
    fun canonicalId(id: String): String = when (id.lowercase()) {
        "search", "extract", "title", "links", "open" -> "browser.${id.lowercase()}"
        "terminal" -> "terminal.exec"
        else -> id.lowercase()
    }
    fun actionLabel(id: String): String = when (canonicalId(id)) {
        "browser.search" -> "搜索网页"
        "browser.extract" -> "读取网页正文"
        "browser.title" -> "获取网页标题"
        "browser.links" -> "提取网页链接"
        "browser.open" -> "打开网页"
        "terminal.exec" -> "执行终端任务"
        else -> "执行工具"
    }
    fun requirementMarker(requirement: AgentRequirement): String = REQUIREMENT_MARKER + requirement.encode()
    fun requirementFromToolResult(result: String): AgentRequirement? = result.substringAfter(REQUIREMENT_MARKER, "").lineSequence().firstOrNull().orEmpty().takeIf { it.isNotBlank() }?.let(AgentRequirement::decode)
}

object AgentPackageRecovery {
    private val packages = mapOf("git" to "git", "curl" to "curl", "wget" to "wget", "jq" to "jq", "python" to "python3", "python3" to "python3", "node" to "nodejs", "npm" to "npm", "ffmpeg" to "ffmpeg", "rg" to "ripgrep")
    fun missingCommand(output: String): String? {
        val patterns = listOf(Regex("(?im)(?:^|[\\s:])([a-zA-Z0-9._+-]+): (?:not found|inaccessible)"), Regex("(?im)command not found:?\\s*([a-zA-Z0-9._+-]+)"))
        return patterns.asSequence().mapNotNull { it.find(output)?.groupValues?.getOrNull(1) }.firstOrNull { it in packages }
    }
    fun installCommand(manager: String, command: String): String? {
        val pkg = packages[command] ?: return null
        return when (manager.substringAfterLast('/')) { "apk" -> "apk add --no-cache $pkg"; "pkg" -> "pkg install -y $pkg"; "apt-get" -> "apt-get update && apt-get install -y $pkg"; else -> null }
    }
    fun runtimeRequirement(command: String) = AgentRequirement("runtime", command, "需要可安装工具的终端环境", "任务需要命令 $command，但当前 Android 沙盒没有可用包管理器。请打开终端环境完成初始化，返回后 AgentT 会继续任务。")
}
''')

p = root / 'app/src/main/java/com/agentt/app/ui/chat/ChatModels.kt'
text = p.read_text(encoding='utf-8')
text = text.replace('val maxOutputChars: Int = 256_000\n)', 'val maxOutputChars: Int = 256_000,\n    val requirement: String = "",\n    val key: String = "",\n    val title: String = "",\n    val reason: String = ""\n)', 1)
text = text.replace('''                // Keep ChatScreen's existing tool branch as the single execution path.
                // The original action type remains visible in the model protocol tests.
                val isTerminal = rawType.equals("terminal", ignoreCase = true)
                add(AgentAction(
                    type = if (isTerminal) "browser" else rawType,
                    tool = if (isTerminal) "terminal" else o.optString("tool"),''', '''                val isTerminal = rawType.equals("terminal", ignoreCase = true)
                val isBrowser = rawType.equals("browser", ignoreCase = true)
                val emittedTool = when { isTerminal -> "terminal.exec"; isBrowser -> "browser.${o.optString("tool")}"; else -> o.optString("tool") }
                add(AgentAction(
                    type = if (isTerminal || isBrowser) "tool" else rawType.lowercase(),
                    tool = AgentToolRegistry.canonicalId(emittedTool),''', 1)
text = text.replace('val maxOutputChars = o.optInt("max_output_chars", 256_000).coerceIn(1_024, 512_000)\n                ))', 'val maxOutputChars = o.optInt("max_output_chars", 256_000).coerceIn(1_024, 512_000),\n                    requirement = o.optString("requirement"), key = o.optString("key"), title = o.optString("title"), reason = o.optString("reason")\n                ))', 1)
p.write_text(text, encoding='utf-8')

p = root / 'app/src/main/java/com/agentt/app/ui/web/WebTools.kt'
text = p.read_text(encoding='utf-8')
text = re.sub(r'package com\.agentt\.app\.ui\.web[\s\S]*', r'''package com.agentt.app.ui.web

import android.content.Context
import com.agentt.app.ui.chat.AgentPackageRecovery
import com.agentt.app.ui.chat.AgentToolRegistry
import com.agentt.app.ui.settings.SandboxEnvironmentStore
import com.agentt.app.ui.terminal.LocalTerminalBackend
import com.agentt.app.ui.terminal.TerminalAgentTool
import com.agentt.app.ui.terminal.TerminalBackendId
import com.agentt.app.ui.terminal.TerminalEventKind
import com.agentt.app.ui.terminal.TerminalToolCall
import java.io.File
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup

object WebTools {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
    @Volatile private var terminalTool: TerminalAgentTool? = null
    @Volatile private var sandboxEnvironment: SandboxEnvironmentStore? = null
    fun initialize(context: Context) { val c = context.applicationContext; if (terminalTool == null) synchronized(this) { if (terminalTool == null) { terminalTool = TerminalAgentTool(LocalTerminalBackend(c)); sandboxEnvironment = SandboxEnvironmentStore.from(c) } } }
    suspend fun runTool(tool: String, url: String?, query: String?): String = when (AgentToolRegistry.executorName(tool)) { "extract" -> extract(url ?: ""); "title" -> title(url ?: ""); "links" -> links(url ?: ""); "search" -> search(query ?: url ?: ""); "open" -> "将在内置浏览器中打开：${url ?: "无地址"}"; "terminal" -> runTerminal(query ?: ""); else -> "未知工具：$tool" }
    private data class TerminalRun(val text: String, val exitCode: Int?)
    private suspend fun executeTerminal(call: TerminalToolCall): TerminalRun { val tool = terminalTool ?: return TerminalRun("终端未初始化", -1); val out = StringBuilder(); var code: Int? = null; tool.execute(call, confirmed = true).collect { e -> when (e.kind) { TerminalEventKind.OUTPUT -> out.append(if (e.stdout) e.text else "[stderr] ${e.text}"); TerminalEventKind.COMPLETED -> e.result?.let { code = it.exitCode; out.append("\\n[exit=${it.exitCode}${if (it.timedOut) ", timeout" else ""}${if (it.truncated) ", truncated" else ""}]") }; TerminalEventKind.FAILED -> { code = -1; out.append("\\n[failed] ${e.text}") }; else -> Unit } }; return TerminalRun(out.toString().trim().ifBlank { "命令执行完成，无输出" }, code) }
    private suspend fun runTerminal(payload: String): String = withContext(Dispatchers.IO) {
        val parsed = runCatching { JSONObject(payload) }.getOrNull(); val command = parsed?.optString("command").orEmpty().ifBlank { payload }; if (command.isBlank()) return@withContext "终端命令为空"
        fun call(value: String) = TerminalToolCall(command = value, timeoutMs = parsed?.optLong("timeout_ms", 30_000)?.coerceIn(1_000, 120_000) ?: 30_000, maxOutputChars = parsed?.optInt("max_output_chars", 256_000)?.coerceIn(1_024, 512_000) ?: 256_000, backend = runCatching { TerminalBackendId.valueOf(parsed?.optString("backend", "LOCAL")?.uppercase() ?: "LOCAL") }.getOrDefault(TerminalBackendId.LOCAL))
        val first = executeTerminal(call(command)); val missing = AgentPackageRecovery.missingCommand(first.text) ?: return@withContext redact(first.text)
        val probe = executeTerminal(call("command -v apk || command -v pkg || command -v apt-get || true")); val manager = probe.text.lineSequence().map(String::trim).firstOrNull { it.endsWith("/apk") || it.endsWith("/pkg") || it.endsWith("/apt-get") }?.let { File(it).name }; val install = manager?.let { AgentPackageRecovery.installCommand(it, missing) }
        if (install == null) return@withContext redact(first.text) + "\\n" + AgentToolRegistry.requirementMarker(AgentPackageRecovery.runtimeRequirement(missing))
        val installed = executeTerminal(call(install)); if (installed.exitCode != 0) return@withContext redact(first.text + "\\n[自动安装 $missing 失败]\\n" + installed.text) + "\\n" + AgentToolRegistry.requirementMarker(AgentPackageRecovery.runtimeRequirement(missing))
        redact("[已自动安装 $missing 并重试]\\n${executeTerminal(call(command)).text}")
    }
    private fun redact(value: String): String = sandboxEnvironment?.redactForModel(value) ?: value
    suspend fun extract(url: String): String = withContext(Dispatchers.IO) { try { val d = Jsoup.connect(url).userAgent(UA).timeout(15000).get(); val t = d.body().text().trim(); "${d.title()}\\n\\n${if (t.length > 6000) t.take(6000) + "\\n…(已截断)" else t}" } catch (e: Exception) { "抓取失败：${e.message ?: e.javaClass.simpleName}" } }
    suspend fun title(url: String): String = withContext(Dispatchers.IO) { try { Jsoup.connect(url).userAgent(UA).timeout(15000).get().title().ifBlank { "（无标题）" } } catch (e: Exception) { "获取失败：${e.message ?: e.javaClass.simpleName}" } }
    suspend fun links(url: String): String = withContext(Dispatchers.IO) { try { val d = Jsoup.connect(url).userAgent(UA).timeout(15000).get(); val links = d.select("a[href]").mapNotNull { a -> val h = a.absUrl("href").ifBlank { null }; if (h == null || h.startsWith("javascript:") || h.startsWith("mailto:")) null else "${a.text().ifBlank { h }} → $h" }.distinct().take(20); if (links.isEmpty()) "未找到链接" else links.joinToString("\\n") } catch (e: Exception) { "获取失败：${e.message ?: e.javaClass.simpleName}" } }
    suspend fun search(query: String): String = withContext(Dispatchers.IO) { try { val url = "https://www.bing.com/search?q=${URLEncoder.encode(query, "UTF-8")}"; val d = Jsoup.connect(url).userAgent(UA).timeout(15000).get(); val results = d.select("li.b_algo").take(6).map { i -> val a = i.selectFirst("h2 a"); val p = i.selectFirst(".b_caption p") ?: i.selectFirst("p"); "${a?.text()?.ifBlank { "（无标题）" }}\\n${a?.absUrl("href")}\\n${p?.text()?.ifBlank { "" }}" }; if (results.isEmpty()) "未搜索到结果" else results.joinToString("\\n---\\n") } catch (e: Exception) { "搜索失败：${e.message ?: e.javaClass.simpleName}" } }
}
''', text, count=1)
p.write_text(text, encoding='utf-8')

p = root / 'app/src/main/java/com/agentt/app/ui/chat/ChatScreen.kt'
text = p.read_text(encoding='utf-8')
text = text.replace('import androidx.compose.material3.DropdownMenu\n', 'import androidx.compose.material3.AlertDialog\nimport androidx.compose.material3.Button\nimport androidx.compose.material3.DropdownMenu\nimport androidx.compose.material3.TextButton\n', 1)
text = text.replace('const val MAX_AGENT_STEPS = 5', 'const val MAX_AGENT_STEPS = 12', 1)
text = re.sub(r'private const val SYSTEM_PROMPT = """[\s\S]*?"""', 'private val SYSTEM_PROMPT: String get() = AgentToolRegistry.systemPrompt()', text, count=1)
text = re.sub(r'private fun buildAgentHistory\(history: List<ChatMessage>, toolResults: List<String>\): List<ChatMessage> \{[\s\S]*?\n\}', '''private fun buildAgentHistory(history: List<ChatMessage>): List<ChatMessage> {
    val out = mutableListOf<ChatMessage>()
    out.add(ChatMessage(UUID.randomUUID().toString(), "user", "[系统指令]\\n$SYSTEM_PROMPT", null, "text"))
    history.forEach { message ->
        when (message.kind) {
            "tool" -> out.add(message.copy(role = "user", content = "[历史工具结果]\\n${message.content}"))
            "guide" -> out.add(message.copy(role = "user", content = "[任务暂停等待配置]\\n${message.content}"))
            else -> out.add(message)
        }
    }
    return out
}''', text, count=1)
text = text.replace('''    onOpenTerminal: () -> Unit,
    onOpenBrowser: (String?) -> Unit,
    modifier: Modifier = Modifier''', '''    onOpenTerminal: () -> Unit,
    onOpenBrowser: (String?) -> Unit,
    onOpenProvidersForAgent: () -> Unit = {},
    onOpenSecretsForAgent: () -> Unit = {},
    resumeToken: Int = 0,
    modifier: Modifier = Modifier''', 1)
text = text.replace('''    var streamTick by remember { mutableStateOf(0) }
    val listState''', '''    var streamTick by remember { mutableStateOf(0) }
    var pendingRequirement by remember { mutableStateOf<AgentRequirement?>(null) }
    val listState''', 1)
text = text.replace('''        if (sessionId != null) messages.addAll(chatStore.loadMessages(sessionId))
        loading = false''', '''        if (sessionId != null) messages.addAll(chatStore.loadMessages(sessionId))
        pendingRequirement = messages.lastOrNull { it.kind == "guide" }?.content?.let(AgentRequirement::decode)
        loading = false''', 1)
text = text.replace('''    suspend fun runAgent(providers: List<ProviderConfig>) {
        var candidates = providers
        val toolResults = mutableListOf<String>()''', '''    fun requireUser(requirement: AgentRequirement) {
        pendingRequirement = requirement
        messages.add(ChatMessage(UUID.randomUUID().toString(), "assistant", requirement.encode(), null, "guide"))
        persist()
    }

    suspend fun runAgent(providers: List<ProviderConfig>) {
        var candidates = providers''', 1)
text = text.replace('history = buildAgentHistory(messages.toList(), toolResults),', 'history = buildAgentHistory(messages.toList()),', 1)
text = text.replace('''            if (recovery == null) {
                addReply(recoveryFailureMessage(failedAttempts), providers.firstOrNull()?.mainModel)
                return
            }''', '''            if (recovery == null) {
                if (failedAttempts.any { it.error?.contains("凭据无效", ignoreCase = true) == true }) requireUser(AgentRequirement("provider", title = "模型密钥需要更新", reason = "当前供应商凭据无效或没有权限。更新并测试连接后，AgentT 会自动继续原任务。"))
                else addReply(recoveryFailureMessage(failedAttempts), providers.firstOrNull()?.mainModel)
                return
            }''', 1)
old = '''                    "browser" -> {
                        loadingLabel = toolActionLabel(a.tool)
                        val summary = WebTools.runTool(a.tool, a.url, a.query)
                        messages.add(ChatMessage(UUID.randomUUID().toString(), "assistant", "${a.tool}(${a.url ?: a.query})\\n${summary.take(1500)}", recovery.model, "tool"))
                        toolResults.add("[工具 ${a.tool}(${a.url ?: a.query}) 结果]\\n${summary.take(3000)}")
                        if (a.tool == "open" && a.url != null) onOpenBrowser(a.url)
                        persist()
                    }'''
new = '''                    "browser", "tool" -> {
                        val toolId = AgentToolRegistry.canonicalId(a.tool)
                        loadingLabel = AgentToolRegistry.actionLabel(toolId)
                        val summary = WebTools.runTool(toolId, a.url, a.query)
                        messages.add(ChatMessage(UUID.randomUUID().toString(), "assistant", "$toolId(${a.url ?: a.query ?: ""})\\n${summary.take(3000)}", recovery.model, "tool"))
                        persist()
                        AgentToolRegistry.requirementFromToolResult(summary)?.let { requireUser(it); return }
                        if (toolId == "browser.open" && a.url != null) onOpenBrowser(a.url)
                    }
                    "require" -> {
                        requireUser(AgentRequirement(a.requirement.ifBlank { "input" }, a.key, a.title.ifBlank { "需要你的操作" }, a.reason.ifBlank { "完成后 AgentT 会继续当前任务。" }))
                        return
                    }'''
if old not in text: raise RuntimeError('tool branch patch failed')
text = text.replace(old, new, 1)
text = text.replace('''            if (providers.isEmpty()) {
                addReply("尚未配置供应商：请到 设置 → 供应商 添加并测试连接后，再开始对话。", null)
                loading = false
                return@launch
            }''', '''            if (providers.isEmpty()) {
                requireUser(AgentRequirement("provider", title = "先连接一个模型", reason = "AgentT 需要模型来规划和执行任务。添加供应商并测试连接后会自动继续。"))
                loading = false
                return@launch
            }''', 1)
resume = '''    LaunchedEffect(resumeToken, sessionId) {
        if (resumeToken <= 0 || loading || pendingRequirement == null) return@LaunchedEffect
        val providers = providerStore.load()
        pendingRequirement = null
        messages.removeAll { it.kind == "guide" }
        messages.add(ChatMessage(UUID.randomUUID().toString(), "assistant", "配置已完成，正在恢复原任务", null, "think"))
        persist()
        loading = true
        loadingLabel = "恢复任务"
        if (providers.isEmpty()) requireUser(AgentRequirement("provider", title = "先连接一个模型", reason = "添加供应商后会自动继续。")) else runAgent(providers)
        loading = false
    }

'''
anchor = '    Scaffold(\n        modifier = modifier.fillMaxSize(),'
if anchor not in text: raise RuntimeError('Scaffold anchor missing')
text = text.replace(anchor, resume + anchor, 1)
text = text.replace('''                        m.kind == "think" -> ThinkCard(m.content)
                        m.kind == "tool" -> ToolCard(m.content)''', '''                        m.kind == "think" -> ThinkCard(m.content)
                        m.kind == "guide" -> RequirementCard(AgentRequirement.decode(m.content))
                        m.kind == "tool" -> ToolCard(m.content)''', 1)
insert = '''    pendingRequirement?.let { requirement ->
        AlertDialog(onDismissRequest = {}, title = { Text(requirement.title) }, text = { Text(requirement.reason) }, confirmButton = { Button(onClick = { when (requirement.kind) { "provider" -> onOpenProvidersForAgent(); "secret" -> onOpenSecretsForAgent(); "runtime" -> onOpenTerminal(); else -> pendingRequirement = null } }) { Text(when (requirement.kind) { "provider" -> "去配置供应商"; "secret" -> "去添加密钥"; "runtime" -> "打开终端环境"; else -> "我已处理" }) } }, dismissButton = { TextButton(onClick = { pendingRequirement = null }) { Text("稍后") } })
    }
'''
marker = '\n}\n\n@Composable\nprivate fun UserBubble'
if marker not in text: raise RuntimeError('ChatScreen end marker missing')
text = text.replace(marker, '\n' + insert + '}\n\n@Composable\nprivate fun RequirementCard(requirement: AgentRequirement?) {\n    val value = requirement ?: return\n    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)) {\n        Column(Modifier.fillMaxWidth().padding(12.dp)) {\n            Text(value.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)\n            Spacer(Modifier.height(4.dp))\n            Text(value.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)\n        }\n    }\n}\n\n@Composable\nprivate fun UserBubble', 1)
chat.write_text(text, encoding='utf-8')

p = root / 'app/src/main/java/com/agentt/app/MainActivity.kt'
text = p.read_text(encoding='utf-8')
text = text.replace('var browserUrl by rememberSaveable { mutableStateOf("") }', 'var browserUrl by rememberSaveable { mutableStateOf("") }\n                    var agentReturnToChat by rememberSaveable { mutableStateOf(false) }\n                    var agentResumeToken by rememberSaveable { mutableStateOf(0) }', 1)
text = text.replace('''                        Screen.Chat -> ChatScreen(
                            chatTitle,
                            chatSessionId,
                            { screen = Screen.Workspace },
                            {
                                val session = createChatSession(ChatStore.from(this@MainActivity))
                                chatSessionId = session.id
                                chatTitle = session.title
                            },
                            { screen = Screen.Terminal },
                            { url ->
                                browserUrl = url ?: ""
                                screen = Screen.Browser
                            }
                        )
                        Screen.Providers -> ProvidersScreen(onBack = { screen = Screen.Workspace })
                        Screen.SandboxEnvironment -> SandboxEnvironmentScreen(onBack = { screen = Screen.Workspace })
                        Screen.Browser -> BrowserScreen(initialUrl = browserUrl, onBack = { screen = Screen.Chat })
                        Screen.Terminal -> TerminalScreen(onBack = { screen = Screen.Chat })''', '''                        Screen.Chat -> ChatScreen(title = chatTitle, sessionId = chatSessionId, onBack = { screen = Screen.Workspace }, onNewChat = { val session = createChatSession(ChatStore.from(this@MainActivity)); chatSessionId = session.id; chatTitle = session.title }, onOpenTerminal = { agentReturnToChat = false; screen = Screen.Terminal }, onOpenBrowser = { url -> browserUrl = url ?: ""; screen = Screen.Browser }, onOpenProvidersForAgent = { agentReturnToChat = true; screen = Screen.Providers }, onOpenSecretsForAgent = { agentReturnToChat = true; screen = Screen.SandboxEnvironment }, resumeToken = agentResumeToken)
                        Screen.Providers -> ProvidersScreen(onBack = { if (agentReturnToChat) { agentReturnToChat = false; agentResumeToken++; screen = Screen.Chat } else screen = Screen.Workspace })
                        Screen.SandboxEnvironment -> SandboxEnvironmentScreen(onBack = { if (agentReturnToChat) { agentReturnToChat = false; agentResumeToken++; screen = Screen.Chat } else screen = Screen.Workspace })
                        Screen.Browser -> BrowserScreen(initialUrl = browserUrl, onBack = { screen = Screen.Chat })
                        Screen.Terminal -> TerminalScreen(onBack = { if (agentReturnToChat) { agentReturnToChat = false; agentResumeToken++ }; screen = Screen.Chat })''', 1)
main.write_text(text, encoding='utf-8')

write('app/src/test/java/com/agentt/app/ui/chat/AgentToolRegistryTest.kt', r'''
package com.agentt.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolRegistryTest {
    @Test fun catalogIsInjectedAndModelChoosesToolsAutonomously() {
        val prompt = AgentToolRegistry.systemPrompt()
        assertTrue(prompt.contains("browser.search")); assertTrue(prompt.contains("terminal.exec")); assertTrue(prompt.contains("用户只需要描述目标")); assertTrue(prompt.contains("禁止让用户改写"))
        assertEquals(AgentToolRegistry.tools.map { it.id }.distinct().size, AgentToolRegistry.tools.size)
    }
    @Test fun requirementRoundTripsThroughToolResult() {
        val original = AgentRequirement("secret", "GITHUB_TOKEN", "需要 Token", "访问私有仓库")
        assertEquals(original, AgentToolRegistry.requirementFromToolResult("failed\\n" + AgentToolRegistry.requirementMarker(original)))
    }
    @Test fun knownMissingPackageGetsSafeInstallPlan() {
        assertEquals("git", AgentPackageRecovery.missingCommand("/system/bin/sh: git: not found")); assertEquals("apk add --no-cache git", AgentPackageRecovery.installCommand("apk", "git")); assertEquals("pkg install -y python3", AgentPackageRecovery.installCommand("/data/pkg", "python")); assertEquals(null, AgentPackageRecovery.installCommand("apk", "unknown;rm"))
    }
    @Test fun markerDoesNotMatchOrdinaryOutput() { assertFalse(AgentToolRegistry.requirementFromToolResult("normal command output") != null) }
}
''')

p = root / 'app/src/test/java/com/agentt/app/ui/chat/ChatActionStreamTest.kt'
text = p.read_text(encoding='utf-8').replace('assertEquals("browser", actions[1].type)\n        assertEquals("extract", actions[1].tool)', 'assertEquals("tool", actions[1].type)\n        assertEquals("browser.extract", actions[1].tool)').replace('assertEquals("browser", action.type)\n        assertEquals("terminal", action.tool)', 'assertEquals("tool", action.type)\n        assertEquals("terminal.exec", action.tool)').replace('assertEquals("search", action.tool); assertEquals("天气", action.query)', 'assertEquals("browser.search", action.tool); assertEquals("天气", action.query)')
p.write_text(text, encoding='utf-8')
print('Autonomous agent core patch applied')
