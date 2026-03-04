package com.example.greenstreem

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ChannelSortingModeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_settings) // Reuse layout

        val rvOptions = findViewById<RecyclerView>(R.id.rvPlaylistOptions)
        rvOptions.layoutManager = LinearLayoutManager(this)

        val options = listOf("Default", "Alphabetical", "Numerical", "Manual")
        
        val prefs = getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
        rvOptions.adapter = SimpleSettingsAdapter(options) { selection ->
            prefs.edit().putString("sort_mode", selection).apply()
            if (selection == "Manual") {
                startActivity(Intent(this, ManualChannelSortingActivity::class.java))
            } else {
                finish()
            }
        }
    }
}
