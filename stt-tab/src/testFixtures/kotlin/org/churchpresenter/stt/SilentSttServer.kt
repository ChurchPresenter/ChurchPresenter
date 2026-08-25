package org.churchpresenter.stt

import java.net.InetAddress
import java.net.ServerSocket

/**
 * A loopback port that accepts TCP connections and then says nothing at all.
 *
 * Used wherever a test clicks Connect. It has to be a real URL, and it has to keep socket.io in its
 * *connecting* state deterministically, because that is what several assertions here are about — so
 * the endpoint has to be reachable at the TCP level and silent above it. Binding a `ServerSocket` and
 * never calling `accept()` does exactly that: the kernel completes the handshake into the backlog and
 * no HTTP response ever comes.
 *
 * The earlier fixture was `http://192.0.2.1:1` (TEST-NET-1). That failed fast on macOS but on a Linux
 * CI runner packets to an unrouted address are dropped rather than refused, so every attempt waited
 * out the full TCP connect timeout while socket.io retried forever
 * (`setReconnectionAttempts(Int.MAX_VALUE)`) — which pushed the whole `jvmTest` task past CI's 25
 * minute step budget. A refused port would have fixed the hang but broken the assertions, since the
 * manager would leave `connecting` before a test could read it.
 *
 * One socket for the whole JVM, opened on first use. It is deliberately never closed: it holds a
 * single file descriptor and the tests that depend on it run throughout the suite.
 */
private val silentSttSocket: ServerSocket by lazy {
    ServerSocket(0, 1, InetAddress.getLoopbackAddress())
}

val SILENT_STT_URL: String
    get() = "http://127.0.0.1:${silentSttSocket.localPort}"
