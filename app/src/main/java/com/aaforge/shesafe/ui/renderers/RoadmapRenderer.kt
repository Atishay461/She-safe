package com.aaforge.shesafe.ui.renderers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaforge.shesafe.parser.RoadmapStepNode
import com.aaforge.shesafe.ui.theme.ObsidianColors

@Composable
fun RoadmapRenderer(steps: List<RoadmapStepNode>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, step ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(32.dp)) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(ObsidianColors.PillBackground)
                            .border(1.dp, ObsidianColors.AccentViolet, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = step.stepNumber.ifBlank { "${index + 1}" },
                            color = ObsidianColors.AccentViolet,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (index < steps.size - 1) {
                        Box(modifier = Modifier.width(2.dp).height(36.dp).background(ObsidianColors.SurfaceBorder))
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f).padding(bottom = 12.dp)) {
                    Text(text = step.title, color = ObsidianColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    ColoredBoldTextRenderer(spans = step.bodySpans)
                }
            }
        }
    }
}
