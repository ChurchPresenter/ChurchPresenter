package org.churchpresenter.app.churchpresenter.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.connector
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
import java.security.MessageDigest
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
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
import org.churchpresenter.app.churchpresenter.data.Bible
import org.churchpresenter.app.churchpresenter.data.Songs
import org.churchpresenter.app.churchpresenter.BuildConfig
import java.awt.image.BufferedImage
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
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
    val index: Int = -1,
    /** When provided, the server looks up the file by name so the correct image is
     *  displayed regardless of index-ordering differences between clients. */
    @kotlinx.serialization.SerialName("file-name") val fileName: String? = null,
)

/**
 * Payload for POST /api/presentations/{id}/select and WS "select_slide".
 * [id] is the presentation ID (file hash or schedule item UUID).
 * [index] is the 0-based slide index to display immediately (no approval).
 */
@Serializable
data class SelectSlideRequest(
    val id: String = "",
    val index: Int
)

/**
 * Payload for POST /api/bible/select and WS "select_bible_verse".
 * Instantly displays the given verse on the presentation output with no approval dialog.
 */
@Serializable
data class SelectBibleVerseRequest(
    val bookName: String,
    val chapter: Int,
    val verseNumber: Int,
    val verseText: String = "",
    val verseRange: String = ""
)

/**
 * Payload for POST /api/songs/{number}/select and WS "select_song_section".
 * [section] is the 0-based index into the song's section list (as returned by GET /api/songs/{number}).
 */
@Serializable
data class SelectSongSectionRequest(
    val number: String,
    val section: Int
)

// ── ServerInfoResponse / WebSocketMessage / etc. ──────────────────────────────

@Serializable
data class ServerInfoResponse(
    val name: String = Constants.SERVER_APP_NAME,
    val version: String = Constants.SERVER_VERSION,
    val port: Int
)

@Serializable
data class DevicePermissionsDto(
    val canPresent: Boolean = true,
    val canAddToSchedule: Boolean = true,
    val canUploadFiles: Boolean = true,
)

