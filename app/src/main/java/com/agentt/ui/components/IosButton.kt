package com.agentt.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// iOS 风格按钮（参照 TIN IosTileButton：圆角12、浅灰底、细边框、图标+文字）
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    backgroundColor: Color? = null,
    foregroundColor: Color? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp
) {
    val isDark = isSystemInDarkTheme()
    val cs = MaterialTheme.colorScheme
    val tint = backgroundColor ?: cs.primary

    // 背景：tinted 时用 primary 透明度，否则用中性浅灰
    val baseBg = if (backgroundColor != null) {
        if (isDark) tint.copy(alpha = 0.20f) else tint.copy(alpha = 0.12f)
    } else {
        if (isDark) Color.White.copy(alpha = 0.10f) else Color(0xFFF2F3F5)
    }
    val defaultFg = foregroundColor ?: if (backgroundColor != null) tint else cs.onSurface.copy(alpha = 0.9f)
    val borderColor = if (backgroundColor != null) {
        tint.copy(alpha = if (isDark) 0.55f else 0.45f)
    } else {
        cs.outlineVariant.copy(alpha = 0.35f)
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) baseBg else baseBg.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, if (enabled) borderColor else borderColor.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (enabled) defaultFg else defaultFg.copy(alpha = 0.45f)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) defaultFg else defaultFg.copy(alpha = 0.45f)
            )
        }
    }
}

@Composable
fun IosTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(
            text = text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}
