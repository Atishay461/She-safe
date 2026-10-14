package com.aaforge.shesafe.searchtool

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object GoogleFaviconFetcher {
    private val memoryCache = LruCache<String, ImageBitmap>(64)

    fun getCached(faviconUrl: String): ImageBitmap? = memoryCache.get(faviconUrl)

    suspend fun fetchFavicon(faviconUrl: String): ImageBitmap? = withContext(Dispatchers.IO) {
        memoryCache.get(faviconUrl)?.let { return@withContext it }

        var connection: HttpsURLConnection? = null
        try {
            connection = (URL(faviconUrl).openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android 14; Mobile)")
            }
            connection.inputStream.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                bitmap?.asImageBitmap()?.also { imageBitmap ->
                    memoryCache.put(faviconUrl, imageBitmap)
                }
            }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
