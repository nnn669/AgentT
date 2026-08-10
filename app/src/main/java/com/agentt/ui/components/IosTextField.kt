package com.agentt.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// iOS 风格输入框（参照 TIN ios_form_text_field：圆角、浅灰填充、无边框）
@Composable
fun IosTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true
) {
    val isDark = isSystemInDarkTheme()
    val cs = MaterialTheme.colorScheme
    val bg = if (isDark) Color.White.copy(alpha = 0.10f) else Color(0xFFF2F3F5)

    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = placeholder,
                fontSize = 14.sp,
                color = cs.onSurface.copy(alpha = 0.4f)
            )
        },
        label = {
            Text(
                text = label,
                fontSize = 14.sp,
                color = cs.onSurface.copy(alpha = 0.6f)
            )
        },
        singleLine = singleLine,
        shape = RoundedCornerShape(10.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = bg,
            unfocusedContainerColor = bg,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        textStyle = LocalTextStyle.current.copy(fontSize = 15.sp)
    )
}
