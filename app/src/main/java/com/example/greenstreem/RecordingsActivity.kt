package com.example.greenstreem

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.media3.common.MediaItem
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import jcifs.smb.SmbRandomAccessFile
import java.text.DateFormat
import java.util.Date

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RecordingsActivity : AppCompatActivity() {
    private val folderPicker=registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { uri ->
        if(uri!=null) runCatching {
            contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            getSharedPreferences(DownloadPrefs.PREFS_NAME,0).edit().putString("recording_tree_uri",uri.toString()).apply()
        }.onFailure { Toast.makeText(this,"Could not keep access to this folder",Toast.LENGTH_LONG).show() }
        showJobs()
    }
    private var player: ExoPlayer?=null
    override fun onCreate(state: Bundle?) { super.onCreate(state)
        if(!ProFeatureGate.require(this,"Scheduled recordings are available in GreenStreem Pro.")) return
        if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),802)
        onBackPressedDispatcher.addCallback(this, object: androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if(player!=null) { player?.release(); player=null; showJobs() } else finish()
            }
        })
        showJobs() }
    override fun onResume() { super.onResume(); if(player==null) showJobs() }
    private fun showJobs() {
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,20,24,20); setBackgroundColor(0xff101820.toInt()) }
        root.addView(TextView(this).apply { text="Recordings"; textSize=26f; setTextColor(android.graphics.Color.WHITE) })
        val prefs=getSharedPreferences(DownloadPrefs.PREFS_NAME,0)
        val tree=prefs.getString("recording_tree_uri", "").orEmpty()
        root.addView(TextView(this).apply {
            text=if(tree.isBlank()) "Saving to: Network folder" else "Saving to: Local / USB folder"
            setTextColor(android.graphics.Color.LTGRAY); textSize=16f; setPadding(0,6,0,18)
        })
        val toolbar=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; setPadding(0,0,0,16) }
        fun button(label: String, action: ()->Unit) {
            toolbar.addView(Button(this).apply {
                text=label; isAllCaps=false; textSize=16f; setTextColor(android.graphics.Color.WHITE)
                backgroundTintList=null; background=AppearanceTheme.buttonBackground(this@RecordingsActivity)
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(0,android.view.ViewGroup.LayoutParams.WRAP_CONTENT,1f).apply { rightMargin=16 })
        }
        button("Recording storage") {
            val options=mutableListOf("Choose local / USB folder", "Use network folder", "Network folder settings")
            if(!RecordingSchedule.alarmAllowed(this)) options.add("Allow scheduled recording alarms")
            AlertDialog.Builder(this).setTitle("Recording storage").setItems(options.toTypedArray()) { _, index ->
                when(index) {
                    0 -> runCatching { folderPicker.launch(null) }.onFailure { Toast.makeText(this,"No folder picker installed on this device",Toast.LENGTH_LONG).show() }
                    1 -> { prefs.edit().remove("recording_tree_uri").apply(); showJobs() }
                    2 -> startActivity(Intent(this,DownloadSettingsActivity::class.java))
                    else -> startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:$packageName")))
                }
            }.show()
        }
        button("Refresh") { showJobs() }
        root.addView(toolbar)
        val list=ListView(this)
        val jobs=RecordingSchedule.jobs(this).sortedByDescending { it.start }
        list.adapter=ArrayAdapter(this,android.R.layout.simple_list_item_1,jobs.map {
            "${it.title} • ${it.channel}\n${DateFormat.getDateTimeInstance().format(Date(it.start))} • ${it.state}\n${it.detail}"
        })
        list.setOnItemClickListener { _,_,position,_ ->
            val job=jobs[position]
            val actions=mutableListOf<String>()
            if(job.bytes>0) actions.add("Play recording")
            if(job.state in listOf("Scheduled","Recording")) actions.add("Cancel recording")
            actions.add("Details")
            AlertDialog.Builder(this).setTitle(job.title).setItems(actions.toTypedArray()) { _,index ->
                when(actions[index]) {
                    "Play recording" -> play(job)
                    "Cancel recording" -> { RecordingSchedule.cancel(this,job); showJobs() }
                    else -> AlertDialog.Builder(this).setMessage("${job.state}\n${job.bytes} bytes\n${if(job.treeUri.isNotBlank()) job.outputUri else "${job.server}/${job.share}/${job.folder}/${job.file}"}\n${job.detail}").setPositiveButton("OK",null).show()
                }
            }.show()
        }
        root.addView(list,LinearLayout.LayoutParams(-1,0,1f)); setContentView(root)
    }
    private fun play(job: RecordingSchedule.Job) {
        val view=PlayerView(this)
        val factory=if(job.outputUri.isNotBlank()) androidx.media3.datasource.DefaultDataSource.Factory(this) else androidx.media3.datasource.DataSource.Factory {
            object : BaseDataSource(false) {
                var file: SmbRandomAccessFile?=null
                var remaining=0L
                override fun getUri()=Uri.parse("smb://${job.server}/${job.share}/${job.file}")
                override fun open(spec: DataSpec): Long {
                    transferInitializing(spec)
                    file=SmbBrowser.openRandomAccess(this@RecordingsActivity,job)
                    file!!.seek(spec.position)
                    remaining=if(spec.length>=0) minOf(spec.length,file!!.length()-spec.position) else file!!.length()-spec.position
                    transferStarted(spec); return remaining
                }
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if(length==0) return 0
                    if(remaining<=0) return -1
                    val n=file!!.read(buffer,offset,minOf(length.toLong(),remaining).toInt())
                    if(n>0) { remaining-=n; bytesTransferred(n) }; return n
                }
                override fun close() { file?.close(); file=null; transferEnded() }
            }
        }
        player=ExoPlayer.Builder(this).build().also {
            view.player=it
            it.addListener(object: androidx.media3.common.Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) { Toast.makeText(this@RecordingsActivity,"Cannot replay: check recording storage",Toast.LENGTH_LONG).show() }
            })
            it.setMediaSource(ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(if(job.outputUri.isNotBlank()) job.outputUri else "smb://recording/${job.id}.ts")))
            it.prepare(); it.play()
        }
        setContentView(view)
    }
    override fun onStop() { player?.release(); player=null; super.onStop() }
}
