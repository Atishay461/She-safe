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
    inputLevel: Float,
    outputLevel: Float,
    onEndCall: () -> Unit
) {
    val smoothIn by animateFloatAsState(
        targetValue = inputLevel,
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
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "spin"
    )
    val idleBreath by inf.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )

    Column(
        modifier = Modifier.fillMaxSize().background(ObsidianColors.Background).padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("m e l l a", color = ObsidianColors.BrandBronze, fontSize = 18.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (uiState) {
                    VoiceUiState.USER_LISTENING -> "Listening..."
                    VoiceUiState.PROCESSING -> "Processing..."
                    VoiceUiState.MODEL_SPEAKING -> "Mella is speaking"
                },
                color = ObsidianColors.TextSecondary,
                fontSize = 13.sp,
                letterSpacing = 0.8.sp
            )
        }

        Box(modifier = Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Crossfade(targetState = uiState, animationSpec = tween(350), label = "stateMorph") { state ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when (state) {
                        VoiceUiState.USER_LISTENING -> {
                            val activeScale = idleBreath + (smoothIn * 0.75f)
                            Box(
                                modifier = Modifier.size(150.dp).scale(activeScale).clip(CircleShape)
                                    .background(ObsidianColors.BrandCopper.copy(alpha = 0.14f))
                            )
                            Box(
                                modifier = Modifier.size(104.dp).scale(1f + smoothIn * 0.35f).clip(CircleShape)
                                    .background(ObsidianColors.SurfaceElevated)
                                    .border(1.5.dp, ObsidianColors.BrandCopper, CircleShape)
                            )
                        }
                        VoiceUiState.PROCESSING -> {
                            Canvas(modifier = Modifier.size(92.dp)) {
                                drawArc(
                                    color = ObsidianColors.SurfaceBorder,
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = 3.dp.toPx())
                                )
                                drawArc(
                                    color = ObsidianColors.BrandCopper,
                                    startAngle = spinAngle,
                                    sweepAngle = 110f,
                                    useCenter = false,
                                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                        }
                        VoiceUiState.MODEL_SPEAKING -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.height(140.dp)
                            ) {
                                val multipliers = listOf(0.75f, 1.25f, 0.85f)
                                multipliers.forEachIndexed { idx, mult ->
                                    val dynamicH = (28f + (smoothOut * 95f * mult) + (idleBreath - 0.94f) * (idx + 1) * 45f).coerceIn(24f, 128f)
                                    val barHeight by animateDpAsState(targetValue = dynamicH.dp, animationSpec = tween(90), label = "bar$idx")
                                    Box(
                                        modifier = Modifier
                                            .width(16.dp)
                                            .height(barHeight)
                                            .clip(RoundedCornerShape(50))
                                            .background(ObsidianColors.PureWhite)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(ObsidianColors.SurfaceCard)
                .border(1.dp, ObsidianColors.StatusRed.copy(alpha = 0.6f), RoundedCornerShape(28.dp))
                .clickable { onEndCall() }
                .padding(horizontal = 32.dp, vertical = 14.dp)
        ) {
            Text("END VOICE SESSION", color = ObsidianColors.StatusRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
        }
    }
}
