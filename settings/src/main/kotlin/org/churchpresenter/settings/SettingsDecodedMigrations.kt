package org.churchpresenter.settings

import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType

// The migration steps that work on the decoded [AppSettings] rather than the raw text (5, 11, 17,
// 20).

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
