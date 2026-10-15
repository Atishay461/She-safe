package com.aaforge.shesafe.ui.renderers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaforge.shesafe.parser.ParsedAgentTurn
import com.aaforge.shesafe.parser.XmlBlockNode
import com.aaforge.shesafe.ui.theme.ObsidianColors

@Composable
fun XmlMessageRenderer(turn: ParsedAgentTurn, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        ThinkingPulseRenderer(
            thinkingText = turn.thinkingText,
            isCurrentlyThinking = turn.isCurrentlyThinking
        )

        turn.evaluation?.let { eval ->
            val evalColor = when (eval.severity) {
                "CRITICAL", "HIGH" -> ObsidianColors.StatusRed
                "MEDIUM" -> ObsidianColors.StatusAmber
                else -> ObsidianColors.StatusGreen
            }
            Box(
                modifier = Modifier
                    .padding(bottom = 8.dp, top = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(evalColor.copy(alpha = 0.12f))
                    .border(1.dp, evalColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Severity: ${eval.severity} | Category: ${eval.category}",
                    color = evalColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        turn.blocks.forEach { block ->
            when (block) {
                is XmlBlockNode.Paragraph -> ColoredBoldTextRenderer(spans = block.spans, modifier = Modifier.padding(vertical = 4.dp))
                is XmlBlockNode.BulletList -> BulletListRenderer(items = block.items, modifier = Modifier.padding(vertical = 4.dp))
                is XmlBlockNode.Roadmap -> RoadmapRenderer(steps = block.steps, modifier = Modifier.padding(vertical = 6.dp))
                is XmlBlockNode.DataTable -> TableRenderer(headers = block.headers, rows = block.rows, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}
