package org.churchpresenter.app.churchpresenter.server

import org.churchpresenter.app.churchpresenter.models.songs.SongItem
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.churchpresenter.app.churchpresenter.data.StrongsEntry
import org.churchpresenter.app.churchpresenter.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.utils.Constants

// ── API DTOs ─────────────────────────────────────────────────────────────────

/**
 * A song as the companion API serves it.
 *
 * **Built inline by `CompanionServer.buildCatalog`, deliberately, and there is no
 * `SongItem.toDto()`.** One existed and was deleted as dead code, having never had a caller. It set
 * every field *except* [id], which defaults to 0 — so anything that adopted it would have given
 * every song the same id, and [id] is how a phone asks for one. The catalogue assigns it from the
 * song's position in the list, which a mapper over a single [SongItem] cannot know. Add a mapper
 * only if it takes that position as a parameter.
 */
@Serializable
data class SongDto(
    /** Position in the server's song list; how a client addresses a song. Not stable across reloads. */
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
    // announcement / timer (lets a companion reconstruct the composer from a schedule item)
    val fontSize: Int? = null,
    val animationType: String? = null,
    val animationDuration: Int? = null,
    val isTimer: Boolean? = null,
    val timerMode: String? = null,
    val timerHours: Int? = null,
    val timerMinutes: Int? = null,
    val timerSeconds: Int? = null,
    val timerExpiredText: String? = null,
    val targetHour: Int? = null,
    val targetMinute: Int? = null,
    val liveClockFormat: String? = null,
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

/**
 * Snapshot of whatever is currently live, broadcast as [Constants.WS_EVENT_LIVE_STATE_CHANGED].
 * Fills the gap left by the content types that (unlike presentations/songs) have no dedicated
 * "now live" event — used by a following instance ([InstanceLinkClient]) to mirror local output.
 *
 * Presentations are intentionally NOT duplicated here — they already have their own
 * presentation_slide_changed/freeze/live events; [contentType] == "PRESENTATION" just tells a
 * follower to rely on those instead.
 */
@Serializable
data class LiveStateDto(
    val contentType: String, // matches presenter.Presenting enum name
    // bible verse
    val bookName: String? = null,
    val chapter: Int? = null,
    val verseNumber: Int? = null,
    val verseRange: String? = null,
    val verseText: String? = null,
    // Canonical (numbering-independent) verse code — see Bible.getCodeReference/getVerseDetailsByCode.
    // Lets a follower in "reference-only" Bible sync mode (InstanceLinkSettings.BibleSyncMode) resolve
    // the same verse in its own independently-configured (possibly different-language) Bible, instead
    // of downloading the primary's file. Null when the primary has no bible loaded to compute it from.
    val verseCodeBook: Int? = null,
    val verseCodeChapter: Int? = null,
    val verseCodeVerse: Int? = null,
    // song section
    val songTitle: String? = null,
    val songNumber: Int? = null,
    val sectionType: String? = null,
    val lines: List<String>? = null,
    // Current line/section position within the mirrored section — lets a follower's own
    // line-by-line display mode track the primary's navigation, not just section changes.
    val songSectionIndex: Int? = null,
    val songLineIndex: Int? = null,
    // picture — resolved against a registered folder catalog when possible
    val pictureFolderId: String? = null,
    val pictureIndex: Int? = null,
    // media
    val mediaId: String? = null,
    val mediaUrl: String? = null,
    val mediaType: String? = null,
    // announcement
    val announcementText: String? = null,
    // website
    val websiteUrl: String? = null,
    val websiteTitle: String? = null,
    // canvas scene
    val sceneId: String? = null,
    val sceneName: String? = null,
    // Q&A
    val questionId: String? = null,
    val questionText: String? = null,
    // Strong's dictionary
    val dictionaryWord: String? = null,
    // Full Strong's entry for DICTIONARY mirroring — the primary holds it at broadcast time, and
    // a follower's own bundled dictionary may be a different language, so carrying the entry beats
    // a local lookup. Old primaries omit it (null → follower presents word-only); old followers
    // ignore it (ignoreUnknownKeys).
    val dictionaryEntry: StrongsEntry? = null,
    // lower third — resolves to a file via the same lowerThirdFiles() lookup /api/lowerthirds/{name}/json uses
    val lowerThirdName: String? = null
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
    val section: Int,
    /** 0-based index of the line within [section] for "one line at a time" display mode, or -1 when
     *  not applicable (section-level navigation only) — same sentinel PresenterManager.songDisplayLineIndex
     *  already uses. Defaults to -1 so older clients that omit this field still decode correctly. */
    val lineIndex: Int = -1
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
    val maxMediaUploadMb: Int = Constants.DEFAULT_MAX_MEDIA_UPLOAD_MB,
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
@OptIn(ExperimentalSerializationApi::class)
data class WebSocketMessage(
    val type: String,
    val payload: String = "",
    // Correlates a command with its command_ack reply (InstanceLink controller mode). NEVER-encoded
    // when null: this server's json has encodeDefaults=true, so without the annotation every
    // existing broadcast would grow a "commandId":null field and change the wire format for all
    // clients. Old servers ignore it (ignoreUnknownKeys); old clients never send it.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val commandId: String? = null
)

/** Reply to a WS command that carried a commandId — see [Constants.WS_EVENT_COMMAND_ACK].
 *  Approval-gated commands ack immediately with ok=true, reason="pending_approval" (the operator's
 *  decision can take minutes; its outcome still arrives via the following schedule_updated). */
@Serializable
data class CommandAckPayload(
    val commandId: String,
    val ok: Boolean,
    val reason: String? = null
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
    // dictionary (Strong's) — strongsNumber is the discriminator; word carried in `title`
    val strongsNumber: String? = null,
    val transliteration: String? = null,
    val definition: String? = null,
    // announcement / timer — announcementText is the discriminator ("" for a pure timer)
    val announcementText: String? = null,
    val textColor: String? = null,
    val backgroundColor: String? = null,
    val fontSize: Int? = null,
    val animationType: String? = null,
    val animationDuration: Int? = null,
    val isTimer: Boolean? = null,
    val timerHours: Int? = null,
    val timerMinutes: Int? = null,
    val timerSeconds: Int? = null,
    val timerTextColor: String? = null,
    val timerExpiredText: String? = null,
    val timerMode: String? = null,
    val targetHour: Int? = null,
    val targetMinute: Int? = null,
    val targetSecond: Int? = null,
    val liveClockFormat: String? = null,
    // website — url is the discriminator
    val url: String? = null,
    val websiteTitle: String? = null,
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

// Keep old wrappers for WS payloads that still use the sealed class discriminator
@Serializable
data class AddToScheduleRequest(val item: ScheduleItem)

@Serializable
data class ProjectRequest(val item: ScheduleItem)

/** Payload for WS "remove_from_schedule" — removes the schedule item with this id, subject to the
 *  same approval flow as add_to_schedule/project. */
@Serializable
data class RemoveFromScheduleRequest(val id: String)
