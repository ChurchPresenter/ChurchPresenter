package org.churchpresenter.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.churchpresenter.bible.Bible
import org.churchpresenter.qa.QAManager
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.settings.MessageTemplate
import org.churchpresenter.settings.ClearGroup
import org.churchpresenter.settings.Macro
import org.churchpresenter.settings.PropDefinition
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants

private const val PORT_SCAN_RANGE = 40
private const val WEBSOCKET_PING_PERIOD_MS = 10_000L
private const val WEBSOCKET_TIMEOUT_MS = 20_000L
private const val SHUTDOWN_GRACE_MS = 1_000L
private const val SHUTDOWN_TIMEOUT_MS = 2_000L

// ── CompanionServer ───────────────────────────────────────────────────────────

/**
 * Ktor-based HTTP + WebSocket server that exposes song/schedule data
 * to the KMP mobile companion app.
 *
 * Lifecycle: call [start] once, [stop] to clean up.
 * Data is pushed in via [updateSongs] / [updateSchedule].
 * Song-selection events from mobile arrive via [onSongSelected].
 */
/** The app's version, for `/api/status` and its version header, when nothing more specific is passed. */
private const val UNKNOWN_VERSION = "dev"

class CompanionServer(
    /** This build's version, reported to companions -- the app passes `BuildConfig.APP_VERSION`. */
    internal val appVersion: String = UNKNOWN_VERSION,
    /**
     * How long [stop] lets open requests and sockets finish before closing them. The app keeps the
     * default; a test passes 0, since it has nothing left in flight and otherwise pays this on
     * every teardown.
     */
    private val shutdownGraceMs: Long = SHUTDOWN_GRACE_MS,
) {
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var _qaEventJob: Job? = null
    var qaManager: QAManager? = null
        set(value) {
            _qaEventJob?.cancel()
            _qaEventJob = null
            field = value
            if (value != null) {
                _qaEventJob = scope.launch {
                    value.events.collect { _ ->
                        broadcast(WebSocketMessage(
                            type = Constants.WS_EVENT_QUESTIONS_UPDATED,
                            payload = ""
                        ))
                    }
                }
            }
        }
    @Volatile var qaAdminPassword: String = ""
    @Volatile var qaCooldownSeconds: Int = 30
    @Volatile var qaVotingEnabled: Boolean = false

    // Presentation remote control settings
    @Volatile var presentationRemoteEnabled: Boolean = false
    @Volatile var presentationRemotePassword: String = ""

    // Current presentation state (updated from desktop, read by remote clients)
    // `internal` rather than private: these are mutable and must be read (and some written)
    // per request by the extracted route groups, so unlike the immutable state they cannot be
    // passed in as parameters.
    @Volatile internal var _currentPresentationId: String = ""
    @Volatile internal var _currentSlideIndex: Int = 0
    @Volatile internal var _currentSlideTotalCount: Int = 0
    @Volatile internal var _presentationFrozen: Boolean = false
    @Volatile internal var _presentationIsPlaying: Boolean = false
    @Volatile internal var _presentationIsLive: Boolean = false

    /** Whether the presentation on screen is live, as the companion feed last reported it. */
    val presentationIsLive: Boolean get() = _presentationIsLive
    @Volatile internal var _autoScrollInterval: Int = 5
    @Volatile internal var _presentationIsLooping: Boolean = true

    /** Emitted when remote taps Go Live. */
    val onPresentationGoLive = MutableSharedFlow<Unit>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emitted when remote presses freeze/blank. */
    val onPresentationFreezeToggle = MutableSharedFlow<Unit>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emitted when remote presses play/pause. */
    val onPresentationPlayPause = MutableSharedFlow<Unit>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emitted when remote presses the loop toggle. */
    val onPresentationLoopToggle = MutableSharedFlow<Unit>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emitted when remote jumps to a specific slide index. */
    val onPresentationGoto = MutableSharedFlow<Int>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // Current data — thread-safe StateFlows
    // All songs flat list
    // Current catalog — rebuilt whenever songs are updated
    internal val _catalog = MutableStateFlow(SongCatalogResponse(emptyList(), 0, 0))
    /** Raw song list kept in sync with _catalog for per-number detail lookups */
    @Volatile internal var _songs: List<SongItem> = emptyList()

    /**
     * How long a song typically runs here, from the app's duration log; null for one never measured.
     * Set by the app once the log exists; the server itself keeps no durations.
     */
    @Volatile var typicalSeconds: (SongItem) -> Int? = { null }

    internal val _bibleCatalog = MutableStateFlow<BibleCatalogResponse?>(null)
    internal val _bible = MutableStateFlow<Bible?>(null)
    /** Absolute path to the primary bible's .spb file — serves GET /api/bible/file for InstanceLink followers. */
    @Volatile internal var _bibleFilePath: String = ""
    /** Same as [_bibleFilePath] but for the secondary bible — serves GET /api/bible/file/secondary,
     *  only used when a follower opts in to mirroring the secondary too (most don't). */
    @Volatile internal var _secondaryBibleFilePath: String = ""
    @Volatile internal var _bibleFilePaths: List<String> = emptyList()
    /** Current background settings — serves GET /api/backgrounds for a follower that opted in to
     *  mirroring backgrounds. The image/video fields are still local file paths on this machine;
     *  GET /api/backgrounds/asset/{slot} resolves the current path for a given slot on demand. */
    internal val _backgroundSettings = MutableStateFlow(BackgroundSettings())
    internal val _schedule = MutableStateFlow<List<ScheduleItemDto>>(emptyList())
    /** Snapshot of whatever is currently live — see [LiveStateDto]. */
    internal val _liveState = MutableStateFlow<LiveStateDto?>(null)
    val liveState: StateFlow<LiveStateDto?> = _liveState.asStateFlow()
    /** Device IDs of currently-connected WS clients that identified as an Instance Link follower
     *  (as opposed to a regular mobile/browser companion client) — see [Constants.HEADER_CLIENT_ROLE]. */
    internal val _connectedInstanceLinkFollowers = MutableStateFlow<Set<String>>(emptySet())
    val connectedInstanceLinkFollowers: StateFlow<Set<String>> = _connectedInstanceLinkFollowers.asStateFlow()

    /**
     * Device ids the operator has permanently blocked (mirrored from `RemoteClientManager`, which
     * owns the list and its persistence).
     *
     * Blocking used to stop only the requests that ask for approval — adding to the schedule,
     * projecting, removing. Everything instant went straight through: a blocked phone or linked
     * instance could still put a verse, a picture or a slide on the main screen, and still received
     * the whole live feed. Enforced here rather than at each of the ~15 command branches so a new
     * command cannot be added without it.
     */
    @Volatile
    var blockedClientIds: Set<String> = emptySet()

    /** schedule item UUID → absolute local media file path — populated by updateSchedule, serves /api/media/stream */
    internal val _scheduleItemToMediaPath = ConcurrentHashMap<String, String>()

    /** Everything the API can serve as a picture — see [PictureLibrary]. */
    internal val pictures = PictureLibrary()

    // API key config (updated from settings without restart)
    internal val _apiKeyEnabled = MutableStateFlow(false)
    internal val _apiKey = MutableStateFlow("")

    // File upload permission (updated from settings without restart)
    internal val _fileUploadEnabled = MutableStateFlow(true)
    // Max media-upload size in MB (updated from settings without restart)
    internal val _maxMediaUploadMb = MutableStateFlow(Constants.DEFAULT_MAX_MEDIA_UPLOAD_MB)

    // Outgoing WebSocket broadcast channel. Buffer sized generously: it is shared by every
    // connected client's collector, and DROP_OLDEST means an overflow silently loses a message
    // for slow consumers with no redelivery until their next reconnect snapshot.
    // `internal` so a test can subscribe to exactly what a connected phone would receive, without
    // standing up a WebSocket client to read it back.
    internal val broadcastChannel = MutableSharedFlow<String>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // Incoming song-selection requests from mobile clients
    val onSongSelected = MutableSharedFlow<ScheduleSongDto>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emitted when a remote client requests an item to be added to the schedule. */
    val onAddToSchedule = MutableSharedFlow<PendingRemoteRequest>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emitted when a remote client requests multiple items to be added to the schedule in one call. */
    val onAddBatchToSchedule = MutableSharedFlow<PendingBatchRequest>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emitted when a remote client requests an item to be removed from the schedule — same
     *  approval flow as [onAddToSchedule], just the reverse operation. */
    val onRemoveFromSchedule = MutableSharedFlow<PendingRemoveRequest>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emitted when a remote client selects a picture image (POST /api/pictures/select or WS select_picture). */
    val onSelectPicture = MutableSharedFlow<SelectPictureRequest>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Emitted when a remote client navigates to a specific song section while live
     * (POST /api/songs/{number}/select or WS "select_song_section").
     */
    val onSelectSongSection = MutableSharedFlow<SelectSongSectionRequest>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Emitted when a remote client selects a specific slide to display
     * (POST /api/presentations/{id}/select or WS "select_slide").
     * No approval required — applied instantly.
     */
    val onSelectSlide = MutableSharedFlow<SelectSlideRequest>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Emitted when a mobile client uploads a presentation file via POST /api/presentations/upload.
     * The emitted [File] has already been saved to disk and is ready to be loaded by
     * [PresentationViewModel.addPresentation].
     */
    val onPresentationUploaded = MutableSharedFlow<File>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Emitted when a remote client selects a Bible verse to display instantly
     * (POST /api/bible/select or WS "select_bible_verse").
     * No approval required — applied instantly.
     */
    val onSelectBibleVerse = MutableSharedFlow<SelectBibleVerseRequest>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emitted when a remote client requests an item to be sent directly to projection. */
    val onProject = MutableSharedFlow<PendingRemoteRequest>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emitted when a phone on the LAN asks to plan the calendar through the relay. */
    val onCalendarEnroll = MutableSharedFlow<PendingCalendarEnroll>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emitted when a device authenticates against the presentation remote for the first time this session. */
    val onPresentationRemoteConnect = MutableSharedFlow<PendingConnectionRequest>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emitted when a tablet asks to use a Browser Source page's transpose buttons. */
    val onMusicianConnect = MutableSharedFlow<PendingConnectionRequest>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emitted when an approved tablet presses a transpose step or resets it. */
    val onBrowserSourceTranspose = MutableSharedFlow<BrowserSourceTransposeCommand>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emitted when a remote client requests a QA admin operation (add/edit/delete). */
    data class PendingQAAdminRequest(
        val action: String,
        val questionId: String = "",
        val text: String = "",
        val clientId: String = "",
        val decision: CompletableDeferred<Boolean> = CompletableDeferred()
    )

    val onQAAdminRequest = MutableSharedFlow<PendingQAAdminRequest>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emitted when a device authenticates against the Q&A admin panel for the first time this session. */
    val onQaAdminConnect = MutableSharedFlow<PendingConnectionRequest>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emitted when the web admin triggers Go Live or clear display for Q&A. Payload: Question or null. */
    val onQADisplay = MutableSharedFlow<Question?>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emitted when a remote client calls POST /api/clear or sends WS "clear". Clears the display instantly. */
    val onClear = MutableSharedFlow<Unit>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Emitted when a remote client clears one layer: POST /api/clear?layer=... or WS "clear" with a
     * `layer`. The payload is the layer's name as sent -- see `layerForName`.
     */
    val onClearLayer = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emitted when a remote client puts a message up: POST /api/message or WS "message". */
    val onMessage = MutableSharedFlow<RemoteMessage>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** The saved messages a remote client may name -- kept current by the app from its settings. */
    @Volatile var messageTemplates: List<MessageTemplate> = emptyList()

    /** Emitted when a remote client switches a prop: POST /api/props/{id}/on|off|toggle or WS "prop". */
    val onProp = MutableSharedFlow<PropSwitch>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** The props a remote client may switch -- kept current by the app from its settings. */
    @Volatile var props: List<PropDefinition> = emptyList()

    /** Emitted with a macro's id when a remote client runs it: POST /api/macro/{name} or WS "macro". */
    val onMacro = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Whether the app is in dev mode -- kept current by the app. Off, the features not yet ready for
     * production refuse remote clients; see [requireDevMode].
     */
    @Volatile var devMode: Boolean = false

    /** The macros a remote client may run -- kept current by the app from its settings. */
    @Volatile var macros: List<Macro> = emptyList()

    /** Emitted with a clear group's id when a remote client fires it: POST /api/clear?group= or WS "clear". */
    val onClearGroup = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** The clear groups a remote client may fire -- kept current by the app from its settings. */
    @Volatile var clearGroups: List<ClearGroup> = emptyList()

    /** Emitted when a remote client takes what is cued on Preview to air: POST /api/take or WS "take". */
    val onTake = MutableSharedFlow<Unit>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emitted when a remote client sends WS "bible_hold". Payload: {"hold": true/false}. */
    val onBibleHold = MutableSharedFlow<Boolean>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** ID-less navigation commands (Instance Link Controller mode) — advance/retreat whatever the
     *  primary currently has live, since a Controller has no way to learn the primary's internally-
     *  assigned folderId/presentationId. See Constants.WS_CMD_NEXT_PICTURE and siblings. */
    val onNextPicture = MutableSharedFlow<Unit>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val onPreviousPicture = MutableSharedFlow<Unit>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val onNextSlide = MutableSharedFlow<Unit>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val onPreviousSlide = MutableSharedFlow<Unit>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // Media transport controls from a companion remote
    val onMediaPlayPause = MutableSharedFlow<Unit>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val onMediaStop = MutableSharedFlow<Unit>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val onMediaSeekForward = MutableSharedFlow<Unit>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val onMediaSeekBackward = MutableSharedFlow<Unit>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val onMediaSeekTo = MutableSharedFlow<Long>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val onMediaSetVolume = MutableSharedFlow<Float>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val onMediaMuteToggle = MutableSharedFlow<Unit>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Emitted for every instant (no-approval) action so the UI can show an activity toast.
     * Carries enough info to build a [RemoteActivityNotification] without approval logic.
     */
    data class RemoteInstantAction(
        /** One of: "present", "upload", "clear" — maps to RemoteEventType in the UI layer. */
        val actionType: String,
        val title: RemoteLabel,
        val detail: RemoteLabel = RemoteLabel.EMPTY,
        val clientId: String = ""
    )

    val onInstantAction = MutableSharedFlow<RemoteInstantAction>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    val tunnelManager = TunnelManager()

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    internal var currentPort: Int = Constants.SERVER_DEFAULT_PORT

    internal val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        // Without this, fields still at their Kotlin default value (e.g. an unmodified
        // BibleSettings/SongSettings/StageMonitorSettings) are omitted from the JSON
        // entirely, which the Browser Source overlay page's JS can't distinguish from
        // "field doesn't exist" — it needs every field present to apply real styling.
        encodeDefaults = true
    }

    /** ATEM hardware and the lower-third folder — see [AtemBridge]. */
    internal val atem = AtemBridge(json)

    /** Presentation catalogue, slide cache and background renders — see [PresentationStore]. */
    internal val presentations = PresentationStore(json, scope, ::broadcast)

    /** OBS/vMix Browser Source outputs and their frame streams — see [BrowserSourceHub]. */
    internal val browserSource = BrowserSourceHub(scope, _apiKey)

    /** Whether Browser Source output [index] offers its musicians a transpose control. */
    fun offersTranspose(index: Int): Boolean = browserSource.offersTranspose(index)

    /**
     * The folder-id of the currently active picture folder pushed to mobile companions via
     * GET /api/pictures.  Null until a folder has been loaded in the Pictures tab.
     */
    val activeFolderId: String? get() = pictures.activeFolderId

    /**
     * Starts the companion server on [port].
     *
     * @param hostOverride  When non-blank, this hostname/IP is used in the displayed
     *   Server URL instead of the auto-detected local address.  Set it to the
     *   machine's static IP or mDNS name (e.g. "192.168.1.50" or "church-mac.local")
     *   so the URL never changes between restarts.
     */
    fun start(port: Int = Constants.SERVER_DEFAULT_PORT, hostOverride: String = "") {
        if (_isRunning.value) return

        val actualPort = findFreePort(port)
        currentPort = actualPort

        val displayHost = hostOverride.trim().ifEmpty { localIpAddress() }

        // Always use plain HTTP — no SSL certificate required.
        // Mobile clients connect over the local network with ws:// and http://.
        startPlainHttp(actualPort, displayHost)
    }

    /** Fallback plain-HTTP start used only if SSL cert generation fails. */
    private fun startPlainHttp(port: Int, displayHost: String = localIpAddress()) {
        try {
            server = embeddedServer(Netty, configure = {
                connector {
                    host = "0.0.0.0"
                    this.port = port
                }
            }) { configurePipeline() }
            server?.start(wait = false)
            _isRunning.value = true
            _serverUrl.value = "http://$displayHost:$port"
            CrashReporter.breadcrumb("Server started on port $port", category = "server")
            scope.launch { pictures.clearDeviceUploads() }
        } catch (_: java.net.BindException) {
            server = null
        } catch (_: Exception) {
            server = null
        }
    }

    private fun findFreePort(startPort: Int): Int {
        for (candidate in startPort until startPort + PORT_SCAN_RANGE) {
            if (isPortFree(candidate)) return candidate
        }
        return startPort
    }

    private fun isPortFree(port: Int): Boolean = try {
        ServerSocket(port).use { true }
    } catch (_: IOException) {
        false
    }

    private fun Application.configurePipeline() {
            install(ContentNegotiation) { json(json) }
            // Protocol-level pings on every /ws client (mobile companions answer them
            // automatically — invisible to application code). A client that stops ponging is
            // closed within ~timeoutMillis, which both frees its broadcast collector and clears
            // ghost entries from _connectedInstanceLinkFollowers instead of leaving half-open
            // TCP sessions "connected" indefinitely.
            install(WebSockets) {
                pingPeriodMillis = WEBSOCKET_PING_PERIOD_MS
                timeoutMillis = WEBSOCKET_TIMEOUT_MS
            }
            install(PartialContent)
            install(CORS) {
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Delete)
                allowHeader(HttpHeaders.ContentType)
                allowHeader(Constants.HEADER_API_KEY)
                allowHeader(Constants.HEADER_DEVICE_ID)
                allowHeader(Constants.HEADER_APP_VERSION)
                allowHeader(Constants.HEADER_CLIENT_ROLE)
                allowHeader("X-QA-Password")
                allowHeader(Constants.HEADER_PRESENTATION_PASSWORD)
                exposeHeader(Constants.HEADER_SERVER_VERSION)
                anyHost()
            }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    cause.printStackTrace()
                    call.respondText("Internal server error")
                }
            }
            routing {
                // Outside the API key check — devices must be able to fetch the CA cert first.
                certificateRoutes()

                // ── API endpoints (require API key when enabled) ────────────────────────────
                infoAndSongRoutes(this@CompanionServer, json, scope)
                scheduleRoutes(this@CompanionServer, _schedule, json, scope)
                bibleAndDictionaryRoutes(
                    this@CompanionServer, _bible, _bibleCatalog, json, scope
                )
                presentationRoutes(this@CompanionServer, json, scope)
                presentationRemoteRoutes(this@CompanionServer, presentations._presentationNotes, scope)
                calendarSyncRoutes(this@CompanionServer, json)
                mediaAndAssetRoutes(this@CompanionServer, json, scope)
                webSocketRoute(this@CompanionServer, json, scope)
                lowerThirdAndAtemRoutes(this@CompanionServer, json, scope)
                browserSourceRoutes(
                    this@CompanionServer, browserSource._browserSourceFrameFlows,
                    browserSource._browserSourceSessions
                )
                browserSourceTransposeRoutes(this@CompanionServer)
                qaRoutes(this@CompanionServer, json, scope)
            }
    }

    fun stop() {
        tunnelManager.stop()
        server?.stop(shutdownGraceMs, maxOf(shutdownGraceMs, SHUTDOWN_TIMEOUT_MS))
        server = null
        scope.coroutineContext[Job]?.cancelChildren()
        _isRunning.value = false
        _serverUrl.value = ""
        CrashReporter.breadcrumb("Server stopped", category = "server")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    //
    // Several of these are `internal` rather than private because the route groups now live in
    // their own files (ScheduleRoutes.kt, QaRoutes.kt, …) and call back into them. Immutable state
    // is handed to those groups as parameters instead, so only behaviour is widened, not data.

    internal fun broadcast(msg: WebSocketMessage) {
        InstanceLinkLogger.log(InstanceLinkLogSide.PRIMARY, "broadcast", mapOf("type" to msg.type))
        scope.launch {
            broadcastChannel.emit(json.encodeToString(WebSocketMessage.serializer(), msg))
        }
    }

    /** Logs one REST hit on an Instance-Link-relevant endpoint — success and failure alike, so the
     *  primary's own log shows exactly what it served without needing to infer it from a follower's
     *  fetch_result. [status] is the HTTP status code actually sent. */
    internal fun logRest(endpoint: String, status: Int, reason: String? = null) {
        InstanceLinkLogger.log(
            InstanceLinkLogSide.PRIMARY, "rest_request",
            mapOf("endpoint" to endpoint, "status" to status, "reason" to reason)
        )
    }

}

