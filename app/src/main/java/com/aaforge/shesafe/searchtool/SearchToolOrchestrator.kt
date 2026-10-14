package com.aaforge.shesafe.searchtool

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class SearchExecutionResult(
    val query: String,
    val sources: List<ScrapedWebSource>
)

object SearchToolOrchestrator {
    suspend fun executeOnDeviceSearch(
        query: String,
        onSourcesDiscovered: (List<ScrapedWebSource>) -> Unit
    ): SearchExecutionResult = coroutineScope {
        val scraped = OnDeviceDdgScraper.scrapeTopSources(query = query, maxResults = 5)
        onSourcesDiscovered(scraped)

        scraped.map { src ->
            async { GoogleFaviconFetcher.fetchFavicon(src.faviconUrl) }
        }.awaitAll()

        SearchExecutionResult(
            query = query,
            sources = scraped
        )
    }
}
