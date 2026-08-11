package com.agentt.app.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val baseDir = remember { FileTools.getBaseDir(context) }
    var currentDir by remember { mutableStateOf(baseDir) }
    var entries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var currentPathText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var previewContent by remember { mutableStateOf<String?>(null) }
    var previewFile by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var snackbarHostState by remember { mutableStateOf(SnackbarHostState()) }
    val scope = rememberCoroutineScope()
    val history = remember { mutableStateListOf(baseDir.absolutePath) }

    fun loadDirectory(dir: File) {
        scope.launch {
            isLoading = true
            currentDir = dir
            currentPathText = dir.absolutePath
            val list = withContext(Dispatchers.IO) {
                val files = dir.listFiles()?.sortedWith(
                    compareBy<File> { if (it.isDirectory) 0 else 1 }.thenBy { it.name.lowercase() }
                ) ?: emptyArray()
                files.map { f ->
                    FileEntry(
                        name = f.name,
                        path = f.absolutePath,
                        isDirectory = f.isDirectory,
                        size = if (f.isFile) f.length() else 0,
                        lastModified = f.lastModified()
                    )
                }
            }
            entries = list
            isLoading = false
        }
    }

    fun navigateTo(dir: File) {
        history.add(dir.absolutePath)
        loadDirectory(dir)
    }

    fun navigateBack() {
        if (history.size > 1) {
            history.removeLast()
            val prev = File(history.last())
            loadDirectory(prev)
        }
    }

    fun navigateUp() {
        val parent = currentDir.parentFile
        if (parent != null && parent.absolutePath.startsWith(baseDir.absolutePath)) {
            navigateTo(parent)
        }
    }

    LaunchedEffect(Unit) {
        loadDirectory(baseDir)
    }

    // 预览/删除对话框
    if (previewContent != null && previewFile != null) {
        AlertDialog(
            onDismissRequest = { previewContent = null; previewFile = null },
            title = { Text(previewFile!!, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    item {
                        Text(
                            previewContent!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { previewContent = null; previewFile = null }) {
                    Text("关闭")
                }
            }
        )
    }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除「${showDeleteConfirm}」吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    val path = showDeleteConfirm!!
                    showDeleteConfirm = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            val file = File(path)
                            if (file.isDirectory) file.deleteRecursively() else file.delete()
                            "已删除"
                        }
                        snackbarHostState.showSnackbar(result)
                        loadDirectory(currentDir)
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("文件管理器", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            currentPathText.takeLast(40),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (history.size > 1) navigateBack() else onBack()
                    }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (currentDir.absolutePath != baseDir.absolutePath) {
                        IconButton(onClick = { navigateUp() }) {
                            Icon(Icons.Outlined.ArrowUpward, contentDescription = "上级目录")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.FolderOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("空目录", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(entries, key = { it.path }) { entry ->
                    FileEntryRow(
                        entry = entry,
                        onClick = {
                            if (entry.isDirectory) {
                                navigateTo(File(entry.path))
                            } else {
                                scope.launch {
                                    previewFile = entry.name
                                    previewContent = withContext(Dispatchers.IO) {
                                        try {
                                            val file = File(entry.path)
                                            if (file.length() > 500_000) {
                                                "文件过大 (${formatSize(file.length())})，无法预览"
                                            } else {
                                                file.readText(Charsets.UTF_8).take(10_000)
                                                    .let { if (it.length >= 10_000) "$it\n\n...(截断)" else it }
                                            }
                                        } catch (e: Exception) {
                                            "无法预览: ${e.message ?: e.javaClass.simpleName}"
                                        }
                                    }
                                }
                            }
                        },
                        onDelete = {
                            showDeleteConfirm = entry.path
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FileEntryRow(
    entry: FileEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (entry.isDirectory) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (entry.isDirectory) Icons.Outlined.Folder
                    else getFileIcon(entry.name),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            // Name + details
            Column(Modifier.weight(1f)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row {
                    if (!entry.isDirectory) {
                        Text(
                            formatSize(entry.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("  ·  ", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        formatTime(entry.lastModified),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

private fun getFileIcon(name: String) = when {
    name.endsWith(".txt", ignoreCase = true) || name.endsWith(".md", ignoreCase = true) -> Icons.Outlined.Description
    name.endsWith(".json", ignoreCase = true) || name.endsWith(".xml", ignoreCase = true) -> Icons.Outlined.Code
    name.endsWith(".png", ignoreCase = true) || name.endsWith(".jpg", ignoreCase = true) || name.endsWith(".jpeg", ignoreCase = true) || name.endsWith(".gif", ignoreCase = true) -> Icons.Outlined.Image
    name.endsWith(".apk", ignoreCase = true) -> Icons.Outlined.Android
    name.endsWith(".zip", ignoreCase = true) || name.endsWith(".tar", ignoreCase = true) || name.endsWith(".gz", ignoreCase = true) -> Icons.Outlined.FolderZip
    name.endsWith(".html", ignoreCase = true) || name.endsWith(".htm", ignoreCase = true) -> Icons.Outlined.Language
    name.endsWith(".pdf", ignoreCase = true) -> Icons.Outlined.PictureAsPdf
    name.endsWith(".kt", ignoreCase = true) || name.endsWith(".java", ignoreCase = true) -> Icons.Outlined.Terminal
    else -> Icons.Outlined.InsertDriveFile
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
    bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}

private fun formatTime(millis: Long): String {
    if (millis <= 0) return "-"
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}
