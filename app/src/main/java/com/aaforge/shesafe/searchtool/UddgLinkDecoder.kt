package com.aaforge.shesafe.searchtool

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object UddgLinkDecoder {
    fun unwrap(rawHref: String): String {
        val unescaped = rawHref
            .replace("&amp;", "&")
            .replace("&#x27;", "'")
            .replace("&quot;", "\"")
            .trim()

        val uddgMarker = "uddg="
        val markerIndex = unescaped.indexOf(uddgMarker)
        if (markerIndex != -1) {
            val start = markerIndex + uddgMarker.length
            val end = unescaped.indexOf('&', start).takeIf { it != -1 } ?: unescaped.length
            val encodedTarget = unescaped.substring(start, end)
            return runCatching {
                URLDecoder.decode(encodedTarget, StandardCharsets.UTF_8.name())
            }.getOrDefault(encodedTarget)
        }

        return when {
            unescaped.startsWith("//") -> "https:$unescaped"
            unescaped.startsWith("http://") || unescaped.startsWith("https://") -> unescaped
            else -> "https://$unescaped"
        }
    }
}