@Serializable
data class StatusResponse(
    val appVersion: String = Constants.SERVER_VERSION,
    val endpoints: List<String> = emptyList(),
    val bibles: List<String> = emptyList(),
    val songbooks: List<String> = emptyList(),
    val permissions: DevicePermissionsDto = DevicePermissionsDto(),
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
    /** Item type discriminator sent by the companion app ("song", "presentation", "image", etc.). */
    val type: String? = null,
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
    // picture (companion app uses folder-id/image-index; desktop uses folderPath)
    @kotlinx.serialization.SerialName("folder-id") val folderId: String? = null,
    @kotlinx.serialization.SerialName("image-index") val imageIndex: Int? = null,
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
    private val _bible = MutableStateFlow<Bible?>(null)
    private val _schedule = MutableStateFlow<List<ScheduleItemDto>>(emptyList())

    // Presentation catalog — metadata only; raw JPEG bytes stored per-slide in _slideBytes
    private val _presentationCatalog = MutableStateFlow(PresentationCatalogResponse(emptyList(), 0))
    /** presentationId → list of JPEG-encoded slide bytes (index = slide number). Max 5 cached. */
    private val _slideBytes = ConcurrentHashMap<String, List<ByteArray>>()
    private val _slideBytesOrder = java.util.concurrent.ConcurrentLinkedDeque<String>()
    private val MAX_CACHED_PRESENTATIONS = 5
    /** presentationId (file hash) → PresentationDto — covers tab-loaded and background-rendered items */
    private val _presentationCatalogs = ConcurrentHashMap<String, PresentationDto>()
    /** presentationId (file hash) → absolute file path — populated by updatePresentation and updateSchedule */
    private val _presentationFilePaths = ConcurrentHashMap<String, String>()
    /** schedule item UUID → presentation file hash — populated when schedule is updated */
    private val _scheduleItemToPresentationId = ConcurrentHashMap<String, String>()
    /** Set of presentation IDs currently being background-rendered (avoids duplicate renders) */
    private val _renderingPresentations = ConcurrentHashMap<String, Unit>()

    private fun cacheSlideBytes(id: String, slides: List<ByteArray>) {
        _slideBytes[id] = slides
        _slideBytesOrder.remove(id)
        _slideBytesOrder.addFirst(id)
        while (_slideBytesOrder.size > MAX_CACHED_PRESENTATIONS) {
            val evicted = _slideBytesOrder.pollLast()
            if (evicted != null) _slideBytes.remove(evicted)
        }
    }

    /** Stable folder ID used for all device-uploaded photos (accumulates across sessions). */
    private val DEVICE_UPLOADS_FOLDER_ID = "device_uploads"

    /**
     * ID of the most recently device-uploaded presentation file.
     * Cleared from [_presentationCatalogs], [_slideBytes], and [_presentationFilePaths] when a new
     * upload replaces it, so the mobile's presentation list never accumulates stale entries.
     */
    @Volatile private var _lastDeviceUploadedPresentationId: String? = null

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

    // File upload permission (updated from settings without restart)
    private val _fileUploadEnabled = MutableStateFlow(true)

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

    /** Emitted when a remote client calls POST /api/clear or sends WS "clear". Clears the display instantly. */
    val onClear = MutableSharedFlow<Unit>(
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
        val title: String,
        val detail: String = "",
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

    /** Allow or disallow file uploads from mobile devices without restarting the server. */
    fun updateFileUploadEnabled(enabled: Boolean) {
        _fileUploadEnabled.value = enabled
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
                        val songs = Songs()
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
                        val bible = Bible()
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
    fun updateBible(bible: Bible, translation: String) {
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
        filePath: String,
        fileName: String,
        fileType: String,
        slides: List<BufferedImage>
    ) {
        if (filePath.isNotBlank()) {
            _presentationFilePaths[id] = filePath
        }
        scope.launch {
            val jpegSlides = slides.map { img ->
                val baos = ByteArrayOutputStream()
                ImageIO.write(img, "jpg", baos)
                baos.toByteArray()
            }
            cacheSlideBytes(id, jpegSlides)

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
     * Returns the [File] for a specific image by folder ID and zero-based index, or null if not
     * found.  Used by the remote-select handler in MainDesktop so the correct file is presented
     * even when the requested folder differs from the currently loaded folder in the Pictures tab
     * (e.g. when the mobile sends a `device_uploads` selection).
     */
    fun getImageFile(folderId: String, index: Int): File? =
        _pictureFiles[folderId]?.getOrNull(index)

    /**
     * The folder-id of the currently active picture folder pushed to mobile companions via
     * GET /api/pictures.  Null until a folder has been loaded in the Pictures tab.
     */
    val activeFolderId: String? get() = _pictureCatalog.value?.folderId

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
     * Clears the device_uploads directory tree on server startup so that device photos
     * are session-only — they disappear when the server is restarted.
     * Deletes all dated subdirectories (e.g. device_uploads/2026-04-13/) and resets
     * every in-memory catalog entry whose folder-id starts with [DEVICE_UPLOADS_FOLDER_ID].
     */
    private fun clearDeviceUploads() {
        val baseDir = File(System.getProperty("user.home"), ".churchpresenter/device_uploads")
        baseDir.deleteRecursively()   // removes dated subdirs and all files inside them
        // Purge every device_uploads_* entry (handles any date or legacy flat entries)
        _pictureFiles.keys
            .filter { it == DEVICE_UPLOADS_FOLDER_ID || it.startsWith("${DEVICE_UPLOADS_FOLDER_ID}_") }
            .forEach { id -> _pictureFiles.remove(id); _pictureCatalogs.remove(id) }
    }

    private fun renderPresentationForServer(presentationId: String, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) return
        try {
            val images: List<BufferedImage> = when (file.extension.lowercase()) {
                "pdf"         -> renderPdfForServer(file)
                "pptx", "ppt" -> renderPowerPointForServer(file)
                "key"         -> renderKeynoteForServer(file)
                else          -> return
            }
            if (images.isEmpty()) return
            val jpegSlides = images.map { img ->
                ByteArrayOutputStream().also { baos -> ImageIO.write(img, "jpg", baos) }.toByteArray()
            }
            cacheSlideBytes(presentationId, jpegSlides)
            _presentationFilePaths[presentationId] = filePath
            val slideDtos = jpegSlides.indices.map { i ->
                SlideDto(slideIndex = i, thumbnailUrl = "${Constants.ENDPOINT_PRESENTATIONS}/$presentationId/slides/$i")
            }
            _presentationCatalogs[presentationId] = PresentationDto(
                id         = presentationId,
                fileName   = file.nameWithoutExtension,
                fileType   = file.extension.lowercase(),
                slideTotal = jpegSlides.size,
                slides     = slideDtos
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
            try {
                val pages = docClass.getMethod("getNumberOfPages").invoke(doc) as Int
                val rend  = rendClass.getConstructor(docClass).newInstance(doc)
                val renderDpi = rendClass.getMethod("renderImageWithDPI", Int::class.java, Float::class.java)
                for (p in 0 until pages) result.add(renderDpi.invoke(rend, p, 150f) as BufferedImage)
            } finally {
                docClass.getMethod("close").invoke(doc)
            }
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
            val clazz  = Class.forName(className)
            val fis    = java.io.FileInputStream(file)
            try {
                val ppt    = clazz.getConstructor(java.io.InputStream::class.java).newInstance(fis)
                try {
                    val slides = clazz.getMethod("getSlides").invoke(ppt) as List<*>
                    val size   = clazz.getMethod("getPageSize").invoke(ppt) as java.awt.Dimension
                    slides.forEach { slide ->
                        val s = slide ?: return@forEach
                        val img = BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB)
                        val g   = img.createGraphics()
                        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
                        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY)
                        g.paint = java.awt.Color.WHITE
                        g.fillRect(0, 0, size.width, size.height)
                        s::class.java.getMethod("draw", java.awt.Graphics2D::class.java).invoke(s, g)
                        g.dispose()
                        result.add(img)
                    }
                } finally {
                    clazz.getMethod("close").invoke(ppt)
                }
            } finally {
                fis.close()
            }
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
        val slideIwaOrder = mutableListOf<Long>()
        if (!file.isDirectory) {
            try {
                ZipFile(file).use { zip ->
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
                ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zip ->
                    val tempCanonical = tempDir.canonicalPath
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val out = File(tempDir, entry.name)
                        if (!out.canonicalPath.startsWith(tempCanonical + File.separator) &&
                            out.canonicalPath != tempCanonical) {
                            zip.closeEntry(); entry = zip.nextEntry; continue
                        }
                        if (entry.isDirectory) out.mkdirs()
                        else {
                            out.parentFile?.mkdirs()
                            BufferedOutputStream(FileOutputStream(out)).use { zip.copyTo(it) }
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
                val main = slideIwaOrder.mapNotNull { id -> rankToSt[iwaOrderSorted.indexOf(id)] }.distinct()
                main + stFiles.filter { it !in main }
            } else stSortedByStId
            ordered.forEach { f -> ImageIO.read(f)?.let { result.add(it) } }
        } finally {
            tempDir?.deleteRecursively()
        }
        return result
    }

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
                    scope.launch(Dispatchers.IO) {
                        registerPictureItem(item.id, item.folderPath, item.folderName)
                    }
                    ScheduleItemDto(
                        id = item.id, type = "picture", displayText = item.displayText,
                        folderPath = item.folderPath, folderName = item.folderName, imageCount = item.imageCount
                    )
                }
                is ScheduleItem.PresentationItem -> {
                    val presentationId = item.filePath.hashCode().toUInt().toString(16)
                    _scheduleItemToPresentationId[item.id] = presentationId
                    _presentationFilePaths[presentationId] = item.filePath
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
                is ScheduleItem.SceneItem -> ScheduleItemDto(
                    id = item.id, type = "scene", displayText = item.displayText
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
        val sections = mutableListOf<SongSectionDto>()
        var currentType = Constants.SECTION_TYPE_VERSE
        var currentLines = mutableListOf<String>()
        for (line in song.lyrics) {
            val trimmed = line.trim()
            val isSectionHeader = isHeaderLine(trimmed)
            val isChorus = isChorusHeader(trimmed)
            if (isSectionHeader) {
                if (currentLines.isNotEmpty()) {
                    sections.add(SongSectionDto(type = currentType, lines = currentLines.toList()))
                    currentLines = mutableListOf()
                }
                currentType = if (isChorus) Constants.SECTION_TYPE_CHORUS else Constants.SECTION_TYPE_VERSE
            } else if (trimmed.isNotEmpty()) {
                currentLines.add(line)
            }
        }
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
        return SongCatalogResponse(songBook = entries, songBooks = entries.size, total = songs.size)
    }

    private fun buildBibleCatalog(bible: Bible, translation: String): BibleCatalogResponse {
        val bookNames = bible.getBooks()
        val bookDtos = mutableListOf<BibleBookDto>()
        var totalVerses = 0
        bookNames.forEachIndexed { bookIndex, bookName ->
            val bookId = bible.getBookId(bookIndex)
            val chapterCount = bible.getChapterCount(bookIndex)
            val chapterDtos = (1..chapterCount).map { chapterNum ->
                val verseCount = bible.getVerseCountForChapter(bookId, chapterNum)
                totalVerses += verseCount
                BibleChapterDto(chapter = chapterNum, verseTotal = verseCount)
            }
            bookDtos.add(BibleBookDto(bookId = bookId, bookName = bookName,
                chapterTotal = chapterCount, chapters = chapterDtos))
        }
        return BibleCatalogResponse(translation = translation, books = bookDtos,
            bookTotal = bookDtos.size, verseTotal = totalVerses)
    }

    private fun buildPresentationCatalog(id: String, fileName: String, fileType: String,
                                         slideCount: Int): PresentationCatalogResponse {
        val slides = (0 until slideCount).map { index ->
            SlideDto(slideIndex = index,
                thumbnailUrl = "${Constants.ENDPOINT_PRESENTATIONS}/$id/slides/$index")
        }
        val dto = PresentationDto(id = id, fileName = fileName, fileType = fileType,
            slideTotal = slideCount, slides = slides)
        return PresentationCatalogResponse(presentations = listOf(dto), total = 1)
    }

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

        // Find the first pair of consecutive free ports starting from the requested one.
        // The server needs port N (plain-HTTP) and port N+1 (plain-HTTP localhost connector).
        val actualPort = findFreePortPair(port)
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
                // Plain HTTP on localhost for embedded WebView (mirrors the SSL dual-connector setup)
                connector {
                    host = "127.0.0.1"
                    this.port = port + 1
                }
            }) { configurePipeline() }
            server?.start(wait = false)
            _isRunning.value = true
            _serverUrl.value = "http://$displayHost:$port"
            scope.launch { clearDeviceUploads() }
        } catch (_: java.net.BindException) {
            server = null
        } catch (_: Exception) {
            server = null
        }
    }

    /**
     * Finds the first port >= [startPort] where BOTH [port] and [port]+1 are free.
     * The server requires two consecutive free ports (HTTPS + plain-HTTP localhost connector).
     * Scans up to 20 candidates before giving up and returning [startPort] as-is.
     */
    private fun findFreePortPair(startPort: Int): Int {
        for (candidate in startPort until startPort + 40 step 2) {
            if (isPortFree(candidate) && isPortFree(candidate + 1)) return candidate
        }
        return startPort  // give up — let Netty surface the real error
    }

    private fun isPortFree(port: Int): Boolean = try {
        ServerSocket(port).use { true }
    } catch (_: IOException) {
        false
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
                allowHeader(Constants.HEADER_APP_VERSION)
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

                // ── CA certificate download (no API key required) ──────────────────────────
                //
                // Mobile devices need to download and install the CA certificate BEFORE they
                // can make authenticated API calls. These two endpoints must therefore be
                // accessible without an API key.
                //
                // Trust-on-first-use flow:
                //  1. The companion app (or the user's browser) fetches GET /ca.crt.
                //     iOS: opening the URL in Safari triggers a "Download certificate profile"
                //           dialog; the user then goes to Settings ▸ VPN & Device Management.
                //     Android: the companion app installs the cert via the system
                //           Certificate Installer API or includes it in NetworkSecurityConfig.
                //  2. The user verifies the SHA-256 fingerprint shown in ChurchPresenter's UI.
                //  3. After one-time installation all HTTPS API calls succeed transparently.

                /**
                 * GET /ca.crt
                 * DER-encoded CA certificate (binary X.509).
                 * The MIME type `application/x-x509-ca-cert` causes iOS Safari / Chrome to
                 * present the system "Install Profile" dialog automatically.
                 */
                get("/ca.crt") {
                    val bytes = SslCertificateManager.getCaCertBytes()
                    if (bytes == null) {
                        call.respond(
                            io.ktor.http.HttpStatusCode.NotFound,
                            "CA certificate is not available (server may be running in plain-HTTP fallback mode)"
                        )
                        return@get
                    }
                    call.response.headers.append(
                        io.ktor.http.HttpHeaders.ContentDisposition,
                        """attachment; filename="ChurchPresenter-CA.crt""""
                    )
                    call.respondBytes(bytes, ContentType("application", "x-x509-ca-cert"))
                }

                /**
                 * GET /ca.pem
                 * PEM-encoded CA certificate (Base64 text).
                 * Used by:
                 *  • Android NetworkSecurityConfig — embed in `res/raw/ca.pem` and reference
                 *    via `<certificates src="@raw/ca"/>` in `network_security_config.xml`.
                 *  • OpenSSL / curl verification:  `curl --cacert ca.pem https://…`
                 *  • Any tool that expects PEM rather than DER format.
                 */
                get("/ca.pem") {
                    val pem = SslCertificateManager.getCaCertPem()
                    if (pem == null) {
                        call.respond(
                            io.ktor.http.HttpStatusCode.NotFound,
                            "CA certificate is not available (server may be running in plain-HTTP fallback mode)"
                        )
                        return@get
                    }
                    call.response.headers.append(
                        io.ktor.http.HttpHeaders.ContentDisposition,
                        """attachment; filename="ChurchPresenter-CA.pem""""
                    )
                    call.respondText(pem, ContentType("application", "x-pem-file"))
                }

                // ── API endpoints (require API key when enabled) ────────────────────────────

                get(Constants.ENDPOINT_INFO) {
                    if (!checkApiKey(call)) return@get
                    call.respond(ServerInfoResponse(port = currentPort))
                }

                get(Constants.ENDPOINT_STATUS) {
                    if (!checkApiKey(call)) return@get
                    val bibleNames = _bibleCatalog.value?.translation?.let { listOf(it) } ?: emptyList()
                    val songbookNames = _catalog.value.songBook.map { it.bookName }
                    val exposedEndpoints = listOf(
                        "songs", "bible", "schedule", "presentations", "pictures", "status"
                    )
                    call.response.headers.append(Constants.HEADER_SERVER_VERSION, BuildConfig.APP_VERSION)
                    call.respond(
                        StatusResponse(
                            appVersion  = BuildConfig.APP_VERSION,
                            endpoints   = exposedEndpoints,
                            bibles      = bibleNames,
                            songbooks   = songbookNames,
                            permissions = DevicePermissionsDto(
                                canPresent       = true,
                                canAddToSchedule = true,
                                canUploadFiles   = _fileUploadEnabled.value,
                            ),
                        )
                    )
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

                /**
                 * POST /api/songs/{number}/select
                 * Body: { "section": 2 }   — OR —   ?section=2 as query param
                 *
                 * Navigates the live presenter to section [section] (0-based) of the currently
                 * projected song.  No approval required — fires instantly.
                 *
                 * Response: {"ok":true}
                 */
                post("${Constants.ENDPOINT_SONGS}/{number}/select") {
                    if (!checkApiKey(call)) return@post
                    val number = call.parameters["number"] ?: run {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"missing number"}""")
                        return@post
                    }
                    // Accept section from query param OR JSON body
                    val sectionIndex = call.request.queryParameters["section"]?.toIntOrNull()
                        ?: runCatching {
                            json.decodeFromString(SelectSongSectionRequest.serializer(), call.receiveText()).section
                        }.getOrNull()
                    if (sectionIndex == null || sectionIndex < 0) {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"missing or invalid section index"}""")
                        return@post
                    }
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    scope.launch { onSelectSongSection.emit(SelectSongSectionRequest(number, sectionIndex)) }
                    scope.launch { onInstantAction.emit(RemoteInstantAction(
                        actionType = "present",
                        title = "Song $number",
                        detail = "Section $sectionIndex",
                        clientId = clientId
                    )) }
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
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

                /**
                 * POST /api/clear
                 * Instantly switches the presenter to display-none (Presenting.NONE).
                 * No request body or approval needed.
                 * Response: {"ok":true}
                 */
                post(Constants.ENDPOINT_CLEAR) {
                    if (!checkApiKey(call)) return@post
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    scope.launch { onClear.emit(Unit) }
                    scope.launch { onInstantAction.emit(RemoteInstantAction(
                        actionType = "clear",
                        title = "Clear Display",
                        clientId = clientId
                    )) }
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
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

                /**
                 * POST /api/bible/select
                 * Body: { "bookName": "John", "chapter": 3, "verseNumber": 16,
                 *         "verseText": "For God so loved…", "verseRange": "" }
                 *
                 * Instantly displays the given verse on the presentation output.
                 * No approval dialog — fires immediately like select_picture / select_song_section.
                 * Response: {"ok":true}
                 */
                post(Constants.ENDPOINT_BIBLE_SELECT) {
                    if (!checkApiKey(call)) return@post
                    val body = call.receiveText()
                    val req = try {
                        json.decodeFromString(SelectBibleVerseRequest.serializer(), body)
                    } catch (_: Exception) {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"invalid request body"}""")
                        return@post
                    }
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    scope.launch { onSelectBibleVerse.emit(req) }
                    val verseRef = if (req.verseRange.isNotEmpty()) "${req.bookName} ${req.chapter}:${req.verseRange}"
                                   else "${req.bookName} ${req.chapter}:${req.verseNumber}"
                    scope.launch { onInstantAction.emit(RemoteInstantAction(
                        actionType = "present",
                        title = verseRef,
                        detail = req.verseText.take(60),
                        clientId = clientId
                    )) }
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }

                // ── Presentation endpoints ────────────────────────────────────

                /**
                 * GET /api/presentations
                 *
                 * Returns only the presentation that is currently loaded in the desktop
                 * Presentations tab ([_presentationCatalog]).  The mobile list should
                 * mirror what the desktop shows — not accumulate every file that has ever
                 * been opened.  Schedule-driven navigation uses
                 * GET /api/presentations/{id} directly via [navigateTo], so individual
                 * schedule items are still accessible without polluting this list.
                 */
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

                /**
                 * POST /api/presentations/{id}/select
                 * Body: { "index": 2 }
                 *
                 * Instantly navigates the live presentation to slide [index] (0-based).
                 * No approval dialog — fires immediately like select_picture.
                 * The {id} is the presentation file hash or schedule item UUID.
                 * Response: {"ok":true}
                 */
                post("${Constants.ENDPOINT_PRESENTATIONS}/{id}/select") {
                    if (!checkApiKey(call)) return@post
                    val id = call.parameters["id"] ?: run {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"missing id"}""")
                        return@post
                    }
                    val index = call.request.queryParameters["index"]?.toIntOrNull()
                        ?: runCatching {
                            json.decodeFromString(SelectSlideRequest.serializer(), call.receiveText()).index
                        }.getOrNull()
                    if (index == null || index < 0) {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"missing or invalid index"}""")
                        return@post
                    }
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    scope.launch { onSelectSlide.emit(SelectSlideRequest(id = id, index = index)) }
                    val presentationName = _presentationCatalogs[_scheduleItemToPresentationId[id] ?: id]?.fileName ?: id
                    scope.launch { onInstantAction.emit(RemoteInstantAction(
                        actionType = "present",
                        title = presentationName,
                        detail = "Slide ${index + 1}",
                        clientId = clientId
                    )) }
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }

                /**
                 * POST /api/presentations/upload
                 * Body: { "name": "slides.pdf", "data": "data:application/pdf;base64,…" }
                 *
                 * Decodes the base64 data-URI, saves the file to
                 * ~/.churchpresenter/device_presentations/, and emits [onPresentationUploaded]
                 * so the desktop can load it into PresentationViewModel.
                 *
                 * Response: { "ok": true, "id": "<hex-hash>", "name": "<fileName>" }
                 */
                post("${Constants.ENDPOINT_PRESENTATIONS}/upload") {
                    if (!checkApiKey(call)) return@post
                    if (!_fileUploadEnabled.value) {
                        call.respond(io.ktor.http.HttpStatusCode.Forbidden, """{"error":"file upload is disabled"}""")
                        return@post
                    }
                    try {
                        val contentLength = call.request.headers["Content-Length"]?.toLongOrNull() ?: 0L
                        if (contentLength > 200 * 1024 * 1024) { // 200 MB limit
                            call.respond(io.ktor.http.HttpStatusCode.PayloadTooLarge, """{"error":"file too large (max 200 MB)"}""")
                            return@post
                        }
                        val body   = call.receiveText()
                        val parsed = json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject
                        val name   = (parsed?.get("name") as? kotlinx.serialization.json.JsonPrimitive)?.content
                        val data   = (parsed?.get("data") as? kotlinx.serialization.json.JsonPrimitive)?.content
                        if (name.isNullOrBlank() || data.isNullOrBlank()) {
                            call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"name and data are required"}""")
                            return@post
                        }
                        val safeName = File(name).name.ifBlank { "upload.pdf" }
                        val ext = safeName.substringAfterLast('.', "").lowercase()
                        if (ext !in setOf("pdf", "ppt", "pptx", "key")) {
                            call.respond(io.ktor.http.HttpStatusCode.UnsupportedMediaType, """{"error":"unsupported file type: $ext"}""")
                            return@post
                        }
                        val base64Match = Regex("^data:[^;]+;base64,(.+)$").find(data)
                        if (base64Match == null) {
                            call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"data must be a base64 data URI"}""")
                            return@post
                        }
                        val fileBytes = Base64.getDecoder().decode(base64Match.groupValues[1])
                        val uploadDir = File(System.getProperty("user.home"), ".churchpresenter/device_presentations").also { it.mkdirs() }
                        val uniqueName = if (File(uploadDir, safeName).exists()) {
                            val ts   = System.currentTimeMillis()
                            val base = safeName.substringBeforeLast('.', safeName)
                            "${base}_$ts.$ext"
                        } else safeName
                        val file = File(uploadDir, uniqueName)
                        file.writeBytes(fileBytes)
                        val id = file.absolutePath.hashCode().toUInt().toString(16)
                        // Evict the previous device-uploaded presentation so the mobile list
                        // never accumulates stale entries — only the latest upload is shown.
                        _lastDeviceUploadedPresentationId?.let { oldId ->
                            _presentationCatalogs.remove(oldId)
                            _slideBytes.remove(oldId)
                            _presentationFilePaths.remove(oldId)
                        }
                        _lastDeviceUploadedPresentationId = id
                        val uploadClientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                        scope.launch { onPresentationUploaded.emit(file) }
                        scope.launch { onInstantAction.emit(RemoteInstantAction(
                            actionType = "upload",
                            title = file.name,
                            detail = "${fileBytes.size / 1024} KB",
                            clientId = uploadClientId
                        )) }
                        call.respondText(
                            """{"ok":true,"id":"$id","name":"${file.nameWithoutExtension.replace("\"", "\\\"")}"}""",
                            ContentType.Application.Json
                        )
                    } catch (e: Exception) {
                        call.respond(io.ktor.http.HttpStatusCode.InternalServerError, """{"error":"upload failed: ${e.message?.replace("\"", "\\\"")}"}""")
                    }
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
                 * Body: { "folder-id": "…", "index": 3, "file-name": "photo.jpg" }
                 * When "file-name" is provided the index is resolved by name so the correct
                 * image is displayed regardless of sort-order differences between clients.
                 */
                post("${Constants.ENDPOINT_PICTURES}/select") {
                    if (!checkApiKey(call)) return@post
                    try {
                        val req = json.decodeFromString(SelectPictureRequest.serializer(), call.receiveText())
                        // Resolve index by filename when provided — immune to sort-order mismatch
                        val resolvedIndex = req.fileName
                            ?.let { name -> _pictureFiles[req.folderId]?.indexOfFirst { it.name == name }?.takeIf { it >= 0 } }
                            ?: req.index
                        val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                        scope.launch { onSelectPicture.emit(req.copy(index = resolvedIndex)) }
                        val folderName = _pictureCatalogs[req.folderId]?.folderName ?: req.folderId
                        val imageLabel = req.fileName ?: "Image $resolvedIndex"
                        scope.launch { onInstantAction.emit(RemoteInstantAction(
                            actionType = "present",
                            title = folderName,
                            detail = imageLabel,
                            clientId = clientId
                        )) }
                        call.respondText("""{"ok":true}""", ContentType.Application.Json)
                    } catch (_: Exception) {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"invalid request body"}""")
                    }
                }

                /**
                 * POST /api/pictures/upload
                 * Body: { "name": "photo.jpg", "data": "data:image/jpeg;base64,…" }
                 *
                 * Saves the uploaded image to ~/.churchpresenter/device_uploads/,
                 * registers it as a single-image folder so the other pictures endpoints
                 * serve it, and returns { "ok": true, "folder-id": "…", "image-index": 0 }.
                 */
                post("${Constants.ENDPOINT_PICTURES}/upload") {
                    if (!checkApiKey(call)) return@post
                    if (!_fileUploadEnabled.value) {
                        call.respond(io.ktor.http.HttpStatusCode.Forbidden, """{"error":"file upload is disabled"}""")
                        return@post
                    }
                    try {
                        val body = call.receiveText()
                        val parsed = json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject
                        val name = (parsed?.get("name") as? kotlinx.serialization.json.JsonPrimitive)?.content
                        val data = (parsed?.get("data") as? kotlinx.serialization.json.JsonPrimitive)?.content
                        if (name.isNullOrBlank() || data.isNullOrBlank()) {
                            call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"name and data are required"}""")
                            return@post
                        }
                        val safeName = File(name).name.ifBlank { "upload.jpg" }
                        val base64Match = Regex("^data:[^;]+;base64,(.+)$").find(data)
                        if (base64Match == null) {
                            call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"data must be a base64 data URI"}""")
                            return@post
                        }
                        val imageBytes = Base64.getDecoder().decode(base64Match.groupValues[1])
                        // Save to ~/.churchpresenter/device_uploads/yyyy-MM-dd/
                        // Each calendar day gets its own subfolder; the folderId includes the
                        // date so uploads from different days are catalogued separately.
                        val dateStr = java.time.LocalDate.now().toString()   // "yyyy-MM-dd"
                        val dateFolderId = "${DEVICE_UPLOADS_FOLDER_ID}_$dateStr"
                        val uploadDir = File(System.getProperty("user.home"), ".churchpresenter/device_uploads/$dateStr").also { it.mkdirs() }
                        // Ensure the file name is unique by appending a timestamp if needed
                        val uniqueName = if (File(uploadDir, safeName).exists()) {
                            val ts = System.currentTimeMillis()
                            val ext = safeName.substringAfterLast('.', "jpg")
                            val base = safeName.substringBeforeLast('.', safeName)
                            "${base}_$ts.$ext"
                        } else safeName
                        val file = File(uploadDir, uniqueName)
                        file.writeBytes(imageBytes)
                        // Accumulate into today's dated "Device Photos" folder.
                        // Sort by filename so the catalog index order matches the desktop's
                        // PicturesViewModel.loadImagesFromFolder which also sorts by name.
                        // Without this, upload order ≠ filename order, so index N on mobile
                        // points to a different photo than index N on the desktop.
                        val existingFiles = (_pictureFiles[dateFolderId] ?: emptyList()).toMutableList()
                        existingFiles.add(file)
                        existingFiles.sortBy { it.name }          // ← match desktop sort order
                        val newIndex = existingFiles.indexOf(file) // recalculate after sort
                        _pictureFiles[dateFolderId] = existingFiles
                        val catalog = PictureFolderResponse(
                            folderId   = dateFolderId,
                            folderName = "Device Photos ($dateStr)",
                            folderPath = uploadDir.absolutePath,
                            imageTotal = existingFiles.size,
                            images     = existingFiles.mapIndexed { idx, f ->
                                PictureFileDto(
                                    index        = idx,
                                    fileName     = f.name,
                                    thumbnailUrl = "${Constants.ENDPOINT_PICTURES}/$dateFolderId/images/$idx"
                                )
                            }
                        )
                        _pictureCatalogs[dateFolderId] = catalog
                        // Do NOT update _pictureCatalog here — that would replace the desktop's
                        // active folder with device_uploads, making GET /api/pictures return the
                        // wrong folder to the mobile companion app.
                        broadcast(WebSocketMessage(
                            type    = Constants.WS_EVENT_PICTURES_UPDATED,
                            payload = json.encodeToString(PictureFolderResponse.serializer(), catalog)
                        ))
                        val picUploadClientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                        scope.launch { onInstantAction.emit(RemoteInstantAction(
                            actionType = "upload",
                            title = file.name,
                            detail = catalog.folderName,
                            clientId = picUploadClientId
                        )) }
                        call.respondText(
                            """{"ok":true,"folder-id":"$dateFolderId","image-index":$newIndex,"file-name":"${file.name}"}""",
                            ContentType.Application.Json
                        )
                    } catch (e: Exception) {
                        call.respond(io.ktor.http.HttpStatusCode.InternalServerError, """{"error":"upload failed: ${e.message?.replace("\"","\\\"")}"}""")
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
                    val wsClientId = call.request.headers[Constants.HEADER_DEVICE_ID]
                        ?: call.request.queryParameters[Constants.HEADER_DEVICE_ID]
                        ?: ""

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

                    try {
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
                                        val folderName = _pictureCatalogs[req.folderId]?.folderName ?: req.folderId
                                        val imageLabel = req.fileName ?: "Image ${req.index}"
                                        scope.launch { onInstantAction.emit(RemoteInstantAction("present", folderName, imageLabel, wsClientId)) }
                                    }
                                    Constants.WS_CMD_SELECT_SONG_SECTION -> {
                                        val req = json.decodeFromString(SelectSongSectionRequest.serializer(), msg.payload)
                                        scope.launch { onSelectSongSection.emit(req) }
                                        scope.launch { onInstantAction.emit(RemoteInstantAction("present", "Song ${req.number}", "Section ${req.section}", wsClientId)) }
                                    }
                                    Constants.WS_CMD_SELECT_SLIDE -> {
                                        val req = json.decodeFromString(SelectSlideRequest.serializer(), msg.payload)
                                        scope.launch { onSelectSlide.emit(req) }
                                        val presName = _presentationCatalogs[_scheduleItemToPresentationId[req.id] ?: req.id]?.fileName ?: req.id
                                        scope.launch { onInstantAction.emit(RemoteInstantAction("present", presName, "Slide ${req.index + 1}", wsClientId)) }
                                    }
                                    Constants.WS_CMD_SELECT_BIBLE_VERSE -> {
                                        val req = json.decodeFromString(SelectBibleVerseRequest.serializer(), msg.payload)
                                        scope.launch { onSelectBibleVerse.emit(req) }
                                        val ref = if (req.verseRange.isNotEmpty()) "${req.bookName} ${req.chapter}:${req.verseRange}"
                                                  else "${req.bookName} ${req.chapter}:${req.verseNumber}"
                                        scope.launch { onInstantAction.emit(RemoteInstantAction("present", ref, req.verseText.take(60), wsClientId)) }
                                    }
                                    Constants.WS_CMD_CLEAR -> {
                                        scope.launch { onClear.emit(Unit) }
                                        scope.launch { onInstantAction.emit(RemoteInstantAction("clear", "Clear Display", clientId = wsClientId)) }
                                    }
                                    Constants.WS_CMD_ADD_TO_SCHEDULE -> {
                                        val item = parseRemoteItem(msg.payload)
                                            ?: json.decodeFromString(AddToScheduleRequest.serializer(), msg.payload).item
                                        val pending = PendingRemoteRequest(item, wsClientId)
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
                                            val pending = PendingBatchRequest(items, wsClientId)
                                            scope.launch { onAddBatchToSchedule.emit(pending) }
                                        }
                                    }
                                    Constants.WS_CMD_PROJECT -> {
                                        val item = parseRemoteItem(msg.payload)
                                            ?: json.decodeFromString(ProjectRequest.serializer(), msg.payload).item
                                        val pending = PendingRemoteRequest(item, wsClientId)
                                        scope.launch { onProject.emit(pending) }
                                    }
                                }
                            } catch (_: Exception) { /* ignore malformed frames */ }
                        }
                    }
                    } finally {
                        broadcastJob.cancel()
                    }
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
                    val rawFilename = call.parameters["filename"] ?: return@get
                    val filename = File(rawFilename).name
                    val fontsDir = lottieGenDir?.let { File(it, "fonts") }
                    val file = fontsDir?.let { File(it, filename) }
                    if (file == null || !file.exists() ||
                        !file.canonicalPath.startsWith(fontsDir!!.canonicalPath + File.separator)) {
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
                    val rawFilename = call.parameters["filename"] ?: return@get
                    val filename = File(rawFilename).name
                    val userLogosDir = lottieLogosDir
                    val bundledLogosDir = lottieGenDir?.let { File(it, "logos") }
                    val file = userLogosDir?.let { File(it, filename) }
                        ?.takeIf { it.exists() && it.canonicalPath.startsWith(userLogosDir.canonicalPath + File.separator) }
                        ?: bundledLogosDir?.let { File(it, filename) }
                            ?.takeIf { it.exists() && it.canonicalPath.startsWith(bundledLogosDir.canonicalPath + File.separator) }
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
                    if (!checkApiKey(call)) return@get
                    val data = if (lottiePresetsFile.exists()) lottiePresetsFile.readText() else "[]"
                    call.respondText(data, ContentType.Application.Json)
                }

                // POST /api/presets — save generator presets
                post(Constants.ENDPOINT_LOTTIE_PRESETS) {
                    if (!checkApiKey(call)) return@post
                    val body = call.receiveText()
                    lottiePresetsFile.writeText(body)
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }

                // GET /api/color-themes — load color themes
                get(Constants.ENDPOINT_LOTTIE_COLOR_THEMES) {
                    if (!checkApiKey(call)) return@get
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
                    if (!checkApiKey(call)) return@post
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
                    val jsonArray = files.joinToString(",") { name ->
                        "\"${name.replace("\\", "\\\\").replace("\"", "\\\"")}\""
                    }
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
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancelChildren()
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
            val dto = req.item
            // Handle picture identified by folder-id (companion app format).
            // folderPath is null in this case — resolve via the cached catalog.
            if (dto.folderId != null && dto.folderPath == null) {
                val catalog = _pictureCatalogs[dto.folderId]
                if (catalog != null) {
                    val safeId = dto.id.ifBlank { java.util.UUID.randomUUID().toString() }
                    return ScheduleItem.PictureItem(
                        id         = safeId,
                        folderPath = catalog.folderPath,
                        folderName = catalog.folderName,
                        imageCount = catalog.imageTotal
                    )
                }
            }
            // Handle presentation identified by id/fileHash (companion app format).
            // filePath is not sent — resolve via _presentationFilePaths (populated by
            // updatePresentation and updateSchedule) then fall back to _schedule scan.
            // NOTE: mobile may omit the "type" field when it equals the default ("presentation"),
            // so also accept type==null as long as the id resolves in _presentationFilePaths.
            if (dto.filePath == null && dto.folderId == null && dto.id.isNotBlank() &&
                (dto.type == "presentation" || dto.type == null)) {
                val filePath = _presentationFilePaths[dto.id]
                    ?: _schedule.value.firstOrNull { s ->
                        s.type == "presentation" && (
                            s.id == dto.id ||
                            s.filePath?.hashCode()?.toUInt()?.toString(16) == dto.id
                        )
                    }?.filePath
                if (filePath != null) {
                    val catalog = _presentationCatalogs[dto.id]
                    return ScheduleItem.PresentationItem(
                        id         = java.util.UUID.randomUUID().toString(),
                        filePath   = filePath,
                        fileName   = catalog?.fileName ?: dto.title ?: "",
                        slideCount = catalog?.slideTotal ?: 0,
                        fileType   = catalog?.fileType ?: ""
                    )
                }
            }
            val item = dto.toScheduleItem()
            if (item != null) return item
        } catch (_: Exception) {
            // ignore — try legacy format below
        }
        // 2. Fall back to legacy sealed-class format with discriminator
        try {
            return json.decodeFromString(AddToScheduleRequest.serializer(), body).item
        } catch (_: Exception) {
            // ignore
        }
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
        return if (MessageDigest.isEqual(provided.toByteArray(), _apiKey.value.toByteArray())) {
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

    /**
     * Returns the best local IPv4 address for display in the Server URL.
     *
     * Preference order (most-stable first):
     *   1. Wired Ethernet  — eth*, en0 (macOS primary)
     *   2. Other en* interfaces (macOS: en1 = WiFi, etc.)
     *   3. wlan* / wifi*
     *   4. Everything else (non-loopback, non-virtual, non-VPN)
     *
     * Configure a static IP or use [start]'s hostOverride parameter to bypass
     * this entirely and always display a fixed address.
     */
    private fun localIpAddress(): String {
        return try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { iface -> iface.isUp && !iface.isLoopback && !iface.isVirtual }
                .sortedWith(compareBy { iface ->
                    val name = iface.name.lowercase()
                    when {
                        name.startsWith("eth")  -> 0   // Linux wired
                        name == "en0"           -> 1   // macOS primary (usually wired on desktops)
                        name.startsWith("en")   -> 2   // macOS secondary (WiFi is typically en1+)
                        name.startsWith("wlan") -> 3   // Linux WiFi
                        name.startsWith("wifi") -> 3
                        else                    -> 10  // VPNs, bridges, docker, etc.
                    }
                })
                .flatMap { iface -> iface.inetAddresses.asSequence() }
                .firstOrNull { addr -> !addr.isLoopbackAddress && addr.hostAddress.contains('.') }
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

