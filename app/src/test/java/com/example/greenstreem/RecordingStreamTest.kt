package com.example.greenstreem

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

class RecordingStreamTest {
    private fun packets(value: Int) = ByteArray(188 * 3) { value.toByte() }.apply {
        for (i in indices step 188) this[i] = 0x47
    }

    @Test fun expiredRedirectReopensEntryAndKeepsOneFileWithoutReplayingWrittenBytes() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val entries = AtomicInteger()
        val first = packets(1)
        val second = packets(2)
        server.createContext("/entry") { e ->
            e.responseHeaders.add("Location", "/token-${entries.incrementAndGet()}")
            e.sendResponseHeaders(302, -1); e.close()
        }
        for ((path, data) in listOf("/token-1" to first, "/token-2" to second)) {
            server.createContext(path) { e ->
                e.sendResponseHeaders(200, data.size.toLong())
                e.responseBody.use { it.write(data) }
            }
        }
        server.start()
        try {
            val saved = ByteArrayOutputStream()
            var opens = 0
            var done = false
            val bytes = RecordingStream.copy("http://127.0.0.1:${server.address.port}/entry",
                System.currentTimeMillis() + 10_000, { opens++; saved }, { done }, {},
                { if (it == (first.size + second.size).toLong()) done = true },
                retryDelaysMs = longArrayOf(0, 0))
            assertEquals(2, entries.get())
            assertEquals(1, opens)
            assertEquals((first.size + second.size).toLong(), bytes)
            assertArrayEquals(first + second, saved.toByteArray())
        } finally { server.stop(0) }
    }

    @Test fun storageFailureIsNotRetriedAsNetworkFailure() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = AtomicInteger()
        server.createContext("/") { e ->
            requests.incrementAndGet()
            val data = packets(3)
            e.sendResponseHeaders(200, data.size.toLong()); e.responseBody.use { it.write(data) }
        }
        server.start()
        try {
            try {
                RecordingStream.copy("http://127.0.0.1:${server.address.port}/",
                    System.currentTimeMillis() + 10_000,
                    { object : OutputStream() { override fun write(b: Int) { throw IOException("Disk full") } } },
                    { false }, {}, {}, retryDelaysMs = longArrayOf(0, 0))
                fail("Storage error must be reported")
            } catch (e: IOException) { assertEquals("Disk full", e.message) }
            assertEquals(1, requests.get())
        } finally { server.stop(0) }
    }

    @Test fun unavailableSourceHasBoundedRetriesAndDoesNotCreateEmptyFiles() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = AtomicInteger()
        server.createContext("/") { e -> requests.incrementAndGet(); e.sendResponseHeaders(503, -1); e.close() }
        server.start()
        try {
            var opened = false
            try {
                RecordingStream.copy("http://127.0.0.1:${server.address.port}/",
                    System.currentTimeMillis() + 10_000, { opened = true; ByteArrayOutputStream() },
                    { false }, {}, {}, retryDelaysMs = longArrayOf(0, 0))
                fail("Source failure must be reported")
            } catch (_: IOException) { }
            assertEquals(3, requests.get())
            assertFalse(opened)
        } finally { server.stop(0) }
    }

    @Test fun errorPageIsNeverAppendedToRecording() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { e ->
            val data = "<html>Access denied</html>".toByteArray()
            e.sendResponseHeaders(200, data.size.toLong()); e.responseBody.use { it.write(data) }
        }
        server.start()
        try {
            var opened = false
            try {
                RecordingStream.copy("http://127.0.0.1:${server.address.port}/",
                    System.currentTimeMillis() + 10_000, { opened = true; ByteArrayOutputStream() },
                    { false }, {}, {}, retryDelaysMs = longArrayOf(0))
                fail("Error page must be rejected")
            } catch (_: IllegalArgumentException) { }
            assertFalse(opened)
        } finally { server.stop(0) }
    }
}
