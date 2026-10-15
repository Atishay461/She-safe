package com.aaforge.shesafe.ui.renderers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.aaforge.shesafe.ui.theme.ObsidianColors

@Composable
fun UpArrowSendButton(
    canSend: Boolean,
    isStreaming: Boolean,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = isStreaming || canSend
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (active) ObsidianColors.PureWhite else ObsidianColors.SurfaceElevated)
            .border(1.dp, if (active) ObsidianColors.PureWhite else ObsidianColors.SurfaceBorder, CircleShape)
            .clickable(enabled = active) {
                if (isStreaming) onStopClick() else onSendClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(15.dp)) {
            val w = size.width
            val h = size.height
            if (isStreaming) {
                val sq = w * 0.68f
                val offset = (w - sq) / 2f
                drawRoundRect(
                    color = ObsidianColors.PureBlack,
                    topLeft = Offset(offset, offset),
                    size = Size(sq, sq),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            } else {
                val fg = if (canSend) ObsidianColors.PureBlack else ObsidianColors.TextSecondary
                val stroke = 2.3.dp.toPx()
                drawLine(fg, Offset(w / 2f, h * 0.85f), Offset(w / 2f, h * 0.15f), stroke, StrokeCap.Round)
                drawLine(fg, Offset(w * 0.2f, h * 0.45f), Offset(w / 2f, h * 0.15f), stroke, StrokeCap.Round)
                drawLine(fg, Offset(w * 0.8f, h * 0.45f), Offset(w / 2f, h * 0.15f), stroke, StrokeCap.Round)
            }
        }
    }
}
