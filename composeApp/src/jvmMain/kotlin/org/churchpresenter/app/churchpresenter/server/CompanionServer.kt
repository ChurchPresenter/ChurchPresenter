package org.churchpresenter.app.churchpresenter.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
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
import java.io.File
import java.net.NetworkInterface
import java.util.Base64

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
data class ScheduleItemDto(
    val id: String,
    val type: String,           // "song", "bible", "label", "picture", "presentation", "media", "lower_third", "announcement", "website"
    val displayText: String,
    // song
    val songNumber: Int? = null,
    val title: String? = null,
    val songbook: String? = null,
    // bible
    val bookName: String? = null,
    val chapter: Int? = null,
    val verseNumber: Int? = null,
    // label
    val text: String? = null,
    val textColor: String? = null,
    val backgroundColor: String? = null,
    // picture
    val folderPath: String? = null,
    val folderName: String? = null,
    val imageCount: Int? = null,
    // presentation
    val filePath: String? = null,
    val fileName: String? = null,
    val slideCount: Int? = null,
    val fileType: String? = null,
    // media
    val mediaUrl: String? = null,
    val mediaTitle: String? = null,
    val mediaType: String? = null,
    // lower third
    val presetId: String? = null,
    val presetLabel: String? = null,
    // website
    val url: String? = null
)

@Serializable
data class ScheduleResponse(
    val items: List<ScheduleItemDto>,
    val total: Int
)

// ── Bible DTOs ────────────────────────────────────────────────────────────────

@Serializable
data class BibleChapterDto(
    val chapter: Int,
    @kotlinx.serialization.SerialName("verse-total") val verseTotal: Int
)

@Serializable
data class BibleBookDto(
    @kotlinx.serialization.SerialName("book-id")      val bookId: Int,
    @kotlinx.serialization.SerialName("book-name")    val bookName: String,
    @kotlinx.serialization.SerialName("chapter-total") val chapterTotal: Int,
    val chapters: List<BibleChapterDto>
)

/**
 * Top-level response for /api/bible
 *
 * {
 *   "translation": "KJV",
 *   "books": [
 *     { "book-id": 1, "book-name": "Genesis", "chapter-total": 50,
 *       "chapters": [ { "chapter": 1, "verse-total": 31 }, … ] }
 *   ],
 *   "book-total": 66,
 *   "verse-total": 31102
 * }
 */
