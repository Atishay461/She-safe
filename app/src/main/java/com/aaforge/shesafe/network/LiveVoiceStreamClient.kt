package com.aaforge.shesafe.network

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Base64
import com.aaforge.shesafe.core.BackendEndpoint
import com.aaforge.shesafe.core.DateTimeProvider
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
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
        onStateChange(VoiceUiState.PROCESSING)

        val cfg = fetchVoiceSessionConfig(history) ?: run { stopVoiceEngine(); return@withContext }
        val wsUrl = cfg.optString("ws_url", "")
        val sysPrompt = cfg.optString("system_instruction", "")
        val voiceName = cfg.optString("voice_name", "Aoede")
        val modelsArr = cfg.optJSONArray("models") ?: JSONArray().put("models/gemini-2.5-flash-preview-native-audio-dialog")

        var conn: NativeVoiceWebSocket? = null
        val setupReady = AtomicBoolean(false)

        for (i in 0 until modelsArr.length()) {
            val modelId = modelsArr.getString(i)
            val candidate = NativeVoiceWebSocket(wsUrl)
            val ok = runCatching {
                candidate.connect()
                val setupJson = JSONObject().put("setup", JSONObject().apply {
                    put("model", modelId)
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().put("AUDIO"))
                        put("speechConfig", JSONObject().put("voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voiceName))))
                    })
                    put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sysPrompt))))
                }).toString()
                candidate.sendTextFrame(setupJson)
                val firstMsg = withTimeoutOrNull(4500L) { candidate.readNextMessage() } ?: ""
                firstMsg.contains("setupComplete")
            }.getOrDefault(false)

            if (ok) {
                conn = candidate
                setupReady.set(true)
                break
            } else {
                candidate.close()
            }
        }

        if (conn == null || !setupReady.get()) { stopVoiceEngine(); return@withContext }
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
                val chunk = pcmOutQueue.poll(45, TimeUnit.MILLISECONDS)
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
                val frame = runCatching { conn.readNextMessage() }.getOrNull() ?: break
                if (frame.isBlank()) continue
                runCatching {
                    val sc = JSONObject(frame).optJSONObject("serverContent") ?: return@runCatching
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
                            if (b64.isNotEmpty()) pcmOutQueue.offer(Base64.decode(b64, Base64.DEFAULT))
                        }
                    }
                    if (sc.optBoolean("turnComplete", false)) {
                        if (transcriptSb.isNotBlank()) {
                            onTranscriptTurn(false, "<p>${transcriptSb.toString().trim()}</p>")
                            transcriptSb.clear()
                        }
                        if (pcmOutQueue.isEmpty()) {
                            stateRef.set(VoiceUiState.USER_LISTENING)
                            onStateChange(VoiceUiState.USER_LISTENING)
                        }
                    }
                }
            }
        }

        val micBuf = ByteArray(3200)
        var userSpoke = false
        var lastVoiceMs = System.currentTimeMillis()
        var noiseFloor = 0.015f

        while (isActive && active && conn.isConnected) {
            val read = rec.read(micBuf, 0, micBuf.size)
            if (read <= 0) continue

            val rms = if (isMicMuted) 0f else calcRms(micBuf, 0, read)
            if (!userSpoke && !isMicMuted && stateRef.get() == VoiceUiState.USER_LISTENING) {
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
                    userSpoke = true; lastVoiceMs = now
                } else if (userSpoke && rms >= sustainThresh) {
                    lastVoiceMs = now
                } else if (userSpoke && stateRef.get() == VoiceUiState.USER_LISTENING && (now - lastVoiceMs) > 1400L) {
                    userSpoke = false
                    stateRef.set(VoiceUiState.PROCESSING)
                    onStateChange(VoiceUiState.PROCESSING)
                }

                val b64 = Base64.encodeToString(micBuf.copyOf(read), Base64.NO_WRAP)
                val msg = """{"realtimeInput":{"mediaChunks":[{"mimeType":"audio/pcm;rate=16000","data":"$b64"}]}}"""
                runCatching { conn.sendTextFrame(msg) }
            }
        }
        speakerJob.cancel(); readerJob.cancel()
    }

    private fun fetchVoiceSessionConfig(history: List<ChatHistoryTurn>): JSONObject? {
        var conn: HttpsURLConnection? = null
        return try {
            val url = "${BackendEndpoint.CLOUDFLARE_BASE_URL}/voice-config"
            val reqJson = JSONObject().apply {
                put("current_datetime", DateTimeProvider.nowFormatted())
                put("history", JSONArray().apply {
                    history.takeLast(6).forEach { put(JSONObject().put("role", it.role).put("content", it.content)) }
                })
            }.toString()
            conn = (URL(url).openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 8000; readTimeout = 8000; doInput = true; doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(reqJson); it.flush() }
            val text = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { it.readText() }
            JSONObject(text)
        } catch (e: Exception) { null } finally { conn?.disconnect() }
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
