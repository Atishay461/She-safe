package com.aaforge.shesafe.network

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Base64
import com.aaforge.shesafe.core.BackendEndpoint
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sqrt

enum class VoiceUiState { USER_LISTENING, PROCESSING, MODEL_SPEAKING }

class LiveVoiceStreamClient {
    private val inRate = 16000
    private val outRate = 24000
    @Volatile private var active = false
    @Volatile var isMicMuted = false; private set
    private var ws: NativeVoiceWebSocket? = null
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null

    fun setMuted(muted: Boolean) { isMicMuted = muted }

    @SuppressLint("MissingPermission")
    suspend fun startLiveSession(
        context: Context,
        history: MutableList<ChatHistoryTurn>,
        onStateChange: (VoiceUiState) -> Unit,
        onInputLevel: (Float) -> Unit,
        onOutputLevel: (Float) -> Unit,
        onTranscriptTurn: (Boolean, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        active = true; isMicMuted = false
        val histJson = JSONArray().apply {
            history.takeLast(6).forEach { put(JSONObject().put("role", it.role).put("content", it.content)) }
        }.toString()
        val encodedHist = URLEncoder.encode(histJson, "UTF-8")
        val modelsToTry = listOf(
            "models/gemini-2.5-flash-preview-native-audio-dialog",
            "models/gemini-2.0-flash-live-001"
        )

        var conn: NativeVoiceWebSocket? = null
        for (modelName in modelsToTry) {
            val url = "${BackendEndpoint.VOICE_WS_ENDPOINT}?model=${URLEncoder.encode(modelName, "UTF-8")}&history=$encodedHist"
            val candidate = NativeVoiceWebSocket(url)
            if (runCatching { candidate.connect() }.isSuccess) {
                conn = candidate; break
            }
        }
        if (conn == null) { stopVoiceEngine(); return@withContext }
        ws = conn

        val inBufSize = AudioRecord.getMinBufferSize(inRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(3200)
        val outBufSize = AudioTrack.getMinBufferSize(outRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(9600)

        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, inRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, inBufSize * 2)
        val trk = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(outRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(outBufSize * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        recorder = rec; player = trk
        runCatching {
            if (AcousticEchoCanceler.isAvailable()) aec = AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = true }
            if (NoiseSuppressor.isAvailable()) ns = NoiseSuppressor.create(rec.audioSessionId)?.apply { enabled = true }
        }
        rec.startRecording(); trk.play()

        val pcmOutQueue = LinkedBlockingQueue<ByteArray>()
        val stateRef = AtomicReference(VoiceUiState.USER_LISTENING)
        onStateChange(VoiceUiState.USER_LISTENING)

        val speakerJob = launch(Dispatchers.IO) {
            while (isActive && active) {
                val chunk = pcmOutQueue.poll(40, TimeUnit.MILLISECONDS)
                if (chunk != null) {
                    if (stateRef.get() != VoiceUiState.MODEL_SPEAKING) {
                        stateRef.set(VoiceUiState.MODEL_SPEAKING)
                        onStateChange(VoiceUiState.MODEL_SPEAKING)
                    }
                    var off = 0
                    while (off < chunk.size && active) {
                        val step = (chunk.size - off).coerceAtMost(2400)
                        onOutputLevel(calcRms(chunk, off, step))
                        trk.write(chunk, off, step)
                        off += step
                    }
                } else if (stateRef.get() == VoiceUiState.MODEL_SPEAKING && pcmOutQueue.isEmpty()) {
                    onOutputLevel(0f)
                    stateRef.set(VoiceUiState.USER_LISTENING)
                    onStateChange(VoiceUiState.USER_LISTENING)
                }
            }
        }

        val readerJob = launch(Dispatchers.IO) {
            val transcriptSb = StringBuilder()
            while (isActive && active && conn.isConnected) {
                val frame = runCatching { conn.readNextFrame() }.getOrNull() ?: break
                if (frame.isBlank()) continue
                runCatching {
                    val json = JSONObject(frame)
                    val sc = json.optJSONObject("serverContent") ?: return@runCatching
                    if (sc.optBoolean("interrupted", false)) {
                        pcmOutQueue.clear()
                        runCatching { trk.pause(); trk.flush(); trk.play() }
                        onOutputLevel(0f)
                        stateRef.set(VoiceUiState.USER_LISTENING)
                        onStateChange(VoiceUiState.USER_LISTENING)
                    }
                    val parts = sc.optJSONObject("modelTurn")?.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val p = parts.getJSONObject(i)
                            val txt = p.optString("text", "")
                            if (txt.isNotBlank()) transcriptSb.append(txt)
                            val b64 = p.optJSONObject("inlineData")?.optString("data", "") ?: ""
                            if (b64.isNotEmpty()) {
                                pcmOutQueue.offer(Base64.decode(b64, Base64.DEFAULT))
                            }
                        }
                    }
                    if (sc.optBoolean("turnComplete", false)) {
                        if (transcriptSb.isNotBlank()) {
                            onTranscriptTurn(false, "<p>${transcriptSb.toString().trim()}</p>")
                            transcriptSb.clear()
                        }
                    }
                }
            }
        }

        val micBuf = ByteArray(3200)
        var userSpokeInTurn = false
        var lastVoiceMs = System.currentTimeMillis()
        var noiseFloor = 0.015f

        while (isActive && active && conn.isConnected) {
            val read = rec.read(micBuf, 0, micBuf.size)
            if (read <= 0) continue

            val rms = if (isMicMuted) 0f else calcRms(micBuf, 0, read)
            if (!userSpokeInTurn && !isMicMuted && stateRef.get() == VoiceUiState.USER_LISTENING) {
                noiseFloor = (noiseFloor * 0.92f + rms * 0.08f).coerceIn(0.008f, 0.045f)
            }
            val startThresh = (noiseFloor + 0.042f).coerceAtLeast(0.055f)
            val sustainThresh = (noiseFloor + 0.016f).coerceAtLeast(0.025f)
            onInputLevel(if (isMicMuted) 0f else ((rms - noiseFloor).coerceAtLeast(0f) * 2.2f).coerceIn(0f, 1f))

            val now = System.currentTimeMillis()
            if (!isMicMuted) {
                if (rms >= startThresh) {
                    if (stateRef.get() == VoiceUiState.MODEL_SPEAKING && rms >= startThresh + 0.04f) {
                        pcmOutQueue.clear()
                        runCatching { trk.pause(); trk.flush(); trk.play() }
                        stateRef.set(VoiceUiState.USER_LISTENING)
                        onStateChange(VoiceUiState.USER_LISTENING)
                    }
                    userSpokeInTurn = true
                    lastVoiceMs = now
                } else if (userSpokeInTurn && rms >= sustainThresh) {
                    lastVoiceMs = now
                } else if (userSpokeInTurn && stateRef.get() == VoiceUiState.USER_LISTENING && (now - lastVoiceMs) > 1500L) {
                    userSpokeInTurn = false
                    stateRef.set(VoiceUiState.PROCESSING)
                    onStateChange(VoiceUiState.PROCESSING)
                }

                val b64 = Base64.encodeToString(micBuf.copyOf(read), Base64.NO_WRAP)
                val packet = """{"realtimeInput":{"mediaChunks":[{"mimeType":"audio/pcm;rate=16000","data":"$b64"}]}}"""
                runCatching { conn.sendTextFrame(packet) }
            }
        }
        speakerJob.cancel(); readerJob.cancel()
    }

    private fun calcRms(buf: ByteArray, offset: Int, len: Int): Float {
        val samples = len / 2
        if (samples <= 0) return 0f
        var sum = 0.0
        var i = offset
        val limit = (offset + len).coerceAtMost(buf.size - 1)
        while (i < limit) {
            val s = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort().toDouble()
            sum += s * s; i += 2
        }
        return ((sqrt(sum / samples) / 9500.0).toFloat()).coerceIn(0f, 1f)
    }

    fun stopVoiceEngine() {
        active = false
        runCatching { ws?.close() }
        runCatching { aec?.release(); ns?.release() }
        runCatching { player?.stop(); player?.release() }
        runCatching { recorder?.stop(); recorder?.release() }
        ws = null; aec = null; ns = null; player = null; recorder = null
    }
}
