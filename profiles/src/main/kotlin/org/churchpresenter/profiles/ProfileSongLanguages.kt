package org.churchpresenter.profiles

import androidx.compose.runtime.Composable
import org.churchpresenter.settings.DEFAULT_STACKED_LANGUAGE_GAP
import org.churchpresenter.settings.LANGUAGE_GAP_RANGE
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.profile_bible_looks_follow
import org.churchpresenter.strings.generated.resources.profile_bible_looks_follow_place
import org.churchpresenter.strings.generated.resources.profile_fit_languages
import org.churchpresenter.strings.generated.resources.profile_fit_languages_each
import org.churchpresenter.strings.generated.resources.profile_fit_languages_same
import org.churchpresenter.strings.generated.resources.profile_fit_languages_sub
import org.churchpresenter.strings.generated.resources.profile_language_gap
import org.churchpresenter.strings.generated.resources.profile_language_gap_sub
import org.churchpresenter.strings.generated.resources.profile_layout
import org.churchpresenter.strings.generated.resources.profile_song_looks_follow_language
import org.churchpresenter.strings.generated.resources.profile_song_looks_follow_language_hint
import org.churchpresenter.strings.generated.resources.profile_song_looks_follow_place_hint
import org.churchpresenter.strings.generated.resources.unit_px
import org.jetbrains.compose.resources.stringResource

/** Where a profile's song looks follow the place or the language. */
internal val SONG_LOOKS_FOLLOW_PATHS = listOf("songSettings.layoutExtras.languageLooksFollowPlace")

/**
 * FOLLOW: whether this output's song language looks stay with their place in the Songs tab's display
 * order when a language is moved, or go with the language -- `SongLayoutExtras.languageLooksFollowPlace`.
 * The song counterpart of the Bible page's [LooksFollowRow].
 */
@Composable
internal fun SongLooksFollowRow(followsPlace: Boolean, onChange: (Boolean) -> Unit) {
    SettingsRow(
        Res.string.profile_bible_looks_follow,
        sub = stringResource(
            if (followsPlace) {
                Res.string.profile_song_looks_follow_place_hint
            } else {
                Res.string.profile_song_looks_follow_language_hint
            },
        ),
        paths = SONG_LOOKS_FOLLOW_PATHS,
    ) {
        RowSegmented(
            options = listOf(
                RowOption(true, stringResource(Res.string.profile_bible_looks_follow_place)),
                RowOption(false, stringResource(Res.string.profile_song_looks_follow_language)),
            ),
            selected = followsPlace,
            onSelect = onChange,
        )
    }
}

/** The rows that arrange two or more languages: the layout, the gap between them and how they are fitted. */
@Composable
internal fun SongLanguageLayoutRows(song: SongSettings, updateSong: ((SongSettings) -> SongSettings) -> Unit) {
    SettingsRow(Res.string.profile_layout, paths = SONG_LAYOUT_PATHS) {
        RowSegmented(
            options = bilingualLayoutRowOptions(),
            selected = song.bilingualLayout,
            onSelect = { v -> updateSong { it.copy(bilingualLayout = v) } },
        )
    }
    SettingsRow(
        Res.string.profile_language_gap,
        sub = stringResource(Res.string.profile_language_gap_sub),
        paths = SONG_GAP_PATHS,
    ) {
        RowStepper(
            value = song.layoutExtras.languageGap ?: DEFAULT_STACKED_LANGUAGE_GAP,
            onValueChange = { v ->
                updateSong { it.copy(layoutExtras = it.layoutExtras.copy(languageGap = v)) }
            },
            range = LANGUAGE_GAP_RANGE,
            step = LANGUAGE_GAP_STEP,
            unit = stringResource(Res.string.unit_px),
            testTag = SONG_LANGUAGE_GAP_TAG,
        )
    }
    SettingsRow(
        Res.string.profile_fit_languages,
        sub = stringResource(Res.string.profile_fit_languages_sub),
        advanced = true,
        paths = SONG_FIT_LANGUAGES_PATHS,
    ) {
        RowSegmented(
            options = listOf(
                RowOption(false, stringResource(Res.string.profile_fit_languages_same)),
                RowOption(true, stringResource(Res.string.profile_fit_languages_each)),
            ),
            selected = song.layoutExtras.fitLanguagesSeparately,
            onSelect = { v ->
                updateSong { it.copy(layoutExtras = it.layoutExtras.copy(fitLanguagesSeparately = v)) }
            },
        )
    }
}
