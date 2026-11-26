package com.example.greenstreem

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import fi.iki.elonen.NanoHTTPD
import java.util.UUID

/** A temporary capability URL for one user-selected recording, never a public folder listing. */
class CastMediaRelayService: Service() {
    companion object {
        @Volatile internal var source: CastSupport.Relay?=null
        @Volatile private var liveSource: CastSupport.Media?=null
        @Volatile private var live: CastLiveStream?=null
        @Volatile private var liveKey=""
        @Volatile private var port=0
        @Volatile private var key=""
        internal fun begin(c: Context,relay: CastSupport.Relay) {
            require(relay.size>0) { "This recording is empty" }
            liveSource=null; source=relay; key=UUID.randomUUID().toString()+UUID.randomUUID().toString()
            val intent=Intent(c,CastMediaRelayService::class.java)
            if(Build.VERSION.SDK_INT>=26) c.startForegroundService(intent) else c.startService(intent)
        }
        internal fun beginLive(c: Context,media: CastSupport.Media) {
            source=null;liveSource=media;key=UUID.randomUUID().toString()+UUID.randomUUID().toString()
            val intent=Intent(c,CastMediaRelayService::class.java)
            if(Build.VERSION.SDK_INT>=26)c.startForegroundService(intent) else c.startService(intent)
        }
        internal fun liveUrl(hls: Boolean): String? {
            if(port==0 || liveKey!=key)return null
            live?.failure?.let { error(it) }
            if(live?.ready()!=true)return null
            return "http://${CastSupport.localAddress()}:$port/$key/"+if(hls) "index.m3u8" else "stream.ts"
        }
        internal fun url(): String {
            check(port>0 && key.isNotBlank()) { "Preparing recording for TV…" }
            return "http://${CastSupport.localAddress()}:$port/$key/video.${source?.name?.substringAfterLast('.',"ts") ?: "ts"}"
        }
        fun stop(c: Context) { c.stopService(Intent(c,CastMediaRelayService::class.java)) }
    }
    private var server: NanoHTTPD?=null
    private var wake: PowerManager.WakeLock?=null
    override fun onBind(intent: Intent?)=null
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(intent?.action=="stop" || (source==null && liveSource==null)) { stopSelf(); return START_NOT_STICKY }
        val manager=getSystemService(NotificationManager::class.java)
        if(Build.VERSION.SDK_INT>=26) manager.createNotificationChannel(NotificationChannel("cast_relay","Casting video",NotificationManager.IMPORTANCE_LOW))
        val stop=PendingIntent.getService(this,9,Intent(this,CastMediaRelayService::class.java).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n=if(Build.VERSION.SDK_INT>=26) Notification.Builder(this,"cast_relay") else Notification.Builder(this)
        startForeground(805,n.setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("Casting ${liveSource?.title ?: source?.name}")
            .setContentText("Keep this device connected to Wi-Fi")
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,"Stop casting",stop).setOngoing(true).build())
        live?.close();live=null
        liveSource?.let { live=CastLiveStream(it,java.io.File(cacheDir,"cast-live-${UUID.randomUUID()}"));liveKey=key }
        if(server!=null) return START_NOT_STICKY
        try {
            server=object: NanoHTTPD(0) {
                override fun serve(s: IHTTPSession): Response {
                    fun reply(status: Response.Status,text: String)=newFixedLengthResponse(status,"text/plain",text)
                    if(!runCatching { CastSupport.isLan(java.net.InetAddress.getByName(s.remoteIpAddress)) }.getOrDefault(false)) return reply(Response.Status.FORBIDDEN,"")
                    if(liveSource!=null && key.isNotBlank() && s.uri.startsWith("/$key/")) {
                        val engine=live ?: return reply(Response.Status.SERVICE_UNAVAILABLE,"")
                        if(s.method==Method.OPTIONS)return reply(Response.Status.OK,"").apply {
                            addHeader("Access-Control-Allow-Origin","*");addHeader("Access-Control-Allow-Methods","GET, HEAD, OPTIONS")
                            addHeader("Access-Control-Allow-Headers","Range");addHeader("Access-Control-Allow-Private-Network","true")
                        }
                        if(s.method !in listOf(Method.GET,Method.HEAD))return reply(Response.Status.METHOD_NOT_ALLOWED,"")
                        val name=s.uri.substringAfterLast('/')
                        val response=when {
                            name=="index.m3u8" -> newFixedLengthResponse(Response.Status.OK,"application/vnd.apple.mpegurl",engine.playlist())
                            name=="stream.ts" -> CastHttpResponse.live(engine.continuous())
                            name.matches(Regex("[0-9]+\\.ts")) -> engine.segment(name.removeSuffix(".ts").toIntOrNull() ?: -1)?.let { newFixedLengthResponse(Response.Status.OK,"video/mp2t",it.inputStream(),it.size.toLong()) } ?: reply(Response.Status.NOT_FOUND,"")
                            else -> reply(Response.Status.NOT_FOUND,"")
                        }
                        response.addHeader("Access-Control-Allow-Origin","*");response.addHeader("Access-Control-Allow-Private-Network","true")
                        response.addHeader("Cache-Control","no-store");response.addHeader("transferMode.dlna.org","Streaming")
                        response.addHeader("contentFeatures.dlna.org","DLNA.ORG_OP=00;DLNA.ORG_CI=1;DLNA.ORG_FLAGS=01700000000000000000000000000000")
                        return response
                    }
                    if(key.isBlank() || s.uri!="/$key/video.${source?.name?.substringAfterLast('.',"ts") ?: "ts"}") return reply(Response.Status.NOT_FOUND,"")
                    if(s.method==Method.OPTIONS) return reply(Response.Status.OK,"").apply {
                        addHeader("Access-Control-Allow-Origin","*"); addHeader("Access-Control-Allow-Methods","GET, HEAD, OPTIONS"); addHeader("Access-Control-Allow-Headers","Range")
                        addHeader("Access-Control-Allow-Private-Network","true")
                    }
                    if(s.method !in listOf(Method.GET,Method.HEAD)) return reply(Response.Status.METHOD_NOT_ALLOWED,"")
                    val media=source ?: return reply(Response.Status.NOT_FOUND,"")
                    val range=runCatching { RecordingByteRange.parse(s.headers["range"],media.size) }.getOrElse {
                        return reply(Response.Status.RANGE_NOT_SATISFIABLE,"").apply { addHeader("Content-Range","bytes */${media.size}") }
                    }
                    return try {
                        newFixedLengthResponse(if(s.headers.containsKey("range")) Response.Status.PARTIAL_CONTENT else Response.Status.OK,
                            CastSupport.mime(media.name),media.open(this@CastMediaRelayService,range.start),range.length).apply {
                            addHeader("Accept-Ranges","bytes"); addHeader("Access-Control-Allow-Origin","*")
                            addHeader("Access-Control-Expose-Headers","Content-Range, Accept-Ranges, Content-Length")
                            addHeader("Cache-Control","no-store")
                            if(s.headers.containsKey("range")) addHeader("Content-Range","bytes ${range.start}-${range.start+range.length-1}/${media.size}")
                        }
                    } catch(e: Exception) { reply(Response.Status.SERVICE_UNAVAILABLE,"Recording storage unavailable") }
                }
            }.also { CastSupport.limitConnections(it); it.start(NanoHTTPD.SOCKET_READ_TIMEOUT,false); port=it.listeningPort }
            wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"GreenStreem:cast-recording").apply { acquire() }
        } catch(e: Exception) { stopSelf() }
        return START_NOT_STICKY
    }
    override fun onDestroy() { server?.stop(); server=null;live?.close();live=null;liveSource=null; port=0; key=""; source=null; wake?.let { if(it.isHeld) it.release() }; wake=null; super.onDestroy() }
}
