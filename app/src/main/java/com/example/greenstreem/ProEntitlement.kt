package com.example.greenstreem

import android.content.Context

object ProEntitlement {
    const val PRODUCT_ID_PRO_UNLOCK = "greenstreem_pro_unlock"
    const val PRODUCT_ID_PRO_MONTHLY = "greenstreem_pro_monthly"

    val PRO_PRODUCT_IDS = setOf(
        PRODUCT_ID_PRO_UNLOCK,
        PRODUCT_ID_PRO_MONTHLY
    )

    private const val PREFS = "iptv_prefs"
    private const val KEY_PRO_UNLOCKED = "pro_unlocked"

    fun isProUnlocked(context: Context): Boolean {
        if (BuildConfig.SIDELOAD_PRO_UNLOCKED) return true
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PRO_UNLOCKED, false)
    }

    fun setProUnlocked(context: Context, unlocked: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PRO_UNLOCKED, unlocked)
            .apply()
    }
}
