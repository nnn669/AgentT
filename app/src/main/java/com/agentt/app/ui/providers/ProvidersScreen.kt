package com.agentt.app.ui.providers

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class ProviderConfig(
    val id: String,
    val name: String,
    val protocol: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String
)

data class ProtocolPreset(
    val key: String,
    val label: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val apiKeyLabel: String,
    val needsApiKey: Boolean = true
)

val PROTOCOLS = listOf(
    ProtocolPreset("openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", "API Key"),
    ProtocolPreset("anthropic", "Anthropic", "https://api.anthropic.com", "claude-3-5-haiku-20241022", "API Key"),
    ProtocolPreset("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-1.5-flash", "API Key"),
    ProtocolPreset("ollama", "Ollama", "http://localhost:11434", "llama3.2", "API Key（本地可留空）", needsApiKey = false),
    ProtocolPreset("custom", "自定义 · OpenAI 兼容", "https://api.example.com/v1", "gpt-4o-mini", "API Key")
)

fun protocolLabel(key: String): String =
    PROTOCOLS.firstOrNull { it.key == key }?.label ?: key

class ProviderStore(private val prefs: SharedPreferences) {
    fun load(): List<ProviderConfig> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        ProviderConfig(
                            id = o.optString("id", UUID.randomUUID().toString()),
                            name = o.optString("name"),
                            protocol = o.optString("protocol", "openai"),
                            baseUrl = o.optString("baseUrl"),
                            apiKey = o.optString("apiKey"),
                            model = o.optString("model")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(providers: List<ProviderConfig>) {
        val arr = JSONArray()
        providers.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("protocol", it.protocol)
                    .put("baseUrl", it.baseUrl)
                    .put("apiKey", it.apiKey)
                    .put("model", it.model)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "providers"
        fun from(context: Context): ProviderStore =
            ProviderStore(context.getSharedPreferences("agentt", Context.MODE_PRIVATE))
    }
}

data class TestResult(val ok: Boolean, val message: String)

suspend fun testProviderApi(p: ProviderConfig): TestResult = withContext(Dispatchers.IO) {
    try {
        when (p.protocol) {
            "anthropic" -> httpPostJson(
                p.baseUrl.trimEnd('/') + "/v1/messages",
                mapOf("x-api-key" to p.apiKey, "anthropic-version" to "2023-06-01"),
                JSONObject()
                    .put("model", p.model)
                    .put("max_tokens", 8)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "ping")))
            )
            "gemini" -> httpPostJson(
                p.baseUrl.trimEnd('/') + "/models/${p.model}:generateContent?key=${p.apiKey}",
                emptyMap(),
                JSONObject().put(
                    "contents",
                    JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "ping"))))
                )
            )
            "ollama" -> httpPostJson(
                p.baseUrl.trimEnd('/') + "/api/chat",
                emptyMap(),
                JSONObject()
                    .put("model", p.model)
                    .put("stream", false)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "ping")))
            )
            else -> httpPostJson(
                p.baseUrl.trimEnd('/') + "/chat/completions",
                mapOf("Authorization" to "Bearer ${p.apiKey}"),
                JSONObject()
                    .put("model", p.model)
                    .put("max_tokens", 8)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "ping")))
            )
        }
    } catch (e: Exception) {
        TestResult(false, "连接失败：${e.message ?: e.javaClass.simpleName}")
    }
}

