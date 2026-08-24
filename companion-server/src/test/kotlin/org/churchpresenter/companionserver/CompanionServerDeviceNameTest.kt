package org.churchpresenter.companionserver

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.churchpresenter.settings.utils.Constants
import org.junit.AfterClass
import org.junit.BeforeClass
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the desktop learns a connecting device is called — ChurchPresenter#381.
 *
 * The desktop shows the operator a name instead of `3f7c1a9e-…` only if the name has been recorded
 * **before** the approval prompt is raised, which is why the reporting is a plain callback rather
 * than a flow and why [`the name is known before the operator is asked`] asserts the order rather
 * than merely that both happened.
 *
 * The transports differ and both are covered: HTTP carries a header, and the WebSocket handshake
 * carries a query parameter of the same name, because a browser cannot set headers there.
 */
class CompanionServerDeviceNameTest {

    private lateinit var client: HttpClient

    companion object {
        private lateinit var server: CompanionServer
        private var port: Int = 0

        @JvmStatic
        @BeforeClass
        fun startServer() {
            server = CompanionServer()
            server.start(port = testPort(39_949))
            port = runBlocking {
                withTimeoutOrNull(10_000) {
                    while (!server.isRunning.value || server.serverUrl.value.isBlank()) {
                        kotlinx.coroutines.delay(25)
                    }
                    server.serverUrl.value.substringAfterLast(':').toInt()
                }
            } ?: error("server did not start")
        }

        @JvmStatic
        @AfterClass
        fun stopServer() {
            runCatching { server.stop() }
        }
    }

    /** Every (id, name) the server reported, in order. */
    private val reported = CopyOnWriteArrayList<Pair<String, String>>()
    private var operatorScope: CoroutineScope? = null

    @BeforeTest
    fun setUp() {
        client = HttpClient(CIO) { install(WebSockets) }
        reported.clear()
        server.blockedClientIds = emptySet()
        server.presentationRemoteEnabled = true
        server.onDeviceNameReported = { id, name -> reported += id to name }
    }

    @AfterTest
    fun tearDown() {
        // Joined, not merely cancelled: a collector that is still detaching when the next test
        // counts subscribers is what made this suite flake one run in three.
        runBlocking { operatorScope?.coroutineContext?.job?.cancelAndJoin() }
        operatorScope = null
        server.onDeviceNameReported = { _, _ -> }
        runCatching { client.close() }
    }

    /** Everything the operator was asked to approve, in the order it was asked. */
    private val asked = CopyOnWriteArrayList<String>()

    /** An operator behind the presentation-remote handshake who approves everything. */
    private fun operatorApproving() {
        val scope = CoroutineScope(Dispatchers.IO + Job()).also { operatorScope = it }
        // The flow allows outright when nobody is collecting, so sending before this collector is
        // attached would test nothing and pass. Waited on `onSubscription`, which runs once this
        // subscription is registered — a subscriber *count* also counts the previous test's
        // collector on its way out, and then never rises.
        val attached = CompletableDeferred<Unit>()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            server.onPresentationRemoteConnect
                .onSubscription { attached.complete(Unit) }
                .collect { pending ->
                    // Read as the prompt is raised: this is the name the dialog would show.
                    asked += reported.lastOrNull { it.first == pending.clientId }?.second ?: ""
                    pending.decision.complete(true)
                }
        }
        // UNDISPATCHED above runs the body on this thread up to its first suspension, and
        // `onSubscription` fires once the subscription is registered — so this is already complete.
        // It used to be a two-second `withTimeoutOrNull`, which depends on the IO dispatcher handing
        // out a thread in time and fails under CI load. Do not put the wait back.
        check(attached.isCompleted) { "the operator collector must attach synchronously" }
    }

    private fun connect(deviceId: String, name: String?) = runBlocking {
        client.post("http://127.0.0.1:$port/api/presentation-remote/auth") {
            header(Constants.HEADER_DEVICE_ID, deviceId)
            name?.let { header(Constants.HEADER_DEVICE_NAME, it) }
            setBody("{}")
        }
    }

    @Test
    fun `the name is known before the operator is asked`() {
        operatorApproving()

        connect("phone-1", "Sound desk iPad")

        assertEquals(listOf("Sound desk iPad"), asked.toList())
    }

    @Test
    fun `a device that sends no name is left to its id`() {
        operatorApproving()

        connect("phone-2", null)

        assertEquals(emptyList(), reported.toList())
        assertEquals(listOf(""), asked.toList())
    }

    @Test
    fun `a percent-encoded name arrives as what the operator will read`() {
        operatorApproving()

        connect("phone-3", "%D0%A1%D0%B5%D1%80%D1%91%D0%B6%D0%B8%D0%BD%20Pixel")

        assertEquals(listOf("phone-3" to "Серёжин Pixel"), reported.toList())
    }

    @Test
    fun `an anonymous device reports nothing, there being nothing to name`() {
        operatorApproving()

        connect("", "Sound desk iPad")

        assertEquals(emptyList(), reported.toList())
    }

    @Test
    fun `a WebSocket names its device through the query parameter a browser can send`() {
        val url = "ws://127.0.0.1:$port/ws" +
            "?${Constants.HEADER_DEVICE_ID}=browser-1" +
            "&${Constants.HEADER_DEVICE_NAME}=Foyer%2520screen"
        runBlocking {
            withTimeoutOrNull(5_000) {
                client.webSocket(url) {
                    // Connected is enough: the name is read as the socket is accepted.
                }
            } ?: error("the socket never connected")
        }

        assertTrue(
            reported.contains("browser-1" to "Foyer screen"),
            "expected the browser to have been named, got $reported",
        )
    }
}
