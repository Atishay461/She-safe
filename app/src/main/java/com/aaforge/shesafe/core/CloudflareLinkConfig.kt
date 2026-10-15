package com.aaforge.shesafe.core

/**
 * SINGLE SOURCE OF TRUTH FOR CLOUDFLARE BACKEND CONNECTION.
 * Pura app sirf isi file ke CLOUDFLARE_BACKEND_URL se Cloudflare ko call karega.
 * Apna live Cloudflare link neeche double quotes ke andar replace karein.
 */
object CloudflareLinkConfig {
    const val CLOUDFLARE_BACKEND_URL: String =
        "https://shesafe-backend.your-cloudflare-domain.com"
}