private fun httpPostJson(url: String, headers: Map<String, String>, body: JSONObject): TestResult {
    var conn: HttpURLConnection? = null
    return try {
        conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Content-Type", "application/json")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        if (code in 200..299) TestResult(true, "连接成功（HTTP $code）")
        else TestResult(false, "请求失败（HTTP $code）：${resp.take(160)}")
    } catch (e: Exception) {
        TestResult(false, "连接失败：${e.message ?: e.javaClass.simpleName}")
    } finally {
        conn?.disconnect()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val store = remember { ProviderStore.from(context.applicationContext) }
    val providers = remember { mutableStateListOf<ProviderConfig>().apply { addAll(store.load()) } }
    val testResults = remember { mutableStateMapOf<String, Pair<String, Boolean>>() }
    var testingId by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProviderConfig?>(null) }
    val scope = rememberCoroutineScope()

    fun saveProvider(p: ProviderConfig) {
        val idx = providers.indexOfFirst { it.id == p.id }
        if (idx >= 0) providers[idx] = p else providers.add(p)
        store.save(providers.toList())
        testResults.remove(p.id)
    }

    fun deleteProvider(p: ProviderConfig) {
        providers.removeAll { it.id == p.id }
        store.save(providers.toList())
        testResults.remove(p.id)
    }

    fun runTest(p: ProviderConfig) {
        testingId = p.id
        scope.launch {
            val r = testProviderApi(p)
            testResults[p.id] = r.message to r.ok
            testingId = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("供应商", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("API 提供商", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "添加供应商")
            }
        }
    ) { padding ->
        if (providers.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(
                        Icons.Outlined.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "还没有供应商\n点击右下角 + 添加，测试 API 连接",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(providers, key = { it.id }) { p ->
                    ProviderCard(
                        provider = p,
                        testing = testingId == p.id,
                        testResult = testResults[p.id],
                        onTest = { runTest(p) },
                        onEdit = { editing = p },
                        onDelete = { deleteProvider(p) }
                    )
                }
            }
        }
    }

    if (showAdd) {
        ProviderEditSheet(
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { p ->
                saveProvider(p)
                showAdd = false
            }
        )
    }
    editing?.let { target ->
        ProviderEditSheet(
            initial = target,
            onDismiss = { editing = null },
            onSave = { p ->
                saveProvider(p)
                editing = null
            }
        )
    }
}

@Composable
private fun ProviderCard(
    provider: ProviderConfig,
    testing: Boolean,
    testResult: Pair<String, Boolean>?,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onEdit,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        provider.name.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            provider.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        ProtocolBadge(provider.protocol)
                    }
                    Text(
                        provider.baseUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (testing) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onTest) {
                        Icon(
                            Icons.Outlined.Bolt,
                            contentDescription = "测试连接",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "模型：${provider.model}    API Key：${provider.apiKey.take(6)}****",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            testResult?.let { (msg, ok) ->
                Spacer(Modifier.height(4.dp))
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ok) Color(0xFF34A853) else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ProtocolBadge(protocol: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            protocolLabel(protocol),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ProviderEditSheet(
    initial: ProviderConfig?,
    onDismiss: () -> Unit,
    onSave: (ProviderConfig) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var protocol by remember { mutableStateOf(initial?.protocol ?: "openai") }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: PROTOCOLS.first().defaultBaseUrl) }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    var model by remember { mutableStateOf(initial?.model ?: PROTOCOLS.first().defaultModel) }

    val preset = PROTOCOLS.firstOrNull { it.key == protocol } ?: PROTOCOLS.first()
    val canSave = name.isNotBlank() && baseUrl.isNotBlank() &&
        (!preset.needsApiKey || apiKey.isNotBlank()) && model.isNotBlank()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (initial == null) "添加供应商" else "编辑供应商",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                "通信协议",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PROTOCOLS.forEach { p ->
                    FilterChip(
                        selected = protocol == p.key,
                        onClick = {
                            protocol = p.key
                            if (initial == null) {
                                baseUrl = p.defaultBaseUrl
                                model = p.defaultModel
                            }
                        },
                        label = { Text(p.label) }
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                placeholder = { Text("例如 OpenAI") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                placeholder = { Text(preset.defaultBaseUrl) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(preset.apiKeyLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("默认模型") },
                placeholder = { Text(preset.defaultModel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(4.dp))
            Button(
                enabled = canSave,
                onClick = {
                    onSave(
                        ProviderConfig(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            protocol = protocol,
                            baseUrl = baseUrl.trim(),
                            apiKey = apiKey.trim(),
                            model = model.trim()
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("保存", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}