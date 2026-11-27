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
        }.onFailure { chooseBuiltInStorage() }
        showJobs()
    }
    private var player: ExoPlayer?=null
    private val casting by lazy { TvCastingController(this) }
    private val selectedRecordingIds=linkedSetOf<String>()
    private var recordingList: ListView?=null
    private data class Row(val local: RecordingSchedule.Job?=null,val remote: org.json.JSONObject?=null) {
        val id: String get()=local?.id ?: "pc:"+remote!!.getString("id")
    }
    private var rows=emptyList<Row>()
    private var remoteJobs=emptyList<org.json.JSONObject>()
    private var listAdapter: ArrayAdapter<String>?=null
    private var refreshJob: kotlinx.coroutines.Job?=null
    private var remoteStatus: TextView?=null
    private fun refreshRows() {
        val list=recordingList ?: return
        val oldPosition=list.firstVisiblePosition
        val oldId=rows.getOrNull(oldPosition)?.id
        val offset=list.getChildAt(0)?.top ?: 0
        val focusedId=rows.getOrNull(list.selectedItemPosition)?.id
        rows=(RecordingSchedule.jobs(this).map { Row(local=it) }+remoteJobs.map { Row(remote=it) }).sortedByDescending { it.local?.start ?: it.remote!!.getLong("start") }
        val labels=rows.map { row -> row.local?.let {
            (if(it.id in selectedRecordingIds) "Selected: " else "")+"${it.title} · ${it.channel}\n${DateFormat.getDateTimeInstance().format(Date(it.start))} · ${it.state}"+(if(it.weekly) " · Weekly" else "")+" · This device\n${it.detail}"
        } ?: row.remote!!.let {
            "${it.getString("title")} · ${it.getString("channel")}\n${DateFormat.getDateTimeInstance().format(Date(it.getLong("start")))} · ${it.getString("state")}"+(if(it.optInt("weekly")==1) " · Weekly" else "")+" · Recorder computer\n${it.optString("detail")}"
        } }
        listAdapter?.apply { setNotifyOnChange(false);clear();addAll(labels);notifyDataSetChanged() }
        if(focusedId!=null && list.hasFocus()) list.setSelection(rows.indexOfFirst { it.id==focusedId }.coerceAtLeast(0))
        else list.setSelectionFromTop(rows.indexOfFirst { it.id==oldId }.takeIf { it>=0 } ?: oldPosition.coerceAtMost((rows.size-1).coerceAtLeast(0)),offset)
    }
    private suspend fun refreshRemote() {
        if(PcRecorder.connected(this)) runCatching { PcRecorder.jobs(this) }.onSuccess { result ->
            val array=result.getJSONArray("jobs")
            remoteJobs=(0 until array.length()).map { array.getJSONObject(it) }
            remoteStatus?.text="Recorder saves to: ${result.optString("folder")}. Tap a recording below to play or manage it."
        }.onFailure { remoteStatus?.text="Recorder computer unavailable. Keep it running on the same network. Previously loaded recordings remain listed." }
        else remoteJobs=emptyList()
        refreshRows()
    }
    private fun canDelete(job: RecordingSchedule.Job) = !job.weekly && job.state !in listOf("Scheduled","Recording") && !RecordingService.isRecording(job.id)
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
    override fun onResume() {
        super.onResume(); if(player==null) showJobs()
        refreshJob?.cancel()
        refreshJob=lifecycleScope.launch { while(true) {
            if(player==null && selectedRecordingIds.isEmpty()) refreshRemote()
            kotlinx.coroutines.delay(5000)
        } }
    }
    override fun onPause() { refreshJob?.cancel();super.onPause() }
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
            text=if(PcRecorder.enabled(this@RecordingsActivity)) "New schedules record on your computer. Computer and device recordings are listed together below." else if(tree.isBlank()) "Saving to: Network folder" else if(RecordingLocalFolders.direct(tree)) "Saving to: ${RecordingLocalFolders.label(this@RecordingsActivity,tree)}" else "Saving to: Local / USB folder"
            setTextColor(android.graphics.Color.LTGRAY); textSize=16f; setPadding(0,6,0,18)
        })
        if(!PcRecorder.enabled(this) && !RecordingSchedule.alarmAllowed(this)) root.addView(TextView(this).apply {
            text="Scheduling needs permission for device recording - tap to enable Alarms & reminders. PC recordings do not need this permission."
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
            }, LinearLayout.LayoutParams(0,android.view.ViewGroup.LayoutParams.WRAP_CONTENT,1f).apply { rightMargin=(12*resources.displayMetrics.density).toInt() })
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
                    "Choose local / USB folder" -> runCatching {
                        folderPicker.launch(tree.takeIf { it.isNotBlank() && !RecordingLocalFolders.direct(it) }?.let(Uri::parse))
                    }.onFailure { chooseBuiltInStorage() }
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
        if(selectedRecordingIds.isEmpty()) root.addView(Button(this).apply {
            text=if(PcRecorder.enabled(this@RecordingsActivity)) "PC recorder · schedules and replay" else "Set up PC recorder · record while this device is off"
            isAllCaps=false;setTextColor(android.graphics.Color.WHITE);backgroundTintList=null
            background=AppearanceTheme.buttonBackground(this@RecordingsActivity)
            setOnClickListener { startActivity(Intent(this@RecordingsActivity,PcRecorderActivity::class.java)) }
        },LinearLayout.LayoutParams(-1,-2).apply { topMargin=(8*resources.displayMetrics.density).toInt();bottomMargin=(12*resources.displayMetrics.density).toInt() })
        val list=ListView(this)
        listAdapter=ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,mutableListOf<String>())
        list.adapter=listAdapter
        list.setOnItemClickListener { _,_,position,_ -> rows.getOrNull(position)?.let { row ->
            if(row.local!=null) { if(selectedRecordingIds.isNotEmpty()) toggleSelection(row.local) else showRecordingActions(row.local) }
            else remoteActions(row.remote!!)
        } }
        list.setOnItemLongClickListener { _,_,position,_ ->
            rows.getOrNull(position)?.local?.let { toggleSelection(it) }
                ?: Toast.makeText(this,"Tap a computer recording for play, stop or delete",Toast.LENGTH_SHORT).show()
            true
        }
        remoteStatus=TextView(this).apply { setTextColor(android.graphics.Color.LTGRAY);setPadding(0,4,0,12)
            text=if(PcRecorder.connected(this@RecordingsActivity)) "Loading recorder computer..." else "" }
        root.addView(remoteStatus)
        val empty=TextView(this).apply {
            text="No recordings yet. Hold a program in the TV guide, then choose Schedule recording."
            setTextColor(android.graphics.Color.LTGRAY); textSize=17f; setPadding(8,24,8,24)
        }
        root.addView(empty)
        list.emptyView=empty
        root.addView(list,LinearLayout.LayoutParams(-1,0,1f)); setContentView(root)
        recordingList=list
        ScreenBack.install(this)
        refreshRows()
        lifecycleScope.launch { refreshRemote() }
        list.setSelectionFromTop(previousPosition.coerceAtMost((jobs.size-1).coerceAtLeast(0)),previousOffset)
    }
    private fun remoteActions(job: org.json.JSONObject) {
        val active=job.getString("state") in listOf("Scheduled","Recording")
        val actions=mutableListOf<String>()
        if(job.optLong("bytes")>0 && !active) actions.add("Play recording")
        if(active || job.optInt("weekly")==1) actions.add("Stop / cancel recording")
        if(!active) actions.add("Delete recording")
        AlertDialog.Builder(this).setTitle(job.getString("title")).setMessage(job.optString("detail").takeIf { it.isNotBlank() })
            .setItems(actions.toTypedArray()) { _,index ->
                if(actions[index]=="Play recording") playRemote(job)
                else AlertDialog.Builder(this).setTitle(actions[index]+"?")
                    .setMessage(if(actions[index].startsWith("Stop")) "Stops this recording and future weekly repeats. Saved video is kept." else "Permanently deletes this file from the recorder computer.")
                    .setNegativeButton("Keep",null).setPositiveButton("Confirm") { _,_ -> lifecycleScope.launch {
                        runCatching { if(actions[index].startsWith("Stop")) PcRecorder.cancel(this@RecordingsActivity,job.getString("id")) else PcRecorder.delete(this@RecordingsActivity,job.getString("id")) }
                            .onSuccess { refreshRemote() }.onFailure { Toast.makeText(this@RecordingsActivity,it.message,Toast.LENGTH_LONG).show() }
                    } }.show()
            }.show()
    }
    private fun playRemote(job: org.json.JSONObject) {
        val view=PlayerView(this)
        val http=androidx.media3.datasource.DefaultHttpDataSource.Factory().setDefaultRequestProperties(mapOf("Authorization" to "Bearer ${PcRecorder.token(this)}"))
        player=ExoPlayer.Builder(this).build().also {
            view.player=it
            it.addListener(object: androidx.media3.common.Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) { Toast.makeText(this@RecordingsActivity,"Cannot replay: check the recorder computer and drive",Toast.LENGTH_LONG).show() }
            })
            it.setMediaSource(ProgressiveMediaSource.Factory(http).createMediaSource(MediaItem.fromUri("${PcRecorder.address(this)}/v1/jobs/${job.getString("id")}/content")))
            it.prepare();it.play()
        }
        setContentView(view);ScreenBack.install(this)
    }
    private fun chooseBuiltInStorage() {
        lifecycleScope.launch {
            val targets=withContext(Dispatchers.IO) { RecordingLocalFolders.available(this@RecordingsActivity) }
            if(isFinishing)return@launch
            val root=LinearLayout(this@RecordingsActivity).apply { orientation=LinearLayout.VERTICAL;setPadding(24,16,24,16) }
            root.addView(TextView(this@RecordingsActivity).apply {
                text="Choose where to save recordings. GreenStreem uses its own folder on that drive. Removing the app may remove these files."+
                    if(targets.none { it.removable }) "\nUSB storage appears here when the device makes it available." else ""
                setTextColor(android.graphics.Color.LTGRAY);textSize=16f;setPadding(0,0,0,16)
            })
            val dialog=AlertDialog.Builder(this@RecordingsActivity).setTitle("Choose recording storage").setView(root).setNegativeButton("Cancel",null).create()
            targets.forEach { target ->
                root.addView(Button(this@RecordingsActivity).apply {
                    text=target.label;isAllCaps=false;setTextColor(android.graphics.Color.WHITE);backgroundTintList=null
                    background=AppearanceTheme.buttonBackground(this@RecordingsActivity)
                    setOnClickListener {
                        isEnabled=false
                        lifecycleScope.launch {
                            val result=withContext(Dispatchers.IO) { runCatching { RecordingLocalFolders.prepare(this@RecordingsActivity,target) } }
                            if(isFinishing)return@launch
                            result.onSuccess { uri ->
                                getSharedPreferences(DownloadPrefs.PREFS_NAME,0).edit().putString("recording_tree_uri",uri).apply()
                                dialog.dismiss();showJobs()
                            }.onFailure { Toast.makeText(this@RecordingsActivity,it.message ?: "Storage is unavailable",Toast.LENGTH_LONG).show();isEnabled=true }
                        }
                    }
                },LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=(12*resources.displayMetrics.density).toInt() })
            }
            dialog.show()
        }
    }
    private fun showRecordingActions(job: RecordingSchedule.Job) {
        val actions=mutableListOf<String>()
        if(job.bytes>0) actions.add("Play recording")
        if(job.weekly || job.state in listOf("Scheduled","Recording") || RecordingService.isRecording(job.id)) actions.add("Cancel recording")
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
        setContentView(casting.wrapPlayer(view,{ CastSupport.recording(applicationContext,job).copy(position=CastSupport.position(player)) }) { player?.pause() });ScreenBack.install(this)
    }
    override fun onStop() { player?.release(); player=null; super.onStop() }
}
