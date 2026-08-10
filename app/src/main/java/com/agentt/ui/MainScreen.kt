package com.agentt.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentt.ui.chat.ChatListScreen
import com.agentt.ui.chat.ChatScreen
import com.agentt.ui.providers.ProviderDrawer
import com.agentt.ui.settings.SettingsScreen
import com.agentt.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: ChatViewModel = viewModel()) {
    val selectedSessionId by viewModel.selectedSessionId.collectAsState()
    var leftDrawerOpen by remember { mutableStateOf(false) }
    var rightDrawerOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (selectedSessionId == null) {
            ChatListScreen(
                viewModel = viewModel,
                onOpenDrawer = { leftDrawerOpen = true },
                onOpenSettings = { rightDrawerOpen = true }
            )
        } else {
            ChatScreen(
                viewModel = viewModel,
                onBack = viewModel::backToSessionList,
                onOpenDrawer = { leftDrawerOpen = true }
            )
        }

        if (leftDrawerOpen || rightDrawerOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable {
                        leftDrawerOpen = false
                        rightDrawerOpen = false
                    }
            )
        }

        AnimatedVisibility(
            visible = leftDrawerOpen,
            enter = slideInHorizontally(tween(250)) { -it },
            exit = slideOutHorizontally(tween(250)) { -it },
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.82f)
            ) {
                ProviderDrawer(
                    viewModel = viewModel,
                    onClose = { leftDrawerOpen = false }
                )
            }
        }

        AnimatedVisibility(
            visible = rightDrawerOpen,
            enter = slideInHorizontally(tween(250)) { it },
            exit = slideOutHorizontally(tween(250)) { it },
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.82f)
            ) {
                SettingsScreen(
                    viewModel = viewModel,
                    onClose = { rightDrawerOpen = false }
                )
            }
        }
    }
}
