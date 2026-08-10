package com.agentt.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Bot
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(modifier = modifier.fillMaxWidth(0.86f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "A",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "AgentT",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "原生 Android Agent 应用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        LazyColumn(modifier = Modifier.weight(1f)) {
            item { SectionHeader("模型与服务") }
            item {
                SettingRow(
                    icon = Icons.Outlined.Dns,
                    title = "供应商",
                    subtitle = "管理 API 供应商与密钥",
                    onClick = {},
                )
            }
            item {
                SettingRow(
                    icon = Icons.Outlined.SmartToy,
                    title = "默认模型",
                    subtitle = "选择默认对话模型",
                    onClick = {},
                )
            }
            item {
                SettingRow(
                    icon = Icons.Outlined.Search,
                    title = "搜索服务",
                    subtitle = "配置联网搜索",
                    onClick = {},
                )
            }
            item {
                SettingRow(
                    icon = Icons.Outlined.RecordVoiceOver,
                    title = "语音服务",
                    subtitle = "语音合成与识别",
                    onClick = {},
                )
            }
            item {
                SettingRow(
                    icon = Icons.Outlined.Extension,
                    title = "MCP",
                    subtitle = "模型上下文协议服务",
                    onClick = {},
                )
            }
            item {
                SettingRow(
                    icon = Icons.Outlined.Bolt,
                    title = "快捷短语",
                    subtitle = "常用回复快捷输入",
                    onClick = {},
                )
            }
            item {
                SettingRow(
                    icon = Icons.Outlined.Code,
                    title = "指令注入",
                    subtitle = "自定义系统指令",
                    onClick = {},
                )
            }

            item { SectionHeader("通用设置") }
            item {
                SettingRow(
                    icon = Icons.Outlined.Tune,
                    title = "偏好设置",
                    subtitle = "外观、行为与交互偏好",
                    onClick = {},
                )
            }
            item {
                SettingRow(
                    icon = Icons.Outlined.Bot,
                    title = "助手",
                    subtitle = "默认助手与对话风格",
                    onClick = {},
                )
            }
            item {
                SettingRow(
                    icon = Icons.Outlined.DarkMode,
                    title = "颜色模式",
                    subtitle = if (darkTheme) "深色" else "浅色",
                    onClick = onToggleDarkTheme,
                    trailing = {
                        Switch(checked = darkTheme, onCheckedChange = { onToggleDarkTheme() })
                    },
                )
            }

            item { SectionHeader("数据设置") }
            item {
                SettingRow(
                    icon = Icons.Outlined.Backup,
                    title = "数据备份",
                    subtitle = "备份与恢复聊天记录",
                    onClick = {},
                )
            }
            item {
                SettingRow(
                    icon = Icons.Outlined.Storage,
                    title = "聊天记录存储",
                    subtitle = "存储位置与空间管理",
                    onClick = {},
                )
            }

            item { SectionHeader("关于") }
            item {
                SettingRow(
                    icon = Icons.Outlined.Info,
                    title = "关于",
                    subtitle = "版本与许可",
                    onClick = {},
                )
            }
            item {
                SettingRow(
                    icon = Icons.Outlined.BarChart,
                    title = "统计",
                    subtitle = "对话与用量统计",
                    onClick = {},
                )
            }
            item {
                SettingRow(
                    icon = Icons.Outlined.MenuBook,
                    title = "使用文档",
                    subtitle = "查看使用说明",
                    onClick = {},
                )
            }
            item {
                SettingRow(
                    icon = Icons.Outlined.ListAlt,
                    title = "日志",
                    subtitle = "查看运行日志",
                    onClick = {},
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trailing != null) {
                trailing()
            } else {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
    )
}
