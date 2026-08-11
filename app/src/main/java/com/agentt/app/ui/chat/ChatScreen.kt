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
import com.agentt.app.ui.files.FileTools
import com.agentt.app.ui.markdown.MarkdownText
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
import java.util.UUID

const val MAX_AGENT_STEPS = 5

private const val SYSTEM_PROMPT = """你是 AgentT 智能体，运行在安卓手机上。你通过"动作流"来完成任务。
你可以调用内置工具获取实时信息：
- browser.search(query)：搜索互联网
- browser.extract(url)：抓取网页正文
- browser.title(url)：获取网页标题
- browser.links(url)：提取网页链接
- browser.open(url)：在浏览器中打开网页
- file.list(path)：列出目录内容
- file.read(path)：读取文本文件
- file.write(path, content)：写入文本文件
- file.stat(path)：查看文件信息
- file.delete(path)：删除文件

你的输出必须是 JSON 动作流，不要输出任何其它文字，格式如下：
{"actions":[{"type":"think","content":"你的推理"},{"type":"tool","tool":"browser.extract","url":"https://example.com"},{"type":"reply","content":"最终回答"}]}

规则：
1. type 只能是 think（推理）、tool（调用工具，带 tool 字段指定工具名）、reply（最终回复）。
2. 文件工具：file.read/write 带 path 和可选的 content；file.list/stat/delete 带 path。
3. 工具结果会自动回传给你，你再继续推理。
4. 如果一次动作流里没有 reply，你会收到工具结果后继续，直到给出 reply。
5. 不需要工具时，直接输出 {"actions":[{"type":"reply","content":"回答"}]}。
6. reply 的 content 是最终展示给用户的内容，使用与用户相同的语言。"""

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
    for (msg in history) {
        parts.put(JSONObject().put("role", if (msg.role == "assistant") "model" else "user").put("parts", JSONArray().put(JSONObject().put("text", msg.content))))
    }
    val r = httpJson(
        "POST", p.baseUrl.trimEnd('/') + "/v1beta/models/${p.mainModel}:generateContent",
        mapOf("x-goog-api-key" to p.apiKey),
        JSONObject().put("contents", parts).put("generationConfig", JSONObject().put("maxOutputTokens", 4096))
    )
    if (r.code !in 200..299) throw RuntimeException("HTTP ${r.code}：${r.body.take(160)}")
    return JSONObject(r.body).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
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

data class ChatMessage(
    val id: String,
    val role: String,
    val content: String,
    val model: String? = null,
    val kind: String = if (role == "user") "text" else "reply"
)

data class Action(
    val type: String,
    val tool: String = "",
    val url: String? = null,
    val query: String? = null,
    val content: String = "",
    val requirement: String = ""
)

fun parseActionStream(raw: String): List<Action>? {
    try {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val obj = JSONObject(cleaned)
        val arr = obj.optJSONArray("actions") ?: return null
        val result = mutableListOf<Action>()
        for (i in 0 until arr.length()) {
            val a = arr.getJSONObject(i)
            result.add(Action(
                type = a.optString("type", ""),
                tool = a.optString("tool", ""),
                url = a.optString("url", null).ifBlank { null },
                query = a.optString("query", null).ifBlank { null },
                content = a.optString("content", ""),
                requirement = a.optString("requirement", "")
            ))
        }
        return result
    } catch (_: Exception) {
        return null
    }
}

data class RecoveryAttempt(
    val provider: String,
    val model: String,
    val error: String? = null,
    val attempt: Int = 1
)

data class RecoveryResult(
    val provider: ProviderConfig,
    val model: String,
    val reply: ChatMessage
)

