package com.agentt.app.ui.providers

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Dialog
import androidx.compose.material3.DialogProperties
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
    val models: List<String>
) {
    val mainModel: String get() = models.firstOrNull() ?: ""
}

data class ProtocolPreset(
    val key: String,
    val label: String,
    val defaultBaseUrl: String,
    val apiKeyLabel: String,
    val needsApiKey: Boolean = true
)

val PROTOCOLS = listOf(
    ProtocolPreset("openai", "OpenAI", "https://api.openai.com/v1", "API Key"),
    ProtocolPreset("anthropic", "Anthropic", "https://api.anthropic.com", "API Key"),
    ProtocolPreset("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta", "API Key"),
    ProtocolPreset("ollama", "Ollama", "http://localhost:11434", "API Key（本地可留空）", needsApiKey = false),
    ProtocolPreset("custom", "自定义 · OpenAI 兼容", "https://api.example.com/v1", "API Key")
)

fun protocolLabel(key: String): String = PROTOCOLS.firstOrNull { it.key == key }?.label ?: key

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
                            models = parseModels(o)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseModels(o: JSONObject): List<String> {
        val arr = o.optJSONArray("models")
        if (arr != null) {
            return buildList { for (i in 0 until arr.length()) add(arr.getString(i)) }.filter { it.isNotBlank() }
        }
        return listOf(o.optString("model")).filter { it.isNotBlank() }
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
                    .put("models", JSONArray().apply { it.models.forEach(::put) })
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

data class NetResult(val code: Int, val body: String)

fun httpJson(method: String, url: String, headers: Map<String, String> = emptyMap(), body: JSONObject? = null): NetResult {
    var conn: HttpURLConnection? = null
    return try {
        conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Content-Type", "application/json")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        val code = conn.responseCode
        val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        NetResult(code, resp)
    } finally {
        conn?.disconnect()
    }
}

data class TestResult(val ok: Boolean, val message: String)

suspend fun testProviderApi(p: ProviderConfig): TestResult = withContext(Dispatchers.IO) {
    try {
        val url = when (p.protocol) {
            "anthropic" -> p.baseUrl.trimEnd('/') + "/v1/messages"
            "gemini" -> p.baseUrl.trimEnd('/') + "/models/${p.mainModel}:generateContent?key=${p.apiKey}"
            "ollama" -> p.baseUrl.trimEnd('/') + "/api/chat"
            else -> p.baseUrl.trimEnd('/') + "/chat/completions"
        }
        val headers = when (p.protocol) {
            "anthropic" -> mapOf("x-api-key" to p.apiKey, "anthropic-version" to "2023-06-01")
            "ollama" -> emptyMap()
            else -> mapOf("Authorization" to "Bearer ${p.apiKey}")
        }
        val body = when (p.protocol) {
            "anthropic" -> JSONObject().put("model", p.mainModel).put("max_tokens", 8)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "ping")))
            "gemini" -> JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "ping")))))
            "ollama" -> JSONObject().put("model", p.mainModel).put("stream", false)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "ping")))
            else -> JSONObject().put("model", p.mainModel).put("max_tokens", 8)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "ping")))
        }
        val r = httpJson("POST", url, headers, body)
        if (r.code in 200..299) TestResult(true, "连接成功（HTTP ${r.code}）")
        else TestResult(false, "请求失败（HTTP ${r.code}）：${r.body.take(160)}")
    } catch (e: Exception) {
        TestResult(false, "连接失败：${e.message ?: e.javaClass.simpleName}")
    }
}

data class ModelListResult(val models: List<String>, val error: String?)

