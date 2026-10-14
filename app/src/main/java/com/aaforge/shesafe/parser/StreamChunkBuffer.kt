package com.aaforge.shesafe.parser

class StreamChunkBuffer {
    private val accumulatedXml = StringBuilder()

    fun appendFrame(rawLine: String): Boolean {
        val trimmed = rawLine.trim()
        if (trimmed.equals("[Done]", ignoreCase = true) ||
            trimmed.equals("data: [Done]", ignoreCase = true)
        ) {
            return true
        }

        val cleaned = trimmed.removePrefix("data:").trim()
        if (cleaned.startsWith("[content]", ignoreCase = true)) {
            val payload = cleaned
                .removePrefix("[content]")
                .removePrefix("[CONTENT]")
                .removeSuffix("[/content]")
                .removeSuffix("[/CONTENT]")
            accumulatedXml.append(payload)
        } else if (cleaned.isNotEmpty()) {
            accumulatedXml.append(cleaned)
        }
        return false
    }

    fun getSafeRenderableXml(): String {
        val raw = accumulatedXml.toString()
        val lastOpenBracket = raw.lastIndexOf('<')
        val lastCloseBracket = raw.lastIndexOf('>')
        return if (lastOpenBracket > lastCloseBracket) {
            raw.substring(0, lastOpenBracket)
        } else {
            raw
        }
    }

    fun getFullRawXml(): String = accumulatedXml.toString()

    fun reset() {
        accumulatedXml.clear()
    }
}
