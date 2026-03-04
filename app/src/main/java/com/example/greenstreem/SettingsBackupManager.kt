package com.example.greenstreem

import android.content.Context
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object SettingsBackupManager {

    private const val PREFS_NAME = "iptv_prefs"
    private const val BACKUP_DIR = "backups"

    suspend fun backupNow(context: Context): Result<File> = runCatching {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val db = AppDatabase.getDatabase(context)

        val root = JSONObject()
        root.put("schema", 1)
        root.put("createdAtMs", System.currentTimeMillis())
        root.put("packageName", context.packageName)

        val prefsArray = JSONArray()
        prefs.all.forEach { (key, raw) ->
            val entry = JSONObject().put("k", key)
            when (raw) {
                is Boolean -> entry.put("t", "bool").put("v", raw)
                is Int -> entry.put("t", "int").put("v", raw)
                is Long -> entry.put("t", "long").put("v", raw)
                is Float -> entry.put("t", "float").put("v", raw.toDouble())
                is String -> entry.put("t", "string").put("v", raw)
                is Set<*> -> {
                    val list = raw.filterIsInstance<String>()
                    entry.put("t", "string_set").put("v", JSONArray(list))
                }
                else -> return@forEach
            }
            prefsArray.put(entry)
        }
        root.put("prefs", prefsArray)

        val favorites = db.favoriteDao().getAll().first()
        root.put("favorites", JSONArray().apply {
            favorites.forEach { f ->
                put(JSONObject()
                    .put("streamId", f.streamId)
                    .put("name", f.name)
                    .put("streamIcon", f.streamIcon)
                    .put("epgId", f.epgId)
                    .put("categoryId", f.categoryId))
            }
        })

        val hiddenGroups = db.groupDao().getAllHidden().first()
        root.put("hiddenGroups", JSONArray().apply {
            hiddenGroups.forEach { g ->
                put(JSONObject().put("groupId", g.groupId).put("name", g.name))
            }
        })

        val hiddenChannels = db.hiddenChannelDao().getAllHidden().first()
        root.put("hiddenChannels", JSONArray().apply {
            hiddenChannels.forEach { c ->
                put(JSONObject().put("channelId", c.channelId).put("name", c.name))
            }
        })

        val groupOrder = db.groupOrderDao().getAllOrder().first()
        root.put("groupOrder", JSONArray().apply {
            groupOrder.forEach { g ->
                put(JSONObject().put("groupId", g.groupId).put("position", g.position))
            }
        })

        val allChannelOrder = db.channelOrderDao().getAllOrder().first()
        root.put("channelOrder", JSONArray().apply {
            allChannelOrder.forEach { c ->
                put(
                    JSONObject()
                        .put("channelId", c.channelId)
                        .put("groupId", c.groupId)
                        .put("position", c.position)
                )
            }
        })

        val backupDir = File(context.getExternalFilesDir(null), BACKUP_DIR).apply { mkdirs() }
        val out = File(backupDir, "greenstreem-backup-${System.currentTimeMillis()}.json")
        out.writeText(root.toString(2))
        out
    }

    suspend fun restoreLatest(context: Context): Result<File> = runCatching {
        val file = listBackupFiles(context).firstOrNull()
            ?: error("No backup file found")
        restoreFromFile(context, file)
        file
    }

    suspend fun restoreFile(context: Context, file: File): Result<File> = runCatching {
        if (!file.exists() || !file.isFile) error("Backup file not found")
        restoreFromFile(context, file)
        file
    }

    fun listBackupFiles(context: Context): List<File> {
        val backupDir = File(context.getExternalFilesDir(null), BACKUP_DIR)
        return backupDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }

    fun backupDirectory(context: Context): File {
        return File(context.getExternalFilesDir(null), BACKUP_DIR).apply { mkdirs() }
    }

    private suspend fun restoreFromFile(context: Context, file: File) {
        val root = JSONObject(file.readText())
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val db = AppDatabase.getDatabase(context)

        prefs.edit().clear().apply()
        val prefsArray = root.optJSONArray("prefs") ?: JSONArray()
        val edit = prefs.edit()
        for (i in 0 until prefsArray.length()) {
            val item = prefsArray.optJSONObject(i) ?: continue
            val key = item.optString("k", "")
            if (key.isBlank()) continue
            when (item.optString("t", "")) {
                "bool" -> edit.putBoolean(key, item.optBoolean("v", false))
                "int" -> edit.putInt(key, item.optInt("v", 0))
                "long" -> edit.putLong(key, item.optLong("v", 0L))
                "float" -> edit.putFloat(key, item.optDouble("v", 0.0).toFloat())
                "string" -> edit.putString(key, item.optString("v", ""))
                "string_set" -> {
                    val arr = item.optJSONArray("v") ?: JSONArray()
                    val set = mutableSetOf<String>()
                    for (j in 0 until arr.length()) {
                        set.add(arr.optString(j, ""))
                    }
                    edit.putStringSet(key, set)
                }
            }
        }
        edit.apply()

        db.favoriteDao().clearAll()
        db.groupDao().clearAll()
        db.hiddenChannelDao().clearAll()
        db.groupOrderDao().clearAllOrder()
        db.channelOrderDao().clearAll()

        val favorites = mutableListOf<Favorite>()
        val favoritesJson = root.optJSONArray("favorites") ?: JSONArray()
        for (i in 0 until favoritesJson.length()) {
            val item = favoritesJson.optJSONObject(i) ?: continue
            favorites.add(
                Favorite(
                    streamId = item.optInt("streamId"),
                    name = item.optString("name"),
                    streamIcon = if (item.has("streamIcon") && !item.isNull("streamIcon")) item.optString("streamIcon") else null,
                    epgId = if (item.has("epgId") && !item.isNull("epgId")) item.optString("epgId") else null,
                    categoryId = if (item.has("categoryId") && !item.isNull("categoryId")) item.optString("categoryId") else null
                )
            )
        }
        if (favorites.isNotEmpty()) db.favoriteDao().insertAll(favorites)

        val hiddenGroups = mutableListOf<HiddenGroup>()
        val hiddenGroupsJson = root.optJSONArray("hiddenGroups") ?: JSONArray()
        for (i in 0 until hiddenGroupsJson.length()) {
            val item = hiddenGroupsJson.optJSONObject(i) ?: continue
            hiddenGroups.add(HiddenGroup(item.optString("groupId"), item.optString("name")))
        }
        if (hiddenGroups.isNotEmpty()) db.groupDao().hideGroups(hiddenGroups)

        val hiddenChannels = mutableListOf<HiddenChannel>()
        val hiddenChannelsJson = root.optJSONArray("hiddenChannels") ?: JSONArray()
        for (i in 0 until hiddenChannelsJson.length()) {
            val item = hiddenChannelsJson.optJSONObject(i) ?: continue
            hiddenChannels.add(HiddenChannel(item.optLong("channelId"), item.optString("name")))
        }
        if (hiddenChannels.isNotEmpty()) db.hiddenChannelDao().hideChannels(hiddenChannels)

        val groupOrder = mutableListOf<GroupOrder>()
        val groupOrderJson = root.optJSONArray("groupOrder") ?: JSONArray()
        for (i in 0 until groupOrderJson.length()) {
            val item = groupOrderJson.optJSONObject(i) ?: continue
            groupOrder.add(GroupOrder(item.optString("groupId"), item.optInt("position")))
        }
        if (groupOrder.isNotEmpty()) db.groupOrderDao().saveOrder(groupOrder)

        val channelOrder = mutableListOf<ChannelOrder>()
        val channelOrderJson = root.optJSONArray("channelOrder") ?: JSONArray()
        for (i in 0 until channelOrderJson.length()) {
            val item = channelOrderJson.optJSONObject(i) ?: continue
            channelOrder.add(
                ChannelOrder(
                    channelId = item.optLong("channelId"),
                    groupId = item.optString("groupId"),
                    position = item.optInt("position")
                )
            )
        }
        if (channelOrder.isNotEmpty()) db.channelOrderDao().saveOrder(channelOrder)

        XtreamManager.initFromPrefs(context)
        PlaylistProfilesManager.syncLegacyKeysToActive(context)
    }
}
