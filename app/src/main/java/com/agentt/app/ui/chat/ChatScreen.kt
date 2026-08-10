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

private const val SYSTEM_PROMPT = """你是 AgentT 智能体，运行在安卓手机上。你通过“动作流”来完成任务。
你可以调用内置网页工具获取实时信息：
- browser.open(url)：在内置浏览器中打开网页
- browser.extract(url)：抓取网页并返回正文纯文本
- browser.title(url)：获取网页标题
- browser.links(url)：提取网页中的所有链接
- browser.search(query)：搜索网页，返回结果列表

你的输出必须是 JSON 动作流，不要输出任何其它文字，格式如下：
{"actions":[{"type":"think","content":"你的推理"},{"type":"browser","tool":"extract","url":"https://example.com"},{"type":"reply","content":"最终回答"}]}

规则：
1. type 只能是 think（推理）、browser（调用工具）、reply（最终回复）。
2. browser 动作必须带 tool 字段；extract/title/links 带 url，search 带 query。
3. 需要网页信息时，先用 browser 获取，工具结果会自动回传给你，你再继续推理。
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
    return JSONObject(r.body).optJSONArray("content")?.optJSONObject(0)?.optString("text")
        ?: throw RuntimeException("响应中无内容")
}

private fun chatGemini(p: ProviderConfig, history: List<ChatMessage>): String {
    val contents = JSONArray().apply {
        history.forEach { m ->
            put(
                JSONObject()
                    .put("role", if (m.role == "assistant") "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", m.content)))
            )
        }
    }
    val r = httpJson(
        "POST", p.baseUrl.trimEnd('/') + "/models/${p.mainModel}:generateContent?key=${p.apiKey}",
        emptyMap(),
        JSONObject().put("contents", contents)
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

private fun buildAgentHistory(history: List<ChatMessage>, toolResults: List<String>): List<ChatMessage> {
    val out = mutableListOf<ChatMessage>()
    out.add(ChatMessage(UUID.randomUUID().toString(), "user", "[系统指令]\n$SYSTEM_PROMPT", null, "text"))
    history.forEach { if (it.kind != "tool") out.add(it) }
    toolResults.forEach {
        out.add(ChatMessage(UUID.randomUUID().toString(), "user", it, null, "text"))
    }
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    title: String,
    sessionId: String? = null,
    onBack: () -> Unit,
    onNewChat: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenBrowser: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val chatStore = remember { ChatStore.from(context.applicationContext) }
    val providerStore = remember { ProviderStore.from(context.applicationContext) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var streamingId by remember { mutableStateOf<String?>(null) }
    var streamTick by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(sessionId) {
        messages.clear()
        if (sessionId != null) messages.addAll(chatStore.loadMessages(sessionId))
        loading = false
    }

    // Auto-follow: scroll to the newest message when the list grows or the
    // typewriter pushes a reply card taller.
    LaunchedEffect(messages.size, streamTick) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
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

    suspend fun runAgent(provider: ProviderConfig) {
        val toolResults = mutableListOf<String>()
        repeat(MAX_AGENT_STEPS) {
            val reply = chatWithProvider(provider, buildAgentHistory(messages.toList(), toolResults))
            if (!reply.ok) {
                addReply("⚠️ 调用失败：${reply.error}", provider.mainModel)
                return
            }
            val actions = parseActionStream(reply.content)
            if (actions == null || actions.isEmpty()) {
                addReply(reply.content, provider.mainModel)
                return
            }
            var done = false
            for (a in actions) {
                when (a.type) {
                    "think" -> {
                        messages.add(ChatMessage(UUID.randomUUID().toString(), "assistant", a.content, provider.mainModel, "think"))
                        persist()
                    }
                    "browser" -> {
                        val summary = WebTools.runTool(a.tool, a.url, a.query)
                        messages.add(
                            ChatMessage(
                                UUID.randomUUID().toString(), "assistant",
                                "${a.tool}(${a.url ?: a.query})\n${summary.take(1500)}",
                                provider.mainModel, "tool"
                            )
                        )
                        toolResults.add("[工具 ${a.tool}(${a.url ?: a.query}) 结果]\n${summary.take(3000)}")
                        if (a.tool == "open" && a.url != null) onOpenBrowser(a.url)
                        persist()
                    }
                    "reply" -> {
                        addReply(a.content, provider.mainModel)
                        done = true
                    }
                }
            }
            if (done) return
        }
        addReply("已达到最大执行步数（$MAX_AGENT_STEPS），请把问题拆小后重试。", provider.mainModel)
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || loading) return
        input = ""
        messages.add(ChatMessage(UUID.randomUUID().toString(), "user", text))
        persist()
        loading = true
        scope.launch {
            val provider = providerStore.load().firstOrNull()
            if (provider == null) {
                addReply("尚未配置供应商：请到 设置 → 供应商 添加并测试连接后，再开始对话。", null)
                loading = false
                return@launch
            }
            runAgent(provider)
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
                        Text("Agent 助手", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f))
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "你好，我是你的 Agent 助手\n在下方输入消息开始对话",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { m ->
                    when {
                        m.role == "user" -> UserBubble(m.content)
                        m.kind == "think" -> ThinkCard(m.content)
                        m.kind == "tool" -> ToolCard(m.content)
                        else -> AgentCard(
                            content = m.content,
                            model = m.model,
                            streaming = m.id == streamingId,
                            onTick = { streamTick++ }
                        )
                    }
                }
                if (loading) item { AgentThinking() }
            }
        }
    }
}

@Composable
private fun UserBubble(content: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun AgentCard(
    content: String,
    model: String?,
    streaming: Boolean = false,
    onTick: () -> Unit = {},
) {
    var visible by remember(content, streaming) {
        mutableStateOf(if (streaming) 0 else content.length)
    }
    LaunchedEffect(content, streaming) {
        if (!streaming) {
            visible = content.length
            return@LaunchedEffect
        }
        while (visible < content.length) {
            visible = nextBoundary(content, visible)
            onTick()
            delay(24)
        }
    }
    val cursorAlpha by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "cursorAlpha"
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Agent", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    model?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            MarkdownText(
                content.take(visible),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
            if (streaming && visible < content.length) {
                Text(
                    "▍",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = cursorAlpha)
                )
            }
        }
    }
}

@Composable
private fun ThinkCard(content: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Bolt, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("思考", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Text(content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToolCard(content: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Public, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("网页工具", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Text(content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AgentThinking() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.width(10.dp))
            Text("Agent 正在执行动作流…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text("给 Agent 发送消息…", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
