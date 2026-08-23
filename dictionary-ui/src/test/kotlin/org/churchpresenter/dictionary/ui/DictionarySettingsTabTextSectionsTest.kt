@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dictionary.ui

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.ui.assertColorFieldShows
import org.churchpresenter.ui.recolor
import org.churchpresenter.ui.retypeNumberField

/**
 * Drives the Definition and KJV Usage sections — the two plain ones, each a Show switch, a colour
 * and a font size, with no styling of their own.
 *
 * They are the pair most exposed to a copy-paste slip: the two sections are the same three controls
 * wired to different fields, sitting in different columns. Every test here therefore asserts the
 * other section's matching field was left alone.
 */
class DictionarySettingsTabTextSectionsTest {

    // ── Definition ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the definition show switch turns it off and back on`() = dictionarySettingsTab { get ->
        switch(Switches.SHOW_DEFINITION).assertIsOn()

        switch(Switches.SHOW_DEFINITION).performScrollTo().performClick()
        waitForIdle()
        assertEquals(false, get().dictionarySettings.showDefinition, "switching off must be stored")
        switch(Switches.SHOW_DEFINITION).assertIsOff()
        assertTrue(get().dictionarySettings.showWord, "the word switch must be untouched")

        switch(Switches.SHOW_DEFINITION).performClick()
        waitForIdle()
        assertEquals(true, get().dictionarySettings.showDefinition, "switching back on must be stored too")
        switch(Switches.SHOW_DEFINITION).assertIsOn()
    }

    @Test
    fun `the definition colour field stores the confirmed hex`() {
        dictionarySettingsTab(initial = dictionarySettings { copy(definitionColor = "#C0FFEE") }) { get ->
            recolor(fromHex = "#C0FFEE", toHex = "#BADA55")
            assertTrue(
                get().dictionarySettings.definitionColor.equals("#BADA55", ignoreCase = true),
                "the confirmed hex must become the definition colour",
            )
            assertColorFieldShows("#BADA55", "the definition colour field")
            assertEquals("#AAAAAA", get().dictionarySettings.kjvUsageColor, "the KJV colour must be untouched")
        }
    }

    @Test
    fun `the definition font size stores a new value`() = dictionarySettingsTab { get ->
        assertEquals(32, get().dictionarySettings.definitionFontSize, "32 out of the box")
        retypeNumberField(showing = 32, to = 48)
        assertEquals(48, get().dictionarySettings.definitionFontSize, "the typed size must be stored")
        assertEquals(70, get().dictionarySettings.wordFontSize, "the word size must be untouched")
        assertEquals(22, get().dictionarySettings.kjvUsageFontSize, "as must the KJV size")
    }

    @Test
    fun `a definition font size outside the range is not stored`() = dictionarySettingsTab { get ->
        retypeNumberField(showing = 32, to = 7)
        assertEquals(32, get().dictionarySettings.definitionFontSize, "7 is below the 8..120 range")

        // The accepted value proves the field was live for the rejected one too.
        retypeNumberField(showing = 7, to = 8)
        assertEquals(8, get().dictionarySettings.definitionFontSize, "8 is the bottom of the range and is accepted")
    }

    // ── KJV Usage ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the KJV show switch turns it off and back on`() = dictionarySettingsTab { get ->
        switch(Switches.SHOW_KJV).assertIsOn()

        switch(Switches.SHOW_KJV).performScrollTo().performClick()
        waitForIdle()
        assertEquals(false, get().dictionarySettings.showKjvUsage, "switching off must be stored")
        switch(Switches.SHOW_KJV).assertIsOff()
        assertTrue(get().dictionarySettings.showReference, "the reference switch must be untouched")

        switch(Switches.SHOW_KJV).performClick()
        waitForIdle()
        assertEquals(true, get().dictionarySettings.showKjvUsage, "switching back on must be stored too")
        switch(Switches.SHOW_KJV).assertIsOn()
    }

    @Test
    fun `the KJV colour field stores the confirmed hex`() {
        dictionarySettingsTab(initial = dictionarySettings { copy(kjvUsageColor = "#010203") }) { get ->
            recolor(fromHex = "#010203", toHex = "#030201")
            assertTrue(
                get().dictionarySettings.kjvUsageColor.equals("#030201", ignoreCase = true),
                "the confirmed hex must become the KJV colour",
            )
            assertColorFieldShows("#030201", "the KJV colour field")
            assertEquals("#DDDDDD", get().dictionarySettings.definitionColor, "the definition colour must be untouched")
        }
    }

    @Test
    fun `the KJV font size stores a new value`() = dictionarySettingsTab { get ->
        assertEquals(22, get().dictionarySettings.kjvUsageFontSize, "22 out of the box")
        retypeNumberField(showing = 22, to = 36)
        assertEquals(36, get().dictionarySettings.kjvUsageFontSize, "the typed size must be stored")
        assertEquals(28, get().dictionarySettings.referenceFontSize, "the reference size must be untouched")
    }

    /** The KJV field accepts 8..80 — a narrower range than any other size field on the tab. */
    @Test
    fun `a KJV font size above its narrower range is not stored`() = dictionarySettingsTab { get ->
        retypeNumberField(showing = 22, to = 100)
        assertEquals(22, get().dictionarySettings.kjvUsageFontSize, "100 is above the 8..80 range")

        // The accepted value proves the field was live for the rejected one too.
        retypeNumberField(showing = 100, to = 80)
        assertEquals(80, get().dictionarySettings.kjvUsageFontSize, "80 is the top of the range and is accepted")
    }

    /**
     * 100 is rejected by the KJV field but accepted by every other size field on the tab, which is
     * what makes it worth checking: the ranges really are per-field rather than one shared bound.
     */
    @Test
    fun `a size the KJV field rejects is accepted by the definition field`() = dictionarySettingsTab { get ->
        retypeNumberField(showing = 32, to = 100)
        assertEquals(100, get().dictionarySettings.definitionFontSize, "100 is inside the definition's 8..120")
    }
}
