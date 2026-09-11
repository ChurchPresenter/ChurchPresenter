package org.churchpresenter.app.churchpresenter.dialogs.tabs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which elements each Customize category offers, and which background surface a Background chip
 * stands for once the output's shape is known.
 */
class CustomizeElementMappingTest {

    // ── The chips a category offers ───────────────────────────────────────────

    @Test
    fun `the stage monitor offers no elements at all`() {
        assertTrue(
            customizeElements(CustomizePane.STAGE_MONITOR).isEmpty(),
            "its pane is a zone layout, not a set of styled elements",
        )
    }

    @Test
    fun `the Bible offers the verse and its reference`() {
        assertEquals(
            listOf(CustomizeElement.BIBLE_TEXT, CustomizeElement.BIBLE_REFERENCE),
            customizeElements(CustomizePane.BIBLE),
        )
    }

    @Test
    fun `Songs offers six elements, the title slide first`() {
        val elements = customizeElements(CustomizePane.SONGS)
        assertEquals(6, elements.size)
        assertEquals(CustomizeElement.SONG_TITLE_SLIDE, elements.first())
    }

    @Test
    fun `Songs offers both look-ahead elements`() {
        val elements = customizeElements(CustomizePane.SONGS)
        assertTrue(CustomizeElement.SONG_LOOK_AHEAD in elements)
        assertTrue(CustomizeElement.SONG_NEXT_SECTION in elements)
    }

    @Test
    fun `the Dictionary offers its four text blocks and the card behind them`() {
        assertEquals(
            listOf(
                CustomizeElement.DICTIONARY_WORD,
                CustomizeElement.DICTIONARY_REFERENCE,
                CustomizeElement.DICTIONARY_DEFINITION,
                CustomizeElement.DICTIONARY_KJV,
                CustomizeElement.DICTIONARY_CARD,
            ),
            customizeElements(CustomizePane.DICTIONARY),
        )
    }

    @Test
    fun `the Background category offers the three surfaces an output can draw`() {
        assertEquals(
            listOf(
                CustomizeElement.BACKGROUND_DEFAULT,
                CustomizeElement.BACKGROUND_BIBLE,
                CustomizeElement.BACKGROUND_SONG,
            ),
            customizeElements(CustomizePane.BACKGROUND),
        )
    }

    @Test
    fun `no element is offered by two categories`() {
        val seen = mutableListOf<CustomizeElement>()
        CustomizePane.entries.forEach { seen += customizeElements(it) }
        assertEquals(seen.size, seen.toSet().size, "an element belongs to exactly one category: $seen")
    }

    @Test
    fun `every element is offered somewhere`() {
        val offered = CustomizePane.entries.flatMap { customizeElements(it) }.toSet()
        assertEquals(
            CustomizeElement.entries.toSet(),
            offered,
            "an element no category lists could never be reached",
        )
    }

    // ── Which surface a Background chip writes ────────────────────────────────

    @Test
    fun `the Default chip writes the default surface of the output's shape`() {
        assertEquals(
            BackgroundScope.DEFAULT,
            CustomizeElement.BACKGROUND_DEFAULT.backgroundScope(lowerThird = false),
        )
        assertEquals(
            BackgroundScope.DEFAULT_LOWER_THIRD,
            CustomizeElement.BACKGROUND_DEFAULT.backgroundScope(lowerThird = true),
        )
    }

    @Test
    fun `the Bible chip writes the Bible surface of the output's shape`() {
        assertEquals(BackgroundScope.BIBLE, CustomizeElement.BACKGROUND_BIBLE.backgroundScope(false))
        assertEquals(
            BackgroundScope.BIBLE_LOWER_THIRD,
            CustomizeElement.BACKGROUND_BIBLE.backgroundScope(true),
        )
    }

    @Test
    fun `the Songs chip writes the Songs surface of the output's shape`() {
        assertEquals(BackgroundScope.SONG, CustomizeElement.BACKGROUND_SONG.backgroundScope(false))
        assertEquals(
            BackgroundScope.SONG_LOWER_THIRD,
            CustomizeElement.BACKGROUND_SONG.backgroundScope(true),
        )
    }

