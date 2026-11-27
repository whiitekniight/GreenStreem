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
        val file: String, val state: String = "Scheduled", val bytes: Long = 0, val detail: String = "", val treeUri: String = "", val outputUri: String = "", val weekly: Boolean = false, val series: String = "", val zone: String = java.util.TimeZone.getDefault().id, val guideStart: Long = start, val guideEnd: Long = end) {
        fun json() = JSONObject().apply {
            put("guideStart",guideStart); put("guideEnd",guideEnd); put("weekly",weekly); put("series",series); put("zone",zone); put("id",id); put("title",title); put("channel",channel); put("url",url)
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
                it.optLong("bytes"),it.optString("detail"),it.optString("treeUri"),it.optString("outputUri"),it.optBoolean("weekly"),it.optString("series"),it.optString("zone",java.util.TimeZone.getDefault().id),it.optLong("guideStart",it.getLong("start")),it.optLong("guideEnd",it.getLong("end")))
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
    fun add(c: Context, title: String, channel: String, url: String, start: Long, end: Long, weekly: Boolean = false, guideStart: Long = start, guideEnd: Long = end): Job {
        require(android.net.Uri.parse(url).scheme in listOf("http", "https")) { "This channel has no supported recording URL" }
        require(!android.net.Uri.parse(url).path.orEmpty().endsWith(".m3u8",true)) { "HLS-only channel recording is not supported yet; choose a transport-stream channel" }
        require(end>start && end-maxOf(start,System.currentTimeMillis())<=24*3600000L) { "Choose a recording of up to 24 hours" }
        require(end > System.currentTimeMillis()) { "This program has already ended" }
        require(alarmAllowed(c)) { "Allow Alarms & reminders first" }
        require(jobs(c).none { (it.weekly || it.state in listOf("Scheduled","Recording")) && overlaps(start,end,weekly,java.util.TimeZone.getDefault().id,it) }) {
            "Another recording overlaps this program. Only one recording at a time is supported."
        }
        val p = c.getSharedPreferences(DownloadPrefs.PREFS_NAME,0)
        val server = p.getString(DownloadPrefs.KEY_SMB_SERVER,"").orEmpty()
        val share = p.getString(DownloadPrefs.KEY_SMB_SHARE,"").orEmpty()
        val tree = p.getString("recording_tree_uri", "").orEmpty()
        require(tree.isNotBlank() || (server.isNotBlank() && share.isNotBlank())) { "Choose a recording folder first" }
        if(tree.isNotBlank()) {
            if(RecordingLocalFolders.direct(tree)) require(RecordingLocalFolders.directory(c,tree).canWrite()) { "Recording storage is unavailable" }
            else require(c.contentResolver.persistedUriPermissions.any { it.uri.toString()==tree && it.isWritePermission }) { "Select your local or USB recording folder again" }
        }
        val folder = p.getString(DownloadPrefs.KEY_SMB_FOLDER,"").orEmpty()
        val id = UUID.randomUUID().toString()
        val job = Job(id,title,channel,url,start,end,server,share,folder,
            "${DownloadPrefs.cleanPathSegment(title)}-${start}-${id.take(8)}.ts", treeUri=tree, weekly=weekly, series=if(weekly) id else "",guideStart=guideStart,guideEnd=guideEnd)
        save(c,job)
        try { arm(c,job) } catch(e: Exception) { save(c,job.copy(state="Failed",detail="Could not schedule alarm")); throw e }
        return job
    }
    private fun overlaps(start: Long,end: Long,weekly: Boolean,zone: String,job: Job): Boolean {
        val horizon=maxOf(start,job.start)+370L*86400000
        val floor=maxOf(start,job.start)-86400000
        fun slots(a: Long,b: Long,repeat: Boolean,tz: String)=sequence {
            val first=java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone(tz)).apply { timeInMillis=a }
            val last=java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone(tz)).apply { timeInMillis=b }
            while(repeat && last.timeInMillis<floor) { first.add(java.util.Calendar.DATE,7);last.add(java.util.Calendar.DATE,7) }
            while(first.timeInMillis<horizon) {
                yield(first.timeInMillis to last.timeInMillis)
                if(!repeat) break
                first.add(java.util.Calendar.DATE,7);last.add(java.util.Calendar.DATE,7)
            }
        }
        val left=slots(start,end,weekly,zone).iterator()
        val right=slots(job.start,job.end,job.weekly,job.zone).iterator()
        var a=if(left.hasNext()) left.next() else null
        var b=if(right.hasNext()) right.next() else null
        while(a!=null && b!=null) {
            if(a.first<b.second && a.second>b.first) return true
            if(a.second<=b.first) a=if(left.hasNext()) left.next() else null
            else b=if(right.hasNext()) right.next() else null
        }
        return false
    }
    @Synchronized fun advanceWeekly(c: Context, job: Job) {
        val saved=jobs(c).find { it.id==job.id } ?: return
        if(!saved.weekly || saved.state=="Cancelled") return
        val start=java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone(job.zone)).apply { timeInMillis=job.start }
        val end=java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone(job.zone)).apply { timeInMillis=job.end }
        do { start.add(java.util.Calendar.DATE,7); end.add(java.util.Calendar.DATE,7) } while(end.timeInMillis<=System.currentTimeMillis())
        val id=UUID.nameUUIDFromBytes((job.series+":"+start.timeInMillis).toByteArray()).toString()
        if(jobs(c).any { it.id==id }) return
        val next=job.copy(id=id,start=start.timeInMillis,end=end.timeInMillis,guideStart=start.timeInMillis+(job.guideStart-job.start),guideEnd=end.timeInMillis-(job.end-job.guideEnd),state="Scheduled",bytes=0,detail="Weekly · same local time",outputUri="",
            file="${DownloadPrefs.cleanPathSegment(job.title)}-${start.timeInMillis}-${id.take(8)}.ts")
        save(c,next)
        runCatching { arm(c,next) }.onFailure { save(c,next.copy(state="Failed",detail="Allow Alarms & reminders to resume weekly recording")) }
    }
    fun restore(c: Context) {
        jobs(c).filter { it.weekly && it.end<=System.currentTimeMillis() && it.state!="Cancelled" }.forEach { advanceWeekly(c,it) }
        jobs(c).filter { it.state in listOf("Scheduled","Recording") }.forEach {
            if(it.state=="Recording") save(c,it.copy(state=if(it.bytes>0) "Partial" else "Failed",detail="Recording was interrupted by a device or app restart"))
            else if(it.end <= System.currentTimeMillis()) save(c,it.copy(state=if(it.bytes>0) "Partial" else "Missed",detail="Device was unavailable"))
            else if(alarmAllowed(c)) runCatching { arm(c,it) }.onFailure { _ ->
                save(c,it.copy(state="Failed",detail="Could not restore recording alarm"))
            }
        }
    }
    /** Runs on IO; remove the row only after its saved file has been deleted. */
    fun delete(c: Context, id: String) {
        val job=jobs(c).firstOrNull { it.id==id } ?: return
        check(!job.weekly && job.state !in listOf("Scheduled","Recording") && !RecordingService.isRecording(id)) { "Cancel the recording and wait for it to stop first" }
        if(job.outputUri.isNotBlank()) {
            if(RecordingLocalFolders.direct(job.outputUri)) {
                val file=RecordingLocalFolders.file(c,job.outputUri)
                check(file.delete()) { "Could not delete the saved recording" }
            } else check(android.provider.DocumentsContract.deleteDocument(c.contentResolver,Uri.parse(job.outputUri))) { "Could not delete the saved recording" }
        } else if(job.treeUri.isBlank()) {
            SmbBrowser.deleteRecording(c,job)
        }
        synchronized(this) {
            val remaining=jobs(c).filter { it.id!=id }
            check(c.getSharedPreferences("recordings",0).edit().putString("jobs",JSONArray(remaining.map { it.json() }).toString()).commit())
        }
    }

    @Synchronized fun cancel(c: Context, job: Job) {
        if(job.series.isNotBlank()) jobs(c).filter { it.series==job.series }.forEach {
            c.getSystemService(AlarmManager::class.java).cancel(pending(c,it.id))
            save(c,it.copy(weekly=false,state=if(it.state in listOf("Scheduled","Recording")) "Cancelled" else it.state))
            if(it.state=="Recording") c.startService(Intent(c,RecordingService::class.java).setAction("stop").putExtra("id",it.id))
        }
        c.getSystemService(AlarmManager::class.java).cancel(pending(c,job.id))
        save(c,job.copy(state=if(job.state in listOf("Scheduled","Recording")) "Cancelled" else job.state,weekly=false))
        c.startService(Intent(c,RecordingService::class.java).setAction("stop").putExtra("id",job.id))
    }
}

class RecordingAlarm : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        if(intent.action != "record") {
            RecordingSchedule.restore(c)
            if(RecordingShareService.enabled(c)) runCatching { RecordingShareService.start(c) }
            return
        }
        val id = intent.data?.lastPathSegment ?: return
        val job = RecordingSchedule.jobs(c).find { it.id==id && it.state in listOf("Scheduled","Recording") } ?: return
        try {
            val service = Intent(c,RecordingService::class.java).putExtra("id",id)
            if(Build.VERSION.SDK_INT>=26) c.startForegroundService(service) else c.startService(service)
        } catch(e: Exception) { RecordingSchedule.save(c,job.copy(state="Failed",detail="Android blocked background recording; open the app and check permissions")) }
    }
}
