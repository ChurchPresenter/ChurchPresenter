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
import org.churchpresenter.app.churchpresenter.utils.HeicDecoder
import org.churchpresenter.app.churchpresenter.utils.isChorusHeader
import org.churchpresenter.app.churchpresenter.utils.isHeaderLine
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.NetworkInterface
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

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
data class SongSectionDto(
    val type: String,           // "verse", "chorus", "other"
    val lines: List<String>
)

/**
 * Full song detail returned by GET /api/songs/{number}[?songbook=Name]
 *
 * {
 *   "number": "42",
 *   "title": "Amazing Grace",
 *   "songbook": "Hymns",
 *   "tune": "NEW BRITAIN",
 *   "author": "John Newton",
 *   "composer": "",
 *   "section-total": 4,
 *   "sections": [
 *     { "type": "verse", "lines": ["Amazing grace, how sweet the sound", "…"] },
 *     { "type": "chorus", "lines": ["…"] }
 *   ]
 * }
 */
@Serializable
data class SongDetailDto(
    val number: String,
    val title: String,
    val songbook: String,
    val tune: String,
    val author: String,
    val composer: String,
    @kotlinx.serialization.SerialName("section-total") val sectionTotal: Int,
    val sections: List<SongSectionDto>
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
    /** Non-null for multi-verse items, e.g. "1-3" or "2,4". Null / absent for single verses. */
    val verseRange: String? = null,
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

@Serializable
data class BibleVerseDto(
    @kotlinx.serialization.SerialName("verse") val verse: Int,
    @kotlinx.serialization.SerialName("text")  val text: String
)

/**
 * Response for /api/bible?book={id}&chapter={num} — full chapter with verse text.
 *
 * {
 *   "translation": "KJV",
 *   "book-id": 1,
 *   "book-name": "Genesis",
 *   "chapter": 1,
 *   "verse-total": 31,
 *   "verses": [ { "verse": 1, "text": "In the beginning…" }, … ]
 * }
 */
@Serializable
data class BibleChapterResponse(
    val translation: String,
    @kotlinx.serialization.SerialName("book-id")    val bookId: Int,
    @kotlinx.serialization.SerialName("book-name")  val bookName: String,
    val chapter: Int,
    @kotlinx.serialization.SerialName("verse-total") val verseTotal: Int,
    val verses: List<BibleVerseDto>
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

// ── Presentation DTOs ─────────────────────────────────────────────────────────

/**
 * Metadata for a single slide within a presentation.
 *
 * The slide image can be retrieved via:
 *   GET /api/presentations/{presentation-id}/slides/{slide-index}
 */
@Serializable
data class SlideDto(
    @kotlinx.serialization.SerialName("slide-index") val slideIndex: Int,
    @kotlinx.serialization.SerialName("thumbnail-url") val thumbnailUrl: String
)

/**
 * A single presentation entry.
 *
 * {
 *   "id": "uuid",
 *   "file-name": "MySlides.pptx",
 *   "file-type": "pptx",
 *   "slide-total": 5,
 *   "slides": [ { "slide-index": 0, "thumbnail-url": "/api/presentations/uuid/slides/0" }, … ]
 * }
 */
@Serializable
data class PresentationDto(
    val id: String,
    @kotlinx.serialization.SerialName("file-name")   val fileName: String,
    @kotlinx.serialization.SerialName("file-type")   val fileType: String,
    @kotlinx.serialization.SerialName("slide-total") val slideTotal: Int,
    val slides: List<SlideDto>
)

/**
 * Top-level response for GET /api/presentations
 *
 * {
 *   "presentations": [ … ],
 *   "total": 1
 * }
 */
@Serializable
data class PresentationCatalogResponse(
    val presentations: List<PresentationDto>,
    val total: Int
)

// ── Picture DTOs ──────────────────────────────────────────────────────────────

@Serializable
data class PictureFileDto(
    @kotlinx.serialization.SerialName("index")         val index: Int,
    @kotlinx.serialization.SerialName("file-name")     val fileName: String,
    @kotlinx.serialization.SerialName("thumbnail-url") val thumbnailUrl: String
)

/**
 * Top-level response for GET /api/pictures
 *
 * {
 *   "folder-id":   "a1b2c3d4",
 *   "folder-name": "Easter 2026",
 *   "image-total": 12,
 *   "images": [ { "index": 0, "file-name": "img001.jpg", "thumbnail-url": "/api/pictures/a1b2c3d4/images/0" }, … ]
 * }
 */
@Serializable
data class PictureFolderResponse(
    @kotlinx.serialization.SerialName("folder-id")    val folderId: String,
    @kotlinx.serialization.SerialName("folder-name")  val folderName: String,
    @kotlinx.serialization.SerialName("folder-path")  val folderPath: String,
    @kotlinx.serialization.SerialName("image-total")  val imageTotal: Int,
    val images: List<PictureFileDto>
)

@Serializable
data class SelectPictureRequest(
    @kotlinx.serialization.SerialName("folder-id") val folderId: String,
    val index: Int
)

// ── ServerInfoResponse / WebSocketMessage / etc. ──────────────────────────────

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

// ── Flat remote-item DTO (accepts the format mobile apps actually send) ───────
//
// Both POST /api/schedule/add and POST /api/project accept the same body:
//
//   { "item": { "id":"1", "songNumber":42, "title":"Amazing Grace", "songbook":"Hymns" } }
//
// The "type" discriminator required by kotlinx.serialization is NOT needed —
// the server infers the item type from which fields are present.

@Serializable
data class RemoteItemDto(
    val id: String = "",
    // song
    val songNumber: Int? = null,
    val title: String? = null,
    val songbook: String? = null,
    // bible
    val bookName: String? = null,
    val chapter: Int? = null,
    val verseNumber: Int? = null,
    val verseText: String? = null,
    /** Optional multi-verse range, e.g. "1-3" or "2,4". When present the schedule item groups all those verses. */
    val verseRange: String? = null,
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
    // display text (optional, ignored during parsing)
    val displayText: String? = null
)

@Serializable
data class RemoteItemRequest(val item: RemoteItemDto)

/**
 * Batch variant of [RemoteItemRequest] — used by POST /api/schedule/add-batch
 * and the [Constants.WS_CMD_ADD_BATCH_TO_SCHEDULE] WebSocket command.
 *
 * {
 *   "items": [
 *     { "bookName": "John",  "chapter": 3, "verseNumber": 16, "verseText": "For God so loved…" },
 *     { "bookName": "John",  "chapter": 3, "verseNumber": 17, "verseText": "For God did not send…" }
 *   ]
 * }
 */
@Serializable
data class RemoteItemsRequest(val items: List<RemoteItemDto>)

/**
 * Infer a [ScheduleItem] from the flat [RemoteItemDto] by detecting which fields are present.
 * Returns null if the dto doesn't match any known item type.
 */
fun RemoteItemDto.toScheduleItem(): ScheduleItem? {
    val safeId = id.ifBlank { java.util.UUID.randomUUID().toString() }
    return when {
        // Song — must have songNumber
        songNumber != null ->
            ScheduleItem.SongItem(
                id         = safeId,
                songNumber = songNumber,
                title      = title ?: "",
                songbook   = songbook ?: ""
            )
        // Bible verse — must have bookName + chapter + verseNumber
        bookName != null && chapter != null && verseNumber != null ->
            ScheduleItem.BibleVerseItem(
                id          = safeId,
                bookName    = bookName,
                chapter     = chapter,
                verseNumber = verseNumber,
                verseText   = verseText ?: "",
                verseRange  = verseRange ?: ""
            )
        // Picture folder — must have folderPath
        folderPath != null ->
            ScheduleItem.PictureItem(
                id         = safeId,
                folderPath = folderPath,
                folderName = folderName ?: folderPath,
                imageCount = imageCount ?: 0
            )
        // Presentation — must have filePath
        filePath != null ->
            ScheduleItem.PresentationItem(
                id         = safeId,
                filePath   = filePath,
                fileName   = fileName ?: filePath,
                slideCount = slideCount ?: 0,
                fileType   = fileType ?: ""
            )
        // Media — must have mediaUrl
        mediaUrl != null ->
            ScheduleItem.MediaItem(
                id         = safeId,
                mediaUrl   = mediaUrl,
                mediaTitle = mediaTitle ?: mediaUrl,
                mediaType  = mediaType ?: "local"
            )
        else -> null
    }
}

// Keep old wrappers for WS payloads that still use the sealed class discriminator
@Serializable
data class AddToScheduleRequest(val item: ScheduleItem)

@Serializable
data class ProjectRequest(val item: ScheduleItem)

/**
 * Wraps an incoming remote request with a [CompletableDeferred] that the UI
 * resolves once the user clicks Allow (true) or Deny/Block (false).
 * The HTTP endpoint suspends on [decision] before sending a response, so the
 * calling device receives the correct status code.
 */
data class PendingRemoteRequest(
    val item: ScheduleItem,
    val clientId: String = "",
    val decision: kotlinx.coroutines.CompletableDeferred<Boolean> = kotlinx.coroutines.CompletableDeferred()
)

/**
 * Same as [PendingRemoteRequest] but carries multiple items — used by the
 * batch add endpoint so the user approves or denies the whole group at once.
 */
data class PendingBatchRequest(
    val items: List<ScheduleItem>,
    val clientId: String = "",
    val decision: kotlinx.coroutines.CompletableDeferred<Boolean> = kotlinx.coroutines.CompletableDeferred()
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
    /** Raw song list kept in sync with _catalog for per-number detail lookups */
    @Volatile private var _songs: List<SongItem> = emptyList()
    private val _bibleCatalog = MutableStateFlow<BibleCatalogResponse?>(null)
    private val _bible = MutableStateFlow<org.churchpresenter.app.churchpresenter.data.Bible?>(null)
    private val _schedule = MutableStateFlow<List<ScheduleItemDto>>(emptyList())

    // Presentation catalog — metadata only; raw JPEG bytes stored per-slide in _slideBytes
    private val _presentationCatalog = MutableStateFlow(PresentationCatalogResponse(emptyList(), 0))
    /** presentationId → list of JPEG-encoded slide bytes (index = slide number) */
    private val _slideBytes = ConcurrentHashMap<String, List<ByteArray>>()
    /** presentationId (file hash) → PresentationDto — covers tab-loaded and background-rendered items */
    private val _presentationCatalogs = ConcurrentHashMap<String, PresentationDto>()
    /** schedule item UUID → presentation file hash — populated when schedule is updated */
    private val _scheduleItemToPresentationId = ConcurrentHashMap<String, String>()
    /** Set of presentation IDs currently being background-rendered (avoids duplicate renders) */
    private val _renderingPresentations = ConcurrentHashMap<String, Unit>()

    // Picture catalog — metadata + file references stored per folder
    private val _pictureCatalog = MutableStateFlow<PictureFolderResponse?>(null)
    /** folderId → ordered list of image Files (index = image order) */
    private val _pictureFiles = ConcurrentHashMap<String, List<File>>()
    /** folderId → catalog metadata (covers both the active folder and all schedule picture items) */
    private val _pictureCatalogs = ConcurrentHashMap<String, PictureFolderResponse>()
    /** Recognised image extensions — matches PicturesViewModel */
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif")

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

    /** Emitted when a remote client selects a picture image (POST /api/pictures/select or WS select_picture). */
    val onSelectPicture = MutableSharedFlow<SelectPictureRequest>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emitted when a remote client requests an item to be sent directly to projection. */
    val onProject = MutableSharedFlow<PendingRemoteRequest>(
        extraBufferCapacity = 16,
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
    @Volatile private var lottieLogosDir: File? = null

    /** Resolved path to the Lottie-Gen directory containing lottie-generator.html */
    private val lottieGenDir: File? by lazy { findLottieGenDir() }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Update the logos directory to be inside the lower third folder. */
    fun updateLowerThirdFolder(folder: String) {
        if (folder.isNotEmpty()) {
            lottieLogosDir = File(folder, "logos").also { it.mkdirs() }
        }
    }

    /** Update API key settings without restarting the server. */
    fun  updateApiKey(enabled: Boolean, key: String) {
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
        _songs = songs
        val catalog = buildCatalog(songs)
        _catalog.value = catalog
        broadcast(WebSocketMessage(
            type = Constants.WS_EVENT_SONGS_UPDATED,
            payload = json.encodeToString(SongCatalogResponse.serializer(), catalog)
        ))
    }

    /** Feed the primary Bible — builds full nested catalog and broadcasts to WS clients. */
    fun updateBible(bible: org.churchpresenter.app.churchpresenter.data.Bible, translation: String) {
        _bible.value = bible
        val catalog = buildBibleCatalog(bible, translation)
        _bibleCatalog.value = catalog
        broadcast(WebSocketMessage(
            type = Constants.WS_EVENT_BIBLE_UPDATED,
            payload = json.encodeToString(BibleCatalogResponse.serializer(), catalog)
        ))
    }

    /**
     * Feed a loaded presentation (id, fileName, fileType and rendered slide bitmaps).
     * Slides are encoded to JPEG on the IO thread and stored in memory so clients
     * can fetch them individually via GET /api/presentations/{id}/slides/{index}.
     *
     * [slides] is a list of AWT [BufferedImage] objects produced by PresentationViewModel.
     * To avoid a hard dependency on Compose runtime here we accept [Any] and downcast.
     */
    fun updatePresentation(
        id: String,
        fileName: String,
        fileType: String,
        slides: List<BufferedImage>
    ) {
        scope.launch {
            val jpegSlides = slides.map { img ->
                val baos = ByteArrayOutputStream()
                ImageIO.write(img, "jpg", baos)
                baos.toByteArray()
            }
            _slideBytes[id] = jpegSlides

            val catalog = buildPresentationCatalog(id, fileName, fileType, jpegSlides.size)
            // Cache the dto so GET /api/presentations/{id} works for tab-loaded presentations
            _presentationCatalogs[id] = catalog.presentations.first()
            _presentationCatalog.value = catalog
            broadcast(WebSocketMessage(
                type = Constants.WS_EVENT_PRESENTATION_UPDATED,
                payload = json.encodeToString(PresentationCatalogResponse.serializer(), catalog)
            ))
        }
    }

    /**
     * Feed the current picture folder — stores file references and broadcasts
     * [Constants.WS_EVENT_PICTURES_UPDATED]. Image bytes are read from disk on-demand
     * when a remote client requests [Constants.ENDPOINT_PICTURES]/{id}/images/{index}.
     *
     * [folderId] is a stable ID derived from the folder path (e.g. hex hash).
     * [folderName] is the display name shown to remote clients.
     * [folderPath] is the absolute filesystem path.
     * [imageFiles] is the ordered list of image Files in the folder.
     */
    fun updatePictures(
        folderId: String,
        folderName: String,
        folderPath: String,
        imageFiles: List<File>
    ) {
        // Store file references — bytes are read on-demand when a client requests an image
        _pictureFiles[folderId] = imageFiles.toList()

        val catalog = PictureFolderResponse(
            folderId = folderId,
            folderName = folderName,
            folderPath = folderPath,
            imageTotal = imageFiles.size,
            images = imageFiles.mapIndexed { index, file ->
                PictureFileDto(
                    index = index,
                    fileName = file.name,
                    thumbnailUrl = "${Constants.ENDPOINT_PICTURES}/$folderId/images/$index"
                )
            }
        )
        _pictureCatalog.value = catalog
        _pictureCatalogs[folderId] = catalog
        broadcast(WebSocketMessage(
            type = Constants.WS_EVENT_PICTURES_UPDATED,
            payload = json.encodeToString(PictureFolderResponse.serializer(), catalog)
        ))
    }

    /**
     * Scans [folderPath] for image files, then caches them in [_pictureFiles] and [_pictureCatalogs]
     * under [id] (the schedule item's UUID).  Skipped if [id] is already registered.
     * Must be called on an IO thread.
     */
    private fun registerPictureItem(id: String, folderPath: String, folderName: String) {
        if (_pictureFiles.containsKey(id)) return          // already cached
        val folder = File(folderPath)
        if (!folder.exists() || !folder.isDirectory) return
        val imageFiles = folder.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in IMAGE_EXTENSIONS }
            ?.sortedBy { it.name }
            ?: return
        if (imageFiles.isEmpty()) return
        _pictureFiles[id] = imageFiles
        _pictureCatalogs[id] = PictureFolderResponse(
            folderId   = id,
            folderName = folderName,
            folderPath = folderPath,
            imageTotal = imageFiles.size,
            images     = imageFiles.mapIndexed { index, file ->
                PictureFileDto(
                    index        = index,
                    fileName     = file.name,
                    thumbnailUrl = "${Constants.ENDPOINT_PICTURES}/$id/images/$index"
                )
            }
        )
    }

    /**
     * Background-renders a presentation file to JPEG slides, then stores them in [_slideBytes]
     * and [_presentationCatalogs] so GET /api/presentations/{id} and
     * GET /api/presentations/{id}/slides/{index} work for every schedule presentation item
     * without the user needing to open it in PresentationTab first.
     *
     * Mirrors [registerPictureItem] — called on the IO dispatcher; does NOT affect UI state.
     */
    private fun renderPresentationForServer(presentationId: String, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) return
        try {
            val images: List<BufferedImage> = when (file.extension.lowercase()) {
                "pdf"        -> renderPdfForServer(file)
                "pptx", "ppt" -> renderPowerPointForServer(file)
                "key"        -> renderKeynoteForServer(file)
                else         -> return
            }
            if (images.isEmpty()) return
            val jpegSlides = images.map { img ->
                ByteArrayOutputStream().also { baos -> ImageIO.write(img, "jpg", baos) }.toByteArray()
            }
            _slideBytes[presentationId] = jpegSlides
            val slideDtos = jpegSlides.indices.map { i ->
                SlideDto(slideIndex = i, thumbnailUrl = "${Constants.ENDPOINT_PRESENTATIONS}/$presentationId/slides/$i")
            }
            _presentationCatalogs[presentationId] = PresentationDto(
                id        = presentationId,
                fileName  = file.nameWithoutExtension,
                fileType  = file.extension.lowercase(),
                slideTotal = jpegSlides.size,
                slides    = slideDtos
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun renderPdfForServer(file: File): List<BufferedImage> {
        val result = mutableListOf<BufferedImage>()
        try {
            val docClass  = Class.forName("org.apache.pdfbox.pdmodel.PDDocument")
            val rendClass = Class.forName("org.apache.pdfbox.rendering.PDFRenderer")
            val doc   = docClass.getMethod("load", File::class.java).invoke(null, file)
            val pages = docClass.getMethod("getNumberOfPages").invoke(doc) as Int
            val rend  = rendClass.getConstructor(docClass).newInstance(doc)
            val renderDpi = rendClass.getMethod("renderImageWithDPI", Int::class.java, Float::class.java)
            for (p in 0 until pages) {
                result.add(renderDpi.invoke(rend, p, 150f) as BufferedImage)
            }
            docClass.getMethod("close").invoke(doc)
        } catch (_: ClassNotFoundException) {
        } catch (e: Exception) { e.printStackTrace() }
        return result
    }

    private fun renderPowerPointForServer(file: File): List<BufferedImage> {
        val result = mutableListOf<BufferedImage>()
        val className = if (file.extension.lowercase() == "pptx")
            "org.apache.poi.xslf.usermodel.XMLSlideShow"
        else
            "org.apache.poi.hslf.usermodel.HSLFSlideShow"
        try {
            val clazz = Class.forName(className)
            val fis   = java.io.FileInputStream(file)
            val ppt   = clazz.getConstructor(java.io.InputStream::class.java).newInstance(fis)
            val slides = clazz.getMethod("getSlides").invoke(ppt) as List<*>
            val size   = clazz.getMethod("getPageSize").invoke(ppt) as java.awt.Dimension
            slides.forEach { slide ->
                val s = slide ?: return@forEach
                val img = BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB)
                val g = img.createGraphics()
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
                g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY)
                g.paint = java.awt.Color.WHITE
                g.fillRect(0, 0, size.width, size.height)
                s::class.java.getMethod("draw", java.awt.Graphics2D::class.java).invoke(s, g)
                g.dispose()
                result.add(img)
            }
            clazz.getMethod("close").invoke(ppt)
            fis.close()
        } catch (_: ClassNotFoundException) {
        } catch (e: Exception) { e.printStackTrace() }
        return result
    }

    /**
     * Renders Keynote slides by extracting the .key bundle and reading the pre-rendered
     * st- thumbnail images that Keynote embeds in every package.
     */
    private fun renderKeynoteForServer(file: File): List<BufferedImage> {
        val result = mutableListOf<BufferedImage>()
        // Read zip entry order so we can recreate presentation order
        val slideIwaOrder = mutableListOf<Long>()
        if (!file.isDirectory) {
            try {
                java.util.zip.ZipFile(file).use { zip ->
                    zip.entries().asSequence()
                        .map { it.name }
                        .filter { n ->
                            val base = n.substringAfterLast("/")
                            base.startsWith("Slide-") && base.endsWith(".iwa") && base != "Slide.iwa"
                        }
                        .forEach { n ->
                            val base = n.substringAfterLast("/")
                            base.removePrefix("Slide-").removeSuffix(".iwa").split("-")[0]
                                .toLongOrNull()?.let { slideIwaOrder.add(it) }
                        }
                }
            } catch (_: Exception) {}
        }
        var tempDir: File? = null
        val keynoteDir: File
        if (file.isDirectory) {
            keynoteDir = file
        } else if (file.name.endsWith(".key")) {
            tempDir = File(System.getProperty("java.io.tmpdir"), "keynote_server_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            try {
                java.util.zip.ZipInputStream(java.io.BufferedInputStream(java.io.FileInputStream(file))).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val out = File(tempDir, entry.name)
                        if (entry.isDirectory) out.mkdirs()
                        else {
                            out.parentFile?.mkdirs()
                            java.io.BufferedOutputStream(java.io.FileOutputStream(out)).use { zip.copyTo(it) }
                        }
                        zip.closeEntry(); entry = zip.nextEntry
                    }
                }
                keynoteDir = tempDir
            } catch (e: Exception) { tempDir.deleteRecursively(); return result }
        } else return result

        try {
            val dataDir = File(keynoteDir, "Data")
            if (!dataDir.exists()) return result
            val stFiles = dataDir.listFiles()?.filter { f ->
                f.isFile && f.extension.lowercase() in listOf("jpg", "jpeg", "png", "tiff", "tif") &&
                    f.name.lowercase().startsWith("st-")
            } ?: return result
            val stSortedByStId = stFiles.sortedBy { f ->
                f.nameWithoutExtension.split("-").lastOrNull()?.toLongOrNull() ?: Long.MAX_VALUE
            }
            val ordered: List<File> = if (slideIwaOrder.isNotEmpty()) {
                val iwaOrderSorted = slideIwaOrder.sorted()
                val rankToSt = stSortedByStId.mapIndexed { rank, f -> rank to f }.toMap()
                val main = slideIwaOrder.mapNotNull { id ->
                    rankToSt[iwaOrderSorted.indexOf(id)]
                }.distinct()
                main + stFiles.filter { it !in main }
            } else stSortedByStId
            ordered.forEach { f -> ImageIO.read(f)?.let { result.add(it) } }
        } finally {
            tempDir?.deleteRecursively()
        }
        return result
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
                    verseRange = item.verseRange.ifEmpty { null },
                    text = item.verseText
                )
                is ScheduleItem.LabelItem -> ScheduleItemDto(
                    id = item.id, type = "label", displayText = item.displayText,
                    text = item.text, textColor = item.textColor, backgroundColor = item.backgroundColor
                )
                is ScheduleItem.PictureItem -> {
                    // Register folder on IO thread so GET /api/pictures/{id} works for every
                    // schedule picture item without requiring the user to load it first.
                    scope.launch(Dispatchers.IO) {
                        registerPictureItem(item.id, item.folderPath, item.folderName)
                    }
                    ScheduleItemDto(
                        id = item.id, type = "picture", displayText = item.displayText,
                        folderPath = item.folderPath, folderName = item.folderName, imageCount = item.imageCount
                    )
                }
                is ScheduleItem.PresentationItem -> {
                    // Map schedule UUID → stable file hash so GET /api/presentations/{id} resolves correctly.
                    val presentationId = item.filePath.hashCode().toUInt().toString(16)
                    _scheduleItemToPresentationId[item.id] = presentationId
                    // Render slides in the background if not already done / in-progress.
                    if (!_slideBytes.containsKey(presentationId) &&
                        _renderingPresentations.putIfAbsent(presentationId, Unit) == null) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                renderPresentationForServer(presentationId, item.filePath)
                            } finally {
                                _renderingPresentations.remove(presentationId)
                            }
                        }
                    }
                    ScheduleItemDto(
                        id = item.id, type = "presentation", displayText = item.displayText,
                        filePath = item.filePath, fileName = item.fileName,
                        slideCount = item.slideCount, fileType = item.fileType
                    )
                }
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

    private fun buildSongDetail(song: SongItem): SongDetailDto {
        // Split the flat lyrics list into typed sections by detecting section-header lines
        val sections = mutableListOf<SongSectionDto>()
        var currentType = Constants.SECTION_TYPE_VERSE
        var currentLines = mutableListOf<String>()

        for (line in song.lyrics) {
            val trimmed = line.trim()
            val isSectionHeader = isHeaderLine(trimmed)
            val isChorus = isChorusHeader(trimmed)

            if (isSectionHeader) {
                // Flush previous section
                if (currentLines.isNotEmpty()) {
                    sections.add(SongSectionDto(type = currentType, lines = currentLines.toList()))
                    currentLines = mutableListOf()
                }
                currentType = if (isChorus) Constants.SECTION_TYPE_CHORUS else Constants.SECTION_TYPE_VERSE
                // Don't include the bare header line itself — it carries no lyric content
            } else if (trimmed.isNotEmpty()) {
                currentLines.add(line)
            }
        }
        // Flush last section
        if (currentLines.isNotEmpty()) {
            sections.add(SongSectionDto(type = currentType, lines = currentLines.toList()))
        }

        return SongDetailDto(
            number       = song.number,
            title        = song.title,
            songbook     = song.songbook,
            tune         = song.tune,
            author       = song.author,
            composer     = song.composer,
            sectionTotal = sections.size,
            sections     = sections
        )
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

    private fun buildPresentationCatalog(
        id: String,
        fileName: String,
        fileType: String,
        slideCount: Int
    ): PresentationCatalogResponse {
        val slides = (0 until slideCount).map { index ->
            SlideDto(
                slideIndex = index,
                thumbnailUrl = "${Constants.ENDPOINT_PRESENTATIONS}/$id/slides/$index"
            )
        }
        val dto = PresentationDto(
            id = id,
            fileName = fileName,
            fileType = fileType,
            slideTotal = slideCount,
            slides = slides
        )
        return PresentationCatalogResponse(
            presentations = listOf(dto),
            total = 1
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

        server?.start(wait = false)
        _isRunning.value = true
        _serverUrl.value = "https://${localIpAddress()}:$port"
    }

    /** Fallback plain-HTTP start used only if SSL cert generation fails. */
    private fun startPlainHttp(port: Int) {
        server = embeddedServer(Netty, configure = {
            connector {
                host = "0.0.0.0"
                this.port = port
            }
            // Plain HTTP on localhost for embedded WebView (mirrors the SSL dual-connector setup)
            connector {
                host = "127.0.0.1"
                this.port = port + 1
            }
        }) { configurePipeline() }
        server?.start(wait = false)
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
                allowHeader(Constants.HEADER_DEVICE_ID)
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

                /**
                 * GET /api/songs/{number}[?songbook=Name]
                 * Returns full song details including all lyric sections.
                 * Use ?songbook= to disambiguate when the same number exists in multiple songbooks.
                 */
                get("${Constants.ENDPOINT_SONGS}/{number}") {
                    if (!checkApiKey(call)) return@get
                    val number = call.parameters["number"] ?: run {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"missing number"}""")
                        return@get
                    }
                    val songbookFilter = call.request.queryParameters[Constants.QUERY_PARAM_SONGBOOK]
                    val song = _songs.firstOrNull { s ->
                        s.number == number &&
                            (songbookFilter.isNullOrBlank() || s.songbook.equals(songbookFilter, ignoreCase = true))
                    }
                    if (song == null) {
                        call.respond(io.ktor.http.HttpStatusCode.NotFound, """{"error":"song not found"}""")
                        return@get
                    }
                    call.respond(buildSongDetail(song))
                }

                get(Constants.ENDPOINT_SCHEDULE) {
                    if (!checkApiKey(call)) return@get
                    val schedule = _schedule.value
                    call.respond(ScheduleResponse(schedule, schedule.size))
                }

                /**
                 * POST /api/schedule/add
                 * Suspends until the user approves or denies the request in the UI.
                 * Returns {"ok":true} on Allow, {"ok":false,"reason":"denied"} on Deny,
                 * or {"ok":false,"reason":"blocked"} if the session is blocked.
                 */
                post(Constants.ENDPOINT_SCHEDULE_ADD) {
                    if (!checkApiKey(call)) return@post
                    val body = call.receiveText()
                    val item = parseRemoteItem(body)
                    if (item == null) {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"invalid request body"}""")
                        return@post
                    }
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    val pending = PendingRemoteRequest(item, clientId)
                    scope.launch { onAddToSchedule.emit(pending) }
                    val allowed = pending.decision.await()
                    if (allowed) {
                        call.respondText("""{"ok":true}""", ContentType.Application.Json)
                    } else {
                        call.respond(io.ktor.http.HttpStatusCode.Forbidden,
                            """{"ok":false,"reason":"${pending.decision.let { "denied" }}"}""")
                    }
                }

                /**
                 * POST /api/schedule/add-batch
                 * Adds multiple items in a single call.  Suspends until the user approves or denies
                 * the whole batch.  On Allow every valid item is added; on Deny nothing is added.
                 *
                 * Request body:
                 * {
                 *   "items": [
                 *     { "bookName": "John",  "chapter": 3, "verseNumber": 16, "verseText": "For God so loved…" },
                 *     { "bookName": "John",  "chapter": 3, "verseNumber": 17, "verseText": "For God did not send…" }
                 *   ]
                 * }
                 *
                 * Success:  {"ok":true,"added":2}
                 * Denied:   HTTP 403  {"ok":false,"reason":"denied"}
                 * Bad body: HTTP 400  {"error":"…"}
                 */
                post(Constants.ENDPOINT_SCHEDULE_ADD_BATCH) {
                    if (!checkApiKey(call)) return@post
                    val body = call.receiveText()
                    val items = try {
                        json.decodeFromString(RemoteItemsRequest.serializer(), body)
                            .items.mapNotNull { it.toScheduleItem() }
                    } catch (_: Exception) { null }
                    if (items.isNullOrEmpty()) {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest,
                            """{"error":"invalid request body or no recognisable items"}""")
                        return@post
                    }
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    val pending = PendingBatchRequest(items, clientId)
                    scope.launch { onAddBatchToSchedule.emit(pending) }
                    val allowed = pending.decision.await()
                    if (allowed) {
                        call.respondText("""{"ok":true,"added":${items.size}}""", ContentType.Application.Json)
                    } else {
                        call.respond(io.ktor.http.HttpStatusCode.Forbidden,
                            """{"ok":false,"reason":"denied"}""")
                    }
                }

                /**
                 * POST /api/project
                 * Same suspend-until-approved behaviour as /api/schedule/add.
                 */
                post(Constants.ENDPOINT_PROJECT) {
                    if (!checkApiKey(call)) return@post
                    val body = call.receiveText()
                    val item = parseRemoteItem(body)
                    if (item == null) {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"invalid request body"}""")
                        return@post
                    }
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    val pending = PendingRemoteRequest(item, clientId)
                    scope.launch { onProject.emit(pending) }
                    val allowed = pending.decision.await()
                    if (allowed) {
                        call.respondText("""{"ok":true}""", ContentType.Application.Json)
                    } else {
                        call.respond(io.ktor.http.HttpStatusCode.Forbidden,
                            """{"ok":false,"reason":"denied"}""")
                    }
                }

                get(Constants.ENDPOINT_BIBLE) {
                    if (!checkApiKey(call)) return@get
                    val catalog = _bibleCatalog.value
                    if (catalog == null) {
                        call.respond(io.ktor.http.HttpStatusCode.ServiceUnavailable, "Bible not loaded")
                        return@get
                    }
                    val bookParam     = call.request.queryParameters[Constants.QUERY_PARAM_BOOK]
                    val chapterFilter = call.request.queryParameters[Constants.QUERY_PARAM_CHAPTER]?.toIntOrNull()

                    // ── Numeric book id + chapter → return full chapter with verse text ──
                    val bookIdParam = bookParam?.toIntOrNull()
                    if (bookIdParam != null && chapterFilter != null) {
                        val bible = _bible.value
                        if (bible == null) {
                            call.respond(io.ktor.http.HttpStatusCode.ServiceUnavailable, "Bible not loaded")
                            return@get
                        }
                        val rawVerses = bible.getChapterVerses(bookIdParam, chapterFilter)
                        if (rawVerses.isEmpty()) {
                            call.respond(io.ktor.http.HttpStatusCode.NotFound, "Chapter not found")
                            return@get
                        }
                        val bookName = bible.getBookName(bookIdParam) ?: "Book $bookIdParam"
                        val verseDtos = rawVerses.map { BibleVerseDto(verse = it.verseNumber, text = it.verseText) }
                        call.respond(BibleChapterResponse(
                            translation = catalog.translation,
                            bookId = bookIdParam,
                            bookName = bookName,
                            chapter = chapterFilter,
                            verseTotal = verseDtos.size,
                            verses = verseDtos
                        ))
                        return@get
                    }

                    if (bookParam.isNullOrBlank()) {
                        call.respond(catalog)
                    } else {
                        val filteredBooks = catalog.books.filter {
                            it.bookName.equals(bookParam, ignoreCase = true)
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

                // ── Presentation endpoints ────────────────────────────────────

                /** GET /api/presentations — list all loaded presentations with slide metadata */
                get(Constants.ENDPOINT_PRESENTATIONS) {
                    if (!checkApiKey(call)) return@get
                    call.respond(_presentationCatalog.value)
                }

                /**
                 * GET /api/presentations/{id}
                 * Returns metadata for a specific presentation by its ID.
                 *
                 * The {id} is either:
                 *  - the schedule item UUID from GET /api/schedule (works for every presentation
                 *    item as soon as the schedule is received — slides are rendered in the background), or
                 *  - the presentation file hash returned by GET /api/presentations.
                 *
                 * Returns 404 while background rendering is still in progress — retry after a moment.
                 */
                get("${Constants.ENDPOINT_PRESENTATIONS}/{id}") {
                    if (!checkApiKey(call)) return@get
                    val id = call.parameters["id"] ?: run {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, "Missing id")
                        return@get
                    }
                    val resolvedId = _scheduleItemToPresentationId[id] ?: id
                    val dto = _presentationCatalogs[resolvedId]
                    if (dto == null) {
                        call.respond(io.ktor.http.HttpStatusCode.NotFound, "Presentation not found or not yet rendered")
                        return@get
                    }
                    call.respond(dto)
                }

                /**
                 * GET /api/presentations/{id}/slides/{index}
                 * Returns the slide at {index} as a JPEG image for the presentation with {id}.
                 */
                get("${Constants.ENDPOINT_PRESENTATIONS}/{id}/slides/{index}") {
                    if (!checkApiKey(call)) return@get
                    val id    = call.parameters["id"]    ?: run { call.respond(io.ktor.http.HttpStatusCode.BadRequest, "Missing id"); return@get }
                    val index = call.parameters["index"]?.toIntOrNull() ?: run { call.respond(io.ktor.http.HttpStatusCode.BadRequest, "Invalid index"); return@get }
                    val resolvedId = _scheduleItemToPresentationId[id] ?: id
                    val slides = _slideBytes[resolvedId]
                    if (slides == null) {
                        call.respond(io.ktor.http.HttpStatusCode.NotFound, "Presentation not found")
                        return@get
                    }
                    if (index < 0 || index >= slides.size) {
                        call.respond(io.ktor.http.HttpStatusCode.NotFound, "Slide index out of range")
                        return@get
                    }
                    call.respondBytes(slides[index], ContentType.Image.JPEG)
                }

                // ── Picture endpoints ─────────────────────────────────────────

                /**
                 * GET /api/pictures
                 * Returns the currently loaded picture folder metadata with per-image thumbnail URLs.
                 */
                get(Constants.ENDPOINT_PICTURES) {
                    if (!checkApiKey(call)) return@get
                    val catalog = _pictureCatalog.value
                    if (catalog == null) {
                        call.respond(io.ktor.http.HttpStatusCode.ServiceUnavailable, "No picture folder loaded")
                        return@get
                    }
                    call.respond(catalog)
                }

                /**
                 * GET /api/pictures/{id}
                 * Returns catalog metadata for the picture folder with {id}.
                 * Works for any schedule picture item (by its schedule UUID) as well as the
                 * currently active folder loaded via the Pictures tab.
                 */
                get("${Constants.ENDPOINT_PICTURES}/{id}") {
                    if (!checkApiKey(call)) return@get
                    val id = call.parameters["id"] ?: run { call.respond(io.ktor.http.HttpStatusCode.BadRequest, "Missing id"); return@get }
                    val catalog = _pictureCatalogs[id]
                    if (catalog == null) {
                        call.respond(io.ktor.http.HttpStatusCode.NotFound, "Picture folder not found")
                        return@get
                    }
                    call.respond(catalog)
                }

                /**
                 * GET /api/pictures/{id}/images/{index}
                 * Returns the image at {index} as a JPEG for the folder with {id}.
                 */
                get("${Constants.ENDPOINT_PICTURES}/{id}/images/{index}") {
                    if (!checkApiKey(call)) return@get
                    val id    = call.parameters["id"]    ?: run { call.respond(io.ktor.http.HttpStatusCode.BadRequest, "Missing id"); return@get }
                    val index = call.parameters["index"]?.toIntOrNull() ?: run { call.respond(io.ktor.http.HttpStatusCode.BadRequest, "Invalid index"); return@get }
                    val files = _pictureFiles[id]
                    if (files == null) {
                        call.respond(io.ktor.http.HttpStatusCode.NotFound, "Picture folder not found")
                        return@get
                    }
                    if (index < 0 || index >= files.size) {
                        call.respond(io.ktor.http.HttpStatusCode.NotFound, "Image index out of range")
                        return@get
                    }
                    val file = files[index]
                    if (!file.exists()) {
                        call.respond(io.ktor.http.HttpStatusCode.NotFound, "Image file not found on disk")
                        return@get
                    }
                    // HEIC/HEIF are not displayable by browsers — convert to JPEG first
                    val ext = file.extension.lowercase()
                    if (ext == "heic" || ext == "heif") {
                        val jpegBytes = HeicDecoder.toJpegBytes(file)
                        if (jpegBytes != null) {
                            call.respondBytes(jpegBytes, ContentType.Image.JPEG)
                        } else {
                            call.respond(io.ktor.http.HttpStatusCode.InternalServerError, "Failed to convert HEIC image")
                        }
                    } else {
                        call.respondBytes(file.readBytes(), contentTypeForExtension(ext))
                    }
                }

                /**
                 * POST /api/pictures/select
                 * Body: { "folder-id": "…", "index": 3 }
                 * Selects the image at {index} in the specified folder, triggering the [onSelectPicture] callback.
                 */
                post("${Constants.ENDPOINT_PICTURES}/select") {
                    if (!checkApiKey(call)) return@post
                    try {
                        val req = json.decodeFromString(SelectPictureRequest.serializer(), call.receiveText())
                        scope.launch { onSelectPicture.emit(req) }
                        call.respondText("""{"ok":true}""", ContentType.Application.Json)
                    } catch (_: Exception) {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"invalid request body"}""")
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
                    val presentationCatalog = _presentationCatalog.value
                    if (presentationCatalog.presentations.isNotEmpty()) {
                        send(Frame.Text(json.encodeToString(WebSocketMessage.serializer(),
                            WebSocketMessage(Constants.WS_EVENT_PRESENTATION_UPDATED,
                                json.encodeToString(PresentationCatalogResponse.serializer(), presentationCatalog)))))
                    }
                    _pictureCatalog.value?.let { pictureCatalog ->
                        send(Frame.Text(json.encodeToString(WebSocketMessage.serializer(),
                            WebSocketMessage(Constants.WS_EVENT_PICTURES_UPDATED,
                                json.encodeToString(PictureFolderResponse.serializer(), pictureCatalog)))))
                    }

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
                                    Constants.WS_CMD_SELECT_PICTURE -> {
                                        val req = json.decodeFromString(SelectPictureRequest.serializer(), msg.payload)
                                        scope.launch { onSelectPicture.emit(req) }
                                    }
                                    Constants.WS_CMD_ADD_TO_SCHEDULE -> {
                                        val item = parseRemoteItem(msg.payload)
                                            ?: json.decodeFromString(AddToScheduleRequest.serializer(), msg.payload).item
                                        val pending = PendingRemoteRequest(item)
                                        scope.launch {
                                            onAddToSchedule.emit(pending)
                                            // WS is fire-and-forget — complete automatically after emit
                                            // (decision resolved by UI; if blocked, item is dropped silently)
                                        }
                                    }
                                    Constants.WS_CMD_ADD_BATCH_TO_SCHEDULE -> {
                                        val items = try {
                                            json.decodeFromString(RemoteItemsRequest.serializer(), msg.payload)
                                                .items.mapNotNull { it.toScheduleItem() }
                                        } catch (_: Exception) { emptyList() }
                                        if (items.isNotEmpty()) {
                                            val pending = PendingBatchRequest(items)
                                            scope.launch { onAddBatchToSchedule.emit(pending) }
                                        }
                                    }
                                    Constants.WS_CMD_PROJECT -> {
                                        val item = parseRemoteItem(msg.payload)
                                            ?: json.decodeFromString(ProjectRequest.serializer(), msg.payload).item
                                        val pending = PendingRemoteRequest(item)
                                        scope.launch { onProject.emit(pending) }
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

                // Serve fonts.css from Lottie-Gen directory
                get("/fonts.css") {
                    val file = lottieGenDir?.let { File(it, "fonts.css") }
                    if (file == null || !file.exists()) {
                        call.respond(io.ktor.http.HttpStatusCode.NotFound)
                        return@get
                    }
                    call.respondText(file.readText(), ContentType.Text.CSS)
                }

                // Serve font files from Lottie-Gen/fonts directory
                get("/fonts/{filename}") {
                    val filename = call.parameters["filename"] ?: return@get
                    val file = lottieGenDir?.let { File(File(it, "fonts"), filename) }
                    if (file == null || !file.exists()) {
                        call.respond(io.ktor.http.HttpStatusCode.NotFound)
                        return@get
                    }
                    val contentType = when (file.extension.lowercase()) {
                        "woff2" -> ContentType("font", "woff2")
                        "woff" -> ContentType("font", "woff")
                        "ttf" -> ContentType("font", "ttf")
                        else -> ContentType.Application.OctetStream
                    }
                    call.respondBytes(file.readBytes(), contentType)
                }

                // Serve logo image files
                get("/logos/{filename}") {
                    val filename = call.parameters["filename"] ?: return@get
                    // Check user logos dir first, then fall back to bundled Lottie-Gen dir
                    val file = lottieLogosDir?.let { File(it, filename) }?.takeIf { it.exists() }
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
                    // Include logos from user data dir (if configured)
                    val logosDir = lottieLogosDir
                    if (logosDir != null && logosDir.exists()) {
                        logosDir.listFiles()
                            ?.filter { it.extension.lowercase().matches(Regex("png|jpe?g|svg|webp")) }
                            ?.forEach { files.add(it.name) }
                    }
                    // Always include bundled logos from Lottie-Gen
                    lottieGenDir?.let { File(it, "logos") }?.listFiles()
                        ?.filter { it.extension.lowercase().matches(Regex("png|jpe?g|svg|webp")) }
                        ?.forEach { files.add(it.name) }
                    val jsonArray = files.joinToString(",") { "\"$it\"" }
                    call.respondText("[$jsonArray]", ContentType.Application.Json)
                }

                // POST /api/logos — upload a logo (base64 JSON body: { name, data })
                post(Constants.ENDPOINT_LOTTIE_LOGOS) {
                    try {
                        val logosDir = lottieLogosDir
                        if (logosDir == null) {
                            call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"No lower third folder configured"}""")
                            return@post
                        }
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
                        logosDir.mkdirs()
                        val bytes = Base64.getDecoder().decode(base64Match.groupValues[1])
                        File(logosDir, safeName).writeBytes(bytes)
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

    /**
     * Try to parse a [ScheduleItem] from raw JSON using the flat [RemoteItemRequest] format first,
     * then fall back to the legacy sealed-class [AddToScheduleRequest] format.
     */
    private fun parseRemoteItem(body: String): ScheduleItem? {
        // 1. Try flat format: {"item":{"songNumber":42,"title":"…","songbook":"…"}}
        try {
            val req = json.decodeFromString(RemoteItemRequest.serializer(), body)
            val item = req.item.toScheduleItem()
            if (item != null) return item
        } catch (_: Exception) {}
        // 2. Fall back to legacy sealed-class format with discriminator
        try {
            return json.decodeFromString(AddToScheduleRequest.serializer(), body).item
        } catch (_: Exception) {}
        return null
    }

    private fun contentTypeForExtension(ext: String): ContentType = when (ext.lowercase()) {
        "jpg", "jpeg" -> ContentType.Image.JPEG
        "png"         -> ContentType.Image.PNG
        "gif"         -> ContentType.Image.GIF
        "webp"        -> ContentType.parse("image/webp")
        "bmp"         -> ContentType.parse("image/bmp")
        "heic", "heif"-> ContentType.parse("image/heic")
        else          -> ContentType.Image.JPEG
    }

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


