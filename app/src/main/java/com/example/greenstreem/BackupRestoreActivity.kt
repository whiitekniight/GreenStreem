package com.example.greenstreem

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class BackupRestoreActivity : AppCompatActivity() {

    private lateinit var rvOptions: RecyclerView
    private var rows: List<Row> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_settings)

        findViewById<TextView>(R.id.tvPlaylistSettingsTitle)?.text = "Backup & Restore"
        rvOptions = findViewById(R.id.rvPlaylistOptions)
        rvOptions.layoutManager = LinearLayoutManager(this)
        render()
    }

    private fun render() {
        val backups = SettingsBackupManager.listAvailableBackups(this)
        val list = mutableListOf<Row>(
            Row.Action("Create named backup", Action.CREATE_BACKUP),
            Row.Action("Refresh backup list", Action.REFRESH_LIST)
        )
        if (backups.isEmpty()) {
            list.add(Row.Info("No backups found in ${SettingsBackupManager.publicBackupLocation()}"))
        } else {
            backups.forEach { backup ->
                list.add(Row.BackupFile(backup, "${backup.title}\n${backup.detail}"))
            }
        }
        list.add(Row.Info("Downloads backups: ${SettingsBackupManager.publicBackupLocation()}"))
        rows = list
        rvOptions.adapter = BackupRowsAdapter(rows) { index -> onRowClick(rows[index]) }
    }

    private fun onRowClick(row: Row) {
        when (row) {
            is Row.Action -> handleAction(row.action)
            is Row.BackupFile -> confirmRestore(row.backup)
            is Row.Info -> Unit
        }
    }

    private fun handleAction(action: Action) {
        when (action) {
            Action.CREATE_BACKUP -> showBackupNameDialog()
            Action.REFRESH_LIST -> render()
        }
    }

    private fun showBackupNameDialog() {
        val input = EditText(this).apply {
            hint = "Living room Shield"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Name backup")
            .setView(input)
            .setPositiveButton("Backup") { _, _ ->
                val label = input.text?.toString()?.trim().orEmpty()
                lifecycleScope.launch {
                    val result = SettingsBackupManager.backupNow(this@BackupRestoreActivity, label)
                    result.onSuccess { file ->
                        Toast.makeText(this@BackupRestoreActivity, "Backup saved: ${file.name}", Toast.LENGTH_LONG).show()
                        render()
                    }.onFailure { err ->
                        Toast.makeText(this@BackupRestoreActivity, "Backup failed: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmRestore(backup: SettingsBackupManager.BackupEntry) {
        AlertDialog.Builder(this)
            .setTitle("Restore backup")
            .setMessage("Restore ${backup.title}? This will overwrite current settings.")
            .setPositiveButton("Restore") { _, _ ->
                lifecycleScope.launch {
                    val result = SettingsBackupManager.restoreEntry(this@BackupRestoreActivity, backup)
                    result.onSuccess { summary ->
                        Toast.makeText(this@BackupRestoreActivity, summary.message(), Toast.LENGTH_LONG).show()
                        restartApp()
                    }.onFailure { err ->
                        Toast.makeText(this@BackupRestoreActivity, "Restore failed: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun restartApp() {
        val intent = Intent(this, SplashActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    private sealed class Row {
        data class Action(val title: String, val action: com.example.greenstreem.BackupRestoreActivity.Action) : Row()
        data class BackupFile(val backup: SettingsBackupManager.BackupEntry, val title: String) : Row()
        data class Info(val title: String) : Row()
    }

    private enum class Action {
        CREATE_BACKUP,
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
