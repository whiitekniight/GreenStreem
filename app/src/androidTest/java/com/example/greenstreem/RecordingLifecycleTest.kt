package com.example.greenstreem

@Suppress("DEPRECATION")
class RecordingLifecycleTest : android.app.Instrumentation() {
    override fun onCreate(arguments: android.os.Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result=android.os.Bundle()
        try { testScheduledJobSurvivesBackgroundAndReportsStreamFailure(); result.putString("stream","PASS: schedule persistence, overlap rejection, background alarm and failure status"); finish(-1,result) }
        catch(e: Throwable) { result.putString("stream","FAIL: ${e}"); finish(0,result) }
    }
    private fun assertTrue(message: String, value: Boolean) { check(value) { message } }
    private fun assertEquals(expected: Any, actual: Any) { check(expected==actual) { "Expected $expected, got $actual" } }
    private fun assertFalse(value: Boolean) { check(!value) }
    private fun fail(message: String): Nothing = error(message)

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
