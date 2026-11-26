package com.example.greenstreem

@Suppress("DEPRECATION")
class RecordingLifecycleTest : android.app.Instrumentation() {
    private var permissionUi=false
    private var storageProbe=false
    private var fallbackUi=false
    override fun onCreate(arguments: android.os.Bundle?) { permissionUi=arguments?.getString("permissionUi")=="true";storageProbe=arguments?.getString("storageProbe")=="true";fallbackUi=arguments?.getString("fallbackUi")=="true"; super.onCreate(arguments); start() }
    override fun onStart() {
        val result=android.os.Bundle()
        try {
            if(storageProbe) {
                val targets=RecordingLocalFolders.available(targetContext)
                check(targets.any { it.removable }) { "No app-accessible USB storage found" }
                targets.forEach { RecordingLocalFolders.prepare(targetContext,it) }
                result.putString("storageTargets",targets.joinToString { it.label })
            } else if(fallbackUi) testMissingPickerFallback()
            else if(permissionUi) testPermissionGuidance()
            else { testScheduledJobSurvivesBackgroundAndReportsStreamFailure();testDirectStorageRecording() }
            result.putString("stream","PASS: recording lifecycle / local storage assertions");finish(-1,result)
        }
        catch(e: Throwable) { result.putString("stream","FAIL: ${e}"); finish(0,result) }
    }
    private fun assertTrue(message: String, value: Boolean) { check(value) { message } }
    private fun assertEquals(expected: Any, actual: Any) { check(expected==actual) { "Expected $expected, got $actual" } }
    private fun assertFalse(value: Boolean) { check(!value) }
    private fun fail(message: String): Nothing = error(message)

