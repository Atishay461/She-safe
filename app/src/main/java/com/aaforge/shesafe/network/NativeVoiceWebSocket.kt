package com.aaforge.shesafe.network

import android.util.Base64
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class NativeVoiceWebSocket(private val wsUrl: String) {
    private var socket: SSLSocket? = null
    private var input: BufferedInputStream? = null
    private var output: BufferedOutputStream? = null
    private val random = SecureRandom()
    @Volatile var isConnected: Boolean = false
        private set

    fun connect() {
        val uri = URI(wsUrl)
        val host = uri.host
        val port = if (uri.port != -1) uri.port else 443
        val path = (if (uri.rawPath.isNullOrEmpty()) "/" else uri.rawPath) +
                (if (uri.rawQuery != null) "?${uri.rawQuery}" else "")

        val ssl = (SSLSocketFactory.getDefault().createSocket(host, port) as SSLSocket).apply {
            soTimeout = 0
            keepAlive = true
            tcpNoDelay = true
            sslParameters = sslParameters.apply {
                serverNames = listOf(SNIHostName(host))
            }
            startHandshake()
        }
        val inp = BufferedInputStream(ssl.inputStream, 131072)
        val out = BufferedOutputStream(ssl.outputStream, 65536)

        val keyBytes = ByteArray(16).also { random.nextBytes(it) }
        val secKey = Base64.encodeToString(keyBytes, Base64.NO_WRAP)
        val req = "GET $path HTTP/1.1\r\n" +
                "Host: $host\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: $secKey\r\n" +
                "Sec-WebSocket-Version: 13\r\n\r\n"

        out.write(req.toByteArray(StandardCharsets.UTF_8))
        out.flush()

        val hdr = StringBuilder()
        while (true) {
            val b = inp.read()
            if (b == -1) throw IllegalStateException("EOF during WS handshake")
            hdr.append(b.toChar())
            if (hdr.endsWith("\r\n\r\n")) break
        }
        if (!hdr.toString().contains("101")) {
            ssl.close()
            throw IllegalStateException("Handshake failed: $hdr")
        }
        socket = ssl; input = inp; output = out; isConnected = true
    }

    @Synchronized
    fun sendTextFrame(jsonText: String) {
        val out = output ?: return
        val payload = jsonText.toByteArray(StandardCharsets.UTF_8)
        val len = payload.size
        out.write(0x81)
        when {
            len <= 125 -> out.write(0x80 or len)
            len <= 65535 -> {
                out.write(0x80 or 126)
                out.write((len shr 8) and 0xFF)
                out.write(len and 0xFF)
            }
            else -> {
                out.write(0x80 or 127)
                for (i in 7 downTo 0) out.write(((len.toLong() shr (i * 8)) and 0xFF).toInt())
            }
        }
        val mask = ByteArray(4).also { random.nextBytes(it) }
        out.write(mask)
        for (i in 0 until len) payload[i] = (payload[i].toInt() xor mask[i and 3].toInt()).toByte()
        out.write(payload)
        out.flush()
    }

    fun readNextMessage(): String? {
        val inp = input ?: return null
        val messageBuffer = ByteArrayOutputStream()
        while (true) {
            val b0 = inp.read(); if (b0 == -1) return null
            val isFin = (b0 and 0x80) != 0
            val opcode = b0 and 0x0F
            val b1 = inp.read(); if (b1 == -1) return null
            var len = (b1 and 0x7F).toLong()
            if (len == 126L) {
                len = ((inp.read() and 0xFF) shl 8 or (inp.read() and 0xFF)).toLong()
            } else if (len == 127L) {
                len = 0L
                for (i in 0..7) len = (len shl 8) or (inp.read().toLong() and 0xFF)
            }
            val mask = if ((b1 and 0x80) != 0) ByteArray(4).also { readExact(inp, it) } else null
            val payload = ByteArray(len.toInt()).also { readExact(inp, it) }
            if (mask != null) {
                for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i and 3].toInt()).toByte()
            }
            when (opcode) {
                0x8 -> { isConnected = false; return null }
                0x9 -> { runCatching { sendPong(payload) }; continue }
                0xA -> continue
                0x0, 0x1, 0x2 -> {
                    messageBuffer.write(payload)
                    if (isFin) return String(messageBuffer.toByteArray(), StandardCharsets.UTF_8)
                }
            }
        }
    }

    @Synchronized
    private fun sendPong(data: ByteArray) {
        val out = output ?: return
        out.write(0x8A)
        val len = data.size.coerceAtMost(125)
        out.write(0x80 or len)
        val mask = ByteArray(4).also { random.nextBytes(it) }
        out.write(mask)
        for (i in 0 until len) out.write(data[i].toInt() xor mask[i and 3].toInt())
        out.flush()
    }

    private fun readExact(inp: BufferedInputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val r = inp.read(buf, off, buf.size - off)
            if (r == -1) break
            off += r
        }
    }

    fun close() {
        isConnected = false
        runCatching { socket?.close() }
    }
}
