package com.agentt.app.ui.memory

import androidx.compose.animation.AnimatedVisibility
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
import com.agentt.app.ui.chat.AssistantStore
import com.agentt.app.ui.chat.TagStore
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val memoryStore = remember { MemoryStore.from(context) }
    val tagStore = remember { TagStore.from(context) }
    val assistantStore = remember { AssistantStore.from(context.applicationContext) }

    var memories by remember { mutableStateOf(memoryStore.loadSorted()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<AgentMemory?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf("全部") }

    val tags = remember { tagStore.loadTags() }
    val assistants = remember { assistantStore.load() }

    // 分组：按标签分组 + 未分组
    val grouped: Map<String, List<AgentMemory>> = remember(memories, selectedFilter) {
        val filtered = when (selectedFilter) {
            "全部" -> memories
            "全局" -> memories.filter { it.assistantId.isEmpty() && it.tagId.isEmpty() }
            else -> {
                val tag = tags.firstOrNull { it.name == selectedFilter }
                if (tag != null) memories.filter { it.tagId == tag.id }
                else memories
            }
        }
        val tagNames = tags.associateBy { it.id }
        val global = mutableListOf<AgentMemory>()
        val byTag = mutableMapOf<String, MutableList<AgentMemory>>()
        val byAssistant = mutableMapOf<String, MutableList<AgentMemory>>()

        for (m in filtered) {
            if (m.tagId.isNotEmpty() && tagNames.containsKey(m.tagId)) {
                byTag.getOrPut(m.tagId) { mutableListOf() }.add(m)
            } else if (m.assistantId.isNotEmpty()) {
                byAssistant.getOrPut(m.assistantId) { mutableListOf() }.add(m)
            } else {
                global.add(m)
            }
        }
        val result = mutableMapOf<String, List<AgentMemory>>()
        tags.forEach { tag ->
            byTag[tag.id]?.let { result[tag.name] = it }
        }
        byAssistant.forEach { (aid, list) ->
            val name = assistants.firstOrNull { it.id == aid }?.name ?: "助手"
            result["📎 $name"] = list
        }
        if (global.isNotEmpty()) result["🌐 全局"] = global
        result
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("记忆管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        val total = memories.size
                        if (total > 0) {
                            Text("$total 条记忆", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "新建记忆", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 过滤标签栏
            val filterOptions = buildList {
                add("全部")
                addAll(tags.map { it.name })
                if (memories.any { it.assistantId.isEmpty() && it.tagId.isEmpty() }) {
                    add("全局")
                }
            }
            if (filterOptions.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filterOptions.forEach { option ->
                        FilterChip(
                            selected = selectedFilter == option,
                            onClick = { selectedFilter = option },
                            label = { Text(option, style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = if (selectedFilter == option) {
                                { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }

            if (memories.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Outlined.Psychology, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f))
                        Spacer(Modifier.height(18.dp))
                        Text("还没有记忆", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
                        Spacer(Modifier.height(8.dp))
                        Text("点击右上角 + 创建第一条记忆，助手会在对话中自动参考", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    grouped.forEach { (groupName, groupMemories) ->
                        item {
                            Text(
                                groupName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(groupMemories, key = { it.id }) { memory ->
                            MemoryCard(
                                memory = memory,
                                tags = tags,
                                assistants = assistants,
                                onEdit = { editingMemory = memory },
                                onDelete = { showDeleteConfirm = memory.id }
                            )
                        }
                    }
                }
            }
        }
    }

    // 添加/编辑对话框
    if (showAddDialog || editingMemory != null) {
        val isEdit = editingMemory != null
        val current = editingMemory
        var content by remember(current) { mutableStateOf(current?.content ?: "") }
        var selectedTagId by remember(current) { mutableStateOf(current?.tagId ?: "") }
        var selectedAssistantId by remember(current) { mutableStateOf(current?.assistantId ?: "") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false; editingMemory = null },
            title = { Text(if (isEdit) "编辑记忆" else "新建记忆") },
            text = {
                Column {
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("记忆内容") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        maxLines = 6
                    )
                    Spacer(Modifier.height(12.dp))
                    // 标签选择
                    if (tags.isNotEmpty()) {
                        Text("关联标签", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            tags.forEach { tag ->
                                FilterChip(
                                    selected = selectedTagId == tag.id,
                                    onClick = {
                                        selectedTagId = if (selectedTagId == tag.id) "" else tag.id
                                        if (selectedTagId.isNotEmpty()) selectedAssistantId = ""
                                    },
                                    label = { Text(tag.name, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    // 助手选择
                    if (assistants.isNotEmpty()) {
                        Text("关联助手", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        assistants.take(5).forEach { a ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedAssistantId == a.id,
                                    onClick = {
                                        selectedAssistantId = if (selectedAssistantId == a.id) "" else a.id
                                        if (selectedAssistantId.isNotEmpty()) selectedTagId = ""
                                    }
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(a.name, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (content.isNotBlank()) {
                        if (isEdit && current != null) {
                            memoryStore.update(current.copy(
                                content = content.trim(),
                                tagId = selectedTagId,
                                assistantId = selectedAssistantId
                            ))
                        } else {
                            memoryStore.add(AgentMemory(
                                id = UUID.randomUUID().toString(),
                                content = content.trim(),
                                tagId = selectedTagId,
                                assistantId = selectedAssistantId
                            ))
                        }
                        memories = memoryStore.loadSorted()
                        showAddDialog = false
                        editingMemory = null
                    }
                }) { Text(if (isEdit) "保存" else "创建") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false; editingMemory = null }) { Text("取消") } }
        )
    }

    // 删除确认
    showDeleteConfirm?.let { deleteId ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("删除记忆") },
            text = { Text("确定要删除这条记忆吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    memoryStore.delete(deleteId)
                    memories = memoryStore.loadSorted()
                    showDeleteConfirm = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun MemoryCard(
    memory: AgentMemory,
    tags: List<com.agentt.app.ui.chat.AgentTag>,
    assistants: List<com.agentt.app.ui.chat.AgentAssistant>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    Surface(
        onClick = onEdit,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                memory.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 关联标签
                if (memory.tagId.isNotEmpty()) {
                    val tag = tags.firstOrNull { it.id == memory.tagId }
                    if (tag != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                tag.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                }
                // 关联助手
                if (memory.assistantId.isNotEmpty()) {
                    val assistant = assistants.firstOrNull { it.id == memory.assistantId }
                    if (assistant != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                assistant.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    dateFormat.format(Date(memory.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}