package com.example.greenstreem

import org.junit.Assert.*
import org.junit.Test

class EpgGuideRangeTest {
    private val now = 1_790_000_000_000L

    @Test fun browsingCanReachTheEndOfEachConfiguredRange() {
        for (days in listOf(1, 2, 3, 7)) {
            var position = now
            repeat(80) { position = EpgGuideRange.pageStart(position, true, now, days) }
            assertEquals(now + days * EpgGuideRange.DAY_MS,
                position + EpgGuideRange.WINDOW_MINUTES * 60_000L)
            assertEquals(position, EpgGuideRange.pageStart(position, true, now, days))
        }
    }

    @Test fun adjacentPagesOverlapWithoutLosingScheduleTime() {
        val next = EpgGuideRange.pageStart(now, true, now, 7)
        assertEquals(3 * 3_600_000L, next - now)
        assertTrue(next < now + EpgGuideRange.WINDOW_MINUTES * 60_000L)
        assertEquals(now, EpgGuideRange.pageStart(next, false, now, 7))
    }

    @Test fun dayPickerTargetsAreBoundedWithoutTurningOldTimesIntoFutureDates() {
        assertEquals(now, EpgGuideRange.clampStart(now - EpgGuideRange.DAY_MS, now, 3))
        assertEquals(now + 2 * EpgGuideRange.DAY_MS,
            EpgGuideRange.clampStart(now + 2 * EpgGuideRange.DAY_MS, now, 3))
        assertEquals(now + 3 * EpgGuideRange.DAY_MS - 4 * 3_600_000L,
            EpgGuideRange.clampStart(now + 30 * EpgGuideRange.DAY_MS, now, 3))
    }

    @Test fun savedChoicesMapToDaysAndInvalidValuesStayBounded() {
        assertEquals(listOf(1, 2, 3, 7), (0..3).map(EpgGuideRange::daysForIndex))
        assertEquals(1, EpgGuideRange.daysForIndex(-1))
        assertEquals(7, EpgGuideRange.daysForIndex(99))
    }
}