@Serializable
data class BibleCatalogResponse(
    val translation: String,
    val books: List<BibleBookDto>,
    @kotlinx.serialization.SerialName("book-total")  val bookTotal: Int,
    @kotlinx.serialization.SerialName("verse-total") val verseTotal: Int
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
    private val _bibleCatalog = MutableStateFlow<BibleCatalogResponse?>(null)
    private val _schedule = MutableStateFlow<List<ScheduleItemDto>>(emptyList())

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

    // ── Lottie Generator data directories ─────────────────────────────────────
    private val lottieDataDir: File = File(System.getProperty("user.home"), ".churchpresenter/lottie_presets").also { it.mkdirs() }
    private val lottiePresetsFile: File = File(lottieDataDir, "presets.json")
    private val lottieColorThemesFile: File = File(lottieDataDir, "color-themes.json")
    private val lottieLogosDir: File = File(lottieDataDir, "logos")

    /** Resolved path to the Lottie-Gen directory containing lottie-generator.html */
    private val lottieGenDir: File? by lazy { findLottieGenDir() }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Update API key settings without restarting the server. */
    fun updateApiKey(enabled: Boolean, key: String) {
        _apiKeyEnabled.value = enabled
        _apiKey.value = key
    }

    /**
     * Preloads songs and bible from disk on the server's IO scope.
     * Safe to call at any time; re-call whenever settings change.
     */
    fun preloadData(
        songStorageDir: String,
        bibleStorageDir: String,
        primaryBibleFileName: String
    ) {
        scope.launch {
            // ── Songs ──────────────────────────────────────────────────────────
            if (songStorageDir.isNotEmpty()) {
                try {
                    val dir = java.io.File(songStorageDir)
                    if (dir.exists() && dir.isDirectory) {
                        val songs = org.churchpresenter.app.churchpresenter.data.Songs()
                        dir.listFiles { f -> f.extension.lowercase() == Constants.EXTENSION_SPS }
                            ?.sortedBy { it.name }
                            ?.forEach { file ->
                                try { songs.loadFromSpsAppend(file.absolutePath) } catch (_: Exception) {}
                            }
                        if (songs.getSongCount() > 0) {
                            updateSongs(songs.getSongs())
                        }
                    }
                } catch (_: Exception) {}
            }

            // ── Bible ──────────────────────────────────────────────────────────
            if (bibleStorageDir.isNotEmpty() && primaryBibleFileName.isNotEmpty()) {
                try {
                    val file = java.io.File(bibleStorageDir, primaryBibleFileName)
                    if (file.exists()) {
                        val bible = org.churchpresenter.app.churchpresenter.data.Bible()
                        bible.loadFromSpb(file.absolutePath)
                        updateBible(bible, primaryBibleFileName)
                    }
                } catch (_: Exception) {}
            }
        }
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

    /** Feed the primary Bible — builds full nested catalog and broadcasts to WS clients. */
    fun updateBible(bible: org.churchpresenter.app.churchpresenter.data.Bible, translation: String) {
        val catalog = buildBibleCatalog(bible, translation)
        _bibleCatalog.value = catalog
        broadcast(WebSocketMessage(
            type = Constants.WS_EVENT_BIBLE_UPDATED,
            payload = json.encodeToString(BibleCatalogResponse.serializer(), catalog)
        ))
    }

    /** Feed all schedule items — maps every type to ScheduleItemDto and broadcasts to WS clients. */
    fun updateSchedule(items: List<ScheduleItem>) {
        val dtos = items.map { item ->
            when (item) {
                is ScheduleItem.SongItem -> ScheduleItemDto(
                    id = item.id, type = "song", displayText = item.displayText,
                    songNumber = item.songNumber, title = item.title, songbook = item.songbook
                )
                is ScheduleItem.BibleVerseItem -> ScheduleItemDto(
                    id = item.id, type = "bible", displayText = item.displayText,
                    bookName = item.bookName, chapter = item.chapter, verseNumber = item.verseNumber,
                    text = item.verseText
                )
                is ScheduleItem.LabelItem -> ScheduleItemDto(
                    id = item.id, type = "label", displayText = item.displayText,
                    text = item.text, textColor = item.textColor, backgroundColor = item.backgroundColor
                )
                is ScheduleItem.PictureItem -> ScheduleItemDto(
                    id = item.id, type = "picture", displayText = item.displayText,
                    folderPath = item.folderPath, folderName = item.folderName, imageCount = item.imageCount
                )
                is ScheduleItem.PresentationItem -> ScheduleItemDto(
                    id = item.id, type = "presentation", displayText = item.displayText,
                    filePath = item.filePath, fileName = item.fileName,
                    slideCount = item.slideCount, fileType = item.fileType
                )
                is ScheduleItem.MediaItem -> ScheduleItemDto(
                    id = item.id, type = "media", displayText = item.displayText,
                    mediaUrl = item.mediaUrl, mediaTitle = item.mediaTitle, mediaType = item.mediaType
                )
                is ScheduleItem.LowerThirdItem -> ScheduleItemDto(
                    id = item.id, type = "lower_third", displayText = item.displayText,
                    presetId = item.presetId, presetLabel = item.presetLabel
                )
                is ScheduleItem.AnnouncementItem -> ScheduleItemDto(
                    id = item.id, type = "announcement", displayText = item.displayText,
                    text = item.text, textColor = item.textColor, backgroundColor = item.backgroundColor
                )
                is ScheduleItem.WebsiteItem -> ScheduleItemDto(
                    id = item.id, type = "website", displayText = item.displayText,
                    url = item.url, title = item.title
                )
            }
        }
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

    private fun buildBibleCatalog(
        bible: org.churchpresenter.app.churchpresenter.data.Bible,
        translation: String
    ): BibleCatalogResponse {
        val bookNames = bible.getBooks()
        val bookDtos = mutableListOf<BibleBookDto>()
        var totalVerses = 0

        bookNames.forEachIndexed { bookIndex, bookName ->
            val bookId = bookIndex + 1
            val chapterCount = bible.getChapterCount(bookIndex)
            // Count verses directly from the verse data without calling getChapter()
            // which mutates Compose mutableStateListOf and requires the main thread.
            val chapterDtos = (1..chapterCount).map { chapterNum ->
                val verseCount = bible.getVerseCountForChapter(bookId, chapterNum)
                totalVerses += verseCount
                BibleChapterDto(chapter = chapterNum, verseTotal = verseCount)
            }
            bookDtos.add(BibleBookDto(
                bookId = bookId,
                bookName = bookName,
                chapterTotal = chapterCount,
                chapters = chapterDtos
            ))
        }

        return BibleCatalogResponse(
            translation = translation,
            books = bookDtos,
            bookTotal = bookDtos.size,
            verseTotal = totalVerses
        )
    }

    fun start(port: Int = Constants.SERVER_DEFAULT_PORT) {
        if (_isRunning.value) return
        currentPort = port

        val keyStore = try {
            SslCertificateManager.getOrCreateKeyStore()
        } catch (e: Exception) {
            startPlainHttp(port)
            return
        }

        server = embeddedServer(Netty, configure = {
            // HTTPS for external clients
            sslConnector(
                keyStore = keyStore,
                keyAlias = Constants.SSL_KEY_ALIAS,
                keyStorePassword = { Constants.SSL_KEYSTORE_PASSWORD.toCharArray() },
                privateKeyPassword = { Constants.SSL_KEYSTORE_PASSWORD.toCharArray() }
            ) {
                host = "0.0.0.0"
                this.port = port
            }
            // Plain HTTP on localhost for embedded WebView (avoids SSL handshake issues)
            connector {
                host = "127.0.0.1"
                this.port = port + 1
            }
        }) { configurePipeline() }

        server!!.start(wait = false)
        _isRunning.value = true
        _serverUrl.value = "https://${localIpAddress()}:$port"
    }

    /** Fallback plain-HTTP start used only if SSL cert generation fails. */
    private fun startPlainHttp(port: Int) {
        server = embeddedServer(Netty, port = port, host = "0.0.0.0") {
            configurePipeline()
        }
        server!!.start(wait = false)
        _isRunning.value = true
        _serverUrl.value = "http://${localIpAddress()}:$port"
    }

    private fun io.ktor.server.application.Application.configurePipeline() {
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
                get(Constants.ENDPOINT_INFO) {
                    if (!checkApiKey(call)) return@get
                    call.respond(ServerInfoResponse(port = currentPort))
                }

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

                get(Constants.ENDPOINT_BIBLE) {
                    if (!checkApiKey(call)) return@get
                    val catalog = _bibleCatalog.value
                    if (catalog == null) {
                        call.respond(io.ktor.http.HttpStatusCode.ServiceUnavailable, "Bible not loaded")
                        return@get
                    }
                    val bookFilter    = call.request.queryParameters[Constants.QUERY_PARAM_BOOK]
                    val chapterFilter = call.request.queryParameters[Constants.QUERY_PARAM_CHAPTER]?.toIntOrNull()

                    if (bookFilter.isNullOrBlank()) {
                        call.respond(catalog)
                    } else {
                        val filteredBooks = catalog.books.filter {
                            it.bookName.equals(bookFilter, ignoreCase = true)
                        }.map { book ->
                            if (chapterFilter != null) {
                                book.copy(chapters = book.chapters.filter { it.chapter == chapterFilter })
                            } else book
                        }
                        call.respond(catalog.copy(
                            books = filteredBooks,
                            bookTotal = filteredBooks.size,
                            verseTotal = filteredBooks.sumOf { b -> b.chapters.sumOf { it.verseTotal } }
                        ))
                    }
                }

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

                    val catalog = _catalog.value
                    val schedule = _schedule.value
                    send(Frame.Text(json.encodeToString(WebSocketMessage.serializer(),
                        WebSocketMessage(Constants.WS_EVENT_SONGS_UPDATED,
                            json.encodeToString(SongCatalogResponse.serializer(), catalog)))))
                    _bibleCatalog.value?.let { bibleCatalog ->
                        send(Frame.Text(json.encodeToString(WebSocketMessage.serializer(),
                            WebSocketMessage(Constants.WS_EVENT_BIBLE_UPDATED,
                                json.encodeToString(BibleCatalogResponse.serializer(), bibleCatalog)))))
                    }
                    send(Frame.Text(json.encodeToString(WebSocketMessage.serializer(),
                        WebSocketMessage(Constants.WS_EVENT_SCHEDULE_UPDATED,
                            json.encodeToString(ScheduleResponse.serializer(), ScheduleResponse(schedule, schedule.size))))))

                    val broadcastJob = scope.launch {
                        broadcastChannel.collect { message -> send(Frame.Text(message)) }
                    }

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

                // ── Lottie Generator endpoints ────────────────────────────────

                // Serve the generator HTML page
                get(Constants.ENDPOINT_LOTTIE_GENERATOR) {
                    val dir = lottieGenDir
                    val html = dir?.let { File(it, "lottie-generator.html") }
                    if (html == null || !html.exists()) {
                        call.respond(io.ktor.http.HttpStatusCode.NotFound, "Lottie generator not found")
                        return@get
                    }
                    call.respondText(html.readText(), ContentType.Text.Html)
                }

                // Serve bundled lottie.min.js
                get("/lottie.min.js") {
                    val file = lottieGenDir?.let { File(it, "lottie.min.js") }
                    if (file == null || !file.exists()) {
                        call.respond(io.ktor.http.HttpStatusCode.NotFound, "lottie.min.js not found")
                        return@get
                    }
                    call.respondText(file.readText(), ContentType.Application.JavaScript)
                }

                // Serve logo image files
                get("/logos/{filename}") {
                    val filename = call.parameters["filename"] ?: return@get
                    // Check lottie data dir first, then fall back to bundled Lottie-Gen dir
                    val file = File(lottieLogosDir, filename).takeIf { it.exists() }
                        ?: lottieGenDir?.let { File(File(it, "logos"), filename) }?.takeIf { it.exists() }
                    if (file == null) {
                        call.respond(io.ktor.http.HttpStatusCode.NotFound, "Logo not found")
                        return@get
                    }
                    val contentType = when (file.extension.lowercase()) {
                        "png" -> ContentType.Image.PNG
                        "jpg", "jpeg" -> ContentType.Image.JPEG
                        "svg" -> ContentType.Image.SVG
                        "webp" -> ContentType("image", "webp")
                        else -> ContentType.Application.OctetStream
                    }
                    call.respondBytes(file.readBytes(), contentType)
                }

                // GET /api/presets — load saved generator presets
                get(Constants.ENDPOINT_LOTTIE_PRESETS) {
                    val data = if (lottiePresetsFile.exists()) lottiePresetsFile.readText() else "[]"
                    call.respondText(data, ContentType.Application.Json)
                }

                // POST /api/presets — save generator presets
                post(Constants.ENDPOINT_LOTTIE_PRESETS) {
                    val body = call.receiveText()
                    lottiePresetsFile.writeText(body)
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }

                // GET /api/color-themes — load color themes
                get(Constants.ENDPOINT_LOTTIE_COLOR_THEMES) {
                    // Fall back to bundled defaults if user hasn't saved custom themes
                    val data = when {
                        lottieColorThemesFile.exists() -> lottieColorThemesFile.readText()
                        else -> {
                            val bundled = lottieGenDir?.let { File(it, "color-themes.json") }
                            if (bundled != null && bundled.exists()) bundled.readText() else "[]"
                        }
                    }
                    call.respondText(data, ContentType.Application.Json)
                }

                // POST /api/color-themes — save color themes
                post(Constants.ENDPOINT_LOTTIE_COLOR_THEMES) {
                    val body = call.receiveText()
                    lottieColorThemesFile.writeText(body)
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }

                // GET /api/logos — list available logo images
                get(Constants.ENDPOINT_LOTTIE_LOGOS) {
                    val files = mutableSetOf<String>()
                    // Include logos from user data dir
                    if (lottieLogosDir.exists()) {
                        lottieLogosDir.listFiles()
                            ?.filter { it.extension.lowercase().matches(Regex("png|jpe?g|svg|webp")) }
                            ?.forEach { files.add(it.name) }
                    }
                    // Include bundled logos from Lottie-Gen
                    lottieGenDir?.let { File(it, "logos") }?.listFiles()
                        ?.filter { it.extension.lowercase().matches(Regex("png|jpe?g|svg|webp")) }
                        ?.forEach { files.add(it.name) }
                    val jsonArray = files.joinToString(",") { "\"$it\"" }
                    call.respondText("[$jsonArray]", ContentType.Application.Json)
                }

                // POST /api/logos — upload a logo (base64 JSON body: { name, data })
                post(Constants.ENDPOINT_LOTTIE_LOGOS) {
                    try {
                        val body = call.receiveText()
                        val parsed = json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject
                        val name = parsed?.get("name")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                        val data = parsed?.get("data")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                        if (name.isNullOrBlank() || data.isNullOrBlank()) {
                            call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"bad data"}""")
                            return@post
                        }
                        val safeName = File(name).name // strip path components
                        val base64Match = Regex("^data:[^;]+;base64,(.+)$").find(data)
                        if (base64Match == null) {
                            call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"bad data"}""")
                            return@post
                        }
                        lottieLogosDir.mkdirs()
                        val bytes = Base64.getDecoder().decode(base64Match.groupValues[1])
                        File(lottieLogosDir, safeName).writeBytes(bytes)
                        call.respondText("""{"file":"$safeName"}""", ContentType.Application.Json)
                    } catch (e: Exception) {
                        call.respond(io.ktor.http.HttpStatusCode.InternalServerError, """{"error":"write failed"}""")
                    }
                }
            }
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




    private fun broadcast(msg: WebSocketMessage) {
        scope.launch {
            broadcastChannel.emit(json.encodeToString(WebSocketMessage.serializer(), msg))
        }
    }

    /** Locate the Lottie-Gen directory containing lottie-generator.html. */
    private fun findLottieGenDir(): File? {
        val appResourcesDir = System.getProperty("compose.application.resources.dir")
        val executablePath = ProcessHandle.current().info().command().orElse(null)
            ?.let { File(it).parentFile }
        fun walkUp(): File? {
            var dir = File(System.getProperty("user.dir"))
            repeat(6) {
                val candidate = File(dir, "Lottie-Gen/lottie-generator.html")
                if (candidate.exists()) return File(dir, "Lottie-Gen")
                dir = dir.parentFile ?: return null
            }
            return null
        }
        return listOfNotNull(
            appResourcesDir?.let { File(it, "Lottie-Gen") },
            executablePath?.let { File(it, "../app/resources/Lottie-Gen") },
            executablePath?.let { File(it, "Lottie-Gen") },
            walkUp(),
            File("Lottie-Gen"),
            File(System.getProperty("user.dir"), "Lottie-Gen")
        ).firstOrNull { File(it, "lottie-generator.html").exists() }
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


