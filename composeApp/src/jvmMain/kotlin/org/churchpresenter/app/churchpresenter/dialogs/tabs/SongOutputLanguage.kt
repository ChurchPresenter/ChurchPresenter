package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.song_language_both
import churchpresenter.composeapp.generated.resources.song_language_primary
import churchpresenter.composeapp.generated.resources.song_language_secondary
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/**
 * Which languages a song is presented in -- read and written where it actually lives.
 *
 * Not in [org.churchpresenter.settings.SongSettings]. `SongSettings` carries
 * `fullscreenLanguageDisplay`, `lowerThirdLanguageDisplay` and the two look-ahead variants, but
 * nothing live reads any of them: every real call site of `SongPresenter` --
 * `PresenterModeContent`, `LivePreviewPanel`, `OffscreenOutputContent` -- passes that output's
 * [ScreenAssignment.songMode] as `languageOverride`, which wins whenever it is set, and it is
 * always set. So a control writing the song-level fields restricts nothing.
 *
 * These accessors read and write [ScreenAssignment.songMode] instead, which is the setting that
 * reaches the screen.
 */

/** The outputs a given target stands for: the full-screen ones, or the lower-third ones. */
private fun AppSettings.outputsFor(target: SongStyleTarget): List<ScreenAssignment> =
    projectionSettings.screenAssignments.filter {
        if (target.isLowerThird) it.isLowerThird else it.displayMode == Constants.DISPLAY_MODE_FULLSCREEN
    }

/**
 * What [target]'s outputs are set to show.
 *
 * The first output of that kind that is showing songs at all speaks for the group -- an output
 * switched off contributes nothing to what is on screen, and reporting its "off" as the answer
 * would show the control a value it does not offer.
 */
internal fun AppSettings.songLanguageFor(target: SongStyleTarget): String =
    outputsFor(target).firstOrNull { it.songMode != Constants.SONG_LANG_OFF }?.songMode
        ?: Constants.SONG_LANG_BOTH

/**
 * [target]'s outputs set to show [language].
 *
 * An output switched off is left off: "off" means songs do not go to that screen at all, which is a
 * different question from which language they are in, and turning it back on from here would put a
 * song on a screen the operator deliberately kept clear.
 */
internal fun AppSettings.withSongLanguage(target: SongStyleTarget, language: String): AppSettings =
    mapSongModes(language) { assignment ->
        if (target.isLowerThird) {
            assignment.isLowerThird
        } else {
            assignment.displayMode == Constants.DISPLAY_MODE_FULLSCREEN
        }
    }

/** True when any output that is showing songs is showing two languages. */
internal val AppSettings.songIsBilingual: Boolean
    get() = projectionSettings.screenAssignments.any {
        it.songMode == Constants.SONG_LANG_BOTH || it.songMode == Constants.SONG_LANG_SECONDARY
    }

/**
 * Every output set to one language or two -- the coarse switch in the rail.
 *
 * Bilingual restores "both" rather than any previous per-output choice, and Single writes "primary"
 * over a "secondary" output as well: this is the control that says how many languages the church is
 * presenting in, and the per-target one on the element row is where a finer answer is given.
 */
internal fun AppSettings.withSongBilingual(bilingual: Boolean): AppSettings =
    mapSongModes(if (bilingual) Constants.SONG_LANG_BOTH else Constants.SONG_LANG_PRIMARY) { true }

private fun AppSettings.mapSongModes(
    language: String,
    matches: (ScreenAssignment) -> Boolean,
): AppSettings = copy(
    projectionSettings = projectionSettings.copy(
        screenAssignments = projectionSettings.screenAssignments.map { assignment ->
            if (assignment.songMode != Constants.SONG_LANG_OFF && matches(assignment)) {
                assignment.copy(songMode = language)
            } else {
                assignment
            }
        },
    ),
)

/** Wide enough for "Secondary" without an ellipsis. */
private val LANGUAGE_BUTTON_WIDTH = 82.dp

/** The Both / Primary / Secondary switch for [target]'s output, shared by both views of the tab. */
@Composable
internal fun SongLanguageScopeButtons(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    target: SongStyleTarget,
) {
    SegmentedButton(
        items = listOf(
            SegmentedButtonItem(Constants.SONG_LANG_BOTH, stringResource(Res.string.song_language_both)),
            SegmentedButtonItem(Constants.SONG_LANG_PRIMARY, stringResource(Res.string.song_language_primary)),
            SegmentedButtonItem(Constants.SONG_LANG_SECONDARY, stringResource(Res.string.song_language_secondary)),
        ),
        selectedValue = settings.songLanguageFor(target),
        onValueChange = { lang ->
            onSettingsChange { s -> s.withSongLanguage(target, lang) }
        },
        buttonWidth = LANGUAGE_BUTTON_WIDTH,
        buttonHeight = 30.dp,
        fontSize = MaterialTheme.typography.labelSmall.fontSize,
    )
}
