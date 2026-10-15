package com.aaforge.shesafe.parser

object XmlBlockParser {
    private val searchQueryRegex = Regex("""<search_query>(.*?)</search_query>""", RegexOption.DOT_MATCHES_ALL)
    private val closedThinkRegex = Regex("""<thinking>(.*?)</thinking>""", RegexOption.DOT_MATCHES_ALL)
    private val openThinkRegex = Regex("""<thinking>(.*)$""", RegexOption.DOT_MATCHES_ALL)
    private val chatNameRegex = Regex("""<chat_name>(.*?)</chat_name>""", RegexOption.DOT_MATCHES_ALL)
    private val partialChatNameStrip = Regex("""<chat_name>.*$""", RegexOption.DOT_MATCHES_ALL)
    private val evalRegex = Regex(
        """<evaluation\s+category="([^"]*)"\s+severity="([^"]*)"\s+escalate="([^"]*)"\s+target="([^"]*)"\s*/?>""",
        RegexOption.IGNORE_CASE
    )
    private val blockMasterRegex = Regex(
        """<(p|bullet_list|roadmap|data_table)>(.*?)</\1>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val itemRegex = Regex("""<item>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
    private val stepRegex = Regex("""<step\s+num="([^"]*)"\s+title="([^"]*)"\s*>(.*?)</step>""", RegexOption.DOT_MATCHES_ALL)
    private val headerColRegex = Regex("""<col>(.*?)</col>""", RegexOption.DOT_MATCHES_ALL)
    private val rowRegex = Regex("""<row>(.*?)</row>""", RegexOption.DOT_MATCHES_ALL)
    private val cellRegex = Regex("""<cell>(.*?)</cell>""", RegexOption.DOT_MATCHES_ALL)

    fun parseXmlStream(xml: String): ParsedAgentTurn {
        searchQueryRegex.find(xml)?.let {
            return ParsedAgentTurn(searchQueryTrigger = it.groupValues[1].trim())
        }
        val closedThink = closedThinkRegex.find(xml)
        val openThink = if (closedThink == null) openThinkRegex.find(xml) else null
        val thinkingText = (closedThink?.groupValues?.get(1) ?: openThink?.groupValues?.get(1))?.trim()
        val isThinkingActive = openThink != null
        val extractedChatName = chatNameRegex.find(xml)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

        val cleanedXml = xml
            .replace(closedThinkRegex, "")
            .replace(openThinkRegex, "")
            .replace(chatNameRegex, "")
            .replace(partialChatNameStrip, "")

        val evalMeta = evalRegex.find(cleanedXml)?.let { m ->
            EvaluationMeta(
                category = m.groupValues[1].ifBlank { "Safety" },
                severity = m.groupValues[2].uppercase().ifBlank { "LOW" },
                escalate = m.groupValues[3].equals("true", ignoreCase = true),
                target = m.groupValues[4].ifBlank { "None" }
            )
        }
        val blocks = parseBodyBlocks(cleanedXml)
        return ParsedAgentTurn(
            thinkingText = thinkingText,
            isCurrentlyThinking = isThinkingActive,
            interceptedChatName = extractedChatName,
            evaluation = evalMeta,
            blocks = blocks
        )
    }

    private fun parseBodyBlocks(cleanedXml: String): List<XmlBlockNode> {
        val blocks = mutableListOf<XmlBlockNode>()
        val matches = blockMasterRegex.findAll(cleanedXml).toList()
        if (matches.isEmpty()) {
            val stripped = cleanedXml.replace(evalRegex, "").replace(Regex("""</?response_body>"""), "").trim()
            if (stripped.isNotEmpty()) blocks.add(XmlBlockNode.Paragraph(XmlInlineParser.parseInline(stripped)))
            return blocks
        }
        for (match in matches) {
            val body = match.groupValues[2].trim()
            when (match.groupValues[1].lowercase()) {
                "p" -> blocks.add(XmlBlockNode.Paragraph(XmlInlineParser.parseInline(body)))
                "bullet_list" -> blocks.add(XmlBlockNode.BulletList(itemRegex.findAll(body).map { XmlInlineParser.parseInline(it.groupValues[1].trim()) }.toList()))
                "roadmap" -> blocks.add(XmlBlockNode.Roadmap(stepRegex.findAll(body).map { RoadmapStepNode(it.groupValues[1].trim(), it.groupValues[2].trim(), XmlInlineParser.parseInline(it.groupValues[3].trim())) }.toList()))
                "data_table" -> blocks.add(XmlBlockNode.DataTable(headerColRegex.findAll(body).map { it.groupValues[1].trim() }.toList(), rowRegex.findAll(body).map { r -> cellRegex.findAll(r.groupValues[1]).map { c -> XmlInlineParser.parseInline(c.groupValues[1].trim()) }.toList() }.toList()))
            }
        }
        return blocks
    }
}
