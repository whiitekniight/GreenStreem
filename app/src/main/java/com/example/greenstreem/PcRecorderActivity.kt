package com.example.greenstreem

import android.os.Bundle
import android.graphics.Color
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PcRecorderActivity: AppCompatActivity() {
    private lateinit var root: LinearLayout
    private var player: androidx.media3.exoplayer.ExoPlayer?=null
    private var generation=0
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if(!ProFeatureGate.require(this,"PC recording is available in GreenStreem Pro.")) return
        onBackPressedDispatcher.addCallback(this,object: androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if(player!=null) { player?.release();player=null;home() } else finish() }
        })
        home()
    }
    private fun text(value: String,size: Float=17f) { root.addView(TextView(this).apply { text=value;textSize=size;setTextColor(Color.WHITE);setPadding(0,8,0,14) }) }
    private fun button(label: String,action: ()->Unit) {
        root.addView(Button(this).apply {
            text=label;isAllCaps=false;textSize=16f;setTextColor(Color.WHITE);backgroundTintList=null
            background=AppearanceTheme.buttonBackground(this@PcRecorderActivity);setOnClickListener { action() }
        },LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=(12*resources.displayMetrics.density).toInt() })
    }
    private fun message(value: String)=Toast.makeText(this,value,Toast.LENGTH_LONG).show()
    private fun home() {
        val current=++generation
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,20,24,20);setBackgroundColor(0xff101820.toInt()) }
        setContentView(ScrollView(this).apply { addView(root) });ScreenBack.install(this)
        text("Recorder computer",26f)
        text("Your PC records to its chosen local, USB or NAS folder. Keep the PC on, signed in and GreenStreem Recorder running. This device can be off after the PC confirms your schedule.")
        if(!PcRecorder.connected(this)) {
            button("Find my PC recorder") {
                lifecycleScope.launch {
                    runCatching { PcRecorder.discover() }.onSuccess { peers ->
                        if(peers.isEmpty()) { message("No recorder found. Check the PC and its private-network firewall permission, or enter its address.");return@onSuccess }
                        AlertDialog.Builder(this@PcRecorderActivity).setTitle("Choose PC recorder").setItems(peers.map { "${it.first} · ${it.second}" }.toTypedArray()) { _,index -> connect(peers[index].second) }.show()
                    }.onFailure { message("Could not search this network; enter the PC address instead") }
                }
            }
            button("Enter PC address") { connect("") }
            return
        }
        text("Connected to ${PcRecorder.address(this)}")
        button(if(PcRecorder.enabled(this)) "Recording destination: PC recorder" else "Recording destination: this device") {
            AlertDialog.Builder(this).setTitle("Record using").setItems(arrayOf("PC recorder — this device can be off","This device — local / USB / NAS")) { _,index -> PcRecorder.setEnabled(this,index==0);home() }.show()
        }
        button("Refresh recordings") { home() }
        button("Disconnect this device from PC") {
            AlertDialog.Builder(this).setTitle("Disconnect PC recorder?").setMessage("Existing PC schedules will keep running. Reconnect to see or manage them.")
                .setNegativeButton("Keep",null).setPositiveButton("Disconnect") { _,_ -> PcRecorder.forget(this);home() }.show()
        }
        val status=TextView(this).apply { text="Loading PC recordings...";setTextColor(Color.LTGRAY) };root.addView(status)
        lifecycleScope.launch {
            runCatching { PcRecorder.jobs(this@PcRecorderActivity) }.onSuccess { result ->
                if(current!=generation || isFinishing)return@onSuccess
                status.text="PC saves to: ${result.optString("folder")}"
                val jobs=result.getJSONArray("jobs")
                if(jobs.length()==0) text("No PC recordings yet. Hold a program in the guide and choose Schedule recording.")
                for(index in 0 until jobs.length()) {
                    val job=jobs.getJSONObject(index)
                    button("${job.getString("title")} · ${job.getString("channel")}\n${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(job.getLong("start")))} · ${job.getString("state")}") { actions(job) }
                }
            }.onFailure { if(current==generation) status.text="PC recorder is unavailable. Check that the PC is on, the recorder is running, and both devices are on the same network." }
        }
    }
    private fun connect(initial: String) {
        val fields=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,12,24,12) }
        val address=EditText(this).apply { hint="PC address, e.g. 192.168.1.10:8791";setText(initial);inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS }
        val code=EditText(this).apply { hint="Code shown on the PC";inputType=android.text.InputType.TYPE_CLASS_NUMBER }
        fields.addView(address);fields.addView(code)
        val dialog=AlertDialog.Builder(this).setTitle("Connect PC recorder").setMessage("On the PC, click Connect a device. Enter its code here once.").setView(fields).setNegativeButton("Cancel",null).setPositiveButton("Connect",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false
            lifecycleScope.launch {
                runCatching { PcRecorder.connect(this@PcRecorderActivity,address.text.toString(),code.text.toString()) }
                    .onSuccess { dialog.dismiss();home() }.onFailure { message(it.message ?: "Could not connect to PC");dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=true }
            }
        } };dialog.show()
    }
    private fun actions(job: JSONObject) {
        val active=job.getString("state") in listOf("Scheduled","Recording") || job.optInt("weekly")==1
        val choices=if(active) arrayOf("Stop / cancel recording") else if(job.optLong("bytes")>0) arrayOf("Play recording","Delete recording") else arrayOf("Delete recording")
        AlertDialog.Builder(this).setTitle(job.getString("title")).setMessage(job.optString("detail").takeIf { it.isNotBlank() }).setItems(choices) { _,index ->
            when(choices[index]) {
                "Play recording" -> play(job)
                else -> AlertDialog.Builder(this).setTitle(choices[index]+"?").setMessage(if(active) "The recorder will stop this schedule and its weekly repeats." else "This permanently deletes the saved file from the PC's recording folder.")
                    .setNegativeButton("Keep",null).setPositiveButton("Confirm") { _,_ -> lifecycleScope.launch {
                        runCatching { if(active) PcRecorder.cancel(this@PcRecorderActivity,job.getString("id")) else PcRecorder.delete(this@PcRecorderActivity,job.getString("id")) }
                            .onSuccess { home() }.onFailure { message(it.message ?: "PC recorder is unavailable") }
                    } }.show()
            }
        }.show()
    }
    private fun play(job: JSONObject) {
        generation++
        val view=androidx.media3.ui.PlayerView(this)
        val http=androidx.media3.datasource.DefaultHttpDataSource.Factory().setDefaultRequestProperties(mapOf("Authorization" to "Bearer ${PcRecorder.token(this)}"))
        player=androidx.media3.exoplayer.ExoPlayer.Builder(this).build().also {
            view.player=it
            it.addListener(object: androidx.media3.common.Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) { message("Cannot replay: check the PC and recording drive") }
            })
            it.setMediaSource(androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(http).createMediaSource(androidx.media3.common.MediaItem.fromUri("${PcRecorder.address(this)}/v1/jobs/${job.getString("id")}/content")))
            it.prepare();it.play()
        }
        setContentView(view)
    }
    override fun onStop() { player?.release();player=null;super.onStop() }
    override fun onResume() { super.onResume();if(::root.isInitialized && player==null)home() }
}
