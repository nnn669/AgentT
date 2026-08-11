package com.agentt.app

import android.os.Bundle
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
import com.agentt.app.ui.browser.BrowserScreen
import com.agentt.app.ui.chat.ChatScreen
import com.agentt.app.ui.chat.ChatStore
import com.agentt.app.ui.chat.createChatSession
import com.agentt.app.ui.providers.ProvidersScreen
import com.agentt.app.ui.settings.SettingsDrawer
import com.agentt.app.ui.terminal.TerminalScreen
import com.agentt.app.ui.theme.AgentTTheme
import com.agentt.app.ui.web.WebTools
import com.agentt.app.ui.workspace.WorkspaceScreen
import kotlinx.coroutines.launch

enum class Screen { Workspace, Chat, Providers, Browser, Terminal }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebTools.initialize(applicationContext)
        enableEdgeToEdge()
        setContent {
            var darkTheme by rememberSaveable { mutableStateOf(false) }
            AgentTTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var screen by rememberSaveable { mutableStateOf(Screen.Workspace) }
                    var chatSessionId by rememberSaveable { mutableStateOf<String?>(null) }
                    var chatTitle by rememberSaveable { mutableStateOf("") }
                    var browserUrl by rememberSaveable { mutableStateOf("") }
                    fun openChat(sessionId: String, title: String) { chatSessionId = sessionId; chatTitle = title; screen = Screen.Chat }
                    when (screen) {
                        Screen.Workspace -> AppRoot(darkTheme, { darkTheme = !darkTheme }, { screen = Screen.Providers }, { id, title -> openChat(id, title) }, {
                            val s = createChatSession(ChatStore.from(this@MainActivity)); openChat(s.id, s.title)
                        })
                        Screen.Chat -> ChatScreen(chatTitle, chatSessionId, { screen = Screen.Workspace }, {
                            val s = createChatSession(ChatStore.from(this@MainActivity)); chatSessionId = s.id; chatTitle = s.title
                        }, { screen = Screen.Terminal }, { url -> browserUrl = url ?: ""; screen = Screen.Browser })
                        Screen.Providers -> ProvidersScreen(onBack = { screen = Screen.Workspace })
                        Screen.Browser -> BrowserScreen(initialUrl = browserUrl, onBack = { screen = Screen.Chat })
                        Screen.Terminal -> TerminalScreen(onBack = { screen = Screen.Chat })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(darkTheme: Boolean, onToggleDarkTheme: () -> Unit, onOpenProviders: () -> Unit, onOpenChat: (String, String) -> Unit, onNewChat: () -> Unit) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
        SettingsDrawer(darkTheme = darkTheme, onToggleDarkTheme = onToggleDarkTheme, onOpenProviders = { scope.launch { drawerState.close() }; onOpenProviders() })
    }) {
        WorkspaceScreen(onOpenSettings = { scope.launch { drawerState.open() } }, onOpenChat = onOpenChat, onNewChat = onNewChat)
    }
}