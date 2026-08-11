package com.agentt.app.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agentt.app.ui.chat.AgentAssistant
import com.agentt.app.ui.chat.AssistantStore
import com.agentt.app.ui.providers.ProviderStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantEditScreen(
    assistantId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val assistantStore = remember { AssistantStore.from(context) }
    val providerStore = remember { ProviderStore.from(context) }
    var assistant by remember { mutableStateOf(assistantStore.load().firstOrNull { it.id == assistantId } ?: AgentAssistant(id = assistantId, name = "")) }
    var name by remember { mutableStateOf(assistant.name) }
    var avatar by remember { mutableStateOf(assistant.avatar) }
    var systemPrompt by remember { mutableStateOf(assistant.systemPrompt) }
    var providerId by remember { mutableStateOf(assistant.providerId) }
    var modelId by remember { mutableStateOf(assistant.modelId) }
    var contextSize by remember { mutableStateOf(assistant.contextMessageSize.toString()) }
    var streamOutput by remember { mutableStateOf(assistant.streamOutput) }
    var searchEnabled by remember { mutableStateOf(assistant.searchEnabled) }
    var showProviderPicker by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showAvatarPicker by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    val providers = remember { providerStore.load() }
    val selectedProvider = providers.firstOrNull { it.id == providerId }

    fun save() {
        val updated = assistant.copy(
            name = name.trim().ifBlank { assistant.name },
            avatar = avatar,
            systemPrompt = systemPrompt.trim(),
            providerId = providerId,
            modelId = modelId,
            contextMessageSize = contextSize.toIntOrNull()?.coerceIn(1, 1024) ?: 64,
            streamOutput = streamOutput,
            searchEnabled = searchEnabled
        )
        assistantStore.update(updated)
        assistant = updated
        saved = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(assistant.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { save(); onBack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    TextButton(onClick = { save(); onBack() }) {
                        Text("保存", fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (saved) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("已保存", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("基本设置")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(56.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (avatar.isNotBlank()) avatar.take(1) else name.take(1).uppercase(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        OutlinedButton(onClick = { showAvatarPicker = true }) {
                            Icon(Icons.Outlined.EmojiEmotions, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("设置图标")
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("助手名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("系统提示词")
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        label = { Text("系统提示词") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        maxLines = 10,
                        placeholder = { Text("例如：你是一个有用的助手...") }
                    )
                }
            }

            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("模型设置")
                    Surface(
                        onClick = { showProviderPicker = true },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("供应商", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    selectedProvider?.name ?: "使用全局默认",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (providerId.isNotBlank()) {
                        Surface(
                            onClick = { showModelPicker = true },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("模型", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        modelId.ifBlank { "未选择" },
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = contextSize,
                        onValueChange = { contextSize = it },
                        label = { Text("上下文消息数 (1-1024)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("行为选项")
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("流式输出", style = MaterialTheme.typography.bodyLarge)
                            Text("逐字显示模型回复", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = streamOutput, onCheckedChange = { streamOutput = it })
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("联网搜索", style = MaterialTheme.typography.bodyLarge)
                            Text("允许助手搜索网络", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = searchEnabled, onCheckedChange = { searchEnabled = it })
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("保存更改", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showProviderPicker) {
        AlertDialog(
            onDismissRequest = { showProviderPicker = false },
            title = { Text("选择供应商") },
            text = {
                Column {
                    Surface(
                        onClick = { providerId = ""; modelId = ""; showProviderPicker = false },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("使用全局默认", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (providerId == "") Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    providers.forEach { p ->
                        Surface(
                            onClick = { providerId = p.id; modelId = ""; showProviderPicker = false },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ) {
                            Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(p.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                                if (providerId == p.id) Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showProviderPicker = false }) { Text("取消") } }
        )
    }

    if (showModelPicker) {
        val provider = providers.firstOrNull { it.id == providerId }
        AlertDialog(
            onDismissRequest = { showModelPicker = false },
            title = { Text("选择模型") },
            text = {
                Column {
                    if (provider != null) {
                        val models = provider.models
                        if (models.isEmpty()) {
                            Text("该供应商没有可用模型", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            models.forEach { m ->
                                Surface(
                                    onClick = { modelId = m; showModelPicker = false },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ) {
                                    Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(m, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                        if (modelId == m) Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showModelPicker = false }) { Text("取消") } }
        )
    }

    if (showAvatarPicker) {
        val emojis = listOf("🤖", "👤", "🌟", "🎯", "💡", "⚡", "🔥", "🌈", "🎨", "🚀", "🦊", "🐱", "🐶", "🦄", "🌸", "🍀")
        AlertDialog(
            onDismissRequest = { showAvatarPicker = false },
            title = { Text("选择图标") },
            text = {
                Column {
                    emojis.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { emoji ->
                                Surface(
                                    onClick = { avatar = emoji; showAvatarPicker = false },
                                    shape = CircleShape,
                                    color = if (avatar == emoji) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ) {
                                    Text(emoji, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.headlineSmall)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAvatarPicker = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}