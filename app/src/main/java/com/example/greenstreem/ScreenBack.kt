package com.example.greenstreem

import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

internal object ScreenBack {
    fun button(activity: AppCompatActivity) = Button(activity).apply {
        text = "‹ Back"; isAllCaps = false; setTextColor(android.graphics.Color.WHITE)
        backgroundTintList = null; background = AppearanceTheme.buttonBackground(activity)
        setOnClickListener { activity.onBackPressedDispatcher.onBackPressed() }
        contentDescription = "Back to previous screen"
    }
    fun install(activity: AppCompatActivity) {
        val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        if (content.findViewWithTag<View>("screen_back") != null) return
        val page = content.getChildAt(0) ?: return
        content.removeView(page)
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(0xff101820.toInt())
            addView(button(activity).apply { tag = "screen_back" }, LinearLayout.LayoutParams(-2,-2).apply { leftMargin=16; topMargin=8; bottomMargin=8 })
            addView(page, LinearLayout.LayoutParams(-1,0,1f))
        }
        activity.setContentView(column)
    }
}
