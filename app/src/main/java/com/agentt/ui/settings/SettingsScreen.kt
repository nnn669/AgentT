package com.agentt.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentt.ui.components.IosSwitch
import com.agentt.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ChatViewModel, onClose: () -> Unit) {
    var followSystem by remember { mutableStateOf(true) }
    var compactMode by remember { mutableStateOf(false) }
    var haptics by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            SettingsGroup("外观") {
                IosSwitch(followSystem, { followSystem = it }, label = "跟随系统主题", description = "自动切换浅色与深色")
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                IosSwitch(compactMode, { compactMode = it }, label = "紧凑布局", description = "减少列表和消息间距")
            }

            SettingsGroup("交互") {
                IosSwitch(haptics, { haptics = it }, label = "触感反馈", description = "按钮操作时提供轻触反馈")
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                SettingsRow(Icons.Default.Tune, "聊天偏好", "模型、上下文与回复设置")
            }

            SettingsGroup("数据") {
                SettingsRow(Icons.Default.Backup, "导出数据", "会话、服务和设置")
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                SettingsRow(Icons.Default.DeleteSweep, "清除本地数据", "当前壳仅使用临时演示数据", destructive = true)
            }

            SettingsGroup("关于") {
                SettingsRow(Icons.Default.Info, "AgentT", "TIN 风格 Android 界面壳")
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    val isDark = isSystemInDarkTheme()
    val cs = MaterialTheme.colorScheme
    val background = if (isDark) Color.White.copy(alpha = 0.10f) else Color(0xFFF7F7F9)

    Column(modifier = Modifier.padding(top = 18.dp)) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onSurface.copy(alpha = 0.55f)
        )
        Surface(
            modifier = Modifier.padding(horizontal = 12.dp),
            shape = RoundedCornerShape(14.dp),
            color = background,
            border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.16f))
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp)) { content() }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    destructive: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val tint = if (destructive) cs.error else cs.primary
    Row(
        modifier = Modifier.fillMaxWidth().clickable { }.padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = if (destructive) cs.error else cs.onSurface)
            Text(subtitle, fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.5f))
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = cs.onSurface.copy(alpha = 0.3f))
    }
}
