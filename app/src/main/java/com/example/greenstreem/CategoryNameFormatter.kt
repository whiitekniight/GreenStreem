package com.example.greenstreem

import android.content.Context

object ChannelNameFormatter {
    const val KEY_PREFIX_MODE = "appearance_channel_prefix_mode"
    const val KEY_PREFIX_VALUES = "appearance_channel_prefix_values"
    const val MODE_OFF = 0
    const val MODE_REMOVE = 1
    const val MODE_ADD = 2

    fun format(context: Context, rawName: String): String {
        val prefs = context.getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
        val prefixes = parsePrefixes(prefs.getString(KEY_PREFIX_VALUES, "").orEmpty())
        if (prefixes.isEmpty() || rawName.isBlank()) return rawName

        return when (prefs.getInt(KEY_PREFIX_MODE, MODE_OFF)) {
            MODE_REMOVE -> removePrefix(rawName, prefixes)
            MODE_ADD -> addPrefix(rawName, prefixes)
            else -> rawName
        }
    }

    fun parsePrefixes(raw: String): List<String> {
        return raw.split(',', '\n')
            .map { it.trim().trim('-', '|', ':', '[', ']', ' ') }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }

    private fun removePrefix(name: String, prefixes: List<String>): String {
        val trimmed = name.trim()
        prefixes.forEach { prefix ->
            val escaped = Regex.escape(prefix)
            val patterns = listOf(
                Regex("""^\[$escaped\]\s*""", RegexOption.IGNORE_CASE),
                Regex("""^$escaped\s*[-|:]\s*""", RegexOption.IGNORE_CASE),
                Regex("""^$escaped\s+""", RegexOption.IGNORE_CASE)
            )
            patterns.forEach { pattern ->
                val cleaned = trimmed.replaceFirst(pattern, "").trim()
                if (cleaned != trimmed && cleaned.isNotBlank()) return cleaned
            }
        }
        return name
    }

    private fun addPrefix(name: String, prefixes: List<String>): String {
        val trimmed = name.trim()
        if (trimmed.isBlank() || prefixes.any { removePrefix(trimmed, listOf(it)) != trimmed }) return name
        return "${prefixes.first()} | $trimmed"
    }

}
