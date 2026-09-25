package org.churchpresenter.calendar.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.churchpresenter.core.models.schedule.RowTiming

/**
 * A run-of-show row as it travels between this desktop, the relay and the phones. A phone can
 * name a song, a passage, a section, a ministry item or a preset; a row this desktop authored
 * travels as a [RemoteRow.Ref] with a kind and a title.
 */
@Serializable
sealed class RemoteRow {
    abstract val id: String
    abstract val title: String

    @Serializable
    @SerialName("section")
    data class Section(override val id: String, override val title: String, val color: String = "") : RemoteRow()

    @Serializable
    @SerialName("song")
    data class Song(
        override val id: String,
        override val title: String,
        val songId: String = "",
        val songbook: String = "",
        val number: String = "",
    ) : RemoteRow()

    /** `Psalms 100:1-5` — the reference alone; this desktop looks the text up. */
    @Serializable
    @SerialName("bible")
    data class Bible(
        override val id: String,
        override val title: String,
        val preview: String = "",
        /** The canonical book number, 1..66, the same in every translation; 0 when only the name is known. */
        val bookId: Int = 0,
    ) : RemoteRow()

    @Serializable
    @SerialName("ministry")
    data class Ministry(override val id: String, override val title: String, val detail: String = "") : RemoteRow()

    /** One of this desktop's presets, by id; the item it holds stays here. */
    @Serializable
    @SerialName("preset")
    data class Preset(
        override val id: String,
        override val title: String,
        val presetId: String,
        val kind: String = RemoteKind.PRESET,
    ) : RemoteRow()

    /** A row this desktop authored: movable and removable on a phone, never opened there. */
    @Serializable
    @SerialName("ref")
    data class Ref(
        override val id: String,
        override val title: String,
        val kind: String = RemoteKind.OTHER,
        val subtitle: String = "",
    ) : RemoteRow()
}

/** The words a row's kind is spelled with on the wire; the phones draw icons from them. */
object RemoteKind {
    const val SECTION = "section"
    const val SONG = "song"
    const val BIBLE = "bible"
    const val MINISTRY = "ministry"
    const val PRESET = "preset"
    const val PICTURES = "pictures"
    const val PRESENTATION = "presentation"
    const val MEDIA = "media"
    const val SCENE = "scene"
    const val TIMER = "timer"
    const val ANNOUNCEMENT = "announcement"
    const val LOWER_THIRD = "lowerThird"
    const val WEBSITE = "website"
    const val DICTIONARY = "dictionary"
    const val CUE = "cue"
    const val OTHER = "other"
}

/**
 * A planned service as it travels inside a [SealedRecord]. [updatedAt], [updatedBy] and [rev]
 * are the relay's stamps: filled on the way in, ignored on the way out, and never merged on -- the
 * relay could write anything there.
 *
 * What orders two copies travels sealed: [version], the number of edits (a writer sends the copy
 * it edited plus one), and [editedAt], the writer's own clock, which only breaks a tie. A deletion
 * is a record too, [deleted] and a version, sealed like any other, so the relay can neither forge
 * one nor hand back an old copy that outranks a newer one.
 */
@Serializable
data class RemoteService(
    val id: String,
    val date: String,
    val startTime: String,
    val name: String,
    val kind: String = "",
    val armed: Boolean = true,
    val seriesId: String = "",
    val rows: List<RemoteRow> = emptyList(),
    val plannedSeconds: Map<String, Int> = emptyMap(),
    val timing: Map<String, RowTiming> = emptyMap(),
    val version: Long = 0L,
    val editedAt: String = "",
    val deleted: Boolean = false,
    val updatedAt: String = "",
    val updatedBy: String = "",
    val rev: Long = 0L,
)

/** One service as the relay stores it. [box] is [Envelope.seal] of a [RemoteService]'s JSON. */
@Serializable
data class SealedRecord(
    val id: String,
    /** `YYYY-MM-DD` after which the relay drops the record — the service's date plus the retention window. */
    val keepUntil: String,
    val box: String,
    val updatedAt: String = "",
    val updatedBy: String = "",
    val rev: Long = 0L,
)

