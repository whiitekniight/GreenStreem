package com.example.greenstreem

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.InputStream
import java.security.MessageDigest
import org.json.JSONObject

/** Enumerates the selected folder, not another device's private job database. No folders are created. */
internal object SharedRecordingLibrary {
    data class Item(val id: String, val title: String, val size: Long, val modified: Long,
                    val uri: String="", val job: RecordingSchedule.Job?=null) {
        fun json()=JSONObject().put("id",id).put("title",title).put("size",size).put("modified",modified)
    }
    private val extensions=setOf("ts","mp4","mkv","m4v","webm","avi","mov","mpg","mpeg")
    fun supported(name: String)=name.substringAfterLast('.',"").lowercase() in extensions
    private fun id(path: String)=MessageDigest.getInstance("SHA-256").digest(path.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun list(c: Context): List<Item> {
        val p=c.getSharedPreferences(DownloadPrefs.PREFS_NAME,0)
        val tree=p.getString("recording_tree_uri","").orEmpty()
        val jobs=RecordingSchedule.jobs(c)
        val active=jobs.filter { it.state in listOf("Scheduled","Recording") || RecordingService.isRecording(it.id) }
        val items=if(RecordingLocalFolders.direct(tree)) {
            val folder=RecordingLocalFolders.directory(c,tree)
            check(folder.isDirectory) { "Recording storage is unavailable" }
            folder.listFiles().orEmpty().filter { it.isFile && supported(it.name) && it.length()>0 }.mapNotNull { file ->
                val uri=Uri.fromFile(file).toString()
                if(active.any { it.outputUri==uri }) null
                else Item(id(uri),jobs.find { it.outputUri==uri }?.title ?: file.name,file.length(),file.lastModified(),uri)
            }
        } else if(tree.isNotBlank()) {
            val base=Uri.parse(tree)
            val children=DocumentsContract.buildChildDocumentsUriUsingTree(base,DocumentsContract.getTreeDocumentId(base))
            val columns=arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_LAST_MODIFIED,DocumentsContract.Document.COLUMN_MIME_TYPE)
            val result=mutableListOf<Item>()
            val cursor=c.contentResolver.query(children,columns,null,null,null) ?: error("Cannot open the chosen folder")
            cursor.use { while(it.moveToNext()) {
                val name=it.getString(1).orEmpty()
                if(it.getString(4)==DocumentsContract.Document.MIME_TYPE_DIR || !supported(name) || it.getLong(2)<=0) continue
                val uri=DocumentsContract.buildDocumentUriUsingTree(base,it.getString(0)).toString()
                if(active.any { job -> job.outputUri==uri }) continue
                result.add(Item(id(uri),jobs.find { job -> job.outputUri==uri }?.title ?: name,it.getLong(2),it.getLong(3),uri))
            } }; result
        } else {
            val server=p.getString(DownloadPrefs.KEY_SMB_SERVER,"").orEmpty()
            val share=p.getString(DownloadPrefs.KEY_SMB_SHARE,"").orEmpty()
            require(server.isNotBlank() && share.isNotBlank()) { "Choose a local / USB or network recording folder first" }
            val folder=p.getString(DownloadPrefs.KEY_SMB_FOLDER,"").orEmpty()
            SmbBrowser.listRecordings(c,server,share,folder).filter { file ->
                active.none { it.server==server && it.share==share && it.folder==folder && it.file==file.name }
            }.map { file ->
                val key=id("$server/$share/$folder/${file.name}")
                val job=RecordingSchedule.Job(key,file.name,"","",file.modified,0,server,share,folder,file.name,"Completed",file.size)
                Item(key,jobs.find { it.server==server && it.share==share && it.folder==folder && it.file==file.name }?.title ?: file.name,file.size,file.modified,job=job)
            }
        }
        return items.sortedByDescending { it.modified }
    }
    fun open(c: Context, item: Item, offset: Long): InputStream {
        if(item.uri.isNotBlank()) {
            if(RecordingLocalFolders.direct(item.uri)) {
                val input=java.io.FileInputStream(RecordingLocalFolders.file(c,item.uri))
                try { input.channel.position(offset);return input } catch(e: Exception) { input.close();throw e }
            }
            val descriptor=c.contentResolver.openFileDescriptor(Uri.parse(item.uri),"r") ?: error("Storage is unavailable")
            val input=android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            try { input.channel.position(offset); return input } catch(e: Exception) { input.close(); throw e }
        }
        val file=SmbBrowser.openRandomAccess(c,requireNotNull(item.job))
        try { file.seek(offset) } catch(e: Exception) { file.close(); throw e }
        return object: InputStream() {
            override fun read()=file.read()
            override fun read(b: ByteArray, off: Int, len: Int)=file.read(b,off,len)
            override fun close()=file.close()
        }
    }
}
