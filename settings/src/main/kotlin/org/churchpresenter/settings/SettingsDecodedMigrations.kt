package org.churchpresenter.settings

import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.settings.utils.Constants

// The migration steps that work on the decoded [AppSettings] rather than the raw text (5, 11, 17,
// 20, 26).

/** What the tray's old constructor seeded both halves of a tile with: opaque black, nothing else. */
private val SEEDED_BLACK = SongBackground(type = SongBackgroundType.COLOR, color = "#000000")

/**
 * Schema version 5. Ensures new tabs (like QA) are hidden by default for existing users.
 * If the raw JSON has no "qaSettings" key, the user has never interacted with Q&A,
 * so we add "QA" to hiddenTabs if it's not already there.
 */
internal fun migrateHiddenTabs(settings: AppSettings, raw: String): AppSettings {
    var result = settings
    if ("\"qaSettings\"" !in raw && "QA" !in result.hiddenTabs) {
        result = result.copy(hiddenTabs = result.hiddenTabs + "QA")
    }
    if ("\"sttSettings\"" !in raw && "STT" !in result.hiddenTabs) {
        result = result.copy(hiddenTabs = result.hiddenTabs + "STT")
    }
    return result
}

/**
 * Schema version 17. A quick-tray tile's lower-third half goes back to inheriting the output's
 * band, where the tray's old constructor had seeded it opaque black.
 *
 * The fix that let a tile's band inherit changed the constructor and said so plainly: *"tiles
 * already saved keep whatever they hold."* They do, and that is the whole of the report that
 * followed it — every tile an operator had made before the update still carried a black band, so
 * picking one still painted every lower third solid black, a keyed transparent one included.
 * A fix to a constructor cannot reach data that is already on disk.
 *
 * **Only the seeded value is reset.** The editor of the day offered no Inherit switch on that
 * half, so a tile holding exactly what the constructor produced -- an opaque black colour and
 * nothing else -- is one nobody chose. Anything else is a choice and is left alone: a tile whose
 * band is a picture, a gradient, a dimmed black or any other colour comes through untouched.
 * A black band that *was* wanted is two clicks to set again, now that the switch exists.
 */
/**
 * Version 20. The preview panel's groups become one layout that draws them the same -- see
 * [layoutFromGroups] -- and outputs no group held stay out of the panel, as they did. A panel with
 * no groups keeps no layout, which is still every output listed one per row.
 */
internal fun migratePreviewGroupsToLayout(settings: AppSettings): AppSettings {
    val projection = settings.projectionSettings
    if (projection.previewLayouts.isNotEmpty()) return settings
    val layout = layoutFromGroups(projection.previewGroups, name = "") ?: return settings
    return settings.copy(
        projectionSettings = projection.copy(
            previewLayouts = listOf(layout),
            activePreviewLayout = layout.id,
            listUnplacedOutputs = false,
        ),
    )
}

internal fun migrateQuickBackgroundLowerThird(settings: AppSettings): AppSettings {
    if (settings.quickBackgrounds.none { it.lowerThirdBackground == SEEDED_BLACK }) return settings
    return settings.copy(
        quickBackgrounds = settings.quickBackgrounds.map { tile ->
            if (tile.lowerThirdBackground == SEEDED_BLACK) {
                tile.copy(lowerThirdBackground = SongBackground())
            } else {
                tile
            }
        },
    )
}

/** The single-language song modes, by the language they named before places -- primary first. */
private val SINGLE_SONG_MODES = listOf(
    Constants.SONG_LANG_PRIMARY,
    Constants.SONG_LANG_SECONDARY,
    Constants.SONG_LANG_THIRD,
    Constants.SONG_LANG_FOURTH,
)

/**
 * Version 26. A profile's song languages became places in the display order rather than the song's
 * own slots (#1029, `songLanguagePicks`). Where the stored order is still the slots in turn, a
 * place is its slot and nothing changes. Otherwise each profile's languages are rewritten as the
 * places they hold now, and its looks set to go with the language, as they did -- so every output
 * draws exactly what it drew before. A profile showing every language is left as it is: that
 * already meant the display order, which is what the Songs tab's menu promised it.
 */
internal fun migrateSongLanguagesToPlaces(settings: AppSettings): AppSettings {
    val order = settings.songSettings.languageDisplayOrder()
    if (order == IDENTITY_LANGUAGE_ORDER) return settings
    val projection = settings.projectionSettings
    return settings.copy(
        projectionSettings = projection.copy(
            outputProfiles = projection.outputProfiles.map { it.withSongLanguagesAsPlaces(order) },
        ),
    )
}

private fun OutputProfile.withSongLanguagesAsPlaces(order: List<Int>): OutputProfile {
    val slots = when {
        songTranslations.isNotEmpty() -> songTranslations
        songMode in SINGLE_SONG_MODES -> listOf(SINGLE_SONG_MODES.indexOf(songMode))
        else -> emptyList()
    }
    val extras = songSettings.layoutExtras.copy(languageLooksFollowPlace = false)
    val kept = copy(songSettings = songSettings.copy(layoutExtras = extras))
    if (slots.isEmpty() || songMode == Constants.SONG_LANG_OFF) return kept
    return kept.copy(
        songMode = Constants.SONG_LANG_BOTH,
        songTranslations = slots.map { order.indexOf(it) }.filter { it >= 0 },
    )
}
