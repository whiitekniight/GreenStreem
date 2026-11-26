package com.example.greenstreem

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.net.InetAddress
import java.net.NetworkInterface

internal object CastSupport {
    fun safeButtonPlacement(view: android.view.View) {
        val density=view.resources.displayMetrics.density
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view) { button,insets ->
            val safe=insets.getInsetsIgnoringVisibility(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            val end=(24*density).toInt()+safe.right
            val top=(16*density).toInt()+safe.top
            val params=button.layoutParams as? android.view.ViewGroup.MarginLayoutParams
            if(params!=null && (params.marginEnd!=end || params.topMargin!=top)) {
                params.marginEnd=end; params.topMargin=top; button.layoutParams=params
            }
            insets
        }
        view.post { androidx.core.view.ViewCompat.requestApplyInsets(view) }
    }
    fun limitConnections(server: fi.iki.elonen.NanoHTTPD) {
        server.setAsyncRunner(object: fi.iki.elonen.NanoHTTPD.AsyncRunner {
            private val clients=java.util.Collections.synchronizedSet(mutableSetOf<fi.iki.elonen.NanoHTTPD.ClientHandler>())
            private val pool=java.util.concurrent.ThreadPoolExecutor(0,8,30,java.util.concurrent.TimeUnit.SECONDS,
                java.util.concurrent.SynchronousQueue(),java.util.concurrent.ThreadFactory { task -> Thread(task,"TV casting").apply { isDaemon=true } })
            override fun exec(handler: fi.iki.elonen.NanoHTTPD.ClientHandler) { clients.add(handler)
                try { pool.execute(handler) } catch(e: java.util.concurrent.RejectedExecutionException) { clients.remove(handler); handler.close() }
            }
            override fun closed(handler: fi.iki.elonen.NanoHTTPD.ClientHandler) { clients.remove(handler) }
            override fun closeAll() { synchronized(clients) { clients.toList() }.forEach { it.close() }; clients.clear(); pool.shutdownNow() }
        })
    }
    fun isLan(address: InetAddress)=address.isSiteLocalAddress || address.isLinkLocalAddress || address.isLoopbackAddress ||
        (address.address.size==16 && (address.address[0].toInt() and 0xfe)==0xfc)
    fun localAddress(): String=NetworkInterface.getNetworkInterfaces().toList().sortedBy { if(it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }.flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is java.net.Inet4Address && it.isSiteLocalAddress && !it.isLoopbackAddress }?.hostAddress
        ?: error("Connect this device to your home Wi-Fi first")
    fun mime(url: String): String=when(url.substringBefore('?').substringBefore('#').substringAfterLast('.').lowercase()) {
        "m3u8" -> "application/x-mpegURL"
        "mpd" -> "application/dash+xml"
        "ts" -> "video/mp2t"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        else -> "video/mp4"
    }
    fun xml(value: String)=value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
    fun position(player: androidx.media3.common.Player?): Long =
        if(player==null || player.playbackState==androidx.media3.common.Player.STATE_ENDED) 0 else player.currentPosition.coerceAtLeast(0)
    data class Media(val url: String,val title: String,val live: Boolean=false,val position: Long=0,
                     val headers: Map<String,String> = emptyMap(),val relay: Relay?=null,val googleUrl: String?=null,val proxied: Boolean=false)
    data class Relay(val name: String,val size: Long,val item: SharedRecordingLibrary.Item?=null,
                     val remoteUrl: String="",val headers: Map<String,String> = emptyMap()) {
        fun open(c: Context,offset: Long): InputStream {
            if(item!=null) return SharedRecordingLibrary.open(c,item,offset)
            val connection=(java.net.URL(remoteUrl).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout=10_000; readTimeout=20_000
                headers.forEach { (key,value)->setRequestProperty(key,value) }
                if(offset>0) setRequestProperty("Range","bytes=$offset-")
            }
            try {
                check(connection.responseCode in 200..299 && (offset==0L || connection.responseCode==206)) { "Source could not seek" }
                return object: java.io.FilterInputStream(connection.inputStream) {
                    override fun close() { try { super.close() } finally { connection.disconnect() } }
                }
            } catch(e: Exception) { connection.disconnect(); throw e }
        }
    }
    fun recording(c: Context,job: RecordingSchedule.Job): Media {
        val uri=job.outputUri
        val size=if(RecordingLocalFolders.direct(uri)) RecordingLocalFolders.file(c,uri).length() else if(uri.isNotBlank()) c.contentResolver.query(Uri.parse(uri),arrayOf(android.provider.OpenableColumns.SIZE),null,null,null)?.use {
            if(it.moveToFirst()) it.getLong(0) else job.bytes
        } ?: job.bytes else job.bytes
        val item=SharedRecordingLibrary.Item(job.id,job.title,size,job.start,uri,if(uri.isBlank()) job else null)
        return Media("",job.title,relay=Relay(job.file,size,item))
    }
}