    @Test
    fun `every Background chip maps to a different surface on a given shape`() {
        for (lowerThird in listOf(false, true)) {
            val scopes = customizeElements(CustomizePane.BACKGROUND).map { it.backgroundScope(lowerThird) }
            assertEquals(scopes.size, scopes.toSet().size, "lowerThird=$lowerThird gave $scopes")
        }
    }

    @Test
    fun `the two shapes never share a surface`() {
        for (element in customizeElements(CustomizePane.BACKGROUND)) {
            assertFalse(
                element.backgroundScope(false) == element.backgroundScope(true),
                "$element must write a different surface per shape",
            )
        }
    }

    @Test
    fun `a chip that is not a Background one reads the default surface`() {
        assertEquals(BackgroundScope.DEFAULT, CustomizeElement.SONG_LYRICS.backgroundScope(false))
        assertEquals(BackgroundScope.DEFAULT_LOWER_THIRD, CustomizeElement.BIBLE_TEXT.backgroundScope(true))
    }

    @Test
    fun `every element the Background category offers maps onto a lower-third surface`() {
        val bandScopes = customizeElements(CustomizePane.BACKGROUND).map { it.backgroundScope(true) }
        assertTrue(bandScopes.all { it.lowerThird }, "a band's chips must all write band surfaces: $bandScopes")
    }

    @Test
    fun `every element the Background category offers maps onto a full-screen surface`() {
        val fullScopes = customizeElements(CustomizePane.BACKGROUND).map { it.backgroundScope(false) }
        assertTrue(fullScopes.none { it.lowerThird })
    }

    // ── The test handles the dialog publishes ─────────────────────────────────

    @Test
    fun `each element chip carries a tag naming the element`() {
        for (element in CustomizeElement.entries) {
            assertEquals("customize_element_${element.name}", elementChipTag(element.name))
        }
    }

    @Test
    fun `no two elements share a chip tag`() {
        val tags = CustomizeElement.entries.map { elementChipTag(it.name) }
        assertEquals(tags.size, tags.toSet().size)
    }

    // ── Which categories carry an override switch ─────────────────────────────

    @Test
    fun `every category can be overridden per output`() {
        assertTrue(CustomizePane.entries.all { it.hasOverride }, "each output styles each category itself")
    }

    @Test
    fun `the surfaces that offer a gradient are the two content bands`() {
        val offering = BackgroundScope.entries.filter { it.offersGradient }
        assertEquals(
            setOf(BackgroundScope.BIBLE_LOWER_THIRD, BackgroundScope.SONG_LOWER_THIRD),
            offering.toSet(),
        )
    }

    @Test
    fun `only the default full screen ends the inheritance chain`() {
        val rootless = BackgroundScope.entries.filter { it.inheritsFrom == null }
        assertEquals(listOf(BackgroundScope.DEFAULT), rootless)
    }

    @Test
    fun `a content surface falls through to the default of its own shape`() {
        assertEquals(BackgroundScope.DEFAULT, BackgroundScope.BIBLE.inheritsFrom)
        assertEquals(BackgroundScope.DEFAULT, BackgroundScope.SONG.inheritsFrom)
        assertEquals(BackgroundScope.DEFAULT_LOWER_THIRD, BackgroundScope.BIBLE_LOWER_THIRD.inheritsFrom)
        assertEquals(BackgroundScope.DEFAULT_LOWER_THIRD, BackgroundScope.SONG_LOWER_THIRD.inheritsFrom)
    }

    @Test
    fun `the default band falls through to the default full screen`() {
        assertEquals(BackgroundScope.DEFAULT, BackgroundScope.DEFAULT_LOWER_THIRD.inheritsFrom)
    }

    @Test
    fun `only the root surface has no inherit option to offer`() {
        for (scope in BackgroundScope.entries) {
            assertEquals(
                scope == BackgroundScope.DEFAULT,
                scope.inheritType == null,
                "$scope",
            )
        }
    }
}
