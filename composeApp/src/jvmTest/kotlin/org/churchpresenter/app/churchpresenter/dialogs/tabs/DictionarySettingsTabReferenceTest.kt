@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.ui.SENTINEL_FONT
import org.churchpresenter.ui.assertColorFieldShows
import org.churchpresenter.ui.pickFont
import org.churchpresenter.ui.recolor
import org.churchpresenter.ui.retypeNumberField
import org.churchpresenter.ui.styleButton
import org.churchpresenter.ui.uniquelyNamedFont

/**
 * Drives every control in the Reference & Transliteration section.
 *
 * It looks like the Word section but is not: `DictionarySettings` has no bold or italic flag for the
 * reference text, so the tab passes `bold = false, italic = false, underline = false` with empty
 * callbacks and only wires the shadow. Three of the four style buttons are therefore decoration —
 * always drawn unselected, clickable, and doing nothing. That is asserted here rather than skipped,
 * so wiring any of them up later fails this class and gets a real test.
 */
class DictionarySettingsTabReferenceTest {

    // ── Show ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the show switch turns the reference off and back on`() = dictionaryTab { get ->
        switch(Switches.SHOW_REFERENCE).assertIsOn()

        switch(Switches.SHOW_REFERENCE).performScrollTo().performClick()
        waitForIdle()
        assertEquals(false, get().dictionarySettings.showReference, "switching off must be stored")
        switch(Switches.SHOW_REFERENCE).assertIsOff()
        assertTrue(get().dictionarySettings.showKjvUsage, "the KJV switch must be untouched")

