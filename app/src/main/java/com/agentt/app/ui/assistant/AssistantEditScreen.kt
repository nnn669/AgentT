package com.agentt.app.ui.assistant

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
                        label = { Text("提示词") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        maxLines = 10,
                        placeholder = { Text("自定义系统提示词，留空使用默认") }
                    )
                }
            }

            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("模型配置")
                    SettingClickableRow(
                        icon = Icons.Outlined.Dns,
                        title = "供应商",
                        subtitle = selectedProvider?.name ?: "使用全局默认"
                    ) { showProviderPicker = true }

                    if (selectedProvider != null) {
                        SettingClickableRow(
                            icon = Icons.Outlined.SmartToy,
                            title = "模型",
                            subtitle = modelId.ifBlank { "使用全局默认" }
                        ) { showModelPicker = true }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("流式输出", style = MaterialTheme.typography.bodyLarge)
                            Text("逐字输出回复", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = streamOutput, onCheckedChange = { streamOutput = it })
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("联网搜索", style = MaterialTheme.typography.bodyLarge)
                            Text("允许助手搜索互联网", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = searchEnabled, onCheckedChange = { searchEnabled = it })
                    }
                }
            }

            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("高级设置")
                    OutlinedTextField(
                        value = contextSize,
                        onValueChange = { contextSize = it },
                        label = { Text("上下文消息数 (1-1024)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text("包含在上下文中的历史消息数") }
                    )
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
                                    Text(p.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (p.id == providerId) Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showProviderPicker = false }) { Text("取消") } }
        )
    }

    if (showModelPicker && selectedProvider != null) {
        AlertDialog(
            onDismissRequest = { showModelPicker = false },
            title = { Text("选择模型") },
            text = {
                Column {
                    Surface(
                        onClick = { modelId = ""; showModelPicker = false },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("使用默认模型", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (modelId == "") Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    selectedProvider.models.forEach { m ->
                        Surface(
                            onClick = { modelId = m; showModelPicker = false },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ) {
                            Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(m, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                if (m == modelId) Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showModelPicker = false }) { Text("取消") } }
        )
    }

    if (showAvatarPicker) {
        var emojiInput by remember { mutableStateOf(avatar) }
        AlertDialog(
            onDismissRequest = { showAvatarPicker = false },
            title = { Text("设置图标") },
            text = {
                Column {
                    Text("输入一个 Emoji 作为图标，留空使用首字母", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = emojiInput,
                        onValueChange = { emojiInput = it.take(1) },
                        label = { Text("Emoji 或字母") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (emojiInput.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(emojiInput, style = MaterialTheme.typography.headlineLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    avatar = emojiInput
                    showAvatarPicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showAvatarPicker = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SettingClickableRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        }
    }
}