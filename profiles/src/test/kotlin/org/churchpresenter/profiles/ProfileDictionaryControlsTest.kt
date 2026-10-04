package org.churchpresenter.profiles

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.DictionarySettings
import org.churchpresenter.settings.OutputProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ProfileDictionaryControlsTest {

    private val d = DictionarySettings()

    private fun doc(ds: DictionarySettings = DictionarySettings(wordFontType = SENTINEL_FONT)) =
        profileDocument(profile = OutputProfile(dictionarySettings = ds))

    @Test
    fun `the word's face, color and shadow are written`() {
        val family = uniquelyNamedFont()
        profilesTab(doc()) { get ->
            openCustomizePane(CustomizePane.DICTIONARY)
            pickFont(SENTINEL_FONT, family)
            recolor(d.wordColor, "#123456")
            toggleCheckbox("Shadow")
            recolor(d.wordShadowColor, "#654321")
            retypeNumberField(d.wordShadowSize, 120)
            retypeNumberField(d.wordShadowOpacity, 40)
            val ds = get().profile().dictionarySettings
            assertEquals(family, ds.wordFontType)
            assertEquals("#123456", ds.wordColor)
            assertEquals("#654321", ds.wordShadowColor)
            assertEquals(120, ds.wordShadowSize)
            assertEquals(40, ds.wordShadowOpacity)
        }
    }

    @Test
    fun `the word's outline and backing, and the reference's backing, are written`() = profilesTab(doc()) { get ->
        openCustomizePane(CustomizePane.DICTIONARY)
        onNodeWithContentDescription("Outline").performScrollTo().performClick()
        waitForIdle()
        onNodeWithContentDescription("Text backing").performScrollTo().performClick()
        waitForIdle()
        tap(dictionaryPartTag(DictionaryPart.REFERENCE))
        onNodeWithContentDescription("Text backing").performScrollTo().performClick()
        waitForIdle()
        val ds = get().profile().dictionarySettings
        assertNotEquals(d.wordOutline, ds.wordOutline)
        assertNotEquals(d.wordBackdrop, ds.wordBackdrop)
        assertNotEquals(d.referenceBackdrop, ds.referenceBackdrop)
    }

    @Test
    fun `the card's color is written`() = profilesTab(doc()) { get ->
        openCustomizePane(CustomizePane.DICTIONARY)
        recolor(d.cardBackgroundColor, "#202020")
        assertEquals("#202020", get().profile().dictionarySettings.cardBackgroundColor)
    }

    @Test
    fun `a part's text box and the box options are written`() = profilesTab(doc()) { get ->
        openCustomizePane(CustomizePane.DICTIONARY)
        toggleCheckbox("Text box")
        typeInRow("Box position", 30, nth = 0)
        toggleCheckbox("Keep clear of other boxes")
        val ds = get().profile().dictionarySettings
        val box = ds.textBoxes.getValue(DictionaryPart.WORD.name)
        assertTrue(box.enabled)
        assertEquals(30f, box.xPercent)
        assertNotEquals(DictionarySettings().textBoxOptions, ds.textBoxOptions)
    }
}
