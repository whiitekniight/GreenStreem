package com.example.greenstreem

import android.content.Context

internal object BrandedServerMigration {
    fun apply(context: Context) {
        if (!BuildConfig.BRANDED_SERVER_LOCKED) return
        fun migrate(value: String) = BrandedServerAddress.migrate(value, true, BuildConfig.BRANDED_SERVER_URL)
        val prefs = context.getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        var changed = false
        for (key in listOf("server_url", "m3u_url")) {
            val old = prefs.getString(key, null) ?: continue
            val next = migrate(old)
            if (next != old) { editor.putString(key, next); changed = true }
        }
        if (changed) editor.apply()
        val profiles = PlaylistProfilesManager.loadProfiles(context)
        var profilesChanged = false
        for (profile in profiles) {
            val next = migrate(profile.serverUrl)
            if (next != profile.serverUrl) { profile.serverUrl = next; profilesChanged = true }
        }
        if (profilesChanged) PlaylistProfilesManager.saveProfiles(context, profiles)
    }
}
