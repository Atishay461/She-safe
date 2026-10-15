package com.aaforge.shesafe.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class StoredMessageRecord(
    val isUser: Boolean,
    val rawContent: String
)

data class StoredChatSession(
    val sessionId: String,
    val chatName: String,
    val updatedAtMillis: Long,
    val messages: List<StoredMessageRecord>
)

object LocalChatStore {
    private const val FILE_NAME = "shesafe_private_sessions.json"

    fun loadAllSessions(context: Context): List<StoredChatSession> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return runCatching {
            val rawJson = file.readText()
            val rootArray = JSONArray(rawJson)
            val sessions = mutableListOf<StoredChatSession>()
            for (i in 0 until rootArray.length()) {
                val obj = rootArray.getJSONObject(i)
                val msgArr = obj.optJSONArray("messages") ?: JSONArray()
                val msgs = mutableListOf<StoredMessageRecord>()
                for (j in 0 until msgArr.length()) {
                    val mObj = msgArr.getJSONObject(j)
                    msgs.add(
                        StoredMessageRecord(
                            isUser = mObj.optBoolean("isUser", true),
                            rawContent = mObj.optString("rawContent", "")
                        )
                    )
                }
                sessions.add(
                    StoredChatSession(
                        sessionId = obj.getString("sessionId"),
                        chatName = obj.optString("chatName", "Safe Conversation"),
                        updatedAtMillis = obj.optLong("updatedAtMillis", System.currentTimeMillis()),
                        messages = msgs
                    )
                )
            }
            sessions.sortedByDescending { it.updatedAtMillis }
        }.getOrDefault(emptyList())
    }

    fun upsertSession(context: Context, session: StoredChatSession) {
        val existing = loadAllSessions(context).toMutableList()
        val index = existing.indexOfFirst { it.sessionId == session.sessionId }
        if (index != -1) {
            existing[index] = session
        } else {
            existing.add(0, session)
        }
        persistList(context, existing)
    }

    fun deleteSession(context: Context, sessionId: String) {
        val filtered = loadAllSessions(context).filterNot { it.sessionId == sessionId }
        persistList(context, filtered)
    }

    private fun persistList(context: Context, sessions: List<StoredChatSession>) {
        runCatching {
            val rootArray = JSONArray()
            sessions.forEach { s ->
                val msgArr = JSONArray()
                s.messages.forEach { m ->
                    msgArr.put(
                        JSONObject()
                            .put("isUser", m.isUser)
                            .put("rawContent", m.rawContent)
                    )
                }
                val sObj = JSONObject()
                    .put("sessionId", s.sessionId)
                    .put("chatName", s.chatName)
                    .put("updatedAtMillis", s.updatedAtMillis)
                    .put("messages", msgArr)
                rootArray.put(sObj)
            }
            File(context.filesDir, FILE_NAME).writeText(rootArray.toString())
        }
    }
}
