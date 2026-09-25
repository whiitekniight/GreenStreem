package com.example.greenstreem

import android.widget.HorizontalScrollView

class ScrollSync {
    private val rows = mutableSetOf<HorizontalScrollView>()
    var onAnyRowScrolled: ((Int) -> Unit)? = null
    private var currentX = 0
    private var heldX: Int? = null
    private var synchronizing = false

    fun getCurrentX() = currentX

    fun register(row: HorizontalScrollView) { rows.add(row) }
    fun unregister(row: HorizontalScrollView) { rows.remove(row) }

    fun holdHorizontalPosition() { heldX = currentX }
    fun releaseHorizontalPosition() { heldX = null }

    fun scrollAllTo(x: Int) {
        synchronizeTo(heldX ?: x)
    }

    fun notifyRowScrolled(source: HorizontalScrollView, x: Int) {
        if (synchronizing) return
        // Up/Down must not let HorizontalScrollView's focus/layout corrections
        // drag every row to a different time. Left/Right releases this hold.
        synchronizeTo(heldX ?: x)
    }

    private fun synchronizeTo(x: Int) {
        if (synchronizing) return
        synchronizing = true
        try {
            currentX = x.coerceAtLeast(0)
            for (row in rows) {
                if (row.scrollX != currentX) row.scrollTo(currentX, 0)
            }
            onAnyRowScrolled?.invoke(currentX)
        } finally {
            synchronizing = false
        }
    }
}
