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
import io.ktor.server.routing.delete
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.churchpresenter.app.churchpresenter.data.SongItem
import org.churchpresenter.app.churchpresenter.data.settings.AtemSettings
import org.churchpresenter.app.churchpresenter.data.settings.PresentationRemoteSettings
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.viewmodel.isLottieFile
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import org.churchpresenter.app.churchpresenter.utils.HeicDecoder
import org.churchpresenter.app.churchpresenter.utils.isChorusHeader
import org.churchpresenter.app.churchpresenter.utils.isHeaderLine
import org.churchpresenter.app.churchpresenter.data.Bible
import org.churchpresenter.app.churchpresenter.data.Songs
import org.churchpresenter.app.churchpresenter.models.QuestionStatus
import org.churchpresenter.app.churchpresenter.models.SubmitQuestionRequest
import org.churchpresenter.app.churchpresenter.models.VoteRequest
import org.churchpresenter.app.churchpresenter.models.toDto
import org.churchpresenter.app.churchpresenter.viewmodel.QAManager
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
    val id: Int = 0,
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

    private var _qaEventJob: kotlinx.coroutines.Job? = null
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
    @Volatile private var _currentPresentationId: String = ""
    @Volatile private var _currentSlideIndex: Int = 0
    @Volatile private var _currentSlideTotalCount: Int = 0
    @Volatile private var _presentationFrozen: Boolean = false
    @Volatile private var _presentationIsPlaying: Boolean = false
    @Volatile private var _presentationIsLive: Boolean = false
    @Volatile private var _autoScrollInterval: Int = 5

    fun updatePresentationRemoteSettings(settings: PresentationRemoteSettings, apiKey: String) {
        val wasEnabled = presentationRemoteEnabled
        presentationRemoteEnabled = settings.remoteControlEnabled
        presentationRemotePassword = apiKey
        if (wasEnabled && !presentationRemoteEnabled) clearPresentationState()
    }

    fun updateAutoScrollInterval(secs: Int) {
        if (_autoScrollInterval == secs) return
        _autoScrollInterval = secs
        broadcast(WebSocketMessage(
            type = Constants.WS_EVENT_PRESENTATION_AUTO_SCROLL_CHANGED,
            payload = """{"autoScrollInterval":$secs}"""
        ))
    }

    fun clearPresentationState() {
        _currentPresentationId = ""
        _currentSlideIndex = 0
        _currentSlideTotalCount = 0
        _presentationIsPlaying = false
        _presentationIsLive = false
        broadcast(WebSocketMessage(
            type = Constants.WS_EVENT_PRESENTATION_SLIDE_CHANGED,
            payload = """{"id":"","index":0,"total":0,"isPlaying":false,"isLive":false}"""
        ))
    }

    fun updatePresentationLiveStatus(isLive: Boolean) {
        if (_presentationIsLive == isLive) return
        _presentationIsLive = isLive
        broadcast(WebSocketMessage(
            type = Constants.WS_EVENT_PRESENTATION_LIVE_CHANGED,
            payload = """{"isLive":$isLive}"""
        ))
    }

    fun broadcastSlideChange(id: String, index: Int, total: Int, isPlaying: Boolean) {
        _currentPresentationId = id
        _currentSlideIndex = index
        _currentSlideTotalCount = total
        _presentationIsPlaying = isPlaying
        broadcast(WebSocketMessage(
            type = Constants.WS_EVENT_PRESENTATION_SLIDE_CHANGED,
            payload = """{"id":"$id","index":$index,"total":$total,"isPlaying":$isPlaying,"isLive":$_presentationIsLive}"""
        ))
    }

    fun broadcastFreezeChange(frozen: Boolean) {
        _presentationFrozen = frozen
        broadcast(WebSocketMessage(
            type = Constants.WS_EVENT_PRESENTATION_FREEZE_CHANGED,
            payload = """{"frozen":$frozen}"""
        ))
    }

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

    /** Emitted when remote jumps to a specific slide index. */
    val onPresentationGoto = MutableSharedFlow<Int>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // ATEM + lower-third folder config for the Companion lower-third sequencer
    @Volatile private var _atemSettings: AtemSettings? = null
    @Volatile private var _lowerThirdFolder: String = ""

    fun updateAtemConfig(atem: AtemSettings, lowerThirdFolder: String) {
        val prev = _atemSettings
        if (prev == null || prev.host != atem.host || prev.port != atem.port) {
            AtemConnectionManager.invalidate()
        }
        _atemSettings = atem
        _lowerThirdFolder = lowerThirdFolder
    }

    /** Lottie files in the configured lower-third folder. */
    private fun lowerThirdFiles(): List<java.io.File> =
        java.io.File(_lowerThirdFolder).takeIf { _lowerThirdFolder.isNotEmpty() && it.isDirectory }
            ?.listFiles { f -> f.extension.lowercase() == "json" && isLottieFile(f) }
            ?.sortedBy { it.nameWithoutExtension.lowercase() } ?: emptyList()

    private fun jsonStr(s: String): String =
        json.encodeToString(kotlinx.serialization.serializer<String>(), s)

    /** Shared body of the run/show endpoints. */
    private suspend fun handleLowerThirdTrigger(
        call: io.ktor.server.application.ApplicationCall,
        autoEnd: Boolean
    ) {
        val rawName = call.parameters["name"] ?: ""
        val file = lowerThirdFiles().firstOrNull { it.nameWithoutExtension.equals(rawName, ignoreCase = true) }
        if (file == null) {
            call.respond(io.ktor.http.HttpStatusCode.NotFound, """{"error":"lower third not found"}""")
            return
        }
        val ltJson = try { file.readText() } catch (_: Exception) {
            call.respond(io.ktor.http.HttpStatusCode.InternalServerError, """{"error":"could not read lottie file"}""")
            return
        }
        val durationMs = AtemRenderCache.lottieDurationMs(ltJson)
        if (durationMs == null) {
            call.respond(io.ktor.http.HttpStatusCode.UnprocessableEntity, """{"error":"lottie has no timing information"}""")
            return
        }
        val atem = _atemSettings ?: AtemSettings()

        // Key target: USK (M/E + keyer) or DSK (?keytype / setting) from settings;
        // ?me=N&key=M (1-based) override; ?key=0 skips. For DSK ?key overrides the DSK index.
        val useDsk = resolveUseDsk(call, atem)
        val meParam = call.request.queryParameters["me"]?.toIntOrNull()
        val keyParam = call.request.queryParameters["key"]?.toIntOrNull()
        val mixEffect: Int?
        val keyer: Int?
        if (keyParam == 0) {
            mixEffect = null; keyer = null
        } else {
            mixEffect = if (useDsk) 0 else (if (meParam != null) meParam - 1 else atem.keyMixEffect)
            keyer = if (keyParam != null) keyParam - 1
                else if (useDsk) atem.dskIndex else atem.keyIndex
            validateKeyTarget(atem, useDsk, mixEffect, keyer)?.let {
                call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":${jsonStr(it)}}""")
                return
            }
        }

        val pause = call.request.queryParameters["pause"]?.toBooleanStrictOrNull() ?: false
        val pauseDurationMs = call.request.queryParameters["pauseDurationMs"]?.toLongOrNull() ?: 2000L

        val keyError = LowerThirdSequencer.run(
            name = file.nameWithoutExtension,
            json = ltJson,
            durationMs = durationMs,
            pauseAtFrame = pause,
            pauseDurationMs = pauseDurationMs,
            mixEffect = mixEffect,
            keyer = keyer,
            atem = atem,
            useDownstreamKey = useDsk,
            autoEnd = autoEnd
        )
        val totalMs = atem.keyPreRollMs + durationMs +
            (if (pause) pauseDurationMs else 0L) + atem.keyPostRollMs
        call.respondText(
            """{"status":"started","name":${jsonStr(file.nameWithoutExtension)},"durationMs":$durationMs,""" +
                """"totalMs":${if (autoEnd) totalMs else -1},"keyError":${keyError?.let { jsonStr(it) } ?: "null"}}""",
            ContentType.Application.Json
        )
    }

    /**
     * Standalone upstream-key on/off (POST /api/atem/key/on|off). Reuses the shared
     * keepalive connection when free; falls back to a short-lived connection when an
     * upload holds it, so a key cut never waits behind an upload. Synchronous 200/502.
     */
    private suspend fun handleKeyToggle(call: io.ktor.server.application.ApplicationCall, onAir: Boolean) {
        val atem = _atemSettings
        if (atem == null || atem.host.isBlank()) {
            call.respond(io.ktor.http.HttpStatusCode.ServiceUnavailable, """{"error":"ATEM not configured"}""")
            return
        }
        val useDsk = resolveUseDsk(call, atem)
        val meParam = call.request.queryParameters["me"]?.toIntOrNull()
        val keyParam = call.request.queryParameters["key"]?.toIntOrNull()
        val mixEffect = if (useDsk) 0 else (if (meParam != null) meParam - 1 else atem.keyMixEffect)
        val keyer = if (keyParam != null) keyParam - 1
            else if (useDsk) atem.dskIndex else atem.keyIndex
        validateKeyTarget(atem, useDsk, mixEffect, keyer)?.let {
            call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":${jsonStr(it)}}""")
            return
        }
        try {
            val ran = AtemConnectionManager.tryRun(atem.host, atem.port) { client ->
                client.setKeyOnAir(useDsk, mixEffect, keyer, onAir)
            }
            if (!ran) AtemClient.cutKey(atem.host, atem.port, useDsk, mixEffect, keyer, onAir)
            val target = if (useDsk) """"dsk":${keyer + 1}""" else """"me":${mixEffect + 1},"key":${keyer + 1}"""
            call.respondText(
                """{"status":"${if (onAir) "on" else "off"}",$target}""",
                ContentType.Application.Json
            )
        } catch (e: Exception) {
            call.respond(
                io.ktor.http.HttpStatusCode.BadGateway,
                """{"error":${jsonStr(e.message ?: "ATEM command failed")}}"""
            )
        }
    }

    /**
     * Validate a 0-based key target against the detected topology. Null = OK.
     * For a downstream key [keyer] is the DSK index and [mixEffect] is ignored.
     */
    private fun validateKeyTarget(atem: AtemSettings, useDsk: Boolean, mixEffect: Int, keyer: Int): String? {
        if (useDsk) {
            if (atem.detectedDownstreamKeyers > 0 && keyer !in 0 until atem.detectedDownstreamKeyers)
                return "DSK ${keyer + 1} does not exist (available: 1-${atem.detectedDownstreamKeyers})"
            return null
        }
        if (atem.detectedMixEffects > 0 && mixEffect !in 0 until atem.detectedMixEffects)
            return "M/E ${mixEffect + 1} does not exist (available: 1-${atem.detectedMixEffects})"
        val keyers = atem.detectedKeyersPerMe.getOrNull(mixEffect)
        if (keyers != null && keyers > 0 && keyer !in 0 until keyers)
            return "Key ${keyer + 1} does not exist on M/E ${mixEffect + 1} (available: 1-$keyers)"
        return null
    }

    /**
     * Resolves whether a request should drive a downstream key: `?keytype=dsk|usk` (or
     * `downstream|upstream`) overrides; otherwise the persisted [AtemSettings.useDownstreamKey].
     */
    private fun resolveUseDsk(call: io.ktor.server.application.ApplicationCall, atem: AtemSettings): Boolean =
        when (call.request.queryParameters["keytype"]?.lowercase()) {
            "dsk", "downstream" -> true
            "usk", "upstream" -> false
            else -> atem.useDownstreamKey
        }

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
    /** Cancels previous updatePresentation encode job when a new presentation is loaded */
    private var _activeUpdateJob: Job? = null

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
    /** Emitted when a remote client requests a QA admin operation (add/edit/delete). */
    data class PendingQAAdminRequest(
        val action: String,
        val questionId: String = "",
        val text: String = "",
        val clientId: String = "",
        val decision: kotlinx.coroutines.CompletableDeferred<Boolean> = kotlinx.coroutines.CompletableDeferred()
    )

    val onQAAdminRequest = MutableSharedFlow<PendingQAAdminRequest>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emitted when the web admin triggers Go Live or clear display for Q&A. Payload: Question or null. */
    val onQADisplay = MutableSharedFlow<org.churchpresenter.app.churchpresenter.models.Question?>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val onClear = MutableSharedFlow<Unit>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emitted when a remote client sends WS "bible_hold". Payload: {"hold": true/false}. */
    val onBibleHold = MutableSharedFlow<Boolean>(
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

    val tunnelManager = TunnelManager()


    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var currentPort: Int = Constants.SERVER_DEFAULT_PORT

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    // ── Public API ────────────────────────────────────────────────────────────

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
                                try { songs.loadFromSpsAppend(file.absolutePath) } catch (e: Exception) {
                                    System.err.println("[CompanionServer] Failed to load song ${file.name}: ${e.message}")
                                }
                            }
                        if (songs.getSongCount() > 0) {
                            updateSongs(songs.getSongs())
                        }
                    }
                } catch (e: Exception) {
                    System.err.println("[CompanionServer] Failed to load songs from $songStorageDir: ${e.message}")
                }
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
                } catch (e: Exception) {
                    System.err.println("[CompanionServer] Failed to load bible $primaryBibleFileName: ${e.message}")
                }
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
     * Feed a loaded presentation (id, fileName, fileType and already-encoded JPEG slide files).
     * Reads bytes from disk on the IO thread; no re-encoding needed since files are already JPEG.
     */
    fun updatePresentation(
        id: String,
        filePath: String,
        fileName: String,
        fileType: String,
        slideFiles: List<File>
    ) {
        if (filePath.isNotBlank()) {
            _presentationFilePaths[id] = filePath
        }
        _activeUpdateJob?.cancel()
        _activeUpdateJob = scope.launch {
            val jpegSlides = slideFiles.map { it.readBytes() }
            cacheSlideBytes(id, jpegSlides)

            val catalog = buildPresentationCatalog(id, fileName, fileType, jpegSlides.size)
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
            val images: List<BufferedImage> = CrashReporter.trace("server.render", "Server render presentation") {
                when (file.extension.lowercase()) {
                    "pdf"         -> renderPdfForServer(file)
                    "pptx", "ppt" -> renderPowerPointForServer(file)
                    "key"         -> renderKeynoteForServer(file)
                    else          -> return
                }
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
            } catch (e: Exception) {
                System.err.println("[CompanionServer] Keynote slide-order scan failed for ${file.name}: ${e.message}")
            }
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
                is ScheduleItem.DictionaryItem -> ScheduleItemDto(
                    id = item.id, type = "dictionary", displayText = item.displayText,
                    text = "${item.word} (${item.transliteration}): ${item.definition}"
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
        // Build an index map so each SongDto gets a unique id (position in _songs)
        val indexMap = songs.withIndex().associate { (i, s) -> s to i }
        val entries = songs
            .groupBy { it.songbook }
            .entries
            .sortedBy { it.key }
            .map { (bookName, bookSongs) ->
                SongbookEntry(
                    bookName = bookName,
                    songTotal = bookSongs.size,
                    songs = bookSongs.map { s ->
                        SongDto(id = indexMap[s] ?: 0, number = s.number, title = s.title, tune = s.tune, author = s.author)
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
                // Plain HTTP on localhost for embedded WebView
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

    private fun findFreePort(startPort: Int): Int {
        for (candidate in startPort until startPort + 40) {
            if (isPortFree(candidate)) return candidate
        }
        return startPort
    }

    /**
     * Finds the first port >= [startPort] where BOTH [port] and [port]+1 are free.
     * The server requires two consecutive free ports (main HTTP + localhost connector).
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
                allowMethod(HttpMethod.Delete)
                allowHeader(HttpHeaders.ContentType)
                allowHeader(Constants.HEADER_API_KEY)
                allowHeader(Constants.HEADER_DEVICE_ID)
                allowHeader(Constants.HEADER_APP_VERSION)
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
                get("${Constants.ENDPOINT_SONGS}/{identifier}") {
                    if (!checkApiKey(call)) return@get
                    val identifier = call.parameters["identifier"] ?: run {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"missing identifier"}""")
                        return@get
                    }
                    val songbookFilter = call.request.queryParameters[Constants.QUERY_PARAM_SONGBOOK]
                    val titleFilter = call.request.queryParameters["title"]
                    // Try index-based lookup first (id=N query param)
                    val idParam = call.request.queryParameters["id"]?.toIntOrNull()
                    val song = if (idParam != null) {
                        _songs.getOrNull(idParam)
                    } else {
                        // Fall back to number + songbook match; treat "_" as empty number
                        val lookupNumber = if (identifier == "_") "" else identifier
                        _songs.firstOrNull { s ->
                            val matchesSongbook = songbookFilter.isNullOrBlank() || s.songbook.equals(songbookFilter, ignoreCase = true)
                            val matchesNumber = s.number == lookupNumber
                            val matchesTitle = !titleFilter.isNullOrBlank() && s.title.equals(titleFilter, ignoreCase = true)
                            matchesSongbook && (matchesNumber || matchesTitle)
                        }
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

                // ── Presentation remote control endpoints ─────────────────────

                /** GET /presentation-remote — mobile remote control web page */
                get("/presentation-remote") {
                    call.respondText(presentationRemotePageHtml(), ContentType.Text.Html)
                }

                /** GET /api/presentation-remote/status — current presentation state (no auth needed) */
                get("/api/presentation-remote/status") {
                    call.respondText(
                        """{"enabled":$presentationRemoteEnabled,"id":"$_currentPresentationId","index":$_currentSlideIndex,"total":$_currentSlideTotalCount,"frozen":$_presentationFrozen,"isPlaying":$_presentationIsPlaying,"isLive":$_presentationIsLive,"autoScrollInterval":$_autoScrollInterval,"passwordRequired":${presentationRemotePassword.isNotEmpty()}}""",
                        ContentType.Application.Json
                    )
                }

                /** POST /api/presentation-remote/auth — verify password */
                post("/api/presentation-remote/auth") {
                    if (!checkPresentationRemoteAuth(call)) return@post
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }

                /** POST /api/presentation-remote/next */
                post("/api/presentation-remote/next") {
                    if (!checkPresentationRemoteAuth(call)) return@post
                    val next = (_currentSlideIndex + 1).coerceAtMost(_currentSlideTotalCount - 1)
                    scope.launch { onPresentationGoto.emit(next) }
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }

                /** POST /api/presentation-remote/previous */
                post("/api/presentation-remote/previous") {
                    if (!checkPresentationRemoteAuth(call)) return@post
                    val prev = (_currentSlideIndex - 1).coerceAtLeast(0)
                    scope.launch { onPresentationGoto.emit(prev) }
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }

                /** POST /api/presentation-remote/goto/{index} */
                post("/api/presentation-remote/goto/{index}") {
                    if (!checkPresentationRemoteAuth(call)) return@post
                    val index = call.parameters["index"]?.toIntOrNull()
                        ?: run { call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"missing index"}"""); return@post }
                    val clamped = index.coerceIn(0, (_currentSlideTotalCount - 1).coerceAtLeast(0))
                    scope.launch { onPresentationGoto.emit(clamped) }
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }

                /** POST /api/presentation-remote/freeze — toggle blank/unblank */
                post("/api/presentation-remote/freeze") {
                    if (!checkPresentationRemoteAuth(call)) return@post
                    scope.launch { onPresentationFreezeToggle.emit(Unit) }
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }

                /** POST /api/presentation-remote/play-pause */
                post("/api/presentation-remote/play-pause") {
                    if (!checkPresentationRemoteAuth(call)) return@post
                    scope.launch { onPresentationPlayPause.emit(Unit) }
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }

                /** POST /api/presentation-remote/go-live — send presentation to presenter screen */
                post("/api/presentation-remote/go-live") {
                    if (!checkPresentationRemoteAuth(call)) return@post
                    scope.launch { onPresentationGoLive.emit(Unit) }
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }

                /** POST /api/presentation-remote/upload — base64 file upload from remote page */
                post("/api/presentation-remote/upload") {
                    if (!checkPresentationRemoteAuth(call)) return@post
                    handlePresentationFileUpload(call)
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
                    send(Frame.Text(json.encodeToString(WebSocketMessage.serializer(),
                        WebSocketMessage(
                            type = Constants.WS_EVENT_PRESENTATION_SLIDE_CHANGED,
                            payload = """{"id":"$_currentPresentationId","index":$_currentSlideIndex,"total":$_currentSlideTotalCount,"isPlaying":$_presentationIsPlaying,"isLive":$_presentationIsLive}"""
                        ))))

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
                                    Constants.WS_CMD_BIBLE_HOLD -> {
                                        val hold = try {
                                            json.parseToJsonElement(msg.payload)
                                                .jsonObject["hold"]?.toString()?.toBooleanStrictOrNull() ?: true
                                        } catch (_: Exception) { true }
                                        scope.launch { onBibleHold.emit(hold) }
                                    }
                                    Constants.WS_CMD_ADD_TO_SCHEDULE -> {
                                        val item = parseRemoteItem(msg.payload)
                                            ?: json.decodeFromString(AddToScheduleRequest.serializer(), msg.payload).item
                                        val pending = PendingRemoteRequest(item, wsClientId)
                                        scope.launch {
                                            onAddToSchedule.emit(pending)
                                            val allowed = try { pending.decision.await() } catch (_: Exception) { false }
                                            val response = if (allowed) """{"ok":true}""" else """{"ok":false,"reason":"denied"}"""
                                            try { send(Frame.Text(response)) } catch (_: Exception) { }
                                        }
                                    }
                                    Constants.WS_CMD_ADD_BATCH_TO_SCHEDULE -> {
                                        val items = try {
                                            json.decodeFromString(RemoteItemsRequest.serializer(), msg.payload)
                                                .items.mapNotNull { it.toScheduleItem() }
                                        } catch (_: Exception) { emptyList() }
                                        if (items.isNotEmpty()) {
                                            val pending = PendingBatchRequest(items, wsClientId)
                                            scope.launch {
                                                onAddBatchToSchedule.emit(pending)
                                                val allowed = try { pending.decision.await() } catch (_: Exception) { false }
                                                val response = if (allowed) """{"ok":true}""" else """{"ok":false,"reason":"denied"}"""
                                                try { send(Frame.Text(response)) } catch (_: Exception) { }
                                            }
                                        }
                                    }
                                    Constants.WS_CMD_PROJECT -> {
                                        val item = parseRemoteItem(msg.payload)
                                            ?: json.decodeFromString(ProjectRequest.serializer(), msg.payload).item
                                        val pending = PendingRemoteRequest(item, wsClientId)
                                        scope.launch {
                                            onProject.emit(pending)
                                            val allowed = try { pending.decision.await() } catch (_: Exception) { false }
                                            val response = if (allowed) """{"ok":true}""" else """{"ok":false,"reason":"denied"}"""
                                            try { send(Frame.Text(response)) } catch (_: Exception) { }
                                        }
                                    }
                                }
                            } catch (_: Exception) { /* ignore malformed frames */ }
                        }
                    }
                    } finally {
                        broadcastJob.cancel()
                    }
                }

                // ── Lower Third Sequencer (Bitfocus Companion) ───────────────────
                // One HTTP call runs the whole timed sequence: ATEM key on → play
                // the lower third → key off when the animation ends.

                get("/api/lowerthirds") {
                    if (!checkApiKey(call)) return@get
                    val items = lowerThirdFiles().map { f ->
                        val dur = try { AtemRenderCache.lottieDurationMs(f.readText()) ?: 0L } catch (_: Exception) { 0L }
                        val nameJson = json.encodeToString(kotlinx.serialization.serializer<String>(), f.nameWithoutExtension)
                        """{"name":$nameJson,"durationMs":$dur}"""
                    }
                    call.respondText("[${items.joinToString(",")}]", ContentType.Application.Json)
                }

                post("/api/lowerthirds/{name}/run") {
                    if (!checkApiKey(call)) return@post
                    handleLowerThirdTrigger(call, autoEnd = true)
                }

                post("/api/lowerthirds/{name}/show") {
                    if (!checkApiKey(call)) return@post
                    handleLowerThirdTrigger(call, autoEnd = false)
                }

                post("/api/lowerthirds/hide") {
                    if (!checkApiKey(call)) return@post
                    LowerThirdSequencer.stop()
                    call.respondText("""{"status":"stopped"}""", ContentType.Application.Json)
                }

                // ── ATEM Media Upload Endpoints ────────────────────────────────────

                // POST /api/atem/still/{name}?slot=N&me=E&key=M
                // Renders the named lower third as a single still frame and uploads it
                // to ATEM still slot N (1-based; defaults to atemSettings.defaultStillSlot).
                // If ?key=M (M > 0) is provided, turns upstream key M on M/E E on after upload.
                // Responds immediately; upload runs in background.
                post("/api/atem/still/{name}") {
                    if (!checkApiKey(call)) return@post
                    val name = call.parameters["name"] ?: run {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"name required"}""")
                        return@post
                    }
                    val file = lowerThirdFiles().firstOrNull { it.nameWithoutExtension.equals(name, ignoreCase = true) }
                    if (file == null) {
                        call.respond(io.ktor.http.HttpStatusCode.NotFound, """{"error":"lower third not found"}""")
                        return@post
                    }
                    val atem = _atemSettings
                    if (atem == null || atem.host.isBlank()) {
                        call.respond(io.ktor.http.HttpStatusCode.ServiceUnavailable, """{"error":"ATEM not configured"}""")
                        return@post
                    }
                    val slotParam = call.request.queryParameters["slot"]?.toIntOrNull()
                    val slot = if (slotParam != null) slotParam - 1 else atem.defaultStillSlot
                    // Optional key-on after upload: present key>0 ⇒ key it; absent/0 ⇒ upload only
                    val keyParam = call.request.queryParameters["key"]?.toIntOrNull()
                    val meParam = call.request.queryParameters["me"]?.toIntOrNull()
                    val keyOn = keyParam != null && keyParam > 0
                    val useDsk = resolveUseDsk(call, atem)
                    val mixEffect = if (useDsk) 0 else (if (meParam != null) meParam - 1 else atem.keyMixEffect)
                    val keyer = if (keyParam != null && keyParam > 0) keyParam - 1
                        else if (useDsk) atem.dskIndex else atem.keyIndex
                    if (keyOn) validateKeyTarget(atem, useDsk, mixEffect, keyer)?.let {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":${jsonStr(it)}}""")
                        return@post
                    }
                    scope.launch {
                        val uploadId = AtemUploadStatus.begin(file.nameWithoutExtension, clip = false, slot = slot + 1)
                        try {
                            val lottieJson = file.readText()
                            val (w, h) = AtemRenderCache.renderSize(atem)
                            val variant = AtemRenderCache.Variant(clip = false, width = w, height = h, frameCount = 1)
                            val cached = AtemRenderCache.prepare(lottieJson, variant).await()
                            AtemConnectionManager.use(atem.host, atem.port, needsState = true) { client ->
                                AtemRenderCache.Reader(cached).use { reader ->
                                    client.uploadStillEncoded(slot, reader.nextFrame(), file.nameWithoutExtension) { p ->
                                        AtemUploadStatus.progress(uploadId, p)
                                    }
                                }
                                if (keyOn) client.setKeyOnAir(useDsk, mixEffect, keyer, true)
                            }
                            AtemUploadStatus.complete(uploadId)
                            kotlinx.coroutines.delay(800)
                            AtemUploadStatus.clear(uploadId)
                        } catch (e: Exception) {
                            System.err.println("[CompanionServer] ATEM still upload failed for '$name': ${e.message}")
                            CrashReporter.reportException(e, "ATEM still upload: $name")
                            AtemUploadStatus.fail(uploadId, e.message)
                        }
                    }
                    val keyInfo = when {
                        !keyOn -> ""
                        useDsk -> ""","dsk":${keyer + 1}"""
                        else -> ""","me":${mixEffect + 1},"key":${keyer + 1}"""
                    }
                    call.respondText(
                        """{"status":"uploading","type":"still","name":${jsonStr(name)},"slot":${slot + 1}$keyInfo}""",
                        ContentType.Application.Json
                    )
                }

                // POST /api/atem/clip/{name}?slot=N&me=E&key=M
                // Renders the named lower third as a full animated clip and uploads it
                // to ATEM clip slot N (1-based; defaults to atemSettings.defaultClipSlot).
                // If ?key=M (M > 0) is provided, turns upstream key M on M/E E on after upload,
                // then off after the clip duration. Responds immediately; upload runs in background.
                post("/api/atem/clip/{name}") {
                    if (!checkApiKey(call)) return@post
                    val name = call.parameters["name"] ?: run {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"name required"}""")
                        return@post
                    }
                    val file = lowerThirdFiles().firstOrNull { it.nameWithoutExtension.equals(name, ignoreCase = true) }
                    if (file == null) {
                        call.respond(io.ktor.http.HttpStatusCode.NotFound, """{"error":"lower third not found"}""")
                        return@post
                    }
                    val atem = _atemSettings
                    if (atem == null || atem.host.isBlank()) {
                        call.respond(io.ktor.http.HttpStatusCode.ServiceUnavailable, """{"error":"ATEM not configured"}""")
                        return@post
                    }
                    val slotParam = call.request.queryParameters["slot"]?.toIntOrNull()
                    val slot = if (slotParam != null) slotParam - 1 else atem.defaultClipSlot
                    val keyParam = call.request.queryParameters["key"]?.toIntOrNull()
                    val meParam = call.request.queryParameters["me"]?.toIntOrNull()
                    val keyOn = keyParam != null && keyParam > 0
                    val useDsk = resolveUseDsk(call, atem)
                    val mixEffect = if (useDsk) 0 else (if (meParam != null) meParam - 1 else atem.keyMixEffect)
                    val keyer = if (keyParam != null && keyParam > 0) keyParam - 1
                        else if (useDsk) atem.dskIndex else atem.keyIndex
                    if (keyOn) validateKeyTarget(atem, useDsk, mixEffect, keyer)?.let {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":${jsonStr(it)}}""")
                        return@post
                    }
                    val lottieJson = file.readText()
                    val fps = atem.clipFps
                    val frameCount = AtemRenderCache.clipFrameCount(lottieJson, fps) ?: 1
                    // Capacity pre-flight (mirrors the Lower Third UI): block a clip that can't
                    // fit the slot before responding "uploading", so the caller gets a real error.
                    val clipCapacity = atem.detectedClipMaxFrames.getOrNull(slot)
                    if (clipCapacity != null && frameCount > clipCapacity) {
                        val secs = String.format(java.util.Locale.US, "%.1f", clipCapacity / fps)
                        call.respond(
                            io.ktor.http.HttpStatusCode.UnprocessableEntity,
                            """{"error":${jsonStr("Clip is $frameCount frames but slot ${slot + 1} holds at most $clipCapacity frames (≈$secs s); use a shorter clip or lower fps")}}"""
                        )
                        return@post
                    }
                    scope.launch {
                        val uploadId = AtemUploadStatus.begin(file.nameWithoutExtension, clip = true, slot = slot + 1)
                        try {
                            val (w, h) = AtemRenderCache.renderSize(atem)
                            val variant = AtemRenderCache.Variant(clip = true, width = w, height = h, fps = fps, frameCount = frameCount)
                            val cached = AtemRenderCache.prepare(lottieJson, variant).await()
                            AtemConnectionManager.use(atem.host, atem.port, needsState = true) { client ->
                                AtemRenderCache.Reader(cached).use { reader ->
                                    client.uploadClipEncoded(slot, reader.frameCount, file.nameWithoutExtension,
                                        nextFrame = { reader.nextFrame() }) { p -> AtemUploadStatus.progress(uploadId, p) }
                                }
                                // Wait for the ATEM to finish ingesting the clip before keying, so
                                // the key never fires over a half-processed clip. Best-effort: key
                                // anyway if the device never reports ready within the timeout.
                                AtemUploadStatus.startProcessing(uploadId)
                                client.awaitClipReady(slot, frameCount) { p -> AtemUploadStatus.progress(uploadId, p) }
                                if (keyOn) client.setKeyOnAir(useDsk, mixEffect, keyer, true)
                            }
                            AtemUploadStatus.complete(uploadId)
                            kotlinx.coroutines.delay(800)
                            AtemUploadStatus.clear(uploadId)
                            // Wait for the clip to finish playing, then turn the key off automatically.
                            // Mutex is released between the two use() calls so other operations can proceed.
                            if (keyOn) {
                                val clipDurationMs = (frameCount.toLong() * 1000L) / fps.toLong()
                                kotlinx.coroutines.delay(clipDurationMs)
                                AtemConnectionManager.use(atem.host, atem.port, needsState = false) { client ->
                                    client.setKeyOnAir(useDsk, mixEffect, keyer, false)
                                }
                            }
                        } catch (e: Exception) {
                            System.err.println("[CompanionServer] ATEM clip upload failed for '$name': ${e.message}")
                            CrashReporter.reportException(e, "ATEM clip upload: $name")
                            AtemUploadStatus.fail(uploadId, e.message)
                        }
                    }
                    val keyInfoClip = when {
                        !keyOn -> ""
                        useDsk -> ""","dsk":${keyer + 1}"""
                        else -> ""","me":${mixEffect + 1},"key":${keyer + 1}"""
                    }
                    call.respondText(
                        """{"status":"uploading","type":"clip","name":${jsonStr(name)},"slot":${slot + 1}$keyInfoClip}""",
                        ContentType.Application.Json
                    )
                }

                // POST /api/atem/key/on?me=E&key=M  — turn upstream key M on M/E E on air (standalone)
                post("/api/atem/key/on") {
                    if (!checkApiKey(call)) return@post
                    handleKeyToggle(call, onAir = true)
                }

                // POST /api/atem/key/off?me=E&key=M  — turn upstream key M on M/E E off air (standalone)
                post("/api/atem/key/off") {
                    if (!checkApiKey(call)) return@post
                    handleKeyToggle(call, onAir = false)
                }

                // ── Q&A Endpoints ─────────────────────────────────────────────────

                // Public: submission page
                get("/qa") {
                    call.respondText(qaSubmissionPageHtml(), ContentType.Text.Html)
                }

                // Public: admin page
                get("/qa/admin") {
                    call.respondText(qaAdminPageHtml(), ContentType.Text.Html)
                }

                // Public: session status
                get("/api/qa/status") {
                    val qa = qaManager
                    call.respondText(
                        """{"sessionActive":${qa?.sessionActive ?: false},"cooldownSeconds":$qaCooldownSeconds,"displayedQuestionId":"${qa?.displayedQuestion?.id ?: ""}","votingEnabled":$qaVotingEnabled}""",
                        ContentType.Application.Json
                    )
                }

                // Public: submit a question (no API key)
                post("/api/qa/submit") {
                    val qa = qaManager
                    if (qa == null || !qa.sessionActive) {
                        call.respond(io.ktor.http.HttpStatusCode.Forbidden, """{"error":"Q&A session is not active"}""")
                        return@post
                    }
                    val body = call.receiveText()
                    val request = try {
                        json.decodeFromString(SubmitQuestionRequest.serializer(), body)
                    } catch (_: Exception) {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"invalid request"}""")
                        return@post
                    }
                    if (request.text.isBlank()) {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"question text is required"}""")
                        return@post
                    }
                    val clientIp = call.request.headers["CF-Connecting-IP"]
                        ?: call.request.headers["X-Forwarded-For"]?.split(",")?.first()?.trim()
                        ?: call.request.local.remoteAddress
                    val deviceId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    val question = qa.submitQuestion(request.text, request.name, clientIp, qaCooldownSeconds, deviceId)
                    if (question != null) {
                        call.respondText(
                            json.encodeToString(org.churchpresenter.app.churchpresenter.models.QuestionDto.serializer(), question.toDto()),
                            ContentType.Application.Json
                        )
                    } else {
                        if (qa.isRateLimited(clientIp, qaCooldownSeconds)) {
                            call.respond(io.ktor.http.HttpStatusCode.TooManyRequests, """{"error":"Too many questions. Please wait a moment."}""")
                        } else {
                            call.respond(io.ktor.http.HttpStatusCode.Forbidden, """{"error":"submission failed"}""")
                        }
                    }
                }

                // Public: voting page
                get("/qa/vote") {
                    call.respondText(qaVotingPageHtml(), ContentType.Text.Html)
                }

                // Public: list approved questions (for voting)
                get("/api/qa/approved") {
                    if (!qaVotingEnabled) {
                        call.respond(io.ktor.http.HttpStatusCode.Forbidden, """{"error":"Voting is not enabled"}""")
                        return@get
                    }
                    val qa = qaManager
                    if (qa == null || !qa.sessionActive) {
                        call.respondText("[]", ContentType.Application.Json)
                        return@get
                    }
                    val approved = qa.getApprovedQuestions()
                    val clientIp = call.request.headers["CF-Connecting-IP"]
                        ?: call.request.headers["X-Forwarded-For"]?.split(",")?.first()?.trim()
                        ?: call.request.local.remoteAddress
                    val dtos = approved.map {
                        val dto = it.toDto()
                        val textEsc = dto.text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
                        val nameEsc = dto.submitterName.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
                        val voteDir = qa.getVoteDirection(it.id, clientIp)
                        val votedStr = if (voteDir != null) "\"$voteDir\"" else "null"
                        """{"id":"${dto.id}","text":"$textEsc","voteCount":${dto.voteCount},"voted":$votedStr}"""
                    }
                    call.respondText("[${dtos.joinToString(",")}]", ContentType.Application.Json)
                }

                // Public: vote for a question
                post("/api/qa/vote") {
                    if (!qaVotingEnabled) {
                        call.respond(io.ktor.http.HttpStatusCode.Forbidden, """{"error":"Voting is not enabled"}""")
                        return@post
                    }
                    val qa = qaManager
                    if (qa == null || !qa.sessionActive) {
                        call.respond(io.ktor.http.HttpStatusCode.Forbidden, """{"error":"Q&A session is not active"}""")
                        return@post
                    }
                    val body = call.receiveText()
                    val request = try {
                        json.decodeFromString(VoteRequest.serializer(), body)
                    } catch (_: Exception) {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"invalid request"}""")
                        return@post
                    }
                    val question = qa.findQuestion(request.questionId)
                    if (question == null) {
                        call.respond(io.ktor.http.HttpStatusCode.NotFound, """{"error":"question not found"}""")
                        return@post
                    }
                    if (question.status != QuestionStatus.APPROVED) {
                        call.respond(io.ktor.http.HttpStatusCode.Forbidden, """{"error":"question is not available for voting"}""")
                        return@post
                    }
                    val clientIp = call.request.headers["CF-Connecting-IP"]
                        ?: call.request.headers["X-Forwarded-For"]?.split(",")?.first()?.trim()
                        ?: call.request.local.remoteAddress
                    val direction = if (request.direction == "down") "down" else "up"
                    qa.voteForQuestion(request.questionId, clientIp, direction)
                    val currentDir = qa.getVoteDirection(request.questionId, clientIp)
                    val voted = if (currentDir != null) "\"$currentDir\"" else "null"
                    call.respondText("""{"ok":true,"voted":$voted}""", ContentType.Application.Json)
                }

                // Admin: check password
                post("/api/qa/auth") {
                    if (!checkQaAdmin(call)) return@post
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }

                // Admin: list questions
                get("/api/qa/questions") {
                    if (!checkQaAdmin(call)) return@get
                    val qa = qaManager ?: run {
                        call.respondText("[]", ContentType.Application.Json)
                        return@get
                    }
                    val statusFilter = call.request.queryParameters["status"]
                    val filtered = if (statusFilter != null) {
                        val s = try { QuestionStatus.valueOf(statusFilter.uppercase()) } catch (_: Exception) { null }
                        if (s != null) qa.questions.filter { it.status == s } else qa.questions
                    } else qa.questions
                    val dtos = filtered.map { it.toDto() }
                    call.respondText(
                        json.encodeToString(kotlinx.serialization.builtins.ListSerializer(org.churchpresenter.app.churchpresenter.models.QuestionDto.serializer()), dtos),
                        ContentType.Application.Json
                    )
                }

                // Admin: approve question
                post("/api/qa/questions/{id}/approve") {
                    if (!checkQaAdmin(call)) return@post
                    val id = call.parameters["id"] ?: run {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"missing id"}""")
                        return@post
                    }
                    val question = qaManager?.findQuestion(id)
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    val pending = PendingQAAdminRequest(action = "approve", questionId = id, text = question?.text ?: "", clientId = clientId)
                    onQAAdminRequest.emit(pending)
                    if (!pending.decision.await()) { call.respond(io.ktor.http.HttpStatusCode.Forbidden, """{"error":"denied by operator"}"""); return@post }
                    val ok = qaManager?.approveQuestion(id) ?: false
                    if (ok) call.respondText("""{"ok":true}""", ContentType.Application.Json)
                    else call.respond(io.ktor.http.HttpStatusCode.NotFound, """{"error":"question not found"}""")
                }

                // Admin: edit question text
                post("/api/qa/questions/{id}/edit") {
                    if (!checkQaAdmin(call)) return@post
                    val id = call.parameters["id"] ?: run {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"missing id"}""")
                        return@post
                    }
                    val body = call.receiveText()
                    val request = try {
                        json.decodeFromString(SubmitQuestionRequest.serializer(), body)
                    } catch (_: Exception) {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"invalid request"}""")
                        return@post
                    }
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    val pending = PendingQAAdminRequest(
                        action = "edit",
                        questionId = id,
                        text = request.text,
                        clientId = clientId
                    )
                    onQAAdminRequest.emit(pending)
                    val allowed = pending.decision.await()
                    if (!allowed) {
                        call.respond(io.ktor.http.HttpStatusCode.Forbidden, """{"error":"denied by operator"}""")
                        return@post
                    }
                    val ok = qaManager?.editQuestion(id, request.text) ?: false
                    if (ok) call.respondText("""{"ok":true}""", ContentType.Application.Json)
                    else call.respond(io.ktor.http.HttpStatusCode.NotFound, """{"error":"question not found"}""")
                }

                // Admin: deny question
                post("/api/qa/questions/{id}/deny") {
                    if (!checkQaAdmin(call)) return@post
                    val id = call.parameters["id"] ?: run {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"missing id"}""")
                        return@post
                    }
                    val question = qaManager?.findQuestion(id)
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    val pending = PendingQAAdminRequest(action = "deny", questionId = id, text = question?.text ?: "", clientId = clientId)
                    onQAAdminRequest.emit(pending)
                    if (!pending.decision.await()) { call.respond(io.ktor.http.HttpStatusCode.Forbidden, """{"error":"denied by operator"}"""); return@post }
                    val ok = qaManager?.denyQuestion(id) ?: false
                    if (ok) {
                        if (qaManager?.displayedQuestion == null) scope.launch { onQADisplay.emit(null) }
                        call.respondText("""{"ok":true}""", ContentType.Application.Json)
                    }
                    else call.respond(io.ktor.http.HttpStatusCode.NotFound, """{"error":"question not found"}""")
                }

                // Admin: mark question as done
                post("/api/qa/questions/{id}/done") {
                    if (!checkQaAdmin(call)) return@post
                    val id = call.parameters["id"] ?: run {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"missing id"}""")
                        return@post
                    }
                    val question = qaManager?.findQuestion(id)
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    val pending = PendingQAAdminRequest(action = "done", questionId = id, text = question?.text ?: "", clientId = clientId)
                    onQAAdminRequest.emit(pending)
                    if (!pending.decision.await()) { call.respond(io.ktor.http.HttpStatusCode.Forbidden, """{"error":"denied by operator"}"""); return@post }
                    val ok = qaManager?.markDone(id) ?: false
                    if (ok) {
                        if (qaManager?.displayedQuestion == null) scope.launch { onQADisplay.emit(null) }
                        call.respondText("""{"ok":true}""", ContentType.Application.Json)
                    }
                    else call.respond(io.ktor.http.HttpStatusCode.NotFound, """{"error":"question not found"}""")
                }

                // Admin: display question on projection
                post("/api/qa/questions/{id}/display") {
                    if (!checkQaAdmin(call)) return@post
                    val id = call.parameters["id"] ?: run {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"missing id"}""")
                        return@post
                    }
                    val question = qaManager?.findQuestion(id)
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    val pending = PendingQAAdminRequest(action = "display", questionId = id, text = question?.text ?: "", clientId = clientId)
                    onQAAdminRequest.emit(pending)
                    if (!pending.decision.await()) { call.respond(io.ktor.http.HttpStatusCode.Forbidden, """{"error":"denied by operator"}"""); return@post }
                    val qa = qaManager
                    val ok = qa?.displayQuestion(id) ?: false
                    if (ok) {
                        scope.launch { onQADisplay.emit(qa.displayedQuestion) }
                        call.respondText("""{"ok":true}""", ContentType.Application.Json)
                    }
                    else call.respond(io.ktor.http.HttpStatusCode.NotFound, """{"error":"question not found or not approved"}""")
                }

                // Admin: delete question
                delete("/api/qa/questions/{id}") {
                    if (!checkQaAdmin(call)) return@delete
                    val id = call.parameters["id"] ?: run {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"missing id"}""")
                        return@delete
                    }
                    val question = qaManager?.findQuestion(id)
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    val pending = PendingQAAdminRequest(
                        action = "delete",
                        questionId = id,
                        text = question?.text ?: "",
                        clientId = clientId
                    )
                    onQAAdminRequest.emit(pending)
                    val allowed = pending.decision.await()
                    if (!allowed) {
                        call.respond(io.ktor.http.HttpStatusCode.Forbidden, """{"error":"denied by operator"}""")
                        return@delete
                    }
                    val ok = qaManager?.deleteQuestion(id) ?: false
                    if (ok) call.respondText("""{"ok":true}""", ContentType.Application.Json)
                    else call.respond(io.ktor.http.HttpStatusCode.NotFound, """{"error":"question not found"}""")
                }

                // Admin: add question (admin-created)
                post("/api/qa/add") {
                    if (!checkQaAdmin(call)) return@post
                    val body = call.receiveText()
                    val request = try {
                        json.decodeFromString(SubmitQuestionRequest.serializer(), body)
                    } catch (_: Exception) {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"invalid request"}""")
                        return@post
                    }
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    val pending = PendingQAAdminRequest(
                        action = "add",
                        text = request.text,
                        clientId = clientId
                    )
                    onQAAdminRequest.emit(pending)
                    val allowed = pending.decision.await()
                    if (!allowed) {
                        call.respond(io.ktor.http.HttpStatusCode.Forbidden, """{"error":"denied by operator"}""")
                        return@post
                    }
                    val question = qaManager?.addQuestion(request.text)
                    if (question != null) {
                        call.respondText(
                            json.encodeToString(org.churchpresenter.app.churchpresenter.models.QuestionDto.serializer(), question.toDto()),
                            ContentType.Application.Json
                        )
                    } else {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"failed to add question"}""")
                    }
                }

                // Admin: clear display
                post("/api/qa/clear-display") {
                    if (!checkQaAdmin(call)) return@post
                    val clientId = call.request.headers["X-Device-Id"] ?: ""
                    val pending = PendingQAAdminRequest(action = "clear-display", clientId = clientId)
                    onQAAdminRequest.emit(pending)
                    if (!pending.decision.await()) {
                        call.respond(io.ktor.http.HttpStatusCode.Forbidden, """{"error":"denied"}""")
                        return@post
                    }
                    qaManager?.clearDisplay()
                    scope.launch { onQADisplay.emit(null) }
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }

                // Admin: clear all questions
                post("/api/qa/clear-all") {
                    if (!checkQaAdmin(call)) return@post
                    qaManager?.clearAll()
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }
            }
    }

    fun stop() {
        tunnelManager.stop()
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

    private suspend fun checkPresentationRemoteAuth(call: io.ktor.server.application.ApplicationCall): Boolean {
        if (!presentationRemoteEnabled) {
            call.respond(io.ktor.http.HttpStatusCode.Forbidden, """{"error":"remote control is disabled"}""")
            return false
        }
        val pw = presentationRemotePassword
        val provided = call.request.headers[Constants.HEADER_PRESENTATION_PASSWORD]
            ?: call.request.queryParameters["password"]
            ?: ""
        return if (pw.isEmpty() || MessageDigest.isEqual(provided.toByteArray(), pw.toByteArray())) {
            true
        } else {
            call.respond(io.ktor.http.HttpStatusCode.Unauthorized, """{"error":"Invalid password"}""")
            false
        }
    }

    private suspend fun handlePresentationFileUpload(call: io.ktor.server.application.ApplicationCall) {
        try {
            val contentLength = call.request.headers["Content-Length"]?.toLongOrNull() ?: 0L
            if (contentLength > 200 * 1024 * 1024) {
                call.respond(io.ktor.http.HttpStatusCode.PayloadTooLarge, """{"error":"file too large (max 200 MB)"}""")
                return
            }
            val body   = call.receiveText()
            val parsed = json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject
            val name   = (parsed?.get("name") as? kotlinx.serialization.json.JsonPrimitive)?.content
            val data   = (parsed?.get("data") as? kotlinx.serialization.json.JsonPrimitive)?.content
            if (name.isNullOrBlank() || data.isNullOrBlank()) {
                call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"name and data are required"}""")
                return
            }
            val safeName = File(name).name.ifBlank { "upload.pdf" }
            val ext = safeName.substringAfterLast('.', "").lowercase()
            if (ext !in setOf("pdf", "ppt", "pptx", "key")) {
                call.respond(io.ktor.http.HttpStatusCode.UnsupportedMediaType, """{"error":"unsupported file type: $ext"}""")
                return
            }
            val base64Match = Regex("^data:[^;]+;base64,(.+)$").find(data)
            if (base64Match == null) {
                call.respond(io.ktor.http.HttpStatusCode.BadRequest, """{"error":"data must be a base64 data URI"}""")
                return
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

    private suspend fun checkQaAdmin(call: io.ktor.server.application.ApplicationCall): Boolean {
        val pw = qaAdminPassword
        if (pw.isEmpty()) return true
        val provided = call.request.headers["X-QA-Password"]
            ?: call.request.queryParameters["password"]
            ?: ""
        return if (MessageDigest.isEqual(provided.toByteArray(), pw.toByteArray())) {
            true
        } else {
            call.respond(io.ktor.http.HttpStatusCode.Unauthorized, """{"error":"Invalid admin password"}""")
            false
        }
    }


    private fun broadcast(msg: WebSocketMessage) {
        scope.launch {
            broadcastChannel.emit(json.encodeToString(WebSocketMessage.serializer(), msg))
        }
    }

    /** Broadcasts a display_cleared event to all connected mobile clients. */
    fun broadcastDisplayCleared() {
        broadcast(WebSocketMessage(type = Constants.WS_EVENT_DISPLAY_CLEARED, payload = ""))
    }

    /** Broadcasts the currently active song section index to all connected mobile clients. */
    fun broadcastSongSectionSelected(sectionIndex: Int) {
        broadcast(WebSocketMessage(type = Constants.WS_EVENT_SONG_SECTION_SELECTED, payload = sectionIndex.toString()))
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

    // ── Q&A HTML Pages ────────────────────────────────────────────────────────

    /** Shared CSS injected into every Q&A page: semantic color tokens + screen-reader-only utility. */
    private val qaSharedCss = """
:root{
--qa-primary:#1e88e5;--qa-primary-hover:#1565c0;
--qa-success:#43a047;--qa-success-hover:#2e7d32;
--qa-danger:#e53935;--qa-danger-hover:#c62828;
}
.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}
""".trimIndent()

    private fun qaSubmissionPageHtml(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Ask a Question</title>
<style>
${qaSharedCss}
:root{--qa-bg:#f5f5f5;--qa-surface:#fff;--qa-ink:#1e1e2e;--qa-sub:#5f5f6b;--qa-border:#e0e0e0;--qa-muted:#616161}
@media(prefers-color-scheme:dark){:root{--qa-bg:#16161f;--qa-surface:#22222e;--qa-ink:#e8e8ef;--qa-sub:#a8a8b5;--qa-border:#3a3a4a;--qa-muted:#9a9aa6}}
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:var(--qa-bg);color:var(--qa-ink);min-height:100vh;display:flex;align-items:center;justify-content:center;padding:16px}
.card{background:var(--qa-surface);border-radius:16px;box-shadow:0 2px 12px rgba(0,0,0,.1);padding:32px;max-width:480px;width:100%}
h1{font-size:24px;margin-bottom:8px;color:var(--qa-ink)}
p.sub{color:var(--qa-sub);margin-bottom:24px;font-size:14px}
label.field-label{display:block;font-size:13px;font-weight:600;color:var(--qa-ink);margin-bottom:6px}
input.name-input{width:100%;border:2px solid var(--qa-border);border-radius:12px;padding:12px 16px;font-size:15px;font-family:inherit;margin-bottom:16px;background:var(--qa-surface);color:var(--qa-ink);transition:border-color .2s}
input.name-input:focus{outline:none;border-color:var(--qa-primary)}
textarea{width:100%;min-height:120px;border:2px solid var(--qa-border);border-radius:12px;padding:16px;font-size:16px;resize:vertical;font-family:inherit;background:var(--qa-surface);color:var(--qa-ink);transition:border-color .2s}
textarea:focus{outline:none;border-color:var(--qa-primary)}
input.name-input::placeholder,textarea::placeholder{color:var(--qa-muted)}
button{width:100%;min-height:48px;padding:14px;background:var(--qa-primary);color:#fff;border:none;border-radius:12px;font-size:16px;font-weight:600;cursor:pointer;margin-top:16px;transition:background .2s}
button:hover{background:var(--qa-primary-hover)}
button:disabled{background:#9aa0a6;cursor:not-allowed}
.msg{text-align:center;padding:12px;border-radius:8px;margin-top:16px;font-size:14px}
.msg.ok{background:#e8f5e9;color:#1b5e20}
.msg.err{background:#ffebee;color:#b71c1c}
.msg.off{background:#fff3e0;color:#e65100}
#charcount{text-align:right;font-size:12px;color:var(--qa-muted);margin-top:4px}
@media(prefers-color-scheme:dark){.msg.ok{background:#1b3a24;color:#a5d6a7}.msg.err{background:#3a1c1f;color:#ef9a9a}.msg.off{background:#3a2a12;color:#ffcc80}.card{box-shadow:0 2px 12px rgba(0,0,0,.4)}}
</style>
</head>
<body>
<main class="card">
<h1 id="page-title">Ask a Question</h1>
<p class="sub" id="page-sub">Your question will be reviewed before being displayed.</p>
<div id="form-area">
<label class="sr-only" for="name">Your name (optional)</label>
<input type="text" id="name" class="name-input" placeholder="Your name">
<label class="sr-only" for="q">Your question</label>
<textarea id="q" maxlength="500" placeholder="Type your question here..."></textarea>
<div id="charcount" aria-live="polite">0 / 500</div>
<button id="btn" onclick="submit()">Submit Question</button>
</div>
<div id="msg" class="msg" role="status" aria-live="polite" style="display:none"></div>
<a id="vote-link" href="/qa/vote" style="display:none;text-align:center;margin-top:12px;text-decoration:none;width:100%;min-height:48px;padding:14px;background:var(--qa-success);color:#fff;border-radius:12px;font-size:16px;font-weight:600;cursor:pointer;transition:background .2s;box-sizing:border-box">Vote on Questions</a>
</main>
<script>
const q=document.getElementById('q'),btn=document.getElementById('btn'),msg=document.getElementById('msg'),cc=document.getElementById('charcount'),nameField=document.getElementById('name');
let submitted=false,cooldown=30,votingOn=false;
const submitTime=parseInt(sessionStorage.getItem('qa_submit_time')||'0');
if(submitTime>0){
  const elapsed=Math.floor((Date.now()-submitTime)/1000);
  const savedCooldown=parseInt(sessionStorage.getItem('qa_cooldown')||'30');
  if(elapsed<savedCooldown){submitted=true;showThanks()}
  else{sessionStorage.removeItem('qa_submit_time');sessionStorage.removeItem('qa_cooldown')}
}
q.addEventListener('input',()=>{cc.textContent=q.value.length+' / 500'});
async function submit(){
  const text=q.value.trim();
  if(!text){show('Please enter a question','err');return}
  btn.disabled=true;btn.textContent='Submitting...';
  try{
    const name=nameField.value.trim();
    const r=await fetch('/api/qa/submit',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({text,name})});
    if(r.ok){showThanks();sessionStorage.setItem('qa_submit_time',Date.now().toString());sessionStorage.setItem('qa_cooldown',cooldown.toString())}
    else if(r.status===429){showThanks()}
    else if(r.status===403){show('Q&A session is not active right now.','off')}
    else{const d=await r.json().catch(()=>({}));show(d.error||'Submission failed','err');btn.disabled=false;btn.textContent='Submit Question'}
  }catch(e){show('Network error. Please try again.','err');btn.disabled=false;btn.textContent='Submit Question'}
}
function showThanks(){
  document.getElementById('form-area').style.display='none';
  document.getElementById('page-title').style.display='none';
  document.getElementById('page-sub').style.display='none';
  msg.innerHTML='<strong>Thanks for submitting your question!</strong>';
  msg.className='msg ok';msg.style.display='block';
  submitted=true;
}
function show(t,c){msg.textContent=t;msg.className='msg '+c;msg.style.display='block'}
// Check session status periodically
async function checkStatus(){
  try{const r=await fetch('/api/qa/status');const d=await r.json();
    if(d.cooldownSeconds)cooldown=d.cooldownSeconds;
    votingOn=!!d.votingEnabled;
    const vl=document.getElementById('vote-link');if(vl)vl.style.display=votingOn?'block':'none';
    if(submitted){
      const st=parseInt(sessionStorage.getItem('qa_submit_time')||'0');
      if(st>0&&Math.floor((Date.now()-st)/1000)>=cooldown){
        submitted=false;sessionStorage.removeItem('qa_submit_time');sessionStorage.removeItem('qa_cooldown');
        document.getElementById('form-area').style.display='block';msg.style.display='none';
        document.getElementById('page-title').style.display='';document.getElementById('page-sub').style.display='';
        btn.disabled=false;btn.textContent='Submit Question';q.value='';
      }
      return;
    }
    if(!d.sessionActive){document.getElementById('form-area').style.display='none';document.getElementById('page-title').style.display='none';document.getElementById('page-sub').style.display='none';show('Q&A session is not active right now.','off')}
    else{document.getElementById('form-area').style.display='block';document.getElementById('page-title').style.display='';document.getElementById('page-sub').style.display='';if(msg.className.includes('off'))msg.style.display='none'}
  }catch(e){}
}
checkStatus();setInterval(checkStatus,5000);
</script>
</body>
</html>
""".trimIndent()

    private fun qaVotingPageHtml(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Vote on Questions</title>
<style>
${qaSharedCss}
:root{--qa-bg:#f5f5f5;--qa-surface:#fff;--qa-ink:#1e1e2e;--qa-sub:#5f5f6b;--qa-muted:#616161;--qa-up-bg:#e3f2fd;--qa-down-bg:#ffebee;--qa-score-pos:#2e7d32;--qa-score-neg:#c62828}
@media(prefers-color-scheme:dark){:root{--qa-bg:#16161f;--qa-surface:#22222e;--qa-ink:#e8e8ef;--qa-sub:#a8a8b5;--qa-muted:#9a9aa6;--qa-up-bg:#12314a;--qa-down-bg:#3a1c1f;--qa-score-pos:#81c784;--qa-score-neg:#ef9a9a}}
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:var(--qa-bg);color:var(--qa-ink);min-height:100vh;padding:16px}
.container{max-width:600px;margin:0 auto}
h1{font-size:24px;color:var(--qa-ink);text-align:center;margin-bottom:4px}
p.sub{color:var(--qa-sub);text-align:center;margin-bottom:24px;font-size:14px}
.question-card{background:var(--qa-surface);border-radius:12px;box-shadow:0 2px 8px rgba(0,0,0,.08);padding:20px;margin-bottom:12px;display:flex;align-items:flex-start;gap:16px}
.vote-btns{display:flex;flex-direction:column;align-items:center;gap:2px;min-width:44px}
.vote-btn{display:flex;align-items:center;justify-content:center;border:none;background:none;cursor:pointer;padding:6px;border-radius:8px;transition:background .2s,color .2s;width:44px;height:44px}
.vote-btn:hover{background:var(--qa-up-bg)}
.vote-btn.voted{color:var(--qa-primary);background:var(--qa-up-bg)}
.vote-btn:disabled{cursor:default}
.vote-arrow{font-size:18px;line-height:1;color:var(--qa-muted);transition:color .2s}
.vote-btn.voted .vote-arrow{color:var(--qa-primary)}
.vote-btn.down-voted{color:var(--qa-danger);background:var(--qa-down-bg)}
.vote-btn.down-voted .vote-arrow{color:var(--qa-danger)}
.vote-score{font-size:13px;font-weight:700;text-align:center;line-height:1;min-width:20px}
.q-content{flex:1;min-width:0}
.q-text{font-size:16px;color:var(--qa-ink);line-height:1.4;word-wrap:break-word}
.q-meta{font-size:12px;color:var(--qa-muted);margin-top:6px}
.msg{text-align:center;padding:16px;border-radius:8px;font-size:14px;margin-top:16px}
.msg.off{background:#fff3e0;color:#e65100}
.empty{text-align:center;color:var(--qa-muted);font-size:14px;margin-top:40px}
a.back{display:block;text-align:center;margin-top:20px;text-decoration:none;padding:14px;min-height:48px;background:var(--qa-primary);color:#fff;border-radius:12px;font-size:16px;font-weight:600;transition:background .2s}
a.back:hover{background:var(--qa-primary-hover)}
@media(prefers-color-scheme:dark){.msg.off{background:#3a2a12;color:#ffcc80}}
</style>
</head>
<body>
<main class="container">
<h1 id="page-title">Vote on Questions</h1>
<p class="sub" id="page-sub">Vote on the questions you'd like answered</p>
<div id="questions"></div>
<div id="msg" class="msg" role="status" aria-live="polite" style="display:none"></div>
<a class="back" href="/qa">&larr; Submit a question</a>
</main>
<script>
const questionsEl=document.getElementById('questions'),msgEl=document.getElementById('msg');
const voted=JSON.parse(sessionStorage.getItem('qa_voted')||'{}'); // {id: "up"|"down"}
let lastDataHash='';

async function loadQuestions(){
  try{
    const r=await fetch('/api/qa/approved');
    if(r.status===403){
      document.getElementById('page-title').style.display='none';
      document.getElementById('page-sub').style.display='none';
      questionsEl.innerHTML='';
      msgEl.textContent='Voting is not enabled right now.';
      msgEl.className='msg off';msgEl.style.display='block';
      lastDataHash='';
      return;
    }
    const data=await r.json();
    if(!Array.isArray(data)||data.length===0){
      const sr=await fetch('/api/qa/status');
      const sd=await sr.json();
      if(!sd.sessionActive){
        document.getElementById('page-title').style.display='none';
        document.getElementById('page-sub').style.display='none';
        questionsEl.innerHTML='';
        msgEl.textContent='Q&A session is not active right now.';
        msgEl.className='msg off';msgEl.style.display='block';
      } else {
        document.getElementById('page-title').style.display='';
        document.getElementById('page-sub').style.display='';
        msgEl.style.display='none';
        questionsEl.innerHTML='<div class="empty">No questions yet. Check back soon!</div>';
      }
      lastDataHash='';
      return;
    }
    // Only rebuild DOM if data changed (prevents wrong-question clicks during refresh)
    const newHash=data.map(q=>q.id).join(',');
    if(newHash===lastDataHash){
      // Just update vote states without rebuilding
      data.forEach(q=>{
        const dir=q.voted||voted[q.id]||null;
        updateBtns(q.id,dir);
      });
      return;
    }
    lastDataHash=newHash;
    document.getElementById('page-title').style.display='';
    document.getElementById('page-sub').style.display='';
    msgEl.style.display='none';
    questionsEl.innerHTML=data.map(q=>{
      const dir=q.voted||voted[q.id]||null;
      const score=(q.upvotes||0)-(q.downvotes||0);
      const scoreColor=score>0?'var(--qa-score-pos)':score<0?'var(--qa-score-neg)':'var(--qa-muted)';
      return '<div class="question-card" id="qc-'+q.id+'">'
        +'<div class="vote-btns">'
        +'<button class="vote-btn'+(dir==='up'?' voted':'')+'" id="up-'+q.id+'" aria-label="Upvote" aria-pressed="'+(dir==='up')+'" onclick="vote(\''+q.id+'\',\'up\')"><span class="vote-arrow" aria-hidden="true">&#9650;</span></button>'
        +'<span class="vote-score" id="vs-'+q.id+'" data-score="'+score+'" style="color:'+scoreColor+'">'+score+'</span>'
        +'<button class="vote-btn'+(dir==='down'?' down-voted':'')+'" id="dn-'+q.id+'" aria-label="Downvote" aria-pressed="'+(dir==='down')+'" onclick="vote(\''+q.id+'\',\'down\')"><span class="vote-arrow" aria-hidden="true">&#9660;</span></button>'
        +'</div>'
        +'<div class="q-content">'
        +'<div class="q-text">'+escHtml(q.text)+'</div>'
        +'</div></div>';
    }).join('');
  }catch(e){console.error(e)}
}

async function vote(id,dir){
  try{
    const prevDir=voted[id]||null;
    const r=await fetch('/api/qa/vote',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({questionId:id,direction:dir})});
    if(r.ok){
      const d=await r.json();
      const newDir=d.voted||null;
      if(newDir){voted[id]=newDir}else{delete voted[id]}
      sessionStorage.setItem('qa_voted',JSON.stringify(voted));
      updateBtns(id,newDir,prevDir);
    }
  }catch(e){}
}
function updateBtns(id,dir,prevDir){
  const up=document.getElementById('up-'+id);
  const dn=document.getElementById('dn-'+id);
  const vs=document.getElementById('vs-'+id);
  if(up){up.className='vote-btn'+(dir==='up'?' voted':'');up.setAttribute('aria-pressed',dir==='up')}
  if(dn){dn.className='vote-btn'+(dir==='down'?' down-voted':'');dn.setAttribute('aria-pressed',dir==='down')}
  if(vs){
    let s=parseInt(vs.dataset.score)||0;
    if(prevDir==='up')s--;else if(prevDir==='down')s++;
    if(dir==='up')s++;else if(dir==='down')s--;
    vs.dataset.score=s;vs.textContent=s;
    vs.style.color=s>0?'var(--qa-score-pos)':s<0?'var(--qa-score-neg)':'var(--qa-muted)';
  }
}

function escHtml(s){const d=document.createElement('div');d.textContent=s;return d.innerHTML}

loadQuestions();
setInterval(loadQuestions,5000);
</script>
</body>
</html>
""".trimIndent()

    private fun qaAdminPageHtml(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Q&A Admin</title>
<style>
${qaSharedCss}
:root{--qa-bg:#1e1e2e;--qa-surface:#2a2a3e;--qa-ink:#e0e0e0;--qa-muted:#a0a0ad;--qa-border:#3b3b5c}
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:var(--qa-bg);color:var(--qa-ink);min-height:100vh;padding:16px}
.header{display:flex;align-items:center;justify-content:space-between;padding:16px;margin-bottom:16px}
h1{font-size:22px}
.tabs{display:flex;gap:8px;margin-bottom:16px;padding:0 16px}
.tab{min-height:44px;padding:8px 20px;border-radius:8px;border:1px solid var(--qa-border);background:transparent;color:var(--qa-ink);cursor:pointer;font-size:14px;transition:background .2s,border-color .2s}
.tab.active{background:var(--qa-primary);border-color:var(--qa-primary);color:#fff}
.tab .count{background:rgba(255,255,255,.2);border-radius:10px;padding:1px 8px;margin-left:6px;font-size:12px}
.list{padding:0 16px}
.q{background:var(--qa-surface);border-radius:12px;padding:16px;margin-bottom:8px;display:flex;align-items:flex-start;gap:12px;flex-wrap:wrap}
.q.live{border:2px solid var(--qa-success)}
.q-text{flex:1;font-size:15px;line-height:1.4;min-width:150px}
.q-time{color:var(--qa-muted);font-size:12px;white-space:nowrap;padding-top:3px}
.q-label{font-size:11px;padding:2px 8px;border-radius:6px;font-weight:600}
.q-label.done{background:#42a5f5;color:#08233b}
.q-label.denied{background:var(--qa-danger);color:#fff}
.q-actions{display:flex;gap:6px;flex-wrap:wrap}
.btn{min-height:44px;padding:8px 14px;border:none;border-radius:8px;cursor:pointer;font-size:13px;font-weight:600;transition:background .2s}
.btn-approve{background:var(--qa-success);color:#fff}.btn-approve:hover{background:var(--qa-success-hover)}
.btn-deny{background:var(--qa-danger);color:#fff}.btn-deny:hover{background:var(--qa-danger-hover)}
.btn-live{background:var(--qa-primary);color:#fff}.btn-live:hover{background:var(--qa-primary-hover)}
.btn-done{background:#42a5f5;color:#08233b}.btn-done:hover{background:#1e88e5;color:#fff}
.btn-back{background:#ff9800;color:#3d2600}.btn-back:hover{background:#f57c00;color:#fff}
.btn-golive-confirm{background:var(--qa-primary);color:#fff;opacity:0.6}.btn-golive-confirm:hover{opacity:1}
.btn-edit{background:#ff9800;color:#3d2600}.btn-edit:hover{background:#f57c00;color:#fff}
.btn-del{background:#4a4a5e;color:#e0e0e0}.btn-del:hover{background:#5e5e77}
.edit-area{width:100%;display:flex;gap:6px;align-items:center;margin-top:8px}
.edit-area textarea{flex:1;background:var(--qa-bg);color:var(--qa-ink);border:2px solid var(--qa-primary);border-radius:8px;padding:8px;font-size:14px;font-family:inherit;resize:vertical;min-height:40px}
.edit-area .btn{white-space:nowrap}
.empty{text-align:center;padding:48px;color:var(--qa-muted);font-size:16px}
.status-bar{padding:8px 16px;background:var(--qa-success);color:#fff;border-radius:8px;margin:0 16px 16px;display:flex;align-items:center;justify-content:space-between}
.status-bar .btn{background:rgba(255,255,255,.2);color:#fff}
.add-bar{display:flex;gap:8px;padding:0 16px;margin-bottom:16px}
.add-bar input{flex:1;min-height:44px;background:var(--qa-surface);color:var(--qa-ink);border:1px solid var(--qa-border);border-radius:8px;padding:10px 14px;font-size:14px;font-family:inherit}
.add-bar input:focus{outline:none;border-color:var(--qa-primary)}
.login{max-width:360px;margin:80px auto;text-align:center}
.login input{width:100%;min-height:48px;background:var(--qa-surface);color:var(--qa-ink);border:2px solid var(--qa-border);border-radius:12px;padding:14px;font-size:16px;margin:16px 0;text-align:center}
.login input:focus{outline:none;border-color:var(--qa-primary)}
.login .btn{width:100%;padding:14px;font-size:16px}
.login .err{color:#ef9a9a;margin-top:8px;font-size:14px}
</style>
</head>
<body>
<div id="login-screen" class="login" style="display:none">
<h1>Q&A Admin</h1>
<p style="color:var(--qa-muted);margin-top:8px">Enter admin password to continue</p>
<label class="sr-only" for="pw-input">Admin password</label>
<input type="password" id="pw-input" aria-label="Admin password" placeholder="Password" onkeydown="if(event.key==='Enter')doLogin()">
<button class="btn btn-live" onclick="doLogin()">Login</button>
<div class="err" id="pw-err" style="display:none"></div>
</div>

<div id="main-app" style="display:none">
<div class="header">
<h1>Q&A Admin</h1>
<span id="status" role="status" aria-live="polite" style="font-size:13px;color:var(--qa-muted)">Connecting...</span>
</div>

<div id="display-bar" class="status-bar" style="display:none">
<span id="display-text">Displaying question...</span>
<button class="btn" onclick="clearDisplay()">Clear Display</button>
</div>

<div class="add-bar">
<label class="sr-only" for="add-input">Add a question</label>
<input type="text" id="add-input" aria-label="Add a question" placeholder="Add a question..." spellcheck="true" onkeydown="if(event.key==='Enter')addQ()">
<button class="btn btn-approve" onclick="addQ()">Add</button>
</div>

<div class="tabs" style="flex-wrap:wrap;gap:4px">
<button class="tab active" onclick="setFilter('ALL',this)">All <span class="count" id="cnt-all">0</span></button>
<button class="tab" onclick="setFilter('INCOMING',this)">Incoming <span class="count" id="cnt-incoming">0</span></button>
<button class="tab" onclick="setFilter('APPROVED',this)">Approved <span class="count" id="cnt-approved">0</span></button>
<button class="tab" onclick="setFilter('INCOMING_APPROVED',this)">Incoming+Approved <span class="count" id="cnt-ia">0</span></button>
<button class="tab" onclick="setFilter('DONE',this)">Done <span class="count" id="cnt-done">0</span></button>
<button class="tab" onclick="setFilter('DENIED',this)">Denied <span class="count" id="cnt-denied">0</span></button>
<button class="tab" id="sort-btn" onclick="toggleSort()" style="margin-left:auto">Sort: Time</button>
</div>

<div class="list" id="list"></div>
</div>

<script>
let questions=[],filter='ALL',sortBy='time',displayedId=null,editingId=null,authed=false;
let password=new URLSearchParams(window.location.search).get('password')||localStorage.getItem('qa_admin_pw')||'';
if(password)localStorage.setItem('qa_admin_pw',password);
const headers={'Content-Type':'application/json'};

function setHeaders(){
  if(password)headers['X-QA-Password']=password;
}
setHeaders();

async function checkAuth(){
  const r=await fetch('/api/qa/auth',{method:'POST',headers});
  if(r.ok){authed=true;document.getElementById('login-screen').style.display='none';document.getElementById('main-app').style.display='block';load();checkStatus()}
  else{document.getElementById('login-screen').style.display='block';document.getElementById('main-app').style.display='none';document.getElementById('pw-input').focus()}
}
async function doLogin(){
  password=document.getElementById('pw-input').value;
  localStorage.setItem('qa_admin_pw',password);
  headers['X-QA-Password']=password;
  const r=await fetch('/api/qa/auth',{method:'POST',headers});
  if(r.ok){authed=true;document.getElementById('login-screen').style.display='none';document.getElementById('main-app').style.display='block';load();checkStatus()}
  else{document.getElementById('pw-err').textContent='Incorrect password';document.getElementById('pw-err').style.display='block'}
}

// Check if password needed
(async()=>{
  try{const r=await fetch('/api/qa/auth',{method:'POST',headers});
    if(r.ok){authed=true;document.getElementById('main-app').style.display='block';load();checkStatus()}
    else{document.getElementById('login-screen').style.display='block';document.getElementById('pw-input').focus()}
  }catch(e){document.getElementById('login-screen').style.display='block'}
})();

function lockOut(){
  authed=false;password='';localStorage.removeItem('qa_admin_pw');
  document.getElementById('main-app').style.display='none';
  document.getElementById('login-screen').style.display='block';
  document.getElementById('pw-err').textContent='Session expired. Please log in again.';
  document.getElementById('pw-err').style.display='block';
  document.getElementById('pw-input').value='';document.getElementById('pw-input').focus();
}
let lastLoadSig='';
async function load(){
  if(!authed)return;
  try{
    const r=await fetch('/api/qa/questions',{headers});
    if(r.status===401){lockOut();return}
    if(r.ok)questions=await r.json();
    // Skip the full innerHTML rebuild when nothing relevant changed (prevents flicker/mis-taps).
    const sig=JSON.stringify(questions.map(q=>[q.id,q.status,q.text,q.upvotes,q.downvotes,q.submitterName]))+'|'+displayedId;
    if(sig===lastLoadSig)return;
    lastLoadSig=sig;
    if(!editingId)render();
  }catch(e){}
}

function render(){
  // Show/hide clear display bar
  const dbar=document.getElementById('display-bar');
  if(displayedId){
    const dq=questions.find(q=>q.id===displayedId);
    document.getElementById('display-text').textContent='Displaying: '+(dq?dq.text.substring(0,50):'...');
    dbar.style.display='flex';
  }else{dbar.style.display='none'}
  let filtered;
  if(filter==='ALL')filtered=questions;
  else if(filter==='INCOMING')filtered=questions.filter(q=>q.status==='PENDING');
  else if(filter==='APPROVED')filtered=questions.filter(q=>q.status==='APPROVED');
  else if(filter==='INCOMING_APPROVED')filtered=questions.filter(q=>q.status==='PENDING'||q.status==='APPROVED');
  else if(filter==='DONE')filtered=questions.filter(q=>q.status==='DONE');
  else if(filter==='DENIED')filtered=questions.filter(q=>q.status==='DENIED');
  else filtered=questions;
  const list=document.getElementById('list');
  document.getElementById('cnt-all').textContent=questions.length;
  document.getElementById('cnt-incoming').textContent=questions.filter(q=>q.status==='PENDING').length;
  document.getElementById('cnt-approved').textContent=questions.filter(q=>q.status==='APPROVED').length;
  document.getElementById('cnt-ia').textContent=questions.filter(q=>q.status==='PENDING'||q.status==='APPROVED').length;
  document.getElementById('cnt-done').textContent=questions.filter(q=>q.status==='DONE').length;
  document.getElementById('cnt-denied').textContent=questions.filter(q=>q.status==='DENIED').length;

  if(sortBy==='votes')filtered=[...filtered].sort((a,b)=>b.voteCount-a.voteCount);
  else filtered=[...filtered].sort((a,b)=>a.timestamp-b.timestamp);
  if(!filtered.length){list.innerHTML='<div class="empty">No questions</div>';return}
  list.innerHTML=filtered.map(q=>{
    const time=new Date(q.timestamp).toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'});
    const isLive=q.id===displayedId;
    let label='';
    if(q.status==='DONE')label='<span class="q-label done">Done</span> ';
    else if(q.status==='DENIED')label='<span class="q-label denied">Denied</span> ';
    let actions='<button class="btn btn-edit" onclick="editQ(\''+q.id+'\')">Edit</button>';
    if(q.status==='PENDING'){
      actions+='<button class="btn btn-approve" onclick="action(\'approve\',\''+q.id+'\')">Approve</button><button class="btn btn-deny" onclick="action(\'deny\',\''+q.id+'\')">Deny</button>';
    }else if(q.status==='APPROVED'){
      actions+=(isLive?'':'<button class="btn btn-live" onclick="action(\'display\',\''+q.id+'\')">Go Live</button>')+'<button class="btn btn-done" onclick="action(\'done\',\''+q.id+'\')">Done</button><button class="btn btn-deny" onclick="action(\'deny\',\''+q.id+'\')">Deny</button>';
    }else if(q.status==='DONE'){
      actions+='<button class="btn btn-back" onclick="action(\'approve\',\''+q.id+'\')">Back to Incoming</button>';
      actions+='<button class="btn btn-golive-confirm" onclick="confirmGoLive(\''+q.id+'\')">Go Live</button>';
    }else if(q.status==='DENIED'){
      actions+='<button class="btn btn-approve" onclick="action(\'approve\',\''+q.id+'\')">Approve</button>';
      actions+='<button class="btn btn-golive-confirm" onclick="confirmGoLive(\''+q.id+'\')">Go Live</button>';
    }
    actions+='<button class="btn btn-del" onclick="del(\''+q.id+'\')">Del</button>';
    const nameTag=q.submitterName?'<span style="color:#888;font-size:12px">'+esc(q.submitterName)+':</span> ':'';
    const upTag=q.upvotes>0?'<span style="display:inline-block;background:#e3f2fd;color:#1565c0;font-size:11px;font-weight:700;padding:2px 4px;border-radius:4px;margin-right:2px">&#9650; '+q.upvotes+'</span>':'';
    const dnTag=q.downvotes>0?'<span style="display:inline-block;background:#ffebee;color:#c62828;font-size:11px;font-weight:700;padding:2px 4px;border-radius:4px;margin-right:4px">&#9660; '+q.downvotes+'</span>':'';
    const voteTag=upTag+dnTag;
    return '<div class="q'+(isLive?' live':'')+'" id="q-'+q.id+'"><span class="q-time">'+time+'</span><span class="q-text" id="qt-'+q.id+'">'+voteTag+label+nameTag+esc(q.text)+'</span><div class="q-actions">'+actions+'</div></div>';
  }).join('');
}

function esc(s){const d=document.createElement('div');d.textContent=s;return d.innerHTML}

let pendingConfirm=null;
function confirmGoLive(id){
  if(pendingConfirm===id){
    pendingConfirm=null;
    action('approve',id).then(()=>action('display',id));
    return;
  }
  pendingConfirm=id;
  const el=document.getElementById('q-'+id);
  if(el){const btns=el.querySelectorAll('.btn-golive-confirm');btns.forEach(b=>{b.textContent='Confirm Go Live?';b.style.opacity='1'})}
  setTimeout(()=>{if(pendingConfirm===id){pendingConfirm=null;load()}},3000);
}
async function action(act,id){
  try{const r=await fetch('/api/qa/questions/'+id+'/'+act,{method:'POST',headers});
    if(r.status===401){lockOut();return}
    if(r.ok&&act==='display')displayedId=id;
    if(r.ok&&(act==='done'||act==='deny'))if(displayedId===id)displayedId=null;
    load();
  }catch(e){}
}
async function del(id){
  if(!confirm('Delete this question? This cannot be undone.'))return;
  try{const r=await fetch('/api/qa/questions/'+id,{method:'DELETE',headers});if(r.status===401){lockOut();return}load()}catch(e){}
}
function editQ(id){
  const q=questions.find(q=>q.id===id);if(!q)return;
  editingId=id;
  const el=document.getElementById('qt-'+id);if(!el)return;
  el.innerHTML='<div class="edit-area"><textarea id="edit-'+id+'" spellcheck="true" aria-label="Edit question"></textarea><button class="btn btn-approve" onclick="saveEdit(\''+id+'\')">Save</button><button class="btn btn-del" onclick="cancelEdit()">Cancel</button></div>';
  const ta=document.getElementById('edit-'+id);if(ta){ta.value=q.text;ta.focus();ta.setSelectionRange(ta.value.length,ta.value.length)}
}
async function saveEdit(id){
  const ta=document.getElementById('edit-'+id);if(!ta)return;
  const text=ta.value.trim();if(!text)return;
  editingId=null;
  try{const r=await fetch('/api/qa/questions/'+id+'/edit',{method:'POST',headers,body:JSON.stringify({text})});if(r.status===401){lockOut();return}load()}catch(e){}
}
function cancelEdit(){editingId=null;render()}
async function addQ(){
  const inp=document.getElementById('add-input');const text=inp.value.trim();if(!text)return;
  inp.value='';
  try{const r=await fetch('/api/qa/add',{method:'POST',headers,body:JSON.stringify({text})});if(r.status===401){lockOut();return}load()}catch(e){}
}
async function clearDisplay(){
  try{await fetch('/api/qa/clear-display',{method:'POST',headers});displayedId=null;render();load()}catch(e){}
}
function setFilter(f,el){
  filter=f;
  document.querySelectorAll('.tab').forEach(t=>t.classList.remove('active'));
  el.classList.add('active');
  render();
}
function toggleSort(){
  sortBy=sortBy==='time'?'votes':'time';
  document.getElementById('sort-btn').textContent='Sort: '+(sortBy==='votes'?'Votes':'Time');
  render();
}
async function checkStatus(){
  if(!authed)return;
  try{const r=await fetch('/api/qa/status');const d=await r.json();
    document.getElementById('status').textContent=d.sessionActive?'Session Active':'Session Inactive';
    document.getElementById('status').style.color=d.sessionActive?'#66bb6a':'#ef5350';
    const newDisplayed=d.displayedQuestionId||null;
    if(newDisplayed!==displayedId){displayedId=newDisplayed;render()}
  }catch(e){document.getElementById('status').textContent='Disconnected';document.getElementById('status').style.color='#ef5350'}
}

setInterval(()=>{if(authed)load()},3000);
setInterval(()=>{if(authed)checkStatus()},3000);
</script>
</body>
</html>
""".trimIndent()

    private fun presentationRemotePageHtml(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<title>Presentation Remote</title>
<style>
*{box-sizing:border-box;margin:0;padding:0;-webkit-tap-highlight-color:transparent}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#111;color:#fff;height:100dvh;display:flex;flex-direction:column;overflow:hidden;user-select:none}
#login{display:flex;flex-direction:column;align-items:center;justify-content:center;flex:1;padding:24px;gap:12px}
#login h2{font-size:20px;margin-bottom:4px}
#login p{font-size:14px;color:#888;text-align:center;max-width:280px}
#login input{width:100%;max-width:320px;padding:12px;border-radius:10px;border:1px solid #333;background:#222;color:#fff;font-size:16px;outline:none}
#login input:focus{border-color:#4fa3e3}
#login button{width:100%;max-width:320px;padding:13px;background:#4fa3e3;color:#fff;border:none;border-radius:10px;font-size:16px;font-weight:600;cursor:pointer}
#err{color:#e57373;font-size:13px;display:none}
#app{display:none;flex-direction:column;flex:1;overflow:hidden}
#topbar{display:flex;align-items:center;padding:8px 10px;background:#1a1a1a;border-bottom:1px solid #222;gap:8px;flex-wrap:wrap;flex-shrink:0}
#counter{font-size:16px;font-weight:700;letter-spacing:1px;min-width:56px}
#blanked-badge{background:#e57373;color:#fff;font-size:10px;font-weight:700;padding:3px 8px;border-radius:12px;letter-spacing:.5px;display:none;flex-shrink:0}
#btns{display:flex;gap:6px;margin-left:auto}
.icon-btn{background:#2a2a2a;border:1px solid #333;color:#fff;border-radius:8px;padding:7px 10px;font-size:12px;cursor:pointer;font-weight:500;transition:background .15s;white-space:nowrap;touch-action:manipulation}
.icon-btn:active{background:#3a3a3a}
.icon-btn.active{background:#e57373;border-color:#e57373}
.icon-btn.active-play{background:#43a047;border-color:#43a047}
#not-live-bar{background:#1e3a5f;color:#aac4e8;font-size:12px;font-weight:500;padding:6px 16px;text-align:center;display:none;flex-shrink:0;border-bottom:1px solid #2a4a70;letter-spacing:.2px}
#slides-area{flex:1;display:flex;flex-direction:row;overflow:hidden;min-height:0}
#cur-wrap{flex:2;position:relative;overflow:hidden;background:#000;display:flex;align-items:center;justify-content:center;border-right:1px solid #222}
#cur-img{max-width:100%;max-height:100%;object-fit:contain;display:block}
#blanked-overlay{position:absolute;inset:0;background:rgba(0,0,0,.5);display:none;align-items:flex-start;justify-content:flex-end;padding:8px;pointer-events:none}
#blanked-overlay span{background:#e57373;color:#fff;font-size:11px;font-weight:700;padding:3px 8px;border-radius:10px}
#next-wrap{flex:1;display:flex;flex-direction:column;overflow:hidden;background:#0d0d0d;min-width:0;cursor:pointer;touch-action:manipulation}
#next-label{font-size:9px;font-weight:700;letter-spacing:1px;color:#555;padding:6px 8px 4px;border-bottom:1px solid #1e1e1e;flex-shrink:0;text-transform:uppercase;pointer-events:none}
#next-img-wrap{flex:1;display:flex;align-items:center;justify-content:center;padding:8px;overflow:hidden;pointer-events:none}
#next-img{max-width:100%;max-height:100%;object-fit:contain;border-radius:4px;display:block}
#upload-status{font-size:11px;color:#aaa;text-align:center;padding:3px 12px;min-height:18px;flex-shrink:0;background:#1a1a1a;border-top:1px solid #222}
#strip-handle{height:6px;background:#222;cursor:row-resize;border-top:1px solid #333;flex-shrink:0;display:flex;align-items:center;justify-content:center}
#strip-handle::after{content:'';width:36px;height:2px;background:#444;border-radius:1px}
#strip-wrap{height:90px;background:#151515;flex-shrink:0;overflow-x:auto;overflow-y:hidden;display:flex;align-items:center;padding:8px 10px;gap:8px;-webkit-overflow-scrolling:touch;scrollbar-width:auto;scrollbar-color:#555 #222}
#strip-wrap::-webkit-scrollbar{height:6px}
#strip-wrap::-webkit-scrollbar-track{background:#222}
#strip-wrap::-webkit-scrollbar-thumb{background:#555;border-radius:3px}
.s-thumb{flex-shrink:0;cursor:pointer;border-radius:6px;overflow:hidden;border:2px solid #2a2a2a;transition:border-color .1s;position:relative;height:var(--thumb-h,66px);aspect-ratio:16/9;touch-action:manipulation}
.s-thumb.cur{border-color:#43a047}
.s-thumb img{width:100%;height:100%;object-fit:contain;display:block;background:#000}
.s-num{position:absolute;bottom:2px;right:3px;font-size:9px;font-weight:700;background:rgba(0,0,0,.65);color:#fff;border-radius:2px;padding:1px 3px;pointer-events:none}
#botbar{display:flex;align-items:center;padding:8px 10px;background:#1a1a1a;border-top:1px solid #222;gap:8px;flex-shrink:0}
.nav-btn{background:#484848;border:1px solid #6a6a6a;color:#fff;border-radius:10px;padding:14px;font-size:18px;font-weight:700;cursor:pointer;flex:1;text-align:center;transition:background .15s;line-height:1;touch-action:manipulation}
.nav-btn:active{background:#606060}
.hidden{display:none}
.grid-icon{display:inline-grid;grid-template-columns:1fr 1fr;grid-template-rows:1fr 1fr;gap:2px;width:14px;height:14px;vertical-align:middle}
.grid-icon i{background:#fff;border-radius:1px;display:block}
#botbar.expanded{flex:1;align-items:stretch;padding:12px;gap:12px}
#botbar.expanded .nav-btn{font-size:44px;display:flex;align-items:center;justify-content:center}
</style>
</head>
<body>
<div id="login">
  <h2>Presentation Remote</h2>
  <input id="pw-input" type="password" placeholder="Password (if set)" autocomplete="current-password">
  <button onclick="doLogin()">Connect</button>
  <span id="err">Incorrect password</span>
</div>
<div id="app">
  <div id="topbar">
    <div id="counter">– / –</div>
    <div id="blanked-badge">BLANKED</div>
    <div id="btns">
      <button class="icon-btn" id="hide-btn" onclick="toggleHideSlides()" title="Hide slides" aria-label="Hide slides"><span class="grid-icon"><i></i><i></i><i></i><i></i></span></button>
      <button class="icon-btn" id="blank-btn" onclick="toggleBlank()">Blank</button>
      <button class="icon-btn" id="play-btn" onclick="togglePlay()">Auto ▶ 5s</button>
      <button class="icon-btn" id="upload-btn" onclick="document.getElementById('upload-input').click()">⬆ Upload</button>
      <input type="file" id="upload-input" accept=".pdf,.ppt,.pptx,.key" style="display:none">
    </div>
  </div>
  <div id="not-live-bar">⚠ Presentation not on screen — enable from the desktop app</div>
  <div id="slides-area">
    <div id="cur-wrap">
      <img id="cur-img" alt="" draggable="false">
      <div id="blanked-overlay"><span>BLANKED</span></div>
    </div>
    <div id="next-wrap" onclick="goSlide(state.index+1)">
      <div id="next-label">Next Slide</div>
      <div id="next-img-wrap"><img id="next-img" alt="" draggable="false"></div>
    </div>
  </div>
  <div id="upload-status"></div>
  <div id="strip-handle"></div>
  <div id="strip-wrap"></div>
  <div id="botbar">
    <button class="nav-btn" onclick="goSlide(state.index-1)">‹ Backward</button>
    <button class="nav-btn" onclick="goSlide(state.index+1)">Forward ›</button>
  </div>
</div>
<script>
let state={id:'',index:0,total:0,frozen:false,isPlaying:false,isLive:false,autoScrollInterval:5};
let fetchFailCount=0;
let offlineMode=false;
let slidesHidden=localStorage.getItem('remote_slides_hidden')==='1';
let password=new URLSearchParams(location.search).get('password')||sessionStorage.getItem('remote_pw')||'';
if(password)sessionStorage.setItem('remote_pw',password);
const headers={'Content-Type':'application/json'};
if(password)headers['X-Presentation-Password']=password;
function slideUrl(i){return'/api/presentations/'+state.id+'/slides/'+i+(password?('?apiKey='+encodeURIComponent(password)):'');}
let stripBuilt=false;
function loadImg(img,src){
  if(img._want===src)return;
  img._want=src;img._tries=0;
  img.onerror=function(){
    if(img._want!==src)return;
    if(img._tries<6){
      img._tries++;
      setTimeout(()=>{if(img._want===src)img.src=src+(src.includes('?')?'&':'?')+'r='+img._tries;},800*img._tries);
    }
  };
  img.src=src;
}
function buildStrip(){
  const wrap=document.getElementById('strip-wrap');
  wrap.innerHTML='';
  for(let i=0;i<state.total;i++){
    const d=document.createElement('div');
    d.className='s-thumb'+(i===state.index?' cur':'');
    d.id='st-'+i;
    const idx=i;
    d.onclick=()=>goSlide(idx);
    const im=document.createElement('img');im.loading='lazy';loadImg(im,slideUrl(i));
    const span=document.createElement('span');span.className='s-num';span.textContent=i+1;
    d.appendChild(im);d.appendChild(span);
    wrap.appendChild(d);
  }
  stripBuilt=true;
}
function updateStripCurrent(){
  const prev=document.querySelector('.s-thumb.cur');if(prev)prev.classList.remove('cur');
  const cur=document.getElementById('st-'+state.index);
  if(cur){cur.classList.add('cur');cur.scrollIntoView({inline:'nearest',block:'nearest',behavior:'smooth'});}
}
function applyHideSlides(){
  document.getElementById('slides-area').classList.toggle('hidden',slidesHidden);
  document.getElementById('strip-wrap').classList.toggle('hidden',slidesHidden);
  document.getElementById('strip-handle').classList.toggle('hidden',slidesHidden);
  document.getElementById('upload-status').classList.toggle('hidden',slidesHidden);
  if(slidesHidden)document.getElementById('not-live-bar').style.display='none';
  document.getElementById('botbar').classList.toggle('expanded',slidesHidden);
  const hb=document.getElementById('hide-btn');
  hb.classList.toggle('active',slidesHidden);
  hb.title=slidesHidden?'Show slides':'Hide slides';hb.setAttribute('aria-label',hb.title);
}
function toggleHideSlides(){slidesHidden=!slidesHidden;localStorage.setItem('remote_slides_hidden',slidesHidden?'1':'0');applyHideSlides();}
function updateUI(){
  document.getElementById('counter').textContent=state.total>0?(state.index+1)+' / '+state.total:'– / –';
  const fb=document.getElementById('blank-btn');
  fb.classList.toggle('active',state.frozen);fb.textContent=state.frozen?'Unblank':'Blank';
  document.getElementById('blanked-badge').style.display=state.frozen?'inline-block':'none';
  document.getElementById('blanked-overlay').style.display=state.frozen?'flex':'none';
  const pb=document.getElementById('play-btn');
  pb.classList.toggle('active-play',state.isPlaying);pb.textContent=state.isPlaying?('⏸ Auto '+state.autoScrollInterval+'s'):('Auto ▶ '+state.autoScrollInterval+'s');
  document.getElementById('not-live-bar').style.display=(state.total>0&&!state.isLive)?'block':'none';
  if(state.id){
    loadImg(document.getElementById('cur-img'),slideUrl(state.index));
    const ni=document.getElementById('next-img');
    if(state.index+1<state.total){loadImg(ni,slideUrl(state.index+1));ni.style.display='block';}
    else{ni.style.display='none';}
  }else{
    const ci=document.getElementById('cur-img');ci._want='';ci.src='';
    const ni=document.getElementById('next-img');ni._want='';ni.src='';ni.style.display='none';
  }
  if(!stripBuilt||document.getElementById('strip-wrap').children.length!==state.total){buildStrip();}
  else{updateStripCurrent();}
  applyHideSlides();
}
async function fetchStatus(){
  try{
    const r=await fetch('/api/presentation-remote/status');const d=await r.json();
    fetchFailCount=0;
    if(offlineMode){if(d.enabled){location.reload();}else{offlineMode=false;showDisabled();}return;}
    if(!d.enabled){showDisabled();return;}
    const changed=d.id!==state.id||d.index!==state.index||d.total!==state.total||d.frozen!==state.frozen||d.isPlaying!==state.isPlaying||d.isLive!==state.isLive||d.autoScrollInterval!==state.autoScrollInterval;
    state={...state,...d};if(changed)updateUI();
  }catch(e){fetchFailCount++;if(!offlineMode&&fetchFailCount>=2)showOffline();}
}
function showDisabled(){
  document.getElementById('app').style.display='none';
  document.getElementById('login').style.display='flex';
  document.getElementById('login').innerHTML='<h2>Remote control is disabled</h2><p>Enable it in the app — this page will auto-connect.</p>';
  startPollingForEnable();
}
function showOffline(){
  offlineMode=true;
  document.getElementById('app').style.display='none';
  document.getElementById('login').style.display='flex';
  document.getElementById('login').innerHTML='<h2>App not available</h2><p>Connection lost — the app may be closed or the network is unavailable.</p>';
}
function startPollingForEnable(){
  (async function poll(){
    try{
      const r=await fetch('/api/presentation-remote/status');const d=await r.json();
      if(d.enabled){location.reload();return;}
    }catch(_){}
    setTimeout(poll,3000);
  })();
}
async function doLogin(){
  password=document.getElementById('pw-input').value;
  sessionStorage.setItem('remote_pw',password);headers['X-Presentation-Password']=password;
  const r=await fetch('/api/presentation-remote/auth',{method:'POST',headers});
  if(r.ok){
    const st=await fetch('/api/presentation-remote/status').then(r=>r.json()).catch(()=>null);
    if(st)state={...state,...st};
    document.getElementById('login').style.display='none';document.getElementById('app').style.display='flex';
    stripBuilt=false;updateUI();startWs();setInterval(fetchStatus,2500);
  }else{document.getElementById('err').style.display='block';}
}
async function post(path){try{return await fetch(path,{method:'POST',headers});}catch(e){return null;}}
function toggleBlank(){state.frozen=!state.frozen;updateUI();post('/api/presentation-remote/freeze');}
function togglePlay(){post('/api/presentation-remote/play-pause');}
function goSlide(i){if(i<0||i>=state.total)return;post('/api/presentation-remote/goto/'+i);}
document.getElementById('upload-input').addEventListener('change',async function(){
  const file=this.files[0];if(!file)return;
  const btn=document.getElementById('upload-btn');const status=document.getElementById('upload-status');
  btn.textContent='Uploading…';btn.disabled=true;status.textContent='Uploading '+file.name+'…';
  const reader=new FileReader();
  reader.onload=async function(e){
    try{
      const r=await fetch('/api/presentation-remote/upload',{method:'POST',headers:{...headers,'Content-Type':'application/json'},body:JSON.stringify({name:file.name,data:e.target.result})});
      if(r.ok){status.textContent='✓ Loaded — slides will appear shortly';}
      else{const d=await r.json().catch(()=>({}));status.textContent='✗ '+(d.error||'Upload failed');}
    }catch(err){status.textContent='✗ Network error';}
    btn.textContent='⬆ Upload';btn.disabled=false;
  };
  reader.readAsDataURL(file);this.value='';
});
let touchX=0;
document.getElementById('cur-wrap').addEventListener('touchstart',e=>{touchX=e.changedTouches[0].clientX;},{passive:true});
document.getElementById('cur-wrap').addEventListener('touchend',e=>{
  const dx=e.changedTouches[0].clientX-touchX;
  if(Math.abs(dx)>50){dx<0?goSlide(state.index+1):goSlide(state.index-1);}
},{passive:true});
let stripResizing=false,stripY0=0,stripH0=0;
const sh=document.getElementById('strip-handle');
const sw=document.getElementById('strip-wrap');
function onSRStart(y){stripResizing=true;stripY0=y;stripH0=sw.offsetHeight;}
function onSRMove(y){if(!stripResizing)return;const dh=stripY0-y;const newH=Math.max(52,Math.min(window.innerHeight*0.75,stripH0+dh));sw.style.height=newH+'px';sw.style.setProperty('--thumb-h',Math.max(36,newH-24)+'px');}
sh.addEventListener('mousedown',e=>{onSRStart(e.clientY);e.preventDefault();});
document.addEventListener('mousemove',e=>{onSRMove(e.clientY);});
document.addEventListener('mouseup',()=>{stripResizing=false;});
sh.addEventListener('touchstart',e=>{onSRStart(e.touches[0].clientY);},{passive:true});
document.addEventListener('touchmove',e=>{if(stripResizing){onSRMove(e.touches[0].clientY);e.preventDefault();}},{passive:false});
document.addEventListener('touchend',()=>{stripResizing=false;});
function startWs(){
  const proto=location.protocol==='https:'?'wss':'ws';
  const ws=new WebSocket(proto+'://'+location.host+'/ws');
  ws.onmessage=e=>{
    try{
      const msg=JSON.parse(e.data);
      if(msg.type==='presentation_slide_changed'){
        const d=JSON.parse(msg.payload);
        const newPres=d.id!==state.id;
        state={...state,id:d.id,index:d.index,total:d.total,isPlaying:d.isPlaying,isLive:d.isLive||false};
        if(newPres)stripBuilt=false;
        updateUI();
      }else if(msg.type==='presentation_freeze_changed'){
        const d=JSON.parse(msg.payload);state={...state,frozen:d.frozen};updateUI();
      }else if(msg.type==='presentation_auto_scroll_changed'){
        const d=JSON.parse(msg.payload);state={...state,autoScrollInterval:d.autoScrollInterval};updateUI();
      }else if(msg.type==='presentation_live_changed'){
        const d=JSON.parse(msg.payload);state={...state,isLive:d.isLive};updateUI();
      }
    }catch(_){}
  };
  ws.onclose=()=>{setTimeout(startWs,2000);};
}
(async()=>{
  try{
    const r=await fetch('/api/presentation-remote/status');const d=await r.json();
    if(!d.enabled){
      document.getElementById('login').innerHTML='<h2>Remote control is disabled</h2><p>Enable it in the app — this page will auto-connect.</p>';
      startPollingForEnable();return;
    }
    if(!d.passwordRequired||password){
      if(d.passwordRequired){
        const authR=await fetch('/api/presentation-remote/auth',{method:'POST',headers});
        if(!authR.ok){document.getElementById('pw-input').value=password;return;}
      }
      state={...state,...d};
      document.getElementById('login').style.display='none';document.getElementById('app').style.display='flex';
      updateUI();startWs();setInterval(fetchStatus,2500);
    }
  }catch(_){}
})();
</script>
</body>
</html>
""".trimIndent()
}

// ── Extension mappers ─────────────────────────────────────────────────────────

fun SongItem.toDto() = SongDto(
    number = number,
    title = title,
    tune = tune,
    author = author
)