        switch(Switches.SHOW_REFERENCE).performClick()
        waitForIdle()
        assertEquals(true, get().dictionarySettings.showReference, "switching back on must be stored too")
        switch(Switches.SHOW_REFERENCE).assertIsOn()
    }

    // ── Colour ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the reference colour field stores the confirmed hex`() {
        dictionaryTab(initial = dictionarySettings { copy(referenceColor = "#334455") }) { get ->
            recolor(fromHex = "#334455", toHex = "#AABBCC")
            assertTrue(
                get().dictionarySettings.referenceColor.equals("#AABBCC", ignoreCase = true),
                "the confirmed hex must become the reference colour",
            )
            assertColorFieldShows("#AABBCC", "the reference colour field")
            assertEquals("#FFFFFF", get().dictionarySettings.wordColor, "the word colour must be untouched")
        }
    }

    // ── The three dead style buttons ────────────────────────────────────────────────────────────

    @Test
    fun `the reference bold italic and underline buttons are rendered but wired to nothing`() =
        dictionaryTab { get ->
            // First prove the group is live, so "B/I/U changed nothing" is a statement about those
            // buttons rather than about the tab: S sits in the same TextStyleButtons and does write.
            styleButton(DictStyleGroup.REFERENCE, "S").performScrollTo().performClick()
            waitForIdle()
            assertEquals(
                true,
                get().dictionarySettings.referenceShadow,
                "the group must be wired up for the rest of this test to mean anything",
            )

            for (label in listOf("B", "I", "U")) {
                val before = get().dictionarySettings
                styleButton(DictStyleGroup.REFERENCE, label).performClick()
                waitForIdle()
                assertEquals(
                    before,
                    get().dictionarySettings,
                    "the reference section stores no $label flag, so clicking it must change nothing",
                )
            }
        }

    // ── Shadow ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the reference shadow button stores the flag and reveals the shadow row`() = dictionaryTab { get ->
        assertEquals(false, get().dictionarySettings.referenceShadow, "no shadow out of the box")
        onAllNodesWithText("SIZE (%)").assertCountEquals(0)

        styleButton(DictStyleGroup.REFERENCE, "S").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().dictionarySettings.referenceShadow, "clicking S must be stored")
        onAllNodesWithText("SIZE (%)").assertCountEquals(1)
        onAllNodesWithText("INTENSITY (%)").assertCountEquals(1)
        assertEquals(false, get().dictionarySettings.wordShadow, "the word shadow must be untouched")

        styleButton(DictStyleGroup.REFERENCE, "S").performClick()
        waitForIdle()
        assertEquals(false, get().dictionarySettings.referenceShadow, "clicking S again must clear it")
        onAllNodesWithText("SIZE (%)").assertCountEquals(0)
    }

    @Test
    fun `the reference shadow colour stores the confirmed hex`() {
        dictionaryTab(
            initial = dictionarySettings { copy(referenceShadow = true, referenceShadowColor = "#0A0B0C") },
        ) { get ->
            recolor(fromHex = "#0A0B0C", toHex = "#0C0B0A")
            assertTrue(
                get().dictionarySettings.referenceShadowColor.equals("#0C0B0A", ignoreCase = true),
                "the confirmed hex must become the reference shadow colour",
            )
            assertColorFieldShows("#0C0B0A", "the reference shadow colour field")
        }
    }

    @Test
    fun `the reference shadow size stores a new percentage`() {
        dictionaryTab(initial = dictionarySettings { copy(referenceShadow = true, referenceShadowSize = 133) }) { get ->
            retypeNumberField(showing = 133, to = 60)
            assertEquals(60, get().dictionarySettings.referenceShadowSize, "the typed size must be stored")
            assertEquals(90, get().dictionarySettings.referenceShadowOpacity, "the intensity must be untouched")
        }
    }

    @Test
    fun `the reference shadow intensity stores a new percentage`() {
        dictionaryTab(
            initial = dictionarySettings { copy(referenceShadow = true, referenceShadowOpacity = 66) },
        ) { get ->
            retypeNumberField(showing = 66, to = 25)
            assertEquals(25, get().dictionarySettings.referenceShadowOpacity, "the typed intensity must be stored")
            assertEquals(100, get().dictionarySettings.referenceShadowSize, "the size must be untouched")
        }
    }

    /**
     * Both shadow rows publish identically captioned fields, so with both on the fixture has to give
     * the one under test a value the other three do not hold. This drives the reference row while
     * the word row is also on screen, which is the arrangement a mis-wired callback would show up in.
     */
    @Test
    fun `the reference shadow row writes its own settings while the word row is also on screen`() {
        dictionaryTab(
            initial = dictionarySettings {
                copy(
                    wordShadow = true, wordShadowSize = 101, wordShadowOpacity = 91,
                    referenceShadow = true, referenceShadowSize = 102, referenceShadowOpacity = 92,
                )
            },
        ) { get ->
            retypeNumberField(showing = 102, to = 55)
            val ds = get().dictionarySettings
            assertEquals(55, ds.referenceShadowSize, "the reference size must take the typed value")
            assertEquals(101, ds.wordShadowSize, "the word size must be untouched")
            assertEquals(91, ds.wordShadowOpacity, "as must the word intensity")
            assertEquals(92, ds.referenceShadowOpacity, "and the reference intensity")
        }
    }

    // ── Font ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the reference font dropdown stores the picked family`() {
        val target = uniquelyNamedFont()
        dictionaryTab(initial = dictionarySettings { copy(referenceFontType = SENTINEL_FONT) }) { get ->
            pickFont(showing = SENTINEL_FONT, to = target)
            assertEquals(target, get().dictionarySettings.referenceFontType, "the picked family must be stored")
            assertEquals("Arial", get().dictionarySettings.wordFontType, "the word font must be untouched")
        }
    }

    @Test
    fun `the reference font size stores a new value`() = dictionaryTab { get ->
        assertEquals(28, get().dictionarySettings.referenceFontSize, "28 out of the box")
        retypeNumberField(showing = 28, to = 44)
        assertEquals(44, get().dictionarySettings.referenceFontSize, "the typed size must be stored")
        assertEquals(22, get().dictionarySettings.kjvUsageFontSize, "the KJV size must be untouched")
    }

    @Test
    fun `a reference font size outside the range is not stored`() = dictionaryTab { get ->
        retypeNumberField(showing = 28, to = 200)
        assertEquals(28, get().dictionarySettings.referenceFontSize, "200 is above the 8..120 range")

        // The accepted value proves the field was live for the rejected one too.
        retypeNumberField(showing = 200, to = 120)
        assertEquals(120, get().dictionarySettings.referenceFontSize, "120 is the top of the range and is accepted")
    }
}
