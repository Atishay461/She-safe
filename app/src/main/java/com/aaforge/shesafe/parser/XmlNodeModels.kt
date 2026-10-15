package com.aaforge.shesafe.parser

enum class HighlightColor {
    RED, AMBER, GREEN, VIOLET
}

sealed interface InlineSpanNode {
    data class Plain(val text: String) : InlineSpanNode
    data class Bold(val text: String) : InlineSpanNode
    data class ColoredHighlight(val text: String, val color: HighlightColor) : InlineSpanNode
}

data class RoadmapStepNode(
    val stepNumber: String,
    val title: String,
    val bodySpans: List<InlineSpanNode>
)

sealed interface XmlBlockNode {
    data class Paragraph(val spans: List<InlineSpanNode>) : XmlBlockNode
    data class BulletList(val items: List<List<InlineSpanNode>>) : XmlBlockNode
    data class Roadmap(val steps: List<RoadmapStepNode>) : XmlBlockNode
    data class DataTable(
        val headers: List<String>,
        val rows: List<List<List<InlineSpanNode>>>
    ) : XmlBlockNode
}

data class EvaluationMeta(
    val category: String = "Safety",
    val severity: String = "LOW",
    val escalate: Boolean = false,
    val target: String = "None"
)

data class ParsedAgentTurn(
    val searchQueryTrigger: String? = null,
    val thinkingText: String? = null,
    val isCurrentlyThinking: Boolean = false,
    val interceptedChatName: String? = null,
    val evaluation: EvaluationMeta? = null,
    val blocks: List<XmlBlockNode> = emptyList()
)
