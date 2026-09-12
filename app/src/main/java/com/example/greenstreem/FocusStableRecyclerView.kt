package com.example.greenstreem

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class FocusStableRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    var suppressFocusDrivenScroll: Boolean = false

    override fun requestChildRectangleOnScreen(
        child: View,
        rect: Rect,
        immediate: Boolean
    ): Boolean {
        if (suppressFocusDrivenScroll) return false
        return super.requestChildRectangleOnScreen(child, rect, immediate)
    }
}
