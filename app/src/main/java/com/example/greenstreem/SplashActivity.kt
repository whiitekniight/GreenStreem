package com.example.greenstreem

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val savedMode = getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
            .getString(KEY_UI_MODE, null)
        if (savedMode == UI_MODE_TV || savedMode == UI_MODE_MOBILE) {
            continueToNextScreen()
            return
        }

        setContentView(R.layout.activity_splash)

        findViewById<LinearLayout>(R.id.btnModeTv).setOnClickListener {
            saveUiMode(UI_MODE_TV)
            continueToNextScreen()
        }
        findViewById<LinearLayout>(R.id.btnModeMobile).setOnClickListener {
            saveUiMode(UI_MODE_MOBILE)
            continueToNextScreen()
        }
    }

    private fun saveUiMode(mode: String) {
        val prefs = getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_UI_MODE, mode).apply()
    }

    private fun continueToNextScreen() {
        val prefs = getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
        val hasPlaylist = prefs.getBoolean("has_playlist", false)
        if (hasPlaylist) {
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            startActivity(Intent(this, SetupActivity::class.java))
        }
        finish()
    }

    companion object {
        const val KEY_UI_MODE = "ui_mode"
        const val UI_MODE_TV = "tv"
        const val UI_MODE_MOBILE = "mobile"
    }
}
