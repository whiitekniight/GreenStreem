package com.example.greenstreem

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.GZIPInputStream

object SecondaryEpgProvider {

    private const val PREFS = "iptv_prefs"
    private const val KEY_SECONDARY_EPG_URL = "secondary_epg_url"
    private const val KEY_SECONDARY_EPG_ENABLED = "secondary_epg_enabled"
    private const val CACHE_MAX_AGE_MS = 6 * 60 * 60 * 1000L

    private val mutex = Mutex()
    private var lastLoadedUrl: String? = null
    private var lastLoadedAtMs: Long = 0L
    private var listingsByChannel: Map<String, List<XtreamEpgListing>> = emptyMap()

    suspend fun getListingsForChannel(context: Context, epgId: String): List<XtreamEpgListing> {
        val normalized = normalizeChannelId(epgId)
        if (normalized.isBlank()) return emptyList()

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_SECONDARY_EPG_ENABLED, false)) return emptyList()
        val url = prefs.getString(KEY_SECONDARY_EPG_URL, null)?.trim().orEmpty()
        if (url.isBlank()) return emptyList()

        ensureLoaded(url)
        return listingsByChannel[normalized].orEmpty()
    }

    private suspend fun ensureLoaded(url: String) {
        val now = System.currentTimeMillis()
        if (url == lastLoadedUrl && now - lastLoadedAtMs < CACHE_MAX_AGE_MS && listingsByChannel.isNotEmpty()) return

        mutex.withLock {
            val nowLocked = System.currentTimeMillis()
            if (url == lastLoadedUrl && nowLocked - lastLoadedAtMs < CACHE_MAX_AGE_MS && listingsByChannel.isNotEmpty()) return
            val parsed = loadXmltv(url)
            listingsByChannel = parsed
            lastLoadedUrl = url
            lastLoadedAtMs = nowLocked
        }
    }

    private suspend fun loadXmltv(url: String): Map<String, List<XtreamEpgListing>> = withContext(Dispatchers.IO) {
        val map = mutableMapOf<String, MutableList<XtreamEpgListing>>()
        runCatching {
            val stream = openStream(url)
            stream.use { input ->
                val parser = Xml.newPullParser()
                parser.setInput(input, null)
                parseProgrammes(parser, map)
            }
        }
        map.mapValues { (_, value) -> value.sortedBy { it.startTimestamp } }
    }

    private fun openStream(url: String): InputStream {
        val conn = URL(url).openConnection().apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "GreenStreem/1.0")
        }
        val raw = conn.getInputStream()
        return if (url.lowercase(Locale.getDefault()).endsWith(".gz")) GZIPInputStream(raw) else raw
    }

    private fun parseProgrammes(parser: XmlPullParser, out: MutableMap<String, MutableList<XtreamEpgListing>>) {
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                val channelRaw = parser.getAttributeValue(null, "channel").orEmpty()
                val channel = normalizeChannelId(channelRaw)
                val start = parseXmltvTime(parser.getAttributeValue(null, "start"))
                val stop = parseXmltvTime(parser.getAttributeValue(null, "stop"))
                var title = ""
                var desc = ""

                while (!(parser.eventType == XmlPullParser.END_TAG && parser.name == "programme")) {
                    if (parser.eventType == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "title" -> title = parser.nextText().orEmpty()
                            "desc" -> desc = parser.nextText().orEmpty()
                        }
                    }
                    parser.next()
                }

                if (channel.isNotBlank() && start > 0L && stop > start) {
                    val listing = XtreamEpgListing(
                        id = "${channel}_${start}",
                        epgId = channelRaw,
                        title = title,
                        description = desc,
                        start = start.toString(),
                        end = stop.toString(),
                        startTimestamp = start,
                        stopTimestamp = stop
                    )
                    out.getOrPut(channel) { mutableListOf() }.add(listing)
                }
            }
            event = parser.next()
        }
    }

    private fun parseXmltvTime(raw: String?): Long {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return 0L
        val normalized = if (value.length > 14) {
            val datePart = value.substring(0, 14)
            val tzPart = value.substring(14).trim().replace(" ", "")
            "$datePart $tzPart"
        } else {
            value
        }
        val formats = listOf(
            SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US),
            SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
        )
        for (fmt in formats) {
            runCatching {
                return (fmt.parse(normalized)?.time ?: 0L) / 1000L
            }
        }
        return 0L
    }

    private fun normalizeChannelId(value: String): String {
        return value.trim().lowercase(Locale.getDefault())
    }
}
