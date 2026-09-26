package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.pixels_short
import churchpresenter.composeapp.generated.resources.profile_shift
import churchpresenter.composeapp.generated.resources.profile_shift_language_sub
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.translationSettings
import org.churchpresenter.settings.withTranslationSettings
import org.jetbrains.compose.resources.stringResource

/**
 * What the Songs Text group is pointed at -- one element, on this output's shape, for all languages
 * or one -- and how it writes.
 *
 * Under several languages the first language stands for All: every other language follows it until
 * it has a look of its own, and an All edit reaches such a language wherever it still matches. A
 * class rather than local functions, for the reason [ProfileSongsPage] gives.
 */
internal class SongEdit(
    val song: SongSettings,
    val element: SongStyleElement,
    val target: SongStyleTarget,
    val language: SongStyleLanguage,
    /** Two or more languages reach this output and [element] has a look per language. */
    val perLanguage: Boolean,
    private val updateSong: ((SongSettings) -> SongSettings) -> Unit,
) {
    val style: SongElementStyle = song.elementStyle(element, target, language)

    /** One language other than the first is being edited, rather than All. */
    val picked: Boolean get() = perLanguage && language.isTranslation

    /** [edited] written for the language being edited, or for all of them. */
    fun write(edited: SongElementStyle) {
        updateSong { it.written(edited) }
    }

    /** [this] with [edited] written the way [write] writes it. */
    fun SongSettings.written(edited: SongElementStyle): SongSettings =
        if (perLanguage && !language.isTranslation) withAllLanguagesStyle(element, target, edited)
        else withElementStyle(element, target, language, edited)

    /** The picked language as the rows mark its own values; null under All. */
    @Composable
    fun styleTarget(): StyleTarget? {
        if (!picked) return null
        return StyleTarget(
            label = language.nameLabel(song),
            entryPath = languagePath(language),
            ownKeys = song.languageOwnFields(element, target, language),
            onClear = { fields -> updateSong { it.clearLanguageOwn(element, target, language, fields) } },
        )
    }

    /**
     * Which of the picked language's own fields each Text row writes, as paths under
     * [languagePath] -- for its "Only Ukrainian ×" chip. Found the way [probeTextLookPaths] finds a
     * linked profile's, one level down: which fields of the style each row changes.
     */
    @Composable
    fun targetPaths(): TextLookPaths {
        if (!picked) return TextLookPaths.NONE
        val look = style.toLook(element)
        return remember(element, target, language) {
            TextLookPaths(
                TextLookField.entries.associateWith { field ->
                    songStyleFieldsChanged(style, style.withLook(look.perturbed(field)))
                        .map { "${languagePath(language)}.$it" }
                },
            )
        }
    }

    /** The picked language moved on its own, on this output's shape. */
    fun shift(x: Int, y: Int) {
        val lowerThird = target.isLowerThird
        updateSong { song ->
            song.withTranslationSettings(language.translation - 1) {
                if (lowerThird) it.copy(lowerThirdShiftX = x, lowerThirdShiftY = y) else it.copy(shiftX = x, shiftY = y)
            }
        }
    }

    val shiftNow: Pair<Int, Int>
        get() = song.translationSettings(language.translation - 1).shiftFor(target.isLowerThird)
}

/** Where a song language's own values are addressed for the Text rows' chip. */
private fun languagePath(language: SongStyleLanguage): String = "songLanguage[${language.translation}]"

private val SHIFT_RANGE = -960..960

/** MOVE X / Y: the picked language's lines moved on their own, in output pixels. */
@Composable
internal fun SongShiftRow(edit: SongEdit) {
    val (x, y) = edit.shiftNow
    val px = stringResource(Res.string.pixels_short)
    SettingsRow(
        stringResource(Res.string.profile_shift),
        sub = stringResource(Res.string.profile_shift_language_sub),
        advanced = true,
    ) {
        RowNumberField(x, { v -> edit.shift(v, y) }, SHIFT_RANGE, unit = "X $px", width = 72.dp, testTag = SHIFT_X_TAG)
        RowNumberField(y, { v -> edit.shift(x, v) }, SHIFT_RANGE, unit = "Y $px", width = 72.dp, testTag = SHIFT_Y_TAG)
    }
}
