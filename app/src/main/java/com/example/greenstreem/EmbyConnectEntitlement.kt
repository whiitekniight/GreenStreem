package com.example.greenstreem

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

object EmbyConnectEntitlement {
    private const val PREFS = "emby_connect_entitlement"
    private const val KEY_UNLOCKED = "unlocked"
    private const val KEY_ACTIVATION_ID = "activation_id"

    fun isUnlocked(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_UNLOCKED, false)

    fun setUnlocked(context: Context, unlocked: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_UNLOCKED, unlocked)
            .apply()
    }

    fun activationId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_ACTIVATION_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val source = "${context.packageName}:$androidId:emby-connect"
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
        val id = digest.take(8).joinToString("") { "%02X".format(it) }.chunked(4).joinToString("-")
        prefs.edit().putString(KEY_ACTIVATION_ID, id).apply()
        return id
    }
}
