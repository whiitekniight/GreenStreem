package com.example.greenstreem

/** Browse days of data with a bounded four-hour rendering window. */
object EpgGuideRange {
    const val WINDOW_MINUTES = 240
    const val PAGE_MINUTES = 180
    const val DAY_MS = 86_400_000L

    fun daysForIndex(index: Int): Int = intArrayOf(1, 2, 3, 7)[index.coerceIn(0, 3)]

    fun clampStart(requestedMs: Long, nowStartMs: Long, days: Int): Long =
        requestedMs.coerceIn(nowStartMs, nowStartMs + days.coerceIn(1, 7) * DAY_MS - WINDOW_MINUTES * 60_000L)

    fun pageStart(currentMs: Long, forward: Boolean, nowStartMs: Long, days: Int): Long =
        clampStart(currentMs + (if (forward) 1 else -1) * PAGE_MINUTES * 60_000L, nowStartMs, days)
}
