package org.churchpresenter.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.signage.core.NUDGE_PORT
import org.churchpresenter.signage.core.Nudge
import org.churchpresenter.signage.core.SignageJson
import org.churchpresenter.signage.core.nudgeOwner
import org.churchpresenter.signage.hub.SignageHub
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Knocks on each paired screen that is away, at the address it last connected from, so it connects
 * at once rather than waiting out its retry pause or finding this computer by mDNS (which Wi-Fi
 * often drops): every [FAST_EVERY_MS] for the first [FAST_FOR_MS] after signage starts, then every
 * [SLOW_EVERY_MS]. [wanted] says what to knock for now, or null for nothing; [refresh] starts and
 * stops the knocking to match.
 */
internal class SignageNudger(
    private val wanted: () -> Wanted?,
    private val send: (host: String, bytes: ByteArray) -> Unit = ::sendUdp,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** The hub whose away screens are knocked on, and the port they should connect to. */
    data class Wanted(val hub: SignageHub, val port: Int)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var startedAtMs: Long? = null
    private var sentAtMs: Long? = null

    @Synchronized
    fun refresh() {
        val on = wanted() != null
        if (on && job == null) {
            job = scope.launch {
                while (isActive) {
                    tick()
                    delay(FAST_EVERY_MS)
                }
            }
        } else if (!on) {
            job?.cancel()
            job = null
        }
    }

    /** One look: knocks on the away screens when it is time, and says how many were knocked on. */
    @Synchronized
    internal fun tick(): Int {
        val now = wanted()
        if (now == null) {
            startedAtMs = null
            sentAtMs = null
            return 0
        }
        val at = clock()
        val since = startedAtMs ?: at.also { startedAtMs = it }
        val every = if (at - since < FAST_FOR_MS) FAST_EVERY_MS else SLOW_EVERY_MS
        if (sentAtMs?.let { at - it < every } == true) return 0
        sentAtMs = at
        return knock(now)
    }

    private fun knock(now: Wanted): Int {
        val online = now.hub.links.links.value.keys
        val away = now.hub.registry.screens.value.filter { it.id !in online }.mapNotNull { it.lastAddress }.distinct()
        if (away.isEmpty()) return 0
        val nudge = Nudge(nudgeOwner(now.hub.registry.ownerId), now.port)
        val bytes = SignageJson.encodeToString(Nudge.serializer(), nudge).toByteArray()
        away.forEach { host ->
            try {
                send(host, bytes)
            } catch (e: IOException) {
                Log.info(TAG, "Could not knock on a signage screen: ${e.message}")
            }
        }
        return away.size
    }

    companion object {
        const val FAST_EVERY_MS = 10_000L
        const val FAST_FOR_MS = 2L * 60_000L
        const val SLOW_EVERY_MS = 60_000L
        private const val TAG = "Signage"

        private fun sendUdp(host: String, bytes: ByteArray) {
            DatagramSocket().use { it.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(host), NUDGE_PORT)) }
        }
    }
}
