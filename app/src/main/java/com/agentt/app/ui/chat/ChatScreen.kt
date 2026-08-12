package com.agentt.app.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agentt.app.ui.assistant.TagStore
import com.agentt.app.ui.files.FileTools
import com.agentt.app.ui.markdown.MarkdownText
import com.agentt.app.ui.memory.MemoryStore
import com.agentt.app.ui.providers.ProviderConfig
import com.agentt.app.ui.providers.ProviderStore
import com.agentt.app.ui.providers.httpJson
import com.agentt.app.ui.web.WebTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

const val MAX_AGENT_STEPS = 5

private const val SYSTEM_PROMPT = """你是 AgentT 智能体，运行在安卓手机上（Android 本机环境，无 root 权限，无默认 Git/Node.js/Python 等运行时）。你通过"动作流"来完成任务。
你可以调用内置工具获取实时信息：
- browser.search(query)：搜索互联网
- browser.extract(url)：抓取网页正文
- browser.title(url)：获取网页标题
- browser.links(url)：提取网页链接
- browser.open(url)：在浏览器中打开网页
- file.list(path)：列出目录内容
- file.read(path)：读取文本文件
- file.write(path, content)：写入文本文件（覆盖写入）
- file.append(path, content)：追加内容到文件
- file.copy(source, target)：复制文件（path=源, target=目标）
- file.move(source, target)：移动/重命名文件（path=源, target=目标）
- file.stat(path)：查看文件信息
- file.delete(path)：删除文件或目录
- terminal.execute(command)：在本地 Shell 中执行命令（无 root 权限，仅限可访问的目录）

你的输出必须是 JSON 动作流，不要输出任何其它文字，格式如下：
{"actions":[{"type":"think","content":"你的推理"},{"type":"tool","tool":"browser.extract","url":"https://example.com"},{"type":"reply","content":"最终回答"}]}

规则：
1. type 只能是 think（推理）、tool（调用工具，带 tool 字段指定工具名）、reply（最终回复）。
2. 文件工具：file.read/write/append 带 path 和 content；file.copy/move 带 path（源）和 target（目标）；file.list/stat/delete 带 path。
3. 终端工具：terminal.execute 带 command（要执行的命令）或 payload 字段。
4. 工具结果会自动回传给你，你再继续推理。
5. 如果一次动作流里没有 reply，你会收到工具结果后继续，直到给出 reply。
6. 不需要工具时，直接输出 {"actions":[{"type":"reply","content":"回答"}]}。
7. reply 的 content 是最终展示给用户的内容，使用与用户相同的语言。"""

data class ChatReply(val ok: Boolean, val content: String, val error: String?)

suspend fun chatWithProvider(p: ProviderConfig, history: List<ChatMessage>): ChatReply =
    withContext(Dispatchers.IO) {
        try {
            val content = when (p.protocol) {
                "anthropic" -> chatAnthropic(p, history)
                "gemini" -> chatGemini(p, history)
                "ollama" -> chatOllama(p, history)
                else -> chatOpenAi(p, history)
            }
            ChatReply(true, content, null)
        } catch (e: Exception) {
            ChatReply(false, "", e.message ?: e.javaClass.simpleName)
        }
    }

private fun chatMessages(history: List<ChatMessage>): JSONArray =
    JSONArray().apply {
        history.forEach { put(JSONObject().put("role", it.role).put("content", it.content)) }
    }

private fun chatOpenAi(p: ProviderConfig, history: List<ChatMessage>): String {
    val r = httpJson(
        "POST", p.baseUrl.trimEnd('/') + "/chat/completions",
        mapOf("Authorization" to "Bearer ${p.apiKey}"),
        JSONObject().put("model", p.mainModel).put("max_tokens", 4096).put("messages", chatMessages(history))
    )
    if (r.code !in 200..299) throw RuntimeException("HTTP ${r.code}：${r.body.take(160)}")
    return JSONObject(r.body).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
        ?: throw RuntimeException("响应中无内容")
}

