package com.example.greenstreem

import java.net.URI

/** A casting-only HLS choice; the phone's working transport-stream playback stays untouched. */
internal object CastMediaUrls {
    fun googleLive(url: String,alternates: List<String>): String {
        val actual=runCatching { URI(url) }.getOrNull() ?: return url
        if(actual.rawPath.orEmpty().endsWith(".m3u8",true)) return url
        val hls=alternates.filter { runCatching { URI(it).rawPath.orEmpty().endsWith(".m3u8",true) }.getOrDefault(false) }
        hls.firstOrNull { val candidate=URI(it); candidate.scheme.equals(actual.scheme,true) && candidate.rawAuthority.equals(actual.rawAuthority,true) }?.let { return it }
        val path=actual.rawPath.orEmpty()
        if(actual.scheme in listOf("http","https") && path.contains("/live/",true) && path.endsWith(".ts",true)) {
            val start=url.indexOf(path,url.indexOf("://")+3)
            if(start>=0) return url.substring(0,start)+path.dropLast(3)+".m3u8"+url.substring(start+path.length)
        }
        return hls.firstOrNull() ?: url
    }
}
