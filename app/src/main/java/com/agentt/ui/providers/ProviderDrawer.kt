package com.agentt.ui.providers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentt.data.model.AIProvider
import com.agentt.data.model.ProviderType
import com.agentt.data.model.defaultModels
import com.agentt.ui.components.IosButton
import com.agentt.ui.components.IosTextField
import com.agentt.ui.theme.*
import com.agentt.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDrawer(viewModel: ChatViewModel, onClose: () -> Unit) {
    val providers by viewModel.providers.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型服务", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                IosButton(
                    text = "添加服务",
                    icon = Icons.Default.Add,
                    onClick = { showAddDialog = true },
                    modifier = Modifier.padding(16.dp),
                    backgroundColor = MaterialTheme.colorScheme.primary
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        if (providers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("尚未添加模型服务", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "当前为界面壳，可先添加占位服务验证交互。",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(providers, key = { it.id }) { provider ->
                    ProviderCard(provider) { viewModel.removeProvider(provider.id) }
                }
            }
        }
    }

    if (showAddDialog) {
        AddProviderDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = {
                viewModel.addProvider(it)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ProviderCard(provider: AIProvider, onDelete: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val cs = MaterialTheme.colorScheme
    val background = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFF7F7F9)
    val accent = providerColor(provider.type)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = background,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).background(accent.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(providerIcon(provider.type), contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(provider.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "${provider.type.displayName} · ${provider.models.size} 个模型",
                    fontSize = 12.sp,
                    color = cs.onSurface.copy(alpha = 0.55f)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "删除", tint = cs.onSurface.copy(alpha = 0.45f))
            }
        }
    }
}

@Composable
fun AddProviderDialog(onDismiss: () -> Unit, onConfirm: (AIProvider) -> Unit) {
    var selectedType by remember { mutableStateOf(ProviderType.OPEN_AI) }
    var name by remember { mutableStateOf(selectedType.displayName) }
    var baseUrl by remember { mutableStateOf(selectedType.defaultBaseUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(14.dp),
        title = { Text("添加模型服务", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProviderType.entries.forEach { type ->
                    Surface(
                        onClick = {
                            selectedType = type
                            name = type.displayName
                            baseUrl = type.defaultBaseUrl
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedType == type) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedType == type, onClick = null)
                            Text(type.displayName, fontSize = 14.sp)
                        }
                    }
                }
                IosTextField(name, { name = it }, "名称", "服务名称")
                IosTextField(baseUrl, { baseUrl = it }, "接口地址", "https://api.example.com/v1")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        AIProvider(
                            name = name.ifBlank { selectedType.displayName },
                            type = selectedType,
                            baseUrl = baseUrl,
                            models = selectedType.defaultModels()
                        )
                    )
                }
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun providerColor(type: ProviderType): Color = when (type) {
    ProviderType.ANTHROPIC -> ProviderAnthropic
    ProviderType.OPEN_AI -> ProviderOpenAI
    ProviderType.GOOGLE -> ProviderGoogle
    ProviderType.OPEN_ROUTER -> ProviderOpenRouter
    ProviderType.CUSTOM -> ProviderCustom
}

private fun providerIcon(type: ProviderType): ImageVector = when (type) {
    ProviderType.ANTHROPIC -> Icons.Default.Psychology
    ProviderType.OPEN_AI -> Icons.Default.AutoAwesome
    ProviderType.GOOGLE -> Icons.Default.Cloud
    ProviderType.OPEN_ROUTER -> Icons.Default.Hub
    ProviderType.CUSTOM -> Icons.Default.Dns
}
