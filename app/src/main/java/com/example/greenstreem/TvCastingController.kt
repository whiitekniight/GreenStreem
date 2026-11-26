package com.example.greenstreem

import android.graphics.Color
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.framework.*
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import kotlinx.coroutines.*

/** One Cast button and one device list. No app receivers, app pairing, or protocol choices. */
internal class TvCastingController(private val activity: FragmentActivity) {
    // Keep the active TV across player-screen recreation without retaining any Activity or callbacks.
    private companion object {
        var sessionDlna: DlnaCasting.Device?=null
        var sessionGoogle=false
        var sessionLive=false
        var sessionMedia: CastSupport.Media?=null
    }
    private val scope get()=activity.lifecycleScope
    private var dlnaJob: Job?=null
    private var chooser: AlertDialog?=null
    private var controls: AlertDialog?=null
    private var multicast: android.net.wifi.WifiManager.MulticastLock?=null
    private var castContext: CastContext?=null
    private var router: MediaRouter?=null
    private var routeCallback: MediaRouter.Callback?=null
    private var googlePending: Pair<CastSupport.Media,()->Unit>?=null
    private var googleCallback: RemoteMediaClient.Callback?=null
    private var googleLoadTimeout: Job?=null
    private var lastGoogleInfo: MediaInfo?=null
    private var googleAttempt=0
    private var activeDlna: DlnaCasting.Device?
        get()=sessionDlna
        set(value) { sessionDlna=value }
    private var activeGoogle: Boolean
        get()=sessionGoogle
        set(value) { sessionGoogle=value }
    private var activeLive: Boolean
        get()=sessionLive
        set(value) { sessionLive=value }
    val isCasting get()=activeDlna!=null || activeGoogle
    private var requestedMedia: CastSupport.Media?
        get()=sessionMedia
        set(value) { sessionMedia=value }
    private var requestedStarted: ()->Unit={}
    fun showControlsOnTap() { if(isCasting) showControls() }
    private val sessionListener=object: SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession,id: String) { sendGoogle(session) }
        override fun onSessionResumed(session: CastSession,suspended: Boolean) { if(googlePending!=null) sendGoogle(session) }
        override fun onSessionStartFailed(session: CastSession,error: Int) { googlePending=null; message("Could not connect to that TV") }
        override fun onSessionResumeFailed(session: CastSession,error: Int) { googlePending=null }
        override fun onSessionEnded(session: CastSession,error: Int) {
            val wasActive=activeGoogle; activeGoogle=false; unregisterGoogleCallback(session)
            if(wasActive && activeDlna==null) CastMediaRelayService.stop(activity)
        }
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession,id: String) {}
        override fun onSessionSuspended(session: CastSession,reason: Int) {}
    }
    init { activity.lifecycle.addObserver(object: androidx.lifecycle.DefaultLifecycleObserver {
        override fun onStop(owner: androidx.lifecycle.LifecycleOwner) { stopDiscovery() }
        override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) { close() }
    }) }
    private fun message(value: String) { android.util.Log.i("GreenStreemCast",value);if(!activity.isFinishing) Toast.makeText(activity,value,Toast.LENGTH_LONG).show() }
    private fun button(root: LinearLayout,label: String,action: ()->Unit) { root.addView(Button(activity).apply {
        text=label; isAllCaps=false; setTextColor(Color.WHITE); backgroundTintList=null
        background=AppearanceTheme.buttonBackground(activity); setOnClickListener { action() }
    },LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=(12*activity.resources.displayMetrics.density).toInt() }) }
    private fun column()=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(20,12,20,12); setBackgroundColor(0xff101820.toInt()) }
    @Suppress("DEPRECATION") private fun initializeGoogle(): CastContext?=runCatching {
        if(com.google.android.gms.common.GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity)!=com.google.android.gms.common.ConnectionResult.SUCCESS) return@runCatching null
        castContext ?: CastContext.getSharedInstance(activity).also {
            castContext=it; it.sessionManager.addSessionManagerListener(sessionListener,CastSession::class.java)
        }
    }.getOrNull()
    fun open(media: CastSupport.Media?,selectDevice: Boolean=false,started: ()->Unit={}) {
        requestedStarted=started
        if(isCasting && !selectDevice && (media==null || media.url==requestedMedia?.url)) { showControls();return }
        if(!ProFeatureGate.require(activity,"Casting is available in GreenStreem Pro.")) return
        if(media==null) { message("Start playing a video first, then tap Cast"); return }
        requestedMedia=media;requestedStarted=started
        chooser?.dismiss(); stopDiscovery()
        val root=column()
        root.addView(TextView(activity).apply {
            text=if(media.relay==null) "Select a TV on your Wi-Fi." else "Select a TV on your Wi-Fi. Keep this phone/tablet connected while the recording plays."
            setTextColor(Color.LTGRAY); textSize=16f; setPadding(0,0,0,12)
        })
        val devices=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }; root.addView(devices)
        val status=TextView(activity).apply { text="Looking for TVs…"; setTextColor(Color.LTGRAY); setPadding(0,8,0,8) }; root.addView(status)
        val renderers=linkedMapOf<String,DlnaCasting.Device>()
        val context=initializeGoogle()
        val selector=context?.mergedSelector
        val mediaRouter=if(context!=null) MediaRouter.getInstance(activity).also { router=it } else null
        fun render() {
            devices.removeAllViews()
            val routes=mediaRouter?.routes.orEmpty().filter { it.isEnabled && !it.isDefault && selector!=null && it.matchesSelector(selector) }
                .distinctBy { route -> CastDevice.getFromBundle(route.extras)?.deviceId ?: route.id }
            val addresses=routes.mapNotNull { route -> runCatching { CastDevice.getFromBundle(route.extras)?.inetAddress?.hostAddress }.getOrNull() }.toSet()
            routes.forEach { route ->
                val address=CastDevice.getFromBundle(route.extras)?.inetAddress?.hostAddress
                val label=if(routes.count { it.name.equals(route.name,true) }>1 && address!=null) "${route.name} ($address)" else route.name
                button(devices,label) { chooseGoogle(route,media,started) }
            }
            val others=renderers.values.filter { device -> java.net.URL(device.endpoint).host !in addresses && routes.none { it.name.equals(device.name,true) } }
            others.forEach { device -> button(devices,device.name) { loadDlna(device,media,started) } }
            status.text=if(routes.isEmpty() && others.isEmpty()) "Looking for TVs… Make sure your TV supports casting and is on the same Wi-Fi." else ""
        }
        if(activeDlna!=null || activeGoogle) button(root,"TV controls") { showControls() }
        chooser=AlertDialog.Builder(activity,androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert).setTitle("Cast to").setView(ScrollView(activity).apply { addView(root) })
            .setNegativeButton("Cancel",null).create().also { it.setOnDismissListener { stopDiscovery() }; it.show() }
        if(selector!=null && mediaRouter!=null) {
            routeCallback=object: MediaRouter.Callback() {
                override fun onRouteAdded(router: MediaRouter,route: MediaRouter.RouteInfo) { render() }
                override fun onRouteRemoved(router: MediaRouter,route: MediaRouter.RouteInfo) { render() }
                override fun onRouteChanged(router: MediaRouter,route: MediaRouter.RouteInfo) { render() }
            }.also { mediaRouter.addCallback(selector,it,MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY) }
        }
        render()
        multicast=(activity.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager)?.createMulticastLock("Cast discovery")?.apply { setReferenceCounted(false); acquire() }
        dlnaJob=scope.launch(Dispatchers.IO) {
            val thisJob=currentCoroutineContext()[Job]!!
            runCatching { DlnaCasting.discover({ !thisJob.isActive }) { device -> activity.runOnUiThread {
                if(chooser?.isShowing==true) { renderers[device.endpoint]=device; render() }
            } } }
        }
    }
    private fun stopDiscovery() {
        routeCallback?.let { router?.removeCallback(it) }; routeCallback=null
        dlnaJob?.cancel(); dlnaJob=null; multicast?.let { if(it.isHeld) it.release() }; multicast=null
    }
    private suspend fun prepare(media: CastSupport.Media,google: Boolean=false): CastSupport.Media {
        if(media.live && media.url.substringBefore('?').endsWith(".ts",true)) {
            message("Preparing video for your TV�")
            CastMediaRelayService.beginLive(activity.applicationContext,media)
            repeat(450) {
                val url=CastMediaRelayService.liveUrl(google)
                if(url!=null)return media.copy(url=url,headers=emptyMap(),googleUrl=null,proxied=true)
                delay(100)
            }
            CastMediaRelayService.stop(activity);error("The stream took too long to prepare. Please try again.")
        }
        if(media.relay!=null) {
            CastMediaRelayService.begin(activity.applicationContext,media.relay)
            repeat(40) {
                val url=runCatching { CastMediaRelayService.url() }.getOrNull()
                if(url!=null) return media.copy(url=url,headers=emptyMap())
                delay(100)
            }
            error("Could not cast this recording. Check storage and Wi-Fi.")
        }
        require(android.net.Uri.parse(media.url).scheme in listOf("http","https")) { "This source cannot be cast" }
        return media
    }
    private suspend fun switchTarget(google: Boolean=false) {
        if(activeGoogle && !google) { activeGoogle=false; castContext?.sessionManager?.endCurrentSession(true) }
        if(google) activeDlna?.let { device -> withContext(Dispatchers.IO) { runCatching { DlnaCasting.action(device,"Stop") } }; activeDlna=null }
    }
    private fun loadDlna(device: DlnaCasting.Device,media: CastSupport.Media,started: ()->Unit) {
        message("Connecting to ${device.name}…")
        scope.launch {
            runCatching {
                switchTarget()
                activeDlna?.takeIf { it.endpoint!=device.endpoint }?.let { old -> withContext(Dispatchers.IO) { runCatching { DlnaCasting.action(old,"Stop") } } }
                val prepared=prepare(media)
                withContext(Dispatchers.IO) { DlnaCasting.load(device,prepared) }
                var playing=false
                repeat(12) { if(!playing) { delay(500); playing=withContext(Dispatchers.IO) { DlnaCasting.playing(device) } } }
                check(playing) { "The TV could not play this video format. Phone playback is still available." }
                activeDlna=device; activeGoogle=false; activeLive=media.live
                if(media.relay==null && !prepared.proxied) CastMediaRelayService.stop(activity)
                started(); chooser?.dismiss()
            }.onFailure { CastMediaRelayService.stop(activity);message(it.message ?: "The TV could not play this video") }
        }
    }
    private fun chooseGoogle(route: MediaRouter.RouteInfo,media: CastSupport.Media,started: ()->Unit) {
        googlePending=media to started
        val context=castContext ?: return
        val session=context.sessionManager.currentCastSession
        val device=CastDevice.getFromBundle(route.extras)
        if(session?.isConnected==true && session.castDevice?.deviceId==device?.deviceId) sendGoogle(session)
        else { message("Connecting to ${route.name}…"); route.select() }
    }
    private fun unregisterGoogleCallback(session: CastSession?) { googleLoadTimeout?.cancel(); googleLoadTimeout=null; googleCallback?.let { session?.remoteMediaClient?.unregisterCallback(it) }; googleCallback=null }
    private fun sendGoogle(session: CastSession) {
        val pending=googlePending ?: return
        googlePending=null
        val attempt=++googleAttempt
        scope.launch {
            runCatching {
                switchTarget(google=true)
                val media=prepare(if(pending.first.live) pending.first else pending.first.copy(url=pending.first.googleUrl ?: pending.first.url),google=true)
                val metadata=MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply { putString(MediaMetadata.KEY_TITLE,media.title) }
                val info=MediaInfo.Builder(media.url).setContentType(CastSupport.mime(media.url))
                    .setStreamType(if(media.live) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED).setMetadata(metadata).build()
                val client=session.remoteMediaClient ?: error("TV disconnected")
                lastGoogleInfo=info
                unregisterGoogleCallback(session)
                var accepted=false
                var reported=false
                fun failed() {
                    if(attempt!=googleAttempt || reported || accepted) return
                    reported=true; activeGoogle=false; googleLoadTimeout?.cancel()
                    CastMediaRelayService.stop(activity)
                    message("This TV could not play this video format. You can keep watching on your phone.")
                }
                googleCallback=object: RemoteMediaClient.Callback() {
                    override fun onStatusUpdated() {
                        if(attempt!=googleAttempt) return
                        if(!accepted && client.mediaStatus?.playerState==com.google.android.gms.cast.MediaStatus.PLAYER_STATE_PLAYING) {
                            accepted=true; googleLoadTimeout?.cancel(); activeGoogle=true; activeDlna=null; activeLive=media.live
                            if(pending.first.relay==null && !media.proxied) CastMediaRelayService.stop(activity)
                            pending.second(); chooser?.dismiss()
                        }
                        if(!accepted && client.mediaStatus?.playerState==com.google.android.gms.cast.MediaStatus.PLAYER_STATE_IDLE &&
                            client.mediaStatus?.idleReason==com.google.android.gms.cast.MediaStatus.IDLE_REASON_ERROR) failed()
                    }
                    override fun onMediaError(error: com.google.android.gms.cast.MediaError) { failed() }
                }.also { client.registerCallback(it) }
                googleLoadTimeout=scope.launch { delay(25_000); if(!accepted) failed() }
                val load=MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true)
                if(!media.live) load.setCurrentTime(media.position)
                client.load(load.build()).setResultCallback {
                    if(!it.status.isSuccess) failed()
                }
            }.onFailure { CastMediaRelayService.stop(activity);message(it.message ?: "Could not cast this video") }
        }
    }
    private fun showControls() {
        if(activeGoogle)initializeGoogle()
        controls?.dismiss()
        val root=column()
        button(root,"Play / pause") { command("toggle") }
        if(!activeLive) {
            val row=LinearLayout(activity).apply { orientation=LinearLayout.HORIZONTAL }
            listOf("−30 seconds" to -30_000L,"+30 seconds" to 30_000L).forEach { (label,delta) -> row.addView(Button(activity).apply {
                text=label; isAllCaps=false; setTextColor(Color.WHITE); backgroundTintList=null; background=AppearanceTheme.buttonBackground(activity); setOnClickListener { command("seek",delta) }
            },LinearLayout.LayoutParams(0,-2,1f).apply { rightMargin=(8*activity.resources.displayMetrics.density).toInt() }) }
            root.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=(12*activity.resources.displayMetrics.density).toInt() })
        }
        button(root,"Stop casting") { command("stop") }
        button(root,"Change TV") { controls?.dismiss();open(requestedMedia,selectDevice=true,started=requestedStarted) }
        controls=AlertDialog.Builder(activity,androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert).setTitle(activeDlna?.name ?: castContext?.sessionManager?.currentCastSession?.castDevice?.friendlyName ?: "TV controls")
            .setView(ScrollView(activity).apply { addView(root) }).setNegativeButton("Close",null).show()
    }
    private fun command(action: String,delta: Long=0) {
        scope.launch {
            runCatching {
                val dlna=activeDlna
                when {
                    activeGoogle -> {
                        val client=castContext?.sessionManager?.currentCastSession?.remoteMediaClient ?: error("TV disconnected")
                        when(action) {
                            "toggle" -> if(client.mediaStatus?.playerState==com.google.android.gms.cast.MediaStatus.PLAYER_STATE_PLAYING) client.pause()
                                else if(client.mediaStatus?.playerState==com.google.android.gms.cast.MediaStatus.PLAYER_STATE_IDLE && lastGoogleInfo!=null)
                                    client.load(MediaLoadRequestData.Builder().setMediaInfo(lastGoogleInfo!!).setAutoplay(true).setCurrentTime(0).build()) else client.play()
                            "play" -> client.play(); "pause" -> client.pause()
                            "seek" -> client.seek(MediaSeekOptions.Builder().setPosition((client.approximateStreamPosition+delta).coerceAtLeast(0)).build())
                            "stop" -> castContext?.sessionManager?.endCurrentSession(true)
                        }
                    }
                    dlna!=null -> withContext(Dispatchers.IO) {
                        if(action=="seek") DlnaCasting.seek(dlna,DlnaCasting.position(dlna)+delta)
                        else if(action=="toggle") DlnaCasting.action(dlna,if(DlnaCasting.playing(dlna)) "Pause" else "Play")
                        else DlnaCasting.action(dlna,when(action) { "play" -> "Play"; "pause" -> "Pause"; else -> "Stop" })
                    }
                    else -> error("No TV connected")
                }
                if(action=="stop") { activeDlna=null; activeGoogle=false; controls?.dismiss(); CastMediaRelayService.stop(activity) }
            }.onFailure { message(it.message ?: "TV command failed") }
        }
    }
    fun wrapPlayer(view: androidx.media3.ui.PlayerView,media: ()->CastSupport.Media?,started: ()->Unit): android.view.View {
        val density=activity.resources.displayMetrics.density
        fun dp(value: Int)=(value*density).toInt()
        val cast=ImageButton(activity).apply {
            contentDescription="Cast to TV"; setImageResource(R.drawable.ic_cast_24)
            setPadding(dp(12),dp(12),dp(12),dp(12)); backgroundTintList=null
            background=AppearanceTheme.buttonBackground(activity); setOnClickListener { open(media(),started=started) }
        }
        val handler=android.os.Handler(android.os.Looper.getMainLooper())
        val hide=Runnable { cast.animate().alpha(0f).setDuration(250).withEndAction {
            if(cast.alpha==0f) cast.visibility=android.view.View.INVISIBLE
        }.start() }
        fun reveal() {
            cast.animate().cancel(); cast.alpha=1f; cast.visibility=android.view.View.VISIBLE
            handler.removeCallbacks(hide); handler.postDelayed(hide,10_000)
        }
        return object: FrameLayout(activity) {
            override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
                if(event.actionMasked==android.view.MotionEvent.ACTION_UP) {
                    reveal()
                    if(isCasting) {
                        val cancel=android.view.MotionEvent.obtain(event).apply { action=android.view.MotionEvent.ACTION_CANCEL }
                        try { super.dispatchTouchEvent(cancel) } finally { cancel.recycle() }
                        showControlsOnTap();return true
                    }
                }
                return super.dispatchTouchEvent(event)
            }
            override fun onAttachedToWindow() { super.onAttachedToWindow(); reveal() }
            override fun onDetachedFromWindow() { handler.removeCallbacks(hide); cast.animate().cancel(); super.onDetachedFromWindow() }
        }.apply {
            addView(view,FrameLayout.LayoutParams(-1,-1))
            addView(cast,FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP or Gravity.END).apply { topMargin=dp(16); rightMargin=dp(24) })
            CastSupport.safeButtonPlacement(cast)
        }
    }
    fun close() {
        stopDiscovery(); chooser?.dismiss(); controls?.dismiss()
        unregisterGoogleCallback(castContext?.sessionManager?.currentCastSession)
        castContext?.sessionManager?.removeSessionManagerListener(sessionListener,CastSession::class.java)
    }
}
