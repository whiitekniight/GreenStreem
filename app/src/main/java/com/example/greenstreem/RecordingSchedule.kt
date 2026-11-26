package com.example.greenstreem

import android.app.*
import android.content.*
import android.os.Build
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Durable jobs; credentials remain in private app preferences, never notifications. */
object RecordingSchedule {
    data class Job(val id: String, val title: String, val channel: String, val url: String,
        val start: Long, val end: Long, val server: String, val share: String, val folder: String,
        val file: String, val state: String = "Scheduled", val bytes: Long = 0, val detail: String = "", val treeUri: String = "", val outputUri: String = "") {
        fun json() = JSONObject().apply {
            put("id",id); put("title",title); put("channel",channel); put("url",url)
            put("start",start); put("end",end); put("server",server); put("share",share)
            put("treeUri",treeUri); put("outputUri",outputUri); put("folder",folder); put("file",file); put("state",state); put("bytes",bytes); put("detail",detail)
        }
    }
    @Synchronized fun jobs(c: Context): List<Job> {
        val array = JSONArray(c.getSharedPreferences("recordings",0).getString("jobs","[]"))
        return (0 until array.length()).map { n -> array.getJSONObject(n).let {
            Job(it.getString("id"),it.getString("title"),it.getString("channel"),it.getString("url"),
                it.getLong("start"),it.getLong("end"),it.getString("server"),it.getString("share"),
                it.getString("folder"),it.getString("file"),it.optString("state","Scheduled"),
                it.optLong("bytes"),it.optString("detail"),it.optString("treeUri"),it.optString("outputUri"))
        }}
    }
    @Synchronized fun save(c: Context, job: Job) {
        val all = jobs(c).filter { it.id != job.id } + job
        check(c.getSharedPreferences("recordings",0).edit().putString("jobs",JSONArray(all.map { it.json() }).toString()).commit())
    }
    fun alarmAllowed(c: Context) = Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    private fun pending(c: Context, id: String) = PendingIntent.getBroadcast(c,0,
        Intent(c, RecordingAlarm::class.java).setAction("record").setData(Uri.parse("greenstreem://record/$id")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun arm(c: Context, job: Job) {
        check(alarmAllowed(c)) { "Allow Alarms & reminders first" }
        c.getSystemService(AlarmManager::class.java).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
            maxOf(System.currentTimeMillis()+1000,job.start),pending(c,job.id))
    }
    fun add(c: Context, title: String, channel: String, url: String, start: Long, end: Long): Job {
        require(android.net.Uri.parse(url).scheme in listOf("http", "https")) { "This channel has no supported recording URL" }
        require(!android.net.Uri.parse(url).path.orEmpty().endsWith(".m3u8",true)) { "HLS-only channel recording is not supported yet; choose a transport-stream channel" }
        require(end > System.currentTimeMillis()) { "This program has already ended" }
        require(alarmAllowed(c)) { "Allow Alarms & reminders first" }
        require(jobs(c).none { it.state in listOf("Scheduled","Recording") && it.start < end && it.end > start }) {
            "Another recording overlaps this program. Only one recording at a time is supported."
        }
        val p = c.getSharedPreferences(DownloadPrefs.PREFS_NAME,0)
        val server = p.getString(DownloadPrefs.KEY_SMB_SERVER,"").orEmpty()
        val share = p.getString(DownloadPrefs.KEY_SMB_SHARE,"").orEmpty()
        val tree = p.getString("recording_tree_uri", "").orEmpty()
        require(tree.isNotBlank() || (server.isNotBlank() && share.isNotBlank())) { "Choose a recording folder first" }
        if(tree.isNotBlank()) require(c.contentResolver.persistedUriPermissions.any { it.uri.toString()==tree && it.isWritePermission }) { "Select your local or USB recording folder again" }
        val folder = p.getString(DownloadPrefs.KEY_SMB_FOLDER,"").orEmpty()
        val id = UUID.randomUUID().toString()
        val job = Job(id,title,channel,url,start,end,server,share,folder,
            "${DownloadPrefs.cleanPathSegment(title)}-${start}-${id.take(8)}.ts", treeUri=tree)
        save(c,job)
        try { arm(c,job) } catch(e: Exception) { save(c,job.copy(state="Failed",detail="Could not schedule alarm")); throw e }
        return job
    }
    fun restore(c: Context) {
        jobs(c).filter { it.state in listOf("Scheduled","Recording") }.forEach {
            if(it.state=="Recording") save(c,it.copy(state=if(it.bytes>0) "Partial" else "Failed",detail="Recording was interrupted by a device or app restart"))
            else if(it.end <= System.currentTimeMillis()) save(c,it.copy(state=if(it.bytes>0) "Partial" else "Missed",detail="Device was unavailable"))
            else if(alarmAllowed(c)) runCatching { arm(c,it) }.onFailure { _ ->
                save(c,it.copy(state="Failed",detail="Could not restore recording alarm"))
            }
        }
    }
    fun cancel(c: Context, job: Job) {
        c.getSystemService(AlarmManager::class.java).cancel(pending(c,job.id))
        save(c,job.copy(state="Cancelled"))
        c.startService(Intent(c,RecordingService::class.java).setAction("stop").putExtra("id",job.id))
    }
}

class RecordingAlarm : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        if(intent.action != "record") { RecordingSchedule.restore(c); return }
        val id = intent.data?.lastPathSegment ?: return
        val job = RecordingSchedule.jobs(c).find { it.id==id && it.state in listOf("Scheduled","Recording") } ?: return
        try {
            val service = Intent(c,RecordingService::class.java).putExtra("id",id)
            if(Build.VERSION.SDK_INT>=26) c.startForegroundService(service) else c.startService(service)
        } catch(e: Exception) { RecordingSchedule.save(c,job.copy(state="Failed",detail="Android blocked background recording; open the app and check permissions")) }
    }
}
