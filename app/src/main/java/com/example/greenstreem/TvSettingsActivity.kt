package com.example.greenstreem

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class TvSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_settings)

        val rvOptions = findViewById<RecyclerView>(R.id.rvPlaylistOptions)
        rvOptions.layoutManager = LinearLayoutManager(this)

        val options = listOf(
            "Channel Sorting",
            "Playback",
            "Groups",
            "Group Sorting"
        )

        rvOptions.adapter = SimpleSettingsAdapter(options) { selection ->
            when (selection) {
                "Channel Sorting" -> {
                    startActivity(Intent(this, ChannelSortingModeActivity::class.java))
                }
                "Playback" -> {
                    startActivity(Intent(this, PlaybackSettingsActivity::class.java))
                }
                "Groups" -> {
                    startActivity(Intent(this, ManageGroupsActivity::class.java))
                }
                "Group Sorting" -> {
                    startActivity(Intent(this, GroupSortingActivity::class.java))
                }
            }
        }
    }
}
