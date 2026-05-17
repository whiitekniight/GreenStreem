package com.example.greenstreem

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.net.URL

class M3uLoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_m3u_login)

        val etName = findViewById<EditText>(R.id.etM3uName)
        val etUrl = findViewById<EditText>(R.id.etM3uUrl)
        val btnSave = findViewById<Button>(R.id.btnSaveM3u)
        val progress = findViewById<ProgressBar>(R.id.m3uProgressBar)

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim().ifBlank { "M3U Playlist" }
            val url = etUrl.text.toString().trim()

            if (url.isBlank()) {
                Toast.makeText(this, "Enter your M3U playlist URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progress.visibility = View.VISIBLE
            btnSave.isEnabled = false

            val xtreamCredentials = XtreamM3uUrl.parse(url)
            if (xtreamCredentials != null) {
                saveXtreamM3uPlaylist(name, xtreamCredentials, progress, btnSave)
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        URL(url).openStream().use { stream -> M3uParser.parse(stream) }
                    }
                }

                progress.visibility = View.GONE
                btnSave.isEnabled = true

                val channels = result.getOrNull().orEmpty()
                if (result.isFailure || channels.isEmpty()) {
                    Toast.makeText(
                        this@M3uLoginActivity,
                        "Could not load channels from that M3U URL",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                saveM3uPlaylist(name, url)
                val intent = Intent(this@M3uLoginActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
    }

    private fun saveXtreamM3uPlaylist(
        name: String,
        credentials: XtreamM3uCredentials,
        progress: ProgressBar,
        btnSave: Button
    ) {
        XtreamManager.init(credentials.serverUrl)
        val service = XtreamManager.getService()
        if (service == null) {
            progress.visibility = View.GONE
            btnSave.isEnabled = true
            Toast.makeText(this, "Could not connect to that IPTV server", Toast.LENGTH_SHORT).show()
            return
        }

        service.login(credentials.username, credentials.password).enqueue(object : Callback<XtreamLoginResponse> {
            override fun onResponse(call: Call<XtreamLoginResponse>, response: Response<XtreamLoginResponse>) {
                progress.visibility = View.GONE
                btnSave.isEnabled = true
                val active = response.body()?.userInfo?.status.equals("Active", ignoreCase = true)
                if (!response.isSuccessful || !active) {
                    Toast.makeText(this@M3uLoginActivity, "Invalid or inactive IPTV account", Toast.LENGTH_LONG).show()
                    return
                }
                saveXtreamCredentials(name, credentials)
                val intent = Intent(this@M3uLoginActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }

            override fun onFailure(call: Call<XtreamLoginResponse>, t: Throwable) {
                progress.visibility = View.GONE
                btnSave.isEnabled = true
                Toast.makeText(this@M3uLoginActivity, "Connection failed: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun saveXtreamCredentials(name: String, credentials: XtreamM3uCredentials) {
        val profile = PlaylistProfilesManager.createProfile(
            name,
            credentials.serverUrl,
            credentials.username,
            credentials.password
        )
        PlaylistProfilesManager.upsertProfileAndActivate(this, profile)
        getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("playlist_type", "xtream")
            .remove("m3u_url")
            .remove("last_category_id_live")
            .remove("last_category_pos_live")
            .remove("last_category_id")
            .remove("last_channel_id")
            .remove("last_playback_channel_id")
            .remove("last_playback_live_category_id")
            .putString("playlist_name", name)
            .putString("server_url", credentials.serverUrl)
            .putString("username", credentials.username)
            .putString("password", credentials.password)
            .putString("player_live_stream_format", if (credentials.output.equals("m3u8", ignoreCase = true)) "hls" else "ts")
            .putBoolean("has_playlist", true)
            .putBoolean("playlist_enabled", true)
            .apply()
    }

    private fun saveM3uPlaylist(name: String, url: String) {
        val profile = PlaylistProfilesManager.createProfile(name, url, "", "")
        PlaylistProfilesManager.upsertProfileAndActivate(this, profile)
        getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("playlist_type", "m3u")
            .putString("m3u_url", url)
            .remove("last_category_id_live")
            .remove("last_category_pos_live")
            .remove("last_category_id")
            .remove("last_channel_id")
            .remove("last_playback_channel_id")
            .remove("last_playback_live_category_id")
            .putString("playlist_name", name)
            .putString("server_url", url)
            .putString("username", "")
            .putString("password", "")
            .putBoolean("has_playlist", true)
            .putBoolean("playlist_enabled", true)
            .apply()
    }
}
