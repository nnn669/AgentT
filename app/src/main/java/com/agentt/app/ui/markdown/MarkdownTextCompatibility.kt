package com.agentt.app.ui.markdown

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Compatibility overload for call sites which use the former text parameter. */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    MarkdownText(markdown = text, modifier = modifier)
}
