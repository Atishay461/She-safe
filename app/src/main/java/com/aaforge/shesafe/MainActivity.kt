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
import com.aaforge.shesafe.network.*
import com.aaforge.shesafe.parser.*
import com.aaforge.shesafe.searchtool.*
import com.aaforge.shesafe.ui.screens.*
import com.aaforge.shesafe.ui.theme.SheSafeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val voiceClient = LiveVoiceStreamClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val audioPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {}

        setContent {
            SheSafeTheme {
                val messages = remember { mutableStateListOf<UiChatMessage>() }
                var isStreaming by remember { mutableStateOf(false) }
                var inVoiceMode by remember { mutableStateOf(false) }
                var amplitude by remember { mutableFloatStateOf(0f) }

                if (inVoiceMode) {
                    VoiceCallScreen(amplitude = amplitude) {
                        voiceClient.stopVoiceEngine()
                        inVoiceMode = false
                    }
                } else {
                    ChatScreen(
                        messages = messages,
                        isStreaming = isStreaming,
                        onSendMessage = { prompt ->
                            messages.add(UiChatMessage(isUser = true, text = prompt))
                            val buffer = StreamChunkBuffer()
                            var activeSources = emptyList<ScrapedWebSource>()
                            val agentMsgIndex = messages.size
                            messages.add(UiChatMessage(isUser = false, agentTurn = ParsedAgentTurn()))
                            isStreaming = true

                            lifecycleScope.launch {
                                val history = messages.filter { it.text.isNotBlank() }
                                    .map { ChatHistoryTurn(if (it.isUser) "user" else "model", it.text) }

                                CloudflareStreamClient.streamPrompt(prompt, history).collect { frame ->
                                    when (frame) {
                                        is StreamFrame.ContentChunk -> {
                                            buffer.appendFrame(frame.rawLine)
                                            val parsed = XmlBlockParser.parseXmlStream(buffer.getSafeRenderableXml())
                                            parsed.searchQueryTrigger?.let { q ->
                                                SearchToolOrchestrator.executeOnDeviceSearch(q) { found ->
                                                    activeSources = found
                                                    messages[agentMsgIndex] = messages[agentMsgIndex].copy(sources = found)
                                                }
                                            }
                                            messages[agentMsgIndex] = messages[agentMsgIndex].copy(agentTurn = parsed)
                                        }
                                        is StreamFrame.Done, is StreamFrame.Error -> isStreaming = false
                                    }
                                }
                            }
                        },
                        onStartVoiceCall = {
                            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                inVoiceMode = true
                                lifecycleScope.launch { voiceClient.startVoiceEngine { amplitude = it } }
                            } else {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    )
                }
            }
        }
    }
}
