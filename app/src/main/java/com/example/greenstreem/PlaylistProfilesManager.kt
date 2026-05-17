package com.example.greenstreem

import android.content.Context
import android.util.Log
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
    var secondaryEpgMode: Int = 0,
    var secondaryEpgUrl: String = "",
    var secondaryEpgUrls: String = ""
)

object PlaylistProfilesManager {
    private const val PREFS = "iptv_prefs"
    private const val KEY_PROFILES_JSON = "playlist_profiles_json"
    private const val KEY_ACTIVE_PROFILE_ID = "active_playlist_profile_id"
    private const val KEY_SECONDARY_EPG_ENABLED = "secondary_epg_enabled"
    private const val KEY_SECONDARY_EPG_MODE = "secondary_epg_mode"
    private const val KEY_SECONDARY_EPG_URL = "secondary_epg_url"
    private const val KEY_SECONDARY_EPG_URLS = "secondary_epg_urls"
    private const val SECONDARY_EPG_MODE_FILL_MISSING = 0
    private const val SECONDARY_EPG_MODE_PROVIDER_ONLY = 3
    private const val TAG = "GreenStreemPlaylist"
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
        if (idx == -1) {
            copyEpgPrefsToProfile(context, profile)
            profiles.add(profile)
        } else {
            profiles[idx] = profile
        }
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
        active.secondaryEpgEnabled = prefs.getBoolean(KEY_SECONDARY_EPG_ENABLED, active.secondaryEpgEnabled)
        active.secondaryEpgMode = prefs.getInt(KEY_SECONDARY_EPG_MODE, active.secondaryEpgMode)
            .coerceIn(SECONDARY_EPG_MODE_FILL_MISSING, SECONDARY_EPG_MODE_PROVIDER_ONLY)
        active.secondaryEpgUrl = prefs.getString(KEY_SECONDARY_EPG_URL, active.secondaryEpgUrl) ?: active.secondaryEpgUrl
        active.secondaryEpgUrls = prefs.getString(KEY_SECONDARY_EPG_URLS, active.secondaryEpgUrls) ?: active.secondaryEpgUrls
        val profiles = loadProfiles(context)
        val idx = profiles.indexOfFirst { it.id == active.id }
        if (idx == -1) {
            profiles.add(active)
        } else {
            profiles[idx] = active
        }
        saveProfiles(context, profiles)
        prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, active.id).apply()
    }

    private fun copyEpgPrefsToProfile(context: Context, profile: PlaylistProfile) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        profile.secondaryEpgEnabled = prefs.getBoolean(KEY_SECONDARY_EPG_ENABLED, profile.secondaryEpgEnabled)
        profile.secondaryEpgMode = prefs.getInt(KEY_SECONDARY_EPG_MODE, profile.secondaryEpgMode)
            .coerceIn(SECONDARY_EPG_MODE_FILL_MISSING, SECONDARY_EPG_MODE_PROVIDER_ONLY)
        profile.secondaryEpgUrl = prefs.getString(KEY_SECONDARY_EPG_URL, profile.secondaryEpgUrl) ?: profile.secondaryEpgUrl
        profile.secondaryEpgUrls = prefs.getString(KEY_SECONDARY_EPG_URLS, profile.secondaryEpgUrls) ?: profile.secondaryEpgUrls
    }

    fun applyProfileToLegacyKeys(context: Context, profile: PlaylistProfile) {
        val xtreamM3uCredentials = if (profile.username.isBlank() && profile.password.isBlank()) {
            XtreamM3uUrl.parse(profile.serverUrl)
        } else {
            null
        }
        val effectiveServerUrl = xtreamM3uCredentials?.serverUrl ?: profile.serverUrl
        val effectiveUsername = xtreamM3uCredentials?.username ?: profile.username
        val effectivePassword = xtreamM3uCredentials?.password ?: profile.password
        val isXtream = effectiveUsername.isNotBlank() && effectivePassword.isNotBlank()

        if (xtreamM3uCredentials != null) {
            Log.i(TAG, "promoting saved M3U profile to Xtream API mode host=${xtreamM3uCredentials.serverUrl}")
            profile.serverUrl = effectiveServerUrl
            profile.username = effectiveUsername
            profile.password = effectivePassword
            saveProfiles(context, loadProfiles(context).map {
                if (it.id == profile.id) profile else it
            })
        }

        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("playlist_name", profile.name)
            .putString("server_url", effectiveServerUrl)
            .putString("username", effectiveUsername)
            .putString("password", effectivePassword)
            .putBoolean("has_playlist", true)
            .putBoolean("playlist_enabled", profile.enabled)
            .putBoolean("playlist_update_on_start", profile.updateOnStart)
            .putInt("playlist_update_interval_hours", profile.updateIntervalHours)
            .putInt("playlist_logo_priority", profile.logoPriority)
            .putBoolean(KEY_SECONDARY_EPG_ENABLED, profile.secondaryEpgEnabled)
            .putInt(
                KEY_SECONDARY_EPG_MODE,
                profile.secondaryEpgMode.coerceIn(SECONDARY_EPG_MODE_FILL_MISSING, SECONDARY_EPG_MODE_PROVIDER_ONLY)
            )
            .putString(KEY_SECONDARY_EPG_URL, profile.secondaryEpgUrl)
            .putString(KEY_SECONDARY_EPG_URLS, profile.secondaryEpgUrls.ifBlank { profile.secondaryEpgUrl })
        if (isXtream) {
            editor.putString("playlist_type", "xtream")
                .remove("m3u_url")
                .putString(
                    "player_live_stream_format",
                    if (xtreamM3uCredentials?.output.equals("m3u8", ignoreCase = true)) "hls" else "ts"
                )
        } else {
            editor.putString("playlist_type", "m3u")
                .putString("m3u_url", profile.serverUrl)
        }
        editor.apply()
        XtreamManager.initFromPrefs(context)
    }
}
