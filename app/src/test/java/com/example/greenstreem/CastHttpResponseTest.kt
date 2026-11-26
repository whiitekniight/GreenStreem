package com.example.greenstreem

import fi.iki.elonen.NanoHTTPD
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class CastHttpResponseTest {
    @Test fun unknownLengthHeadIsValidAndGetDeliversTheStream() {
        val bytes=ByteArray(1000) { it.toByte() }
        val server=object: NanoHTTPD("127.0.0.1",0) {
            override fun serve(s: IHTTPSession)=CastHttpResponse.live(bytes.inputStream())
        }
        server.start(5000,true)
        try {
            for(method in listOf("HEAD","GET")) {
                val connection=URL("http://127.0.0.1:${server.listeningPort}/stream.ts").openConnection() as HttpURLConnection
                try {
                    connection.requestMethod=method;connection.readTimeout=2000
                    assertEquals(200,connection.responseCode)
                    assertNull(connection.getHeaderField("Content-Length"))
                    assertEquals("chunked",connection.getHeaderField("Transfer-Encoding"))
                    if(method=="GET") assertArrayEquals(bytes,connection.inputStream.readBytes())
                    else assertEquals(-1,connection.inputStream.read())
                } finally { connection.disconnect() }
            }
        } finally { server.stop() }
    }
}
