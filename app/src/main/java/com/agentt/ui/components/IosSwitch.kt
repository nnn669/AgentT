package com.agentt.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun IosSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    description: String = ""
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (label.isNotEmpty()) Text(label, fontSize = 15.sp)
            if (description.isNotEmpty()) {
                Text(
                    description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        SwitchThumb(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SwitchThumb(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val isDark = isSystemInDarkTheme()
    val trackColor by animateColorAsState(
        targetValue = when {
            checked && isDark -> Color(0xFF30D158)
            checked -> Color(0xFF34C759)
            isDark -> Color(0xFF39393D)
            else -> Color(0xFFE9E9EA)
        },
        label = "switchTrack"
    )
    val offset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        label = "switchThumbOffset"
    )

    Box(
        modifier = Modifier
            .width(51.dp)
            .height(31.dp)
            .background(trackColor, RoundedCornerShape(15.5.dp))
            .clickable { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .offset(x = offset, y = 2.dp)
                .size(27.dp)
                .background(Color.White, CircleShape)
        )
    }
}
