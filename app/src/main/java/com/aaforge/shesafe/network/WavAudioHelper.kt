package com.aaforge.shesafe.network

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.sqrt

object WavAudioHelper {
    fun pcmToWav(pcmData: ByteArray, sampleRate: Int = 16000): ByteArray {
        val out = ByteArrayOutputStream(pcmData.size + 44)
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(sampleRate)
            putInt(byteRate)
            putShort(2)
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmData.size)
        }.array()
        out.write(header)
        out.write(pcmData)
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
            sum += s * s
            i += 2
        }
        return ((sqrt(sum / samples) / 9500.0).toFloat()).coerceIn(0f, 1f)
    }

    fun readWavSampleRate(wavBytes: ByteArray): Int {
        if (wavBytes.size < 28) return 22050
        return ByteBuffer.wrap(wavBytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int.coerceIn(8000, 48000)
    }

    suspend fun synthesizeToWavBytes(context: Context, tts: TextToSpeech?, text: String): ByteArray? {
        val engine = tts ?: return null
        val cleanText = text.replace(Regex("""<[^>]+>|\[/?content\]"""), "").trim()
        if (cleanText.isBlank()) return null

        val tempFile = File(context.cacheDir, "mella_tts_out.wav")
        if (tempFile.exists()) tempFile.delete()
        val deferred = CompletableDeferred<Boolean>()
        val uttId = "mella_utt_${System.currentTimeMillis()}"

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { deferred.complete(true) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { deferred.complete(false) }
            override fun onError(utteranceId: String?, errorCode: Int) { deferred.complete(false) }
        })

        val res = engine.synthesizeToFile(cleanText, Bundle(), tempFile, uttId)
        if (res != TextToSpeech.SUCCESS) return null

        val ok = withTimeoutOrNull(8000L) { deferred.await() } ?: false
        return if (ok && tempFile.exists() && tempFile.length() > 44) tempFile.readBytes() else null
    }

    fun configureVoice(tts: TextToSpeech) {
        runCatching {
            tts.language = Locale("en", "IN")
            tts.setPitch(1.04f)
            tts.setSpeechRate(0.98f)
        }
    }
}
