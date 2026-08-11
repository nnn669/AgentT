package com.agentt.app.ui.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agentt.app.ui.chat.AgentTag
import com.agentt.app.ui.chat.AssistantStore
import com.agentt.app.ui.chat.TagStore
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManagerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val tagStore = remember { TagStore.from(context) }
    val assistantStore = remember { AssistantStore.from(context) }
    var tags by remember { mutableStateOf(tagStore.loadTags()) }
    var assignment by remember { mutableStateOf(tagStore.loadAssignment()) }
    var assistants by remember { mutableStateOf(assistantStore.load()) }
    var showAddTag by remember { mutableStateOf(false) }
    var showRenameTag by remember { mutableStateOf<AgentTag?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<AgentTag?>(null) }
    var showAssignSheet by remember { mutableStateOf<AgentTag?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("标签管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("${tags.size} 个标签", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddTag = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "新建标签", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        if (tags.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Outlined.Bookmark, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f))
                    Spacer(Modifier.height(18.dp))
                    Text("还没有标签", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
                    Spacer(Modifier.height(8.dp))
                    Text("点击右上角 + 创建第一个标签，用于助手分组", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tags, key = { it.id }) { tag ->
                    TagCard(
                        tag = tag,
                        count = assignment.values.count { it == tag.id },
                        onRename = { showRenameTag = tag },
                        onDelete = { showDeleteConfirm = tag },
                        onAssign = { showAssignSheet = tag }
                    )
                }
            }
        }
    }

    if (showAddTag) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddTag = false },
            title = { Text("新建标签") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("标签名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        tagStore.createTag(name.trim())
                        tags = tagStore.loadTags()
                        showAddTag = false
                    }
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showAddTag = false }) { Text("取消") } }
        )
    }

    showRenameTag?.let { tag ->
        var name by remember { mutableStateOf(tag.name) }
        AlertDialog(
            onDismissRequest = { showRenameTag = null },
            title = { Text("重命名标签") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("标签名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        tagStore.renameTag(tag.id, name.trim())
                        tags = tagStore.loadTags()
                        showRenameTag = null
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showRenameTag = null }) { Text("取消") } }
        )
    }

    showDeleteConfirm?.let { tag ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("删除标签") },
            text = { Text("确定要删除「${tag.name}」吗？相关助手的标签将被清除。") },
            confirmButton = {
                TextButton(onClick = {
                    tagStore.deleteTag(tag.id)
                    tags = tagStore.loadTags()
                    assignment = tagStore.loadAssignment()
                    showDeleteConfirm = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") } }
        )
    }

    showAssignSheet?.let { tag ->
        val assignedIds = assignment.filter { it.value == tag.id }.keys
        AlertDialog(
            onDismissRequest = { showAssignSheet = null },
            title = { Text("分配到「${tag.name}」") },
            text = {
                Column {
                    assistants.forEach { a ->
                        val isAssigned = a.id in assignedIds
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isAssigned,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        tagStore.assignAssistant(a.id, tag.id)
                                    } else {
                                        tagStore.assignAssistant(a.id, null)
                                    }
                                    assignment = tagStore.loadAssignment()
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(a.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAssignSheet = null }) { Text("完成") } }
        )
    }
}

@Composable
private fun TagCard(
    tag: AgentTag,
    count: Int,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAssign: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cs.surfaceVariant.copy(alpha = 0.25f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Bookmark, contentDescription = null, tint = cs.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tag.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("$count 个助手", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            OutlinedButton(onClick = onAssign, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("分配", style = MaterialTheme.typography.labelMedium)
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "更多", tint = cs.onSurfaceVariant)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = { showMenu = false; onRename() },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) }
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