suspend fun recoverChatWithProviders(
    providers: List<ProviderConfig>,
    history: List<ChatMessage>,
    onAttempt: (RecoveryAttempt) -> Unit = {}
): RecoveryResult? {
    for (provider in providers) {
        val preferred = provider.models.firstOrNull() ?: provider.mainModel
        var lastError: String? = null
        for (attempt in 1..3) {
            val attemptInfo = RecoveryAttempt(
                provider = provider.name,
                model = if (provider.models.size > 1) "$preferred (${provider.models.size}个模型)" else preferred,
                attempt = attempt
            )
            val modelToUse = if (attempt == 1) preferred else provider.models.getOrNull(attempt - 1) ?: preferred
            val p = provider.copy(mainModel = modelToUse, models = provider.models.filter { it != preferred } + preferred)
            onAttempt(attemptInfo.copy(error = null))
            val result = chatWithProvider(p, listOf(ChatMessage("sys", "system", SYSTEM_PROMPT)) + history)
            if (result.ok) {
                val msg = ChatMessage(
                    UUID.randomUUID().toString(), "assistant", result.content,
                    modelToUse, "reply"
                )
                return RecoveryResult(p, modelToUse, msg)
            }
            lastError = result.error
            val isConfigError = result.error?.let { e ->
                e.contains("401") || e.contains("403") || e.contains("40") ||
                    e.contains("API key") || e.contains("apikey") || e.contains("auth") ||
                    e.contains("not found") || e.contains("Not Found") || e.contains("404")
            } ?: false
            if (isConfigError) {
                onAttempt(attemptInfo.copy(error = "配置错误，跳过此供应商"))
                break
            }
            onAttempt(attemptInfo.copy(error = "请求失败，${provider.models.size - attempt}个备用模型可尝试"))
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
            b.writeInput(command + "\n")
            delay(2000)
            val output = b.readOutput().take(3000)
            if (output.isBlank()) "命令已执行，无输出" else output
        } catch (e: Exception) {
            "命令执行失败: ${e.message}"
        }
    }
}