private fun chatAnthropic(p: ProviderConfig, history: List<ChatMessage>): String {
    val r = httpJson(
        "POST", p.baseUrl.trimEnd('/') + "/v1/messages",
        mapOf("x-api-key" to p.apiKey, "anthropic-version" to "2023-06-01"),
        JSONObject().put("model", p.mainModel).put("max_tokens", 4096).put("messages", chatMessages(history))
    )
    if (r.code !in 200..299) throw RuntimeException("HTTP ${r.code}：${r.body.take(160)}")
    val content = JSONObject(r.body).optJSONArray("content")?.optJSONObject(0)?.optString("text")
        ?: throw RuntimeException("响应中无内容")
    return content
}

private fun chatGemini(p: ProviderConfig, history: List<ChatMessage>): String {
    val parts = JSONArray()
    val last = history.lastOrNull() ?: return ""
    parts.put(JSONObject().put("text", last.content))
    val r = httpJson(
        "POST", p.baseUrl.trimEnd('/') + "/v1beta/models/${p.mainModel}:generateContent",
        mapOf("x-goog-api-key" to p.apiKey),
        JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put("generationConfig", JSONObject().put("maxOutputTokens", 4096))
    )
    if (r.code !in 200..299) throw RuntimeException("HTTP ${r.code}：${r.body.take(160)}")
    return JSONObject(r.body).optJSONArray("candidates")?.optJSONObject(0)
        ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
        ?: throw RuntimeException("响应中无内容")
}

private fun chatOllama(p: ProviderConfig, history: List<ChatMessage>): String {
    val r = httpJson(
        "POST", p.baseUrl.trimEnd('/') + "/api/chat",
        emptyMap(),
        JSONObject().put("model", p.mainModel).put("stream", false).put("messages", chatMessages(history))
    )
    if (r.code !in 200..299) throw RuntimeException("HTTP ${r.code}：${r.body.take(160)}")
    return JSONObject(r.body).optJSONObject("message")?.optString("content")
        ?: throw RuntimeException("响应中无内容")
}

data class RecoveryAttempt(val provider: String, val model: String, val attempt: Int, val error: String? = null)
data class RecoveryResult(val provider: ProviderConfig, val model: String, val reply: ChatMessage)

private suspend fun recoverChatWithProviders(
    providers: List<ProviderConfig>,
    history: List<ChatMessage>,
    systemPrompt: String = SYSTEM_PROMPT,
    onAttempt: (RecoveryAttempt) -> Unit = {}
): RecoveryResult? {
    for (provider in providers) {
        if (provider.models.isEmpty()) continue
        val preferred = provider.models.firstOrNull() ?: provider.mainModel
        var lastError: String? = null
        for (attempt in 1..3) {
            val attemptInfo = RecoveryAttempt(
                provider = provider.name,
                model = if (provider.models.size > 1) "$preferred (${provider.models.size}个模型)" else preferred,
                attempt = attempt
            )
            val modelToUse = if (attempt == 1) preferred else provider.models.getOrNull(attempt - 1) ?: preferred
            val p = provider.copy(models = listOf(modelToUse) + provider.models.filter { it != modelToUse })
            onAttempt(attemptInfo.copy(error = null))
            val result = chatWithProviderReliable(p, history, systemPrompt)
            if (result.ok) {
                val msg = ChatMessage(
                    UUID.randomUUID().toString(), "assistant", result.content,
                    modelToUse, "reply"
                )
                return RecoveryResult(p, modelToUse, msg)
            }
            lastError = result.error
            val isConfigError = result.error?.let { e ->
                e.contains("401") || e.contains("403") ||
                    e.contains("API key") || e.contains("apikey") || e.contains("auth") ||
                    e.contains("not found") || e.contains("Not Found") || e.contains("404")
            } ?: false
            if (isConfigError) {
                onAttempt(attemptInfo.copy(error = "配置错误：${result.error?.take(180) ?: "未知错误"}"))
                break
            }
            onAttempt(attemptInfo.copy(error = result.error?.take(180) ?: "请求失败"))
            if (attempt < 3) delay(1000L * attempt)
        }
    }
    return null
}

