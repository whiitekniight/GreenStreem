package com.example.greenstreem

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** LAN recorder credentials are kept outside playlist backups and tied to this install. */
internal object PcRecorder {
    private const val PREFS="pc_recorder"
    private const val KEY="greenstreem_pc_recorder"
    private fun prefs(c: Context)=c.getSharedPreferences(PREFS,0)
    fun enabled(c: Context)=prefs(c).getBoolean("enabled",false) && prefs(c).contains("token")
    fun connected(c: Context)=prefs(c).contains("token")
    fun address(c: Context)=prefs(c).getString("address","").orEmpty()
    fun setEnabled(c: Context,value: Boolean) { prefs(c).edit().putBoolean("enabled",value).apply() }
    fun forget(c: Context) { prefs(c).edit().clear().apply() }
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(KEY,null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun token(c: Context): String {
        val raw=prefs(c).getString("token",null) ?: error("Connect to your PC recorder first")
        val pieces=raw.split(':')
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(pieces[0],Base64.NO_WRAP)))
            String(doFinal(Base64.decode(pieces[1],Base64.NO_WRAP)),Charsets.UTF_8)
        }
    }
    suspend fun connect(c: Context,address: String,code: String)=withContext(Dispatchers.IO) {
        val base=validateAddress(address)
        val response=request(base,"/v1/pair","POST",JSONObject().put("code",code.trim()).put("name",android.os.Build.MODEL))
        val encrypted=Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE,key())
            val bytes=doFinal(response.getString("token").toByteArray(Charsets.UTF_8))
            Base64.encodeToString(iv,Base64.NO_WRAP)+":"+Base64.encodeToString(bytes,Base64.NO_WRAP)
        }
        check(prefs(c).edit().putString("address",base).putString("token",encrypted).putBoolean("enabled",true).commit())
    }
    private fun validateAddress(raw: String): String {
        val url=URL(if(raw.trim().contains("://")) raw.trim() else "http://${raw.trim()}")
        require(url.protocol=="http" && url.userInfo==null && url.query==null && url.ref==null && url.path in listOf("","/")) { "Enter the PC's home-network address and port" }
        require(InetAddress.getAllByName(url.host).all { it.isSiteLocalAddress || it.isLoopbackAddress }) { "Use a recorder on your home network" }
        return "http://${url.host}:${if(url.port>0) url.port else 8791}"
    }
    private fun request(base: String,path: String,method: String="GET",body: JSONObject?=null,token: String?=null): JSONObject {
        val conn=URL(base+path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod=method;conn.connectTimeout=5000;conn.readTimeout=10000;conn.instanceFollowRedirects=false
            token?.let { conn.setRequestProperty("Authorization","Bearer $it") }
            if(body!=null) {
                conn.doOutput=true;conn.setRequestProperty("Content-Type","application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val good=conn.responseCode in 200..299
            val stream=if(good) conn.inputStream else conn.errorStream
            val result=stream?.bufferedReader()?.use { reader ->
                val text=reader.readText();require(text.length<2_000_000) { "Recorder response is too large" };JSONObject(text)
            } ?: JSONObject()
            check(good) { result.optString("error","PC recorder could not complete this request") }
            return result
        } finally { conn.disconnect() }
    }
    suspend fun jobs(c: Context)=withContext(Dispatchers.IO) { request(address(c),"/v1/jobs",token=token(c)) }
    suspend fun schedule(c: Context,title: String,channel: String,url: String,start: Long,end: Long,weekly: Boolean=false,guideStart: Long=start,guideEnd: Long=end)=withContext(Dispatchers.IO) {
        if(weekly) {
            val info=request(address(c),"/v1/info")
            require(info.optString("version").split('.').take(2).joinToString(".") !in listOf("0.1","0.0","")) { "Update GreenStreem Recorder on your PC to use weekly schedules" }
        }
        request(address(c),"/v1/jobs","POST",JSONObject().put("guide_start",guideStart).put("guide_end",guideEnd).put("weekly",weekly).put("zone",java.util.TimeZone.getDefault().id).put("id",java.util.UUID.randomUUID().toString()).put("title",title)
            .put("channel",channel).put("url",url).put("start",start).put("end",end),token(c))
    }
    suspend fun cancel(c: Context,id: String)=withContext(Dispatchers.IO) { request(address(c),"/v1/jobs/$id/cancel","POST",JSONObject(),token(c)) }
    suspend fun delete(c: Context,id: String)=withContext(Dispatchers.IO) { request(address(c),"/v1/jobs/$id","DELETE",token=token(c)) }
    suspend fun discover(): List<Pair<String,String>> = withContext(Dispatchers.IO) {
        val found=linkedMapOf<String,String>()
        java.net.DatagramSocket().use { socket ->
            socket.broadcast=true;socket.soTimeout=600
            val data="GREENSTREEM_RECORDER_DISCOVER_V1".toByteArray(Charsets.UTF_8)
            val targets=mutableSetOf(InetAddress.getByName("255.255.255.255"))
            java.net.NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp }.forEach { net ->
                net.interfaceAddresses.mapNotNull { it.broadcast }.forEach { targets.add(it) }
            }
            targets.forEach { runCatching { socket.send(java.net.DatagramPacket(data,data.size,it,8792)) } }
            val until=System.currentTimeMillis()+2500
            while(System.currentTimeMillis()<until) {
                val packet=java.net.DatagramPacket(ByteArray(2048),2048)
                try {
                    socket.receive(packet)
                    val value=JSONObject(String(packet.data,0,packet.length,Charsets.UTF_8))
                    if(value.optString("service")=="greenstreem-recorder") {
                        val base="${packet.address.hostAddress}:${value.getInt("port")}"
                        found[base]=value.optString("name","PC recorder")
                    }
                } catch(_: java.net.SocketTimeoutException) {} catch(_: org.json.JSONException) {}
            }
        }
        found.map { it.value to it.key }
    }
}
