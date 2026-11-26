package com.example.greenstreem

import java.io.OutputStream

/** Small MPEG-TS writer for the H.264/AAC compatibility stream. No video re-encoding. */
internal class CastTransportStream(private val output: OutputStream) {
    private val counters=IntArray(8192)
    private fun crc(bytes: ByteArray): ByteArray {
        var crc=-1
        bytes.forEach { byte ->
            crc=crc xor ((byte.toInt() and 255) shl 24)
            repeat(8) { crc=if(crc<0) (crc shl 1) xor 0x04c11db7 else crc shl 1 }
        }
        return bytes+byteArrayOf((crc ushr 24).toByte(),(crc ushr 16).toByte(),(crc ushr 8).toByte(),crc.toByte())
    }
    fun tables() {
        packetize(0,byteArrayOf(0)+crc(byteArrayOf(0,0xb0.toByte(),13,0,1,0xc1.toByte(),0,0,0,1,0xf0.toByte(),0)),null)
        packetize(0x1000,byteArrayOf(0)+crc(byteArrayOf(2,0xb0.toByte(),23,0,1,0xc1.toByte(),0,0,
            0xe1.toByte(),0,0xf0.toByte(),0,0x1b,0xe1.toByte(),0,0xf0.toByte(),0,0x0f,0xe1.toByte(),1,0xf0.toByte(),0)),null)
    }
    private fun timestamp(value: Long,prefix: Int)=byteArrayOf(
        (prefix or (((value ushr 30).toInt() and 7) shl 1) or 1).toByte(),(value ushr 22).toByte(),
        ((((value ushr 15).toInt() and 127) shl 1) or 1).toByte(),(value ushr 7).toByte(),(((value.toInt() and 127) shl 1) or 1).toByte())
    fun sample(video: Boolean,data: ByteArray,timeUs: Long,decodeTimeUs: Long?=null) {
        fun clock(us: Long)=((us*90/1000)+90_000) and 0x1ffffffffL
        val pts=clock(timeUs)
        val dts=if(video) decodeTimeUs?.let(::clock) else null
        val stamps=if(dts!=null) timestamp(pts,0x30)+timestamp(dts,0x10) else timestamp(pts,0x20)
        val length=if(video) 0 else data.size+3+stamps.size
        val header=byteArrayOf(0,0,1,if(video) 0xe0.toByte() else 0xc0.toByte(),(length ushr 8).toByte(),length.toByte(),
            0x80.toByte(),if(dts!=null) 0xc0.toByte() else 0x80.toByte(),stamps.size.toByte())+stamps
        packetize(if(video) 0x100 else 0x101,header+data,if(video) dts ?: pts else null)
    }
    private fun packetize(pid: Int,data: ByteArray,pcr: Long?) {
        var offset=0
        while(offset<data.size) {
            val first=offset==0
            val clock=if(first) pcr else null
            val capacity=if(clock!=null) 176 else 184
            val count=minOf(capacity,data.size-offset)
            val adaptation=184-count
            val packet=ByteArray(188) { 0xff.toByte() }
            packet[0]=0x47;packet[1]=((pid ushr 8) or if(first) 0x40 else 0).toByte();packet[2]=pid.toByte()
            packet[3]=((if(adaptation>0) 0x30 else 0x10) or (counters[pid]++ and 15)).toByte()
            if(adaptation>0) {
                packet[4]=(adaptation-1).toByte()
                if(adaptation>1) packet[5]=if(clock!=null) 0x10 else 0
                if(clock!=null) {
                    packet[6]=(clock ushr 25).toByte();packet[7]=(clock ushr 17).toByte();packet[8]=(clock ushr 9).toByte()
                    packet[9]=(clock ushr 1).toByte();packet[10]=(((clock and 1).toInt() shl 7) or 0x7e).toByte();packet[11]=0
                }
            }
            data.copyInto(packet,4+adaptation,offset,offset+count);output.write(packet);offset+=count
        }
    }
}
