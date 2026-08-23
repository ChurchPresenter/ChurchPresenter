package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.bible.Bible
import org.churchpresenter.ui.tidyPreviewLines

/** The verse every translation has, and the one nobody has to read to recognise. */
private const val PREVIEW_BOOK = 1
private const val PREVIEW_CHAPTER = 1
private const val PREVIEW_VERSE = 1

/**
 * Genesis 1:1 out of [bibles], as the font preview box shows it.
 *
 * The picker itself lives in `:ui-components` and knows nothing about a Bible — it is handed lines
 * of text. This is the one place that turns loaded translations into those lines, so the widget
 * library does not have to depend on `:bible` to preview a font.
 */
fun previewLinesFrom(bibles: List<Bible>): List<String> = tidyPreviewLines(
    bibles.mapNotNull { it.getVerseDetails(PREVIEW_BOOK, PREVIEW_CHAPTER, PREVIEW_VERSE)?.second },
)
