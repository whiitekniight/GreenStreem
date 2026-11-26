package com.example.greenstreem

/** A single HTTP byte range. Reject unsupported multipart ranges instead of sending the wrong bytes. */
internal data class RecordingByteRange(val start: Long, val length: Long) {
    companion object {
        fun parse(header: String?, size: Long): RecordingByteRange {
            require(size >= 0)
            if(header == null) return RecordingByteRange(0,size)
            require(size > 0 && header.startsWith("bytes=") && ',' !in header)
            val match=Regex("bytes=(\\d*)-(\\d*)").matchEntire(header) ?: error("Invalid range")
            val (first,last)=match.destructured
            if(first.isEmpty()) {
                val suffix=last.toLong(); require(suffix>0)
                val count=minOf(suffix,size)
                return RecordingByteRange(size-count,count)
            }
            val start=first.toLong(); require(start<size)
            val end=if(last.isEmpty()) size-1 else minOf(last.toLong(),size-1)
            require(end>=start)
            return RecordingByteRange(start,end-start+1)
        }
    }
}