fun recoveryFailureMessage(attempts: List<RecoveryAttempt>): String {
    val providers = attempts.map { it.provider }.distinct()
    val errors = attempts.mapNotNull { it.error }.distinct()
    val parts = mutableListOf<String>()
    if (providers.isNotEmpty()) parts.add("已尝试供应商：${providers.joinToString("、")}")
    if (errors.isNotEmpty()) parts.add("错误摘要：${errors.joinToString("；")}")
    if (parts.isEmpty()) parts.add("所有供应商均无法响应")
    return "模型请求失败，请检查供应商配置和网络连接。\n${parts.joinToString("\n")}"
}

object AgentToolRegistry {
    private val tools = mutableListOf<AgentToolDef>()
    fun register(t: AgentToolDef) { tools.add(t) }
    fun all() = tools.toList()
    fun canonicalId(tool: String): String = when {
        tool.startsWith("browser.") -> "browser.${tool.removePrefix("browser.")}"
        tool.startsWith("file.") -> "file.${tool.removePrefix("file.")}"
        tool.startsWith("terminal.") -> "terminal.${tool.removePrefix("terminal.")}"
        else -> tool
    }
    fun actionLabel(tool: String): String = when {
        tool.startsWith("browser.") -> "浏览网页"
        tool.startsWith("file.") -> "操作文件"
        tool.startsWith("terminal.") -> "执行命令"
        else -> "执行工具"
    }
}

data class AgentToolDef(
    val id: String,
    val label: String,
    val run: suspend (Map<String, String>) -> String
)

object TerminalAgentTool {
    private var backend: com.agentt.app.ui.terminal.TerminalBackend? = null
    private var initialized = false
    private val outputBuffer = mutableListOf<String>()

    fun init(backend: com.agentt.app.ui.terminal.TerminalBackend) {
        this.backend = backend
        initialized = true
    }

    suspend fun executeCommand(command: String): String = withContext(Dispatchers.IO) {
        if (!initialized) return@withContext "终端未初始化"
        val b = backend ?: return@withContext "终端后端不可用"
        outputBuffer.clear()
        try {
            val request = com.agentt.app.ui.terminal.TerminalRequest(
                sessionId = UUID.randomUUID().toString(),
                command = command,
                timeoutMs = 30_000,
                maxOutputChars = 256_000
            )
            val result = b.execute(request)
            val output = (result.stdout + "\n" + result.stderr).trim().take(3000)
            if (output.isBlank()) "命令已执行，无输出" else output
        } catch (e: Exception) {
            "命令执行失败: ${e.message}"
        }
    }
}

private fun buildAgentHistory(messages: List<ChatMessage>, toolResults: List<String>, systemPrompt: String = SYSTEM_PROMPT): List<ChatMessage> {
    val result = mutableListOf<ChatMessage>()
    if (messages.isNotEmpty() && messages.first().role == "system") {
        result.add(messages.first())
    } else {
        result.add(ChatMessage("sys", "system", systemPrompt))
    }
    val remaining = messages.dropWhile { it.role == "system" }.filter { it.role != "system" }
    for (msg in remaining) {
        result.add(msg)
        if (msg.role == "user" && toolResults.isNotEmpty()) {
            val toolCtx = toolResults.joinToString("\n\n").take(12000)
            result.add(ChatMessage("ctx-${msg.id}", "user", "[工具结果]\n$toolCtx\n\n请根据上述工具结果继续推理，若已完成任务则输出 reply。"))
        }
    }
    return result
}

