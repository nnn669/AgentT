package com.agentt.ui.providers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentt.data.model.AIProvider
import com.agentt.data.model.ProviderType
import com.agentt.ui.components.IosButton
import com.agentt.ui.components.IosTextField
import com.agentt.ui.theme.*
import com.agentt.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDrawer(
    viewModel: ChatViewModel,
    onClose: () -> Unit
) {
    val providers by viewModel.providers.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "AI 供应商",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 已添加的供应商列表
            if (providers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "暂无供应商",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "点击下方按钮添加 AI 供应商",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(providers, key = { it.id }) { provider ->
                        ProviderItem(
                            provider = provider,
                            onDelete = { viewModel.removeProvider(provider.id) }
                        )
                    }
                }
            }

            // 添加按钮
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    IosButton(
                        text = "添加供应商",
                        onClick = { showAddDialog = true }
                    )
                }
            }
        }

        // 添加供应商对话框
        if (showAddDialog) {
            AddProviderDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { provider ->
                    viewModel.addProvider(provider)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
private fun ProviderItem(
    provider: AIProvider,
    onDelete: () -> Unit
) {
    val providerColor = providerColor(provider.type)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 供应商图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(providerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = providerIcon(provider.type),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Text(
                    text = "${provider.type.displayName} · ${provider.models.size} 个模型",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun AddProviderDialog(
    onDismiss: () -> Unit,
    onConfirm: (AIProvider) -> Unit
) {
    var selectedType by remember { mutableStateOf(ProviderType.OPEN_AI) }
    var name by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(14.dp),
        title = {
            Text(
                text = "添加供应商",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 选择供应商类型
                Text(
                    text = "选择类型",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                // 类型选择行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProviderType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = {
                                selectedType = type
                                name = type.displayName
                                baseUrl = type.defaultBaseUrl
                            },
                            label = { Text(type.displayName, fontSize = 13.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                IosTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "名称",
                    placeholder = "供应商名称"
                )

                IosTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = "API Key",
                    placeholder = "sk-..."
                )

                IosTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = "API 地址",
                    placeholder = "https://api.example.com/v1"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val provider = AIProvider(
                        name = name.ifEmpty { selectedType.displayName },
                        type = selectedType,
                        apiKey = apiKey,
                        baseUrl = baseUrl.ifEmpty { selectedType.defaultBaseUrl },
                        models = selectedType.defaultModels()
                    )
                    onConfirm(provider)
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun providerColor(type: ProviderType): Color = when (type) {
    ProviderType.ANTHROPIC -> ProviderAnthropic
    ProviderType.OPEN_AI -> ProviderOpenAI
    ProviderType.GOOGLE -> ProviderGoogle
    ProviderType.OPEN_ROUTER -> ProviderOpenRouter
    ProviderType.CUSTOM -> ProviderCustom
}

private fun providerIcon(type: ProviderType) = when (type) {
    ProviderType.ANTHROPIC -> Icons.Default.Psychology
    ProviderType.OPEN_AI -> Icons.Default.AutoAwesome
    ProviderType.GOOGLE -> Icons.Default.Google
    ProviderType.OPEN_ROUTER -> Icons.Default.Hub
    ProviderType.CUSTOM -> Icons.Default.Dns
}
