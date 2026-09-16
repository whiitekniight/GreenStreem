package com.example.greenstreem

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object EmbySecureStore {
    private const val PREFS = "emby_connect_secure"
    private const val KEY_ALIAS = "greenstreem_emby_credentials"
    private const val KEY_CREDENTIALS = "credentials"

    data class Credentials(
        val serverUrl: String,
        val userId: String,
        val userName: String,
        val accessToken: String
    )

    fun save(context: Context, credentials: Credentials) {
        val clear = JSONObject()
            .put("serverUrl", credentials.serverUrl)
            .put("userId", credentials.userId)
            .put("userName", credentials.userName)
            .put("accessToken", credentials.accessToken)
            .toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val payload = JSONObject()
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(cipher.doFinal(clear.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP))
            .toString()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_CREDENTIALS, payload).apply()
    }

    fun load(context: Context): Credentials? = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CREDENTIALS, null)
            ?: return null
        val payload = JSONObject(raw)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(128, Base64.decode(payload.getString("iv"), Base64.NO_WRAP))
        )
        val json = JSONObject(String(cipher.doFinal(Base64.decode(payload.getString("data"), Base64.NO_WRAP)), Charsets.UTF_8))
        Credentials(
            serverUrl = json.getString("serverUrl"),
            userId = json.getString("userId"),
            userName = json.getString("userName"),
            accessToken = json.getString("accessToken")
        )
    }.getOrNull()

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_CREDENTIALS).apply()
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
