package org.churchpresenter.app.churchpresenter.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.data.SongItem
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.utils.Constants
import java.net.NetworkInterface

// ── API DTOs ─────────────────────────────────────────────────────────────────

@Serializable
data class SongDto(
    val number: String,
    val title: String,
    val tune: String = "",
    val author: String = ""
)

/** One songbook entry — contains its songs inline. */
@Serializable
data class SongbookEntry(
    @kotlinx.serialization.SerialName("book-name")   val bookName: String,
    @kotlinx.serialization.SerialName("song-total")  val songTotal: Int,
    val songs: List<SongDto>
)

/**
 * Top-level response for /api/songs and the WS songs_updated event.
 *
 * {
 *   "song-book": [ { "book-name": "…", "song-total": 100, "songs": […] } ],
 *   "songBooks": 3,
 *   "total": 6255
 * }
 */
@Serializable
data class SongCatalogResponse(
    @kotlinx.serialization.SerialName("song-book") val songBook: List<SongbookEntry>,
    @kotlinx.serialization.SerialName("songBooks") val songBooks: Int,
    val total: Int
)

@Serializable
data class ScheduleSongDto(
    val id: String,
    val songNumber: Int,
    val title: String,
    val songbook: String
)

@Serializable
data class ScheduleResponse(
    val songs: List<ScheduleSongDto>,
    val total: Int
)

@Serializable
data class ServerInfoResponse(
    val name: String = Constants.SERVER_APP_NAME,
    val version: String = Constants.SERVER_VERSION,
    val port: Int
)

@Serializable
data class WebSocketMessage(
    val type: String,
    val payload: String = ""
)

// ── CompanionServer ───────────────────────────────────────────────────────────

/**
 * Ktor-based HTTP + WebSocket server that exposes song/schedule data
 * to the KMP mobile companion app.
 *
 * Lifecycle: call [start] once, [stop] to clean up.
 * Data is pushed in via [updateSongs] / [updateSchedule].
 * Song-selection events from mobile arrive via [onSongSelected].
 */
