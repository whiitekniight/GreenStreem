package com.example.greenstreem

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PlaylistManagerActivity : AppCompatActivity() {

    private lateinit var rvOptions: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_settings)
        rvOptions = findViewById(R.id.rvPlaylistOptions)
        rvOptions.layoutManager = LinearLayoutManager(this)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val profiles = PlaylistProfilesManager.loadProfiles(this)
        val activeId = PlaylistProfilesManager.getActiveProfileId(this)

        val labels = mutableListOf("Add playlist")
        labels += profiles.map { profile ->
            val active = if (profile.id == activeId) " (Active)" else ""
            val enabled = if (profile.enabled) "" else " [Disabled]"
            "${profile.name}$active$enabled"
        }

        rvOptions.adapter = SimpleSettingsAdapter(labels) { selection ->
            if (selection == "Add playlist") {
                startActivity(Intent(this, XtreamLoginActivity::class.java))
                return@SimpleSettingsAdapter
            }
            val profile = profiles.firstOrNull { selection.startsWith(it.name) }
            if (profile != null) showProfileActions(profile)
        }
    }

    private fun showProfileActions(profile: PlaylistProfile) {
        val options = arrayOf("Set active", "Edit credentials", "Delete playlist")
        AlertDialog.Builder(this)
            .setTitle(profile.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        PlaylistProfilesManager.setActiveProfile(this, profile.id)
                        Toast.makeText(this, "Active playlist switched", Toast.LENGTH_SHORT).show()
                        render()
                    }
                    1 -> {
                        startActivity(
                            Intent(this, XtreamLoginActivity::class.java)
                                .putExtra("edit_profile_id", profile.id)
                        )
                    }
                    2 -> confirmDelete(profile)
                }
            }
            .show()
    }

    private fun confirmDelete(profile: PlaylistProfile) {
        AlertDialog.Builder(this)
            .setTitle("Delete playlist?")
            .setMessage("All channels from \"${profile.name}\" will be removed from active use.")
            .setPositiveButton("Delete") { _, _ ->
                PlaylistProfilesManager.deleteProfile(this, profile.id)
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
