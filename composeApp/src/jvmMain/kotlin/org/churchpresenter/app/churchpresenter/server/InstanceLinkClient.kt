package org.churchpresenter.app.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.CrashReporter

enum class InstanceLinkStatus { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/**
 * Connects to another ChurchPresenter instance's [CompanionServer] as just another authenticated
 * WebSocket client — the exact protocol a mobile companion device already uses (same `/ws` route,
 * same API-key/device-id headers). Mirrors the primary's live content locally via the callbacks
 * below, and can push new schedule items back through the primary's existing add-to-schedule
 * approval flow ([sendAddToSchedule]).
 *
 * Connect/reconnect loop modeled directly on [org.churchpresenter.app.churchpresenter.viewmodel.BibleEngineClient],
 * which already does this shape of thing (Ktor CIO client, generation-guarded reconnect loop)
 * against another local server.
 */
class InstanceLinkClient(
    private val onStatusChanged: (InstanceLinkStatus) -> Unit,
    private val onScheduleUpdated: (List<ScheduleItemDto>) -> Unit,
    private val onLiveStateUpdated: (LiveStateDto) -> Unit,
    private val onDisplayCleared: () -> Unit,
    private val onSongSectionSelected: (Int) -> Unit,
    private val onPresentationSlideChanged: (id: String, index: Int, total: Int, isPlaying: Boolean, isLive: Boolean) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = HttpClient(CIO) {
        install(WebSockets)
        install(HttpTimeout) { connectTimeoutMillis = 5_000 }
    }
    private val json = Json { ignoreUnknownKeys = true }

    private var connectJob: Job? = null
    @Volatile private var session: DefaultClientWebSocketSession? = null

    // Bumped on every connect()/disconnect() so a stale, cooperatively-cancelling old loop
    // iteration can't clobber a newer connection's state (Socket/WS reads aren't real suspension
    // points, so cancellation alone isn't enough) — same guard CompanionSatelliteClient uses.
    @Volatile private var generation = 0L

    /** Starts (or restarts) the link to the primary instance's CompanionServer. */
    fun connect(host: String, port: Int, apiKey: String, deviceId: String, reconnectDelayMs: Long) {
        disconnect()
        val myGeneration = ++generation
        connectJob = scope.launch {
            connectLoop(host, port, apiKey, deviceId, reconnectDelayMs, myGeneration)
        }
    }

    private suspend fun connectLoop(
        host: String,
        port: Int,
        apiKey: String,
        deviceId: String,
        reconnectDelayMs: Long,
        myGeneration: Long
    ) {
        while (scope.isActive && generation == myGeneration) {
            onStatusChanged(InstanceLinkStatus.CONNECTING)
            try {
                httpClient.webSocket(
                    method = HttpMethod.Get,
                    host = host,
                    port = port,
                    path = Constants.ENDPOINT_WS,
                    request = {
                        if (apiKey.isNotEmpty()) header(Constants.HEADER_API_KEY, apiKey)
                        header(Constants.HEADER_DEVICE_ID, deviceId)
                        header(Constants.HEADER_CLIENT_ROLE, Constants.CLIENT_ROLE_INSTANCE_LINK)
                    }
                ) {
                    if (generation != myGeneration) return@webSocket
                    session = this
                    onStatusChanged(InstanceLinkStatus.CONNECTED)
                    for (frame in incoming) {
                        if (generation != myGeneration) break
                        if (frame is Frame.Text) handleMessage(frame.readText())
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                System.err.println("InstanceLink: connect to ws://$host:$port${Constants.ENDPOINT_WS} failed — ${e.message}")
                CrashReporter.reportWarning(
                    "InstanceLink: connection failed — ${e.message}",
                    tags = mapOf("subsystem" to "instance_link")
                )
            }
            session = null
            if (generation != myGeneration) return
            onStatusChanged(InstanceLinkStatus.ERROR)
            if (!scope.isActive) break
            delay(reconnectDelayMs)
        }
    }

    private fun handleMessage(raw: String) {
        val msg = runCatching { json.decodeFromString(WebSocketMessage.serializer(), raw) }.getOrNull() ?: return
        when (msg.type) {
            Constants.WS_EVENT_SCHEDULE_UPDATED -> {
                val response = runCatching { json.decodeFromString(ScheduleResponse.serializer(), msg.payload) }.getOrNull()
                    ?: return
                onScheduleUpdated(response.items)
            }
            Constants.WS_EVENT_LIVE_STATE_CHANGED -> {
                val state = runCatching { json.decodeFromString(LiveStateDto.serializer(), msg.payload) }.getOrNull()
                    ?: return
                onLiveStateUpdated(state)
            }
            Constants.WS_EVENT_DISPLAY_CLEARED -> onDisplayCleared()
            Constants.WS_EVENT_SONG_SECTION_SELECTED -> {
                val index = msg.payload.toIntOrNull() ?: return
                onSongSectionSelected(index)
            }
            Constants.WS_EVENT_PRESENTATION_SLIDE_CHANGED -> {
                val obj = runCatching { json.parseToJsonElement(msg.payload).jsonObject }.getOrNull() ?: return
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return
                val index = obj["index"]?.jsonPrimitive?.intOrNull ?: return
                val total = obj["total"]?.jsonPrimitive?.intOrNull ?: return
                val isPlaying = obj["isPlaying"]?.jsonPrimitive?.booleanOrNull ?: false
                val isLive = obj["isLive"]?.jsonPrimitive?.booleanOrNull ?: false
                onPresentationSlideChanged(id, index, total, isPlaying, isLive)
            }
        }
    }

    /**
     * Sends a new item to the primary's schedule — still subject to the primary operator's
     * existing approval dialog (same [Constants.WS_CMD_ADD_TO_SCHEDULE] flow a mobile client uses).
     * Fire-and-forget: approval/denial is observed indirectly via the next schedule_updated broadcast.
     */
    fun sendAddToSchedule(item: ScheduleItem) {
        val activeSession = session ?: return
        val payload = json.encodeToString(AddToScheduleRequest.serializer(), AddToScheduleRequest(item))
        scope.launch {
            runCatching {
                activeSession.send(
                    Frame.Text(
                        json.encodeToString(
                            WebSocketMessage.serializer(),
                            WebSocketMessage(type = Constants.WS_CMD_ADD_TO_SCHEDULE, payload = payload)
                        )
                    )
                )
            }
        }
    }

    /** Stops the link without disposing the underlying HTTP client (safe to [connect] again). */
    fun disconnect() {
        generation++
        connectJob?.cancel()
        connectJob = null
        session = null
        onStatusChanged(InstanceLinkStatus.DISCONNECTED)
    }

    fun dispose() {
        disconnect()
        runCatching { httpClient.close() }
        scope.cancel()
    }
}
