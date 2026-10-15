package com.aaforge.shesafe.ui.renderers

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaforge.shesafe.ui.theme.ObsidianColors

@Composable
fun ThinkingPulseRenderer(
    thinkingText: String?,
    isCurrentlyThinking: Boolean,
    modifier: Modifier = Modifier
) {
    if (thinkingText.isNullOrBlank() && !isCurrentlyThinking) return

    val transition = rememberInfiniteTransition(label = "whiteDotPulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val pulseScale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .scale(if (isCurrentlyThinking) pulseScale else 1f)
                .alpha(if (isCurrentlyThinking) pulseAlpha else 0.6f)
                .clip(CircleShape)
                .background(ObsidianColors.PureWhite)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = thinkingText?.ifBlank { "Analyzing situation..." } ?: "Thinking...",
            color = ObsidianColors.TextSecondary,
            fontSize = 12.5.sp,
            fontStyle = FontStyle.Italic,
            lineHeight = 18.sp
        )
    }
}
