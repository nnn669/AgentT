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
import com.agentt.app.ui.assistant.AssistantEditScreen
import com.agentt.app.ui.assistant.AssistantsScreen
import com.agentt.app.ui.assistant.TagManagerScreen
import com.agentt.app.ui.browser.BrowserScreen
import com.agentt.app.ui.chat.ChatScreen
import com.agentt.app.ui.chat.ChatStore
import com.agentt.app.ui.chat.createChatSession
import com.agentt.app.ui.files.FileBrowserScreen
import com.agentt.app.ui.providers.ProvidersScreen
import com.agentt.app.ui.settings.SandboxEnvironmentScreen
import com.agentt.app.ui.settings.SettingsDrawer
import com.agentt.app.ui.terminal.TerminalScreen
import com.agentt.app.ui.theme.AgentTTheme
import com.agentt.app.ui.web.WebTools
import com.agentt.app.ui.workspace.WorkspaceScreen
import kotlinx.coroutines.launch

enum class Screen { Workspace, Chat, Providers, SandboxEnvironment, Browser, Terminal, Assistants, AssistantEdit, TagManager, FileBrowser }

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
                    var editAssistantId by rememberSaveable { mutableStateOf("") }
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
                            onOpenSandboxEnvironment = { screen = Screen.SandboxEnvironment },
                            onOpenAssistants = { screen = Screen.Assistants },
                            onOpenFileBrowser = { screen = Screen.FileBrowser },
                            onOpenChat = { id, title -> openChat(id, title) },
                            onNewChat = {
                                val session = createChatSession(ChatStore.from(this@MainActivity))
                                openChat(session.id, session.title)
                            }
                        )
                        Screen.Chat -> ChatScreen(
                            chatTitle,
                            chatSessionId,
                            { screen = Screen.Workspace },
                            {
                                val session = createChatSession(ChatStore.from(this@MainActivity))
                                chatSessionId = session.id
                                chatTitle = session.title
                            },
                            { screen = Screen.Terminal },
                            { url ->
                                browserUrl = url ?: ""
                                screen = Screen.Browser
                            }
                        )
                        Screen.Providers -> ProvidersScreen(onBack = { screen = Screen.Workspace })
                        Screen.SandboxEnvironment -> SandboxEnvironmentScreen(onBack = { screen = Screen.Workspace })
                        Screen.Browser -> BrowserScreen(initialUrl = browserUrl, onBack = { screen = Screen.Chat })
                        Screen.Terminal -> TerminalScreen(onBack = { screen = Screen.Chat })
                        Screen.Assistants -> AssistantsScreen(
                            onBack = { screen = Screen.Workspace },
                            onEditAssistant = { id ->
                                editAssistantId = id
                                screen = Screen.AssistantEdit
                            },
                            onManageTags = { screen = Screen.TagManager }
                        )
                        Screen.AssistantEdit -> AssistantEditScreen(
                            assistantId = editAssistantId,
                            onBack = { screen = Screen.Assistants }
                        )
                        Screen.TagManager -> TagManagerScreen(
                            onBack = { screen = Screen.Assistants }
                        )
                        Screen.FileBrowser -> FileBrowserScreen(
                            onBack = { screen = Screen.Workspace }
                        )
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
    onOpenSandboxEnvironment: () -> Unit,
    onOpenAssistants: () -> Unit,
    onOpenFileBrowser: () -> Unit,
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
                },
                onOpenSandboxEnvironment = {
                    scope.launch { drawerState.close() }
                    onOpenSandboxEnvironment()
                },
                onOpenAssistants = {
                    scope.launch { drawerState.close() }
                    onOpenAssistants()
                },
                onOpenFileBrowser = {
                    scope.launch { drawerState.close() }
                    onOpenFileBrowser()
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