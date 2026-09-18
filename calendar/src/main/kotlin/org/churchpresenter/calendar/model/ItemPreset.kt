package org.churchpresenter.calendar.model

import kotlinx.serialization.Serializable
import org.churchpresenter.core.models.schedule.ScheduleItem

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
    /** ISO-8601 local date-time, for ordering newest first. */
    val savedAt: String = "",
)

/** The whole of `presets.json`. */
@Serializable
data class PresetDocument(
    val version: Int = CURRENT_PRESET_VERSION,
    val presets: List<ItemPreset> = emptyList(),
) {
    /** Adds a preset, or replaces the one already saved under the same name. */
    fun withPreset(preset: ItemPreset): PresetDocument {
        val index = presets.indexOfFirst { it.id == preset.id || it.name.equals(preset.name, ignoreCase = true) }
        return copy(
            presets = if (index >= 0) presets.toMutableList().also { it[index] = preset } else presets + preset
        )
    }

    fun withoutPreset(id: String): PresetDocument = copy(presets = presets.filterNot { it.id == id })
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