private fun parseActionStream(text: String): List<Action>? {
    val json = try {
        val s = text.trim()
        if (s.startsWith("```")) {
            val start = s.indexOf('\n')
            if (start < 0) return null
            val end = s.lastIndexOf("```")
            if (end <= start) return null
            JSONObject(s.substring(start, end).trim())
        } else {
            JSONObject(s)
        }
    } catch (_: Exception) { return null }
    val actions = json.optJSONArray("actions") ?: return null
    return buildList {
        for (i in 0 until actions.length()) {
            val a = actions.getJSONObject(i)
            add(Action(
                type = a.optString("type"),
                tool = a.optString("tool").ifBlank { null },
                url = a.optString("url").ifBlank { null },
                query = a.optString("query").ifBlank { null },
                content = a.optString("content").ifBlank { null },
                path = a.optString("path").ifBlank { null },
                payload = a.optString("payload").ifBlank { null },
                target = a.optString("target").ifBlank { null }
            ))
        }
    }
}

data class Action(
    val type: String,
    val tool: String? = null,
    val url: String? = null,
    val query: String? = null,
    val content: String? = null,
    val path: String? = null,
    val payload: String? = null,
    val target: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    sessionId: String?,
    title: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onNewChat: () -> Unit = {},
    onOpenBrowser: (String?) -> Unit = {},
    onOpenTerminal: () -> Unit = {}
) {
    val context = LocalContext.current
    val chatStore = remember { ChatStore.from(context) }
    val providerStore = remember { ProviderStore.from(context) }
    val assistantStore = remember { AssistantStore.from(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var loadingLabel by remember { mutableStateOf("分析问题") }
    var menuExpanded by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()
    var streamingId by remember { mutableStateOf<String?>(null) }
    var streamTick by remember { mutableStateOf(0) }
    val fileTools = FileTools

    // Load existing session
    LaunchedEffect(sessionId) {
        if (sessionId != null) {
            messages.clear()
            messages.addAll(chatStore.loadMessages(sessionId))
        }
    }

    // Typewriter effect
    LaunchedEffect(streamingId, streamTick) {
        if (streamingId != null) {
            val msg = messages.find { it.id == streamingId } ?: return@LaunchedEffect
            if (streamTick < msg.content.length) {
                delay(16)
                streamTick = nextBoundary(msg.content, streamTick)
            }
        }
    }

    // Auto-scroll
    LaunchedEffect(messages.size, streamTick) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun persist() {
        if (sessionId != null) chatStore.saveMessages(sessionId, messages.toList())
    }

    fun addReply(content: String, model: String?) {
        val id = UUID.randomUUID().toString()
        messages.add(ChatMessage(id, "assistant", content, model, "reply"))
        streamingId = id
        persist()
    }

    suspend fun runAgent(providers: List<ProviderConfig>, systemPrompt: String = SYSTEM_PROMPT) {
        var candidates = providers
        val toolResults = mutableListOf<String>()
        val fileBaseDir: File = fileTools.getBaseDir(context.applicationContext)
        repeat(MAX_AGENT_STEPS) {
            val failedAttempts = mutableListOf<RecoveryAttempt>()
            val recovery = recoverChatWithProviders(
                providers = candidates,
                history = buildAgentHistory(messages.toList(), toolResults, systemPrompt),
                systemPrompt = systemPrompt,
                onAttempt = { attempt ->
                    if (attempt.error != null) failedAttempts += attempt
                    loadingLabel = when {
                        attempt.error != null -> "正在切换可用模型"
                        attempt.attempt > 1 -> "正在重试 ${attempt.model}"
                        else -> "调用 ${attempt.model}"
                    }
                }
            )
            if (recovery == null) {
                addReply(recoveryFailureMessage(failedAttempts), providers.firstOrNull()?.mainModel)
                return
            }

            val provider = recovery.provider
            val original = providers.firstOrNull { it.id == provider.id }
            val preferred = original?.copy(
                models = listOf(recovery.model) + original.models.filterNot { it == recovery.model }
            ) ?: provider
            candidates = listOf(preferred) + providers.filterNot { it.id == preferred.id }

            val actions = parseActionStream(recovery.reply.content)
            if (actions == null || actions.isEmpty()) {
                addReply(recovery.reply.content, recovery.model)
                return
            }
            var done = false
            for (a in actions) {
                when (a.type) {
                    "think" -> {
                        loadingLabel = "分析问题"
                    }
                    "tool" -> {
                        val toolName = a.tool ?: continue
                        loadingLabel = when {
                            toolName.startsWith("browser.") -> "浏览网页"
                            toolName.startsWith("file.") -> "操作文件"
                            toolName.startsWith("terminal.") -> "执行命令"
                            else -> "执行工具"
                        }
                        try {
                            var result = when {
                                toolName.startsWith("browser.") -> when (toolName.removePrefix("browser.")) {
                                    "extract" -> WebTools.extract(a.url.orEmpty())
                                    "title" -> WebTools.title(a.url.orEmpty())
                                    "links" -> WebTools.links(a.url.orEmpty())
                                    "search" -> WebTools.search(a.query.orEmpty())
                                    "open" -> {
                                        val url = a.url ?: a.query ?: ""
                                        if (url.isNotBlank()) {
                                            kotlinx.coroutines.runBlocking {
                                                onOpenBrowser(url)
                                            }
                                        }
                                        "已在浏览器中打开：$url"
                                    }
                                    else -> null
                                }
                                toolName.startsWith("file.") -> when (toolName.removePrefix("file.")) {
                                    "list" -> {
                                        val path = a.path ?: a.url ?: ""
                                        fileTools.list(fileBaseDir, path)
                                    }
                                    "read" -> {
                                        val path = a.path ?: a.url ?: ""
                                        if (path.isBlank()) "文件路径为空" else fileTools.read(fileBaseDir, path)
                                    }
                                    "write" -> {
                                        val path = a.path ?: ""
                                        val content = a.content ?: ""
                                        if (path.isBlank()) "文件路径为空" else fileTools.write(fileBaseDir, path, content)
                                    }
                                    "append" -> {
                                        val path = a.path ?: ""
                                        val content = a.content ?: ""
                                        if (path.isBlank()) "文件路径为空" else fileTools.append(fileBaseDir, path, content)
                                    }
                                    "copy" -> {
                                        val src = a.path ?: ""
                                        val dst = a.target ?: a.content ?: ""
                                        if (src.isBlank() || dst.isBlank()) "源路径或目标路径为空"
                                        else fileTools.copy(fileBaseDir, src, dst)
                                    }
                                    "move" -> {
                                        val src = a.path ?: ""
                                        val dst = a.target ?: a.content ?: ""
                                        if (src.isBlank() || dst.isBlank()) "源路径或目标路径为空"
                                        else fileTools.move(fileBaseDir, src, dst)
                                    }
                                    "stat" -> {
                                        val path = a.path ?: a.url ?: ""
                                        if (path.isBlank()) "文件路径为空" else fileTools.stat(fileBaseDir, path)
                                    }
                                    "delete" -> {
                                        val path = a.path ?: a.url ?: ""
                                        if (path.isBlank()) "文件路径为空" else fileTools.delete(fileBaseDir, path)
                                    }
                                    else -> null
                                }
                                toolName.startsWith("terminal.") || toolName == "terminal.exec" -> {
                                    TerminalAgentTool.executeCommand(a.payload ?: a.content ?: a.query.orEmpty())
                                }
                                else -> null
                            }
                            if (result == null) {
                                result = "未知工具：$toolName"
                            }
                            toolResults.add("[$toolName]\n${result.take(10000)}")
                            messages.add(ChatMessage(
                                UUID.randomUUID().toString(), "tool", result.take(2000),
                                recovery.model, "tool"
                            ))
                            persist()
                        } catch (e: Exception) {
                            val err = "工具执行失败：${e.message ?: e.javaClass.simpleName}"
                            toolResults.add("[$toolName]\n$err")
                            messages.add(ChatMessage(
                                UUID.randomUUID().toString(), "tool", err,
                                recovery.model, "tool"
                            ))
                            persist()
                        }
                    }
                    "reply" -> {
                        addReply(a.content ?: recovery.reply.content, recovery.model)
                        done = true
                        return@repeat
                    }
                }
            }
            if (done) return
        }
        addReply("已达到最大推理步数（$MAX_AGENT_STEPS），可能未完成任务。", providers.firstOrNull()?.mainModel)
    }

    fun send() {
        val text = input.trim()
        if (text.isBlank() || loading) return
        input = ""
        val userMsg = ChatMessage(UUID.randomUUID().toString(), "user", text)
        messages.add(userMsg)
        persist()
        loading = true
        loadingLabel = "分析问题"
        scope.launch {
            val assistant = assistantStore.currentAssistant()
            val tagStore = TagStore.from(context)
            val memoryStore = MemoryStore.from(context)
            val tagId = assistant?.id?.let { tagStore.tagOfAssistant(it) }
            val memoryContext = memoryStore.buildMemoryContext(
                assistantId = assistant?.id ?: "",
                assistantTagId = tagId
            )
            var systemPrompt = if (assistant?.systemPrompt?.isNotBlank() == true) {
                assistant.systemPrompt
            } else {
                SYSTEM_PROMPT
            }
            if (memoryContext.isNotEmpty()) {
                systemPrompt += memoryContext
            }
            val allProviders = providerStore.load()
            val providers = if (assistant?.providerId?.isNotBlank() == true) {
                val matched = allProviders.filter { it.id == assistant.providerId }
                if (matched.isNotEmpty()) matched else allProviders
            } else {
                allProviders
            }
            if (providers.isEmpty()) {
                addReply("请先在设置中添加供应商（如 OpenAI）和模型。", null)
                loading = false
                return@launch
            }
            runAgent(prioritizeAssistantModel(providers, assistant), systemPrompt)
            loading = false
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        if (loading) {
                            Text(
                                loadingLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onNewChat) {
                        Icon(Icons.Outlined.AddComment, contentDescription = "新对话")
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = "工具菜单")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("打开浏览器") },
                                onClick = {
                                    menuExpanded = false
                                    onOpenBrowser(null)
                                },
                                leadingIcon = { Icon(Icons.Outlined.Public, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("打开终端") },
                                onClick = {
                                    menuExpanded = false
                                    onOpenTerminal()
                                },
                                leadingIcon = { Icon(Icons.Outlined.Terminal, null) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surface
            ) {
                ChatInputBar(
                    value = input,
                    onValueChange = { input = it },
                    onSend = { send() },
                    enabled = !loading,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    ) { padding ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "开始与 AgentT 对话",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "AgentT 可以浏览网页、操作文件、执行命令",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    when (msg.kind) {
                        "tool" -> {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        "工具结果",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        msg.content.take(300),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        "reply" -> {
                            val isStreaming = msg.id == streamingId
                            val displayText = if (isStreaming) msg.content.take(streamTick) else msg.content
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Outlined.Bolt,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            msg.model ?: "AgentT",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    MarkdownText(
                                        text = displayText,
                                        modifier = Modifier.widthIn(max = 600.dp)
                                    )
                                    if (isStreaming && streamTick < msg.content.length) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 4.dp)
                                                .size(8.dp, 16.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.primary,
                                                    RoundedCornerShape(2.dp)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            // user message
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Outlined.Face,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "你",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        msg.content,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text("给 AgentT 发送消息…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )
        IconButton(
            onClick = onSend,
            enabled = enabled,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Outlined.ArrowUpward, contentDescription = "发送", tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

// Advance the typewriter to the next sentence / line boundary (with a 48-char
// guard so unpunctuated text still progresses).
private fun nextBoundary(text: String, from: Int): Int {
    var i = from
    var guard = 0
    while (i < text.length && guard < 48) {
        val c = text[i]
        if (c == '\n' || c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?') break
        i++
        guard++
    }
    return if (i > from) minOf(text.length, i + 1) else minOf(text.length, from + 16)
}