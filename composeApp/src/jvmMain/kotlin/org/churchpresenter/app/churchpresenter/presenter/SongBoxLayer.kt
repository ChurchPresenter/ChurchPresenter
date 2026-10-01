package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.churchpresenter.sharedui.composables.OutlinedText
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongStyleElement
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongStyleTarget
import org.churchpresenter.app.churchpresenter.dialogs.tabs.elementStyle
import org.churchpresenter.sharedui.utils.spacingEm
import org.churchpresenter.sharedui.utils.styledDisplayText
import org.churchpresenter.core.models.text.TextOutline
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.boxAt
import org.churchpresenter.settings.textBoxKey
import org.churchpresenter.settings.utils.Constants

/** The song elements drawn once per language, and so boxed once per language unless they share a box. */
internal val SongStyleElement.boxedPerLanguage: Boolean
    get() = this == SongStyleElement.LYRICS || this == SongStyleElement.LOOK_AHEAD ||
        this == SongStyleElement.NEXT_SECTION

/**
 * The key [element]'s box is stored under on [lowerThird]'s output -- with [language] for an element
 * drawn once per language, unless the page has its languages share one box.
 */
internal fun SongSettings.songBoxKey(
    element: SongStyleElement,
    lowerThird: Boolean,
    language: Int? = null,
): String {
    val own = language?.takeIf { element.boxedPerLanguage && !layoutExtras.textBoxOptions.sharedLanguageBox }
    return textBoxKey(element.name, lowerThird, own?.toString())
}

/** The prefix a title slide element's box is stored under, apart from the same element on a lyric slide. */
private const val TITLE_SLIDE_BOX_PREFIX = "SLIDE_"

/**
 * The key a title slide [element]'s box is stored under -- with [language] for a title line, which
 * the slide draws once per language, unless the page has its languages share one box.
 */
internal fun SongSettings.titleSlideBoxKey(
    element: SongStyleElement,
    lowerThird: Boolean,
    language: Int? = null,
): String {
    val own = language?.takeIf { element == SongStyleElement.TITLE && !layoutExtras.textBoxOptions.sharedLanguageBox }
    return textBoxKey(TITLE_SLIDE_BOX_PREFIX + element.name, lowerThird, own?.toString())
}

/** A title slide [element]'s box on [lowerThird]'s output. */
internal fun SongSettings.titleSlideBox(
    element: SongStyleElement,
    lowerThird: Boolean,
    language: Int? = null,
): TextBox =
    layoutExtras.textBoxes.boxAt(titleSlideBoxKey(element, lowerThird, language))

/**
 * The title slide's boxed lines: each [lines] entry whose box is on, the title carrying its number
 * ahead of it when the two share a line.
 */
internal fun titleSlideBoxItems(
    settings: SongSettings,
    lowerThird: Boolean,
    lines: List<TitleSlideLine>,
): List<SongBoxItem> =
    lines.mapNotNull { line ->
        val box = settings.titleSlideBox(line.element, lowerThird, line.language)
        if (!box.enabled || line.text.isBlank()) return@mapNotNull null
        val text = listOfNotNull(line.number, line.text).joinToString("  ")
        SongBoxItem(
            key = settings.titleSlideBoxKey(line.element, lowerThird, line.language),
            box = box,
            element = line.element,
            language = line.language,
            lines = listOf(text),
            fitLines = listOf(listOf(text)),
        )
    }

/** [element]'s box on [lowerThird]'s output, for [language] where it has one per language. */
internal fun SongSettings.songBox(
    element: SongStyleElement,
    lowerThird: Boolean,
    language: Int? = null,
): TextBox =
    layoutExtras.textBoxes.boxAt(songBoxKey(element, lowerThird, language))

/** Whether [element] is drawn in a box of its own on [lowerThird]'s output, rather than laid out. */
internal fun SongSettings.isBoxed(
    element: SongStyleElement,
    lowerThird: Boolean,
    language: Int? = null,
): Boolean =
    songBox(element, lowerThird, language).enabled

/**
 * One boxed element as a slide draws it: where it goes, what it says, and the look it says it in.
 *
 * [fitLines] are every set of lines the element's size must hold -- the slide's alone, or every
 * slide of the song when the song is fitted as a whole, so the size does not jump from slide to slide.
 */
internal data class SongBoxItem(
    val key: String,
    val box: TextBox,
    val element: SongStyleElement,
    val language: Int?,
    val lines: List<String>,
    val fitLines: List<List<String>>,
)

/** What the title row, the number and the section label say on a slide, each null where it shows nothing there. */
internal data class SongBoxTexts(val title: String?, val number: String?, val label: String?)

/**
 * One slide as its boxes see it: [blocks] are its languages as it slices them, [songBlocks] every
 * slide's, for fitting the song as a whole, and [texts] what its single elements say.
 */
internal data class SongBoxSlide(
    val blocks: List<SongLanguageBlock>,
    val songBlocks: List<List<SongLanguageBlock>>,
    val texts: SongBoxTexts,
)

