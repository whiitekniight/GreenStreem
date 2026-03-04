package com.example.greenstreem

import android.content.Context
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PlaybackSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var rvOptions: RecyclerView

    private val aspectOptions = listOf(
        "Fit" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
        "Fill" to AspectRatioFrameLayout.RESIZE_MODE_FILL,
        "Zoom" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    )
    private val miniInfoOptions = listOf(
        "Off" to 0,
        "2 seconds" to 2,
        "4 seconds" to 4,
        "6 seconds" to 6,
        "10 seconds" to 10
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_settings)

        prefs = getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
        findViewById<TextView>(R.id.tvPlaylistSettingsTitle)?.text = "Playback Settings"

        rvOptions = findViewById(R.id.rvPlaylistOptions)
        rvOptions.layoutManager = LinearLayoutManager(this)
        renderOptions()
    }

    private fun renderOptions() {
        val autoplayLabel = if (prefs.getBoolean(KEY_AUTOPLAY_LAST_CHANNEL, true)) "On" else "Off"
        val aspectLabel = aspectOptions.firstOrNull { it.second == prefs.getInt(KEY_PLAYER_ASPECT_MODE, AspectRatioFrameLayout.RESIZE_MODE_FILL) }?.first ?: "Fill"
        val timeoutLabel = miniInfoOptions.firstOrNull { it.second == prefs.getInt(KEY_MINI_INFO_TIMEOUT_SEC, 4) }?.first ?: "4 seconds"
        val options = listOf(
            "Auto play last channel on startup: $autoplayLabel",
            "Default aspect ratio: $aspectLabel",
            "Mini info timeout: $timeoutLabel",
            "Tip: Use PREVIOUS to switch channel"
        )

        rvOptions.adapter = SimpleSettingsAdapter(options) { selection ->
            when {
                selection.startsWith("Auto play last channel") -> toggleAutoplay()
                selection.startsWith("Default aspect ratio") -> showAspectRatioDialog()
                selection.startsWith("Mini info timeout") -> showMiniInfoTimeoutDialog()
                else -> Unit
            }
        }
    }

    private fun toggleAutoplay() {
        val next = !prefs.getBoolean(KEY_AUTOPLAY_LAST_CHANNEL, true)
        prefs.edit().putBoolean(KEY_AUTOPLAY_LAST_CHANNEL, next).apply()
        renderOptions()
    }

    private fun showAspectRatioDialog() {
        val current = prefs.getInt(KEY_PLAYER_ASPECT_MODE, AspectRatioFrameLayout.RESIZE_MODE_FILL)
        val currentIndex = aspectOptions.indexOfFirst { it.second == current }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Default aspect ratio")
            .setSingleChoiceItems(aspectOptions.map { it.first }.toTypedArray(), currentIndex) { dialog, which ->
                prefs.edit().putInt(KEY_PLAYER_ASPECT_MODE, aspectOptions[which].second).apply()
                dialog.dismiss()
                renderOptions()
            }
            .show()
    }

    private fun showMiniInfoTimeoutDialog() {
        val current = prefs.getInt(KEY_MINI_INFO_TIMEOUT_SEC, 4)
        val currentIndex = miniInfoOptions.indexOfFirst { it.second == current }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Mini info timeout")
            .setSingleChoiceItems(miniInfoOptions.map { it.first }.toTypedArray(), currentIndex) { dialog, which ->
                prefs.edit().putInt(KEY_MINI_INFO_TIMEOUT_SEC, miniInfoOptions[which].second).apply()
                dialog.dismiss()
                renderOptions()
            }
            .show()
    }

    companion object {
        const val KEY_AUTOPLAY_LAST_CHANNEL = "autoplay_last_channel"
        const val KEY_PLAYER_ASPECT_MODE = "player_aspect_mode"
        const val KEY_MINI_INFO_TIMEOUT_SEC = "mini_info_timeout_sec"
    }
}
