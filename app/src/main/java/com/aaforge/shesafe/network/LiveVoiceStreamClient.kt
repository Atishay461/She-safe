package com.aaforge.shesafe.network

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs

class LiveVoiceStreamClient {
    private val sampleRate: Int = 16000
    private val channelIn: Int = AudioFormat.CHANNEL_IN_MONO
    private val channelOut: Int = AudioFormat.CHANNEL_OUT_MONO
    private val pcmFormat: Int = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize: Int = AudioRecord.getMinBufferSize(sampleRate, channelIn, pcmFormat)

    @Volatile private var isSessionActive: Boolean = false
    private var recorder: AudioRecord? = null
    private var tracker: AudioTrack? = null

    @SuppressLint("MissingPermission")
    suspend fun startVoiceEngine(onAmplitudeLevel: (Float) -> Unit) = withContext(Dispatchers.IO) {
        isSessionActive = true
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, sampleRate, channelIn, pcmFormat, bufferSize)
        val audioAttr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val audioFmt = AudioFormat.Builder()
            .setEncoding(pcmFormat)
            .setSampleRate(sampleRate)
            .setChannelMask(channelOut)
            .build()
        val trk = AudioTrack.Builder()
            .setAudioAttributes(audioAttr)
            .setAudioFormat(audioFmt)
            .setBufferSizeInBytes(bufferSize)
            .build()

        recorder = rec
        tracker = trk
        rec.startRecording()
        trk.play()

        val buffer = ByteArray(bufferSize)
        while (isActive && isSessionActive) {
            val bytesRead = rec.read(buffer, 0, buffer.size)
            if (bytesRead > 0) {
                var maxSample = 0
                for (i in 0 until bytesRead step 2) {
                    val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
                    val absVal = abs(sample.toShort().toInt())
                    if (absVal > maxSample) maxSample = absVal
                }
                onAmplitudeLevel((maxSample / 32768f).coerceIn(0f, 1f))
            }
        }
    }

    fun stopVoiceEngine() {
        isSessionActive = false
        recorder?.run { runCatching { stop(); release() } }
        tracker?.run { runCatching { stop(); release() } }
        recorder = null
        tracker = null
    }
}
