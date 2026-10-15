package com.aaforge.shesafe.network

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
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
import javax.net.ssl.HttpsURLConnection

enum class VoiceUiState {
    USER_LISTENING,
    PROCESSING,
    MODEL_SPEAKING
}

class LiveVoiceStreamClient {
    private val inRate = 16000
    @Volatile private var active = false
    @Volatile var isMicMuted = false
        private set
    @Volatile private var forceProcessTurn = false
    private var recorder: AudioRecord? = null
    private var activePlayer: AudioTrack? = null
    private var ttsEngine: TextToSpeech? = null

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
        active = true
        isMicMuted = false
        ttsEngine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) ttsEngine?.let { WavAudioHelper.configureVoice(it) }
        }

        val inBufSize = AudioRecord.getMinBufferSize(inRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(3200)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, inRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, inBufSize)
        recorder = rec
        rec.startRecording()

        val micBuf = ByteArray(2048)
        val speechAccumulator = ByteArrayOutputStream()
        var hasSpoken = false
        var lastVoiceMs = System.currentTimeMillis()
        onStateChange(VoiceUiState.USER_LISTENING)

        while (isActive && active) {
            val read = rec.read(micBuf, 0, micBuf.size)
            if (read <= 0) continue

            if (isMicMuted) {
                onInputLevel(0f)
                if (hasSpoken && speechAccumulator.size() > 10000) {
                    forceProcessTurn = true
                } else {
                    delay(40)
                    continue
                }
            }

            val rms = if (isMicMuted) 0f else WavAudioHelper.calcRms(micBuf, 0, read)
            onInputLevel(rms)
            val now = System.currentTimeMillis()

            if (!isMicMuted && rms > 0.055f) {
                hasSpoken = true
                lastVoiceMs = now
                speechAccumulator.write(micBuf, 0, read)
            } else if (hasSpoken) {
                speechAccumulator.write(micBuf, 0, read)
            }

            val silenceElapsed = now - lastVoiceMs
            val enoughAudio = speechAccumulator.size() >= 14000
            val shouldTrigger = hasSpoken && enoughAudio && (silenceElapsed > 1050L || speechAccumulator.size() > 380000 || forceProcessTurn)

            if (shouldTrigger) {
                forceProcessTurn = false
                hasSpoken = false
                onInputLevel(0f)
                onStateChange(VoiceUiState.PROCESSING)

                val capturedPcm = speechAccumulator.toByteArray()
                speechAccumulator.reset()
                val wavBytes = WavAudioHelper.pcmToWav(capturedPcm, inRate)
                val replyText = sendVoiceTurnToCloudflare(wavBytes, history)

                if (replyText.isNotBlank() && active) {
                    history.add(ChatHistoryTurn("model", replyText))
                    onTranscriptTurn(false, "<p>$replyText</p>")
                    val synthWav = WavAudioHelper.synthesizeToWavBytes(context, ttsEngine, replyText)
                    if (synthWav != null && active) {
                        onStateChange(VoiceUiState.MODEL_SPEAKING)
                        playWavWithWaveBars(synthWav, rec, micBuf, onOutputLevel)
                    }
                }
                speechAccumulator.reset()
                lastVoiceMs = System.currentTimeMillis()
                if (active) {
                    onOutputLevel(0f)
                    onStateChange(VoiceUiState.USER_LISTENING)
                }
            }
        }
    }

    private fun playWavWithWaveBars(wav: ByteArray, rec: AudioRecord, micBuf: ByteArray, onOutputLevel: (Float) -> Unit) {
        val sampleRate = WavAudioHelper.readWavSampleRate(wav)
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
        val trk = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(minBuf)
            .build()
        activePlayer = trk
        trk.play()
        var offset = 44
        val chunkLen = 2048
        var loudInterruptFrames = 0
        while (active && offset < wav.size) {
            val toWrite = (wav.size - offset).coerceAtMost(chunkLen)
            onOutputLevel(WavAudioHelper.calcRms(wav, offset, toWrite))
            trk.write(wav, offset, toWrite)
            offset += toWrite
            if (!isMicMuted && rec.read(micBuf, 0, micBuf.size, AudioRecord.READ_NON_BLOCKING) > 0) {
                if (WavAudioHelper.calcRms(micBuf) > 0.25f) {
                    loudInterruptFrames++
                    if (loudInterruptFrames >= 4) break
                } else loudInterruptFrames = 0
            }
        }
        runCatching { trk.stop(); trk.release() }
        activePlayer = null
    }

    private fun sendVoiceTurnToCloudflare(wavBytes: ByteArray, history: List<ChatHistoryTurn>): String {
        var conn: HttpsURLConnection? = null
        return try {
            val b64 = Base64.encodeToString(wavBytes, Base64.NO_WRAP)
            val body = JSONObject().apply {
                put("voice_mode", true)
                put("audio_base64", b64)
                put("user_prompt", "Spoken audio turn")
                put("current_datetime", DateTimeProvider.nowFormatted())
                put("history", JSONArray().apply {
                    history.takeLast(6).forEach { put(JSONObject().put("role", it.role).put("content", it.content)) }
                })
            }.toString()

            conn = (URL(BackendEndpoint.STREAM_ENDPOINT).openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 10000; readTimeout = 30000; doInput = true; doOutput = true
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
        runCatching { activePlayer?.stop(); activePlayer?.release() }
        runCatching { recorder?.stop(); recorder?.release() }
        runCatching { ttsEngine?.stop(); ttsEngine?.shutdown() }
        activePlayer = null; recorder = null; ttsEngine = null
    }
}
