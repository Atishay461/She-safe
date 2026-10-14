package com.aaforge.shesafe.parser

object XmlBlockParser {
    private val searchQueryRegex = Regex("""<search_query>(.*?)</search_query>""", RegexOption.DOT_MATCHES_ALL)
    private val evalRegex = Regex(
        """<evaluation\s+category="([^"]*)"\s+severity="([^"]*)"\s+escalate="([^"]*)"\s+target="([^"]*)"\s*/?>""",
        RegexOption.IGNORE_CASE
    )
    private val blockMasterRegex = Regex(
        """<(p|bullet_list|roadmap|data_table)>(.*?)</\1>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val itemRegex = Regex("""<item>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
    private val stepRegex = Regex(
        """<step\s+num="([^"]*)"\s+title="([^"]*)"\s*>(.*?)</step>""",
        RegexOption.DOT_MATCHES_ALL
    )
    private val headerColRegex = Regex("""<col>(.*?)</col>""", RegexOption.DOT_MATCHES_ALL)
    private val rowRegex = Regex("""<row>(.*?)</row>""", RegexOption.DOT_MATCHES_ALL)
    private val cellRegex = Regex("""<cell>(.*?)</cell>""", RegexOption.DOT_MATCHES_ALL)

    fun parseXmlStream(xml: String): ParsedAgentTurn {
        val searchMatch = searchQueryRegex.find(xml)
        if (searchMatch != null) {
            return ParsedAgentTurn(searchQueryTrigger = searchMatch.groupValues[1].trim())
        }

        val evalMeta = evalRegex.find(xml)?.let { m ->
            EvaluationMeta(
                category = m.groupValues[1].ifBlank { "Safety" },
                severity = m.groupValues[2].uppercase().ifBlank { "LOW" },
                escalate = m.groupValues[3].equals("true", ignoreCase = true),
                target = m.groupValues[4].ifBlank { "None" }
            )
        }

        val blocks = mutableListOf<XmlBlockNode>()
        val matches = blockMasterRegex.findAll(xml).toList()

        if (matches.isEmpty()) {
            val stripped = xml.replace(evalRegex, "").replace(Regex("""</?response_body>"""), "").trim()
            if (stripped.isNotEmpty()) {
                blocks.add(XmlBlockNode.Paragraph(XmlInlineParser.parseInline(stripped)))
            }
            return ParsedAgentTurn(evaluation = evalMeta, blocks = blocks)
        }

        for (match in matches) {
            val tag = match.groupValues[1].lowercase()
            val body = match.groupValues[2].trim()
            when (tag) {
                "p" -> blocks.add(XmlBlockNode.Paragraph(XmlInlineParser.parseInline(body)))
                "bullet_list" -> {
                    val items = itemRegex.findAll(body).map {
                        XmlInlineParser.parseInline(it.groupValues[1].trim())
                    }.toList()
                    if (items.isNotEmpty()) blocks.add(XmlBlockNode.BulletList(items))
                }
                "roadmap" -> {
                    val steps = stepRegex.findAll(body).map {
                        RoadmapStepNode(
                            stepNumber = it.groupValues[1].trim(),
                            title = it.groupValues[2].trim(),
                            bodySpans = XmlInlineParser.parseInline(it.groupValues[3].trim())
                        )
                    }.toList()
                    if (steps.isNotEmpty()) blocks.add(XmlBlockNode.Roadmap(steps))
                }
                "data_table" -> {
                    val headers = headerColRegex.findAll(body).map { it.groupValues[1].trim() }.toList()
                    val rows = rowRegex.findAll(body).map { rMatch ->
                        cellRegex.findAll(rMatch.groupValues[1]).map { cMatch ->
                            XmlInlineParser.parseInline(cMatch.groupValues[1].trim())
                        }.toList()
                    }.toList()
                    if (headers.isNotEmpty() || rows.isNotEmpty()) {
                        blocks.add(XmlBlockNode.DataTable(headers = headers, rows = rows))
                    }
                }
            }
        }
        return ParsedAgentTurn(evaluation = evalMeta, blocks = blocks)
    }
}
