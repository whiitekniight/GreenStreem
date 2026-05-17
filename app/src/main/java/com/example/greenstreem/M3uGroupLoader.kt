package com.example.greenstreem

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

object M3uGroupLoader {
    suspend fun loadLiveGroups(context: Context): List<XtreamCategory> {
        val prefs = context.getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
        val url = prefs.getString("m3u_url", "").orEmpty()
        if (url.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                URL(url).openStream().use { stream -> M3uParser.parse(stream) }
                    .filter { classify(it) == Kind.LIVE }
                    .map { it.group.ifBlank { "Default" } }
                    .distinct()
                    .mapIndexed { index, group -> XtreamCategory(group, group, index) }
            }.getOrDefault(emptyList())
        }
    }

    private enum class Kind { LIVE, MOVIES, SERIES }

    private fun classify(channel: Channel): Kind {
        val url = channel.streamUrl.lowercase()
        val group = channel.group.lowercase()
        val name = channel.name.lowercase()
        val haystack = "$group $name $url"
        return when {
            "/series/" in url ||
                group.startsWith("series") ||
                group.contains(" series") ||
                Regex("""\bs\d{1,2}e\d{1,2}\b""").containsMatchIn(haystack) -> Kind.SERIES
            "/movie/" in url ||
                group.startsWith("movie") ||
                group.startsWith("vod") ||
                group.contains("movie") ||
                group.contains("vod") ||
                group.contains("film") -> Kind.MOVIES
            else -> Kind.LIVE
        }
    }
}
