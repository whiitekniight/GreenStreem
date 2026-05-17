package com.example.greenstreem

import android.util.Base64

object DataUtils {
    /**
     * Decodes a Base64 string. If it's not Base64 or fails, returns the original text.
     */
    fun decodeBase64(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        val trimmed = text.trim()
        if (!looksLikeBase64(trimmed)) return text
        return try {
            val data = Base64.decode(trimmed, Base64.DEFAULT)
            val decoded = String(data, Charsets.UTF_8)
            if (isReadableText(decoded)) decoded else text
        } catch (e: Exception) {
            text // Return original if it wasn't actually Base64
        }
    }

    private fun looksLikeBase64(value: String): Boolean {
        if (value.length < 8) return false
        if (value.any { it.isWhitespace() }) return false
        if (!Regex("""^[A-Za-z0-9+/=_-]+$""").matches(value)) return false
        return value.length % 4 != 1
    }

    private fun isReadableText(value: String): Boolean {
        if (value.isBlank()) return false
        val printable = value.count { ch ->
            ch == '\n' || ch == '\r' || ch == '\t' || !ch.isISOControl()
        }
        return printable >= (value.length * 0.9f)
    }
}
