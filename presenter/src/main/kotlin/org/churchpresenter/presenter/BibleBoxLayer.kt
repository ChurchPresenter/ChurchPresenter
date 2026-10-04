package org.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.churchpresenter.sharedui.composables.OutlinedText
import org.churchpresenter.sharedui.utils.Utils.parseHexColor
import org.churchpresenter.sharedui.utils.Utils.systemFontFamilyOrDefault
import org.churchpresenter.sharedui.utils.combinedTextDecoration
import org.churchpresenter.sharedui.utils.spacingEm
import org.churchpresenter.sharedui.utils.styledDisplayText
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.BIBLE_REFERENCE_BOX
import org.churchpresenter.settings.BIBLE_TEXT_BOX
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.boxAt
import org.churchpresenter.settings.textBoxKey
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.presenter.BoxFitText
import org.churchpresenter.sharedui.presenter.BoxedItem
import org.churchpresenter.sharedui.presenter.clearOf
import org.churchpresenter.sharedui.presenter.fitInBox
import org.churchpresenter.sharedui.presenter.rectIn

/** The item name [this] element's boxes are stored under. */
private val BibleStyleElement.boxItem: String
    get() = if (this == BibleStyleElement.REFERENCE) BIBLE_REFERENCE_BOX else BIBLE_TEXT_BOX

/**
 * The key [element]'s box is stored under for the translation in [fileName] on [lowerThird]'s
 * output -- one per translation, unless the page has its translations share one box.
 */
fun BibleSettings.bibleBoxKey(element: BibleStyleElement, lowerThird: Boolean, fileName: String): String =
    textBoxKey(element.boxItem, lowerThird, fileName.takeUnless { textBoxOptions.sharedLanguageBox })

/** Whether [item]'s [element] is drawn in a box of its own on [lowerThird]'s output, rather than laid out. */
internal fun BibleSettings.isBoxed(
    item: BibleTranslationSettings,
    element: BibleStyleElement,
    lowerThird: Boolean,
): Boolean =
    textBoxes.boxAt(bibleBoxKey(element, lowerThird, item.fileName)).enabled

/** One boxed verse text or reference: where it goes, what it says, and whose look it takes. */
internal data class BibleBoxItem(
    val key: String,
    val box: TextBox,
    val element: BibleStyleElement,
    val item: BibleTranslationSettings,
    val text: String,
)

/**
 * What a slide draws in boxes: each shown translation's boxed verse text and reference. Where the
 * translations share one box, the first's look draws all of their text in it, one after another.
 */
internal fun bibleBoxItems(
    settings: BibleSettings,
    lowerThird: Boolean,
    shown: List<Pair<SelectedVerse, BibleTranslationSettings>>,
): List<BibleBoxItem> {
    val items = mutableListOf<BibleBoxItem>()
    BibleStyleElement.entries.forEach { element ->
        val parts = shown.mapNotNull { (verse, item) ->
            val key = settings.bibleBoxKey(element, lowerThird, item.fileName)
            val box = settings.textBoxes.boxAt(key)
            if (!box.enabled) return@mapNotNull null
            val text = if (element == BibleStyleElement.REFERENCE) buildRefText(verse, item) else verse.verseText
            BibleBoxItem(key, box, element, item, text).takeIf { text.isNotBlank() }
        }
        if (settings.textBoxOptions.sharedLanguageBox && parts.size > 1) {
            items += parts.first().copy(text = parts.joinToString("\n\n") { it.text })
        } else {
            items += parts
        }
    }
    return items
}

/**
 * Every boxed verse text and reference of one slide, drawn over it at [alpha] -- the Bible twin of
 * [SongBoxLayer], measured and placed the same way.
 */
@Composable
internal fun BibleBoxLayer(
    items: List<BibleBoxItem>,
    settings: BibleSettings,
    lowerThird: Boolean,
    area: Rect,
    origin: Offset,
    scaleFactor: Float,
    alpha: Float,
    isKey: Boolean,
    shadowOf: (color: String, size: Int, opacity: Int) -> Shadow,
) {
    if (items.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val raw = items.map { it.box.rectIn(area) }
    val rects = if (!settings.textBoxOptions.keepClear) raw else raw.mapIndexed { i, rect ->
        rect.clearOf(raw.filterIndexed { j, _ -> j != i })
    }
    val target = if (lowerThird) BibleStyleTarget.LOWER_THIRD else BibleStyleTarget.FULL_SCREEN
    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }) {
        items.forEachIndexed { index, boxed ->
            val look = boxed.item.elementStyle(boxed.element, target)
            val rect = rects[index]
            val letterEm = spacingEm(look.letterSpacing, look.fontSize)
            val wordEm = spacingEm(look.wordSpacing, look.fontSize)
            val text = styledDisplayText(boxed.text, look.transform, letterEm, wordEm)
            val style = boxTextStyle(look, letterEm, shadowOf)
            val fontFamily = systemFontFamilyOrDefault(look.fontType)
            val size = remember(boxed, rect, look, scaleFactor) {
                fitInBox(
                    measurer = measurer,
                    fitText = BoxFitText(text, style.copy(shadow = null, fontFamily = fontFamily), look.fontSize),
                    box = boxed.box,
                    room = IntSize((rect.width / scaleFactor).toInt(), (rect.height / scaleFactor).toInt()),
                )
            }
            BoxedItem(rect.translate(-origin.x, -origin.y), boxed.box, look.horizontalAlignment, boxed.key) {
                OutlinedText(
                    text = text,
                    outline = look.outline.let { if (isKey && it.isVisible) it.copy(color = "#FFFFFF") else it },
                    scaleFactor = scaleFactor,
                    color = if (isKey) Color.White else parseHexColor(look.color),
                    fontSize = (size * scaleFactor).sp,
                    style = style,
                    fontFamily = fontFamily,
                    textAlign = when (look.horizontalAlignment) {
                        Constants.LEFT -> TextAlign.Start
                        Constants.RIGHT -> TextAlign.End
                        else -> TextAlign.Center
                    },
                )
            }
        }
    }
}

/** The text style [look] draws with, its shadow scaled as the presenter scales every shadow. */
private fun boxTextStyle(
    look: BibleElementStyle,
    letterEm: Float,
    shadowOf: (color: String, size: Int, opacity: Int) -> Shadow,
): TextStyle = TextStyle(
    fontWeight = if (look.bold) FontWeight.Bold else FontWeight.Normal,
    fontStyle = if (look.italic) FontStyle.Italic else FontStyle.Normal,
    textDecoration = combinedTextDecoration(look.underline, look.strikethrough),
    letterSpacing = letterEm.em,
    shadow = if (look.shadow) shadowOf(look.shadowColor, look.shadowSize, look.shadowOpacity) else null,
)
