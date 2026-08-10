package com.agentt.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.agentt.app.ui.chat.ChatScreen
import com.agentt.app.ui.chat.ChatStore
import com.agentt.app.ui.chat.createChatSession
import com.agentt.app.ui.providers.ProvidersScreen
import com.agentt.app.ui.settings.SettingsDrawer
import com.agentt.app.ui.theme.AgentTTheme
import com.agentt.app.ui.workspace.WorkspaceScreen
import kotlinx.coroutines.launch

enum class Screen { Workspace, Chat, Providers }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var darkTheme by rememberSaveable { mutableStateOf(false) }
            AgentTTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var screen by rememberSaveable { mutableStateOf(Screen.Workspace) }
                    var chatSessionId by rememberSaveable { mutableStateOf<String?>(null) }
                    var chatTitle by rememberSaveable { mutableStateOf("") }

                    fun openChat(sessionId: String, title: String) {
                        chatSessionId = sessionId
                        chatTitle = title
                        screen = Screen.Chat
                    }

                    when (screen) {
                        Screen.Workspace -> AppRoot(
                            darkTheme = darkTheme,
                            onToggleDarkTheme = { darkTheme = !darkTheme },
                            onOpenProviders = { screen = Screen.Providers },
                            onOpenChat = { id, title -> openChat(id, title) },
                            onNewChat = {
                                val s = createChatSession(ChatStore.from(this@MainActivity))
                                openChat(s.id, s.title)
                            }
                        )
                        Screen.Chat -> ChatScreen(
                            title = chatTitle,
                            onBack = { screen = Screen.Workspace },
                            onNewChat = {
                                val s = createChatSession(ChatStore.from(this@MainActivity))
                                chatSessionId = s.id
                                chatTitle = s.title
                            },
                            onOpenTerminal = { Toast.makeText(this@MainActivity, "终端暂未开放", Toast.LENGTH_SHORT).show() },
                            onOpenBrowser = { Toast.makeText(this@MainActivity, "浏览器暂未开放", Toast.LENGTH_SHORT).show() }
                        )
                        Screen.Providers -> ProvidersScreen(onBack = { screen = Screen.Workspace })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    darkTheme: Boolean,
    onToggleDarkTheme: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenChat: (String, String) -> Unit,
    onNewChat: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SettingsDrawer(
                darkTheme = darkTheme,
                onToggleDarkTheme = onToggleDarkTheme,
                onOpenProviders = {
                    scope.launch { drawerState.close() }
                    onOpenProviders()
                }
            )
        }
    ) {
        WorkspaceScreen(
            onOpenSettings = { scope.launch { drawerState.open() } },
            onOpenChat = onOpenChat,
            onNewChat = onNewChat
        )
    }
}
