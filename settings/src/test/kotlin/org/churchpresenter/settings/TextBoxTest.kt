package org.churchpresenter.settings

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A text box as settings keep it: off by default everywhere, its keys, and a map that stores only
 * the boxes that differ from an untouched one.
 */
class TextBoxTest {

    @Test
    fun `a box is off by default, so nothing moves until one is turned on`() {
        val box = TextBox()
        assertFalse(box.enabled)
        assertEquals(Constants.MIDDLE, box.vertical)
        assertEquals(TextBoxOverflow.SHRINK, box.overflow)
        assertFalse(box.fill)
    }

    @Test
    fun `its right and bottom edges are its corner plus its size`() {
        val box = TextBox(xPercent = 10f, yPercent = 20f, widthPercent = 30f, heightPercent = 40f)
        assertEquals(40f, box.rightPercent)
        assertEquals(60f, box.bottomPercent)
    }

    @Test
    fun `a key names the item, then the language, then the lower third`() {
        assertEquals("LYRICS", textBoxKey("LYRICS", lowerThird = false))
        assertEquals("LYRICS#1", textBoxKey("LYRICS", lowerThird = false, language = "1"))
        assertEquals("LYRICS#1@LT", textBoxKey("LYRICS", lowerThird = true, language = "1"))
        assertEquals("TEXT#kjv.spb@LT", textBoxKey(BIBLE_TEXT_BOX, lowerThird = true, language = "kjv.spb"))
    }

    @Test
    fun `a box nobody stored reads as an untouched one`() {
        assertEquals(TextBox(), emptyMap<String, TextBox>().boxAt("TITLE"))
        val stored = TextBox(enabled = true)
        assertEquals(stored, mapOf("TITLE" to stored).boxAt("TITLE"))
    }

    @Test
    fun `writing an untouched box drops it rather than storing it`() {
        val on = TextBox(enabled = true)
        val withOne = emptyMap<String, TextBox>().withBox("TITLE", on)
        assertEquals(mapOf("TITLE" to on), withOne)
        assertTrue(withOne.withBox("TITLE", TextBox()).isEmpty())
    }

    @Test
    fun `boxes and their options survive a save and a load`() {
        val extras = SongLayoutExtras(
            textBoxes = mapOf("LYRICS#0" to TextBox(enabled = true, overflow = TextBoxOverflow.CUT, fill = true)),
            textBoxOptions = TextBoxOptions(
                insideMargins = true,
                sharedLanguageBox = true,
                keepClear = true,
                snap = false,
            ),
        )
        val json = Json { encodeDefaults = true }
        assertEquals(extras, json.decodeFromString<SongLayoutExtras>(json.encodeToString(extras)))
    }

    @Test
    fun `the options default to what a page without boxes already does`() {
        val options = TextBoxOptions()
        assertFalse(options.insideMargins)
        assertFalse(options.lowerThirdWholeScreen)
        assertFalse(options.sharedLanguageBox)
        assertFalse(options.keepClear)
        assertTrue(options.snap)
    }

    @Test
    fun `the language gap keeps each layout's old spacing until it is set`() {
        val unset = SongLayoutExtras()
        assertEquals(DEFAULT_STACKED_LANGUAGE_GAP, unset.stackedLanguageGap())
        assertEquals(0, unset.sideBySideLanguageGap())
        val set = SongLayoutExtras(languageGap = 40)
        assertEquals(40, set.stackedLanguageGap())
        assertEquals(40, set.sideBySideLanguageGap())
    }

    @Test
    fun `a content region moves the background until told not to`() {
        assertTrue(ContentRegion().movesBackground)
        assertFalse(ContentRegion(movesBackground = false).movesBackground)
    }
}
