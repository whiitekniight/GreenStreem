package com.example.greenstreem

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        if (BuildConfig.BRANDED_SERVER_LOCKED) {
            startActivity(Intent(this, XtreamLoginActivity::class.java))
            finish()
            return
        }

        findViewById<LinearLayout>(R.id.btnM3u).setOnClickListener {
            startActivity(Intent(this, M3uLoginActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.btnXtream).setOnClickListener {
            startActivity(Intent(this, XtreamLoginActivity::class.java))
        }
    }
}
