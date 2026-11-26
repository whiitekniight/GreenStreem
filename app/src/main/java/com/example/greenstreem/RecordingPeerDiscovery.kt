package com.example.greenstreem

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import java.util.ArrayDeque

/** Serial resolution also works on older Fire OS NSD implementations. */
internal class RecordingPeerDiscovery(c: Context, val found: (Peer)->Unit, val failure: ()->Unit) {
    data class Peer(val id: String,val name: String,val address: String,val port: Int)
    private val manager=c.getSystemService(NsdManager::class.java)
    private val main=Handler(Looper.getMainLooper())
    private var active=false
    private var resolving=false
    private val pending=ArrayDeque<NsdServiceInfo>()
    private val listener=object: NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(type: String) {}
        override fun onDiscoveryStopped(type: String) {}
        override fun onStartDiscoveryFailed(type: String,error: Int) { main.post { failure() } }
        override fun onStopDiscoveryFailed(type: String,error: Int) {}
        override fun onServiceLost(info: NsdServiceInfo) {}
        override fun onServiceFound(info: NsdServiceInfo) { main.post { if(active) { pending.add(info); resolveNext() } } }
    }
    fun start() {
        active=true
        runCatching { manager.discoverServices(RecordingShareService.TYPE,NsdManager.PROTOCOL_DNS_SD,listener) }.onFailure { failure() }
    }
    @Suppress("DEPRECATION") private fun resolveNext() {
        if(!active || resolving || pending.isEmpty()) return
        resolving=true
        val next=pending.removeFirst()
        runCatching { manager.resolveService(next,object: NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo,error: Int) { main.post { resolving=false; resolveNext() } }
            override fun onServiceResolved(info: NsdServiceInfo) { main.post {
                if(active) {
                    val address=info.host?.hostAddress
                    val id=info.attributes["id"]?.toString(Charsets.UTF_8)
                    if(address!=null && !id.isNullOrBlank()) found(Peer(id,info.serviceName,address,info.port))
                }
                resolving=false; resolveNext()
            } }
        }) }.onFailure { resolving=false; resolveNext() }
    }
    fun stop() { active=false; pending.clear(); runCatching { manager.stopServiceDiscovery(listener) } }
}
