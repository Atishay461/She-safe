package com.aaforge.shesafe.ui.renderers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.aaforge.shesafe.ui.theme.ObsidianColors

@Composable
fun UpArrowSendButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (enabled) ObsidianColors.PureWhite else ObsidianColors.PureWhite.copy(alpha = 0.3f))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val w = size.width
            val h = size.height
            val stroke = 2.4.dp.toPx()
            drawLine(
                color = ObsidianColors.PureBlack,
                start = Offset(w / 2f, h * 0.85f),
                end = Offset(w / 2f, h * 0.15f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = ObsidianColors.PureBlack,
                start = Offset(w * 0.2f, h * 0.45f),
                end = Offset(w / 2f, h * 0.15f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = ObsidianColors.PureBlack,
                start = Offset(w * 0.8f, h * 0.45f),
                end = Offset(w / 2f, h * 0.15f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}
