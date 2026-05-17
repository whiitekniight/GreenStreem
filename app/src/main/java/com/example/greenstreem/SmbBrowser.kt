package com.example.greenstreem

import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.io.OutputStream
import java.util.Properties

object SmbBrowser {
    data class Entry(val name: String, val isDirectory: Boolean)

    fun listFolders(server: String, username: String, password: String, share: String?, folder: String): List<Entry> {
        val file = SmbFile(directoryUrl(server, share, folder), context(username, password))
        return file.listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .map { Entry(it.name.trimEnd('/'), true) }
            .filter { it.name.isNotBlank() && it.name != "." && it.name != ".." && !it.name.endsWith("$") }
            .sortedBy { it.name.lowercase() }
    }

    fun createFolder(server: String, username: String, password: String, share: String, folder: String, name: String) {
        val cleanName = DownloadPrefs.cleanPathSegment(name).ifBlank { return }
        val target = SmbFile(directoryUrl(server, share, join(folder, cleanName)), context(username, password))
        if (!target.exists()) target.mkdirs()
    }

    fun openOutputStream(
        server: String,
        username: String,
        password: String,
        share: String,
        folder: String,
        fileName: String
    ): OutputStream {
        val target = SmbFile(fileUrl(server, share, folder, fileName), context(username, password))
        target.parent?.let { parentUrl ->
            val parent = SmbFile(parentUrl, context(username, password))
            if (!parent.exists()) parent.mkdirs()
        }
        return target.outputStream
    }

    fun targetUrl(server: String, share: String, folder: String): String {
        return directoryUrl(server, share, folder).trimEnd('/')
    }

    private fun context(username: String, password: String): CIFSContext {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.enableSMB2", "true")
            setProperty("jcifs.smb.client.disableSMB1", "false")
            setProperty("jcifs.resolveOrder", "DNS")
        }
        val base = BaseContext(PropertyConfiguration(props))
        val domainAndUser = username.trim()
        val domain = domainAndUser.substringBefore('\\', "").takeIf { '\\' in domainAndUser }
        val user = if ('\\' in domainAndUser) domainAndUser.substringAfter('\\') else domainAndUser
        return base.withCredentials(NtlmPasswordAuthenticator(domain, user, password))
    }

    private fun directoryUrl(server: String, share: String?, folder: String): String {
        val cleanServer = server.trim().removePrefix("smb://").trim('/')
        val cleanShare = share?.trim()?.trim('/').orEmpty()
        val cleanFolder = folder.trim().trim('/')
        return buildString {
            append("smb://")
            append(cleanServer)
            append("/")
            if (cleanShare.isNotBlank()) {
                append(cleanShare)
                append("/")
            }
            if (cleanFolder.isNotBlank()) {
                append(cleanFolder)
                append("/")
            }
        }
    }

    private fun fileUrl(server: String, share: String, folder: String, fileName: String): String {
        return directoryUrl(server, share, folder) + fileName
    }

    private fun join(folder: String, child: String): String {
        return listOf(folder.trim('/'), child.trim('/'))
            .filter { it.isNotBlank() }
            .joinToString("/")
    }
}
