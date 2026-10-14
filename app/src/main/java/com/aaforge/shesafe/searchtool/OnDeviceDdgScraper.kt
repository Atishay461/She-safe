package com.aaforge.shesafe.searchtool

import com.aaforge.shesafe.core.BackendEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection
import java.net.URL

data class ScrapedWebSource(
    val domain: String,
    val url: String,
    val title: String,
    val snippet: String,
    val faviconUrl: String
)

object OnDeviceDdgScraper {
    private val linkRegex = Regex(
        """<a[^>]*class="[^"]*result__a[^"]*"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val snippetRegex = Regex(
        """<a[^>]*class="[^"]*result__snippet[^"]*"[^>]*>(.*?)</a>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val htmlTagStripRegex = Regex("""<[^>]+>""")

    suspend fun scrapeTopSources(query: String, maxResults: Int = 5): List<ScrapedWebSource> =
        withContext(Dispatchers.IO) {
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val postBody = "q=$encodedQuery&kl=in-en"
            val conn = (URL(BackendEndpoint.DDG_HTML_ENDPOINT).openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 6000
                readTimeout = 6000
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36"
                )
            }

            try {
                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(postBody)
                    writer.flush()
                }
                val html = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use {
                    it.readText()
                }
                parseDdgHtml(html, maxResults)
            } catch (e: Exception) {
                emptyList()
            } finally {
                conn.disconnect()
            }
        }

    private fun parseDdgHtml(html: String, maxResults: Int): List<ScrapedWebSource> {
        val linkMatches = linkRegex.findAll(html).toList()
        val snippetMatches = snippetRegex.findAll(html).toList()
        val results = mutableListOf<ScrapedWebSource>()
        val seenDomains = mutableSetOf<String>()

        for (i in linkMatches.indices) {
            if (results.size >= maxResults) break
            val rawHref = linkMatches[i].groupValues[1]
            val rawTitle = linkMatches[i].groupValues[2]
            val realUrl = UddgLinkDecoder.unwrap(rawHref)
            val domain = DomainExtractor.extractCleanDomain(realUrl)
            if (domain.isBlank() || domain.contains("duckduckgo.com") || !seenDomains.add(domain)) continue

            val cleanTitle = stripHtml(rawTitle)
            val cleanSnippet = if (i < snippetMatches.size) stripHtml(snippetMatches[i].groupValues[1]) else ""
            results.add(
                ScrapedWebSource(
                    domain = domain,
                    url = realUrl,
                    title = cleanTitle,
                    snippet = cleanSnippet,
                    faviconUrl = DomainExtractor.buildGoogleFaviconUrl(domain)
                )
            )
        }
        return results
    }

    private fun stripHtml(raw: String): String =
        raw.replace(htmlTagStripRegex, "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#x27;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
}
