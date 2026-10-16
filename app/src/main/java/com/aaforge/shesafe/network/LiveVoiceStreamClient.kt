package com.aaforge.shesafe.network

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.speech.tts.TextToSpeech
import android.util.Base64
import com.aaforge.shesafe.core.BackendEndpoint
import com.aaforge.shesafe.core.DateTimeProvider
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

enum class VoiceUiState { USER_LISTENING, PROCESSING, MODEL_SPEAKING }

class LiveVoiceStreamClient {
    private val inRate = 16000
    @Volatile private var active = false
    @Volatile var isMicMuted = false; private set
    @Volatile private var forceProcessTurn = false
    private var recorder: AudioRecord? = null
    private var activePlayer: AudioTrack? = null
    private var ttsEngine: TextToSpeech? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null

    fun setMuted(muted: Boolean) {
        isMicMuted = muted
        if (muted) forceProcessTurn = true
    }

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
        // IMMEDIATELY enter Listening state on tap (Never stuck on Processing!)
        onStateChange(VoiceUiState.USER_LISTENING)

        ttsEngine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) ttsEngine?.let { ExpressiveVoiceHelper.selectBestNeuralVoice(it) }
        }

        val inBufSize = AudioRecord.getMinBufferSize(inRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(3200)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, inRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, inBufSize * 2)
        recorder = rec
        runCatching {
            if (AcousticEchoCanceler.isAvailable()) aec = AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = true }
            if (NoiseSuppressor.isAvailable()) ns = NoiseSuppressor.create(rec.audioSessionId)?.apply { enabled = true }
        }
        rec.startRecording()

        val micBuf = ByteArray(1600)
        val rollingPreRoll = ArrayDeque<ByteArray>(8)
        val speechAccumulator = ByteArrayOutputStream()
        var hasSpoken = false
        var lastVoiceMs = System.currentTimeMillis()
        var noiseFloor = 0.015f

        while (isActive && active) {
            val read = rec.read(micBuf, 0, micBuf.size)
            if (read <= 0) continue
            val frameCopy = micBuf.copyOf(read)
            val rawRms = if (isMicMuted) 0f else ExpressiveVoiceHelper.calcRms(frameCopy, 0, read)

            if (!hasSpoken && !isMicMuted) {
                noiseFloor = (noiseFloor * 0.92f + rawRms * 0.08f).coerceIn(0.008f, 0.045f)
            }
            val startThresh = (noiseFloor + 0.040f).coerceAtLeast(0.052f)
            val sustainThresh = (noiseFloor + 0.015f).coerceAtLeast(0.024f)
            val visualLevel = ((rawRms - noiseFloor).coerceAtLeast(0f) * 2.3f).coerceIn(0f, 1f)
            onInputLevel(if (isMicMuted) 0f else visualLevel)
            val now = System.currentTimeMillis()

            if (!hasSpoken) {
                if (rollingPreRoll.size >= 7) rollingPreRoll.removeFirst()
                rollingPreRoll.addLast(frameCopy)
                if (!isMicMuted && rawRms >= startThresh) {
                    hasSpoken = true; lastVoiceMs = now
                    rollingPreRoll.forEach { speechAccumulator.write(it) }
                    rollingPreRoll.clear()
                }
            } else {
                speechAccumulator.write(frameCopy)
                if (!isMicMuted && rawRms >= sustainThresh) lastVoiceMs = now
            }

            val silenceMs = now - lastVoiceMs
            val shouldSend = hasSpoken && speechAccumulator.size() >= 20000 &&
                    (silenceMs >= 1500L || speechAccumulator.size() >= 768000 || forceProcessTurn)

            if (shouldSend) {
                forceProcessTurn = false; hasSpoken = false; onInputLevel(0f)
                onStateChange(VoiceUiState.PROCESSING)
                val wavBytes = ExpressiveVoiceHelper.pcmToWav(speechAccumulator.toByteArray(), inRate)
                speechAccumulator.reset()

                val rawReply = sendAudioTurnToCloudflare(wavBytes, history)
                if (rawReply.isNotBlank() && active) {
                    val (cleanReply, synthWav) = ExpressiveVoiceHelper.synthesizeExpressiveWav(context, ttsEngine, rawReply)
                    if (cleanReply.isNotBlank()) {
                        history.add(ChatHistoryTurn("model", cleanReply))
                        onTranscriptTurn(false, "<p>$cleanReply</p>")
                    }
                    if (synthWav != null && active) {
                        onStateChange(VoiceUiState.MODEL_SPEAKING)
                        val interruptedAudio = playStaticZeroStutter(synthWav, rec, startThresh, onOutputLevel)
                        onOutputLevel(0f)
                        if (interruptedAudio != null && active) {
                            hasSpoken = true; lastVoiceMs = System.currentTimeMillis()
                            speechAccumulator.write(interruptedAudio)
                        }
                    }
                }
                if (active) onStateChange(VoiceUiState.USER_LISTENING)
            }
        }
    }

    private suspend fun playStaticZeroStutter(
        wav: ByteArray,
        rec: AudioRecord,
        bargeThreshold: Float,
        onOut: (Float) -> Unit
    ): ByteArray? = coroutineScope {
        val sr = ExpressiveVoiceHelper.readWavSampleRate(wav)
        val pcmLen = (wav.size - 44).coerceAtLeast(0)
        if (pcmLen <= 0) return@coroutineScope null
        val totalFrames = pcmLen / 2

        // MODE_STATIC pre-loads entire audio into hardware buffer -> 0% stutter guaranteed
        val trk = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(pcmLen)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        trk.write(wav, 44, pcmLen)
        activePlayer = trk
        trk.play()

        val interrupted = AtomicBoolean(false)
        val capturedInterrupt = ByteArrayOutputStream()
        val mBuf = ByteArray(1600)
        var bargeFrames = 0

        while (isActive && active && !interrupted.get()) {
            val head = trk.playbackHeadPosition
            if (head >= totalFrames) break
            val bytePos = (44 + head * 2).coerceIn(44, (wav.size - 1200).coerceAtLeast(44))
            val window = (wav.size - bytePos).coerceAtMost(1200)
            onOut(if (window > 0) ExpressiveVoiceHelper.calcRms(wav, bytePos, window) else 0f)

            val r = rec.read(mBuf, 0, mBuf.size)
            if (!isMicMuted && r > 0) {
                if (ExpressiveVoiceHelper.calcRms(mBuf, 0, r) >= (bargeThreshold + 0.045f)) {
                    bargeFrames++
                    capturedInterrupt.write(mBuf, 0, r)
                    if (bargeFrames >= 4) {
                        interrupted.set(true)
                        runCatching { trk.pause(); trk.flush() }
                        break
                    }
                } else {
                    bargeFrames = 0; capturedInterrupt.reset()
                }
            }
        }
        runCatching { trk.stop(); trk.release() }; activePlayer = null
        if (interrupted.get()) capturedInterrupt.toByteArray() else null
    }

    private fun sendAudioTurnToCloudflare(wavBytes: ByteArray, history: List<ChatHistoryTurn>): String {
        var conn: HttpsURLConnection? = null
        return try {
            val body = JSONObject().apply {
                put("voice_mode", true)
                put("audio_base64", Base64.encodeToString(wavBytes, Base64.NO_WRAP))
                put("user_prompt", "Spoken audio turn")
                put("current_datetime", DateTimeProvider.nowFormatted())
                put("history", JSONArray().apply { history.takeLast(6).forEach { put(JSONObject().put("role", it.role).put("content", it.content)) } })
            }.toString()
            conn = (URL(BackendEndpoint.STREAM_ENDPOINT).openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 8000; readTimeout = 20000; doInput = true; doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body); it.flush() }
            val sb = StringBuilder()
            BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { r ->
                var line: String?
                while (r.readLine().also { line = it } != null) {
                    val t = line!!.trim()
                    if (t.equals("[Done]", true)) break
                    sb.append(t.replace(Regex("""\[/?content\]|<[^>]+>"""), ""))
                }
            }
            sb.toString().trim()
        } catch (e: Exception) { "" } finally { conn?.disconnect() }
    }

    fun stopVoiceEngine() {
        active = false
        runCatching { aec?.release(); ns?.release() }
        runCatching { activePlayer?.stop(); activePlayer?.release() }
        runCatching { recorder?.stop(); recorder?.release() }
        runCatching { ttsEngine?.stop(); ttsEngine?.shutdown() }
        aec = null; ns = null; activePlayer = null; recorder = null; ttsEngine = null
    }
}
