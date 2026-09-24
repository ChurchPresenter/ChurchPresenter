package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.background
import churchpresenter.composeapp.generated.resources.customize_bible
import churchpresenter.composeapp.generated.resources.customize_songs
import churchpresenter.composeapp.generated.resources.media_subtitles
import churchpresenter.composeapp.generated.resources.stage_monitor
import churchpresenter.composeapp.generated.resources.tab_dictionary
import churchpresenter.composeapp.generated.resources.tab_qa
import churchpresenter.composeapp.generated.resources.tab_stt
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/**
 * One category of a profile's appearance — one of the Profiles tab's Style tabs.
 *
 * Each maps to one part of [org.churchpresenter.settings.OutputProfile]: [STAGE_MONITOR] to its
 * `stageMonitorSettings`, [BIBLE] to `bibleSettings`, and so on. Unlike the per-output Customize
 * dialog this replaced, there is no per-category on/off any more -- a profile's styling for every
 * category is simply part of it, always. The tabs exist to organize the editing surface, not to
 * say what is or isn't customized.
 */
internal enum class CustomizePane {
    STAGE_MONITOR,
    BIBLE,
    SONGS,
    BACKGROUND,
    CAPTIONS,
    SUBTITLES,
    QA,
    DICTIONARY,
}

/**
 * True for the categories whose editor is a whole form of its own -- the stage monitor's zones,
 * captions, subtitles, Q&A and the dictionary card -- with no element chips and nothing on the strip
 * under the preview. Bible, Songs and Background are styled element by element.
 */
internal val CustomizePane.isWholeForm: Boolean
    get() = this !in setOf(CustomizePane.BIBLE, CustomizePane.SONGS, CustomizePane.BACKGROUND)

/** The categories a profile in [displayMode] can actually use, in tab order. */
internal fun customizePanes(displayMode: String): List<CustomizePane> =
    if (displayMode == Constants.DISPLAY_MODE_STAGE_MONITOR) {
        // A stage monitor draws its own zones; it never draws the full-screen or lower-third Bible
        // and Song profiles. Its Q&A and dictionary zones do draw those two in their own styling.
        listOf(CustomizePane.STAGE_MONITOR, CustomizePane.QA, CustomizePane.DICTIONARY)
    } else {
        listOf(
            CustomizePane.BIBLE,
            CustomizePane.SONGS,
            CustomizePane.BACKGROUND,
            CustomizePane.CAPTIONS,
            CustomizePane.SUBTITLES,
            CustomizePane.QA,
            CustomizePane.DICTIONARY,
        )
    }

@Composable
internal fun CustomizePane.label(): String = when (this) {
    CustomizePane.STAGE_MONITOR -> stringResource(Res.string.stage_monitor)
    CustomizePane.BIBLE -> stringResource(Res.string.customize_bible)
    CustomizePane.SONGS -> stringResource(Res.string.customize_songs)
    CustomizePane.BACKGROUND -> stringResource(Res.string.background)
    CustomizePane.CAPTIONS -> stringResource(Res.string.tab_stt)
    CustomizePane.SUBTITLES -> stringResource(Res.string.media_subtitles)
    CustomizePane.QA -> stringResource(Res.string.tab_qa)
    CustomizePane.DICTIONARY -> stringResource(Res.string.tab_dictionary)
}

/** Test handle for one Style tab, by [CustomizePane] name. (Named for the rail it used to be.) */
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

/**
 * The elements of [pane] this profile actually draws, in chip order.
 *
 * An element switched off under "Content on this output" has nothing on screen to style, so it
 * gets no chip: the look-ahead only while the look-ahead is on, and a background surface only while
 * both it and the content it sits behind are shown.
 */
internal fun styleElementsFor(pane: CustomizePane, profile: OutputProfile): List<CustomizeElement> =
    customizeElements(pane).filter { element ->
        when (element) {
            CustomizeElement.SONG_LOOK_AHEAD -> profile.songLookAhead
            CustomizeElement.BACKGROUND_DEFAULT ->
                if (profile.isLowerThird) profile.showLowerThirdBackground else profile.showFullscreenBackground
            CustomizeElement.BACKGROUND_BIBLE -> profile.showBible && profile.showBibleBackground
            CustomizeElement.BACKGROUND_SONG -> profile.showSongs && profile.showSongsBackground
            else -> true
        }
    }

/**
 * The Style tabs this profile offers: [customizePanes] less any category it has switched off.
 *
 * Bible goes with scripture, Songs with songs, and Background once none of its surfaces is left.
 * A stage monitor keeps its one tab whatever it shows -- its zones are its styling.
 */
internal fun stylePanesFor(profile: OutputProfile): List<CustomizePane> =
    customizePanes(profile.displayMode).filter { pane ->
        when (pane) {
            CustomizePane.BIBLE -> profile.showBible
            CustomizePane.SONGS -> profile.showSongs
            CustomizePane.BACKGROUND -> styleElementsFor(pane, profile).isNotEmpty()
            CustomizePane.CAPTIONS -> profile.showSTT
            CustomizePane.SUBTITLES -> profile.showSubtitles
            CustomizePane.QA -> profile.showQA
            CustomizePane.DICTIONARY -> profile.showDictionary
            CustomizePane.STAGE_MONITOR -> true
        }
    }
