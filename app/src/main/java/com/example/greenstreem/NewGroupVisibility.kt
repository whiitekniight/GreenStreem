package com.example.greenstreem

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

internal object NewGroupVisibilityPolicy {
    fun newlyHidden(current: Set<String>, known: Set<String>?, autoShow: Boolean): Set<String> =
        if (autoShow || known == null) emptySet() else current - known
}

/** Remember every group seen for each provider, including groups temporarily removed by it. */
object NewGroupVisibility {
    const val KEY_AUTO_SHOW = "auto_show_new_groups"
    private val mutex = Mutex()

    fun autoShow(context: Context): Boolean =
        context.getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE).getBoolean(KEY_AUTO_SHOW, true)

    fun setAutoShow(context: Context, enabled: Boolean) {
        context.getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTO_SHOW, enabled).apply()
    }

    suspend fun recordGroups(context: Context, categories: List<XtreamCategory>) {
        if (categories.isEmpty()) return // An empty/failed response must not establish a baseline.
        mutex.withLock {
            val prefs = context.getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
            val identity = if (prefs.getString("playlist_type", "xtream") == "m3u") {
                "m3u|${prefs.getString("m3u_url", "") }"
            } else {
                "xtream|${prefs.getString("server_url", "")?.trimEnd('/')}|${prefs.getString("username", "") }"
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
                .joinToString("") { "%02x".format(it) }
            val key = "known_live_groups_$digest"
            val known = prefs.getStringSet(key, null)?.toSet()
            val current = categories.map { it.id }.toSet()
            val hidden = NewGroupVisibilityPolicy.newlyHidden(current, known, autoShow(context))
            if (hidden.isNotEmpty()) {
                AppDatabase.getDatabase(context).groupDao().hideGroups(
                    categories.filter { it.id in hidden }.map { HiddenGroup(it.id, it.name) }
                )
            }
            prefs.edit().putStringSet(key, known.orEmpty() + current).apply()
        }
    }
}
