package com.example.greenstreem

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PlaylistProfileSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var rvOptions: RecyclerView
    private val updateIntervals = listOf(2, 6, 12, 24)
    private val logoPriorityOptions = listOf("Playlist logos", "Local folder logos", "EPG logos")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_settings)
        prefs = getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)

        rvOptions = findViewById(R.id.rvPlaylistOptions)
        rvOptions.layoutManager = LinearLayoutManager(this)
        render()
    }

    private fun render() {
        val interval = prefs.getInt(KEY_PLAYLIST_UPDATE_INTERVAL_HOURS, 12)
        val logoPriority = prefs.getInt(KEY_PLAYLIST_LOGO_PRIORITY, 0).coerceIn(logoPriorityOptions.indices)
        val rows = listOf(
            "Enable playlist: ${onOff(prefs.getBoolean(KEY_PLAYLIST_ENABLED, true))}",
            "Playlist name: ${prefs.getString("playlist_name", "Playlist") ?: "Playlist"}",
            "Server URL: ${prefs.getString("server_url", "") ?: ""}",
            "Username: ${prefs.getString("username", "") ?: ""}",
            "Password: ${mask(prefs.getString("password", "") ?: "")}",
            "Update on app start: ${onOff(prefs.getBoolean(KEY_PLAYLIST_UPDATE_ON_START, true))}",
            "Update interval: $interval hours",
            "Logo priority: ${logoPriorityOptions[logoPriority]}"
        )

        rvOptions.adapter = SimpleSettingsAdapter(rows) { row ->
            when {
                row.startsWith("Enable playlist") -> toggle(KEY_PLAYLIST_ENABLED, true)
                row.startsWith("Playlist name") -> editText("Playlist name", "playlist_name", false)
                row.startsWith("Server URL") -> editText("Server URL", "server_url", false, onSaved = {
                    XtreamManager.initFromPrefs(this)
                })
                row.startsWith("Username") -> editText("Username", "username", false, onSaved = {
                    XtreamManager.initFromPrefs(this)
                })
                row.startsWith("Password") -> editText("Password", "password", true, onSaved = {
                    XtreamManager.initFromPrefs(this)
                })
                row.startsWith("Update on app start") -> toggle(KEY_PLAYLIST_UPDATE_ON_START, true)
                row.startsWith("Update interval") -> chooseUpdateInterval()
                row.startsWith("Logo priority") -> chooseLogoPriority()
            }
        }
    }

    private fun toggle(key: String, defaultValue: Boolean) {
        val next = !prefs.getBoolean(key, defaultValue)
        prefs.edit().putBoolean(key, next).apply()
        PlaylistProfilesManager.syncLegacyKeysToActive(this)
        render()
    }

    private fun chooseUpdateInterval() {
        val current = prefs.getInt(KEY_PLAYLIST_UPDATE_INTERVAL_HOURS, 12)
        val currentIndex = updateIntervals.indexOf(current).coerceAtLeast(0)
        val labels = updateIntervals.map { "$it hours" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Update interval")
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                prefs.edit().putInt(KEY_PLAYLIST_UPDATE_INTERVAL_HOURS, updateIntervals[which]).apply()
                PlaylistProfilesManager.syncLegacyKeysToActive(this@PlaylistProfileSettingsActivity)
                dialog.dismiss()
                render()
            }
            .show()
    }

    private fun chooseLogoPriority() {
        val current = prefs.getInt(KEY_PLAYLIST_LOGO_PRIORITY, 0).coerceIn(logoPriorityOptions.indices)
        AlertDialog.Builder(this)
            .setTitle("Logo priority")
            .setSingleChoiceItems(logoPriorityOptions.toTypedArray(), current) { dialog, which ->
                prefs.edit().putInt(KEY_PLAYLIST_LOGO_PRIORITY, which).apply()
                PlaylistProfilesManager.syncLegacyKeysToActive(this@PlaylistProfileSettingsActivity)
                dialog.dismiss()
                render()
            }
            .show()
    }

    private fun editText(title: String, key: String, password: Boolean, onSaved: (() -> Unit)? = null) {
        val input = EditText(this).apply {
            setSingleLine(true)
            setText(prefs.getString(key, "") ?: "")
            if (password) {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().putString(key, input.text?.toString()?.trim().orEmpty()).apply()
                PlaylistProfilesManager.syncLegacyKeysToActive(this@PlaylistProfileSettingsActivity)
                onSaved?.invoke()
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun onOff(value: Boolean): String = if (value) "On" else "Off"
    private fun mask(value: String): String = if (value.isBlank()) "" else "********"

    companion object {
        private const val KEY_PLAYLIST_ENABLED = "playlist_enabled"
        private const val KEY_PLAYLIST_UPDATE_ON_START = "playlist_update_on_start"
        private const val KEY_PLAYLIST_UPDATE_INTERVAL_HOURS = "playlist_update_interval_hours"
        private const val KEY_PLAYLIST_LOGO_PRIORITY = "playlist_logo_priority"
    }
}
