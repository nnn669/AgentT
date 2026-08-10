package com.agentt.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentt.ui.chat.ChatListScreen
import com.agentt.ui.chat.ChatScreen
import com.agentt.ui.providers.ProviderDrawer
import com.agentt.ui.settings.SettingsScreen
import com.agentt.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: ChatViewModel = viewModel()
) {
    val selectedSessionId by viewModel.selectedSessionId.collectAsState()

    // 抽屉状态
    var isLeftDrawerOpen by remember { mutableStateOf(false) }
    var isRightDrawerOpen by remember { mutableStateOf(false) }

    // 手势拖拽偏移
    var dragOffset by remember { mutableStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 主内容
        if (selectedSessionId != null) {
            // 聊天界面
            ChatScreen(
                viewModel = viewModel,
                onBack = { viewModel.backToSessionList() },
                onOpenDrawer = { isLeftDrawerOpen = true },
                onOpenSettings = { isRightDrawerOpen = true }
            )
        } else {
            // 会话列表（主界面）
            ChatListScreen(
                viewModel = viewModel,
                onOpenDrawer = { isLeftDrawerOpen = true },
                onOpenSettings = { isRightDrawerOpen = true }
            )
        }

        // 左侧抽屉遮罩
        if (isLeftDrawerOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { isLeftDrawerOpen = false }
            )
        }

        // 左侧抽屉（供应商管理）
        AnimatedVisibility(
            visible = isLeftDrawerOpen,
            enter = slideInHorizontally(tween(300)) { -it },
            exit = slideOutHorizontally(tween(300)) { -it }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.8f)
                    .align(Alignment.CenterStart)
            ) {
                ProviderDrawer(
                    viewModel = viewModel,
                    onClose = { isLeftDrawerOpen = false }
                )
            }
        }

        // 右侧抽屉遮罩
        if (isRightDrawerOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { isRightDrawerOpen = false }
            )
        }

        // 右侧抽屉（设置页面）
        AnimatedVisibility(
            visible = isRightDrawerOpen,
            enter = slideInHorizontally(tween(300)) { it },
            exit = slideOutHorizontally(tween(300)) { it }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.8f)
                    .align(Alignment.CenterEnd)
            ) {
                SettingsScreen(
                    viewModel = viewModel,
                    onClose = { isRightDrawerOpen = false }
                )
            }
        }
    }
}
