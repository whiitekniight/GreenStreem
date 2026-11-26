package com.example.greenstreem

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.w3c.dom.Element
import java.net.*
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

/** UPnP AVTransport renderer support, without assuming a TV has Google services. */
internal object DlnaCasting {
    data class Device(val name: String,val endpoint: String,val service: String)
    private val http=OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).followRedirects(false).build()
    private fun parse(xml: String): Element {
        require(xml.length<=512_000 && !xml.contains("<!DOCTYPE",true) && !xml.contains("<!ENTITY",true)) { "Invalid TV description" }
        val factory=DocumentBuilderFactory.newInstance().apply { isNamespaceAware=true; isExpandEntityReferences=false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl",true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities",false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities",false) }
        }
        return factory.newDocumentBuilder().parse(xml.byteInputStream()).documentElement
    }
    private fun Element.value(name: String)=getElementsByTagNameNS("*",name).item(0)?.textContent.orEmpty()
    internal fun describe(xml: String,location: String): Device? {
        val root=parse(xml)
        val base=root.value("URLBase").ifBlank { location }
        val services=root.getElementsByTagNameNS("*","service")
        for(n in 0 until services.length) {
            val service=services.item(n) as Element
            val type=service.value("serviceType")
            if(!type.startsWith("urn:schemas-upnp-org:service:AVTransport:")) continue
            val endpoint=URL(URL(base),service.value("controlURL")).toString()
            require(URL(endpoint).host==URL(location).host) { "TV control address differs from discovery address" }
            return Device(root.value("friendlyName").ifBlank { URL(location).host },endpoint,type)
        }
        return null
    }
    fun discover(cancelled: ()->Boolean,found: (Device)->Unit) {
        DatagramSocket().use { socket ->
            socket.soTimeout=700
            socket.broadcast=true
            val request="M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n".toByteArray()
            socket.send(DatagramPacket(request,request.size,InetAddress.getByName("239.255.255.250"),1900))
            val until=System.nanoTime()+6_000_000_000
            val seen=mutableSetOf<String>()
            while(System.nanoTime()<until && !cancelled()) {
                try {
                    val packet=DatagramPacket(ByteArray(8192),8192); socket.receive(packet)
                    val location=String(packet.data,0,packet.length).lineSequence().firstOrNull { it.startsWith("location:",true) }?.substringAfter(':')?.trim() ?: continue
                    if(!seen.add(location)) continue
                    val url=URL(location)
                    if(url.protocol!="http" || !CastSupport.isLan(packet.address) || InetAddress.getByName(url.host)!=packet.address) continue
                    runCatching {
                        http.newCall(Request.Builder().url(location).build()).execute().use { response ->
                            check(response.isSuccessful)
                            val source=response.body!!.source(); source.request(512_001)
                            val bytes=source.readByteArray(minOf(source.buffer.size,512_001))
                            describe(String(bytes,Charsets.UTF_8),location)?.let(found)
                        }
                    }
                } catch(e: SocketTimeoutException) { } catch(e: Exception) { if(cancelled()) break }
            }
        }
    }
    private fun soap(device: Device,action: String,fields: List<Pair<String,String>>): String {
        require(URL(device.endpoint).protocol=="http" && CastSupport.isLan(InetAddress.getByName(URL(device.endpoint).host)))
        val body="<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:$action xmlns:u=\"${device.service}\">"+
            fields.joinToString("") { (key,value)->"<$key>${CastSupport.xml(value)}</$key>" }+"</u:$action></s:Body></s:Envelope>"
        return http.newCall(Request.Builder().url(device.endpoint).header("SOAPAction","\"${device.service}#$action\"")
            .post(body.toRequestBody("text/xml; charset=utf-8".toMediaType())).build()).execute().use {
            val body=it.body?.string().orEmpty()
            check(it.isSuccessful) {
                val code=runCatching { parse(body).value("errorCode") }.getOrDefault("")
                "TV rejected $action (${it.code}${if(code.isBlank()) "" else "/$code"})."
            }
            body
        }
    }
    fun load(device: Device,media: CastSupport.Media) {
        val mime=if(CastSupport.mime(media.url)=="video/mp2t") "video/mpeg" else CastSupport.mime(media.url)
        val metadata="<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"><item id=\"0\" parentID=\"-1\" restricted=\"1\"><dc:title>${CastSupport.xml(media.title)}</dc:title><upnp:class>object.item.videoItem</upnp:class><res protocolInfo=\"http-get:*:${mime}:*\">${CastSupport.xml(media.url)}</res></item></DIDL-Lite>"
        // Samsung rejects a new Play transition if the previous resource is still active.
        runCatching { action(device,"Stop") }
        soap(device,"SetAVTransportURI",listOf("InstanceID" to "0","CurrentURI" to media.url,"CurrentURIMetaData" to metadata))
        soap(device,"Play",listOf("InstanceID" to "0","Speed" to "1"))
        if(!media.live && media.position>0) runCatching { seek(device,media.position) }
    }
    fun action(device: Device,action: String) { soap(device,action,if(action=="Play") listOf("InstanceID" to "0","Speed" to "1") else listOf("InstanceID" to "0")) }
    fun playing(device: Device)=parse(soap(device,"GetTransportInfo",listOf("InstanceID" to "0"))).value("CurrentTransportState")=="PLAYING"
    fun position(device: Device): Long {
        val parts=parse(soap(device,"GetPositionInfo",listOf("InstanceID" to "0"))).value("RelTime").split(':')
        require(parts.size==3) { "This TV does not report a seek position" }
        return ((parts[0].toLong()*3600+parts[1].toLong()*60)*1000+(parts[2].toDouble()*1000).toLong())
    }
    fun seek(device: Device,position: Long) {
        val seconds=(position.coerceAtLeast(0)/1000)
        soap(device,"Seek",listOf("InstanceID" to "0","Unit" to "REL_TIME","Target" to "%02d:%02d:%02d".format(seconds/3600,seconds/60%60,seconds%60)))
    }
}
