package com.example.greenstreem

import android.util.Base64

object DataUtils {
    /**
     * Decodes a Base64 string. If it's not Base64 or fails, returns the original text.
     */
    fun decodeBase64(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        return try {
            val data = Base64.decode(text, Base64.DEFAULT)
            String(data, Charsets.UTF_8)
        } catch (e: Exception) {
            text // Return original if it wasn't actually Base64
        }
    }
}
