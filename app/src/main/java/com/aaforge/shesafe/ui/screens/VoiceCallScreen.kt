package com.aaforge.shesafe.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaforge.shesafe.network.VoiceUiState
import com.aaforge.shesafe.ui.theme.ObsidianColors

@Composable
fun VoiceCallScreen(
    uiState: VoiceUiState,
    isMuted: Boolean,
    inputLevel: Float,
    outputLevel: Float,
    onToggleMute: () -> Unit,
    onEndCall: () -> Unit
) {
    val smoothIn by animateFloatAsState(
        targetValue = if (isMuted) 0f else inputLevel,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "inWave"
    )
    val smoothOut by animateFloatAsState(
        targetValue = outputLevel,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "outWave"
    )
    val inf = rememberInfiniteTransition(label = "voiceLoop")
    val spinAngle by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1050, easing = LinearEasing)),
        label = "spin"
    )
    val idleBreath by inf.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )

    Column(
        modifier = Modifier.fillMaxSize().background(ObsidianColors.Background).padding(vertical = 38.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("m e l l a", color = ObsidianColors.BrandBronze, fontSize = 18.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when {
                    uiState == VoiceUiState.PROCESSING -> "Processing..."
                    uiState == VoiceUiState.MODEL_SPEAKING -> "Mella is speaking"
                    isMuted -> "Microphone muted"
                    else -> "Listening..."
                },
                color = if (isMuted && uiState == VoiceUiState.USER_LISTENING) ObsidianColors.StatusAmber else ObsidianColors.TextSecondary,
                fontSize = 13.sp,
                letterSpacing = 0.8.sp
            )
        }

        Box(modifier = Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Crossfade(targetState = uiState, animationSpec = tween(320), label = "stateMorph") { state ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when (state) {
                        VoiceUiState.USER_LISTENING -> {
                            val ringColor = if (isMuted) ObsidianColors.StatusAmber else ObsidianColors.BrandCopper
                            val activeScale = (if (isMuted) 0.95f else idleBreath) + (smoothIn * 0.85f)
                            Box(modifier = Modifier.size(152.dp).scale(activeScale).clip(CircleShape).background(ringColor.copy(alpha = 0.14f)))
                            Box(
                                modifier = Modifier.size(104.dp).scale(1f + smoothIn * 0.4f).clip(CircleShape)
                                    .background(ObsidianColors.SurfaceElevated).border(1.5.dp, ringColor, CircleShape)
                            )
                        }
                        VoiceUiState.PROCESSING -> {
                            Canvas(modifier = Modifier.size(92.dp)) {
                                drawArc(ObsidianColors.SurfaceBorder, 0f, 360f, false, style = Stroke(width = 3.dp.toPx()))
                                drawArc(ObsidianColors.BrandCopper, spinAngle, 115f, false, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round))
                            }
                        }
                        VoiceUiState.MODEL_SPEAKING -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.height(140.dp)
                            ) {
                                listOf(0.75f, 1.3f, 0.85f).forEachIndexed { idx, mult ->
                                    val dynamicH = (26f + (smoothOut * 98f * mult) + (idleBreath - 0.94f) * (idx + 1) * 40f).coerceIn(24f, 130f)
                                    val barHeight by animateDpAsState(targetValue = dynamicH.dp, animationSpec = tween(80), label = "bar$idx")
                                    Box(modifier = Modifier.width(16.dp).height(barHeight).clip(RoundedCornerShape(50)).background(ObsidianColors.PureWhite))
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val muteBorder = if (isMuted) ObsidianColors.StatusAmber else ObsidianColors.SurfaceBorder
            val muteBg = if (isMuted) ObsidianColors.StatusAmber.copy(alpha = 0.14f) else ObsidianColors.SurfaceCard
            val muteTint = if (isMuted) ObsidianColors.StatusAmber else ObsidianColors.BrandCopper

            Row(
                modifier = Modifier.clip(RoundedCornerShape(28.dp)).background(muteBg).border(1.dp, muteBorder, RoundedCornerShape(28.dp))
                    .clickable { onToggleMute() }.padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(modifier = Modifier.size(16.dp)) {
                    val w = size.width; val h = size.height; val st = 1.8.dp.toPx()
                    drawRoundRect(muteTint, Offset(w * 0.32f, h * 0.08f), Size(w * 0.36f, h * 0.52f), CornerRadius(w * 0.18f), Stroke(st))
                    drawArc(muteTint, 0f, 180f, false, Offset(w * 0.18f, h * 0.25f), Size(w * 0.64f, h * 0.5f), style = Stroke(st, cap = StrokeCap.Round))
                    drawLine(muteTint, Offset(w * 0.5f, h * 0.75f), Offset(w * 0.5f, h * 0.94f), st, StrokeCap.Round)
                    if (isMuted) drawLine(ObsidianColors.StatusAmber, Offset(w * 0.12f, h * 0.12f), Offset(w * 0.88f, h * 0.88f), 2.2.dp.toPx(), StrokeCap.Round)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(if (isMuted) "MUTED" else "MUTE", color = muteTint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
            }

            Box(
                modifier = Modifier.clip(RoundedCornerShape(28.dp)).background(ObsidianColors.SurfaceCard)
                    .border(1.dp, ObsidianColors.StatusRed.copy(alpha = 0.6f), RoundedCornerShape(28.dp))
                    .clickable { onEndCall() }.padding(horizontal = 26.dp, vertical = 14.dp)
            ) {
                Text("END CALL", color = ObsidianColors.StatusRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
            }
        }
    }
}
