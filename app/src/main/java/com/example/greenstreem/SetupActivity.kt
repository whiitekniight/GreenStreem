package com.example.greenstreem

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        findViewById<LinearLayout>(R.id.btnM3u).setOnClickListener {
            Toast.makeText(this, "M3U setup coming soon", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.btnXtream).setOnClickListener {
            startActivity(Intent(this, XtreamLoginActivity::class.java))
        }
    }
}
