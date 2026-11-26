package com.example.greenstreem

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SharedRecordingsActivity: AppCompatActivity() {
    private var player: ExoPlayer?=null
    private var discovery: RecordingPeerDiscovery?=null
    private var generation=0
    private var screen="home"
    private var currentPeer: RecordingPeerDiscovery.Peer?=null
    private val peers=linkedMapOf<String,RecordingPeerDiscovery.Peer>()
    private val http=OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS).readTimeout(25,TimeUnit.SECONDS).followRedirects(false).build()
    private val prefs by lazy { getSharedPreferences(RecordingShareService.PREFS,0) }
    private lateinit var root: LinearLayout
    private fun page(title: String, message: String, scroll: Boolean=false) {
        generation++
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,20,24,20); setBackgroundColor(0xff101820.toInt()) }
        root.addView(TextView(this).apply { text=title; textSize=25f; setTextColor(Color.WHITE) })
        root.addView(TextView(this).apply { text=message; textSize=16f; setTextColor(Color.LTGRAY); setPadding(0,8,0,12) })
        if(scroll) setContentView(ScrollView(this).apply { isFillViewport=true; setBackgroundColor(0xff101820.toInt()); addView(root) }) else setContentView(root)
    }
    private fun button(label: String, action: ()->Unit) {
        root.addView(Button(this).apply {
            text=label; isAllCaps=false; textSize=16f; setTextColor(Color.WHITE); backgroundTintList=null
            background=AppearanceTheme.buttonBackground(this@SharedRecordingsActivity); setOnClickListener { action() }
        },LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin=8 })
    }
    private fun message(text: String)=Toast.makeText(this,text,Toast.LENGTH_LONG).show()
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if(!ProFeatureGate.require(this,"Shared recordings are available in GreenStreem Pro.")) return
        onBackPressedDispatcher.addCallback(this,object: androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if(player!=null) { player?.release(); player=null; showLibrary(currentPeer) }
                else if(screen!="home") home() else finish()
            }
        })
        home()
    }
    private fun home() {
        discovery?.stop(); discovery=null; screen="home"; currentPeer=null
        page("Shared recordings","Watch files in your recording folder or on another GreenStreem device on the same home network.",true)
        button("Open my recording folder (USB / NAS)") { showLibrary(null) }
        button("Find another stick or device") { findDevices() }
        button("Share this device’s recording folder") { sharing() }
        button("Back to Recordings") { finish() }
    }
    private fun sharing() {
        screen="sharing"
        val addresses=runCatching { NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .filter { it is java.net.Inet4Address && it.isSiteLocalAddress }.joinToString("\n") { "${it.hostAddress}:${RecordingShareService.port}" } }.getOrDefault("")
        page("Share this recording folder",if(RecordingShareService.running)
            "${RecordingShareService.status}\n$addresses\nKeep this device on and its recording storage connected. Paired devices can play files; they cannot delete them."
            else "Share only your selected recording folder with paired devices on your home network. Keep this device powered on while others watch.",true)
        if(RecordingShareService.running) {
            val code=RecordingShareService.currentCode()
            if(code.isNotBlank()) root.addView(TextView(this).apply { text="Pairing code: $code (valid for 5 minutes)"; textSize=24f; setTextColor(0xffffb438.toInt()); setPadding(0,8,0,12) })
            button("Show a new pairing code") { RecordingShareService.openPairing(); sharing() }
            button("Stop sharing") { RecordingShareService.stop(this); root.postDelayed({ if(!isFinishing) sharing() },300) }
            button("Forget all paired devices") {
                AlertDialog.Builder(this).setTitle("Disconnect paired devices?").setMessage("They will need a new code to reconnect. Existing playback may continue until its next request.")
                    .setNegativeButton("Keep",null).setPositiveButton("Forget devices") { _,_ -> prefs.edit().remove("tokens").apply(); message("Paired devices forgotten") }.show()
            }
        } else button("Turn on folder sharing") {
            val requestGeneration=generation
            lifecycleScope.launch {
                val result=withContext(Dispatchers.IO) { runCatching { SharedRecordingLibrary.list(this@SharedRecordingsActivity) } }
                if(requestGeneration!=generation) return@launch
                result.onSuccess {
                    prefs.edit().putBoolean("enabled",true).apply()
                    runCatching { RecordingShareService.start(this@SharedRecordingsActivity); RecordingShareService.openPairing() }
                        .onFailure { prefs.edit().putBoolean("enabled",false).apply(); message("Could not start sharing") }
                    root.postDelayed({ if(!isFinishing && screen=="sharing") sharing() },700)
                }.onFailure { message(it.message ?: "Choose a recording folder first") }
            }
        }
        button("Back") { home() }
    }
    private fun saved()=runCatching { JSONObject(prefs.getString("peers","{}").orEmpty()) }.getOrDefault(JSONObject())
    private fun remember(peer: RecordingPeerDiscovery.Peer,token: String?=null) {
        val all=saved(); val entry=all.optJSONObject(peer.id) ?: JSONObject()
        entry.put("name",peer.name).put("address",peer.address).put("port",peer.port)
        if(token!=null) entry.put("token",token)
        all.put(peer.id,entry); prefs.edit().putString("peers",all.toString()).apply()
    }
    private fun token(peer: RecordingPeerDiscovery.Peer)=saved().optJSONObject(peer.id)?.optString("token").orEmpty()
    private fun findDevices() {
        screen="discovery"; peers.clear()
        val all=saved()
        all.keys().forEach { id -> val entry=all.getJSONObject(id); peers[id]=RecordingPeerDiscovery.Peer(id,entry.getString("name"),entry.getString("address"),entry.getInt("port")) }
        renderDevices()
        discovery?.stop()
        discovery=RecordingPeerDiscovery(this,{ peer ->
            if(screen=="discovery" && peer.id!=prefs.getString("id","")) {
                peers[peer.id]=peer
                if(token(peer).isNotBlank()) remember(peer)
                renderDevices()
            }
        }, { if(screen=="discovery") message("Automatic discovery unavailable. Use Connect by address.") }).also { it.start() }
    }
    private fun renderDevices() {
        page("Find shared recordings","Looking on your home network. On the USB stick, open Shared recordings → Share this device’s recording folder.\nSaved devices also appear here; they may be offline.")
        button("Connect by address") { manualAddress() }
        val list=ListView(this)
        val items=peers.values.toList()
        list.adapter=ArrayAdapter(this,android.R.layout.simple_list_item_1,items.map { "${it.name}\n${it.address}:${it.port}" })
        list.setOnItemClickListener { _,_,position,_ -> connect(items[position]) }
        root.addView(list,LinearLayout.LayoutParams(-1,0,1f))
    }
    private fun manualAddress() {
        val input=EditText(this).apply { hint="192.168.1.160:12345"; inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI }
        AlertDialog.Builder(this).setTitle("Address shown on the sharing device").setView(input).setNegativeButton("Cancel",null)
            .setPositiveButton("Connect") { _,_ ->
                val value=input.text.toString().trim().removePrefix("http://")
                val address=value.substringBefore(':'); val port=value.substringAfter(':',"").toIntOrNull()
                if(!Regex("\\d{1,3}(\\.\\d{1,3}){3}").matches(address) || port==null || port !in 1..65535) message("Enter the address and port shown on the sharing device")
                else connect(RecordingPeerDiscovery.Peer("manual:$value","GreenStreem $address",address,port))
            }.show()
    }
    private fun base(peer: RecordingPeerDiscovery.Peer)=Uri.Builder().scheme("http").encodedAuthority(
        if(':' in peer.address) "[${peer.address}]:${peer.port}" else "${peer.address}:${peer.port}").build().toString()
    private fun connect(peer: RecordingPeerDiscovery.Peer) {
        if(token(peer).isNotBlank()) { showLibrary(peer); return }
        val input=EditText(this).apply { hint="6-digit pairing code"; inputType=android.text.InputType.TYPE_CLASS_NUMBER; filters=arrayOf(android.text.InputFilter.LengthFilter(6)) }
        AlertDialog.Builder(this).setTitle("Pair with ${peer.name}").setMessage("Enter the code shown under Share this device’s recording folder on that device.")
            .setView(input).setNegativeButton("Cancel",null).setPositiveButton("Pair") { _,_ ->
                val code=input.text.toString()
                if(!Regex("\\d{6}").matches(code)) { message("Enter all six digits"); return@setPositiveButton }
                lifecycleScope.launch {
                    val result=withContext(Dispatchers.IO) { runCatching {
                        requireLan(peer)
                        http.newCall(Request.Builder().url(base(peer)+"/pair").header("X-Pair-Code",code).post(ByteArray(0).toRequestBody()).build()).execute().use {
                            check(it.isSuccessful) { "Code expired or incorrect. Show a new code on the other device and try again." }
                            JSONObject(it.body!!.string())
                        }
                    } }
                    result.onSuccess {
                        val knownPeer=peer.copy(id=it.getString("id"))
                        remember(knownPeer,it.getString("token")); showLibrary(knownPeer)
                    }.onFailure { message(it.message ?: "Could not connect. Keep both devices on the same home network.") }
                }
            }.show()
    }
    private fun requireLan(peer: RecordingPeerDiscovery.Peer) {
        val address=java.net.InetAddress.getByName(peer.address)
        require(address.isSiteLocalAddress || address.isLinkLocalAddress || address.isLoopbackAddress) { "Use a device on your home network" }
    }
    private fun showLibrary(peer: RecordingPeerDiscovery.Peer?) {
        discovery?.stop(); discovery=null; currentPeer=peer; screen="library"
        page(peer?.name ?: "My recording folder","Loading recordings…")
        val requestGeneration=generation
        lifecycleScope.launch {
            val result=withContext(Dispatchers.IO) { runCatching {
                if(peer==null) SharedRecordingLibrary.list(this@SharedRecordingsActivity)
                else {
                    requireLan(peer)
                    http.newCall(Request.Builder().url(base(peer)+"/library").header("Authorization","Bearer ${token(peer)}").build()).execute().use {
                        check(it.code!=401) { "Pairing was reset on that device. Forget it below, then connect again." }
                        check(it.isSuccessful) { "The other device’s recording folder is unavailable" }
                        val array=JSONArray(it.body!!.string())
                        (0 until array.length()).map { index -> array.getJSONObject(index).let { item ->
                            SharedRecordingLibrary.Item(item.getString("id"),item.getString("title"),item.getLong("size"),item.getLong("modified"))
                        } }
                    }
                }
            } }
            if(requestGeneration!=generation || isFinishing) return@launch
            result.onSuccess { items ->
                page(peer?.name ?: "My recording folder",if(items.isEmpty()) "No finished video files in this folder yet. Refresh after recording finishes." else "${items.size} video file(s). Select one to play. Shared files are read-only here.")
                val bar=LinearLayout(this@SharedRecordingsActivity).apply { orientation=LinearLayout.HORIZONTAL }
                fun barButton(label: String,action: ()->Unit) { bar.addView(Button(this@SharedRecordingsActivity).apply {
                    text=label; isAllCaps=false; setTextColor(Color.WHITE); backgroundTintList=null; background=AppearanceTheme.buttonBackground(this@SharedRecordingsActivity); setOnClickListener { action() }
                },LinearLayout.LayoutParams(0,-2,1f).apply { rightMargin=12 }) }
                barButton("Refresh") { showLibrary(peer) }; barButton("Back") { home() }; root.addView(bar)
                val list=ListView(this@SharedRecordingsActivity)
                list.adapter=ArrayAdapter(this@SharedRecordingsActivity,android.R.layout.simple_list_item_1,items.map { "${it.title}\n${android.text.format.Formatter.formatFileSize(this@SharedRecordingsActivity,it.size)}" })
                list.setOnItemClickListener { _,_,position,_ -> play(items[position],peer) }
                root.addView(list,LinearLayout.LayoutParams(-1,0,1f))
            }.onFailure {
                page("Could not open recordings",it.message ?: "Check that the other device is on, sharing is enabled, and storage is attached.")
                button("Try again") { showLibrary(peer) }
                if(peer!=null) button("Forget this connection and pair again") {
                    val all=saved(); all.remove(peer.id); prefs.edit().putString("peers",all.toString()).apply(); connect(peer)
                }
                button("Back") { home() }
            }
        }
    }
    private fun play(item: SharedRecordingLibrary.Item, peer: RecordingPeerDiscovery.Peer?) {
        val view=PlayerView(this)
        val factory=if(peer!=null) DefaultHttpDataSource.Factory().setDefaultRequestProperties(mapOf("Authorization" to "Bearer ${token(peer)}"))
            else androidx.media3.datasource.DataSource.Factory { object: BaseDataSource(false) {
                var input: java.io.InputStream?=null; var remaining=0L
                override fun getUri()=Uri.parse(item.uri.ifBlank { "smb://recording/${item.id}" })
                override fun open(spec: DataSpec): Long {
                    transferInitializing(spec); input=SharedRecordingLibrary.open(this@SharedRecordingsActivity,item,spec.position)
                    remaining=if(spec.length>=0) minOf(spec.length,item.size-spec.position) else item.size-spec.position
                    transferStarted(spec); return remaining
                }
                override fun read(b: ByteArray,offset: Int,length: Int): Int {
                    if(length==0) return 0
                    if(remaining<=0) return -1
                    val count=input!!.read(b,offset,minOf(length.toLong(),remaining).toInt())
                    if(count>0) { remaining-=count; bytesTransferred(count) }; return count
                }
                override fun close() { val opened=input!=null; input?.close(); input=null; if(opened) transferEnded() }
            } }
        player=ExoPlayer.Builder(this).setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(factory)).build().also {
            view.player=it
            it.addListener(object: androidx.media3.common.Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) { message("Cannot play this file. Check storage and the sharing device.") }
            })
            val uri=if(peer==null) item.uri.ifBlank { "smb://recording/${item.id}" } else base(peer)+"/file/${item.id}"
            it.setMediaItem(MediaItem.fromUri(uri)); it.prepare(); it.play()
        }
        setContentView(view)
    }
    override fun onStop() { discovery?.stop(); discovery=null; player?.release(); player=null; super.onStop() }
    override fun onRestart() { super.onRestart(); if(screen=="library") showLibrary(currentPeer) else home() }
    override fun onDestroy() { generation++; discovery?.stop(); player?.release(); super.onDestroy() }
}
