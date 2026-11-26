package com.example.greenstreem

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Isolated UI/network checks; never replace a user's playlist or settings. */
internal class GuideFeedbackChecks(private val i: Instrumentation, private val nativeCastTarget: String? = null) {
    private val c get() = i.targetContext
    private fun waitFor(message: String, timeout: Long = 15000, check: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            if (check()) return
            Thread.sleep(100)
        }
        error(message)
    }
    private fun <T> ui(block: () -> T): T {
        var result: T? = null
        i.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST") return result as T
    }
    private fun text(view: View, value: String): View? {
        if (view is TextView && view.text.toString() == value) return view
        if (view is ViewGroup) for (n in 0 until view.childCount) text(view.getChildAt(n), value)?.let { return it }
        return null
    }
    private fun screenRect(view: View): Rect = ui {
        val rect=Rect();check(view.getGlobalVisibleRect(rect))
        val origin=IntArray(2);view.rootView.getLocationOnScreen(origin)
        rect.offset(origin[0],origin[1]);rect
    }
    private fun tap(view: View) {
        val r = screenRect(view)
        val t = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val e = MotionEvent.obtain(t, SystemClock.uptimeMillis(), action, r.exactCenterX(), r.exactCenterY(), 0)
            e.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            try { i.sendPointerSync(e) } finally { e.recycle() }
        }
    }
    private fun swipe(r: Rect, horizontal: Boolean = false) {
        val x1 = if(horizontal) r.left+r.width()*0.85f else r.exactCenterX()
        val x2 = if(horizontal) r.left+r.width()*0.35f else x1
        val y1 = if(horizontal) r.exactCenterY() else r.top+r.height()*0.80f
        val y2 = if(horizontal) y1 else r.top+r.height()*0.20f
        val t = SystemClock.uptimeMillis()
        for (step in 0..16) {
            val e = MotionEvent.obtain(t, SystemClock.uptimeMillis(), when(step) {
                0 -> MotionEvent.ACTION_DOWN; 16 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE
            }, x1 + (x2-x1)*step/16, y1+(y2-y1)*step/16, 0)
            e.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            try { i.sendPointerSync(e) } finally { e.recycle() }
            Thread.sleep(18)
        }
        Thread.sleep(180)
    }

    fun run() {
        check(c.packageName.endsWith(".feedbackcheck")) { "Run only with feedback-check.init.gradle isolated package" }
        val loads = CopyOnWriteArrayList<String>()
        val titles = CopyOnWriteArrayList<String>()
        val data = i.context.assets.open("feedback-cast.ts").use { it.readBytes() }
        val video = i.context.assets.open("feedback-vod.mp4").use { it.readBytes() }
        val server = object : NanoHTTPD("127.0.0.1", 0) {
            override fun serve(s: IHTTPSession): Response {
                if(s.uri == "/soap") {
                    val action = s.headers["soapaction"].orEmpty().trim('"').substringAfterLast('#')
                    // Consume every SOAP body, including Stop/Play, before reusing HTTP connections.
                    val parts = hashMapOf<String,String>(); s.parseBody(parts)
                    if(action == "SetAVTransportURI") {
                        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                            .parse(ByteArrayInputStream(parts["postData"].orEmpty().toByteArray()))
                        loads.add(doc.getElementsByTagName("CurrentURI").item(0).textContent)
                        titles.add(doc.getElementsByTagName("CurrentURIMetaData").item(0).textContent)
                    }
                    return newFixedLengthResponse(Response.Status.OK, "text/xml",
                        "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body>"+
                        "<u:${action}Response xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">"+
                        (if(action=="GetTransportInfo") "<CurrentTransportState>PLAYING</CurrentTransportState>" else "")+
                        "</u:${action}Response></s:Body></s:Envelope>")
                }
                if(s.uri=="/movie.mp4") {
                    val range=RecordingByteRange.parse(s.headers["range"],video.size.toLong())
                    return newFixedLengthResponse(if(s.headers.containsKey("range")) Response.Status.PARTIAL_CONTENT else Response.Status.OK,"video/mp4",
                        ByteArrayInputStream(video,range.start.toInt(),range.length.toInt()),range.length).apply {
                        addHeader("Accept-Ranges","bytes")
                        if(s.headers.containsKey("range")) addHeader("Content-Range","bytes ${range.start}-${range.start+range.length-1}/${video.size}")
                    }
                }
                if(s.uri.endsWith(".ts")) return newChunkedResponse(Response.Status.OK,"video/mp2t",object: java.io.InputStream() {
                    var pos = 0
                    override fun read(): Int = (data[(pos++).let { it % data.size }].toInt() and 255)
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        Thread.sleep(20)
                        val size = minOf(len, 188 * 8)
                        repeat(size) { b[off+it] = read().toByte() }; return size
                    }
                })
                val playlist = "#EXTM3U\n"+(1..120).joinToString("\n") {
                    "#EXTINF:-1 group-title=\"Feedback test channels\",Feedback channel $it\nhttp://127.0.0.1:$listeningPort/$it.ts"
                }
                return newFixedLengthResponse(Response.Status.OK,"audio/x-mpegurl",playlist)
            }
        }
        server.start(5000, true)
        var activity: MainActivity? = null
        try {
            val prefs = c.getSharedPreferences("iptv_prefs", 0)
            prefs.edit().clear().putString("ui_mode","mobile").putBoolean("pro_unlocked",true)
                .putBoolean("updater_auto_check",false).commit()
            val url = "http://127.0.0.1:${server.listeningPort}/playlist.m3u"
            PlaylistProfilesManager.upsertProfileAndActivate(c, PlaylistProfilesManager.createProfile("Feedback fixture",url,"",""))
            prefs.edit().putString("playlist_type","m3u").putString("m3u_url",url)
                .putBoolean("has_playlist",true).putBoolean("playlist_enabled",true).commit()
            val main = i.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            activity = main
            waitFor("Test category did not appear") { ui { text(main.window.decorView,"Feedback test channels") != null } }
            tap(ui { text(main.window.decorView,"Feedback test channels")!! })
            val rows = main.findViewById<RecyclerView>(R.id.rvEpg)
            waitFor("Guide rows did not load") { ui { (rows.adapter?.itemCount ?: 0) >= 120 } }
            val header = main.findViewById<View>(R.id.guideHeader)
            var stableHeader=screenRect(header)
            var stableSince=SystemClock.uptimeMillis()
            waitFor("Tablet guide layout did not settle") {
                val current=screenRect(header)
                if(current!=stableHeader) { stableHeader=current;stableSince=SystemClock.uptimeMillis() }
                SystemClock.uptimeMillis()-stableSince>=1200
            }
            val before = ui { Rect().also { check(header.getGlobalVisibleRect(it)) } }
            val grid = screenRect(rows)
            repeat(5) { swipe(grid) }
            check(ui { (rows.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() > 0 }) { "Touch swipes did not move channels" }
            val after = screenRect(header)
            val afterLocal=ui { Rect().also { check(header.getGlobalVisibleRect(it)) } }
            check(before == afterLocal) { "Guide header moved within its window while channels scrolled: $before -> $afterLocal" }
            val clock = main.findViewById<TextView>(R.id.tvGuideClock)
            check(ui { clock.isShown && clock.text.isNotBlank() }) { "Guide clock disappeared" }
            val bitmap = i.uiAutomation.takeScreenshot() ?: error("Could not capture guide")
            val clockRect=screenRect(clock)
            var clockPixels = 0; var timePixels = 0
            for(y in after.top.coerceAtLeast(0) until after.bottom.coerceAtMost(bitmap.height))
                for(x in after.left.coerceAtLeast(0) until after.right.coerceAtMost(bitmap.width)) {
                    val color=bitmap.getPixel(x,y);val r=android.graphics.Color.red(color);val g=android.graphics.Color.green(color);val b=android.graphics.Color.blue(color)
                    if(clockRect.contains(x,y) && maxOf(r,g,b)>160) clockPixels++
                    if(x>=clockRect.right && r>210 && g>210 && b>210) timePixels++
                }
            java.io.FileOutputStream(File(c.filesDir,"guide-feedback-after.png")).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            check(clockPixels>10 && timePixels>10) { "Clock/time pixels covered after tablet scrolling: clock=$clockPixels times=$timePixels rect=$after clockRect=$clockRect image=${bitmap.width}x${bitmap.height}" }
            bitmap.recycle()
            val cursor = main.findViewById<View>(R.id.nowTimeLine)
            val cursorBefore = screenRect(cursor)
            swipe(grid,true)
            val cursorAfter = screenRect(cursor)
            check(cursorBefore.left==cursorAfter.left) { "Fixed guide cursor shifted" }

            waitFor("Previous guide update did not finish") { !prefs.getBoolean("epg_update_in_progress",false) }
            val refreshedBefore=prefs.getLong("epg_last_refreshed_at",0)
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU)
            waitFor("Convenient refresh action missing") { i.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Update EPG now")?.isNotEmpty()==true }
            val refresh=i.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("Update EPG now").first()
            val refreshRect=Rect();refresh.getBoundsInScreen(refreshRect)
            val refreshDown=SystemClock.uptimeMillis()
            for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                val event=MotionEvent.obtain(refreshDown,SystemClock.uptimeMillis(),action,refreshRect.exactCenterX(),refreshRect.exactCenterY(),0)
                event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                try { i.sendPointerSync(event) } finally { event.recycle() }
            }
            waitFor("Quick-panel guide refresh did not complete",30000) { prefs.getLong("epg_last_refreshed_at",0)>refreshedBefore }


            val castField = TvCastingController::class.java.getDeclaredField("sessionDlna").apply { isAccessible=true }
            val adapter=rows.adapter as EpgRowAdapter
            if(nativeCastTarget!=null) {
                ui { main.playChannel(adapter.getChannelAt(0)!!,openInGuide=true) }
                ui { main.findViewById<View>(R.id.btnMovieCast).performClick() }
                var targetRoute: androidx.mediarouter.media.MediaRouter.RouteInfo?=null
                waitFor("Native Shield cast target was not discovered",30000) {
                    ui {
                        val context=com.google.android.gms.cast.framework.CastContext.getSharedInstance(main)
                        val router=androidx.mediarouter.media.MediaRouter.getInstance(main)
                        targetRoute=router.routes.firstOrNull { route ->
                            route.isEnabled && !route.isDefault && route.matchesSelector(context.mergedSelector ?: androidx.mediarouter.media.MediaRouteSelector.EMPTY) &&
                                com.google.android.gms.cast.CastDevice.getFromBundle(route.extras)?.ipAddress?.hostAddress==nativeCastTarget
                        }
                        targetRoute!=null
                    }
                }
                // QA selects by the explicitly supplied IP; duplicate Shield names must never target another TV.
                ui {
                    val controller=MainActivity::class.java.getDeclaredMethod("getTvCasting").apply { isAccessible=true }.invoke(main)
                    val media=MainActivity::class.java.getDeclaredMethod("currentCastingMedia").apply { isAccessible=true }.invoke(main) as CastSupport.Media
                    val local=MainActivity::class.java.getDeclaredField("player").apply { isAccessible=true }.get(main) as androidx.media3.exoplayer.ExoPlayer
                    val started: ()->Unit={ local.pause() }
                    TvCastingController::class.java.getDeclaredMethod("chooseGoogle",androidx.mediarouter.media.MediaRouter.RouteInfo::class.java,
                        CastSupport.Media::class.java,kotlin.jvm.functions.Function0::class.java).apply { isAccessible=true }
                        .invoke(controller,targetRoute,media,started)
                }
                fun playingTitle(number: Int)=ui {
                    val session=com.google.android.gms.cast.framework.CastContext.getSharedInstance(main).sessionManager.currentCastSession
                    val status=session?.remoteMediaClient?.mediaStatus
                    session?.isConnected==true && status?.playerState==com.google.android.gms.cast.MediaStatus.PLAYER_STATE_PLAYING &&
                        status.mediaInfo?.metadata?.getString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE)=="Feedback channel $number"
                }
                waitFor("Native Shield did not play first channel",60000) { playingTitle(1) }
                val sessionId=ui { com.google.android.gms.cast.framework.CastContext.getSharedInstance(main).sessionManager.currentCastSession?.sessionId }
                ui { main.playChannel(adapter.getChannelAt(1)!!,openInGuide=true) }
                waitFor("Native Shield did not retune to channel 2",60000) { playingTitle(2) }
                ui { main.playChannel(adapter.getChannelAt(2)!!,openInGuide=true);main.playChannel(adapter.getChannelAt(3)!!,openInGuide=true) }
                waitFor("Native Shield did not keep latest channel 4",60000) { playingTitle(4) }
                check(ui { com.google.android.gms.cast.framework.CastContext.getSharedInstance(main).sessionManager.currentCastSession?.sessionId }==sessionId) { "Retune replaced native TV session" }
                val mediaSessionId=ui { com.google.android.gms.cast.framework.CastContext.getSharedInstance(main).sessionManager.currentCastSession?.remoteMediaClient?.mediaStatus?.mediaInfo?.contentId }
                ui { main.playChannel(adapter.getChannelAt(3)!!,openInGuide=false) }
                Thread.sleep(800)
                check(ui { com.google.android.gms.cast.framework.CastContext.getSharedInstance(main).sessionManager.currentCastSession?.remoteMediaClient?.mediaStatus?.mediaInfo?.contentId }==mediaSessionId) { "Reselecting the same channel unnecessarily restarted casting" }
                android.util.Log.i("GuideFeedbackChecks","PASS native Shield PLAYING titles 1 -> 2 -> 4; same Cast session")
                ui { com.google.android.gms.cast.framework.CastContext.getSharedInstance(main).sessionManager.endCurrentSession(true) }
            } else {
            ui { castField.set(null,DlnaCasting.Device("Fixture TV","http://127.0.0.1:${server.listeningPort}/soap","urn:schemas-upnp-org:service:AVTransport:1")) }
            ui { main.playChannel(adapter.getChannelAt(0)!!,openInGuide=true) }
            waitFor("Connected TV did not load first selected channel",45000) { titles.lastOrNull()?.contains("Feedback channel 1")==true }
            ui { main.playChannel(adapter.getChannelAt(1)!!,openInGuide=true) }
            waitFor("Connected TV did not retune automatically",45000) { titles.lastOrNull()?.contains("Feedback channel 2")==true }
            ui { main.playChannel(adapter.getChannelAt(2)!!,openInGuide=true);main.playChannel(adapter.getChannelAt(3)!!,openInGuide=true) }
            waitFor("Rapid selections did not keep newest channel",45000) { titles.lastOrNull()?.contains("Feedback channel 4")==true }
            Thread.sleep(1000)
            check(titles.last().contains("Feedback channel 4")) { "Older cast load overwrote newest selection" }
            check(loads.distinct().size>=3) { "Retune reused an old relay capability URL" }
            waitFor("Latest cast load was not accepted",30000) { ui { TvCastingController::class.java.getDeclaredField("sessionLoadedUrl").apply { isAccessible=true }.get(null)==adapter.getChannelAt(3)?.streamUrl } }
            val loadCount=loads.size
            ui { main.playChannel(adapter.getChannelAt(3)!!,openInGuide=false) }
            Thread.sleep(800)
            check(loads.size==loadCount) { "Reselecting the same channel unnecessarily restarted casting" }
            check(i.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Looking for TVs")?.isEmpty()!=false) { "Retune opened TV discovery" }
            ui { castField.set(null,null); CastMediaRelayService.stop(c) }

            }

            prefs.edit().putString("ui_mode","tv").commit()
            ui {
                MainActivity::class.java.getDeclaredMethod("handleExternalPlaybackIntent",Intent::class.java).apply { isAccessible=true }
                    .invoke(main,Intent().putExtra("play_url","http://127.0.0.1:${server.listeningPort}/movie.mp4")
                        .putExtra("media_title","Feedback movie").putExtra("resume_key","feedback_movie_"+SystemClock.uptimeMillis()))
            }
            val player=ui { MainActivity::class.java.getDeclaredField("player").apply { isAccessible=true }.get(main) as androidx.media3.exoplayer.ExoPlayer }
            waitFor("VOD fixture did not become ready",30000) { ui { player.playbackState==androidx.media3.common.Player.STATE_READY && player.duration>150000 } }
            ui { player.pause();player.seekTo(0) }
            i.setInTouchMode(false)
            waitFor("VOD did not settle at the starting position") { ui { player.currentPosition<1000 && player.playbackState==androidx.media3.common.Player.STATE_READY } }
            val forwardPositions=mutableListOf<Long>()
            repeat(3) { index ->
                i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
                val target=(index+1)*30000L
                waitFor("Movie forward skip did not reach $target",5000) { ui { player.currentPosition in (target-1000)..(target+1000) } }
                forwardPositions.add(ui { player.currentPosition })
            }
            repeat(2) { index ->
                i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT)
                val target=60000L-index*30000L
                waitFor("Movie rewind skip did not reach $target",5000) { ui { player.currentPosition in (target-1000)..(target+1000) } }
            }
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            waitFor("Expanded movie controls missing") { ui { main.findViewById<View>(R.id.movieControlsButtons).isShown } }
            repeat(3) { i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT) }
            waitFor("Right did not reach Subtitles in movie controls") { ui { main.currentFocus?.id==R.id.btnMovieSubtitles } }
            check(ui { player.currentPosition in 29000L..31000L }) { "Control-row navigation incorrectly sought the movie" }
            android.util.Log.i("GuideFeedbackChecks","PASS movie Right x3=90s, Left x2=30s; expanded Right reaches Subtitles without seeking")
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            check(!main.isFinishing) { "Short Back unexpectedly exited the app" }
            val t=SystemClock.uptimeMillis()
            i.sendKeySync(KeyEvent(t,t,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK,0))
            Thread.sleep(android.view.ViewConfiguration.getLongPressTimeout()+250L)
            i.uiAutomation.injectInputEvent(KeyEvent(t,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK,0),true)
            waitFor("Long Back did not exit to Home") { main.isFinishing }
            val home=i.uiAutomation.executeShellCommand("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME").use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
            }.lineSequence().filter { it.contains('/') }.lastOrNull()?.substringBefore('/')
            check(home!=null) { "Could not resolve Home launcher for the test" }
            waitFor("TV Home not visible after long Back") { i.uiAutomation.rootInActiveWindow?.packageName?.toString()==home }
            android.util.Log.i("GuideFeedbackChecks","PASS tablet swipes/header pixels; fixed cursor; refresh menu; cast retune/rapid selection; short Back and long Back Home")
        } finally {
            ui {
                if(nativeCastTarget!=null && activity!=null) runCatching {
                    val manager=com.google.android.gms.cast.framework.CastContext.getSharedInstance(activity!!).sessionManager
                    if(manager.currentCastSession?.castDevice?.ipAddress?.hostAddress==nativeCastTarget) manager.endCurrentSession(true)
                }
                TvCastingController::class.java.getDeclaredField("sessionDlna").apply { isAccessible=true }.set(null,null)
                TvCastingController::class.java.getDeclaredField("sessionGoogle").apply { isAccessible=true }.setBoolean(null,false)
                CastMediaRelayService.stop(c); activity?.finish()
            }
            server.stop()
        }
    }
}
