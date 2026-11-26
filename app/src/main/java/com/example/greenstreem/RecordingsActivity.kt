package com.example.greenstreem

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val selectedRecordingIds=linkedSetOf<String>()
    private var recordingList: ListView?=null
    private fun canDelete(job: RecordingSchedule.Job) = job.state !in listOf("Scheduled","Recording") && !RecordingService.isRecording(job.id)
    private fun toggleSelection(job: RecordingSchedule.Job) {
        if(!canDelete(job)) { Toast.makeText(this,"Cancel this recording and wait for it to stop before deleting",Toast.LENGTH_LONG).show(); return }
        if(!selectedRecordingIds.add(job.id)) selectedRecordingIds.remove(job.id)
        showJobs()
    }
    override fun onCreate(state: Bundle?) { super.onCreate(state)
        if(!ProFeatureGate.require(this,"Scheduled recordings are available in GreenStreem Pro.")) return
        if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),802)
        onBackPressedDispatcher.addCallback(this, object: androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if(player!=null) { player?.release(); player=null; showJobs() }
                else if(selectedRecordingIds.isNotEmpty()) { selectedRecordingIds.clear(); showJobs() }
                else finish()
            }
        })
        showJobs() }
    override fun onResume() { super.onResume(); if(player==null) showJobs() }
    private fun showJobs() {
        val previousPosition=recordingList?.firstVisiblePosition ?: 0
        val previousOffset=recordingList?.getChildAt(0)?.top ?: 0
        val jobs=RecordingSchedule.jobs(this).sortedByDescending { it.start }
        selectedRecordingIds.retainAll(jobs.filter { canDelete(it) }.map { it.id }.toSet())
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,20,24,20); setBackgroundColor(0xff101820.toInt()) }
        root.addView(TextView(this).apply { text=if(selectedRecordingIds.isEmpty()) "Recordings" else "Recordings (${selectedRecordingIds.size} selected)"; textSize=26f; setTextColor(android.graphics.Color.WHITE) })
        val prefs=getSharedPreferences(DownloadPrefs.PREFS_NAME,0)
        val tree=prefs.getString("recording_tree_uri", "").orEmpty()
        root.addView(TextView(this).apply {
            text=if(tree.isBlank()) "Saving to: Network folder" else "Saving to: Local / USB folder"
            setTextColor(android.graphics.Color.LTGRAY); textSize=16f; setPadding(0,6,0,18)
        })
        if(!RecordingSchedule.alarmAllowed(this)) root.addView(TextView(this).apply {
            text="Scheduling needs permission - tap to enable Alarms & reminders"
            textSize=15f; setTextColor(android.graphics.Color.WHITE); setPadding(16,12,16,12)
            background=AppearanceTheme.buttonBackground(this@RecordingsActivity)
            isFocusable=true; isClickable=true
            setOnClickListener {
                runCatching { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:$packageName"))) }
                    .onFailure { Toast.makeText(this@RecordingsActivity,"Enable Alarms & reminders in Android Settings",Toast.LENGTH_LONG).show() }
            }
        })
        val toolbar=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; setPadding(0,0,0,16) }
        fun button(label: String, action: ()->Unit) {
            toolbar.addView(Button(this).apply {
                text=label; isAllCaps=false; textSize=16f; setTextColor(android.graphics.Color.WHITE)
                backgroundTintList=null; background=AppearanceTheme.buttonBackground(this@RecordingsActivity)
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(0,android.view.ViewGroup.LayoutParams.WRAP_CONTENT,1f).apply { rightMargin=16 })
        }
        if(selectedRecordingIds.isNotEmpty()) {
            button("Delete selected (${selectedRecordingIds.size})") { confirmDelete(jobs.filter { it.id in selectedRecordingIds }) }
            button("Cancel selection") { selectedRecordingIds.clear(); showJobs() }
        } else {
        button("Recording storage") {
            val options=mutableListOf("Choose local / USB folder", "Use network folder", "Network folder settings")
            val previousFolders=contentResolver.persistedUriPermissions.filter { it.isReadPermission && it.isWritePermission }.map { it.uri }
            val savedFolders=previousFolders.map { uri -> "Use saved folder: " + runCatching {
                val documentId=android.provider.DocumentsContract.getTreeDocumentId(uri)
                val location=if(documentId.startsWith("primary:")) "Local" else "USB"
                "$location / ${documentId.substringAfter(':').ifBlank { "Drive folder" }}"
            }.getOrDefault("Local / USB folder") }
            options.addAll(savedFolders)
            if(!RecordingSchedule.alarmAllowed(this)) options.add("Allow scheduled recording alarms")
            AlertDialog.Builder(this).setTitle("Recording storage").setItems(options.toTypedArray()) { _, index ->
                when(options[index]) {
                    "Choose local / USB folder" -> runCatching { folderPicker.launch(tree.takeIf { it.isNotBlank() }?.let(Uri::parse)) }.onFailure { Toast.makeText(this,"No folder picker installed on this device",Toast.LENGTH_LONG).show() }
                    "Use network folder" -> { prefs.edit().remove("recording_tree_uri").apply(); showJobs() }
                    "Network folder settings" -> startActivity(Intent(this,DownloadSettingsActivity::class.java))
                    "Allow scheduled recording alarms" -> startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:$packageName")))
                    else -> { prefs.edit().putString("recording_tree_uri",previousFolders[index-3].toString()).apply(); showJobs() }
                }
            }.show()
        }
        button("Refresh") { showJobs() }
        }
        root.addView(toolbar)
        if(selectedRecordingIds.isEmpty()) root.addView(Button(this).apply {
            text="Shared recordings · USB / NAS / other sticks"; isAllCaps=false; setTextColor(android.graphics.Color.WHITE)
            backgroundTintList=null; background=AppearanceTheme.buttonBackground(this@RecordingsActivity)
            setOnClickListener { startActivity(Intent(this@RecordingsActivity,SharedRecordingsActivity::class.java)) }
        })
        val list=ListView(this)
        list.adapter=object: ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,jobs.map {
            (if(it.id in selectedRecordingIds) "Selected: " else "") + "${it.title} • ${it.channel}\n${DateFormat.getDateTimeInstance().format(Date(it.start))} • ${it.state}\n${it.detail}"
        }) {
            override fun getView(position: Int, recycled: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                return super.getView(position,recycled,parent).apply {
                    setBackgroundColor(if(jobs[position].id in selectedRecordingIds)
                        androidx.core.graphics.ColorUtils.blendARGB(AppearanceTheme.accentColor(this@RecordingsActivity),android.graphics.Color.BLACK,0.45f)
                        else android.graphics.Color.TRANSPARENT)
                    isSelected=jobs[position].id in selectedRecordingIds
                }
            }
        }
        list.setOnItemClickListener { _,_,position,_ ->
            if(selectedRecordingIds.isNotEmpty()) toggleSelection(jobs[position]) else showRecordingActions(jobs[position])
        }
        list.setOnItemLongClickListener { _,_,position,_ -> toggleSelection(jobs[position]); true }
        val empty=TextView(this).apply {
            text="No recordings yet. Hold a program in the TV guide, then choose Schedule recording."
            setTextColor(android.graphics.Color.LTGRAY); textSize=17f; setPadding(8,24,8,24)
        }
        root.addView(empty)
        list.emptyView=empty
        root.addView(list,LinearLayout.LayoutParams(-1,0,1f)); setContentView(root)
        recordingList=list
        list.setSelectionFromTop(previousPosition.coerceAtMost((jobs.size-1).coerceAtLeast(0)),previousOffset)
    }
    private fun showRecordingActions(job: RecordingSchedule.Job) {
        val actions=mutableListOf<String>()
        if(job.bytes>0) actions.add("Play recording")
        if(job.state in listOf("Scheduled","Recording") || RecordingService.isRecording(job.id)) actions.add("Cancel recording")
        else actions.add("Delete recording")
        actions.add("Details")
        AlertDialog.Builder(this).setTitle(job.title).setItems(actions.toTypedArray()) { _,index ->
            when(actions[index]) {
                "Play recording" -> play(job)
                "Cancel recording" -> { RecordingSchedule.cancel(this,job); showJobs() }
                "Delete recording" -> confirmDelete(listOf(job))
                else -> AlertDialog.Builder(this).setMessage("${job.state}\n${job.bytes} bytes\n${if(job.treeUri.isNotBlank()) job.outputUri else "${job.server}/${job.share}/${job.folder}/${job.file}"}\n${job.detail}").setPositiveButton("OK",null).show()
            }
        }.show()
    }
    private fun confirmDelete(jobs: List<RecordingSchedule.Job>) {
        if(jobs.isEmpty()) return
        val label=if(jobs.size==1) jobs.single().title else "${jobs.size} selected recordings"
        AlertDialog.Builder(this).setTitle(if(jobs.size==1) "Delete recording?" else "Delete ${jobs.size} recordings?")
            .setMessage("Delete $label from their saved storage and remove them from Recordings? This cannot be undone.")
            .setNegativeButton("Keep",null)
            .setPositiveButton("Delete") { _, _ ->
                val progress=AlertDialog.Builder(this).setMessage("Deleting ${jobs.size} recording(s)...").setCancelable(false).create()
                progress.show()
                lifecycleScope.launch {
                    val results=withContext(Dispatchers.IO) {
                        jobs.map { job -> job.id to runCatching { RecordingSchedule.delete(this@RecordingsActivity,job.id) }.isSuccess }
                    }
                    selectedRecordingIds.removeAll(results.filter { it.second }.map { it.first }.toSet())
                    if(!isFinishing) {
                        progress.dismiss()
                        showJobs()
                        val deleted=results.count { it.second }
                        if(deleted==jobs.size) Toast.makeText(this@RecordingsActivity,"Deleted $deleted recording(s)",Toast.LENGTH_SHORT).show()
                        else AlertDialog.Builder(this@RecordingsActivity).setTitle("Some recordings could not be deleted")
                            .setMessage("Deleted $deleted of ${jobs.size}. Check that the remaining recordings' storage is connected and writable. They remain in your list so you can try again.")
                            .setPositiveButton("OK",null).show()
                    }
                }
            }.show()
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
