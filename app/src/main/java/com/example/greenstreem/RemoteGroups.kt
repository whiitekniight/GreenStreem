package com.example.greenstreem

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object RemoteGroups {
    const val PREFIX = "dashboard-group:"
    private fun key(context: Context, name: String) = "dashboard_${name}_${PlaylistProfilesManager.getActiveProfileId(context) ?: "default"}"
    fun read(context: Context, name: String): JSONArray = runCatching {
        JSONArray(context.getSharedPreferences("iptv_prefs",Context.MODE_PRIVATE).getString(key(context,name),"[]"))
    }.getOrElse { JSONArray() }
    fun validate(name: String, rows: JSONArray) {
        require(rows.length() <= 500) { "Too many groups" }
        if(name == "customGroups") for(i in 0 until rows.length()) {
            val row=rows.getJSONObject(i)
            require(row.getString("id").startsWith(PREFIX) && row.getString("name").length in 1..120)
            val channels=row.getJSONArray("channels")
            require(channels.length() <= 10000)
            for(j in 0 until channels.length()) require(channels.get(j) is Number)
        }
        if(name=="pinnedGroups") for(i in 0 until rows.length()) require(rows.get(i) is String)
    }
    fun save(context: Context, name: String, rows: JSONArray) {
        validate(name,rows)
        check(context.getSharedPreferences("iptv_prefs",Context.MODE_PRIVATE).edit().putString(key(context,name),rows.toString()).commit())
    }
    fun categories(context: Context, original: List<XtreamCategory>): List<XtreamCategory> {
        val rows=read(context,"customGroups")
        val custom=(0 until rows.length()).map { rows.getJSONObject(it) }.map { XtreamCategory(it.getString("id"),it.getString("name"),0) }
        val pins=read(context,"pinnedGroups")
        val positions=(0 until pins.length()).associate { pins.getString(it) to it }
        return (custom+original).sortedBy { positions[it.id] ?: Int.MAX_VALUE }
    }
    fun channels(context: Context, id: String): Set<Long> {
        val rows=read(context,"customGroups")
        val row=(0 until rows.length()).map { rows.getJSONObject(it) }.firstOrNull { it.optString("id")==id } ?: return emptySet()
        val ids=row.getJSONArray("channels")
        return (0 until ids.length()).map { ids.getLong(it) }.toSet()
    }
}
