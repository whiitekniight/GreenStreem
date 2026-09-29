package com.example.greenstreem

import java.net.URI

/** Only migrate the retired branded provider, preserving paths and user data. */
internal object BrandedServerAddress {
    fun migrate(value: String, branded: Boolean, replacement: String): String {
        if (!branded || replacement.isBlank()) return value
        return runCatching {
            val uri = URI(value.trim())
            if (!uri.host.equals("kennye71.trustissues.life", ignoreCase = true) ||
                uri.scheme !in listOf("http", "https") || uri.rawUserInfo != null) return value
            replacement.trimEnd('/') + (uri.rawPath ?: "") +
                (uri.rawQuery?.let { "?$it" } ?: "") +
                (uri.rawFragment?.let { "#$it" } ?: "")
        }.getOrDefault(value)
    }
}
