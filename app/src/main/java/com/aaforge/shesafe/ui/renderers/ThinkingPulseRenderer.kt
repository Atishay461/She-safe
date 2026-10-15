package com.aaforge.shesafe.ui.renderers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaforge.shesafe.ui.theme.ObsidianColors
import kotlinx.coroutines.delay

@Composable
fun ThinkingPulseRenderer(
    thinkingText: String?,
    isCurrentlyThinking: Boolean,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(isCurrentlyThinking, thinkingText) {
        if (isCurrentlyThinking) {
            visible = true
        } else if (!thinkingText.isNullOrBlank()) {
            delay(1000L)
            visible = false
        } else {
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(450)) + shrinkVertically(tween(450))
    ) {
        val transition = rememberInfiniteTransition(label = "dotPulse")
        val pulseAlpha by transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
            label = "alpha"
        )
        val pulseScale by transition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
            label = "scale"
        )

        Row(
            modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(pulseScale)
                    .alpha(pulseAlpha)
                    .clip(CircleShape)
                    .background(ObsidianColors.PureWhite)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = thinkingText?.take(90)?.let { "$it..." } ?: "Thinking...",
                color = ObsidianColors.TextSecondary,
                fontSize = 12.5.sp,
                fontStyle = FontStyle.Italic,
                maxLines = 1
            )
        }
    }
}
