package com.aaforge.shesafe.core

object BackendEndpoint {
    @Volatile
    var CLOUDFLARE_BASE_URL: String = "https://shesafe-gateway.workers.dev"

    val STREAM_ENDPOINT: String
        get() = "${CLOUDFLARE_BASE_URL.trimEnd('/')}/stream"

    val VOICE_WS_ENDPOINT: String
        get() = CLOUDFLARE_BASE_URL
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
            .trimEnd('/') + "/voice"

    const val GOOGLE_FAVICON_ENDPOINT: String =
        "https://www.google.com/s2/favicons?sz=64&domain="

    const val DDG_HTML_ENDPOINT: String =
        "https://html.duckduckgo.com/html/"
}
