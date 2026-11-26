package com.example.greenstreem

import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File

/** App-owned recording folders for TVs which have no Storage Access Framework picker. */
internal object RecordingLocalFolders {
    data class Target(val label: String,val folder: File,val removable: Boolean)
    fun available(c: Context): List<Target> {
        val external=runCatching { c.getExternalFilesDirs(null).toList() }.getOrDefault(emptyList()).filterNotNull().filter {
            Environment.getExternalStorageState(it)==Environment.MEDIA_MOUNTED && it.canWrite()
        }.map { root ->
            val removable=Environment.isExternalStorageRemovable(root)
            val description=if(android.os.Build.VERSION.SDK_INT>=24) runCatching {
                c.getSystemService(android.os.storage.StorageManager::class.java).getStorageVolume(root)?.getDescription(c)
            }.getOrNull() else null
            Target(if(removable) "USB storage${description?.let { " · $it" }.orEmpty()}" else "Device storage",File(root,"Recordings"),removable)
        }
        return (external+if(external.none { !it.removable }) listOf(Target("Device storage",File(c.filesDir,"Recordings"),false)) else emptyList())
            .distinctBy { it.folder.absolutePath }
    }
    fun direct(uri: String)=Uri.parse(uri).scheme=="file"
    fun directory(c: Context,uri: String): File {
        require(direct(uri)) { "Not a local recording folder" }
        val file=File(requireNotNull(Uri.parse(uri).path)).canonicalFile
        require(available(c).any { it.folder.canonicalFile==file }) { "Reconnect your recording drive and choose its storage again" }
        return file
    }
    fun file(c: Context,uri: String): File {
        require(direct(uri)) { "Not a local recording file" }
        val file=File(requireNotNull(Uri.parse(uri).path)).canonicalFile
        require(available(c).any { it.folder.canonicalFile==file.parentFile }) { "Recording storage is unavailable" }
        return file
    }
    fun prepare(c: Context,target: Target): String {
        val uri=Uri.fromFile(target.folder).toString()
        val folder=directory(c,uri)
        check(folder.isDirectory || folder.mkdirs()) { "Cannot open this storage" }
        val probe=File.createTempFile(".greenstreem-write-",".tmp",folder)
        try { java.io.FileOutputStream(probe).use { it.write(0);it.fd.sync() } }
        finally { check(probe.delete()) { "Could not verify write access" } }
        return uri
    }
    fun label(c: Context,uri: String)=runCatching {
        val folder=directory(c,uri)
        available(c).first { it.folder.canonicalFile==folder }.label
    }.getOrDefault("Local / USB folder (reconnect drive)")
}

internal object RecordingLocalPaths {
    fun child(folder: File,name: String): File {
        require(name.isNotBlank() && name!="." && name!=".." && '/' !in name && '\\' !in name) { "Invalid recording filename" }
        return File(folder,name).canonicalFile.also { require(it.parentFile==folder.canonicalFile) { "Recording must stay in its selected folder" } }
    }
}
