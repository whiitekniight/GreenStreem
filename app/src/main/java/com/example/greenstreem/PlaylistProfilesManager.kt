package com.example.greenstreem

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class PlaylistProfile(
    val id: String,
    var name: String,
    var serverUrl: String,
    var username: String,
    var password: String,
    var enabled: Boolean = true,
    var updateOnStart: Boolean = true,
    var updateIntervalHours: Int = 12,
    var logoPriority: Int = 0,
    var secondaryEpgEnabled: Boolean = false,
    var secondaryEpgUrl: String = ""
)

object PlaylistProfilesManager {
    private const val PREFS = "iptv_prefs"
    private const val KEY_PROFILES_JSON = "playlist_profiles_json"
    private const val KEY_ACTIVE_PROFILE_ID = "active_playlist_profile_id"
    private val gson = Gson()

    fun loadProfiles(context: Context): MutableList<PlaylistProfile> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PROFILES_JSON, null).orEmpty()
        if (raw.isBlank()) return mutableListOf()
        val type = object : TypeToken<MutableList<PlaylistProfile>>() {}.type
        return runCatching { gson.fromJson<MutableList<PlaylistProfile>>(raw, type) }.getOrDefault(mutableListOf())
    }

    fun saveProfiles(context: Context, profiles: List<PlaylistProfile>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROFILES_JSON, gson.toJson(profiles))
            .apply()
    }

    fun getActiveProfileId(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACTIVE_PROFILE_ID, null)
    }

    fun getActiveProfile(context: Context): PlaylistProfile? {
        val profiles = loadProfiles(context)
        val activeId = getActiveProfileId(context)
        return profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
    }

    fun setActiveProfile(context: Context, profileId: String) {
        val profile = loadProfiles(context).firstOrNull { it.id == profileId } ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_PROFILE_ID, profile.id)
            .apply()
        applyProfileToLegacyKeys(context, profile)
    }

    fun upsertProfileAndActivate(context: Context, profile: PlaylistProfile) {
        val profiles = loadProfiles(context)
        val idx = profiles.indexOfFirst { it.id == profile.id }
        if (idx == -1) profiles.add(profile) else profiles[idx] = profile
        saveProfiles(context, profiles)
        setActiveProfile(context, profile.id)
    }

    fun createProfile(
        name: String,
        url: String,
        username: String,
        password: String
    ): PlaylistProfile {
        return PlaylistProfile(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Playlist" },
            serverUrl = url,
            username = username,
            password = password
        )
    }

    fun deleteProfile(context: Context, profileId: String) {
        val profiles = loadProfiles(context).filterNot { it.id == profileId }
        saveProfiles(context, profiles)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val currentActive = prefs.getString(KEY_ACTIVE_PROFILE_ID, null)
        if (currentActive == profileId) {
            val newActive = profiles.firstOrNull()
            prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, newActive?.id).apply()
            if (newActive != null) {
                applyProfileToLegacyKeys(context, newActive)
            } else {
                prefs.edit().putBoolean("has_playlist", false).apply()
            }
        }
    }

    fun syncLegacyKeysToActive(context: Context) {
        val active = getActiveProfile(context) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        active.name = prefs.getString("playlist_name", active.name) ?: active.name
        active.serverUrl = prefs.getString("server_url", active.serverUrl) ?: active.serverUrl
        active.username = prefs.getString("username", active.username) ?: active.username
        active.password = prefs.getString("password", active.password) ?: active.password
        active.enabled = prefs.getBoolean("playlist_enabled", active.enabled)
        active.updateOnStart = prefs.getBoolean("playlist_update_on_start", active.updateOnStart)
        active.updateIntervalHours = prefs.getInt("playlist_update_interval_hours", active.updateIntervalHours)
        active.logoPriority = prefs.getInt("playlist_logo_priority", active.logoPriority)
        active.secondaryEpgEnabled = prefs.getBoolean("secondary_epg_enabled", active.secondaryEpgEnabled)
        active.secondaryEpgUrl = prefs.getString("secondary_epg_url", active.secondaryEpgUrl) ?: active.secondaryEpgUrl
        upsertProfileAndActivate(context, active)
    }

    fun applyProfileToLegacyKeys(context: Context, profile: PlaylistProfile) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("playlist_name", profile.name)
            .putString("server_url", profile.serverUrl)
            .putString("username", profile.username)
            .putString("password", profile.password)
            .putBoolean("has_playlist", true)
            .putBoolean("playlist_enabled", profile.enabled)
            .putBoolean("playlist_update_on_start", profile.updateOnStart)
            .putInt("playlist_update_interval_hours", profile.updateIntervalHours)
            .putInt("playlist_logo_priority", profile.logoPriority)
            .putBoolean("secondary_epg_enabled", profile.secondaryEpgEnabled)
            .putString("secondary_epg_url", profile.secondaryEpgUrl)
            .apply()
        XtreamManager.initFromPrefs(context)
    }
}
