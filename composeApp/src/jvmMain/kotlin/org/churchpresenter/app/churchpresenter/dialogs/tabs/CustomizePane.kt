package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.background
import churchpresenter.composeapp.generated.resources.customize_bible
import churchpresenter.composeapp.generated.resources.customize_songs
import churchpresenter.composeapp.generated.resources.stage_monitor
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/**
 * One category of a profile's appearance — a row of the Profiles tab's left rail.
 *
 * Each maps to one part of [org.churchpresenter.settings.OutputProfile]: [STAGE_MONITOR] to its
 * `stageMonitorSettings`, [BIBLE] to `bibleSettings`, and so on. Unlike the per-output Customize
 * dialog this replaced, there is no per-category on/off any more -- a profile's styling for every
 * category is simply part of it, always. The rail exists to organize the editing surface, not to
 * say what is or isn't customized.
 */
internal enum class CustomizePane(val icon: ImageVector) {
    STAGE_MONITOR(Icons.Filled.Tv),
    BIBLE(Icons.Filled.MenuBook),
    SONGS(Icons.Filled.MusicNote),
    BACKGROUND(Icons.Filled.Wallpaper),
}

/**
 * The categories a profile in [displayMode] can actually use, in rail order.
 *
 * No dictionary: its look is one per install now, edited from the gear on the Dictionary tab the
 * way the STT tab styles itself, so there is nothing per-profile left to put here.
 */
internal fun customizePanes(displayMode: String): List<CustomizePane> =
    if (displayMode == Constants.DISPLAY_MODE_STAGE_MONITOR) {
        // A stage monitor draws its own zones; it never draws the full-screen or lower-third Bible
        // and Song profiles.
        listOf(CustomizePane.STAGE_MONITOR)
    } else {
        listOf(
            CustomizePane.BIBLE,
            CustomizePane.SONGS,
            CustomizePane.BACKGROUND,
        )
    }

@Composable
internal fun CustomizePane.label(): String = when (this) {
    CustomizePane.STAGE_MONITOR -> stringResource(Res.string.stage_monitor)
    CustomizePane.BIBLE -> stringResource(Res.string.customize_bible)
    CustomizePane.SONGS -> stringResource(Res.string.customize_songs)
    CustomizePane.BACKGROUND -> stringResource(Res.string.background)
}

/** Test handle for one rail row, by [CustomizePane] name. */
internal fun railTag(paneName: String): String = "customize_rail_$paneName"

/**
 * The mode a Display Mode dropdown should show as selected for a profile in [mode].
 *
 * The dropdown offers one Lower Third entry, so a vertical profile has to be recognized as that
 * entry — matching on the stored mode alone finds nothing and falls through to the Full Screen
 * label, which would report the wrong mode for every vertical profile.
 */
internal fun shownDisplayMode(mode: String): String =
    if (mode == Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL)
        Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL
    else mode

/**
 * The mode to store when the operator picks [picked] on a profile currently in [current].
 *
 * Picking Lower Third on a profile that is already a vertical strip leaves it vertical: the
 * dropdown entry means "be a lower third", and the orientation it already has is not something the
 * operator just asked to change.
 */
internal fun pickedDisplayMode(picked: String, current: String): String =
    if (picked == Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL &&
        current == Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL
    ) current else picked
