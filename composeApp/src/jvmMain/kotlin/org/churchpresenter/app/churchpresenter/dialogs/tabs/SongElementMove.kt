package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.pixels_short
import churchpresenter.composeapp.generated.resources.profile_shift
import churchpresenter.composeapp.generated.resources.profile_shift_element_sub
import churchpresenter.composeapp.generated.resources.profile_shift_language_sub
import org.churchpresenter.settings.SongElementShift
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.elementShift
import org.churchpresenter.settings.songElementShiftKey
import org.churchpresenter.settings.withElementShift
import org.jetbrains.compose.resources.stringResource

/**
 * Where [element]'s move is stored on [lowerThird]'s output: the element as a whole, or -- for one
 * drawn once per language -- [language]'s own on top of it. The title on the title slide is its own:
 * moving it there must not move the title above every verse.
 */
internal fun songShiftKey(
    element: SongStyleElement,
    lowerThird: Boolean,
    language: Int? = null,
    titleSlide: Boolean = false,
): String {
    val name = if (titleSlide && element == SongStyleElement.TITLE) TITLE_SLIDE_TITLE_KEY else element.name
    return songElementShiftKey(name, lowerThird, language.takeIf { element.translationElement != null })
}

private const val TITLE_SLIDE_TITLE_KEY = "TITLE_SLIDE_TITLE"

/** [key]'s move as the handles and rows read it, x to y. */
internal fun SongSettings.shiftAt(key: String): Pair<Int, Int> = elementShift(key).let { it.x to it.y }

/** [this] with [key] moved to [x], [y]. */
internal fun SongSettings.shiftedAt(key: String, x: Int, y: Int): SongSettings =
    withElementShift(key, SongElementShift(x, y))

private val SHIFT_RANGE = -960..960

/** MOVE X / Y: the element the Text rows are pointed at -- or the picked language of it -- moved on its own. */
@Composable
internal fun SongMoveRow(
    song: SongSettings,
    key: String,
    language: Boolean,
    updateSong: ((SongSettings) -> SongSettings) -> Unit,
) {
    val (x, y) = song.shiftAt(key)
    val px = stringResource(Res.string.pixels_short)
    SettingsRow(
        stringResource(Res.string.profile_shift),
        sub = stringResource(
            if (language) Res.string.profile_shift_language_sub else Res.string.profile_shift_element_sub,
        ),
        advanced = true,
    ) {
        RowNumberField(
            x,
            { v -> updateSong { it.shiftedAt(key, v, y) } },
            SHIFT_RANGE,
            unit = "X $px",
            width = 72.dp,
            testTag = SHIFT_X_TAG,
        )
        RowNumberField(
            y,
            { v -> updateSong { it.shiftedAt(key, x, v) } },
            SHIFT_RANGE,
            unit = "Y $px",
            width = 72.dp,
            testTag = SHIFT_Y_TAG,
        )
    }
}
