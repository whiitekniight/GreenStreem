package com.example.greenstreem

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.security.MessageDigest

class SettingsActivity : AppCompatActivity() {

    private lateinit var tvDetailTitle: TextView
    private lateinit var tvDetailDesc: TextView
    private lateinit var ivDetailIcon: ImageView
    private val prefs by lazy { getSharedPreferences("iptv_prefs", MODE_PRIVATE) }

    private val settingsList = listOf(
        SettingItem("Playlists", "Manage your IPTV playlists and sources.", android.R.drawable.ic_menu_slideshow),
        SettingItem("TV Guide", "EPG sources, update behavior, and guide display.", android.R.drawable.ic_menu_today),
        SettingItem("General", "Configure app behavior and global options.", android.R.drawable.ic_menu_preferences),
        SettingItem("Playback", "Playback, startup, and stream behavior.", android.R.drawable.ic_media_play),
        SettingItem("Channel Sorting", "Choose and configure live channel sort mode.", android.R.drawable.ic_menu_sort_alphabetically),
        SettingItem("Group Sorting", "Reorder channel groups.", android.R.drawable.ic_menu_sort_by_size),
        SettingItem("Appearance", "Themes, colors, and layout options.", android.R.drawable.ic_menu_gallery),
        SettingItem("Remote Control", "Configure remote keys and navigation behavior.", android.R.drawable.ic_menu_directions),
        SettingItem("Parental Control", "Set restrictions and passwords.", android.R.drawable.ic_lock_lock),
        SettingItem("Channel Groups", "Hide or show channel groups.", android.R.drawable.ic_menu_sort_by_size),
        SettingItem("Other", "Data, logs, and maintenance options.", android.R.drawable.ic_menu_manage),
        SettingItem("About", "Version info, licenses, and project details.", android.R.drawable.ic_menu_info_details)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (shouldRequestSettingsPin() && !settingsUnlocked) {
            promptForSettingsPin()
            return
        }
        setContentView(R.layout.activity_settings)

        tvDetailTitle = findViewById(R.id.tvSettingDetailTitle)
        tvDetailDesc = findViewById(R.id.tvSettingDetailDesc)
        ivDetailIcon = findViewById(R.id.ivSettingIconLarge)

        val rvOptions = findViewById<RecyclerView>(R.id.rvSettingsOptions)
        rvOptions.layoutManager = LinearLayoutManager(this)
        
        rvOptions.adapter = SettingsAdapter(settingsList) { item ->
            when (item.title) {
                "Playlists" -> startActivity(Intent(this, PlaylistSettingsActivity::class.java))
                "TV Guide" -> openAdvanced(AdvancedSettingsActivity.Section.EPG.id)
                "General" -> openAdvanced(AdvancedSettingsActivity.Section.GENERAL.id)
                "Playback" -> startActivity(Intent(this, PlaybackSettingsActivity::class.java))
                "Channel Sorting" -> startActivity(Intent(this, ChannelSortingModeActivity::class.java))
                "Group Sorting" -> startActivity(Intent(this, GroupSortingActivity::class.java))
                "Appearance" -> openAdvanced(AdvancedSettingsActivity.Section.APPEARANCE.id)
                "Remote Control" -> openAdvanced(AdvancedSettingsActivity.Section.REMOTE_CONTROL.id)
                "Parental Control" -> openAdvanced(AdvancedSettingsActivity.Section.PARENTAL_CONTROL.id)
                "Channel Groups" -> startActivity(Intent(this, ManageGroupsActivity::class.java))
                "Other" -> openAdvanced(AdvancedSettingsActivity.Section.OTHER.id)
                "About" -> openAdvanced(AdvancedSettingsActivity.Section.ABOUT.id)
            }
        }
        
        updateDetailView(settingsList[0])
    }

    private fun shouldRequestSettingsPin(): Boolean {
        val lockEnabled = prefs.getBoolean("parental_lock_settings", false)
        val pinHash = prefs.getString(KEY_PARENTAL_PIN_HASH, "").orEmpty()
        return lockEnabled && pinHash.isNotBlank()
    }

    private fun promptForSettingsPin() {
        val input = EditText(this).apply {
            hint = "Enter parental PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Settings locked")
            .setCancelable(false)
            .setView(input)
            .setPositiveButton("Unlock") { _, _ ->
                val entered = input.text?.toString()?.trim().orEmpty()
                val expected = prefs.getString(KEY_PARENTAL_PIN_HASH, "").orEmpty()
                if (entered.isNotBlank() && hashPin(entered) == expected) {
                    settingsUnlocked = true
                    recreate()
                } else {
                    Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .show()
    }

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun updateDetailView(item: SettingItem) {
        tvDetailTitle.text = item.title
        tvDetailDesc.text = item.desc
        ivDetailIcon.setImageResource(item.iconRes)
    }

    private fun openAdvanced(section: String) {
        startActivity(Intent(this, AdvancedSettingsActivity::class.java).putExtra(AdvancedSettingsActivity.EXTRA_SECTION, section))
    }

    data class SettingItem(val title: String, val desc: String, val iconRes: Int)

    private inner class SettingsAdapter(
        private val items: List<SettingItem>,
        private val onClick: (SettingItem) -> Unit
    ) : RecyclerView.Adapter<SettingsAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvSettingTitle)
            val ivIcon: ImageView = view.findViewById(R.id.ivSettingIcon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_settings_row, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvTitle.text = item.title
            holder.ivIcon.setImageResource(item.iconRes)
            
            holder.itemView.setOnClickListener { onClick(item) }
            
            holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    updateDetailView(item)
                    holder.itemView.animate().scaleX(1.05f).scaleY(1.05f).setDuration(200).start()
                } else {
                    holder.itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                }
            }
        }

        override fun getItemCount() = items.size
    }

    companion object {
        private const val KEY_PARENTAL_PIN_HASH = "parental_pin_hash"
        private var settingsUnlocked = false
    }
}
