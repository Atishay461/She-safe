package com.aaforge.shesafe.network

import android.util.Base64
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class RawWebSocketConnection(private val wsUrl: String) {
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

        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val ssl = (factory.createSocket(host, port) as SSLSocket).apply {
            soTimeout = 0
            keepAlive = true
            startHandshake()
        }
        val inp = BufferedInputStream(ssl.inputStream, 16384)
        val out = BufferedOutputStream(ssl.outputStream, 16384)

        val keyBytes = ByteArray(16).also { random.nextBytes(it) }
        val secKey = Base64.encodeToString(keyBytes, Base64.NO_WRAP)
        val handshake = "GET $path HTTP/1.1\r\n" +
                "Host: $host\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: $secKey\r\n" +
                "Sec-WebSocket-Version: 13\r\n\r\n"

        out.write(handshake.toByteArray(StandardCharsets.UTF_8))
        out.flush()

        val headerBuf = StringBuilder()
        var b: Int
        while (inp.read().also { b = it } != -1) {
            headerBuf.append(b.toChar())
            if (headerBuf.endsWith("\r\n\r\n")) break
        }
        if (!headerBuf.toString().contains("101")) {
            ssl.close()
            throw IllegalStateException("WebSocket handshake rejected")
        }
        socket = ssl; input = inp; output = out; isConnected = true
    }

    @Synchronized
    fun sendBinary(payload: ByteArray) = sendFrame(0x82, payload)

    @Synchronized
    private fun sendFrame(opcode: Int, payload: ByteArray) {
        val out = output ?: return
        val len = payload.size
        out.write(opcode)
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
        val masked = ByteArray(len) { i -> (payload[i].toInt() xor mask[i % 4].toInt()).toByte() }
        out.write(masked)
        out.flush()
    }

    fun readNextTextFrame(): String? {
        val inp = input ?: return null
        val b0 = inp.read(); if (b0 == -1) return null
        val opcode = b0 and 0x0F
        if (opcode == 0x8) return null
        val b1 = inp.read(); if (b1 == -1) return null
        var len = (b1 and 0x7F).toLong()
        if (len == 126L) {
            len = ((inp.read() and 0xFF) shl 8 or (inp.read() and 0xFF)).toLong()
        } else if (len == 127L) {
            len = 0L
            for (i in 0..7) len = (len shl 8) or (inp.read().toLong() and 0xFF)
        }
        val mask = if ((b1 and 0x80) != 0) ByteArray(4).also { inp.readFully(it) } else null
        val data = ByteArray(len.toInt()).also { inp.readFully(it) }
        if (mask != null) for (i in data.indices) data[i] = (data[i].toInt() xor mask[i % 4].toInt()).toByte()
        return if (opcode == 0x1 || opcode == 0x2) String(data, StandardCharsets.UTF_8) else ""
    }

    private fun BufferedInputStream.readFully(buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val r = read(buf, off, buf.size - off)
            if (r == -1) break
            off += r
        }
    }

    fun close() {
        isConnected = false
        runCatching { socket?.close() }
    }
}