    private fun testMissingPickerFallback() {
        val c=targetContext
        val prefs=c.getSharedPreferences(DownloadPrefs.PREFS_NAME,0)
        val oldTree=prefs.getString("recording_tree_uri",null)
        val activity=startActivitySync(android.content.Intent(c,RecordingsActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        fun find(node: android.view.accessibility.AccessibilityNodeInfo?,text: String): android.view.accessibility.AccessibilityNodeInfo? {
            if(node==null)return null
            if(node.text?.toString()==text)return node
            for(index in 0 until node.childCount) { val result=find(node.getChild(index),text);if(result!=null)return result }
            return null
        }
        fun tap(text: String) {
            val deadline=System.currentTimeMillis()+7000
            var node: android.view.accessibility.AccessibilityNodeInfo?=null
            while(node==null && System.currentTimeMillis()<deadline) { node=find(uiAutomation.rootInActiveWindow,text);if(node==null)Thread.sleep(100) }
            check(node!=null) { "Missing UI: $text" }
            val bounds=android.graphics.Rect();node.getBoundsInScreen(bounds)
            val time=android.os.SystemClock.uptimeMillis()
            for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
                val event=android.view.MotionEvent.obtain(time,time+20,action,bounds.exactCenterX(),bounds.exactCenterY(),0)
                event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                try { sendPointerSync(event) } finally { event.recycle() }
            }
            waitForIdleSync()
        }
        try {
            // Make the launcher fail, just as it does when the TV has no folder-picker handler.
            runOnMainSync {
                val field=RecordingsActivity::class.java.getDeclaredField("folderPicker").apply { isAccessible=true }
                (field.get(activity) as androidx.activity.result.ActivityResultLauncher<*>).unregister()
            }
            tap("Recording storage");tap("Choose local / USB folder");tap("Device storage")
            val limit=System.currentTimeMillis()+7000
            while(!prefs.getString("recording_tree_uri","").orEmpty().startsWith("file:") && System.currentTimeMillis()<limit) Thread.sleep(100)
            assertTrue("Fallback saves usable local storage",prefs.getString("recording_tree_uri","").orEmpty().startsWith("file:"))
        } finally { prefs.edit().putString("recording_tree_uri",oldTree).commit();runOnMainSync { activity.finish() } }
    }

    private fun testDirectStorageRecording() {
        val c=targetContext
        val prefs=c.getSharedPreferences(DownloadPrefs.PREFS_NAME,0)
        val store=c.getSharedPreferences("recordings",0)
        val oldTree=prefs.getString("recording_tree_uri",null)
        val oldJobs=store.getString("jobs",null)
        var job: RecordingSchedule.Job?=null
        val packet=ByteArray(188) { 0xff.toByte() }.apply { this[0]=0x47;this[1]=0x1f;this[2]=0xff.toByte();this[3]=0x10 }
        val server=object: fi.iki.elonen.NanoHTTPD("127.0.0.1",0) {
            override fun serve(session: IHTTPSession): Response = newChunkedResponse(Response.Status.OK,"video/mp2t",object: java.io.InputStream() {
                private var position=0
                override fun read(): Int { val result=packet[position%188].toInt() and 255;position++;return result }
                override fun read(bytes: ByteArray,offset: Int,length: Int): Int {
                    Thread.sleep(15)
                    val count=minOf(length,188*4)
                    repeat(count) { bytes[offset+it]=read().toByte() }
                    return count
                }
            })
        }
        try {
            val target=RecordingLocalFolders.available(c).first { !it.removable }
            val tree=RecordingLocalFolders.prepare(c,target)
            prefs.edit().putString("recording_tree_uri",tree).commit();store.edit().putString("jobs","[]").commit()
            server.start(5000,true)
            val start=System.currentTimeMillis()+2000
            val scheduled=RecordingSchedule.add(c,"Local storage test","Test channel","http://127.0.0.1:${server.listeningPort}/test.ts",start,start+4000)
            job=scheduled
            val limit=System.currentTimeMillis()+20000
            while(System.currentTimeMillis()<limit && RecordingSchedule.jobs(c).first { it.id==scheduled.id }.state in listOf("Scheduled","Recording")) Thread.sleep(200)
            val result=RecordingSchedule.jobs(c).first { it.id==scheduled.id }
            val stoppedBy=System.currentTimeMillis()+2000
            while(RecordingService.isRecording(scheduled.id) && System.currentTimeMillis()<stoppedBy) Thread.sleep(50)
            assertEquals("Completed",result.state)
            assertTrue("Recording has bytes",result.bytes>=376)
            assertTrue("Uses app-owned local storage without SAF permission",result.outputUri.startsWith("file:"))
            val item=SharedRecordingLibrary.list(c).first { it.uri==result.outputUri }
            SharedRecordingLibrary.open(c,item,188).use { assertEquals(0x47,it.read()) }
            val file=RecordingLocalFolders.file(c,result.outputUri)
            RecordingSchedule.delete(c,result.id)
            assertFalse(file.exists())
            assertTrue("Deleted row removed",RecordingSchedule.jobs(c).none { it.id==result.id })
        } finally {
            job?.let { pending ->
                val current=RecordingSchedule.jobs(c).find { it.id==pending.id }
                if(current!=null && current.state in listOf("Scheduled","Recording")) runCatching { RecordingSchedule.cancel(c,current) }
                val until=System.currentTimeMillis()+3000
                while(RecordingService.isRecording(pending.id) && System.currentTimeMillis()<until) Thread.sleep(50)
                RecordingSchedule.jobs(c).find { it.id==pending.id }?.takeIf { it.outputUri.startsWith("file:") }?.let { runCatching { RecordingLocalFolders.file(c,it.outputUri).delete() } }
            }
            server.stop()
            prefs.edit().putString("recording_tree_uri",oldTree).commit();store.edit().putString("jobs",oldJobs).commit()
        }
    }

    private fun testPermissionGuidance() {
        val activity=startActivitySync(android.content.Intent(targetContext,RecordingsActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            waitForIdleSync()
            runOnMainSync {
                fun texts(view: android.view.View): List<String> {
                    val own=if(view is android.widget.TextView) listOf(view.text.toString()) else emptyList()
                    return own + if(view is android.view.ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) } else emptyList()
                }
                val all=texts(activity.window.decorView)
                val guidance=all.any { it.startsWith("Scheduling needs permission") }
                assertEquals(!RecordingSchedule.alarmAllowed(targetContext),guidance)
                if(RecordingSchedule.jobs(targetContext).isEmpty()) assertTrue("Empty list explains scheduling",all.any { it.startsWith("No recordings yet.") })
                assertTrue("Storage controls still present",all.contains("Recording storage"))
            }
        } finally { runOnMainSync { activity.finish() } }
    }

    fun testScheduledJobSurvivesBackgroundAndReportsStreamFailure() {
        val md4=SmbMd4().digest(byteArrayOf()).joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals("31d6cfe0d16ae931b73c59d7e0c089c0",md4)
        SmbBrowser.targetUrl("test.invalid","test","")
        assertEquals("31d6cfe0d16ae931b73c59d7e0c089c0",jcifs.util.Crypto.getMD4().digest(byteArrayOf()).joinToString("") { "%02x".format(it.toInt() and 255) })
        val c=targetContext
        val p=c.getSharedPreferences(DownloadPrefs.PREFS_NAME,0)
        val oldServer=p.getString(DownloadPrefs.KEY_SMB_SERVER,null)
        val oldShare=p.getString(DownloadPrefs.KEY_SMB_SHARE,null)
        val store=c.getSharedPreferences("recordings",0)
        val oldJobs=store.getString("jobs",null)
        var job: RecordingSchedule.Job?=null
        try {
            store.edit().clear().commit()
            p.edit().putString(DownloadPrefs.KEY_SMB_SERVER,"test.invalid").putString(DownloadPrefs.KEY_SMB_SHARE,"test").commit()
            assertTrue("Grant exact alarms for the test package",RecordingSchedule.alarmAllowed(c))
            val start=System.currentTimeMillis()+4000
            job=RecordingSchedule.add(c,"Lifecycle test","Test channel","http://127.0.0.1:1/test.ts",start,start+60000)
            assertEquals("Scheduled",RecordingSchedule.jobs(c).single().state)
            try { RecordingSchedule.delete(c,job.id); fail("Scheduled recordings must not be deleted") }
            catch(expected: IllegalStateException) { }
            val missingStorage=job.copy(id="delete-failure-fixture",state="Completed",bytes=1,
                treeUri="content://missing.recording.test/tree/test",outputUri="content://missing.recording.test/document/test")
            RecordingSchedule.save(c,missingStorage)
            var deletionFailed=false
            try { RecordingSchedule.delete(c,missingStorage.id) } catch(expected: Exception) { deletionFailed=true }
            assertTrue("Unavailable storage must fail deletion",deletionFailed)
            assertTrue("Failed deletion must retain its entry",RecordingSchedule.jobs(c).any { it.id==missingStorage.id })
            c.getSharedPreferences("recordings",0).edit().putString("jobs",org.json.JSONArray(listOf(job.json())).toString()).commit()
            try {
                RecordingSchedule.add(c,"Overlap","Test channel","http://127.0.0.1:1/test.ts",start+1000,start+30000)
                fail("Overlapping schedules must be rejected")
            } catch(expected: IllegalArgumentException) { }
            c.startActivity(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            val deadline=System.currentTimeMillis()+25000
            while(System.currentTimeMillis()<deadline && RecordingSchedule.jobs(c).single().state in listOf("Scheduled","Recording")) Thread.sleep(250)
            val result=RecordingSchedule.jobs(c).single()
            assertEquals("Failed",result.state)
            assertEquals(0L,result.bytes)
            assertFalse(result.detail.isBlank())
        } finally {
            job?.let { c.getSystemService(android.app.AlarmManager::class.java).cancel(android.app.PendingIntent.getBroadcast(c,0,android.content.Intent(c,RecordingAlarm::class.java).setAction("record").setData(android.net.Uri.parse("greenstreem://record/${it.id}")),android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)) }
            p.edit().putString(DownloadPrefs.KEY_SMB_SERVER,oldServer).putString(DownloadPrefs.KEY_SMB_SHARE,oldShare).commit()
            store.edit().putString("jobs",oldJobs).commit()
        }
    }
}