class CompanionServer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Current data — thread-safe StateFlows
    // All songs flat list
    // Current catalog — rebuilt whenever songs are updated
    private val _catalog = MutableStateFlow(SongCatalogResponse(emptyList(), 0, 0))
    private val _schedule = MutableStateFlow<List<ScheduleSongDto>>(emptyList())

    // API key config (updated from settings without restart)
    private val _apiKeyEnabled = MutableStateFlow(false)
    private val _apiKey = MutableStateFlow("")

    // Outgoing WebSocket broadcast channel
    private val broadcastChannel = MutableSharedFlow<String>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // Incoming song-selection requests from mobile clients
    val onSongSelected = MutableSharedFlow<ScheduleSongDto>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var currentPort: Int = Constants.SERVER_DEFAULT_PORT

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Update API key settings without restarting the server. */
    fun updateApiKey(enabled: Boolean, key: String) {
        _apiKeyEnabled.value = enabled
        _apiKey.value = key
    }

    /** Feed the full song list — builds grouped catalog and broadcasts to WS clients. */
    fun updateSongs(songs: List<SongItem>) {
        val catalog = buildCatalog(songs)
        _catalog.value = catalog
        broadcast(WebSocketMessage(
            type = Constants.WS_EVENT_SONGS_UPDATED,
            payload = json.encodeToString(SongCatalogResponse.serializer(), catalog)
        ))
    }

    /** Feed only the song-type items from the current schedule. */
    fun updateSchedule(items: List<ScheduleItem>) {
        val dtos = items.filterIsInstance<ScheduleItem.SongItem>().map { it.toDto() }
        _schedule.value = dtos
        broadcast(WebSocketMessage(
            type = Constants.WS_EVENT_SCHEDULE_UPDATED,
            payload = json.encodeToString(ScheduleResponse.serializer(), ScheduleResponse(dtos, dtos.size))
        ))
    }

    private fun buildCatalog(songs: List<SongItem>): SongCatalogResponse {
        val entries = songs
            .groupBy { it.songbook }
            .entries
            .sortedBy { it.key }
            .map { (bookName, bookSongs) ->
                SongbookEntry(
                    bookName = bookName,
                    songTotal = bookSongs.size,
                    songs = bookSongs.map { s ->
                        SongDto(number = s.number, title = s.title, tune = s.tune, author = s.author)
                    }
                )
            }
        return SongCatalogResponse(
            songBook = entries,
            songBooks = entries.size,
            total = songs.size
        )
    }

    fun start(port: Int = Constants.SERVER_DEFAULT_PORT) {
        if (_isRunning.value) return
        currentPort = port
        server = embeddedServer(Netty, port = port, host = "0.0.0.0") {
            install(ContentNegotiation) { json(json) }
            install(WebSockets)
            install(CORS) {
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowHeader(HttpHeaders.ContentType)
                allowHeader(Constants.HEADER_API_KEY)
                anyHost()
            }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    call.respondText("Internal error: ${cause.message}")
                }
            }
            routing {
                // ── REST endpoints ─────────────────────────────────────────
                get(Constants.ENDPOINT_INFO) {
                    if (!checkApiKey(call)) return@get
                    call.respond(ServerInfoResponse(port = currentPort))
                }

                // GET /api/songs              → full catalog (all songbooks with songs nested)
                // GET /api/songs?songbook=X   → catalog filtered to one songbook
                get(Constants.ENDPOINT_SONGS) {
                    if (!checkApiKey(call)) return@get
                    val filter = call.request.queryParameters[Constants.QUERY_PARAM_SONGBOOK]
                    val catalog = _catalog.value
                    if (filter.isNullOrBlank()) {
                        call.respond(catalog)
                    } else {
                        val filtered = catalog.songBook.filter { it.bookName == filter }
                        call.respond(SongCatalogResponse(
                            songBook = filtered,
                            songBooks = filtered.size,
                            total = filtered.sumOf { it.songTotal }
                        ))
                    }
                }

                get(Constants.ENDPOINT_SCHEDULE) {
                    if (!checkApiKey(call)) return@get
                    val schedule = _schedule.value
                    call.respond(ScheduleResponse(schedule, schedule.size))
                }

                // ── WebSocket ──────────────────────────────────────────────
                webSocket(Constants.ENDPOINT_WS) {
                    val queryKey = call.request.queryParameters[Constants.QUERY_PARAM_API_KEY]
                    val headerKey = call.request.headers[Constants.HEADER_API_KEY]
                    if (_apiKeyEnabled.value && _apiKey.value.isNotEmpty()) {
                        val provided = queryKey ?: headerKey ?: ""
                        if (provided != _apiKey.value) {
                            send(Frame.Text("{\"error\":\"Unauthorized\"}"))
                            return@webSocket
                        }
                    }

                    // Push current state to newly connected client
                    val catalog = _catalog.value
                    val schedule = _schedule.value
                    send(Frame.Text(json.encodeToString(WebSocketMessage.serializer(),
                        WebSocketMessage(Constants.WS_EVENT_SONGS_UPDATED,
                            json.encodeToString(SongCatalogResponse.serializer(), catalog)))))
                    send(Frame.Text(json.encodeToString(WebSocketMessage.serializer(),
                        WebSocketMessage(Constants.WS_EVENT_SCHEDULE_UPDATED,
                            json.encodeToString(ScheduleResponse.serializer(), ScheduleResponse(schedule, schedule.size))))))

                    // Forward broadcasts to this client
                    val broadcastJob = scope.launch {
                        broadcastChannel.collect { message -> send(Frame.Text(message)) }
                    }

                    // Handle incoming messages from mobile
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            try {
                                val msg = json.decodeFromString(WebSocketMessage.serializer(), frame.readText())
                                when (msg.type) {
                                    Constants.WS_CMD_SELECT_SONG -> {
                                        val song = json.decodeFromString(ScheduleSongDto.serializer(), msg.payload)
                                        scope.launch { onSongSelected.emit(song) }
                                    }
                                }
                            } catch (_: Exception) { /* ignore malformed frames */ }
                        }
                    }
                    broadcastJob.cancel()
                }
            }
        }
        server!!.start(wait = false)
        _isRunning.value = true
        _serverUrl.value = "http://${localIpAddress()}:$port"
    }

    fun stop() {
        server?.stop(1_000, 2_000)
        server = null
        _isRunning.value = false
        _serverUrl.value = ""
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun checkApiKey(call: io.ktor.server.application.ApplicationCall): Boolean {
        if (!_apiKeyEnabled.value || _apiKey.value.isEmpty()) return true
        val provided = call.request.headers[Constants.HEADER_API_KEY]
            ?: call.request.queryParameters[Constants.QUERY_PARAM_API_KEY]
            ?: ""
        return if (provided == _apiKey.value) {
            true
        } else {
            call.respond(io.ktor.http.HttpStatusCode.Unauthorized, "Invalid API key")
            false
        }
    }



    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun broadcast(msg: WebSocketMessage) {
        scope.launch {
            broadcastChannel.emit(json.encodeToString(WebSocketMessage.serializer(), msg))
        }
    }

    private fun localIpAddress(): String {
        return try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .firstOrNull { !it.isLoopbackAddress && it.hostAddress.contains('.') }
                ?.hostAddress ?: "localhost"
        } catch (_: Exception) {
            "localhost"
        }
    }
}

// ── Extension mappers ─────────────────────────────────────────────────────────

fun SongItem.toDto() = SongDto(
    number = number,
    title = title,
    tune = tune,
    author = author
)

fun ScheduleItem.SongItem.toDto() = ScheduleSongDto(
    id = id,
    songNumber = songNumber,
    title = title,
    songbook = songbook
)

