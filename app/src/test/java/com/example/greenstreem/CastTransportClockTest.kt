package com.example.greenstreem

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class CastTransportClockTest {
    @Test fun retainsDecodeTimeAcrossFragmentedNetworkReads() {
        val out=ByteArrayOutputStream()
        CastTransportStream(out).sample(true,ByteArray(500) { it.toByte() },3_000_000,2_933_333)
        val bytes=out.toByteArray();val clock=CastTransportClock()
        var offset=0
        while(offset<bytes.size) { val size=minOf(17,bytes.size-offset);clock.consume(bytes,offset,size);offset+=size }
        assertEquals(2_933_333.0,clock.decodeTime(3_000_000,-1_000_000)!!.toDouble(),12.0)
        assertNull(clock.decodeTime(3_000_000,-1_000_000))
    }
    @Test fun presentationOnlyStreamsDoNotInventDecodeTime() {
        val out=ByteArrayOutputStream();CastTransportStream(out).sample(true,ByteArray(20),3_000_000)
        val bytes=out.toByteArray();val clock=CastTransportClock();clock.consume(bytes,0,bytes.size)
        assertNull(clock.decodeTime(3_000_000,-1_000_000))
    }
    @Test fun decodeTimeSurvivesThe33BitTransportClockWrap() {
        val wrap=0x200000000L
        val sourceTime=(wrap*1_000_000+89_999)/90_000-1_000_000+1000
        val out=ByteArrayOutputStream();CastTransportStream(out).sample(true,ByteArray(20),sourceTime,sourceTime-66_667)
        val bytes=out.toByteArray();val clock=CastTransportClock();clock.consume(bytes,0,bytes.size)
        val rawPts=((sourceTime*90/1000)+90_000) and (wrap-1)
        val offset=100_000-rawPts*1_000_000/90_000
        assertEquals(33_333.0,clock.decodeTime(100_000,offset)!!.toDouble(),20.0)
    }
}
