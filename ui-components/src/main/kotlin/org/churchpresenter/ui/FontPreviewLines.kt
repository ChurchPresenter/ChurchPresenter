package org.churchpresenter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.font_preview_sample
import org.jetbrains.compose.resources.stringResource

/** Two or three lines fill the preview box; a fourth only makes each one shorter. */
private const val PREVIEW_LINES = 3

/**
 * The Cyrillic the preview falls back to when there is no Bible to quote.
 *
 * Letters, not a sentence: it is here to show whether a family has the glyphs, so it is a specimen
 * of the script rather than UI copy — nothing to translate, and nothing that changes with the
 * interface language.
 */
internal const val CYRILLIC_SPECIMEN = "Аа Бб Вв Гг Дд Ее Жж Зз"

/**
 * Genesis 1:1 out of the translations already loaded, which is what the font picker previews.
 *
 * Kept here rather than passed down because the picker appears at twenty-odd call sites, most of
 * them layers inside a settings tab or a dialog window of its own, and none of them may be handed a
 * ViewModel to read the Bible from. `MainDesktop` fills it in as translations load; everything else
 * only reads it.
 *
 * Real verses rather than a specimen because that is the text the operator will actually project —
 * a family that cannot draw their Cyrillic shows it here, in their own Bible, rather than in a
 * sample sentence they have no reason to care about.
 */
object FontPreviewText {

    var lines: List<String> by mutableStateOf(emptyList())
        private set

    /** Keeps [verses] as the preview, dropping blanks and the ones that read the same. */
    fun update(verses: List<String>) {
        lines = tidyPreviewLines(verses)
    }

    /** Forgets the verses. Tests use it to start from a known state. */
    fun clear() {
        lines = emptyList()
    }
}

/**
 * The verses as the box shows them: trimmed, no blanks, and no two translations that read the same.
 *
 * Two translations of the same language often carry Genesis 1:1 word for word, and a preview that
 * shows one line twice has spent half its height saying nothing.
 */
fun tidyPreviewLines(verses: List<String>): List<String> = verses
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .distinct()
    .take(PREVIEW_LINES)

/** The preview lines, or a specimen of each script while no Bible has been loaded. */
@Composable
internal fun fontPreviewLines(): List<String> = FontPreviewText.lines.ifEmpty {
    listOf(stringResource(Res.string.font_preview_sample), CYRILLIC_SPECIMEN)
}
