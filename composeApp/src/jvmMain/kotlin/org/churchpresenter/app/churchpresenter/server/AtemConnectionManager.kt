package org.churchpresenter.app.churchpresenter.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Singleton that maintains a single reusable [AtemClient] connection.
 *
 * The ATEM will silently drop an idle UDP session after ~5 s, so we use
 * lazy-reconnect: if any operation throws (stale session), the cached client
 * is discarded and the next [use] call reconnects transparently.
 *
 * [AtemClient] is NOT thread-safe — the [Mutex] ensures all operations are
 * serialised.
 */
object AtemConnectionManager {

    private val mutex = Mutex()
    private var client: AtemClient? = null
    private var cachedHost: String = ""
    private var cachedPort: Int = 9910

    /**
     * Acquire the shared client for [host]:[port], ensuring it is connected
     * (with full state if [needsState] is true), then run [block].
     *
     * On exception the cached client is invalidated so the next call reconnects.
     */
    suspend fun <T> use(
        host: String,
        port: Int = 9910,
        needsState: Boolean = false,
        block: suspend (AtemClient) -> T
    ): T = mutex.withLock {
        val c = ensureConnected(host, port, needsState)
        try {
            block(c)
        } catch (e: Exception) {
            client?.disconnect()
            client = null
            throw e
        }
    }

    /**
     * Like [use], but non-blocking on contention: if the shared connection is busy
     * (e.g. a clip upload holds it), returns false immediately instead of waiting, so the
     * caller can fall back to a separate short-lived connection. Returns true if [block] ran.
     * A failure inside [block] still throws (distinct from the "busy" false).
     */
    suspend fun tryRun(
        host: String,
        port: Int = 9910,
        needsState: Boolean = false,
        block: suspend (AtemClient) -> Unit
    ): Boolean {
        if (!mutex.tryLock()) return false
        try {
            val c = ensureConnected(host, port, needsState)
            try {
                block(c)
            } catch (e: Exception) {
                client?.disconnect()
                client = null
                throw e
            }
            return true
        } finally {
            mutex.unlock()
        }
    }

    /** Immediately closes the cached connection (e.g. when ATEM settings change). */
    fun invalidate() {
        client?.disconnect()
        client = null
        cachedHost = ""
        cachedPort = 9910
    }

    private suspend fun ensureConnected(host: String, port: Int, needsState: Boolean): AtemClient {
        val existing = client
        // Reconnect when there is no client, the target changed, or the keepalive loop
        // tore the socket down because the ATEM went silent.
        if (existing == null || !existing.isAlive() || host != cachedHost || port != cachedPort) {
            existing?.disconnect()
            return openConnection(host, port, collectState = needsState)
        }
        if (needsState && existing.lastKnownState == null) {
            existing.disconnect()
            return openConnection(host, port, collectState = true)
        }
        return existing
    }

    private suspend fun openConnection(host: String, port: Int, collectState: Boolean): AtemClient {
        val c = AtemClient(host, port)
        // keepAlive = true: hold the session open across calls so reused operations
        // never hit a stale-session timeout.
        withContext(Dispatchers.IO) { c.connect(collectState = collectState, keepAlive = true) }
        client = c
        cachedHost = host
        cachedPort = port
        return c
    }
}
