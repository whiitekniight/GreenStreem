package com.example.greenstreem

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object EmbyConnectActivationClient {
    private const val BASE_URL = "https://dashboard.greenstreemlabs.com"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun submitPurchaseRequest(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject()
                .put("activationId", EmbyConnectEntitlement.activationId(context))
                .put("deviceName", listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.joinToString(" "))
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("product", "emby_connect_lifetime")
                .put("price", "9.99")
            request("POST", "/api/emby-connect/request", payload)
            Unit
        }
    }

    suspend fun refreshStatus(context: Context): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val id = EmbyConnectEntitlement.activationId(context)
            val result = request("GET", "/api/emby-connect/status?activationId=$id")
            val unlocked = result.optBoolean("unlocked", false)
            if (unlocked) EmbyConnectEntitlement.setUnlocked(context, true)
            unlocked
        }
    }

    private fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        val builder = Request.Builder().url(BASE_URL + path)
        if (method == "POST") {
            builder.post((body ?: JSONObject()).toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        } else {
            builder.get()
        }
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) error(JSONObject(text.ifBlank { "{}" }).optString("error", "Activation service unavailable"))
            return JSONObject(text.ifBlank { "{}" })
        }
    }
}
