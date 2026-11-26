package com.example.greenstreem

import fi.iki.elonen.NanoHTTPD
import java.io.InputStream
import java.io.PrintWriter

internal object CastHttpResponse {
    /** NanoHTTPD otherwise advertises Content-Length: -1 for HEAD on an unknown-length stream. */
    fun live(input: InputStream): NanoHTTPD.Response = object: NanoHTTPD.Response(NanoHTTPD.Response.Status.OK,"video/mpeg",input,-1) {
        override fun sendContentLengthHeaderIfNotAlreadyPresent(writer: PrintWriter,size: Long): Long {
            if(size<0) { printHeader(writer,"Transfer-Encoding","chunked");return 0 }
            return super.sendContentLengthHeaderIfNotAlreadyPresent(writer,size)
        }
    }
}
