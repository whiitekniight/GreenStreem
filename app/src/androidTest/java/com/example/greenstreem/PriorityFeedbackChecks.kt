package com.example.greenstreem

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking

/** Recycles actual group rows and navigates a short phone-sized menu viewport. */
internal class PriorityFeedbackChecks(private val i: Instrumentation) {
    private val c get()=i.targetContext
    private fun <T> ui(block: ()->T): T {
        var result: T?=null;i.runOnMainSync { result=block() }
        @Suppress("UNCHECKED_CAST") return result as T
    }
    private fun waitFor(message: String,block: ()->Boolean) {
        val until=SystemClock.uptimeMillis()+15000
        while(SystemClock.uptimeMillis()<until) { if(block())return;Thread.sleep(80) }
        error(message)
    }
    private fun bounds(view: View): Rect=ui {
        val r=Rect();check(view.getGlobalVisibleRect(r));val origin=IntArray(2);view.rootView.getLocationOnScreen(origin);r.offset(origin[0],origin[1]);r
    }
    private fun swipe(rect: Rect) {
        val t=SystemClock.uptimeMillis()
        for(step in 0..16) {
            val e=MotionEvent.obtain(t,SystemClock.uptimeMillis(),when(step){0->0;16->1;else->2},rect.exactCenterX(),rect.top+rect.height()*(.85f-.7f*step/16),0)
            e.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
            try{i.sendPointerSync(e)}finally{e.recycle()};Thread.sleep(20)
        }
        Thread.sleep(250)
    }
    fun run() {
        check(c.packageName.endsWith(".feedbackcheck"))
        val server=object: NanoHTTPD("127.0.0.1",0) {
            override fun serve(s: IHTTPSession): Response=newFixedLengthResponse(Response.Status.OK,"audio/x-mpegurl",
                "#EXTM3U\n"+(1..100).joinToString("\n") { "#EXTINF:-1 group-title=\"Feedback group $it\",Test $it\nhttp://127.0.0.1:$listeningPort/$it.ts" })
        }
        server.start(5000,true)
        var groups: ManageGroupsActivity?=null
        var main: MainActivity?=null
        var settings: android.app.Activity?=null
        try {
            val prefs=c.getSharedPreferences("iptv_prefs",0)
            prefs.edit().clear().putString("ui_mode","mobile").putBoolean("pro_unlocked",true).putBoolean("updater_auto_check",false)
                .putBoolean("autoplay_last_channel",false).commit()
            val url="http://127.0.0.1:${server.listeningPort}/playlist.m3u"
            PlaylistProfilesManager.upsertProfileAndActivate(c,PlaylistProfilesManager.createProfile("Priority feedback fixture",url,"",""))
            prefs.edit().putString("playlist_type","m3u").putString("m3u_url",url).putBoolean("has_playlist",true).putBoolean("playlist_enabled",true).commit()
            runBlocking { AppDatabase.getDatabase(c).groupDao().clearAll() }
            fun openGroups(): ManageGroupsActivity=i.startActivitySync(Intent(c,ManageGroupsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ManageGroupsActivity
            groups=openGroups()
            var rows=groups.findViewById<RecyclerView>(R.id.rvGroups)
            waitFor("Test groups did not load") { ui { rows.adapter?.itemCount==100 && rows.findViewHolderForAdapterPosition(0)!=null } }
            fun checked(position: Int)=ui { rows.findViewHolderForAdapterPosition(position)?.itemView?.findViewById<SwitchCompat>(R.id.swGroupVisible)?.isChecked }
            ui { for(pos in 0..2) rows.findViewHolderForAdapterPosition(pos)!!.itemView.findViewById<SwitchCompat>(R.id.swGroupVisible).isChecked=false }
            waitFor("Group changes were not saved") { runBlocking { (1..3).all { AppDatabase.getDatabase(c).groupDao().isGroupHidden("Feedback group $it") } } }
            ui { rows.scrollToPosition(99) };waitFor("Group list did not scroll") { ui { rows.findViewHolderForAdapterPosition(99)!=null } }
            ui { rows.recycledViewPool.clear();rows.scrollToPosition(0) }
            waitFor("Group list did not return") { checked(0)!=null }
            check((0..2).all { checked(it)==false }) { "Hidden switches turned on after rows were recycled" }
            ui { groups!!.finish() };groups=openGroups();rows=groups.findViewById(R.id.rvGroups)
            waitFor("Groups did not reload") { checked(0)!=null }
            check((0..2).all { checked(it)==false }) { "Hidden switches did not survive closing and reopening" }
            ui { groups!!.findViewById<View>(R.id.btnUnhideAll).performClick() }
            waitFor("Show all did not restore switches") { checked(0)==true }
            ui { groups!!.finish() };groups=null
            main=i.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val activity=main
            Thread.sleep(1500)
            val nav=activity.findViewById<NestedScrollView>(R.id.navRail)
            val settingsButton=activity.findViewById<View>(R.id.navSettings)
            ui {
                MainActivity::class.java.getDeclaredMethod("updateUiState",MainActivity.UiState::class.java).apply { isAccessible=true }.invoke(activity,MainActivity.UiState.NAV_RAIL)
                nav.layoutParams=nav.layoutParams.apply { height=(220*c.resources.displayMetrics.density).toInt() }
                nav.scrollTo(0,0)
            }
            waitFor("Phone menu did not become scrollable") { ui { nav.canScrollVertically(1) } }
            repeat(3) { swipe(bounds(nav)) }
            waitFor("Settings still clipped after touch scrolling") { ui { val r=Rect();settingsButton.getGlobalVisibleRect(r) && r.height()==settingsButton.height } }
            val monitor=i.addMonitor(SettingsActivity::class.java.name,null,false)
            if(c.packageManager.hasSystemFeature("android.software.leanback")) {
                i.setInTouchMode(false)
                ui { nav.scrollTo(0,0);activity.findViewById<View>(R.id.navSearch).requestFocus() }
                waitFor("Search did not gain remote focus") { ui { activity.findViewById<View>(R.id.navSearch).hasFocus() } }
                for(id in listOf(R.id.navTv,R.id.navMovies,R.id.navSeries,R.id.navRecordings,R.id.navSettings)) {
                    i.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
                    waitFor("Remote focus did not reach ${c.resources.getResourceEntryName(id)}") { ui { activity.findViewById<View>(id).hasFocus() } }
                }
                i.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
            } else {
                val rect=bounds(settingsButton);val t=SystemClock.uptimeMillis()
                for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                    val event=MotionEvent.obtain(t,SystemClock.uptimeMillis(),action,rect.exactCenterX(),rect.exactCenterY(),0)
                    event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                    try { i.sendPointerSync(event) } finally { event.recycle() }
                }
            }
            settings=i.waitForMonitorWithTimeout(monitor,5000);i.removeMonitor(monitor)
            check(settings!=null) { "Settings did not open" }
            ui { check(settings!!.window.decorView.findViewWithTag<View>("screen_back").performClick()) }
            waitFor("Visible Back button did not return from Settings") { settings!!.isFinishing }
            android.util.Log.i("PriorityFeedbackChecks","PASS group toggle recycling/reopen/show-all; short-screen touch menu; remote Settings navigation")
        } finally {
            ui { settings?.finish();main?.finish();groups?.finish() };server.stop()
        }
    }
}
