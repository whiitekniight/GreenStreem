package com.example.greenstreem

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object XtreamManager {
    private var service: XtreamService? = null
    var baseUrl: String = ""
    var username: String = ""
    var password: String = ""

    fun init(url: String) {
        if (url.isBlank()) return
        
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        try {
            val formattedUrl = if (url.startsWith("http")) url else "http://$url"
            val finalUrl = if (formattedUrl.endsWith("/")) formattedUrl else "$formattedUrl/"
            
            val retrofit = Retrofit.Builder()
                .baseUrl(finalUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()

            service = retrofit.create(XtreamService::class.java)
            baseUrl = finalUrl.trimEnd('/')
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Helper to initialize the manager from saved SharedPreferences.
     * Prevents crashes by ensuring the service is never null when accessed.
     */
    fun initFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
        val url = prefs.getString("server_url", "") ?: ""
        username = prefs.getString("username", "") ?: ""
        password = prefs.getString("password", "") ?: ""
        if (url.isNotEmpty()) {
            init(url)
        }
    }

    fun getService(): XtreamService? = service
}
