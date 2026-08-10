package com.agentt.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        IosSwitchThumb(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun IosSwitchThumb(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) Color(0xFF34C759) else Color(0xFFE9E9EA),
        label = "trackColor"
    )
    val trackColorDark by animateColorAsState(
        targetValue = if (checked) Color(0xFF30D158) else Color(0xFF39393D),
        label = "trackColorDark"
    )
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF000000)

    Box(
        modifier = Modifier
            .width(51.dp)
            .height(31.dp)
            .clip(RoundedCornerShape(15.5.dp))
            .background(if (isDark) trackColorDark else trackColor)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .size(27.dp)
                .clip(CircleShape)
                .background(Color.White)
                .align(
                    if (checked) Alignment.CenterEnd else Alignment.CenterStart
                )
        )
    }
}
