package com.agentt.app.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import java.util.UUID
import kotlinx.coroutines.launch

data class TerminalEntry(
    val id: String = UUID.randomUUID().toString(),
    val command: String,
    val output: String,
    val exitCode: Int? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val backend = remember { LocalTerminalBackend(context.applicationContext) }
    val sessionId = remember { UUID.randomUUID().toString() }
    val entries = remember { mutableStateListOf<TerminalEntry>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }

    fun execute() {
        val command = input.trim()
        if (command.isEmpty() || running) return
        input = ""
        if (command == "clear") {
            entries.clear()
            return
        }
        running = true
        scope.launch {
            val entry = try {
                val result = backend.execute(TerminalRequest(sessionId, command))
                val output = buildString {
                    append(result.stdout)
                    if (result.stderr.isNotBlank()) {
                        if (isNotEmpty() && !endsWith("\n")) append('\n')
                        append(result.stderr)
                    }
                    if (result.timedOut) append("\n[执行超时]")
                    if (result.truncated) append("\n[输出已截断]")
                }.trimEnd()
                TerminalEntry(command = command, output = output, exitCode = result.exitCode)
            } catch (e: Exception) {
                TerminalEntry(command = command, output = e.message ?: "执行失败", exitCode = -1)
            }
            entries += entry
            running = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { backend.cancel(sessionId) }
    }
    LaunchedEffect(entries.size, running) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("终端", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("本地 · ~/terminal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (running) {
                        IconButton(onClick = { backend.cancel(sessionId) }) {
                            Icon(Icons.Outlined.Stop, contentDescription = "停止", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    IconButton(onClick = { entries.clear() }, enabled = entries.isNotEmpty()) {
                        Icon(Icons.Outlined.ClearAll, contentDescription = "清屏")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        enabled = !running,
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Terminal, contentDescription = null) },
                        placeholder = { Text("输入命令") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { execute() })
                    )
                    IconButton(onClick = { execute() }, enabled = input.isNotBlank() && !running) {
                        Icon(Icons.Outlined.Send, contentDescription = "执行", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    ) { padding ->
        if (entries.isEmpty() && !running) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Outlined.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("$ /system/bin/sh", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                Text("工作目录保存在 AgentT 私有空间", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    Column(Modifier.fillMaxWidth()) {
                        Text("$ ${entry.command}", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                        if (entry.output.isNotBlank()) {
                            Text(entry.output, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 6.dp))
                        }
                        entry.exitCode?.takeIf { it != 0 }?.let {
                            Text("exit $it", color = MaterialTheme.colorScheme.error, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}
