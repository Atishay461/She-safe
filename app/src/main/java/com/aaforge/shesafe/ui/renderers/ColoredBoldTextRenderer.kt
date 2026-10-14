package com.aaforge.shesafe.ui.renderers

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.aaforge.shesafe.parser.HighlightColor
import com.aaforge.shesafe.parser.InlineSpanNode
import com.aaforge.shesafe.ui.theme.ObsidianColors

@Composable
fun ColoredBoldTextRenderer(
    spans: List<InlineSpanNode>,
    modifier: Modifier = Modifier
) {
    val annotated: AnnotatedString = buildAnnotatedString {
        spans.forEach { span ->
            when (span) {
                is InlineSpanNode.Plain -> {
                    withStyle(SpanStyle(color = ObsidianColors.TextPrimary)) {
                        append(span.text)
                    }
                }
                is InlineSpanNode.Bold -> {
                    withStyle(
                        SpanStyle(
                            color = ObsidianColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    ) {
                        append(span.text)
                    }
                }
                is InlineSpanNode.ColoredHighlight -> {
                    val targetColor = when (span.color) {
                        HighlightColor.RED -> ObsidianColors.StatusRed
                        HighlightColor.AMBER -> ObsidianColors.StatusAmber
                        HighlightColor.GREEN -> ObsidianColors.StatusGreen
                        HighlightColor.VIOLET -> ObsidianColors.AccentViolet
                    }
                    withStyle(
                        SpanStyle(
                            color = targetColor,
                            fontWeight = FontWeight.Bold,
                            background = targetColor.copy(alpha = 0.12f)
                        )
                    ) {
                        append(" ${span.text} ")
                    }
                }
            }
        }
    }
    Text(
        text = annotated,
        modifier = modifier,
        fontSize = 14.5.sp,
        lineHeight = 22.sp
    )
}
