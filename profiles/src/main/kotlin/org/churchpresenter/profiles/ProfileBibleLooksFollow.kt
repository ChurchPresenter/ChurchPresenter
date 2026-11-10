package org.churchpresenter.profiles

import androidx.compose.runtime.Composable
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.profile_bible_looks_follow
import org.churchpresenter.strings.generated.resources.profile_bible_looks_follow_bible
import org.churchpresenter.strings.generated.resources.profile_bible_looks_follow_bible_hint
import org.churchpresenter.strings.generated.resources.profile_bible_looks_follow_place
import org.churchpresenter.strings.generated.resources.profile_bible_looks_follow_place_hint
import org.jetbrains.compose.resources.stringResource

/**
 * FOLLOW: whether this output's translation looks stay with their place in the stack when a Bible is
 * changed, swapped or moved, or go with the Bible -- `BibleLooksByPosition.kt` in `:settings`.
 */
@Composable
internal fun LooksFollowRow(followsPosition: Boolean, onChange: (Boolean) -> Unit) {
    SettingsRow(
        Res.string.profile_bible_looks_follow,
        sub = stringResource(
            if (followsPosition) {
                Res.string.profile_bible_looks_follow_place_hint
            } else {
                Res.string.profile_bible_looks_follow_bible_hint
            },
        ),
        paths = listOf("bibleSettings.looksFollowPosition"),
    ) {
        RowSegmented(
            options = listOf(
                RowOption(true, stringResource(Res.string.profile_bible_looks_follow_place)),
                RowOption(false, stringResource(Res.string.profile_bible_looks_follow_bible)),
            ),
            selected = followsPosition,
            onSelect = onChange,
        )
    }
}

private val DEFAULTS = BibleSettings()

/** Whether anything the Translations group's reset puts back differs from the defaults. */
internal fun BibleSettings.translationsGroupChanged(): Boolean =
    looksFollowPosition != DEFAULTS.looksFollowPosition ||
        bilingualLayout != DEFAULTS.bilingualLayout ||
        bilingualLayoutLowerThird != DEFAULTS.bilingualLayoutLowerThird ||
        multiTranslationDivider != DEFAULTS.multiTranslationDivider ||
        multiTranslationSpacing != DEFAULTS.multiTranslationSpacing ||
        splitLongVerses != DEFAULTS.splitLongVerses

/** [this] with the Translations group's values back at their defaults. */
internal fun BibleSettings.withTranslationsGroupReset(): BibleSettings = copy(
    looksFollowPosition = DEFAULTS.looksFollowPosition,
    bilingualLayout = DEFAULTS.bilingualLayout,
    bilingualLayoutLowerThird = DEFAULTS.bilingualLayoutLowerThird,
    multiTranslationDivider = DEFAULTS.multiTranslationDivider,
    multiTranslationSpacing = DEFAULTS.multiTranslationSpacing,
    splitLongVerses = DEFAULTS.splitLongVerses,
    longVerseWordCount = DEFAULTS.longVerseWordCount,
)
