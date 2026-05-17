package com.example.greenstreem

import android.app.Activity
import androidx.appcompat.app.AlertDialog

object ProFeatureGate {
    fun isUnlocked(activity: Activity): Boolean = ProEntitlement.isProUnlocked(activity)

    fun showUnlockPrompt(activity: Activity, message: String, finishOnCancel: Boolean = false) {
        AlertDialog.Builder(activity)
            .setTitle("GreenStreem Pro")
            .setMessage(message)
            .setPositiveButton("Unlock Pro") { _, _ -> PlayBillingManager.launchProUnlock(activity) }
            .setNegativeButton("Not now") { _, _ ->
                if (finishOnCancel) activity.finish()
            }
            .setOnCancelListener {
                if (finishOnCancel) activity.finish()
            }
            .show()
    }

    fun require(activity: Activity, message: String): Boolean {
        if (isUnlocked(activity)) return true
        showUnlockPrompt(activity, message, finishOnCancel = true)
        return false
    }
}
