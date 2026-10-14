package com.aaforge.shesafe.ui.renderers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaforge.shesafe.parser.InlineSpanNode
import com.aaforge.shesafe.ui.theme.ObsidianColors

@Composable
fun TableRenderer(
    headers: List<String>,
    rows: List<List<List<InlineSpanNode>>>,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, ObsidianColors.SurfaceBorder, RoundedCornerShape(8.dp))
            .horizontalScroll(scrollState)
    ) {
        if (headers.isNotEmpty()) {
            Row(modifier = Modifier.background(ObsidianColors.PillBackground)) {
                headers.forEach { header ->
                    Text(
                        text = header,
                        color = ObsidianColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.widthIn(min = 110.dp).padding(10.dp)
                    )
                }
            }
        }
        rows.forEachIndexed { index, rowCells ->
            val bg = if (index % 2 == 0) ObsidianColors.SurfaceCard else ObsidianColors.Background
            Row(modifier = Modifier.background(bg)) {
                rowCells.forEach { cellSpans ->
                    Box(modifier = Modifier.widthIn(min = 110.dp).padding(10.dp)) {
                        ColoredBoldTextRenderer(spans = cellSpans)
                    }
                }
            }
        }
    }
}
