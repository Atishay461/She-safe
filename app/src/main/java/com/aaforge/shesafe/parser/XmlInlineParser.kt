package com.aaforge.shesafe.parser

import java.util.Locale

object XmlInlineParser {
    private val inlineTokenRegex = Regex(
        """<b_text>(.*?)</b_text>|<b>(.*?)</b>|<hl\s+color="([^"]+)"\s*>(.*?)</hl>|<color\s+hex="([^"]+)"\s*>(.*?)</color>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )

    fun parseInline(rawText: String): List<InlineSpanNode> {
        if (rawText.isBlank()) return emptyList()
        val spans = mutableListOf<InlineSpanNode>()
        var cursor = 0

        for (match in inlineTokenRegex.findAll(rawText)) {
            if (match.range.first > cursor) {
                val plainPart = cleanEntities(rawText.substring(cursor, match.range.first))
                if (plainPart.isNotEmpty()) {
                    spans.add(InlineSpanNode.Plain(plainPart))
                }
            }

            val bText = match.groupValues[1].ifEmpty { match.groupValues[2] }
            val hlColorAttr = match.groupValues[3].ifEmpty { match.groupValues[5] }
            val hlContent = match.groupValues[4].ifEmpty { match.groupValues[6] }

            if (bText.isNotEmpty()) {
                spans.add(InlineSpanNode.Bold(cleanEntities(bText)))
            } else if (hlContent.isNotEmpty()) {
                spans.add(
                    InlineSpanNode.ColoredHighlight(
                        text = cleanEntities(hlContent),
                        color = mapColor(hlColorAttr)
                    )
                )
            }
            cursor = match.range.last + 1
        }

        if (cursor < rawText.length) {
            val remaining = cleanEntities(rawText.substring(cursor))
            if (remaining.isNotEmpty()) {
                spans.add(InlineSpanNode.Plain(remaining))
            }
        }
        return spans
    }

    private fun mapColor(attr: String): HighlightColor {
        return when (attr.lowercase(Locale.US).trim()) {
            "red", "critical", "#ef4444" -> HighlightColor.RED
            "amber", "yellow", "warning", "#f59e0b" -> HighlightColor.AMBER
            "green", "safe", "#10b981" -> HighlightColor.GREEN
            else -> HighlightColor.VIOLET
        }
    }

    private fun cleanEntities(input: String): String =
        input.replace(Regex("""</?[^>]+>"""), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
}