@Serializable
data class RemoteTombstone(val id: String, val deletedAt: String)

/** A desktop preset as a phone sees it: enough to pick, nothing to open. Travels sealed, as [PresetIndex]. */
@Serializable
data class RemotePreset(val id: String, val name: String, val kind: String = RemoteKind.PRESET)

/** The plaintext behind the relay's one `presets` box. */
@Serializable
data class PresetIndex(val presets: List<RemotePreset> = emptyList())

/** A paired phone, for the revoke list. [nameBox] is its name, sealed under record id [id]. */
@Serializable
data class RemoteDevice(val id: String, val nameBox: String = "", val pairedAt: String = "", val lastSeen: String = "")

@Serializable
data class RegisterResponse(val desktopToken: String)

/** `PUT /i/{id}/devices/{deviceId}` — enrolling a phone this desktop approved. */
@Serializable
data class EnrollRequest(val tokenHash: String, val nameBox: String = "")

/** `GET /i/{id}/changes?since=` — everything newer than the cursor, and who last pushed as the desktop. */
@Serializable
data class ChangesResponse(
    /** The revision this page is complete up to; the cursor for the next call, whether or not [more]. */
    val rev: Long,
    /** A full page: call again from [rev] for the rest. A client that ignores this skips rows. */
    val more: Boolean = false,
    val records: List<SealedRecord> = emptyList(),
    val tombstones: List<RemoteTombstone> = emptyList(),
    val devices: List<RemoteDevice> = emptyList(),
    /** The install id of the desktop that last pushed, so a second machine on this instance is noticed. */
    val lastDesktopInstall: String = "",
)

/** `PUT /i/{id}/state` — the desktop's authoritative picture, replacing the relay's. */
@Serializable
data class StateRequest(
    val records: List<SealedRecord>,
    /** Always empty: deletions travel sealed, in [records]. Kept for the relay's schema. */
    val tombstones: List<RemoteTombstone> = emptyList(),
    /** [Envelope.seal] of a [PresetIndex], record id [PRESETS_RECORD]. */
    val presetsBox: String,
)

/** The record id the preset index is sealed under. */
const val PRESETS_RECORD = "presets"

/** The prefix of a song catalog record: `catalog:<songbook>` or `catalog:<songbook>:<part>`. */
const val CATALOG_PREFIX = "catalog:"

/**
 * One song as the catalog carries it: number, title, its usual length in seconds if measured, and
 * for a bilingual song the title in the second language.
 */
@Serializable
data class CatalogSong(val n: String, val t: String, val s: Int? = null, val t2: String? = null)

/**
 * One songbook (or one part of a large one) as the desktop keeps it on the relay, so a phone can
 * plan with the real song list and lengths without ever having been on the church network.
 */
@Serializable
data class CatalogRecord(val songbook: String, val part: Int = 0, val songs: List<CatalogSong> = emptyList())

@Serializable
data class StateResponse(val rev: Long)

/** The caps the relay enforces; this desktop applies the same ones. */
object WireLimits {
    const val NAME_CHARS = 120
    const val TITLE_CHARS = 200
    const val DETAIL_CHARS = 200
    const val ROWS_PER_SERVICE = 200
    const val SERVICES_PER_PUSH = 500
    const val DELETIONS_PER_PUSH = 500
    /** Songs per catalog record; a bigger book is split into parts so each box stays under the relay's cap. */
    const val CATALOG_PART_SONGS = 2_000
    const val CATALOG_SONGS_MAX = 20_000
    const val MAX_PLANNED_SECONDS = 24 * 60 * 60
    const val MAX_REPEATS = 99
    /** Services older than this are neither pushed nor kept by the relay. */
    const val RETENTION_DAYS = 90L
    /** How far ahead a service may be planned. */
    const val HORIZON_DAYS = 2 * 366L
}
