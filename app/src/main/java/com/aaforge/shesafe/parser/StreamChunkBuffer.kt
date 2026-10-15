package com.aaforge.shesafe.parser

class StreamChunkBuffer {
    private val accumulatedXml = StringBuilder()
    private val contentWrapperRegex = Regex("""\[/?content\]""", RegexOption.IGNORE_CASE)

    fun appendFrame(rawLine: String): Boolean {
        val trimmed = rawLine.trim()
        if (trimmed.equals("[Done]", ignoreCase = true) ||
            trimmed.equals("data: [Done]", ignoreCase = true)
        ) {
            return true
        }

        val cleaned = trimmed
            .removePrefix("data:")
            .replace(contentWrapperRegex, "")

        if (cleaned.isNotEmpty()) {
            accumulatedXml.append(cleaned)
        }
        return false
    }

    fun getSafeRenderableXml(): String {
        val raw = accumulatedXml.toString().replace(contentWrapperRegex, "")
        val lastOpenBracket = raw.lastIndexOf('<')
        val lastCloseBracket = raw.lastIndexOf('>')
        return if (lastOpenBracket > lastCloseBracket) {
            raw.substring(0, lastOpenBracket)
        } else {
            raw
        }
    }

    fun getFullRawXml(): String = accumulatedXml.toString().replace(contentWrapperRegex, "")

    fun reset() {
        accumulatedXml.clear()
    }
}
