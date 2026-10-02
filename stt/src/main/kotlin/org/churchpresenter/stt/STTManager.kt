package org.churchpresenter.stt

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URI


data class STTSegment(
    val id: Int,
    val timestamp: String,
    val text: String,
    val start: Double,
    val end: Double,
    val completed: Boolean
)

data class HighlightedWord(
    val word: String,
    val color: String,
    val caseSensitive: Boolean = false,
    val isRegex: Boolean = false
)

class STTManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── Connection state ──────────────────────────────────────────────
    private var socket: Socket? = null
    private val _connected = mutableStateOf(false)
    val connected: State<Boolean> = _connected

    private val _connecting = mutableStateOf(false)
    val connecting: State<Boolean> = _connecting

    // True after a connection attempt fails to reach the STT server (EVENT_CONNECT_ERROR). Stays true
    // while Socket.IO keeps retrying (reconnection is on), so the UI can show "can't reach server".
    // Cleared on a successful connect, on a fresh connect() call, and on disconnect().
    private val _connectError = mutableStateOf(false)
    val connectError: State<Boolean> = _connectError

    // True after an UNEXPECTED disconnect (network drop / server restart) while reconnection keeps
    // retrying — distinct from a user-initiated disconnect(). Lets the UI show "reconnecting…" only
    // when it's actually trying to come back. Cleared on (re)connect and on manual disconnect().
    private val _reconnecting = mutableStateOf(false)
    val reconnecting: State<Boolean> = _reconnecting

    // ── What the server sends, and the Help Dev capture ───────────────
    internal val transcript = STTTranscript(scope)
    internal val capture = STTCapture(scope)

    val segments: List<STTSegment> get() = transcript.segments
    val inProgressText: State<String> get() = transcript.inProgressText
    val translationSegments: List<STTSegment> get() = transcript.translationSegments
    val inProgressTranslation: State<String> get() = transcript.inProgressTranslation
    val translationLanguage: State<String> get() = transcript.translationLanguage
    val highlightedWords: List<HighlightedWord> get() = transcript.highlightedWords
    val wordHighlightingEnabled: State<Boolean> get() = transcript.wordHighlightingEnabled

    /** Whether the Help Dev capture pulls the session's `.db`; see [STTCapture]. */
    var helpDevModeEnabled: Boolean by capture::helpDevModeEnabled

    // ── Display state (is STT currently being projected) ──────────────
    private val _isLive = mutableStateOf(false)
    val isLive: State<Boolean> = _isLive

    fun setLive(live: Boolean) {
        _isLive.value = live
    }

    // ── Socket state transitions ──────────────────────────────────────
    // The four connection flags only ever change together, and every combination means something
    // different to the UI (green dot vs "connecting…" vs "can't reach server" vs "reconnecting…").
    // They live here as named functions rather than inline in connect()'s socket callbacks so the
    // transitions are one testable step each: reaching them through a real socket needs a live STT
    // server, but which flags a given event sets is ordinary logic. Public because the app's own
    // suites (the Bible and STT tabs' screenshots among them) put the connection in a state with them.

    /** An attempt has started: connecting, with any previous failure or retry cleared. */
    fun applyConnecting() {
        _connecting.value = true
        _connectError.value = false
        _reconnecting.value = false
    }

    /** A socket connection came up: live, and no longer connecting/failed/retrying. */
    fun applyConnected() {
        _connected.value = true
        _connecting.value = false
        _connectError.value = false
        _reconnecting.value = false
    }

    /**
     * The socket went down. [reason] is socket.io's disconnect reason: `"io client disconnect"` means
     * we called [disconnect] ourselves, so the link is simply closed. Anything else (transport close,
     * ping timeout, server disconnect) is an unexpected drop that reconnection keeps retrying, which
     * the UI shows as "reconnecting…".
     */
    fun applyDisconnected(reason: String?) {
        _connected.value = false
        if (reason != CLIENT_DISCONNECT_REASON) _reconnecting.value = true
    }

    /** The server could not be reached at all — not connected, not connecting, and flagged failed. */
    fun applyConnectError() {
        _connected.value = false
        _connecting.value = false
        _connectError.value = true
    }

    fun connect(url: String) {
        if (_connected.value || _connecting.value) return
        // Clean up any leftover socket (e.g. from a previous failed connection)
        socket?.off()
        socket?.disconnect()
        socket?.close()
        socket = null

        applyConnecting()

        scope.launch(Dispatchers.IO) {
            try {
                val opts = IO.Options.builder()
                    .setTransports(arrayOf("websocket"))
                    .setReconnection(true)
                    .setReconnectionAttempts(Int.MAX_VALUE)
                    .setReconnectionDelay(RECONNECT_DELAY_MS)
                    .build()
                val s = IO.socket(URI.create(url), opts)
                SOCKET_EVENTS.forEach { event ->
                    s.on(event) { args -> onSocketEvent(event, args, url) { request -> s.emit(request) } }
                }
                socket = s
                s.connect()
            } catch (_: Exception) {
                scope.launch { applyConnectError() }
            }
        }
    }

    /**
     * What one socket event does, apart from the socket that raised it, so each can be driven
     * without a live STT server. [emit] sends a request back down the same socket.
     */
    internal fun onSocketEvent(event: String, args: Array<out Any?>, url: String, emit: (String) -> Unit) {
        when (event) {
            Socket.EVENT_CONNECT -> {
                scope.launch { applyConnected() }
                // Request initial data
                emit("request_all_entries")
                emit("request_all_translation_entries")
                // Fetch word highlighting via REST (not sent on connect via socket)
                scope.launch(Dispatchers.IO) { transcript.fetchWordHighlighting(url) }
                // startDbCapture's loop reads the session id on its first tick, i.e. right now.
                capture.startDbCapture(url)
            }
            Socket.EVENT_DISCONNECT -> {
                val reason = args.firstOrNull()?.toString()
                scope.launch { applyDisconnected(reason) }
            }
            Socket.EVENT_CONNECT_ERROR -> scope.launch { applyConnectError() }
            else -> {
                val data = args.firstOrNull() as? JSONObject ?: return
                val handle: (JSONObject) -> Unit = when (event) {
                    TRANSCRIPTION_UPDATE -> transcript::handleTranscriptionUpdate
                    TRANSLATION_UPDATE -> transcript::handleTranslationUpdate
                    WORD_HIGHLIGHTING_UPDATE -> transcript::handleWordHighlightingUpdate
                    else -> return
                }
                scope.launch { handle(data) }
            }
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.close()
        socket = null
        _connected.value = false
        _connecting.value = false
        _connectError.value = false
        _reconnecting.value = false
        capture.stop()
        leaveSession()
    }

    fun dispose() {
        disconnect()
    }

    internal companion object {
        /** socket.io's disconnect reason when the client itself closed the socket. */
        const val CLIENT_DISCONNECT_REASON = "io client disconnect"
        const val TRANSCRIPTION_UPDATE = "transcription_update"
        const val TRANSLATION_UPDATE = "translation_update"
        const val WORD_HIGHLIGHTING_UPDATE = "word_highlighting_update"
        private const val RECONNECT_DELAY_MS = 2000L

        /** Every event the manager listens for. */
        val SOCKET_EVENTS = listOf(
            Socket.EVENT_CONNECT, Socket.EVENT_DISCONNECT, Socket.EVENT_CONNECT_ERROR,
            TRANSCRIPTION_UPDATE, TRANSLATION_UPDATE, WORD_HIGHLIGHTING_UPDATE,
        )
    }
}
