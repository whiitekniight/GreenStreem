package com.example.greenstreem

import android.app.*
import android.content.Intent
import android.os.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class RecordingService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private val stopped = AtomicBoolean(false)
    @Volatile private var connection: HttpURLConnection? = null
    @Volatile private var active: String? = null
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra("id") ?: return START_NOT_STICKY
        if(intent.action=="stop") {
            if(active==id) { stopped.set(true); connection?.disconnect() }
            else if(active==null) stopSelf()
            return START_NOT_STICKY
        }
        if(active!=null) return START_NOT_STICKY
        val job = RecordingSchedule.jobs(this).find { it.id==id } ?: run { stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(NotificationManager::class.java)
        if(Build.VERSION.SDK_INT>=26) manager.createNotificationChannel(NotificationChannel("recording","Scheduled recordings",NotificationManager.IMPORTANCE_LOW))
        val notification = if(Build.VERSION.SDK_INT>=26) Notification.Builder(this,"recording") else Notification.Builder(this)
        startForeground(801,notification.setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Recording ${job.title}")
            .setContentText("Saving to your chosen recording folder").setOngoing(true).build())
        active=id
        executor.execute { record(job) }
        return START_NOT_STICKY
    }
    private fun record(originalJob: RecordingSchedule.Job) {
        var job=originalJob
        val wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"GreenStreem:recording")
        val deadline = job.end
        var bytes = 0L
        val timer = Executors.newSingleThreadScheduledExecutor()
        try {
            require(deadline>System.currentTimeMillis()) { "Program already ended" }
            wake.acquire((deadline-System.currentTimeMillis()+60_000).coerceAtLeast(1000))
            RecordingSchedule.save(this,job.copy(state="Recording"))
            timer.schedule({ connection?.disconnect() },deadline-System.currentTimeMillis(),java.util.concurrent.TimeUnit.MILLISECONDS)
            val p = getSharedPreferences(DownloadPrefs.PREFS_NAME,0)
            val user = p.getString(DownloadPrefs.KEY_SMB_USERNAME,"").orEmpty()
            val pass = p.getString(DownloadPrefs.KEY_SMB_PASSWORD,"").orEmpty()
            val conn = (URL(job.url).openConnection() as HttpURLConnection).apply { connectTimeout=15000; readTimeout=15000 }
            connection=conn
            require(conn.responseCode in 200..299) { "Provider refused stream (${conn.responseCode})" }
            conn.inputStream.buffered().use { input ->
                // Never save an HLS playlist or an error page as a recording.
                input.mark(1024)
                val prefix=ByteArray(564)
                var count=0
                while(count<prefix.size) { val n=input.read(prefix,count,prefix.size-count); if(n<0) break; count+=n }
                input.reset()
                require(count>=376 && (0 until 188).any { prefix[it]==0x47.toByte() && prefix[it+188]==0x47.toByte() }) {
                    "This channel does not provide a supported MPEG-TS recording stream"
                }
                val destination = if(job.treeUri.isNotBlank()) {
                    val tree=android.net.Uri.parse(job.treeUri)
                    val parent=android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,android.provider.DocumentsContract.getTreeDocumentId(tree))
                    val document=android.provider.DocumentsContract.createDocument(contentResolver,parent,"video/mp2t",job.file)
                        ?: throw java.io.IOException("Cannot create recording in selected folder")
                    job=job.copy(outputUri=document.toString())
                    RecordingSchedule.save(this,job.copy(state="Recording"))
                    contentResolver.openOutputStream(document,"w") ?: throw java.io.IOException("Cannot write recording")
                } else SmbBrowser.openRecordingOutput(job.server,user,pass,job.share,job.folder,job.file)
                destination.use { output ->
                    val buffer=ByteArray(64*1024)
                    var updated=0L
                    while(!stopped.get() && System.currentTimeMillis()<deadline) {
                        val n=try { input.read(buffer) } catch(e: java.io.IOException) {
                            if(System.currentTimeMillis()>=deadline || stopped.get()) break else throw e
                        }
                        if(n<0) throw java.io.IOException("Stream ended before the program finished")
                        output.write(buffer,0,n); bytes+=n
                        if(System.currentTimeMillis()-updated>5000) {
                            RecordingSchedule.save(this,job.copy(state="Recording",bytes=bytes)); updated=System.currentTimeMillis()
                        }
                    }
                }
            }
            finish(job,bytes,null)
        } catch(e: Exception) { finish(job,bytes,e) }
        finally { timer.shutdownNow(); connection?.disconnect(); connection=null; if(wake.isHeld) wake.release(); active=null; stopForeground(true); stopSelf() }
    }
    private fun finish(job: RecordingSchedule.Job, bytes: Long, error: Exception?) {
        val cancelled=stopped.get() || RecordingSchedule.jobs(this).find { it.id==job.id }?.state=="Cancelled"
        val complete=error==null && System.currentTimeMillis()>=job.end && bytes>0
        RecordingSchedule.save(this,job.copy(bytes=bytes,state=when {cancelled -> "Cancelled";complete -> "Completed";bytes>0 -> "Partial";else -> "Failed"},
            detail=if(cancelled) "Stopped by user" else if(complete) "" else "Recording interrupted: check stream and recording storage"))
    }
    override fun onTimeout(startId: Int, fgsType: Int) { stopped.set(true); connection?.disconnect(); stopSelf() }
    override fun onDestroy() { stopped.set(true); connection?.disconnect(); executor.shutdownNow(); super.onDestroy() }
}
