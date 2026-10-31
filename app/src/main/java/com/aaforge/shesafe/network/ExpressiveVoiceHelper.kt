package com.aaforge.shesafe.network

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

object ExpressiveVoiceHelper {
    fun pcmToWav(pcmData: ByteArray, sampleRate: Int = 16000): ByteArray {
        val out = ByteArrayOutputStream(pcmData.size + 44)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(pcmData.size + 36)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16); putShort(1); putShort(1)
            putInt(sampleRate); putInt(sampleRate * 2)
            putShort(2); putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmData.size)
        }.array()
        out.write(header); out.write(pcmData)
        return out.toByteArray()
    }

    fun calcRms(buf: ByteArray, offset: Int = 0, len: Int = buf.size): Float {
        val samples = len / 2
        if (samples <= 0) return 0f
        var sum = 0.0
        var i = offset
        val limit = (offset + len).coerceAtMost(buf.size - 1)
        while (i < limit) {
            val s = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort().toDouble()
            sum += s * s; i += 2
        }
        return ((sqrt(sum / samples) / 7500.0).toFloat()).coerceIn(0f, 1f)
    }

    fun readWavSampleRate(wavBytes: ByteArray): Int {
        if (wavBytes.size < 28) return 24000
        return ByteBuffer.wrap(wavBytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int.coerceIn(8000, 48000)
    }

    fun selectBestNeuralVoice(tts: TextToSpeech) {
        runCatching {
            tts.language = Locale("en", "IN")
            val voices = tts.voices ?: return@runCatching
            val best = voices.filter { v ->
                val langOk = v.locale.language == "en" && (v.locale.country == "IN" || v.locale.country == "US" || v.locale.country == "GB")
                val name = v.name.lowercase()
                langOk && !name.contains("male") && (name.contains("network") || name.contains("wavenet") || name.contains("neural") || v.quality >= 400)
            }.maxByOrNull { it.quality }
            if (best != null) tts.voice = best
        }
    }

    fun applyToneAndClean(tts: TextToSpeech?, rawReply: String): String {
        val upper = rawReply.uppercase()
        tts?.let { engine ->
            when {
                upper.contains("[TONE:URGENT]") -> {
                    engine.setPitch(1.12f)
                    engine.setSpeechRate(1.12f)
                }
                upper.contains("[TONE:DEEP]") -> {
                    engine.setPitch(0.92f)
                    engine.setSpeechRate(0.94f)
                }
                else -> {
                    engine.setPitch(1.04f)
                    engine.setSpeechRate(0.98f)
                }
            }
        }
        return rawReply
            .replace(Regex("""\[TONE:[A-Z]+\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<[^>]+>|\[/?content\]"""), "")
            .trim()
    }

    suspend fun speakDirectlyWithVisualizer(
        tts: TextToSpeech?,
        cleanText: String,
        onOutputLevel: (Float) -> Unit
    ) {
        val engine = tts ?: return
        if (cleanText.isBlank()) return
        val doneDeferred = CompletableDeferred<Boolean>()
        val uttId = "utt_direct_${System.currentTimeMillis()}"

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { doneDeferred.complete(true) }
            @Deprecated("Deprecated") override fun onError(id: String?) { doneDeferred.complete(false) }
            override fun onError(id: String?, code: Int) { doneDeferred.complete(false) }
        })

        if (engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, Bundle(), uttId) != TextToSpeech.SUCCESS) {
            doneDeferred.complete(false)
            return
        }

        var tick = 0
        withTimeoutOrNull(18000L) {
            while (!doneDeferred.isCompleted) {
                onOutputLevel((0.35f + 0.45f * abs(sin(tick * 0.45))).toFloat())
                tick++
                delay(70L)
            }
        }
        onOutputLevel(0f)
    }

    suspend fun synthesizeExpressiveWav(context: Context, tts: TextToSpeech?, rawReply: String): Pair<String, ByteArray?> {
        val engine = tts ?: return Pair(rawReply, null)
        val cleanText = applyToneAndClean(engine, rawReply)
        if (cleanText.isBlank()) return Pair("", null)

        val tempFile = File(context.cacheDir, "mella_expressive.wav")
        if (tempFile.exists()) tempFile.delete()
        val deferred = CompletableDeferred<Boolean>()
        val uttId = "utt_${System.currentTimeMillis()}"

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { deferred.complete(true) }
            @Deprecated("Deprecated") override fun onError(id: String?) { deferred.complete(false) }
            override fun onError(id: String?, code: Int) { deferred.complete(false) }
        })

        if (engine.synthesizeToFile(cleanText, Bundle(), tempFile, uttId) != TextToSpeech.SUCCESS) {
            return Pair(cleanText, null)
        }
        val ok = withTimeoutOrNull(5000L) { deferred.await() } ?: false
        val bytes = if (ok && tempFile.exists() && tempFile.length() > 44) tempFile.readBytes() else null
        return Pair(cleanText, bytes)
    }
}
