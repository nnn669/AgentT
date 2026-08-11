package com.agentt.app.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agentt.app.ui.chat.AgentAssistant
import com.agentt.app.ui.chat.AssistantStore
import com.agentt.app.ui.chat.TagStore
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantsScreen(
    onBack: () -> Unit,
    onEditAssistant: (String) -> Unit,
    onManageTags: () -> Unit
) {
    val context = LocalContext.current
    val assistantStore = remember { AssistantStore.from(context) }
    val tagStore = remember { TagStore.from(context) }
    var assistants by remember { mutableStateOf(assistantStore.load()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("助手管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("${assistants.size} 个助手", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "新建助手", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = onManageTags) {
                        Icon(Icons.Outlined.Bookmark, contentDescription = "管理标签", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        if (assistants.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Outlined.SmartToy, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f))
                    Spacer(Modifier.height(18.dp))
                    Text("还没有助手", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
                    Spacer(Modifier.height(8.dp))
                    Text("点击右上角 + 创建第一个助手", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(assistants, key = { it.id }) { assistant ->
                    val currentId = assistantStore.currentId()
                    val isCurrent = assistant.id == currentId
                    AssistantCard(
                        assistant = assistant,
                        isCurrent = isCurrent,
                        tagName = tagStore.tagOfAssistant(assistant.id)?.let { tid ->
                            tagStore.loadTags().firstOrNull { it.id == tid }?.name
                        },
                        onTap = { onEditAssistant(assistant.id) },
                        onSetCurrent = {
                            assistantStore.setCurrentId(assistant.id)
                            assistants = assistantStore.load()
                        },
                        onDuplicate = {
                            assistantStore.duplicate(assistant.id)
                            assistants = assistantStore.load()
                        },
                        onDelete = { showDeleteConfirm = assistant.id }
                    )
                }
            }
        }
    }

    // Add dialog
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("新建助手") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("助手名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        val a = AgentAssistant(
                            id = UUID.randomUUID().toString(),
                            name = name.trim(),
                            order = assistants.size
                        )
                        assistantStore.add(a)
                        assistants = assistantStore.load()
                        showAddDialog = false
                    }
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("取消") } }
        )
    }

    // Delete confirm
    showDeleteConfirm?.let { deleteId ->
        val target = assistants.firstOrNull { it.id == deleteId }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("删除助手") },
            text = { Text("确定要删除「${target?.name ?: ""}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    assistantStore.delete(deleteId)
                    assistants = assistantStore.load()
                    showDeleteConfirm = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun AssistantCard(
    assistant: AgentAssistant,
    isCurrent: Boolean,
    tagName: String?,
    onTap: () -> Unit,
    onSetCurrent: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    Surface(
        onClick = onTap,
        shape = RoundedCornerShape(14.dp),
        color = if (isCurrent) cs.primaryContainer.copy(alpha = 0.35f) else cs.surfaceVariant.copy(alpha = 0.25f),
        border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.dp, cs.primary.copy(alpha = 0.3f)) else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape)
                    .background(if (isCurrent) cs.primary.copy(alpha = 0.2f) else cs.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (assistant.avatar.isNotBlank()) assistant.avatar.take(1) else assistant.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = cs.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(assistant.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (isCurrent) {
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(4.dp), color = cs.primary.copy(alpha = 0.15f)) {
                            Text("当前", style = MaterialTheme.typography.labelSmall, color = cs.primary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        if (assistant.systemPrompt.isNotBlank()) append(assistant.systemPrompt.take(60).replace('\n', ' '))
                        else append("无自定义提示词")
                        if (tagName != null) append(" · $tagName")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "更多", tint = cs.onSurfaceVariant)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("设为首选") },
                        onClick = { showMenu = false; onSetCurrent() },
                        leadingIcon = { Icon(Icons.Outlined.Check, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("复制") },
                        onClick = { showMenu = false; onDuplicate() },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = cs.error) },
                        onClick = { showMenu = false; onDelete() },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = cs.error) }
                    )
                }
            }
        }
    }
}