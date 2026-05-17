package com.example.greenstreem

import android.net.Uri

data class XtreamM3uCredentials(
    val serverUrl: String,
    val username: String,
    val password: String,
    val output: String?
)

object XtreamM3uUrl {
    fun parse(url: String): XtreamM3uCredentials? {
        val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.takeIf { it.equals("http", true) || it.equals("https", true) } ?: return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val path = uri.path.orEmpty()
        if (!path.endsWith("/get.php", ignoreCase = true) && !path.equals("/get.php", ignoreCase = true)) {
            return null
        }
        val username = uri.getQueryParameter("username")?.takeIf { it.isNotBlank() } ?: return null
        val password = uri.getQueryParameter("password")?.takeIf { it.isNotBlank() } ?: return null
        val port = if (uri.port > 0) ":${uri.port}" else ""
        return XtreamM3uCredentials(
            serverUrl = "$scheme://$host$port",
            username = username,
            password = password,
            output = uri.getQueryParameter("output")
        )
    }
}
