package com.aaforge.shesafe

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aaforge.shesafe.data.*
import com.aaforge.shesafe.network.*
import com.aaforge.shesafe.parser.*
import com.aaforge.shesafe.searchtool.*
import com.aaforge.shesafe.ui.screens.*
import com.aaforge.shesafe.ui.theme.SheSafeTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val voiceClient = LiveVoiceStreamClient()
    private var activeStreamJob: Job? = null
    private var activeVoiceJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val audioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

        setContent {
            SheSafeTheme {
                val savedSessions = remember { mutableStateListOf<StoredChatSession>().apply { addAll(LocalChatStore.loadAllSessions(this@MainActivity)) } }
                var activeSessionId by remember { mutableStateOf(UUID.randomUUID().toString()) }
                var activeChatTitle by remember { mutableStateOf("New Safe Chat") }
                val messages = remember { mutableStateListOf<UiChatMessage>() }
                var isStreaming by remember { mutableStateOf(false) }
                var inVoiceMode by remember { mutableStateOf(false) }
                var isMicMuted by remember { mutableStateOf(false) }
                var voiceUiState by remember { mutableStateOf(VoiceUiState.USER_LISTENING) }
                var inputWave by remember { mutableFloatStateOf(0f) }
                var outputWave by remember { mutableFloatStateOf(0f) }

                fun persistCurrentState() {
                    val records = messages.map {
                        StoredMessageRecord(isUser = it.isUser, rawContent = if (it.isUser) it.text else it.rawXml)
                    }
                    if (records.isNotEmpty()) {
                        val updated = StoredChatSession(activeSessionId, activeChatTitle, System.currentTimeMillis(), records)
                        LocalChatStore.upsertSession(this@MainActivity, updated)
                        savedSessions.clear()
                        savedSessions.addAll(LocalChatStore.loadAllSessions(this@MainActivity))
                    }
                }

                if (inVoiceMode) {
                    VoiceCallScreen(
                        uiState = voiceUiState,
                        isMuted = isMicMuted,
                        inputLevel = inputWave,
                        outputLevel = outputWave,
                        onToggleMute = {
                            val nextMute = !isMicMuted
                            isMicMuted = nextMute
                            voiceClient.setMuted(nextMute)
                        },
                        onEndCall = {
                            voiceClient.stopVoiceEngine()
                            activeVoiceJob?.cancel()
                            inVoiceMode = false
                            isMicMuted = false
                            persistCurrentState()
                        }
                    )
                } else {
                    ChatScreen(
                        messages = messages,
                        savedSessions = savedSessions,
                        activeSessionId = activeSessionId,
                        isStreaming = isStreaming,
                        onNewSession = {
                            activeStreamJob?.cancel(); isStreaming = false
                            activeSessionId = UUID.randomUUID().toString()
                            activeChatTitle = "New Safe Chat"; messages.clear()
                        },
                        onSelectSession = { session ->
                            activeStreamJob?.cancel(); isStreaming = false
                            activeSessionId = session.sessionId; activeChatTitle = session.chatName
                            messages.clear()
                            session.messages.forEach { rec ->
                                if (rec.isUser) messages.add(UiChatMessage(isUser = true, text = rec.rawContent))
                                else messages.add(UiChatMessage(isUser = false, rawXml = rec.rawContent, agentTurn = XmlBlockParser.parseXmlStream(rec.rawContent)))
                            }
                        },
                        onStopStreaming = {
                            activeStreamJob?.cancel(); activeStreamJob = null
                            if (messages.isNotEmpty() && !messages.last().isUser) {
                                val last = messages.last()
                                messages[messages.lastIndex] = last.copy(agentTurn = last.agentTurn?.copy(isCurrentlyThinking = false))
                            }
                            isStreaming = false; persistCurrentState()
                        },
                        onSendMessage = { prompt ->
                            activeStreamJob?.cancel()
                            messages.add(UiChatMessage(isUser = true, text = prompt))
                            val buffer = StreamChunkBuffer()
                            val agentIdx = messages.size
                            messages.add(UiChatMessage(isUser = false, agentTurn = ParsedAgentTurn(isCurrentlyThinking = true)))
                            isStreaming = true

                            activeStreamJob = lifecycleScope.launch {
                                val history = messages.dropLast(1).map {
                                    ChatHistoryTurn(if (it.isUser) "user" else "model", if (it.isUser) it.text else it.rawXml)
                                }
                                CloudflareStreamClient.streamPrompt(prompt, history).collect { frame ->
                                    when (frame) {
                                        is StreamFrame.ContentChunk -> {
                                            buffer.appendFrame(frame.rawLine)
                                            val rawXml = buffer.getSafeRenderableXml()
                                            val parsed = XmlBlockParser.parseXmlStream(rawXml)
                                            parsed.interceptedChatName?.let { activeChatTitle = it }
                                            parsed.searchQueryTrigger?.let { q ->
                                                SearchToolOrchestrator.executeOnDeviceSearch(q) { found ->
                                                    messages[agentIdx] = messages[agentIdx].copy(sources = found)
                                                }
                                            }
                                            messages[agentIdx] = messages[agentIdx].copy(rawXml = rawXml, agentTurn = parsed)
                                        }
                                        is StreamFrame.Done, is StreamFrame.Error -> {
                                            isStreaming = false; persistCurrentState()
                                        }
                                    }
                                }
                            }
                        },
                        onStartVoiceCall = {
                            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                isMicMuted = false
                                inVoiceMode = true
                                val history = messages.map { ChatHistoryTurn(if (it.isUser) "user" else "model", if (it.isUser) it.text else it.rawXml) }.toMutableList()
                                activeVoiceJob = lifecycleScope.launch {
                                    voiceClient.startLiveSession(
                                        context = this@MainActivity,
                                        history = history,
                                        onStateChange = { voiceUiState = it },
                                        onInputLevel = { inputWave = it },
                                        onOutputLevel = { outputWave = it },
                                        onTranscriptTurn = { isUser, content ->
                                            runOnUiThread {
                                                if (isUser) messages.add(UiChatMessage(isUser = true, text = content))
                                                else messages.add(UiChatMessage(isUser = false, rawXml = content, agentTurn = XmlBlockParser.parseXmlStream(content)))
                                                persistCurrentState()
                                            }
                                        }
                                    )
                                }
                            } else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    )
                }
            }
        }
    }
}
