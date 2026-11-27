package com.example.greenstreem

import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.*
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

internal class RecordingImprovementsChecks(private val i: Instrumentation) {
    private val c get()=i.targetContext
    private fun <T> ui(block: ()->T): T { if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper()) return block();var result:T?=null;i.runOnMainSync { result=block() };@Suppress("UNCHECKED_CAST") return result as T }
    private fun waitFor(message: String,block: ()->Boolean) {
        val until=SystemClock.uptimeMillis()+20000
        while(SystemClock.uptimeMillis()<until) { if(block())return;Thread.sleep(100) };error(message)
    }
    private fun tap(label: String) {
        waitFor("Missing button: $label") {
            val node=i.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(label)?.firstOrNull { it.text?.toString()?.equals(label,true)==true } ?: return@waitFor false
            var target: android.view.accessibility.AccessibilityNodeInfo?=node
            while(target!=null && !target.isClickable) target=target.parent
            target?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)==true
        }
        Thread.sleep(250)
    }
    fun run() {
        check(c.packageName.endsWith(".feedbackcheck"))
        val prefs=c.getSharedPreferences("recordings",0);prefs.edit().clear().commit();PcRecorder.forget(c)
        val remoteState=java.util.concurrent.atomic.AtomicReference("Scheduled")
        val captured=java.util.concurrent.atomic.AtomicReference<JSONObject?>(null)
        val requests=java.util.concurrent.CopyOnWriteArrayList<String>()
        val server=object: NanoHTTPD("127.0.0.1",0) {
            override fun serve(s: IHTTPSession): Response {
                requests.add(s.method.toString()+" "+s.uri)
                val posted=mutableMapOf<String,String>()
                if(s.method==Method.POST) s.parseBody(posted)
                val response=when(s.uri) {
                    "/v1/info" -> JSONObject().put("version","0.2.0")
                    "/v1/pair" -> JSONObject().put("token","fixture-only")
                    "/v1/jobs" -> if(s.method==Method.POST) {
                        JSONObject(posted["postData"]!!).also { captured.set(it) }
                    } else JSONObject().put("folder","Fixture computer storage").put("jobs",JSONArray((1..40).map { n ->
                        JSONObject().put("id","remote-$n").put("title","Computer recording $n").put("channel","Fixture")
                            .put("start",System.currentTimeMillis()+n*60000).put("end",System.currentTimeMillis()+n*60000+30000)
                            .put("state",remoteState.get()).put("bytes",0).put("weekly",0).put("detail","")
                    }))
                    else -> return newFixedLengthResponse(Response.Status.OK,"audio/x-mpegurl","#EXTM3U\n#EXTINF:-1 group-title=\"Fixture\",Fixture\nhttp://127.0.0.1:$listeningPort/live.ts")
                }
                return newFixedLengthResponse(Response.Status.OK,"application/json",response.toString())
            }
        }
        server.start(5000,true)
        var main: MainActivity?=null;var recordings: RecordingsActivity?=null
        try {
            c.getSharedPreferences("iptv_prefs",0).edit().clear().putString("ui_mode","mobile").putBoolean("pro_unlocked",true).putBoolean("updater_auto_check",false).commit()
            val url="http://127.0.0.1:${server.listeningPort}/playlist.m3u"
            PlaylistProfilesManager.upsertProfileAndActivate(c,PlaylistProfilesManager.createProfile("Recording improvements fixture",url,"",""))
            c.getSharedPreferences("iptv_prefs",0).edit().putString("playlist_type","m3u").putString("m3u_url",url).putBoolean("has_playlist",true).putBoolean("playlist_enabled",true).commit()
            val storage=RecordingLocalFolders.prepare(c,RecordingLocalFolders.available(c).first())
            c.getSharedPreferences(DownloadPrefs.PREFS_NAME,0).edit().putString("recording_tree_uri",storage).commit()
            check(RecordingSchedule.alarmAllowed(c)) { "Grant exact alarms to the isolated fixture" }
            main=i.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val activity=main
            Thread.sleep(1500)
            val start=(System.currentTimeMillis()/1000+3600)*1000
            val channel=Channel(1,"Fixture","Fixture",streamUrl="http://127.0.0.1:${server.listeningPort}/live.ts")
            val listing=XtreamEpgListing("1","1",android.util.Base64.encodeToString("Scheduled fixture".toByteArray(),2),"","","",start/1000,(start+3600000)/1000)
            fun dialog(now: Boolean)=ui {
                MainActivity::class.java.getDeclaredMethod("recordingDialog",Channel::class.java,XtreamEpgListing::class.java,Boolean::class.javaPrimitiveType)
                    .apply { isAccessible=true }.invoke(activity,channel,listing,now)
            }
            dialog(false);tap("Repeat weekly · same day and local time");tap("Schedule")
            waitFor("Local weekly schedule not saved") { RecordingSchedule.jobs(c).any { it.weekly } }
            val scheduled=RecordingSchedule.jobs(c).single()
            check(scheduled.start==start-180000 && scheduled.end==start+3600000+180000) { "Default padding was not three minutes each" }
            ui { RecordingSchedule.cancel(c,scheduled) }
            dialog(true);tap("Record now")
            waitFor("Record now did not create a job") { RecordingSchedule.jobs(c).size==2 }
            val immediate=RecordingSchedule.jobs(c).first { it.id!=scheduled.id }
            check(immediate.start<=System.currentTimeMillis() && immediate.start>System.currentTimeMillis()-10000 && !immediate.weekly)
            ui { RecordingSchedule.cancel(c,immediate) }
            val zone=java.util.TimeZone.getTimeZone("America/Chicago")
            val calendar=java.util.Calendar.getInstance(zone).apply { clear();set(2027,2,7,20,0) }
            val recurring=scheduled.copy(id=java.util.UUID.randomUUID().toString(),series=java.util.UUID.randomUUID().toString(),start=calendar.timeInMillis,end=calendar.timeInMillis+3600000,state="Completed",weekly=true,zone=zone.id)
            RecordingSchedule.save(c,recurring);RecordingSchedule.advanceWeekly(c,recurring);RecordingSchedule.advanceWeekly(c,recurring)
            val next=RecordingSchedule.jobs(c).single { it.series==recurring.series && it.id!=recurring.id }
            check(next.start-recurring.start==167*3600000L) { "Weekly schedule drifted across daylight saving" }
            ui { RecordingSchedule.cancel(c,recurring) }
            check(RecordingSchedule.jobs(c).filter { it.series==recurring.series }.none { it.weekly || it.state=="Scheduled" })
            ui {
                val adapter=EpgRowAdapter(8,ScrollSync(),{_,_->},{_,_->},{_,_->},{})
                adapter.setTimelineStartTimestamp(start/1000);adapter.setData(listOf(channel),mapOf(1 to listOf(listing)))
                val holder=adapter.onCreateViewHolder(FrameLayout(activity),0)
                adapter.setRecordingMarkers(listOf(RecordingMarker(channel.name,start,start+3600000,false)))
                adapter.onBindViewHolder(holder,0)
                val label=holder.container.getChildAt(0).findViewById<TextView>(R.id.tvProgramTitle)
                check(label.text.toString().startsWith("\u25cf ")) { "Scheduled program has no red dot" }
                val card=holder.container.getChildAt(0)
                adapter.setRecordingMarkers(emptyList());adapter.onBindViewHolder(holder,0,mutableListOf("RECORDINGS"))
                check(holder.container.getChildAt(0)===card && !label.text.toString().startsWith("\u25cf")) { "Marker refresh replaced the guide card or failed to clear" }
            }
            runBlocking { PcRecorder.connect(c,"127.0.0.1:${server.listeningPort}","123456") }
            check(PcRecorder.enabled(c)) { "Pairing did not enable the recorder" }
            runBlocking { PcRecorder.jobs(c) }
            ui { check(!activity.isDestroyed && !activity.isFinishing) { "Main activity closed before remote scheduling" } }
            dialog(false);tap("Repeat weekly · same day and local time");tap("Schedule")
            waitFor("Computer did not receive weekly schedule; requests=$requests") { captured.get()!=null }
            check(captured.get()!!.getBoolean("weekly") && captured.get()!!.getLong("start")==start-180000 && captured.get()!!.getLong("end")==start+3780000)
            val monitor=i.addMonitor(RecordingsActivity::class.java.name,null,false)
            ui { MainActivity::class.java.getDeclaredMethod("launchInternalActivity",Intent::class.java).apply { isAccessible=true }.invoke(activity,Intent(activity,RecordingsActivity::class.java)) }
            recordings=i.waitForMonitorWithTimeout(monitor,10000) as? RecordingsActivity
            i.removeMonitor(monitor)
            check(recordings!=null) { "Recordings did not open through the internal app route" }
            val page=recordings!!
            fun list(): ListView=ui { fun find(v: View): ListView? { if(v is ListView)return v;if(v is android.view.ViewGroup) for(n in 0 until v.childCount) find(v.getChildAt(n))?.let{return it};return null };find(page.window.decorView)!! }
            waitFor("Computer recordings not in main Recordings list") { ui { list().adapter?.count ?: 0 }>=44 }
            ui { list().setSelectionFromTop(15,-10) };Thread.sleep(300)
            val previous=ui { list().firstVisiblePosition }
            remoteState.set("Completed")
            waitFor("Computer status did not auto-refresh") { ui { (0 until list().adapter.count).any { list().adapter.getItem(it).toString().let { value -> value.contains("Computer recording 40") && value.contains("Completed") } } } }
            check(ui { list().firstVisiblePosition }==previous) { "Auto-refresh reset the recordings scroll position" }
            check(ui { page.window.decorView.findViewWithTag<View>("screen_back")!=null }) { "Recordings has no visible Back button" }
            android.util.Log.i("RecordingImprovementsChecks","PASS record now; local/remote weekly and padding; DST/idempotence/cancel; in-place guide dot; unified computer/device library; auto-refresh preserves scroll; visible Back")
        } finally {
            ui { recordings?.finish();main?.finish();RecordingSchedule.jobs(c).filter { it.state in listOf("Scheduled","Recording") || it.weekly }.forEach { RecordingSchedule.cancel(c,it) } }
            prefs.edit().clear().commit();PcRecorder.forget(c);server.stop()
        }
    }
}