/** What [slide] draws in boxes: every boxed element that has something to show on it. */
internal fun songBoxItems(
    settings: SongSettings,
    lowerThird: Boolean,
    lyricsElement: SongStyleElement,
    slide: SongBoxSlide,
): List<SongBoxItem> {
    val blocks = slide.blocks
    val songBlocks = slide.songBlocks
    val texts = slide.texts
    val items = mutableListOf<SongBoxItem>()
    fun single(element: SongStyleElement, text: String?) {
        val box = settings.songBox(element, lowerThird)
        if (box.enabled && !text.isNullOrBlank()) {
            val key = settings.songBoxKey(element, lowerThird)
            items += SongBoxItem(key, box, element, null, listOf(text), listOf(listOf(text)))
        }
    }
    single(SongStyleElement.TITLE, texts.title)
    single(SongStyleElement.NUMBER, texts.number)
    single(SongStyleElement.SECTION_LABEL, texts.label)
    fun perLanguage(element: SongStyleElement, linesOf: (SongLanguageBlock) -> List<String>) {
        val shared = settings.layoutExtras.textBoxOptions.sharedLanguageBox
        if (shared) {
            val box = settings.songBox(element, lowerThird)
            val lines = blocks.flatMap(linesOf)
            if (box.enabled && lines.isNotEmpty()) {
                val fit = songBlocks.map { slide -> slide.flatMap(linesOf) }.filter { it.isNotEmpty() }
                items += SongBoxItem(settings.songBoxKey(element, lowerThird), box, element, null, lines, fit)
            }
            return
        }
        blocks.forEach { block ->
            val box = settings.songBox(element, lowerThird, block.index)
            val lines = linesOf(block)
            if (box.enabled && lines.isNotEmpty()) {
                val fit = songBlocks
                    .mapNotNull { slide -> slide.firstOrNull { it.index == block.index }?.let(linesOf) }
                    .filter { it.isNotEmpty() }
                val key = settings.songBoxKey(element, lowerThird, block.index)
                items += SongBoxItem(key, box, element, block.index, lines, fit)
            }
        }
    }
    perLanguage(lyricsElement) { it.lines }
    perLanguage(SongStyleElement.NEXT_SECTION) { it.lookAheadLines }
    return items
}

/** The rectangles [items] occupy in [area], with Keep clear applied when the page asks for it. */
internal fun songBoxRects(items: List<SongBoxItem>, area: Rect, keepClear: Boolean): List<Rect> {
    val raw = items.map { it.box.rectIn(area) }
    if (!keepClear) return raw
    return raw.mapIndexed { i, rect -> rect.clearOf(raw.filterIndexed { j, _ -> j != i }) }
}

/**
 * Every boxed element of one slide, drawn over it at [alpha].
 *
 * [area] is what the boxes are measured against and [origin] is where this layer's own top-left
 * corner sits on the output, both in dp: the layer fills whatever it is placed in, so a box at the
 * output's edge is drawn at `rect - origin`. Fonts are fitted in reference points and scaled by
 * [scaleFactor], as every presenter measures.
 */
@Composable
internal fun SongBoxLayer(
    items: List<SongBoxItem>,
    settings: SongSettings,
    target: SongStyleTarget,
    area: Rect,
    origin: Offset,
    scaleFactor: Float,
    alpha: Float,
    isKey: Boolean,
    outlineOf: (TextOutline) -> TextOutline,
    shadowOf: (color: String, size: Int, opacity: Int) -> Shadow,
) {
    if (items.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val rects = songBoxRects(items, area, settings.layoutExtras.textBoxOptions.keepClear)
    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }) {
        items.forEachIndexed { index, item ->
            val profile = settings.elementStyle(item.element, target, item.language ?: 0)
            val styling = songLineStyling(profile, null, scaleFactor, isKey, shadowOf)
            val letterEm = spacingEm(profile.letterSpacing, profile.fontSize)
            val wordEm = spacingEm(profile.wordSpacing, profile.fontSize)
            val rect = rects[index]
            val fitStyle = TextStyle(
                fontFamily = styling.fontFamily,
                fontWeight = styling.textStyle.fontWeight,
                fontStyle = styling.textStyle.fontStyle,
                letterSpacing = letterEm.em,
            )
            val size = remember(item, rect, profile, scaleFactor) {
                item.fitLines.ifEmpty { listOf(item.lines) }.minOf { lines ->
                    fitInBox(
                        measurer = measurer,
                        fitText = BoxFitText(
                            text = styledDisplayText(lines.joinToString("\n"), profile.transform, letterEm, wordEm),
                            style = fitStyle,
                            configuredSize = profile.fontSize,
                            softWrap = settings.wordWrap,
                        ),
                        box = item.box,
                        room = IntSize((rect.width / scaleFactor).toInt(), (rect.height / scaleFactor).toInt()),
                    )
                }
            }
            BoxedItem(
                rect = rect.translate(-origin.x, -origin.y),
                box = item.box,
                horizontal = profile.horizontalAlignment,
                key = item.key,
            ) {
                OutlinedText(
                    text = styledDisplayText(item.lines.joinToString("\n"), profile.transform, letterEm, wordEm),
                    outline = outlineOf(profile.outline),
                    scaleFactor = scaleFactor,
                    color = styling.color,
                    fontSize = (size * scaleFactor).sp,
                    style = styling.textStyle,
                    fontFamily = styling.fontFamily,
                    textAlign = textAlignOf(profile.horizontalAlignment),
                    softWrap = settings.wordWrap,
                )
            }
        }
    }
}

private fun textAlignOf(alignment: String): TextAlign = when (alignment) {
    Constants.LEFT -> TextAlign.Start
    Constants.RIGHT -> TextAlign.End
    else -> TextAlign.Center
}
