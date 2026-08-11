package com.agentt.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SandboxEnvironmentScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { SandboxEnvironmentStore.from(context) }
    var variables by remember { mutableStateOf(store.variables()) }
    var privacyMode by rememberSaveable { mutableStateOf(store.privacyMode) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val revealed = remember { mutableStateMapOf<String, Boolean>() }
    val clipboard = LocalClipboardManager.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("沙盒环境", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "返回") } },
                actions = { IconButton(onClick = { adding = true }) { Icon(Icons.Outlined.Add, "添加变量") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionLabel("隐私") }
            item {
                Card(shape = RoundedCornerShape(8.dp), colors = sandboxCardColors()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("隐私模式", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "发送给模型的工具输出会遮罩已保存的变量值",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Switch(checked = privacyMode, onCheckedChange = {
                            privacyMode = it
                            store.privacyMode = it
                        })
                    }
                }
            }
            item {
                Text(
                    "变量仅保存在本机加密存储中，并在每次沙盒 shell 启动时自动注入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(4.dp)
                )
            }
            item { SectionLabel("变量") }
            if (variables.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("暂无环境变量", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { adding = true }, modifier = Modifier.padding(top = 12.dp)) {
                            Icon(Icons.Outlined.Add, null)
                            Text("添加变量", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            } else {
                items(variables, key = SandboxVariable::name) { variable ->
                    Card(shape = RoundedCornerShape(8.dp), colors = sandboxCardColors()) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(variable.name, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                                Text(
                                    if (revealed[variable.name] == true) variable.value else SandboxVariableRules.mask(variable.value),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                if (variable.description.isNotBlank()) {
                                    Text(
                                        variable.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                            IconButton(onClick = { revealed[variable.name] = revealed[variable.name] != true }) {
                                Icon(
                                    if (revealed[variable.name] == true) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    if (revealed[variable.name] == true) "隐藏" else "显示"
                                )
                            }
                            IconButton(onClick = { clipboard.setText(AnnotatedString(variable.value)) }) {
                                Icon(Icons.Outlined.ContentCopy, "复制")
                            }
                            IconButton(onClick = {
                                store.delete(variable.name)
                                revealed.remove(variable.name)
                                variables = store.variables()
                            }) { Icon(Icons.Outlined.Delete, "删除") }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        AddVariableDialog(
            onDismiss = { adding = false },
            onSave = { name, value, description ->
                store.put(name, value, description)
                variables = store.variables()
                adding = false
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

@Composable
private fun sandboxCardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
)

@Composable
private fun AddVariableDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var value by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var revealed by rememberSaveable { mutableStateOf(false) }
    val normalized = SandboxVariableRules.normalizeName(name)
    val nameError = name.isNotEmpty() && !SandboxVariableRules.isValidName(normalized)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加环境变量") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.uppercase() },
                    label = { Text("名称") },
                    placeholder = { Text("GH_TOKEN") },
                    isError = nameError,
                    supportingText = { if (nameError) Text("使用大写字母、数字和下划线，且不能以数字开头") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("值") },
                    visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { revealed = !revealed }) {
                            Icon(
                                if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                if (revealed) "隐藏" else "显示"
                            )
                        }
                    },
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("说明（可选）") },
                    placeholder = { Text("GitHub 令牌") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(normalized, value, description) },
                enabled = SandboxVariableRules.isValidName(normalized) && value.isNotEmpty()
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}