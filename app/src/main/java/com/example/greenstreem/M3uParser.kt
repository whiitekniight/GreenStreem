package com.example.greenstreem

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * A simple parser for M3U playlists.
 */
object M3uParser {

    fun parse(inputStream: InputStream): List<Channel> {
        val channels = mutableListOf<Channel>()
        val reader = BufferedReader(InputStreamReader(inputStream))
        var line: String?
        var currentChannelName = ""
        var currentGroup = "Default"
        var currentLogo = ""
        var currentEpgId = ""
        var currentNumber = 0

        try {
            line = reader.readLine()
            if (line == null || !line.startsWith("#EXTM3U")) {
                return emptyList()
            }

            while (reader.readLine().also { line = it } != null) {
                if (line!!.startsWith("#EXTINF:")) {
                    // Parse metadata
                    // Example: #EXTINF:-1 tvg-id="CNN.us" tvg-name="CNN" tvg-logo="http://logo.com" group-title="News",CNN
                    val info = line!!.substring(8)
                    
                    currentEpgId = parseAttribute(info, "tvg-id")
                    currentLogo = parseAttribute(info, "tvg-logo")
                    currentGroup = parseAttribute(info, "group-title").ifEmpty { "Default" }
                    
                    val lastComma = info.lastIndexOf(',')
                    currentChannelName = if (lastComma != -1) {
                        info.substring(lastComma + 1).trim()
                    } else {
                        "Unknown"
                    }
                } else if (line!!.startsWith("http") || line!!.startsWith("rtmp") || line!!.startsWith("rtsp")) {
                    // Stream URL
                    val streamUrl = line!!.trim()
                    channels.add(
                        Channel(
                            id = channels.size.toLong(),
                            name = currentChannelName,
                            group = currentGroup,
                            logoUrl = currentLogo,
                            streamUrl = streamUrl,
                            epgId = currentEpgId,
                            number = ++currentNumber
                        )
                    )
                    // Reset for next channel
                    currentChannelName = ""
                    currentGroup = "Default"
                    currentLogo = ""
                    currentEpgId = ""
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            reader.close()
        }
        return channels
    }

    private fun parseAttribute(line: String, attribute: String): String {
        val key = "$attribute=\""
        val start = line.indexOf(key)
        if (start == -1) return ""
        val valueStart = start + key.length
        val end = line.indexOf("\"", valueStart)
        if (end == -1) return ""
        return line.substring(valueStart, end)
    }
}
