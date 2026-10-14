package com.aaforge.shesafe.network

import com.aaforge.shesafe.core.BackendEndpoint
import com.aaforge.shesafe.core.DateTimeProvider
import com.aaforge.shesafe.searchtool.ScrapedWebSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

data class ChatHistoryTurn(val role: String, val content: String)

sealed interface StreamFrame {
    data class ContentChunk(val rawLine: String) : StreamFrame
    data object Done : StreamFrame
    data class Error(val reason: String) : StreamFrame
}

object CloudflareStreamClient {
    fun streamPrompt(
        userPrompt: String,
        history: List<ChatHistoryTurn>,
        searchSources: List<ScrapedWebSource>? = null
    ): Flow<StreamFrame> = flow {
        val payloadJson = buildRequestJson(userPrompt, history, searchSources)
        var conn: HttpsURLConnection? = null

        try {
            conn = (URL(BackendEndpoint.STREAM_ENDPOINT).openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 60000
                doInput = true
                doOutput = true
                useCaches = false
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "text/event-stream")
                setRequestProperty("Cache-Control", "no-cache")
            }

            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(payloadJson)
                writer.flush()
            }

            val status = conn.responseCode
            if (status !in 200..299) {
                emit(StreamFrame.Error("Cloudflare HTTP $status"))
                return@flow
            }

            BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val current = line!!.trim()
                    if (current.isEmpty()) continue
                    if (current.equals("[Done]", ignoreCase = true) ||
                        current.equals("data: [Done]", ignoreCase = true)
                    ) {
                        emit(StreamFrame.Done)
                        break
                    }
                    emit(StreamFrame.ContentChunk(current))
                }
            }
        } catch (e: Exception) {
            emit(StreamFrame.Error(e.localizedMessage ?: "Network stream interrupted"))
        } finally {
            conn?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    private fun buildRequestJson(
        userPrompt: String,
        history: List<ChatHistoryTurn>,
        searchSources: List<ScrapedWebSource>?
    ): String {
        val root = JSONObject()
        root.put("user_prompt", userPrompt)
        root.put("current_datetime", DateTimeProvider.nowFormatted())
        val histArray = JSONArray()
        history.forEach { turn ->
            histArray.put(JSONObject().put("role", turn.role).put("content", turn.content))
        }
        root.put("history", histArray)
        if (!searchSources.isNullOrEmpty()) {
            val srcArray = JSONArray()
            searchSources.forEach { src ->
                srcArray.put(
                    JSONObject()
                        .put("domain", src.domain)
                        .put("url", src.url)
                        .put("title", src.title)
                        .put("snippet", src.snippet)
                )
            }
            root.put("search_results", srcArray)
        }
        return root.toString()
    }
}
