package com.example.greenstreem

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PlaylistSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_settings)

        val rvOptions = findViewById<RecyclerView>(R.id.rvPlaylistOptions)
        rvOptions.layoutManager = LinearLayoutManager(this)

        val options = listOf(
            "Manage playlists",
            "Current playlist settings",
            "Update all playlists",
            "M3U Playlists",
            "Xtream Codes API"
        )

        rvOptions.adapter = SimpleSettingsAdapter(options) { selection ->
            when (selection) {
                "Manage playlists" -> {
                    startActivity(Intent(this, PlaylistManagerActivity::class.java))
                }
                "Current playlist settings" -> {
                    startActivity(Intent(this, PlaylistProfileSettingsActivity::class.java))
                }
                "Update all playlists" -> {
                    updateAllPlaylistsNow()
                }
                "Xtream Codes API" -> {
                    startActivity(Intent(this, XtreamLoginActivity::class.java))
                }
                "M3U Playlists" -> {
                    startActivity(Intent(this, SetupActivity::class.java))
                }
            }
        }
    }

    private fun updateAllPlaylistsNow() {
        XtreamManager.initFromPrefs(this)
        val service = XtreamManager.getService()
        if (service == null) {
            Toast.makeText(this, "No active playlist connection", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Updating playlist...", Toast.LENGTH_SHORT).show()
        service.getLiveCategories(XtreamManager.username, XtreamManager.password)
            .enqueue(object : Callback<List<XtreamCategory>> {
                override fun onResponse(call: Call<List<XtreamCategory>>, response: Response<List<XtreamCategory>>) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@PlaylistSettingsActivity, "Playlist update successful", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@PlaylistSettingsActivity, "Playlist update failed", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<List<XtreamCategory>>, t: Throwable) {
                    Toast.makeText(this@PlaylistSettingsActivity, "Update failed: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }
}
