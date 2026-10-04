package com.example.greenstreem

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Public management preferences. Credentials and entitlement flags are never exposed. */
object RemoteManagementSettings {
    data class Setting(val key: String, val title: String, val section: String, val default: Any, val choices: List<Any> = emptyList())
    val settings = listOf(
        Setting("autoplay_last_channel", "Autoplay last channel", "general", true),
        Setting("player_auto_next_episode", "Play next episode automatically", "general", true),
        Setting("player_aspect_mode", "Aspect ratio (0 Fit, 3 Fill, 4 Zoom)", "general", 3, listOf(0,3,4)),
        Setting("player_buffer_size_sec", "Playback buffer (seconds)", "general", 60, listOf(0,3,5,10,15,30,60,120)),
        Setting("player_audio_offset_ms", "Audio offset (milliseconds)", "general", 0, listOf(-2000,-1000,-500,0,500,1000,2000)),
        Setting("player_live_stream_format", "Live stream format", "general", "ts", listOf("ts","hls")),
        Setting("player_movie_player_mode", "Movie player", "general", "built_in", listOf("built_in","external")),
        Setting("player_video_decoder", "Video decoder (0 Auto, 1 Hardware, 2 Software)", "general", 0, listOf(0,1,2)),
        Setting("player_audio_decoder", "Audio decoder (0 Auto, 1 Hardware, 2 Software)", "general", 0, listOf(0,1,2)),
        Setting("player_audio_passthrough", "Audio passthrough", "general", false),
        Setting("player_tunneled_playback", "Tunneled playback", "general", false),
        Setting("general_confirm_exit", "Confirm app exit", "general", false),
        Setting("general_show_clock", "Show clock", "general", true),
        Setting("general_show_date_clock", "Show date", "general", false),
        Setting("general_time_format", "Time format (0 12-hour, 1 24-hour)", "general", 0, listOf(0,1)),
        Setting("epg_time_offset", "EPG offset (0 -2h, 1 -1h, 2 -30m, 3 Default, 4 +30m, 5 +1h, 6 +2h)", "general", 3, listOf(0,1,2,3,4,5,6)),
        Setting("appearance_show_logos", "Show channel logos", "advanced", true),
        Setting("appearance_channel_prefix_mode", "Channel prefixes (0 Off, 1 Hide, 2 Add)", "advanced", 0, listOf(0,1,2)),
        Setting("appearance_channel_prefix_values", "Channel prefixes (comma separated)", "advanced", ""),
        Setting("remote_menu_quick_panel", "Menu opens quick panel", "advanced", true),
        Setting("remote_channel_keys_zap", "Channel keys change channels", "advanced", true),
        Setting("remote_guide_toggle", "Guide key toggles guide", "advanced", true),
        Setting("remote_long_ok_options", "Long OK opens channel options", "advanced", true),
        Setting("remote_info_cycles_aspect", "Info key cycles aspect ratio", "advanced", true)
    ) + listOf("Guide day / time", "Channel options", "Cycle aspect ratio", "Video quality", "Audio tracks", "Subtitle tracks", "Sleep timer").mapIndexed { index, title ->
        Setting("dashboard_quick_$index", title, "quick", true)
    } + listOf("Guide day / time", "Favorites", "EPG channel finder / override", "Manage Channel Visibility", "Hide Channel", "Show all hidden channels").mapIndexed { index, title ->
        Setting("dashboard_channel_$index", title, "group-menu", true)
    }

    fun snapshot(context: Context): JSONObject {
        val prefs = context.getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
        return JSONObject().put("schema", JSONArray().apply {
            settings.forEach { s -> put(JSONObject().put("key",s.key).put("title",s.title).put("section",s.section)
                .put("type",when(s.default) { is Boolean -> "boolean"; is Int -> "integer"; else -> "string" })
                .put("choices",JSONArray(s.choices))) }
        }).put("values",JSONObject().apply { settings.forEach { s -> put(s.key, prefs.all[s.key] ?: s.default) } })
    }

    fun validateSetting(key: String, value: Any) {
        val spec = settings.firstOrNull { it.key == key } ?: error("Unsupported setting: $key")
        require(when(spec.default) { is Boolean -> value is Boolean; is Int -> value is Int; else -> value is String && value.length <= 2000 }) { "Invalid setting: $key" }
        require(spec.choices.isEmpty() || value in spec.choices) { "Invalid choice: $key" }
    }

    fun validate(values: JSONObject) {
        values.keys().forEach { key -> validateSetting(key,values.get(key)) }
    }

    fun apply(context: Context, values: JSONObject) {
        validate(values)
        val editor = context.getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE).edit()
        values.keys().forEach { key -> when(val value=values.get(key)) {
            is Boolean -> editor.putBoolean(key,value)
            is Int -> editor.putInt(key,value)
            is String -> editor.putString(key,value)
        } }
        check(editor.putBoolean("groups_changed",true).commit()) { "Could not save settings" }
    }
}
