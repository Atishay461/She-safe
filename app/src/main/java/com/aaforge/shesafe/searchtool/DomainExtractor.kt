package com.aaforge.shesafe.searchtool

import com.aaforge.shesafe.core.BackendEndpoint
import java.net.URI
import java.util.Locale

object DomainExtractor {
    fun extractCleanDomain(realUrl: String): String {
        return runCatching {
            val uri = URI(realUrl.trim())
            val host = uri.host ?: realUrl
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore('/')
            host.lowercase(Locale.US)
                .removePrefix("www.")
                .removePrefix("m.")
                .trim()
        }.getOrElse {
            realUrl.removePrefix("https://")
                .removePrefix("http://")
                .substringBefore('/')
                .removePrefix("www.")
                .lowercase(Locale.US)
        }
    }

    fun buildGoogleFaviconUrl(domain: String): String {
        val clean = domain.ifBlank { "google.com" }
        return "${BackendEndpoint.GOOGLE_FAVICON_ENDPOINT}$clean"
    }
}
