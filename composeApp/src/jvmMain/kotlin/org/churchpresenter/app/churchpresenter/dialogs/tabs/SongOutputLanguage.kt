package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.song_language_both
import churchpresenter.composeapp.generated.resources.song_language_primary
import churchpresenter.composeapp.generated.resources.song_language_fourth
import churchpresenter.composeapp.generated.resources.song_language_secondary
import churchpresenter.composeapp.generated.resources.song_language_third
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/**
 * Which languages a song is presented in -- read and written where it actually lives.
 *
 * Not in [org.churchpresenter.settings.SongSettings]. `SongSettings` carries
 * `fullscreenLanguageDisplay`, `lowerThirdLanguageDisplay` and the two look-ahead variants, but
 * nothing live reads any of them: every real call site of `SongPresenter` --
 * `PresenterModeContent`, `LivePreviewPanel`, `OffscreenOutputContent` -- passes that output's
 * assigned profile's [OutputProfile.songMode] as `languageOverride`, which wins whenever it is set,
 * and it is always set. So a control writing the song-level fields restricts nothing.
 *
 * These accessors read and write [OutputProfile.songMode] instead, which is the setting that
 * reaches the screen. [SongLanguageScopeButtons]'s `outputMode`/`onOutputModeChange` parameters --
 * used when editing one profile on the Profiles tab -- take precedence over these when given; the
 * `AppSettings`-wide accessors below speak for *every saved profile* of the matching shape, for
 * whatever global "every profile" control still wants that.
 */

/** The profiles a given target stands for: the full-screen ones, or the lower-third ones. */
private fun AppSettings.profilesFor(target: SongStyleTarget): List<OutputProfile> =
    projectionSettings.outputProfiles.filter {
        if (target.isLowerThird) it.isLowerThird else it.displayMode == Constants.DISPLAY_MODE_FULLSCREEN
    }

/**
 * What [target]'s profiles are set to show.
 *
 * The first profile of that kind that is showing songs at all speaks for the group -- a profile
 * switched off contributes nothing to what is on screen, and reporting its "off" as the answer
 * would show the control a value it does not offer.
 */
internal fun AppSettings.songLanguageFor(target: SongStyleTarget): String =
    profilesFor(target).firstOrNull { it.songMode != Constants.SONG_LANG_OFF }?.songMode
        ?: Constants.SONG_LANG_BOTH

/**
 * [target]'s profiles set to show [language].
 *
 * A profile switched off is left off: "off" means songs do not go to that screen at all, which is a
 * different question from which language they are in, and turning it back on from here would put a
 * song on a screen the operator deliberately kept clear.
 */
internal fun AppSettings.withSongLanguage(target: SongStyleTarget, language: String): AppSettings =
    mapSongModes(language) { profile ->
        if (target.isLowerThird) profile.isLowerThird else profile.displayMode == Constants.DISPLAY_MODE_FULLSCREEN
    }

/** True when any profile that is showing songs is showing two languages. */
internal val AppSettings.songIsBilingual: Boolean
    get() = projectionSettings.outputProfiles.any {
        it.songMode != Constants.SONG_LANG_OFF && it.songMode != Constants.SONG_LANG_PRIMARY
    }

/**
 * Every profile set to one language or two -- the coarse switch in the rail.
 *
 * Bilingual restores "both" rather than any previous per-profile choice, and Single writes
 * "primary" over a "secondary" profile as well: this is the control that says how many languages
 * the church is presenting in, and the per-target one on the element row is where a finer answer
 * is given.
 */
internal fun AppSettings.withSongBilingual(bilingual: Boolean): AppSettings =
    mapSongModes(if (bilingual) Constants.SONG_LANG_BOTH else Constants.SONG_LANG_PRIMARY) { true }

private fun AppSettings.mapSongModes(
    language: String,
    matches: (OutputProfile) -> Boolean,
): AppSettings = copy(
    projectionSettings = projectionSettings.copy(
        outputProfiles = projectionSettings.outputProfiles.map { profile ->
            if (profile.songMode != Constants.SONG_LANG_OFF && matches(profile)) {
                profile.copy(songMode = language)
            } else {
                profile
            }
        },
    ),
)

/** Wide enough for "Language 2" without an ellipsis. */
private val LANGUAGE_BUTTON_WIDTH = 88.dp

/** The All / Language 1-4 switch for [target]'s output, shared by both views of the tab. */
@Composable
internal fun SongLanguageScopeButtons(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    target: SongStyleTarget,
    /**
     * One output's own mode, for the per-output Customize dialog.
     *
     * Without it this control speaks for *every* output of [target]'s shape, through
     * [withSongLanguage], which writes `projectionSettings.screenAssignments`. That is right on the
     * global tab and wrong twice over in the dialog: the dialog stores only the difference between
     * two `SongSettings`, so a change to the assignments is never carried out of it -- the dialog's
     * own preview reads the edited draft and looked right while the screen kept showing both
     * languages -- and even if it were, it would have been applied to every screen rather than to
     * the one being customized.
     */
    outputMode: String? = null,
    onOutputModeChange: ((String) -> Unit)? = null,
) {
    SegmentedButton(
        items = listOf(
            SegmentedButtonItem(Constants.SONG_LANG_BOTH, stringResource(Res.string.song_language_both)),
            SegmentedButtonItem(Constants.SONG_LANG_PRIMARY, stringResource(Res.string.song_language_primary)),
            SegmentedButtonItem(Constants.SONG_LANG_SECONDARY, stringResource(Res.string.song_language_secondary)),
            SegmentedButtonItem(Constants.SONG_LANG_THIRD, stringResource(Res.string.song_language_third)),
            SegmentedButtonItem(Constants.SONG_LANG_FOURTH, stringResource(Res.string.song_language_fourth)),
        ),
        selectedValue = outputMode ?: settings.songLanguageFor(target),
        onValueChange = { lang ->
            if (onOutputModeChange != null) {
                onOutputModeChange(lang)
            } else {
                onSettingsChange { s -> s.withSongLanguage(target, lang) }
            }
        },
        buttonWidth = LANGUAGE_BUTTON_WIDTH,
        buttonHeight = 30.dp,
        fontSize = MaterialTheme.typography.labelSmall.fontSize,
    )
}