private fun buildAgentHistory(messages: List<ChatMessage>, toolResults: List<String>): List<ChatMessage> {
    val result = mutableListOf<ChatMessage>()
    if (messages.isNotEmpty() && messages.first().role == "system") {
        result.add(messages.first())
    } else {
        result.add(ChatMessage("sys", "system", SYSTEM_PROMPT))
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
    val chatStore = remember { ChatStore(context) }
    val providerStore = remember { ProviderStore(context) }
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

    suspend fun runAgent(providers: List<ProviderConfig>) {
        var candidates = providers
        val toolResults = mutableListOf<String>()
        val fileBaseDir = fileTools.getBaseDir(context.applicationContext)
        repeat(MAX_AGENT_STEPS) {
            val failedAttempts = mutableListOf<RecoveryAttempt>()
            val recovery = recoverChatWithProviders(
                providers = candidates,
                history = buildAgentHistory(messages.toList(), toolResults),
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
                        messages.add(ChatMessage(UUID.randomUUID().toString(), "assistant", a.content, recovery.model, "think"))
                        persist()
                    }
                    "browser" -> {
                        loadingLabel = AgentToolRegistry.actionLabel(a.tool)
                        val summary = WebTools.runTool(a.tool, a.url, a.query)
                        messages.add(
                            ChatMessage(
                                UUID.randomUUID().toString(), "assistant",
                                "${a.tool}(${a.url ?: a.query})\n${summary.take(1500)}",
                                recovery.model, "tool"
                            )
                        )
                        toolResults.add("[工具 ${a.tool}(${a.url ?: a.query}) 结果]\n${summary.take(3000)}")
                        if (a.tool == "open" && a.url != null) onOpenBrowser(a.url)
                        persist()
                    }
                    "file" -> {
                        loadingLabel = AgentToolRegistry.actionLabel(a.tool)
                        val toolId = AgentToolRegistry.canonicalId(a.tool)
                        val path = a.url ?: a.query ?: ""
                        val summary = withContext(Dispatchers.IO) {
                            when (toolId) {
                                "file.list" -> fileTools.list(fileBaseDir, path)
                                "file.read" -> fileTools.read(fileBaseDir, path)
                                "file.write" -> {
                                    val content = a.content.ifBlank { a.requirement }
                                    fileTools.write(fileBaseDir, path, content)
                                }
                                "file.stat" -> fileTools.stat(fileBaseDir, path)
                                "file.delete" -> fileTools.delete(fileBaseDir, path)
                                else -> "未知文件工具: ${a.tool}"
                            }
                        }
                        messages.add(
                            ChatMessage(
                                UUID.randomUUID().toString(), "assistant",
                                "${a.tool}($path)\n${summary.take(1500)}",
                                recovery.model, "tool"
                            )
                        )
                        toolResults.add("[工具 ${a.tool}($path) 结果]\n${summary.take(3000)}")
                        persist()
                    }
                    "reply" -> {
                        loadingLabel = "整理回答"
                        addReply(a.content, recovery.model)
                        done = true
                    }
                }
            }
            if (done) return
        }
        addReply("已达到最大执行步数（$MAX_AGENT_STEPS），请把问题拆小后重试。", candidates.firstOrNull()?.mainModel)
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || loading) return
        input = ""
        messages.add(ChatMessage(UUID.randomUUID().toString(), "user", text))
        persist()
        loading = true
        loadingLabel = "分析问题"
        scope.launch {
            val providers = providerStore.load()
            if (providers.isEmpty()) {
                addReply("尚未配置供应商：请到 设置 → 供应商 添加并测试连接后，再开始对话。", null)
                loading = false
                return@launch
            }
            runAgent(providers)
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
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("AgentT 助手", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Outlined.Add, contentDescription = "更多", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("新建对话") },
                                onClick = { menuExpanded = false; onNewChat() },
                                leadingIcon = { Icon(Icons.Outlined.AddComment, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("打开终端") },
                                onClick = { menuExpanded = false; onOpenTerminal() },
                                leadingIcon = { Icon(Icons.Outlined.Terminal, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("打开浏览器") },
                                onClick = { menuExpanded = false; onOpenBrowser(null) },
                                leadingIcon = { Icon(Icons.Outlined.Public, contentDescription = null) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            ChatInputBar(
                value = input,
                onValueChange = { input = it },
                onSend = { send() },
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
            )
        }
    ) { innerPadding ->
        if (messages.isEmpty() && !loading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "开始对话",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "AgentT 可以调用工具来帮你完成各种任务",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(
                        msg = msg,
                        isStreaming = msg.id == streamingId && streamTick < msg.content.length,
                        streamTick = streamTick,
                        onOpenBrowser = onOpenBrowser
                    )
                }
                if (loading) {
                    item {
                        LoadingIndicator(loadingLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    msg: ChatMessage,
    isStreaming: Boolean,
    streamTick: Int,
    onOpenBrowser: (String) -> Unit = {}
) {
    val isUser = msg.role == "user"
    val isSystem = msg.role == "system"
    val kind = msg.kind
    val bubbleColor = when {
        isUser -> MaterialTheme.colorScheme.primaryContainer
        kind == "think" -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        kind == "tool" -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val rowAlignment = if (isUser) Arrangement.End else Arrangement.Start

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = rowAlignment
    ) {
        when {
            isUser -> {
                Surface(
                    modifier = Modifier.widthIn(max = 320.dp),
                    shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                    color = bubbleColor
                ) {
                    Text(
                        msg.content,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
            kind == "think" -> {
                Row(
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bubbleColor)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        msg.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            kind == "tool" -> {
                Row(
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bubbleColor)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (msg.content.length > 100) msg.content.take(100) + "…" else msg.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 3
                    )
                }
            }
            else -> {
                // Assistant reply with Markdown support
                Surface(
                    modifier = Modifier.widthIn(max = 400.dp),
                    shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                    color = bubbleColor,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        if (isStreaming) {
                            val displayText = msg.content.substring(0, minOf(msg.content.length, streamTick))
                            MarkdownText(
                                markdown = displayText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            MarkdownText(
                                markdown = msg.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (isStreaming && streamTick < msg.content.length) {
                            Spacer(Modifier.height(4.dp))
                            val blinkAlpha = rememberInfiniteTransition("blink").animateFloat(
                                initialValue = 1f, targetValue = 0.3f,
                                animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse)
                            )
                            Text(
                                "▌",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = blinkAlpha.value)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingIndicator(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val alpha = rememberInfiniteTransition("loading").animateFloat(
            initialValue = 0.3f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse)
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha.value))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha.value)
        )
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