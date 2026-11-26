package com.example.greenstreem

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.security.SecureRandom
import java.util.UUID

/** Read-only LAN library. Pairing and playback never expose SMB logins or provider URLs. */
class RecordingShareService: Service() {
    companion object {
        const val TYPE="_greenstreem._tcp."
        const val PREFS="recording_sharing"
        @Volatile var running=false; private set
        @Volatile var port=0; private set
        @Volatile var status="Sharing is off"; private set
        @Volatile private var pairingCode=""
        @Volatile private var pairingUntil=0L
        fun openPairing(): String {
            pairingCode="%06d".format(SecureRandom().nextInt(1_000_000))
            pairingUntil=android.os.SystemClock.elapsedRealtime()+300_000
            return pairingCode
        }
        fun currentCode()=pairingCode.takeIf { android.os.SystemClock.elapsedRealtime()<pairingUntil }.orEmpty()
        fun start(c: Context) {
            val intent=Intent(c,RecordingShareService::class.java)
            if(Build.VERSION.SDK_INT>=26) c.startForegroundService(intent) else c.startService(intent)
        }
        fun enabled(c: Context)=c.getSharedPreferences(PREFS,0).getBoolean("enabled",false)
        fun stop(c: Context) {
            c.getSharedPreferences(PREFS,0).edit().putBoolean("enabled",false).apply()
            c.stopService(Intent(c,RecordingShareService::class.java))
        }
    }
    private var server: NanoHTTPD?=null
    private var registration: NsdManager.RegistrationListener?=null
    private var multicast: WifiManager.MulticastLock?=null
    private var wake: PowerManager.WakeLock?=null
    override fun onBind(intent: Intent?)=null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action=="stop") { stop(this); return START_NOT_STICKY }
        if(!enabled(this)) { stopSelf(); return START_NOT_STICKY }
        val manager=getSystemService(NotificationManager::class.java)
        if(Build.VERSION.SDK_INT>=26) manager.createNotificationChannel(NotificationChannel("recording_share","Home recording sharing",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,0,Intent(this,SharedRecordingsActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,1,Intent(this,RecordingShareService::class.java).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=if(Build.VERSION.SDK_INT>=26) Notification.Builder(this,"recording_share") else Notification.Builder(this)
        startForeground(803,notification.setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("Sharing GreenStreem recordings")
            .setContentText("Paired devices on your home network can play this folder")
            .setContentIntent(open).addAction(android.R.drawable.ic_menu_close_clear_cancel,"Stop sharing",stop).setOngoing(true).build())
        if(server!=null) return START_STICKY
        try {
            val prefs=getSharedPreferences(PREFS,0)
            val hostId=prefs.getString("id",null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("id",it).apply() }
            val http=LibraryServer(this)
            http.start(NanoHTTPD.SOCKET_READ_TIMEOUT,false)
            server=http; port=http.listeningPort
            multicast=(applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.createMulticastLock("GreenStreem recordings")?.apply { setReferenceCounted(false); acquire() }
            wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"GreenStreem:recording-share").apply { acquire() }
            running=true; status="Sharing on home network"
            val listener=object: NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) { status="Sharing as ${info.serviceName}" }
                override fun onRegistrationFailed(info: NsdServiceInfo,error: Int) { status="Sharing is on. Use Connect by address if discovery is unavailable." }
                override fun onServiceUnregistered(info: NsdServiceInfo) {}
                override fun onUnregistrationFailed(info: NsdServiceInfo,error: Int) {}
            }
            registration=listener
            runCatching { getSystemService(NsdManager::class.java).registerService(NsdServiceInfo().apply {
                serviceName="GreenStreem ${Build.MODEL}"; serviceType=TYPE; setPort(RecordingShareService.port); setAttribute("id",hostId)
            },NsdManager.PROTOCOL_DNS_SD,listener) }
                .onFailure { status="Sharing is on. Use Connect by address." }
        } catch(e: Exception) { status="Could not start sharing. Try again."; stopSelf() }
        return START_STICKY
    }
    override fun onDestroy() {
        server?.stop(); server=null
        registration?.let { runCatching { getSystemService(NsdManager::class.java).unregisterService(it) } }; registration=null
        multicast?.let { if(it.isHeld) it.release() }; multicast=null
        wake?.let { if(it.isHeld) it.release() }; wake=null
        running=false; port=0; pairingCode=""; pairingUntil=0
        super.onDestroy()
    }

    private class LibraryServer(val c: Context): NanoHTTPD(0) {
        init {
            // Bound concurrent streams and idle connections on a small streaming box.
            setAsyncRunner(object: AsyncRunner {
                private val clients=java.util.Collections.synchronizedSet(mutableSetOf<ClientHandler>())
                private val pool=java.util.concurrent.ThreadPoolExecutor(0,8,30,java.util.concurrent.TimeUnit.SECONDS,
                    java.util.concurrent.SynchronousQueue(),java.util.concurrent.ThreadFactory { task -> Thread(task,"Recording share").apply { isDaemon=true } })
                override fun exec(handler: ClientHandler) {
                    clients.add(handler)
                    try { pool.execute(handler) } catch(e: java.util.concurrent.RejectedExecutionException) { clients.remove(handler); handler.close() }
                }
                override fun closed(handler: ClientHandler) { clients.remove(handler) }
                override fun closeAll() {
                    val open=synchronized(clients) { clients.toList() }
                    open.forEach { it.close() }; clients.clear(); pool.shutdownNow()
                }
            })
        }
        private var attempts=0
        private var attemptWindow=0L
        @Volatile private var catalog=emptyList<SharedRecordingLibrary.Item>()
        @Synchronized private fun pair(code: String): String? {
            val now=android.os.SystemClock.elapsedRealtime()
            if(now-attemptWindow>60_000) { attemptWindow=now; attempts=0 }
            if(++attempts>10 || code.isBlank() || code!=currentCode()) return null
            val token=UUID.randomUUID().toString()+UUID.randomUUID().toString()
            val p=c.getSharedPreferences(PREFS,0)
            val tokens=p.getStringSet("tokens",emptySet()).orEmpty().toMutableSet()
            if(tokens.size>=32) return null
            tokens.add(token); p.edit().putStringSet("tokens",tokens).commit()
            return token
        }
        private fun response(status: Response.Status,text: String)=newFixedLengthResponse(status,"application/json",text).apply {
            addHeader("Cache-Control","no-store"); addHeader("X-Content-Type-Options","nosniff")
        }
        override fun serve(session: IHTTPSession): Response {
            try {
                val address=InetAddress.getByName(session.remoteIpAddress)
                if(!(address.isSiteLocalAddress || address.isLinkLocalAddress || address.isLoopbackAddress) || session.headers.containsKey("origin"))
                    return response(Response.Status.FORBIDDEN,"{}")
                if(session.uri=="/pair" && session.method==Method.POST) {
                    val token=pair(session.headers["x-pair-code"].orEmpty()) ?: return response(Response.Status.FORBIDDEN,"{}")
                    return response(Response.Status.OK,JSONObject().put("token",token).put("id",c.getSharedPreferences(PREFS,0).getString("id","")).toString())
                }
                val token=session.headers["authorization"].orEmpty().removePrefix("Bearer ")
                if(token.isBlank() || token !in c.getSharedPreferences(PREFS,0).getStringSet("tokens",emptySet()).orEmpty())
                    return response(Response.Status.UNAUTHORIZED,"{}")
                if(session.method !in listOf(Method.GET,Method.HEAD)) return response(Response.Status.METHOD_NOT_ALLOWED,"{}")
                if(session.uri=="/library") {
                    catalog=SharedRecordingLibrary.list(c)
                    return response(Response.Status.OK,JSONArray(catalog.map { it.json() }).toString())
                }
                if(!session.uri.startsWith("/file/")) return response(Response.Status.NOT_FOUND,"{}")
                val id=session.uri.removePrefix("/file/")
                val previous=catalog.firstOrNull { it.id==id } ?: return response(Response.Status.NOT_FOUND,"{}")
                // Recheck selected folder and active recording state before opening a previously listed file.
                val item=SharedRecordingLibrary.list(c).firstOrNull { it.id==previous.id } ?: return response(Response.Status.NOT_FOUND,"{}")
                val range=try { RecordingByteRange.parse(session.headers["range"],item.size) } catch(e: Exception) {
                    return response(Response.Status.RANGE_NOT_SATISFIABLE,"{}").apply { addHeader("Content-Range","bytes */${item.size}") }
                }
                val input=SharedRecordingLibrary.open(c,item,range.start)
                return newFixedLengthResponse(if(session.headers.containsKey("range")) Response.Status.PARTIAL_CONTENT else Response.Status.OK,
                    "application/octet-stream",input,range.length).apply {
                    addHeader("Accept-Ranges","bytes"); addHeader("Cache-Control","no-store")
                    if(session.headers.containsKey("range")) addHeader("Content-Range","bytes ${range.start}-${range.start+range.length-1}/${item.size}")
                }
            } catch(e: Exception) { return response(Response.Status.SERVICE_UNAVAILABLE,"{\"error\":\"Recording folder is unavailable. Check storage on the sharing device.\"}") }
        }
    }
}
