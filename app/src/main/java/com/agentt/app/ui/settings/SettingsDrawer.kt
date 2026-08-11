package com.agentt.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDrawer(
    darkTheme: Boolean,
    onToggleDarkTheme: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenSandboxEnvironment: () -> Unit,
    onOpenAssistants: () -> Unit,
    onOpenMemory: () -> Unit = {},
    onOpenFileBrowser: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(modifier = modifier.fillMaxWidth(0.86f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "A",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("AgentT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "原生 Android Agent 应用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        LazyColumn(modifier = Modifier.weight(1f)) {
            item { SectionHeader("助手与服务") }
            item { SettingRow(Icons.Outlined.Face, "助手管理", "管理助手、标签与分组", onOpenAssistants) }
            item { SettingRow(Icons.Outlined.Dns, "供应商", "管理 API 供应商与密钥", onOpenProviders) }
            item { SettingRow(Icons.Outlined.SmartToy, "默认模型", "选择默认对话模型", {}) }
            item { SettingRow(Icons.Outlined.Search, "搜索服务", "配置联网搜索", {}) }
            item { SettingRow(Icons.Outlined.Extension, "MCP", "模型上下文协议服务", {}) }

            item { SectionHeader("通用设置") }
            item { SettingRow(Icons.Outlined.Security, "沙盒环境", "环境变量与隐私模式", onOpenSandboxEnvironment) }
            item { SettingRow(Icons.Outlined.Tune, "偏好设置", "外观、行为与交互偏好", {}) }
            item {
                SettingRow(
                    Icons.Outlined.DarkMode,
                    "颜色模式",
                    if (darkTheme) "深色" else "浅色",
                    onToggleDarkTheme,
                    trailing = { Switch(checked = darkTheme, onCheckedChange = { onToggleDarkTheme() }) }
                )
            }

            item { SectionHeader("数据设置") }
            item { SettingRow(Icons.Outlined.Folder, "文件管理器", "浏览与管理设备文件", onOpenFileBrowser) }
            item { SettingRow(Icons.Outlined.Psychology, "记忆管理", "管理 AI 记忆，按助手/标签分组", onOpenMemory) }
            item { SettingRow(Icons.Outlined.Backup, "数据备份", "备份与恢复聊天记录", {}) }
            item { SettingRow(Icons.Outlined.Storage, "聊天记录存储", "存储位置与空间管理", {}) }
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (trailing != null) trailing() else Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)
    )
}