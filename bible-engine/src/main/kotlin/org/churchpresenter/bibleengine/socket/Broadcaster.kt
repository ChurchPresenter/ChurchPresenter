package org.churchpresenter.bibleengine.socket

import org.churchpresenter.bibleengine.engine.ScriptureEvent
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * Fans emitted events out to every connected WebSocket client without ever blocking the
 * producer: each session gets its own bounded channel (drop-oldest) drained by a dedicated
 * sender coroutine, so one slow/stalled client can no longer back-pressure the detection
 * pipeline or the STT ingest thread (which previously `runBlocking`-sent to every session
 * sequentially). Dropping the oldest frame is safe — scripture events are idempotent UI
 * hints, and a starved client's next frame supersedes what it missed. (A dropped
 * `scripture.detected` may be followed by an `updated` for the same ref; consumers treat
 * both alike.)
 *
 * [broadcastStatus] and [broadcastVersion] additionally cache their latest message and replay
 * it to every newly registered session, so a late-joining ChurchPresenter learns the engine's
 * STT link state and the version being read without waiting for the next transition. Both are
 * standing state rather than events, which is why they are replayed and scripture is not.
 */
class Broadcaster {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { encodeDefaults = true }
    private val sessions = ConcurrentHashMap<WebSocketSession, Channel<String>>()
    @Volatile private var latestStatus: String? = null
    @Volatile private var latestVersion: String? = null

    fun register(session: WebSocketSession) {
        val channel = Channel<String>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        sessions[session] = channel
        scope.launch {
            try {
                for (msg in channel) session.send(Frame.Text(msg))
            } catch (_: Exception) {
                // session died — the socket handler's finally block unregisters it
            }
        }
        latestStatus?.let { channel.trySend(it) }
        latestVersion?.let { channel.trySend(it) }
    }

    fun unregister(session: WebSocketSession) {
        sessions.remove(session)?.close()
    }

    /** Encodes once and enqueues to every session; never suspends, never blocks the caller. */
    fun broadcast(event: ScriptureEvent) {
        val text = json.encodeToString(event)
        for (channel in sessions.values) channel.trySend(text)
    }

    /** Sends an engine-status JSON message to all sessions and replays it to future ones. */
    fun broadcastStatus(statusJson: String) {
        latestStatus = statusJson
        for (channel in sessions.values) channel.trySend(statusJson)
    }

    /**
     * Sends the detected Bible version to all sessions and replays it to future ones.
     *
     * Separate from the scripture events because the answer is established asynchronously, several
     * verses after the detection that first hinted at it — so it can never ride the event that
     * produced it. Clients apply it to rows already on screen.
     */
    fun broadcastVersion(versionJson: String) {
        latestVersion = versionJson
        for (channel in sessions.values) channel.trySend(versionJson)
    }

    fun close() {
        for (channel in sessions.values) channel.close()
        sessions.clear()
        scope.cancel()
    }
}
