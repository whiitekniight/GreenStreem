package com.example.greenstreem

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupRestoreActivity : AppCompatActivity() {

    private lateinit var rvOptions: RecyclerView
    private var rows: List<Row> = emptyList()
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_settings)

        findViewById<TextView>(R.id.tvPlaylistSettingsTitle)?.text = "Backup & Restore"
        rvOptions = findViewById(R.id.rvPlaylistOptions)
        rvOptions.layoutManager = LinearLayoutManager(this)
        render()
    }

    private fun render() {
        val backups = SettingsBackupManager.listBackupFiles(this)
        val list = mutableListOf<Row>(
            Row.Action("Create backup now", Action.CREATE_BACKUP),
            Row.Action("Restore latest backup", Action.RESTORE_LATEST),
            Row.Action("Refresh backup list", Action.REFRESH_LIST)
        )
        if (backups.isEmpty()) {
            list.add(Row.Info("No backups found"))
        } else {
            backups.forEach { file ->
                val date = dateFmt.format(Date(file.lastModified()))
                list.add(Row.BackupFile(file, "Restore: ${file.name}\n$date"))
            }
        }
        rows = list
        rvOptions.adapter = BackupRowsAdapter(rows) { index -> onRowClick(rows[index]) }
    }

    private fun onRowClick(row: Row) {
        when (row) {
            is Row.Action -> handleAction(row.action)
            is Row.BackupFile -> confirmRestore(row.file)
            is Row.Info -> Unit
        }
    }

    private fun handleAction(action: Action) {
        when (action) {
            Action.CREATE_BACKUP -> lifecycleScope.launch {
                val result = SettingsBackupManager.backupNow(this@BackupRestoreActivity)
                result.onSuccess { file ->
                    Toast.makeText(this@BackupRestoreActivity, "Backup saved: ${file.name}", Toast.LENGTH_LONG).show()
                    render()
                }.onFailure { err ->
                    Toast.makeText(this@BackupRestoreActivity, "Backup failed: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
            Action.RESTORE_LATEST -> lifecycleScope.launch {
                val result = SettingsBackupManager.restoreLatest(this@BackupRestoreActivity)
                result.onSuccess { file ->
                    Toast.makeText(this@BackupRestoreActivity, "Restored: ${file.name}", Toast.LENGTH_LONG).show()
                    render()
                }.onFailure { err ->
                    Toast.makeText(this@BackupRestoreActivity, "Restore failed: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
            Action.REFRESH_LIST -> render()
        }
    }

    private fun confirmRestore(file: File) {
        AlertDialog.Builder(this)
            .setTitle("Restore backup")
            .setMessage("Restore from ${file.name}? This will overwrite current settings.")
            .setPositiveButton("Restore") { _, _ ->
                lifecycleScope.launch {
                    val result = SettingsBackupManager.restoreFile(this@BackupRestoreActivity, file)
                    result.onSuccess {
                        Toast.makeText(this@BackupRestoreActivity, "Restore completed", Toast.LENGTH_LONG).show()
                        render()
                    }.onFailure { err ->
                        Toast.makeText(this@BackupRestoreActivity, "Restore failed: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private sealed class Row {
        data class Action(val title: String, val action: com.example.greenstreem.BackupRestoreActivity.Action) : Row()
        data class BackupFile(val file: File, val title: String) : Row()
        data class Info(val title: String) : Row()
    }

    private enum class Action {
        CREATE_BACKUP,
        RESTORE_LATEST,
        REFRESH_LIST
    }

    private class BackupRowsAdapter(
        private val items: List<Row>,
        private val onClickIndex: (Int) -> Unit
    ) : RecyclerView.Adapter<BackupRowsAdapter.ViewHolder>() {

        class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val text: TextView = view.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val title = when (val row = items[position]) {
                is Row.Action -> row.title
                is Row.BackupFile -> row.title
                is Row.Info -> row.title
            }
            holder.text.text = title
            holder.text.setTextColor(android.graphics.Color.WHITE)
            holder.text.textSize = 16f
            holder.itemView.isFocusable = true
            holder.itemView.setBackgroundResource(R.drawable.selector_button_bg)
            holder.itemView.setPadding(32, 24, 32, 24)
            holder.itemView.setOnClickListener { onClickIndex(position) }
        }

        override fun getItemCount(): Int = items.size
    }
}
