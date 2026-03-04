package com.example.greenstreem

import android.widget.HorizontalScrollView

class ScrollSync {
    private val rows = mutableSetOf<HorizontalScrollView>()
    var onAnyRowScrolled: ((Int) -> Unit)? = null
    private var currentX = 0

    fun getCurrentX() = currentX

    fun register(row: HorizontalScrollView) { rows.add(row) }
    fun unregister(row: HorizontalScrollView) { rows.remove(row) }

    fun scrollAllTo(x: Int) {
        currentX = x
        for (r in rows) { if (r.scrollX != x) r.scrollTo(x, 0) }
    }

    fun notifyRowScrolled(source: HorizontalScrollView, x: Int) {
        currentX = x
        for (r in rows) { if (r !== source && r.scrollX != x) r.scrollTo(x, 0) }
        onAnyRowScrolled?.invoke(x)
    }
}
