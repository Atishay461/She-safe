package com.aaforge.shesafe.network

import android.annotation.SuppressLint
import android.media.*
import android.util.Base64
import com.aaforge.shesafe.core.BackendEndpoint
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.sqrt

enum class VoiceUiState {
    USER_LISTENING,
    PROCESSING,
    MODEL_SPEAKING
}

class LiveVoiceStreamClient {
    private val inRate = 16000
    private val outRate = 24000
    @Volatile private var active = false
    private var ws: RawWebSocketConnection? = null
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null

    @SuppressLint("MissingPermission")
    suspend fun startLiveSession(
        history: List<ChatHistoryTurn>,
        onStateChange: (VoiceUiState) -> Unit,
        onInputLevel: (Float) -> Unit,
        onOutputLevel: (Float) -> Unit,
        onTranscriptTurn: (Boolean, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        active = true
        val histJson = JSONArray().apply {
            history.takeLast(6).forEach {
                put(JSONObject().put("role", it.role).put("content", it.content))
            }
        }.toString()
        val encodedHist = URLEncoder.encode(histJson, "UTF-8")
        val fullUrl = "${BackendEndpoint.VOICE_WS_ENDPOINT}?history=$encodedHist"

        val inBufSize = AudioRecord.getMinBufferSize(inRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(3200)
        val outBufSize = AudioTrack.getMinBufferSize(outRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4800)

        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, inRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, inBufSize)
        val trk = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(outRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(outBufSize * 2)
            .build()

        recorder = rec; player = trk
        val conn = RawWebSocketConnection(fullUrl)
        ws = conn
        runCatching { conn.connect() }.onFailure { stopVoiceEngine(); return@withContext }

        rec.startRecording()
        trk.play()
        onStateChange(VoiceUiState.USER_LISTENING)

        var currentState = VoiceUiState.USER_LISTENING
        var userHasSpoken = false
        var lastSpeechMs = System.currentTimeMillis()
        val transcriptBuffer = StringBuilder()

        val readerJob = launch {
            while (isActive && active && conn.isConnected) {
                val frame = runCatching { conn.readNextTextFrame() }.getOrNull() ?: break
                if (frame.isBlank()) continue
                runCatching {
                    val root = JSONObject(frame)
                    val sc = root.optJSONObject("serverContent") ?: return@runCatching
                    if (sc.optBoolean("interrupted", false)) {
                        trk.pause(); trk.flush(); trk.play()
                        currentState = VoiceUiState.USER_LISTENING
                        onOutputLevel(0f); onStateChange(currentState)
                    }
                    val parts = sc.optJSONObject("modelTurn")?.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val p = parts.getJSONObject(i)
                            val txt = p.optString("text", "")
                            if (txt.isNotBlank()) transcriptBuffer.append(txt)
                            val b64 = p.optJSONObject("inlineData")?.optString("data", "") ?: ""
                            if (b64.isNotEmpty()) {
                                val pcm = Base64.decode(b64, Base64.DEFAULT)
                                currentState = VoiceUiState.MODEL_SPEAKING
                                onStateChange(currentState)
                                onOutputLevel(calcRms(pcm, pcm.size))
                                trk.write(pcm, 0, pcm.size)
                            }
                        }
                    }
                    if (sc.optBoolean("turnComplete", false)) {
                        if (transcriptBuffer.isNotBlank()) {
                            onTranscriptTurn(false, "<p>${transcriptBuffer.toString().trim()}</p>")
                            transcriptBuffer.clear()
                        }
                        onOutputLevel(0f)
                        userHasSpoken = false
                        currentState = VoiceUiState.USER_LISTENING
                        onStateChange(currentState)
                    }
                }
            }
        }

        val micBuf = ByteArray(2048)
        while (isActive && active && conn.isConnected) {
            val read = rec.read(micBuf, 0, micBuf.size)
            if (read > 0) {
                val rms = calcRms(micBuf, read)
                onInputLevel(rms)
                val now = System.currentTimeMillis()
                if (rms > 0.08f) {
                    lastSpeechMs = now
                    userHasSpoken = true
                    if (currentState == VoiceUiState.PROCESSING) {
                        currentState = VoiceUiState.USER_LISTENING
                        onStateChange(currentState)
                    }
                } else if (userHasSpoken && currentState == VoiceUiState.USER_LISTENING && (now - lastSpeechMs) > 1050L) {
                    currentState = VoiceUiState.PROCESSING
                    onStateChange(currentState)
                }
                runCatching { conn.sendBinary(micBuf.copyOf(read)) }
            }
        }
        readerJob.cancel()
    }

    private fun calcRms(buf: ByteArray, len: Int): Float {
        var sum = 0.0
        val samples = len / 2
        if (samples == 0) return 0f
        for (i in 0 until samples) {
            val s = ((buf[i * 2 + 1].toInt() shl 8) or (buf[i * 2].toInt() and 0xFF)).toShort().toDouble()
            sum += s * s
        }
        return ((sqrt(sum / samples) / 11000.0).toFloat()).coerceIn(0f, 1f)
    }

    fun stopVoiceEngine() {
        active = false
        runCatching { ws?.close() }
        recorder?.run { runCatching { stop(); release() } }
        player?.run { runCatching { stop(); release() } }
        ws = null; recorder = null; player = null
    }
}
