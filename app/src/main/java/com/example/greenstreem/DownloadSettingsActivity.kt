package com.example.greenstreem

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadSettingsActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences(DownloadPrefs.PREFS_NAME, MODE_PRIVATE) }
    private lateinit var adapter: DownloadSettingsAdapter
    private val statusHandler = Handler(Looper.getMainLooper())
    private val statusRefresh = object : Runnable {
        override fun run() {
            clearFinishedDownloadStatusIfExpired()
            adapter.updateStatusRow(downloadStatusRow())
            statusHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ProFeatureGate.require(this, "Downloads and network storage are available in GreenStreem Pro.")) return

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(10, 14, 22))
            setPadding(48, 36, 48, 36)
        }

        val title = TextView(this).apply {
            text = "Downloads"
            setTextColor(Color.WHITE)
            textSize = 30f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 8)
        }
        val subtitle = TextView(this).apply {
            text = "Choose where movie downloads are saved and store network target details."
            setTextColor(Color.LTGRAY)
            textSize = 16f
            setPadding(0, 0, 0, 24)
        }
        val recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@DownloadSettingsActivity)
            itemAnimator = null
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(
            recycler,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        setContentView(root)

        adapter = DownloadSettingsAdapter { row ->
            when (row.id) {
                ROW_LOCATION -> showLocationDialog()
                ROW_SUBFOLDER -> showSubfolderDialog()
                ROW_SMB -> showSmbDialog()
                ROW_CLEAR_SMB -> clearSmbTarget()
                ROW_DOWNLOAD_STATUS -> refreshRows()
            }
        }
        recycler.adapter = adapter
        refreshRows()
    }

    override fun onResume() {
        super.onResume()
        statusHandler.post(statusRefresh)
    }

    override fun onPause() {
        statusHandler.removeCallbacks(statusRefresh)
        super.onPause()
    }

    private fun refreshRows() {
        adapter.submit(rows())
    }

    private fun rows(): List<Row> {
        val location = prefs.getString(DownloadPrefs.KEY_LOCATION, DownloadPrefs.LOCATION_DOWNLOADS)
            ?: DownloadPrefs.LOCATION_DOWNLOADS
        val subfolder = prefs.getString(DownloadPrefs.KEY_SUBFOLDER, DownloadPrefs.DEFAULT_SUBFOLDER)
            ?: DownloadPrefs.DEFAULT_SUBFOLDER
        val server = prefs.getString(DownloadPrefs.KEY_SMB_SERVER, "").orEmpty()
        val share = prefs.getString(DownloadPrefs.KEY_SMB_SHARE, "").orEmpty()
        val folder = prefs.getString(DownloadPrefs.KEY_SMB_FOLDER, "").orEmpty()
        clearFinishedDownloadStatusIfExpired()
        val smbSummary = if (server.isBlank() || share.isBlank()) {
            "Not configured"
        } else {
            SmbBrowser.targetUrl(server, share, folder)
        }

        return listOf(
            downloadStatusRow(),
            Row(ROW_LOCATION, "Save movies to", DownloadPrefs.locationLabel(location)),
            Row(ROW_SUBFOLDER, "Download subfolder", subfolder.ifBlank { "No subfolder" }),
            Row(ROW_SMB, "SMB / network target", smbSummary),
            Row(ROW_CLEAR_SMB, "Clear network target", "Remove saved SMB server, share, and login details")
        )
    }

    private fun downloadStatusRow(): Row {
        val downloadStatus = prefs.getString(DownloadPrefs.KEY_DOWNLOAD_STATUS, DownloadPrefs.STATUS_IDLE)
            ?: DownloadPrefs.STATUS_IDLE
        val downloadTitle = prefs.getString(DownloadPrefs.KEY_DOWNLOAD_TITLE, "").orEmpty()
        val downloadTarget = prefs.getString(DownloadPrefs.KEY_DOWNLOAD_TARGET, "").orEmpty()
        val downloadProgress = prefs.getInt(DownloadPrefs.KEY_DOWNLOAD_PROGRESS, 0)
        val downloadSummary = when {
            downloadTitle.isBlank() -> "No download started yet"
            downloadStatus == DownloadPrefs.STATUS_RUNNING -> "$downloadProgress%  $downloadTitle"
            downloadStatus == DownloadPrefs.STATUS_DONE -> "Complete  $downloadTitle"
            downloadStatus == DownloadPrefs.STATUS_FAILED -> "Failed  $downloadTarget"
            else -> downloadTitle
        }
        val statusProgress = if (downloadTitle.isBlank()) -1 else downloadProgress
        return Row(ROW_DOWNLOAD_STATUS, "Download status", downloadSummary, statusProgress)
    }

    private fun clearFinishedDownloadStatusIfExpired() {
        val status = prefs.getString(DownloadPrefs.KEY_DOWNLOAD_STATUS, DownloadPrefs.STATUS_IDLE)
        if (status != DownloadPrefs.STATUS_DONE && status != DownloadPrefs.STATUS_FAILED) return
        val updatedAt = prefs.getLong(DownloadPrefs.KEY_DOWNLOAD_UPDATED_AT, 0L)
        if (updatedAt <= 0L || System.currentTimeMillis() - updatedAt < FINISHED_STATUS_VISIBLE_MS) return
        prefs.edit()
            .putString(DownloadPrefs.KEY_DOWNLOAD_STATUS, DownloadPrefs.STATUS_IDLE)
            .remove(DownloadPrefs.KEY_DOWNLOAD_TITLE)
            .remove(DownloadPrefs.KEY_DOWNLOAD_TARGET)
            .putInt(DownloadPrefs.KEY_DOWNLOAD_PROGRESS, 0)
            .putLong(DownloadPrefs.KEY_DOWNLOAD_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    private fun showLocationDialog() {
        val labels = arrayOf("Downloads folder", "Movies folder")
        val values = arrayOf(DownloadPrefs.LOCATION_DOWNLOADS, DownloadPrefs.LOCATION_MOVIES)
        val current = prefs.getString(DownloadPrefs.KEY_LOCATION, DownloadPrefs.LOCATION_DOWNLOADS)
        val checked = values.indexOf(current).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Save movies to")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                prefs.edit().putString(DownloadPrefs.KEY_LOCATION, values[which]).apply()
                dialog.dismiss()
                refreshRows()
            }
            .show()
    }

    private fun showSubfolderDialog() {
        val input = EditText(this).apply {
            setText(prefs.getString(DownloadPrefs.KEY_SUBFOLDER, DownloadPrefs.DEFAULT_SUBFOLDER))
            hint = DownloadPrefs.DEFAULT_SUBFOLDER
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle("Download subfolder")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val cleaned = DownloadPrefs.cleanPathSegment(input.text?.toString().orEmpty())
                    .ifBlank { DownloadPrefs.DEFAULT_SUBFOLDER }
                prefs.edit().putString(DownloadPrefs.KEY_SUBFOLDER, cleaned).apply()
                refreshRows()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSmbDialog() {
        val server = editText("Server or IP", DownloadPrefs.KEY_SMB_SERVER)
        val username = editText("Username", DownloadPrefs.KEY_SMB_USERNAME)
        val password = editText("Password", DownloadPrefs.KEY_SMB_PASSWORD).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 12, 32, 0)
            addView(server)
            addView(username)
            addView(password)
        }

        AlertDialog.Builder(this)
            .setTitle("SMB login")
            .setView(content)
            .setPositiveButton("Browse shares") { _, _ ->
                val cleanServer = server.text?.toString()?.trim().orEmpty()
                val cleanUsername = username.text?.toString()?.trim().orEmpty()
                val cleanPassword = password.text?.toString().orEmpty()
                if (cleanServer.isBlank()) {
                    Toast.makeText(this, "Enter a server IP or name", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                prefs.edit()
                    .putString(DownloadPrefs.KEY_SMB_SERVER, cleanServer)
                    .putString(DownloadPrefs.KEY_SMB_USERNAME, cleanUsername)
                    .putString(DownloadPrefs.KEY_SMB_PASSWORD, cleanPassword)
                    .apply()
                browseSmb(cleanServer, cleanUsername, cleanPassword, null, "")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun browseSmb(server: String, username: String, password: String, share: String?, folder: String) {
        val progress = AlertDialog.Builder(this)
            .setTitle(if (share.isNullOrBlank()) "Opening shares" else "Opening folder")
            .setMessage(if (share.isNullOrBlank()) "Checking smb://$server/" else "Checking ${SmbBrowser.targetUrl(server, share, folder)}")
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { SmbBrowser.listFolders(server, username, password, share, folder) }
            }
            progress.dismiss()
            result.onSuccess { entries ->
                showSmbFolderChooser(server, username, password, share, folder, entries)
            }.onFailure { error ->
                Toast.makeText(
                    this@DownloadSettingsActivity,
                    "SMB browse failed: ${error.message ?: error.javaClass.simpleName}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showSmbFolderChooser(
        server: String,
        username: String,
        password: String,
        share: String?,
        folder: String,
        entries: List<SmbBrowser.Entry>
    ) {
        val isInShare = !share.isNullOrBlank()
        val rows = mutableListOf<String>()
        val savedServer = prefs.getString(DownloadPrefs.KEY_SMB_SERVER, "").orEmpty()
        val savedShare = prefs.getString(DownloadPrefs.KEY_SMB_SHARE, "").orEmpty()
        val savedFolder = prefs.getString(DownloadPrefs.KEY_SMB_FOLDER, "").orEmpty()
        val hasSavedTarget = savedServer.equals(server, ignoreCase = true) && savedShare.isNotBlank()
        if (hasSavedTarget) rows += savedTargetLabel(savedShare, savedFolder)
        if (isInShare) rows += "Use this folder"
        rows += "Enter path manually"
        rows += entries.map { it.name }
        if (rows.isEmpty()) rows += "No shared folders found"

        val title = if (isInShare) {
            SmbBrowser.targetUrl(server, share.orEmpty(), folder)
        } else {
            "Shares on $server"
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(rows.toTypedArray()) { _, which ->
                val picked = rows[which]
                when {
                    picked.startsWith(SAVED_TARGET_PREFIX) && hasSavedTarget -> saveSmbTarget(server, savedShare, savedFolder, username, password)
                    picked == "Use this folder" && isInShare -> saveSmbTarget(server, share.orEmpty(), folder, username, password)
                    picked == "Enter path manually" -> showManualSmbPathDialog(server, username, password)
                    picked == "No shared folders found" -> Unit
                    share.isNullOrBlank() -> browseSmb(server, username, password, picked, "")
                    else -> browseSmb(server, username, password, share, joinPath(folder, picked))
                }
            }
            .setPositiveButton("Change login") { _, _ -> showSmbDialog() }
            .setNeutralButton(if (isInShare) "New folder" else null) { _, _ ->
                if (isInShare) showCreateSmbFolderDialog(server, username, password, share.orEmpty(), folder)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showManualSmbPathDialog(server: String, username: String, password: String) {
        val share = EditText(this).apply {
            hint = "Share name"
            setText(prefs.getString(DownloadPrefs.KEY_SMB_SHARE, "").orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            selectAll()
        }
        val folder = EditText(this).apply {
            hint = "Folder path inside share"
            setText(prefs.getString(DownloadPrefs.KEY_SMB_FOLDER, "").orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 12, 32, 0)
            addView(share)
            addView(folder)
        }
        AlertDialog.Builder(this)
            .setTitle("SMB path")
            .setView(content)
            .setPositiveButton("Save") { _, _ ->
                val cleanShare = share.text?.toString()?.trim()?.trim('/').orEmpty()
                val cleanFolder = folder.text?.toString()?.trim()?.trim('/').orEmpty()
                if (cleanShare.isBlank()) {
                    Toast.makeText(this, "Enter the share name", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                saveSmbTarget(server, cleanShare, cleanFolder, username, password)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun savedTargetLabel(share: String, folder: String): String {
        val path = listOf(share, folder)
            .filter { it.isNotBlank() }
            .joinToString("/")
        return "$SAVED_TARGET_PREFIX $path"
    }

    private fun showCreateSmbFolderDialog(server: String, username: String, password: String, share: String, folder: String) {
        val input = EditText(this).apply {
            hint = "Folder name"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("New folder")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text?.toString().orEmpty()
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { SmbBrowser.createFolder(server, username, password, share, folder, name) }
                    }
                    result.onSuccess {
                        browseSmb(server, username, password, share, folder)
                    }.onFailure { error ->
                        Toast.makeText(
                            this@DownloadSettingsActivity,
                            "Could not create folder: ${error.message ?: error.javaClass.simpleName}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveSmbTarget(server: String, share: String, folder: String, username: String, password: String) {
        prefs.edit()
            .putString(DownloadPrefs.KEY_SMB_SERVER, server)
            .putString(DownloadPrefs.KEY_SMB_SHARE, share)
            .putString(DownloadPrefs.KEY_SMB_FOLDER, folder.trim('/'))
            .putString(DownloadPrefs.KEY_SMB_USERNAME, username)
            .putString(DownloadPrefs.KEY_SMB_PASSWORD, password)
            .apply()
        Toast.makeText(this, "Network target saved", Toast.LENGTH_SHORT).show()
        refreshRows()
    }

    private fun joinPath(folder: String, child: String): String {
        return listOf(folder.trim('/'), child.trim('/'))
            .filter { it.isNotBlank() }
            .joinToString("/")
    }

    private fun editText(hintText: String, key: String): EditText {
        return EditText(this).apply {
            hint = hintText
            setText(prefs.getString(key, "").orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
    }

    private fun clearSmbTarget() {
        prefs.edit()
            .remove(DownloadPrefs.KEY_SMB_SERVER)
            .remove(DownloadPrefs.KEY_SMB_SHARE)
            .remove(DownloadPrefs.KEY_SMB_FOLDER)
            .remove(DownloadPrefs.KEY_SMB_USERNAME)
            .remove(DownloadPrefs.KEY_SMB_PASSWORD)
            .apply()
        Toast.makeText(this, "Network target cleared", Toast.LENGTH_SHORT).show()
        refreshRows()
    }

    private data class Row(val id: String, val title: String, val summary: String, val progress: Int = -1)

    private class DownloadSettingsAdapter(
        private val onClick: (Row) -> Unit
    ) : RecyclerView.Adapter<DownloadSettingsAdapter.ViewHolder>() {
        private val rows = mutableListOf<Row>()

        fun submit(newRows: List<Row>) {
            rows.clear()
            rows.addAll(newRows)
            notifyDataSetChanged()
        }

        fun updateStatusRow(row: Row) {
            val index = rows.indexOfFirst { it.id == ROW_DOWNLOAD_STATUS }
            if (index < 0) return
            if (rows[index] == row) return
            rows[index] = row
            notifyItemChanged(index)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val context = parent.context
            val density = context.resources.displayMetrics.density
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((32 * density).toInt(), (20 * density).toInt(), (32 * density).toInt(), (20 * density).toInt())
            }
            val title = TextView(context).apply {
                id = android.R.id.text1
            }
            val summary = TextView(context).apply {
                id = android.R.id.text2
            }
            val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (8 * density).toInt()
                ).apply {
                    topMargin = (10 * density).toInt()
                }
            }
            root.addView(title)
            root.addView(summary)
            root.addView(progress)
            return ViewHolder(root, title, summary, progress)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val row = rows[position]
            holder.title.text = row.title
            holder.summary.text = row.summary
            holder.title.setTextColor(Color.WHITE)
            holder.summary.setTextColor(Color.LTGRAY)
            holder.title.textSize = 20f
            holder.summary.textSize = 14f
            holder.progress.visibility = if (row.progress >= 0) View.VISIBLE else View.GONE
            holder.progress.progress = row.progress.coerceIn(0, 100)
            holder.itemView.isFocusable = true
            holder.itemView.background = AppearanceTheme.buttonBackground(holder.itemView.context)
            holder.itemView.setOnClickListener { onClick(row) }
            holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                val scale = if (hasFocus) 1.03f else 1.0f
                holder.itemView.animate().scaleX(scale).scaleY(scale).setDuration(150).start()
            }
        }

        override fun getItemCount(): Int = rows.size

        class ViewHolder(
            view: View,
            val title: TextView,
            val summary: TextView,
            val progress: ProgressBar
        ) : RecyclerView.ViewHolder(view)
    }

    companion object {
        private const val ROW_DOWNLOAD_STATUS = "download_status"
        private const val ROW_LOCATION = "location"
        private const val ROW_SUBFOLDER = "subfolder"
        private const val ROW_SMB = "smb"
        private const val ROW_CLEAR_SMB = "clear_smb"
        private const val FINISHED_STATUS_VISIBLE_MS = 12000L
        private const val SAVED_TARGET_PREFIX = "Use saved target:"
    }
}