suspend fun fetchProviderModels(protocol: String, baseUrl: String, apiKey: String): ModelListResult =
    withContext(Dispatchers.IO) {
        try {
            val (url, headers, extract) = when (protocol) {
                "anthropic" -> Triple(
                    baseUrl.trimEnd('/') + "/v1/models",
                    mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01"),
                    { root: JSONObject -> idsFrom(root) }
                )
                "gemini" -> Triple(
                    baseUrl.trimEnd('/') + "/models?key=$apiKey",
                    emptyMap(),
                    { root: JSONObject -> namesFrom(root) }
                )
                "ollama" -> Triple(
                    baseUrl.trimEnd('/') + "/api/tags",
                    emptyMap(),
                    { root: JSONObject -> namesFrom(root) }
                )
                else -> Triple(
                    baseUrl.trimEnd('/') + "/models",
                    mapOf("Authorization" to "Bearer $apiKey"),
                    { root: JSONObject -> idsFrom(root) }
                )
            }
            val r = httpJson("GET", url, headers)
            if (r.code !in 200..299) return@withContext ModelListResult(emptyList(), "HTTP ${r.code}：${r.body.take(160)}")
            val models = extract(JSONObject(r.body))
            if (models.isEmpty()) ModelListResult(emptyList(), "未获取到模型")
            else ModelListResult(models, null)
        } catch (e: Exception) {
            ModelListResult(emptyList(), "获取失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

private fun idsFrom(root: JSONObject): List<String> =
    buildList {
        val arr = root.optJSONArray("data")
        for (i in 0 until (arr?.length() ?: 0)) add(arr.getJSONObject(i).optString("id"))
    }.filter { it.isNotBlank() }

private fun namesFrom(root: JSONObject): List<String> =
    buildList {
        val arr = root.optJSONArray("models")
        for (i in 0 until (arr?.length() ?: 0)) add(arr.getJSONObject(i).optString("name").substringAfterLast("/"))
    }.filter { it.isNotBlank() }

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
                    Text("还没有供应商", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text("点击右下角 + 添加第一个供应商", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
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
        ProviderEditScreen(
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { saveProvider(it); showAdd = false }
        )
    }
    editing?.let { target ->
        ProviderEditScreen(
            initial = target,
            onDismiss = { editing = null },
            onSave = { saveProvider(it); editing = null }
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
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
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
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                protocolLabel(provider.protocol),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1
                            )
                        }
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
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onTest) {
                        Icon(Icons.Outlined.Bolt, contentDescription = "测试连接", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "模型：${provider.models.take(3).joinToString(" / ")}${if (provider.models.size > 3) " …" else ""}    API Key：${provider.apiKey.take(6)}****",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            testResult?.let { (msg, ok) ->
                Spacer(Modifier.height(6.dp))
                Text(
                    if (ok) "✓ $msg" else "✗ $msg",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ProviderEditScreen(
    initial: ProviderConfig?,
    onDismiss: () -> Unit,
    onSave: (ProviderConfig) -> Unit
) {
    val scope = rememberCoroutineScope()
    var protocol by remember { mutableStateOf(initial?.protocol ?: "openai") }
    var name by remember { mutableStateOf(initial?.name ?: "OpenAI") }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: PROTOCOLS.first().defaultBaseUrl) }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedModels by remember { mutableStateOf(initial?.models ?: emptyList()) }
    var fetchingModels by remember { mutableStateOf(false) }
    var modelError by remember { mutableStateOf<String?>(null) }
    var modelMenu by remember { mutableStateOf(false) }

    val preset = PROTOCOLS.firstOrNull { it.key == protocol } ?: PROTOCOLS.first()
    val canSave = name.isNotBlank() && baseUrl.isNotBlank() && (!preset.needsApiKey || apiKey.isNotBlank()) && selectedModels.isNotEmpty()

    fun loadModels() {
        fetchingModels = true
        modelError = null
        scope.launch {
            val r = fetchProviderModels(protocol, baseUrl.trim(), apiKey.trim())
            if (r.models.isNotEmpty()) {
                availableModels = r.models
                if (selectedModels.isEmpty()) selectedModels = listOf(r.models.first())
            } else {
                modelError = r.error
            }
            fetchingModels = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (initial == null) "添加供应商" else "编辑供应商",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("通信协议", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PROTOCOLS.forEach { p ->
                        FilterChip(
                            selected = protocol == p.key,
                            onClick = {
                                protocol = p.key
                                if (initial == null) {
                                    baseUrl = p.defaultBaseUrl
                                    availableModels = emptyList()
                                    selectedModels = emptyList()
                                    modelError = null
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
                    placeholder = { Text("OpenAI") },
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
                if (preset.needsApiKey) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(preset.apiKeyLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("模型（可多选）", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    if (fetchingModels) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    IconButton(onClick = { if (!fetchingModels) loadModels() }, enabled = !fetchingModels) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新模型", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                modelError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (selectedModels.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        selectedModels.forEach { m ->
                            InputChip(
                                selected = true,
                                onClick = { selectedModels = selectedModels - m },
                                label = { Text(m, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                trailingIcon = {
                                    Icon(Icons.Outlined.Close, contentDescription = "移除", Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }
                Box {
                    OutlinedButton(
                        onClick = { if (availableModels.isEmpty()) loadModels() else modelMenu = true },
                        enabled = !fetchingModels,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (availableModels.isEmpty()) "获取模型" else "选择模型（已选 ${selectedModels.size}）")
                        Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = modelMenu,
                        onDismissRequest = { modelMenu = false },
                        modifier = Modifier.heightIn(max = 320.dp)
                    ) {
                        availableModels.forEach { m ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = m in selectedModels, onCheckedChange = null)
                                        Text(m, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                },
                                onClick = {
                                    selectedModels = if (m in selectedModels) selectedModels - m else selectedModels + m
                                }
                            )
                        }
                    }
                }
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
                                models = selectedModels
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
}
