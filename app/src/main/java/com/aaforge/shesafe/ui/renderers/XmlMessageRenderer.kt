package com.aaforge.shesafe.ui.renderers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aaforge.shesafe.parser.ParsedAgentTurn
import com.aaforge.shesafe.parser.XmlBlockNode

@Composable
fun XmlMessageRenderer(turn: ParsedAgentTurn, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        ThinkingPulseRenderer(
            thinkingText = turn.thinkingText,
            isCurrentlyThinking = turn.isCurrentlyThinking
        )
        turn.blocks.forEach { block ->
            when (block) {
                is XmlBlockNode.Paragraph -> ColoredBoldTextRenderer(spans = block.spans, modifier = Modifier.padding(vertical = 5.dp))
                is XmlBlockNode.BulletList -> BulletListRenderer(items = block.items, modifier = Modifier.padding(vertical = 5.dp))
                is XmlBlockNode.Roadmap -> RoadmapRenderer(steps = block.steps, modifier = Modifier.padding(vertical = 8.dp))
                is XmlBlockNode.DataTable -> TableRenderer(headers = block.headers, rows = block.rows, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}
