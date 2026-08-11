package com.agentt.app.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.agentt.app.ui.chat.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    onOpenSettings: () -> Unit,
    onOpenChat: (String, String) -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember { ChatStore.from(context.applicationContext) }
    val assistantStore = remember { AssistantStore.from(context.applicationContext) }
    val tagStore = remember { TagStore.from(context.applicationContext) }
    val sessions = remember { mutableStateListOf<ChatSession>().apply { addAll(store.loadSessions()) } }
    val categories = remember { mutableStateListOf<ChatCategory>().apply { addAll(store.loadCategories()) } }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showManageCategories by remember { mutableStateOf(false) }
    var assistants by remember { mutableStateOf(assistantStore.load()) }
    var currentAssistantId by remember { mutableStateOf(assistantStore.currentId()) }
    var showAssistantPicker by remember { mutableStateOf(false) }
    val tags = remember { tagStore.loadTags() }
    val assignment = remember { tagStore.loadAssignment() }
    val collapsedTags = remember { tagStore.loadCollapsed() }

    fun persistSessions() = store.saveSessions(sessions.toList())
    fun persistCategories() = store.saveCategories(categories.toList())

    fun deleteCategory(target: ChatCategory) {
        categories.removeAll { it.id == target.id }
        sessions.indices.forEach { i ->
            if (sessions[i].categoryId == target.id) sessions[i] = sessions[i].copy(categoryId = null)
        }
        if (selectedCategory == target.id) selectedCategory = null
        persistCategories()
        persistSessions()
    }

    val visibleSessions = if (selectedCategory == null) sessions else sessions.filter { it.categoryId == selectedCategory }
    val currentAssistant = assistants.firstOrNull { it.id == currentAssistantId }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("对话管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("工作区", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = onNewChat) {
                        Icon(Icons.Outlined.AddComment, contentDescription = "新建对话", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Surface(
                onClick = { showAssistantPicker = true },
                shape = RoundedCornerShape(0.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(32.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (currentAssistant?.avatar.isNullOrBlank()) (currentAssistant?.name?.take(1) ?: "A").uppercase()
                            else currentAssistant!!.avatar.take(1),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            currentAssistant?.name ?: "默认助手",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (currentAssistant != null) {
                            Text(
                                if (currentAssistant.systemPrompt.isNotBlank()) "自定义提示词" else "默认提示词",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(Icons.Outlined.SwapHoriz, contentDescription = "切换助手", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("全部") }
                    )
                }
                items(categories, key = { it.id }) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat.id,
                        onClick = { selectedCategory = cat.id },
                        label = { Text(cat.name) }
                    )
                }
                item {
                    Surface(
                        onClick = { showManageCategories = true },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = "管理分类", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            if (visibleSessions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f))
                        Spacer(Modifier.height(18.dp))
                        Text("还没有对话", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
                        Spacer(Modifier.height(8.dp))
                        Text("点击右上角 + 开始新对话", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleSessions.sortedByDescending { it.updatedAt }, key = { it.id }) { session ->
                        SessionCard(
                            session = session,
                            categoryName = categories.firstOrNull { it.id == session.categoryId }?.name,
                            onClick = { onOpenChat(session.id, session.title) },
                            onDelete = {
                                val idx = sessions.indexOfFirst { it.id == session.id }
                                if (idx >= 0) {
                                    sessions.removeAt(idx)
                                    persistSessions()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAssistantPicker) {
        AssistantPickerSheet(
            assistants = assistants,
            tags = tags,
            assignment = assignment,
            collapsedTags = collapsedTags,
            currentAssistantId = currentAssistantId,
            onSelect = { id ->
                assistantStore.setCurrentId(id)
                currentAssistantId = id
                assistants = assistantStore.load()
                showAssistantPicker = false
            },
            onDismiss = { showAssistantPicker = false },
            onToggleCollapsed = { tagId ->
                tagStore.toggleCollapsed(tagId)
            }
        )
    }

    if (showManageCategories) {
        CategoryManageDialog(
            categories = categories.toList(),
            onAdd = { name ->
                categories.add(ChatCategory(UUID.randomUUID().toString(), name))
                persistCategories()
            },
            onRename = { cat, name ->
                val idx = categories.indexOfFirst { it.id == cat.id }
                if (idx >= 0) {
                    categories[idx] = cat.copy(name = name)
                    persistCategories()
                }
            },
            onDelete = { deleteCategory(it) },
            onDismiss = { showManageCategories = false }
        )
    }
}

@Composable
private fun AssistantPickerSheet(
    assistants: List<AgentAssistant>,
    tags: List<AgentTag>,
    assignment: Map<String, String>,
    collapsedTags: Map<String, Boolean>,
    currentAssistantId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onToggleCollapsed: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Face, contentDescription = null, tint = cs.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("选择助手")
            }
        },
        text = {
            Column {
                tags.forEach { tag ->
                    val tagAssistants = assistants.filter { assignment[it.id] == tag.id }
                    if (tagAssistants.isNotEmpty()) {
                        val isCollapsed = collapsedTags[tag.id] ?: false
                        Surface(
                            onClick = { onToggleCollapsed(tag.id) },
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isCollapsed) Icons.Outlined.ChevronRight else Icons.Outlined.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = cs.primary
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Outlined.Bookmark, contentDescription = null, modifier = Modifier.size(14.dp), tint = cs.primary)
                                Spacer(Modifier.width(4.dp))
                                Text(tag.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = cs.primary)
                            }
                        }
                        if (!isCollapsed) {
                            tagAssistants.forEach { a ->
                                AssistantPickerRow(a, a.id == currentAssistantId, onSelect)
                            }
                        }
                    }
                }
                val unassigned = assistants.filter { !assignment.containsKey(it.id) }
                if (unassigned.isNotEmpty()) {
                    if (tags.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))
                        Text("未分组", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant)
                    }
                    unassigned.forEach { a ->
                        AssistantPickerRow(a, a.id == currentAssistantId, onSelect)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun AssistantPickerRow(
    assistant: AgentAssistant,
    isCurrent: Boolean,
    onSelect: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = { onSelect(assistant.id) },
        shape = RoundedCornerShape(10.dp),
        color = if (isCurrent) cs.primaryContainer.copy(alpha = 0.3f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape)
                    .background(if (isCurrent) cs.primary.copy(alpha = 0.2f) else cs.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (assistant.avatar.isNotBlank()) assistant.avatar.take(1) else assistant.name.take(1).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) cs.primary else cs.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                assistant.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isCurrent) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = cs.primary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: ChatSession,
    categoryName: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(session.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(formatTime(session.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    (categoryName ?: "未分类") + " · 暂无消息",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CategoryManageDialog(
    categories: List<ChatCategory>,
    onAdd: (String) -> Unit,
    onRename: (ChatCategory, String) -> Unit,
    onDelete: (ChatCategory) -> Unit,
    onDismiss: () -> Unit
) {
    var showInput by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ChatCategory?>(null) }
    var inputName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理分类") },
        text = {
            Column {
                if (categories.isEmpty() && !showInput) {
                    Text("还没有分类，点击下方添加", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                }
                categories.forEach { cat ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(cat.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { renameTarget = cat; inputName = cat.name; showInput = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Edit, contentDescription = "重命名", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onDelete(cat) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (showInput) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = inputName,
                            onValueChange = { inputName = it },
                            label = { Text(if (renameTarget == null) "新分类名称" else "重命名") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val name = inputName.trim()
                                if (name.isNotEmpty()) {
                                    if (renameTarget != null) onRename(renameTarget!!, name) else onAdd(name)
                                    showInput = false
                                    renameTarget = null
                                    inputName = ""
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.Check, contentDescription = "确认")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { showInput = true; renameTarget = null; inputName = "" }) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("添加分类")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

private fun formatTime(ts: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))