package com.example.greenstreem

import java.io.ByteArrayOutputStream

/** Retains PES decode timestamps which TrackOutput's presentation-only API does not expose. */
internal class CastTransportClock {
    private val packet=ByteArray(188)
    private var filled=0
    private val headers=mutableMapOf<Int,ByteArrayOutputStream>()
    private val clocks=linkedMapOf<Long,Long>()
    fun consume(bytes: ByteArray,offset: Int,length: Int) {
        var position=offset
        val end=offset+length
        while(position<end) {
            if(filled==0 && bytes[position]!=0x47.toByte()) { position++;continue }
            val count=minOf(188-filled,end-position)
            bytes.copyInto(packet,filled,position,position+count);filled+=count;position+=count
            if(filled==188) { consumePacket();filled=0 }
        }
    }
    private fun stamp(bytes: ByteArray,offset: Int): Long =
        (((bytes[offset].toLong() shr 1) and 7) shl 30) or
            ((bytes[offset+1].toLong() and 255) shl 22) or
            (((bytes[offset+2].toLong() shr 1) and 127) shl 15) or
            ((bytes[offset+3].toLong() and 255) shl 7) or ((bytes[offset+4].toLong() shr 1) and 127)
    private fun consumePacket() {
        if(packet[1].toInt() and 0x80!=0)return
        val control=(packet[3].toInt() ushr 4) and 3
        if(control!=1 && control!=3)return
        val pid=((packet[1].toInt() and 31) shl 8) or (packet[2].toInt() and 255)
        val start=if(control==3) 5+(packet[4].toInt() and 255) else 4
        if(start>=188)return
        if(packet[1].toInt() and 0x40!=0) headers[pid]=ByteArrayOutputStream(19)
        val pending=headers[pid] ?: return
        pending.write(packet,start,minOf(19-pending.size(),188-start))
        val bytes=pending.toByteArray()
        if(bytes.size<4)return
        if(bytes[0]!=0.toByte() || bytes[1]!=0.toByte() || bytes[2]!=1.toByte() || bytes[3].toInt() and 0xf0!=0xe0) { headers.remove(pid);return }
        if(bytes.size<9)return
        if(bytes[7].toInt() and 0xc0!=0xc0 || bytes[8].toInt() and 255<10) { headers.remove(pid);return }
        if(bytes.size<19)return
        clocks[stamp(bytes,9)]=stamp(bytes,14)
        while(clocks.size>1024)clocks.remove(clocks.keys.first())
        headers.remove(pid)
    }
    fun decodeTime(presentationUs: Long,offsetUs: Long): Long? {
        val mask=0x1ffffffffL
        val raw=((presentationUs-offsetUs)*90/1000) and mask
        val pts=listOf(raw,(raw+1) and mask,(raw-1) and mask).firstOrNull { clocks.containsKey(it) } ?: return null
        val dts=clocks.remove(pts)!!
        var delta=(pts-dts) and mask
        if(delta>0x100000000L)delta-=0x200000000L
        return presentationUs-delta*1_000_000/90_000
    }
}
