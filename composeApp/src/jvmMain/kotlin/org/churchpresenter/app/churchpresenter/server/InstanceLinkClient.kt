package org.churchpresenter.app.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
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
import org.churchpresenter.app.churchpresenter.data.settings.BackgroundSettings
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import org.churchpresenter.app.churchpresenter.utils.InstanceLinkLogSide
import org.churchpresenter.app.churchpresenter.utils.InstanceLinkLogger

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
    private val onSongsUpdated: (SongCatalogResponse) -> Unit,
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

    // Captured on connect() so fetchSongDetail() (called on-demand, outside the connect loop) can
    // build REST URLs against the same primary without needing its own host/port/key parameters.
    @Volatile private var currentHost: String = ""
    @Volatile private var currentPort: Int = 0
    @Volatile private var currentApiKey: String = ""

    /** Starts (or restarts) the link to the primary instance's CompanionServer. */
    fun connect(host: String, port: Int, apiKey: String, deviceId: String, reconnectDelayMs: Long) {
        disconnect()
        currentHost = host
        currentPort = port
        currentApiKey = apiKey
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
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "connect_attempt",
                mapOf("host" to host, "port" to port)
            )
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
                    InstanceLinkLogger.log(
                        InstanceLinkLogSide.FOLLOWER, "connect_result",
                        mapOf("success" to true, "host" to host, "port" to port)
                    )
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
                InstanceLinkLogger.log(
                    InstanceLinkLogSide.FOLLOWER, "connect_result",
                    mapOf("success" to false, "host" to host, "port" to port, "reason" to e.message)
                )
            }
            session = null
            if (generation != myGeneration) return
            onStatusChanged(InstanceLinkStatus.ERROR)
            if (!scope.isActive) break
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "reconnecting",
                mapOf("host" to host, "port" to port, "delayMs" to reconnectDelayMs)
            )
            delay(reconnectDelayMs)
        }
    }

    private fun handleMessage(raw: String) {
        val msg = runCatching { json.decodeFromString(WebSocketMessage.serializer(), raw) }.getOrNull()
        if (msg == null) {
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "ws_message_decode_failed",
                mapOf("context" to "envelope")
            )
            return
        }
        InstanceLinkLogger.log(InstanceLinkLogSide.FOLLOWER, "ws_message_received", mapOf("type" to msg.type))
        when (msg.type) {
            Constants.WS_EVENT_SCHEDULE_UPDATED -> {
                val response = runCatching { json.decodeFromString(ScheduleResponse.serializer(), msg.payload) }.getOrNull()
                if (response == null) {
                    InstanceLinkLogger.log(InstanceLinkLogSide.FOLLOWER, "ws_message_decode_failed", mapOf("context" to "schedule"))
                    return
                }
                onScheduleUpdated(response.items)
            }
            Constants.WS_EVENT_LIVE_STATE_CHANGED -> {
                val state = runCatching { json.decodeFromString(LiveStateDto.serializer(), msg.payload) }.getOrNull()
                if (state == null) {
                    InstanceLinkLogger.log(InstanceLinkLogSide.FOLLOWER, "ws_message_decode_failed", mapOf("context" to "live_state"))
                    return
                }
                onLiveStateUpdated(state)
            }
            Constants.WS_EVENT_SONGS_UPDATED -> {
                val catalog = runCatching { json.decodeFromString(SongCatalogResponse.serializer(), msg.payload) }.getOrNull()
                if (catalog == null) {
                    InstanceLinkLogger.log(InstanceLinkLogSide.FOLLOWER, "ws_message_decode_failed", mapOf("context" to "song_catalog"))
                    return
                }
                onSongsUpdated(catalog)
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
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "ws_command_sent",
                mapOf("type" to Constants.WS_CMD_ADD_TO_SCHEDULE)
            )
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

    /** Sends a generic command payload to the primary — shared by every send* method below, same
     *  fire-and-forget shape as [sendAddToSchedule]. */
    private fun sendCommand(type: String, payload: String) {
        val activeSession = session ?: return
        scope.launch {
            InstanceLinkLogger.log(InstanceLinkLogSide.FOLLOWER, "ws_command_sent", mapOf("type" to type))
            runCatching {
                activeSession.send(
                    Frame.Text(json.encodeToString(WebSocketMessage.serializer(), WebSocketMessage(type = type, payload = payload)))
                )
            }
        }
    }

    /**
     * Instance Link "Controller" mode — Controller mode drives the primary's live output the same
     * way an already-trusted mobile companion device can, using the exact same WS commands the
     * primary already fully supports for any authenticated client (no CompanionServer changes needed).
     */

    /** Universal "go live with this new item" — [Constants.WS_CMD_PROJECT], approval-gated on the
     *  primary the first time this device is seen (same [dialogs.RemoteEventDialog] flow a mobile
     *  client goes through), instant afterwards once trusted. Covers Bible/Songs/Pictures/
     *  Presentations/Media alike via the primary's existing per-subtype `executeProjectItem` dispatch. */
    fun sendProject(item: ScheduleItem) {
        sendCommand(Constants.WS_CMD_PROJECT, json.encodeToString(ProjectRequest.serializer(), ProjectRequest(item)))
    }

    /** Instantly displays a Bible verse — [Constants.WS_CMD_SELECT_BIBLE_VERSE], no approval gate on
     *  the primary (same as a mobile client). Used for every verse go-live in Controller mode, not
     *  just the first — the command is already instant either way. */
    fun sendSelectBibleVerse(bookName: String, chapter: Int, verseNumber: Int, verseText: String, verseRange: String) {
        sendCommand(
            Constants.WS_CMD_SELECT_BIBLE_VERSE,
            json.encodeToString(SelectBibleVerseRequest.serializer(), SelectBibleVerseRequest(bookName, chapter, verseNumber, verseText, verseRange))
        )
    }

    /** Instantly displays a picture — [Constants.WS_CMD_SELECT_PICTURE], no approval gate. */
    fun sendSelectPicture(folderId: String, index: Int, fileName: String?) {
        sendCommand(
            Constants.WS_CMD_SELECT_PICTURE,
            json.encodeToString(SelectPictureRequest.serializer(), SelectPictureRequest(folderId, index, fileName))
        )
    }

    /** Instantly navigates within an already-live song — [Constants.WS_CMD_SELECT_SONG_SECTION], no
     *  approval gate. Picking a *different* song still needs [sendProject] first. */
    fun sendSelectSongSection(number: String, section: Int) {
        sendCommand(
            Constants.WS_CMD_SELECT_SONG_SECTION,
            json.encodeToString(SelectSongSectionRequest.serializer(), SelectSongSectionRequest(number, section))
        )
    }

    /** Instantly navigates within an already-live presentation — [Constants.WS_CMD_SELECT_SLIDE], no
     *  approval gate. Picking a *different* presentation still needs [sendProject] first. */
    fun sendSelectSlide(id: String, index: Int) {
        sendCommand(
            Constants.WS_CMD_SELECT_SLIDE,
            json.encodeToString(SelectSlideRequest.serializer(), SelectSlideRequest(id, index))
        )
    }

    /** Instantly clears the primary's display — [Constants.WS_CMD_CLEAR], no payload, no approval gate. */
    fun sendClear() {
        sendCommand(Constants.WS_CMD_CLEAR, "")
    }

    /** Toggles Bible hold on the primary — [Constants.WS_CMD_BIBLE_HOLD]. No formal DTO exists for
     *  this on the primary either (it parses the raw `{"hold":bool}` JSON directly), so this builds
     *  the same ad-hoc payload rather than introducing a serializer class for a single boolean. */
    fun sendBibleHold(hold: Boolean) {
        sendCommand(Constants.WS_CMD_BIBLE_HOLD, """{"hold":$hold}""")
    }

    /** Logs the outcome of one `fetch*` call below — [kind] identifies which one (e.g. "bible_file",
     *  "picture_bytes"), symmetric with the primary's `rest_request` log line for the same hit. */
    private fun logFetch(kind: String, success: Boolean, status: Int? = null, reason: String? = null) {
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "fetch_result",
            mapOf("kind" to kind, "success" to success, "status" to status, "reason" to reason)
        )
    }

    /**
     * Fetches full song detail (sections/lyrics) from the primary on demand — the catalog broadcast
     * only carries metadata (title/number/tune/author), so this is called lazily when a song is
     * actually selected rather than for the whole library upfront (which could mean thousands of
     * requests for a large library).
     */
    suspend fun fetchSongDetail(number: String, songbook: String): SongDetailDto? {
        if (currentHost.isEmpty()) {
            logFetch("song_detail", success = false, reason = "not_connected")
            return null
        }
        return runCatching {
            val response = httpClient.get("http://$currentHost:$currentPort${Constants.ENDPOINT_SONGS}/$number") {
                if (currentApiKey.isNotEmpty()) header(Constants.HEADER_API_KEY, currentApiKey)
                if (songbook.isNotEmpty()) parameter(Constants.QUERY_PARAM_SONGBOOK, songbook)
            }
            if (!response.status.isSuccess()) {
                logFetch("song_detail", success = false, status = response.status.value, reason = "http_status")
                return null
            }
            logFetch("song_detail", success = true, status = response.status.value)
            json.decodeFromString(SongDetailDto.serializer(), response.bodyAsText())
        }.onFailure { e -> logFetch("song_detail", success = false, reason = e.message) }.getOrNull()
    }

    /** Fetches one picture's raw bytes from the primary — used to mirror a live picture (resolved
     *  via [LiveStateDto.pictureFolderId]/[LiveStateDto.pictureIndex]) without a local copy of it. */
    suspend fun fetchPictureImageBytes(folderId: String, index: Int): ByteArray? {
        if (currentHost.isEmpty()) {
            logFetch("picture_bytes", success = false, reason = "not_connected")
            return null
        }
        return runCatching {
            val response = httpClient.get("http://$currentHost:$currentPort${Constants.ENDPOINT_PICTURES}/$folderId/images/$index") {
                if (currentApiKey.isNotEmpty()) header(Constants.HEADER_API_KEY, currentApiKey)
            }
            if (!response.status.isSuccess()) {
                logFetch("picture_bytes", success = false, status = response.status.value, reason = "http_status")
                return null
            }
            logFetch("picture_bytes", success = true, status = response.status.value)
            response.readRawBytes()
        }.onFailure { e -> logFetch("picture_bytes", success = false, reason = e.message) }.getOrNull()
    }

    /** Fetches one presentation slide's raw bytes from the primary — mirrors [RemotePresentationSlide]
     *  (from the existing presentation_slide_changed broadcast) without a local copy of the file. */
    suspend fun fetchPresentationSlideBytes(id: String, index: Int): ByteArray? {
        if (currentHost.isEmpty()) {
            logFetch("presentation_slide", success = false, reason = "not_connected")
            return null
        }
        return runCatching {
            val response = httpClient.get("http://$currentHost:$currentPort${Constants.ENDPOINT_PRESENTATIONS}/$id/slides/$index") {
                if (currentApiKey.isNotEmpty()) header(Constants.HEADER_API_KEY, currentApiKey)
            }
            if (!response.status.isSuccess()) {
                logFetch("presentation_slide", success = false, status = response.status.value, reason = "http_status")
                return null
            }
            logFetch("presentation_slide", success = true, status = response.status.value)
            response.readRawBytes()
        }.onFailure { e -> logFetch("presentation_slide", success = false, reason = e.message) }.getOrNull()
    }

    /**
     * Downloads the primary's raw .spb bible file bytes — the caller loads it through the same
     * Bible.loadFromSpb() used for local files instead of reimplementing that engine against the API.
     */
    suspend fun fetchBibleFile(): ByteArray? {
        if (currentHost.isEmpty()) {
            logFetch("bible_file", success = false, reason = "not_connected")
            return null
        }
        return runCatching {
            val response = httpClient.get("http://$currentHost:$currentPort${Constants.ENDPOINT_BIBLE_FILE}") {
                if (currentApiKey.isNotEmpty()) header(Constants.HEADER_API_KEY, currentApiKey)
            }
            if (!response.status.isSuccess()) {
                logFetch("bible_file", success = false, status = response.status.value, reason = "http_status")
                return null
            }
            logFetch("bible_file", success = true, status = response.status.value)
            response.readRawBytes()
        }.onFailure { e -> logFetch("bible_file", success = false, reason = e.message) }.getOrNull()
    }

    /** Downloads the primary's raw secondary .spb bible file — only used when the follower opted in
     *  to mirroring the primary's secondary bible instead of keeping its own local one. */
    suspend fun fetchSecondaryBibleFile(): ByteArray? {
        if (currentHost.isEmpty()) {
            logFetch("secondary_bible_file", success = false, reason = "not_connected")
            return null
        }
        return runCatching {
            val response = httpClient.get("http://$currentHost:$currentPort${Constants.ENDPOINT_BIBLE_FILE}/secondary") {
                if (currentApiKey.isNotEmpty()) header(Constants.HEADER_API_KEY, currentApiKey)
            }
            if (!response.status.isSuccess()) {
                logFetch("secondary_bible_file", success = false, status = response.status.value, reason = "http_status")
                return null
            }
            logFetch("secondary_bible_file", success = true, status = response.status.value)
            response.readRawBytes()
        }.onFailure { e -> logFetch("secondary_bible_file", success = false, reason = e.message) }.getOrNull()
    }

    /** Fetches one lower-third preset's raw Lottie JSON by name — see [Constants.ENDPOINT_LOWER_THIRDS]. */
    suspend fun fetchLowerThirdJson(name: String): ByteArray? {
        if (currentHost.isEmpty()) {
            logFetch("lower_third_json", success = false, reason = "not_connected")
            return null
        }
        // Path segment, not a query param: URLEncoder turns spaces into "+", which Ktor's route
        // parameter decoding does NOT turn back into a space (that only happens for query/form
        // encoding) — swap it for "%20" so a name with spaces still resolves on the server.
        val encodedName = java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        return runCatching {
            val response = httpClient.get("http://$currentHost:$currentPort${Constants.ENDPOINT_LOWER_THIRDS}/$encodedName/json") {
                if (currentApiKey.isNotEmpty()) header(Constants.HEADER_API_KEY, currentApiKey)
            }
            if (!response.status.isSuccess()) {
                logFetch("lower_third_json", success = false, status = response.status.value, reason = "http_status")
                return null
            }
            logFetch("lower_third_json", success = true, status = response.status.value)
            response.readRawBytes()
        }.onFailure { e -> logFetch("lower_third_json", success = false, reason = e.message) }.getOrNull()
    }

    /** Fetches the primary's current background settings — only used when the follower opted in to
     *  mirroring backgrounds (InstanceLinkSettings.mirrorBackgrounds). Image/video fields are still
     *  the primary's own local file paths; use [fetchBackgroundAsset] (keyed by slot) for bytes. */
    suspend fun fetchBackgroundSettings(): BackgroundSettings? {
        if (currentHost.isEmpty()) {
            logFetch("background_settings", success = false, reason = "not_connected")
            return null
        }
        return runCatching {
            val response = httpClient.get("http://$currentHost:$currentPort${Constants.ENDPOINT_BACKGROUNDS}") {
                if (currentApiKey.isNotEmpty()) header(Constants.HEADER_API_KEY, currentApiKey)
            }
            if (!response.status.isSuccess()) {
                logFetch("background_settings", success = false, status = response.status.value, reason = "http_status")
                return null
            }
            logFetch("background_settings", success = true, status = response.status.value)
            json.decodeFromString(BackgroundSettings.serializer(), response.bodyAsText())
        }.onFailure { e -> logFetch("background_settings", success = false, reason = e.message) }.getOrNull()
    }

    /** Fetches one background slot's raw image/video bytes by slot name — see
     *  [Constants.BACKGROUND_SLOT_DEFAULT] and siblings for the shared slot vocabulary. */
    suspend fun fetchBackgroundAsset(slot: String, isVideo: Boolean): ByteArray? {
        if (currentHost.isEmpty()) {
            logFetch("background_asset", success = false, reason = "not_connected")
            return null
        }
        return runCatching {
            val response = httpClient.get("http://$currentHost:$currentPort${Constants.ENDPOINT_BACKGROUNDS}/asset/$slot") {
                if (currentApiKey.isNotEmpty()) header(Constants.HEADER_API_KEY, currentApiKey)
                parameter("type", if (isVideo) "video" else "image")
            }
            if (!response.status.isSuccess()) {
                logFetch("background_asset", success = false, status = response.status.value, reason = "http_status")
                return null
            }
            logFetch("background_asset", success = true, status = response.status.value)
            response.readRawBytes()
        }.onFailure { e -> logFetch("background_asset", success = false, reason = e.message) }.getOrNull()
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
