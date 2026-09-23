@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The strip under the Bible preview: the four margins, the fades, the band's height and the rows
 * that only mean anything with more than one translation on screen.
 *
 * Ported from `ProjectionCustomizeBibleStripTest`. Every field starts at a distinct odd number so
 * it can be found by what it shows.
 */
class ProfilesCustomizeBibleStripTest {

    private val band = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL

    private fun output(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) = profileDocument(
        mode = mode,
        bible = BibleSettings(
            marginTop = 11,
            marginBottom = 22,
            marginLeft = 33,
            marginRight = 44,
            multiTranslationSpacing = 17,
            lowerThirdHeightPercent = 29,
            translations = listOf(BibleTranslationSettings(fileName = "kjv.spb")),
        ),
    )

    // ── The margins ───────────────────────────────────────────────────────────

    @Test
    fun `each margin field writes its own margin`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            retypeNumberField(11, 12)
            retypeNumberField(22, 23)
            retypeNumberField(33, 34)
            retypeNumberField(44, 45)

            val stored = get().bible()
            assertEquals(
                listOf(12, 23, 34, 45),
                listOf(stored.marginTop, stored.marginBottom, stored.marginLeft, stored.marginRight),
            )
        }
    }

    @Test
    fun `a margin of zero is a value, not an absence`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            retypeNumberField(11, 0)

            assertEquals(0, get().bible().marginTop)
        }
    }

    @Test
    fun `a margin past the range is not stored`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            retypeNumberField(11, 9999)

            assertEquals(11, get().bible().marginTop)
        }
    }

    @Test
    fun `one margin does not move another`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            retypeNumberField(33, 34)

            val stored = get().bible()
            assertEquals(34, stored.marginLeft)
            assertEquals(11, stored.marginTop)
            assertEquals(22, stored.marginBottom)
            assertEquals(44, stored.marginRight)
        }
    }

    // ── The fades ─────────────────────────────────────────────────────────────

    @Test
    fun `Fade In writes the profile's own flag`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            toggleCheckbox("Fade In", scroll = false)

            assertFalse(get().bible().fadeIn, "it ships on and must have gone off")
        }
    }

    @Test
    fun `Fade Out writes the profile's own flag`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            toggleCheckbox("Fade Out", scroll = false)

            val stored = get().bible()
            assertFalse(stored.fadeOut)
            assertTrue(stored.fadeIn, "the box above it must not move")
        }
    }

    @Test
    fun `Crossfade writes the profile's own flag`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            toggleCheckbox("Crossfade", scroll = false)

            assertTrue(get().bible().crossfade, "it ships off and must have come on")
        }
    }

    @Test
    fun `a fade goes back on`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            toggleCheckbox("Fade Out", scroll = false)
            assertFalse(get().bible().fadeOut)
            toggleCheckbox("Fade Out", scroll = false)
            assertTrue(get().bible().fadeOut)
        }
    }

    // ── The band's height ─────────────────────────────────────────────────────

    @Test
    fun `a full screen is offered no band height`() {
        profilesTab(output()) { _ ->
            openCustomizePane(CustomizePane.BIBLE)
            // A full screen has no band to size.
            onNodeWithText("29").assertDoesNotExist()
        }
    }

    @Test
    fun `a band is offered its own height`() {
        profilesTab(output(band)) { _ ->
            openCustomizePane(CustomizePane.BIBLE)
            onNodeWithText("29").assertExists()
        }
    }

    @Test
    fun `retyping the band's height writes it`() {
        profilesTab(output(band)) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            retypeNumberField(29, 40)

            assertEquals(40, get().bible().lowerThirdHeightPercent)
        }
    }

    @Test
    fun `a band height past the range is not stored`() {
        profilesTab(output(band)) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            retypeNumberField(29, 400)

            assertEquals(29, get().bible().lowerThirdHeightPercent)
        }
    }

    // ── The two translation rows ──────────────────────────────────────────────

    @Test
    fun `the strip carries the divider switch`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            toggleCheckbox("Show divider between translations", scroll = false)

            assertTrue(get().bible().multiTranslationDivider, "it ships off and must have come on")
        }
    }

    @Test
    fun `the divider switch goes back off`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            toggleCheckbox("Show divider between translations", scroll = false)
            toggleCheckbox("Show divider between translations", scroll = false)

            assertFalse(get().bible().multiTranslationDivider)
        }
    }

    @Test
    fun `the strip carries the spacing between translations`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            retypeNumberField(17, 60)

            assertEquals(60, get().bible().multiTranslationSpacing)
        }
    }

    @Test
    fun `the spacing can be pulled negative`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            retypeNumberField(17, -20)

            assertEquals(-20, get().bible().multiTranslationSpacing)
        }
    }

    @Test
    fun `the split-long-verses switch is on the strip too`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            val before = get().bible().splitLongVerses
            toggleCheckbox("Split long verses across two slides", scroll = false)

            assertEquals(!before, get().bible().splitLongVerses)
        }
    }

    @Test
    fun `the Background tab's Bible styling is never written through`() {
        profilesTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            retypeNumberField(11, 12)

            assertEquals(12, get().bible().marginTop, "the profile moved")
            assertEquals(11, get().bibleSettings.marginTop, "the document did not")
        }
    }
}
