package com.agentt.app.ui.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/** Compatibility alias for the user avatar used by the chat UI. */
val Icons.Outlined.Face: ImageVector
    get() = Icons.Outlined.AccountCircle

/**
 * Keeps the historical positional call order used by MainActivity while the
 * canonical ChatScreen API uses sessionId before title and includes Modifier.
 */
@Composable
fun ChatScreen(
    title: String,
    sessionId: String?,
    onBack: () -> Unit,
    onNewChat: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenBrowser: (String?) -> Unit
) {
    ChatScreen(
        sessionId = sessionId,
        title = title,
        modifier = Modifier,
        onBack = onBack,
        onNewChat = onNewChat,
        onOpenBrowser = onOpenBrowser,
        onOpenTerminal = onOpenTerminal
    )
}
