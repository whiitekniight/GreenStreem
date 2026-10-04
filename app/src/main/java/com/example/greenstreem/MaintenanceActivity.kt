package com.example.greenstreem

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MaintenanceActivity : Activity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var prefs: SharedPreferences
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        prefs=getSharedPreferences("iptv_prefs",Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)
        render()
    }
    private fun render() {
        if(!prefs.getBoolean("dashboard_maintenance",false)){finish();return}
        val layout=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER;setPadding(64,64,64,64);setBackgroundColor(Color.rgb(12,34,28)) }
        listOf("GreenStreem",prefs.getString("dashboard_maintenance_message","Service maintenance").orEmpty(),prefs.getString("dashboard_maintenance_support","").orEmpty()).forEachIndexed { index,text ->
            layout.addView(TextView(this).apply {this.text=text;textSize=if(index==0)32f else 23f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(0,20,0,20)})
        }
        setContentView(layout)
    }
    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) { if(key?.startsWith("dashboard_maintenance")==true)runOnUiThread { render() } }
    @Deprecated("Maintenance remains visible until the service is available")
    override fun onBackPressed() { moveTaskToBack(true) }
    override fun onDestroy(){prefs.unregisterOnSharedPreferenceChangeListener(this);super.onDestroy()}
}
