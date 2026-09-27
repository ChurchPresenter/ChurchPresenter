package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongStyleElement
import org.churchpresenter.app.churchpresenter.dialogs.tabs.songShiftKey
import org.churchpresenter.app.churchpresenter.dialogs.tabs.translationElement
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.elementShift

/**
 * How far [element] is moved on [lowerThird]'s output, in output pixels: its own move, and -- for
 * one drawn once per language -- [language]'s on top of it. [titleSlide] for the title slide's own.
 */
internal fun SongSettings.elementMove(
    element: SongStyleElement,
    lowerThird: Boolean,
    language: Int?,
    titleSlide: Boolean = false,
): Pair<Int, Int> {
    val whole = elementShift(songShiftKey(element, lowerThird, titleSlide = titleSlide))
    val own = language?.takeIf { element.translationElement != null }
        ?.let { elementShift(songShiftKey(element, lowerThird, it, titleSlide)) }
    return (whole.x + (own?.x ?: 0)) to (whole.y + (own?.y ?: 0))
}

/**
 * [element] moved as [settings] says, and reporting where it was drawn -- under the key its move is
 * stored by, the language's own where it has one per language -- while a preview listens.
 * [scaleFactor] turns output pixels into this output's own.
 */
@Composable
internal fun Modifier.songElementMove(
    settings: SongSettings,
    element: SongStyleElement,
    lowerThird: Boolean,
    language: Int?,
    scaleFactor: Float,
    titleSlide: Boolean = false,
): Modifier {
    val (x, y) = settings.elementMove(element, lowerThird, language, titleSlide)
    val moved = if (x == 0 && y == 0) this else offset((x * scaleFactor).dp, (y * scaleFactor).dp)
    val key = songShiftKey(element, lowerThird, language, titleSlide)
    return moved.reportsBlock(PresentedBlock(PresentedBlock.Kind.ELEMENT, key))
}

/**
 * Lines of [element] drawn as one block that moves on its own. Left unwrapped when nothing moves it
 * and no preview is listening, so an output draws exactly what it drew before.
 */
@Composable
internal fun ColumnScope.SongElementLines(
    settings: SongSettings,
    element: SongStyleElement,
    lowerThird: Boolean,
    language: Int?,
    scaleFactor: Float,
    content: @Composable ColumnScope.() -> Unit,
) {
    val (x, y) = settings.elementMove(element, lowerThird, language)
    if (x == 0 && y == 0 && LocalPresentedBlocks.current == null) {
        content()
        return
    }
    Column(Modifier.fillMaxWidth().songElementMove(settings, element, lowerThird, language, scaleFactor)) { content() }
}
