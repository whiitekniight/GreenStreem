package com.example.greenstreem

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingByteRangeTest {
    @Test fun fullAndSeekRanges() {
        assertEquals(RecordingByteRange(0,100),RecordingByteRange.parse(null,100))
        assertEquals(RecordingByteRange(20,80),RecordingByteRange.parse("bytes=20-",100))
        assertEquals(RecordingByteRange(20,11),RecordingByteRange.parse("bytes=20-30",100))
        assertEquals(RecordingByteRange(20,80),RecordingByteRange.parse("bytes=20-999",100))
    }
    @Test fun suffixAndEmptyFiles() {
        assertEquals(RecordingByteRange(90,10),RecordingByteRange.parse("bytes=-10",100))
        assertEquals(RecordingByteRange(0,100),RecordingByteRange.parse("bytes=-999",100))
        assertEquals(RecordingByteRange(0,0),RecordingByteRange.parse(null,0))
    }
    @Test fun invalidRangesAreRejected() {
        listOf("bytes=100-","bytes=40-20","bytes=-0","bytes=-","bytes=0-1,4-5","bytes=9223372036854775808-","garbage").forEach {
            check(runCatching { RecordingByteRange.parse(it,100) }.isFailure) { it }
        }
        check(runCatching { RecordingByteRange.parse("bytes=0-",0) }.isFailure)
    }
}
