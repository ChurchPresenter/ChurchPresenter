package org.churchpresenter.calendar.model

import kotlinx.serialization.Serializable
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * A schedule item saved from one of the app's tabs to be picked again later — a slideshow, a
 * presentation, a video, an announcement, a timer, a scene.
 *
 * What **Save preset** beside a tab's Add to Schedule writes. The item is an ordinary
 * [ScheduleItem], so a preset can go into a run of show, become a cue's payload, or be added to
 * the live schedule, all through the paths those already have.
 */
@Serializable
data class ItemPreset(
    val id: String,
    val name: String,
    val item: ScheduleItem,
    /**
     * When it was saved, as [storedInstant] writes it -- what orders the list newest first and what
     * decides a merge. A file from before the stamps were instants holds a local date-time here;
     * [normalizedStamp] reads that as this machine's zone, so the two kinds compare.
     */
    val savedAt: String = "",
)

/**
 * [ItemPreset.savedAt] as an instant string whatever form it was written in: an instant is returned
 * as it is, a local date-time (the form written before 2026-09) is taken in this machine's zone,
 * and anything else -- blank, garbage -- becomes the empty string, which sorts before every stamp.
 */
fun normalizedStamp(savedAt: String, zone: ZoneId = ZoneId.systemDefault()): String {
    if (savedAt.isBlank()) return ""
    if (savedAt.endsWith("Z")) return savedAt
    return try {
        storedInstant(LocalDateTime.parse(savedAt).atZone(zone).toInstant())
    } catch (_: DateTimeParseException) {
        ""
    }
}

/**
 * The preset as a run-of-show row: a fresh id, and the name it was saved under as its row text.
 *
 * The name is the only thing the user chose about it, and for a scene it is often the only thing
 * that tells two apart -- scenes carry a default name, so three presets of three different scenes
 * all read `Scene: Scene` if the item's own text is kept.
 */
fun ItemPreset.asRow(): ScheduleItem = item.withNewId().renamed(name)

/** The whole of `presets.json`. */
@Serializable
data class PresetDocument(
    val version: Int = CURRENT_PRESET_VERSION,
    val presets: List<ItemPreset> = emptyList(),
    /**
     * Presets deleted here, as id → when, for the same reason `CalendarDocument.deletedServices`
     * exists: a file shared between two machines can otherwise only ever gain presets, because the
     * machine still holding one puts it back. Pruned by [mergedWith] after [TOMBSTONE_LIFETIME].
     */
    val deletedPresets: Map<String, String> = emptyMap(),
) {
    /** Adds a preset, or replaces the one already saved under the same name. */
    fun withPreset(preset: ItemPreset): PresetDocument {
        val index = presets.indexOfFirst { it.id == preset.id || it.name.equals(preset.name, ignoreCase = true) }
        return copy(
            presets = if (index >= 0) presets.toMutableList().also { it[index] = preset } else presets + preset,
            deletedPresets = deletedPresets - preset.id,
        )
    }

    fun withoutPreset(id: String, at: Instant = Instant.now()): PresetDocument = copy(
        presets = presets.filterNot { it.id == id },
        deletedPresets = deletedPresets + (id to storedInstant(at)),
    )

    /**
     * This file and [other] as one, decided preset by preset the way `CalendarDocument.mergedWith`
     * decides services: on one side only and not deleted on the other → kept; on both → the one
     * saved later; deleted on one side → gone, unless it was saved again *after* the deletion.
     *
     * Two presets with one name but two ids -- each machine saved its own before seeing the
     * other's -- keep only the newer, because a name is what [withPreset] promises is unique.
     * Ordered by stamp and then id so both machines write the identical file and stop there.
     */
    fun mergedWith(other: PresetDocument, now: Instant = Instant.now()): PresetDocument {
        val tombstones = (deletedPresets + other.deletedPresets).mapValues { (id, stamp) ->
            maxOf(stamp, deletedPresets[id] ?: stamp, other.deletedPresets[id] ?: stamp)
        }
        val merged = (presets + other.presets)
            .groupBy { it.id }
            .mapNotNull { (id, both) ->
                val newest = both.maxBy { it.savedAt }
                val deletedAt = tombstones[id]
                if (deletedAt != null && newest.savedAt <= deletedAt) null else newest
            }
            .groupBy { it.name.lowercase() }
            .map { (_, sameName) -> sameName.maxWith(compareBy({ it.savedAt }, { it.id })) }
        return copy(
            version = maxOf(version, other.version),
            presets = merged.sortedWith(compareByDescending<ItemPreset> { it.savedAt }.thenBy { it.id }),
            deletedPresets = tombstones.filterValues { it > storedInstant(now.minus(TOMBSTONE_LIFETIME)) },
        )
    }
}

const val CURRENT_PRESET_VERSION: Int = 1

/**
 * The name a preset is offered under before the user changes it: the item's own title, not its
 * row text. A row's `displayText` carries an icon glyph, a "Scene:" prefix or a count in brackets,
 * and none of that belongs in a name.
 */
fun suggestedPresetName(item: ScheduleItem): String = cleanPresetName(
    when (item) {
        is ScheduleItem.PictureItem -> item.folderName
        is ScheduleItem.PresentationItem -> item.fileName
        is ScheduleItem.MediaItem -> item.mediaTitle
        is ScheduleItem.SceneItem -> item.sceneName
        is ScheduleItem.AnnouncementItem -> if (item.isTimer) item.displayText else item.text.lineSequence().first()
        else -> item.displayText
    }
)

/**
 * A preset name is letters, digits and spaces, with the punctuation a title ordinarily has —
 * nothing else. Emoji, symbols and control characters are dropped, and runs of spaces collapsed,
 * so a name pasted in from anywhere still reads as a name in a list.
 */
fun cleanPresetName(raw: String): String = raw
    .filter { it.isLetterOrDigit() || it == ' ' || it in NAME_PUNCTUATION }
    .replace(Regex(" {2,}"), " ")
    .trim()

private const val NAME_PUNCTUATION = "-_.,'()&:"
