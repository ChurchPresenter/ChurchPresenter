package org.churchpresenter.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.churchpresenter.diagnostics.Log
import java.io.IOException
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

/**
 * Advertises the companion server as `_churchpresenter._tcp` over mDNS, so a signage player on the
 * same network finds it without being told the address. [wanted] says what to advertise now, or null
 * for nothing; [refresh] brings the advert in line with it on a thread of its own, in order.
 */
internal class SignageAdvert(private val wanted: () -> Wanted?) {
    /** The port to advertise, and the name a player logs. */
    data class Wanted(val port: Int, val name: String)

    private var mdns: JmDNS? = null
    private var advertised: Pair<String, Int>? = null

    /** One thread, never cancelled, so starts and stops keep their order. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    fun refresh() {
        val now = wanted()
        scope.launch {
            if (now != null) advertise(localIpAddress(), now.port, now.name) else stop()
        }
    }

    @Synchronized
    private fun advertise(host: String, port: Int, name: String) {
        if (advertised == host to port) return
        stop()
        try {
            val jmdns = JmDNS.create(InetAddress.getByName(host), name)
            jmdns.registerService(ServiceInfo.create(SERVICE_TYPE, name, port, "ChurchPresenter signage"))
            mdns = jmdns
            advertised = host to port
            Log.info(TAG, "Advertising signage on $host:$port")
        } catch (e: IOException) {
            Log.warn(TAG, "Could not advertise signage over mDNS: ${e.message}")
        }
    }

    @Synchronized
    private fun stop() {
        val jmdns = mdns ?: return
        mdns = null
        advertised = null
        try {
            jmdns.unregisterAllServices()
            jmdns.close()
        } catch (e: IOException) {
            Log.warn(TAG, "Could not stop the mDNS advert: ${e.message}")
        }
    }

    private companion object {
        const val SERVICE_TYPE = "_churchpresenter._tcp.local."
        const val TAG = "Signage"
    }
}
