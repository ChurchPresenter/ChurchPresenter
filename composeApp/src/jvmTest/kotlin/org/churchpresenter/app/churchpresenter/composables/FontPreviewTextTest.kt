package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.ui.FontPreviewText
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the font preview quotes: Genesis 1:1 out of whichever translations are loaded. */
class FontPreviewTextTest {

    @get:Rule
    val temp = TemporaryFolder()

    @AfterTest
    fun forgetVerses() = FontPreviewText.clear()

    private fun bible(genesisOneOne: String, title: String) = SpbFixture.loadedBible(
        temp.newFolder(title),
        SpbFixture.buildContent(
            title = title,
            books = listOf(SpbFixture.Book(1, "Genesis", 1)),
            verses = listOf(SpbFixture.Verse(1, 1, 1, genesisOneOne)),
        ),
    )

    @Test
    fun `nothing is quoted until a translation is loaded`() {
        assertTrue(FontPreviewText.lines.isEmpty())
    }

    @Test
    fun `each loaded translation contributes its own first verse`() {
        FontPreviewText.update(
            previewLinesFrom(
                listOf(
                    bible("In the beginning God created the heaven and the earth.", "KJV"),
                    bible("В начале сотворил Бог небо и землю.", "RST"),
                ),
            ),
        )

        assertEquals(
            listOf("In the beginning God created the heaven and the earth.", "В начале сотворил Бог небо и землю."),
            FontPreviewText.lines,
        )
    }

    @Test
    fun `two translations that read the same are quoted once`() {
        // Two English translations often carry Genesis 1:1 word for word, and a preview showing the
        // same line twice has spent half its height saying nothing.
        val same = "In the beginning God created the heaven and the earth."
        FontPreviewText.update(previewLinesFrom(listOf(bible(same, "KJV"), bible(same, "AKJV"))))

        assertEquals(listOf(same), FontPreviewText.lines)
    }

    @Test
    fun `a translation without Genesis contributes nothing rather than a blank line`() {
        val psalmsOnly = SpbFixture.loadedBible(
            temp.newFolder("Psalter"),
            SpbFixture.buildContent(
                title = "Psalter",
                books = listOf(SpbFixture.Book(19, "Psalms", 1)),
                verses = listOf(SpbFixture.Verse(19, 23, 1, "The LORD is my shepherd.")),
            ),
        )

        FontPreviewText.update(previewLinesFrom(listOf(psalmsOnly)))

        assertTrue(FontPreviewText.lines.isEmpty())
    }

    @Test
    fun `unloading every translation empties the preview again`() {
        FontPreviewText.update(previewLinesFrom(listOf(bible("In the beginning.", "KJV"))))

        FontPreviewText.update(previewLinesFrom(emptyList()))

        assertTrue(FontPreviewText.lines.isEmpty())
    }
}
