package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants

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
