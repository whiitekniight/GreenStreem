package com.example.greenstreem

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

object EmbyApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class LoginResult(val serverUrl: String, val userId: String, val userName: String, val accessToken: String)
    data class MediaEntry(
        val id: String,
        val name: String,
        val type: String,
        val overview: String,
        val number: String,
        val imageUrl: String
    )
    data class GuideProgram(
        val name: String,
        val overview: String,
        val startMs: Long,
        val endMs: Long
    )
    data class GuideChannel(val channel: MediaEntry, val programs: List<GuideProgram>)

    suspend fun authenticate(serverInput: String, userName: String, password: String): Result<LoginResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val server = normalizeServerUrl(serverInput)
                val body = JSONObject()
                    .put("Username", userName.trim())
                    .put("Pw", password)
                    .toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url("$server/emby/Users/AuthenticateByName")
                    .header("X-Emby-Authorization", authorizationHeader())
                    .post(body)
                    .build()
                client.newCall(request).execute().use { response ->
                    val text = response.body.string()
                    if (!response.isSuccessful) error("Emby sign-in failed (${response.code})")
                    val json = JSONObject(text)
                    val user = json.getJSONObject("User")
                    LoginResult(
                        serverUrl = server,
                        userId = user.getString("Id"),
                        userName = user.optString("Name", userName.trim()),
                        accessToken = json.getString("AccessToken")
                    )
                }
            }
        }

    suspend fun liveChannels(credentials: EmbySecureStore.Credentials): Result<List<MediaEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val channels = getItemsOrThrow(
                    credentials,
                    "/emby/LiveTv/Channels?UserId=${encode(credentials.userId)}&Fields=Overview,ChannelNumber"
                )
                val now = Date()
                val later = Date(now.time + 6L * 60L * 60L * 1000L)
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val programs = requestJson(
                    credentials,
                    "/emby/LiveTv/Programs?UserId=${encode(credentials.userId)}" +
                        "&MinStartDate=${encode(format.format(now))}&MaxStartDate=${encode(format.format(later))}&Limit=1000"
                ).optJSONArray("Items") ?: JSONArray()
                val currentByChannel = mutableMapOf<String, String>()
                for (index in 0 until programs.length()) {
                    val program = programs.optJSONObject(index) ?: continue
                    val channelId = program.optString("ChannelId")
                    if (channelId.isBlank() || currentByChannel.containsKey(channelId)) continue
                    val name = program.optString("Name")
                    if (name.isNotBlank()) currentByChannel[channelId] = "Now: $name"
                }
                channels.map { channel ->
                    channel.copy(overview = currentByChannel[channel.id] ?: channel.overview)
                }
            }
        }

    suspend fun liveGuide(credentials: EmbySecureStore.Credentials): Result<List<GuideChannel>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val channels = getItemsOrThrow(
                    credentials,
                    "/emby/LiveTv/Channels?UserId=${encode(credentials.userId)}&Fields=Overview,ChannelNumber"
                )
                val now = Date()
                val later = Date(now.time + 4L * 60L * 60L * 1000L)
                val requestFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val items = requestJson(
                    credentials,
                    "/emby/LiveTv/Programs?UserId=${encode(credentials.userId)}" +
                        "&MinEndDate=${encode(requestFormat.format(now))}" +
                        "&MaxStartDate=${encode(requestFormat.format(later))}" +
                        "&Fields=Overview&Limit=5000"
                ).optJSONArray("Items") ?: JSONArray()
                val byChannel = mutableMapOf<String, MutableList<GuideProgram>>()
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val channelId = item.optString("ChannelId")
                    val start = parseEmbyDate(item.optString("StartDate")) ?: continue
                    val end = parseEmbyDate(item.optString("EndDate")) ?: continue
                    if (channelId.isBlank() || end <= start) continue
                    byChannel.getOrPut(channelId) { mutableListOf() }.add(
                        GuideProgram(item.optString("Name", "No Information"), item.optString("Overview"), start, end)
                    )
                }
                channels.map { channel ->
                    GuideChannel(channel, byChannel[channel.id].orEmpty().sortedBy { it.startMs })
                }
            }
        }

    suspend fun movies(credentials: EmbySecureStore.Credentials): Result<List<MediaEntry>> =
        getItems(
            credentials,
            "/emby/Users/${encode(credentials.userId)}/Items?Recursive=true&IncludeItemTypes=Movie&Fields=Overview&SortBy=SortName&Limit=1000"
        )

    suspend fun series(credentials: EmbySecureStore.Credentials): Result<List<MediaEntry>> =
        getItems(
            credentials,
            "/emby/Users/${encode(credentials.userId)}/Items?Recursive=true&IncludeItemTypes=Series&Fields=Overview&SortBy=SortName&Limit=1000"
        )

    suspend fun episodes(credentials: EmbySecureStore.Credentials, seriesId: String): Result<List<MediaEntry>> =
        getItems(
            credentials,
            "/emby/Shows/${encode(seriesId)}/Episodes?UserId=${encode(credentials.userId)}&Fields=Overview&IsMissing=false"
        )

    fun streamUrl(credentials: EmbySecureStore.Credentials, itemId: String): String =
        "${credentials.serverUrl}/emby/Videos/${encode(itemId)}/stream?static=true&api_key=${encode(credentials.accessToken)}"

    suspend fun movieStreamUrl(credentials: EmbySecureStore.Credentials, itemId: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = "{}".toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(
                        "${credentials.serverUrl}/emby/Items/${encode(itemId)}/PlaybackInfo" +
                            "?UserId=${encode(credentials.userId)}&IsPlayback=true"
                    )
                    .header("X-Emby-Token", credentials.accessToken)
                    .header("X-Emby-Authorization", authorizationHeader())
                    .post(body)
                    .build()
                client.newCall(request).execute().use { response ->
                    val text = response.body.string()
                    if (!response.isSuccessful) error("Emby playback request failed (${response.code})")
                    val root = JSONObject(text)
                    val source = root.optJSONArray("MediaSources")?.optJSONObject(0)
                        ?: error("Emby did not return a playable media source")
                    val playableItemId = source.optString("ItemId").ifBlank { itemId }
                    val mediaSourceId = source.optString("Id")
                    val playSessionId = root.optString("PlaySessionId").ifBlank {
                        UUID.randomUUID().toString().replace("-", "")
                    }
                    Log.i(
                        "GreenStreemEmby",
                        "Playback source selected: itemIdMatch=${playableItemId == itemId}, " +
                            "hasMediaSourceId=${mediaSourceId.isNotBlank()}, " +
                            "directPlay=${source.optBoolean("SupportsDirectPlay")}, " +
                            "directStream=${source.optBoolean("SupportsDirectStream")}, " +
                            "transcoding=${source.optBoolean("SupportsTranscoding")}, " +
                            "container=${source.optString("Container", "unknown")}"
                    )
                    val serverProvided = source.optString("DirectStreamUrl").ifBlank {
                        source.optString("TranscodingUrl")
                    }
                    if (serverProvided.isNotBlank()) {
                        val absolute = if (serverProvided.startsWith("http://", true) || serverProvided.startsWith("https://", true)) {
                            serverProvided
                        } else {
                            credentials.serverUrl + if (serverProvided.startsWith('/')) serverProvided else "/$serverProvided"
                        }
                        val authenticated = if (absolute.contains("api_key=", true)) {
                            absolute
                        } else {
                            absolute + (if (absolute.contains('?')) "&" else "?") +
                                "api_key=${encode(credentials.accessToken)}"
                        }
                        Log.i("GreenStreemEmby", "Using server-provided Emby movie stream path")
                        return@use authenticated
                    }
                    Log.i("GreenStreemEmby", "Using Emby HLS movie fallback")
                    val hlsUrl = "${credentials.serverUrl}/emby/Videos/${encode(playableItemId)}/master.m3u8" +
                        "?MediaSourceId=${encode(mediaSourceId)}" +
                        "&DeviceId=greenstreem-emby" +
                        "&PlaySessionId=${encode(playSessionId)}" +
                        "&UserId=${encode(credentials.userId)}" +
                        "&VideoCodec=h264&AudioCodec=aac" +
                        "&MaxStreamingBitrate=40000000" +
                        "&api_key=${encode(credentials.accessToken)}"
                    val masterRequest = Request.Builder()
                        .url(hlsUrl)
                        .header("X-Emby-Token", credentials.accessToken)
                        .header("X-Emby-Authorization", authorizationHeader())
                        .get()
                        .build()
                    client.newCall(masterRequest).execute().use { masterResponse ->
                        val masterText = masterResponse.body.string()
                        if (!masterResponse.isSuccessful) {
                            Log.e("GreenStreemEmby", "Emby HLS master failed (${masterResponse.code})")
                            error("Emby could not prepare this movie (${masterResponse.code})")
                        }
                        val variantPath = masterText.lineSequence()
                            .map { it.trim() }
                            .firstOrNull { it.isNotBlank() && !it.startsWith('#') }
                        if (variantPath != null && masterText.contains("#EXT-X-STREAM-INF")) {
                            val variantUrl = masterResponse.request.url.resolve(variantPath)?.toString()
                            if (variantUrl != null) {
                                Log.i("GreenStreemEmby", "Resolved Emby HLS variant playlist")
                                val variantRequest = Request.Builder()
                                    .url(variantUrl)
                                    .header("X-Emby-Token", credentials.accessToken)
                                    .header("X-Emby-Authorization", authorizationHeader())
                                    .get()
                                    .build()
                                client.newCall(variantRequest).execute().use { variantResponse ->
                                    if (!variantResponse.isSuccessful) {
                                        val errorText = variantResponse.body.string()
                                            .replace(credentials.accessToken, "[hidden]")
                                            .replace(Regex("(?i)(api_key|token)=[^&\\s\"']+"), "credential=[hidden]")
                                            .take(1000)
                                        Log.e(
                                            "GreenStreemEmby",
                                            "Emby HLS variant failed (${variantResponse.code}): $errorText"
                                        )
                                        error("Emby movie stream failed (${variantResponse.code})")
                                    }
                                }
                                return@use variantUrl
                            }
                        }
                        hlsUrl
                    }
                }
            }
        }

    fun playbackHeaders(credentials: EmbySecureStore.Credentials): Map<String, String> = mapOf(
        "X-Emby-Token" to credentials.accessToken,
        "X-Emby-Authorization" to authorizationHeader()
    )

    private suspend fun getItems(credentials: EmbySecureStore.Credentials, path: String): Result<List<MediaEntry>> =
        withContext(Dispatchers.IO) {
            runCatching { getItemsOrThrow(credentials, path) }
        }

    private fun getItemsOrThrow(credentials: EmbySecureStore.Credentials, path: String): List<MediaEntry> {
        val items = requestJson(credentials, path).optJSONArray("Items") ?: JSONArray()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("Id")
                if (id.isBlank()) continue
                val season = item.optInt("ParentIndexNumber", 0)
                val episode = item.optInt("IndexNumber", 0)
                val episodeNumber = if (season > 0 || episode > 0) "S%02d E%02d".format(season, episode) else item.optString("ChannelNumber")
                add(
                    MediaEntry(
                        id = id,
                        name = item.optString("Name", "Emby item"),
                        type = item.optString("Type"),
                        overview = item.optString("Overview"),
                        number = episodeNumber,
                        imageUrl = "${credentials.serverUrl}/emby/Items/${encode(id)}/Images/Primary?maxWidth=500&quality=85&api_key=${encode(credentials.accessToken)}"
                    )
                )
            }
        }
    }

    private fun requestJson(credentials: EmbySecureStore.Credentials, path: String): JSONObject {
        val request = Request.Builder()
            .url(credentials.serverUrl + path)
            .header("X-Emby-Token", credentials.accessToken)
            .header("X-Emby-Authorization", authorizationHeader())
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) error("Emby request failed (${response.code})")
            JSONObject(text)
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun parseEmbyDate(value: String): Long? {
        if (value.isBlank()) return null
        val normalized = value.replace(Regex("\\.(\\d{3})\\d+(Z|[+-]\\d{2}:\\d{2})$"), ".$1$2")
        val formats = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX")
        for (pattern in formats) {
            runCatching {
                return SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(normalized)?.time
            }
        }
        return null
    }

    private fun normalizeServerUrl(input: String): String {
        var value = input.trim().trimEnd('/')
        require(value.isNotBlank()) { "Enter your Emby server address" }
        if (!value.startsWith("http://", true) && !value.startsWith("https://", true)) {
            value = "https://$value"
        }
        return value.removeSuffix("/emby")
    }

    private fun authorizationHeader(): String =
        "MediaBrowser Client=\"GreenStreem\", Device=\"Android TV\", DeviceId=\"greenstreem-emby\", Version=\"${BuildConfig.VERSION_NAME}\""
}
