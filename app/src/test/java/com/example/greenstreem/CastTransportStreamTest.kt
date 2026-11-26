package com.example.greenstreem

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class CastTransportStreamTest {
    private fun payload(packet: ByteArray): ByteArray {
        val start=if(packet[3].toInt() and 0x20!=0) 5+(packet[4].toInt() and 255) else 4
        return packet.copyOfRange(start,188)
    }
    @Test fun programTablesHaveValidLengthsAndCrc() {
        val out=ByteArrayOutputStream();CastTransportStream(out).tables()
        val bytes=out.toByteArray();assertEquals(376,bytes.size)
        for(offset in listOf(0,188)) {
            val section=payload(bytes.copyOfRange(offset,offset+188)).drop(1)
            val length=((section[1].toInt() and 15) shl 8)+(section[2].toInt() and 255)
            assertEquals(section.size,3+length)
            var crc=-1
            section.forEach { b -> crc=crc xor ((b.toInt() and 255) shl 24);repeat(8) { crc=if(crc<0) (crc shl 1) xor 0x04c11db7 else crc shl 1 } }
            assertEquals(0,crc)
        }
    }
    @Test fun packetizationPreservesVideoAndAudioAndContinuity() {
        for(video in listOf(true,false)) for(size in listOf(1,169,170,175,176,183,184,185,4000)) {
            val out=ByteArrayOutputStream();val source=ByteArray(size) { (it*7).toByte() }
            CastTransportStream(out).sample(video,source,1_234_567)
            val bytes=out.toByteArray();assertEquals(0,bytes.size%188)
            val restored=ByteArrayOutputStream()
            for(offset in bytes.indices step 188) {
                val packet=bytes.copyOfRange(offset,offset+188)
                assertEquals(0x47,packet[0].toInt() and 255)
                assertEquals(offset/188%16,packet[3].toInt() and 15)
                restored.write(payload(packet))
            }
            assertArrayEquals(source,restored.toByteArray().drop(14).toByteArray())
        }
    }
}
