package org.churchpresenter.atem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.churchpresenter.diagnostics.CrashReporter

private const val DEFAULT_ATEM_PORT = 9910

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
     * Bumped by [invalidate]. A connect that was already in flight when the cache was dropped must
     * not put itself back: [invalidate] does not hold [mutex] -- it cannot, it is not suspending and
     * is called from settings changes and teardown -- so without this the sequence "upload connects,
     * settings change invalidates, connect completes" leaves the manager caching a client for the
     * endpoint the operator just moved away from. It reports [AtemClient.isAlive] as true forever
     * after, because for UDP that is only `socket != null`, so nothing later notices and every
     * command goes to a switcher that is no longer listening.
     */
    @Volatile
    private var generation: Int = 0

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
        runInvalidatingOnFailure(ensureConnected(host, port, needsState), block)
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
            runInvalidatingOnFailure(ensureConnected(host, port, needsState), block)
            return true
        } finally {
            mutex.unlock()
        }
    }

    /**
     * Runs [block] against [client], discarding the cached connection unless it completes.
     *
     * `finally` on a success flag rather than `catch (e: Exception) { …; throw e }`, which named
     * the exception only to rethrow it untouched and covered nothing outside `Exception`. The
     * reason this manager exists is that an ATEM silently expires an idle session, and what
     * surfaces from a stale one is whatever the caller's own command happened to throw — so
     * anything other than a completed call means "reconnect next time", cancellation included,
     * and the cause travels on unchanged.
     */
    private suspend fun <T> runInvalidatingOnFailure(client: AtemClient, block: suspend (AtemClient) -> T): T {
        var completed = false
        try {
            val result = block(client)
            completed = true
            return result
        } finally {
            if (!completed) {
                this.client?.disconnect()
                this.client = null
            }
        }
    }

    /** Immediately closes the cached connection (e.g. when ATEM settings change). */
    fun invalidate() {
        generation++
        if (client != null) CrashReporter.breadcrumb("ATEM disconnected", category = "integration")
        client?.disconnect()
        client = null
        cachedHost = ""
        cachedPort = DEFAULT_ATEM_PORT
    }

    private suspend fun ensureConnected(host: String, port: Int, needsState: Boolean): AtemClient {
        val existing = client
        // Reconnect when there is no client, the target changed, or the keepalive loop
        // tore the socket down because the ATEM went silent.
        val endpointChanged = host != cachedHost || port != cachedPort
        if (existing == null || !existing.isAlive() || endpointChanged) {
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
        val openedAt = generation
        val c = AtemClient(host, port)
        // keepAlive = true: hold the session open across calls so reused operations
        // never hit a stale-session timeout.
        withContext(Dispatchers.IO) { c.connect(collectState = collectState, keepAlive = true) }
        if (openedAt != generation) {
            // Invalidated while this was connecting: hand the caller its connection, since it asked
            // for one and is holding the mutex, but do not cache what is already known to be stale.
            CrashReporter.breadcrumb(
                "ATEM connection discarded (invalidated while connecting)",
                category = "integration",
            )
            return c
        }
        client = c
        cachedHost = host
        cachedPort = port
        CrashReporter.breadcrumb("ATEM connected ($host:$port)", category = "integration")
        return c
    }
}
