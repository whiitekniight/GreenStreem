package com.example.greenstreem

import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Reopen the provider entry URL, never an expiring redirect or a buffered copy. */
internal object RecordingStream {
    fun copy(
        originalUrl: String,
        deadlineMs: Long,
        openOutput: () -> OutputStream,
        cancelled: () -> Boolean,
        onConnection: (HttpURLConnection?) -> Unit,
        onProgress: (Long) -> Unit,
        nowMs: () -> Long = System::currentTimeMillis,
        retryDelaysMs: LongArray = longArrayOf(1000, 2000, 4000, 8000, 10000)
    ): Long {
        var output: OutputStream? = null
        var bytes = 0L
        var failures = 0
        fun running() = !cancelled() && nowMs() < deadlineMs
        try {
            while (running()) {
                val connectedAt = nowMs()
                val bytesBefore = bytes
                var sourceError: IOException? = null
                val connection = (URL(originalUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 15000
                    useCaches = false
                    instanceFollowRedirects = true
                }
                onConnection(connection)
                try {
                    val input = try {
                        if (connection.responseCode !in 200..299) throw IOException("Provider refused recording stream")
                        connection.inputStream.buffered().also { stream ->
                            stream.mark(1024)
                            val prefix = ByteArray(564)
                            var count = 0
                            while (count < prefix.size) {
                                val n = stream.read(prefix, count, prefix.size - count)
                                if (n < 0) break
                                count += n
                            }
                            stream.reset()
                            // Reject playlists/error pages before creating or appending to a file.
                            require(count >= 376 && (0 until 188).any {
                                prefix[it] == 0x47.toByte() && prefix[it + 188] == 0x47.toByte()
                            }) { "This channel does not provide a supported MPEG-TS recording stream" }
                        }
                    } catch (e: IOException) { sourceError = e; null }
                    input?.use { stream ->
                        if (output == null && running()) output = openOutput()
                        val buffer = ByteArray(64 * 1024)
                        while (running()) {
                            val n = try { stream.read(buffer) } catch (e: IOException) {
                                sourceError = e; break
                            }
                            if (n < 0) { sourceError = IOException("Recording stream ended early"); break }
                            if (!running()) break
                            if (n == 0) continue
                            // Storage errors must fail, not trigger a network retry or overwrite.
                            output!!.write(buffer, 0, n)
                            bytes += n
                            onProgress(bytes)
                        }
                    }
                } finally {
                    connection.disconnect()
                    onConnection(null)
                }
                if (!running()) break
                if (bytes - bytesBefore >= 188 * 100 && nowMs() - connectedAt >= 30_000L) failures = 0
                if (failures >= retryDelaysMs.size) {
                    throw IOException("Stream unavailable after recording reconnect attempts", sourceError)
                }
                val retryAt = minOf(deadlineMs, nowMs() + retryDelaysMs[failures++])
                while (running() && nowMs() < retryAt) Thread.sleep(minOf(100L, retryAt - nowMs()).coerceAtLeast(1L))
            }
            return bytes
        } finally { output?.close() }
    }
}
