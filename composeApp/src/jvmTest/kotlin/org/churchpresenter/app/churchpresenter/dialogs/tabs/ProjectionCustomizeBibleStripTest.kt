@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The strip under the Bible preview: the four margins, the fades, and the two rows that only mean
 * anything with more than one translation on screen.
 */
class ProjectionCustomizeBibleStripTest {

    private val band = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL

    private fun output(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) = AppSettings(
        bibleSettings = BibleSettings(
            marginTop = 11,
            marginBottom = 22,
            marginLeft = 33,
            marginRight = 44,
            multiTranslationSpacing = 17,
            lowerThirdHeightPercent = 29,
            translations = listOf(BibleTranslationSettings(fileName = "kjv.spb")),
        ),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(ScreenAssignment(displayMode = mode)),
        ),
    )

    private fun AppSettings.stored(): BibleSettings =
        assertNotNull(projectionSettings.screenAssignments[0].bibleOverride, "the output must have its own Bible")

    // ── The margins ───────────────────────────────────────────────────────────

    @Test
    fun `each margin field writes its own margin`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            retypeNumberField(11, 12)
            retypeNumberField(22, 23)
            retypeNumberField(33, 34)
            retypeNumberField(44, 45)

            val stored = get().stored()
            assertEquals(
                listOf(12, 23, 34, 45),
                listOf(stored.marginTop, stored.marginBottom, stored.marginLeft, stored.marginRight),
            )
        }
    }

    @Test
    fun `a margin of zero is a value, not an absence`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            retypeNumberField(11, 0)

            assertEquals(0, get().stored().marginTop)
        }
    }

    @Test
    fun `a margin past the range is not stored`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            retypeNumberField(11, 9999)

            assertEquals(11, get().stored().marginTop)
        }
    }

    @Test
    fun `one margin does not move another`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            retypeNumberField(33, 34)

            val stored = get().stored()
            assertEquals(34, stored.marginLeft)
            assertEquals(11, stored.marginTop)
            assertEquals(22, stored.marginBottom)
            assertEquals(44, stored.marginRight)
        }
    }

    // ── The fades ─────────────────────────────────────────────────────────────

    @Test
    fun `Fade In writes the output's own flag`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            toggleCheckbox("Fade In", scroll = false)

            assertFalse(get().stored().fadeIn, "it ships on and must have gone off")
        }
    }

    @Test
    fun `Fade Out writes the output's own flag`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            toggleCheckbox("Fade Out", scroll = false)

            val stored = get().stored()
            assertFalse(stored.fadeOut)
            assertTrue(stored.fadeIn, "the box above it must not move")
        }
    }

    @Test
    fun `Crossfade writes the output's own flag`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            toggleCheckbox("Crossfade", scroll = false)

            assertTrue(get().stored().crossfade, "it ships off and must have come on")
        }
    }

    @Test
    fun `a fade goes back on`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            toggleCheckbox("Fade Out", scroll = false)
            assertFalse(get().stored().fadeOut)
            toggleCheckbox("Fade Out", scroll = false)
            assertTrue(get().stored().fadeOut)
        }
    }

    // ── The band's height ─────────────────────────────────────────────────────

    @Test
    fun `a full screen is offered no band height`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.BIBLE, override = false)
            // A full screen has no band to size.
            onNodeWithText("29").assertDoesNotExist()
        }
    }

    @Test
    fun `a band is offered its own height`() {
        projectionTab(output(band)) { _ ->
            openCustomizePane(CustomizePane.BIBLE, override = false)
            onNodeWithText("29").assertExists()
        }
    }

    @Test
    fun `retyping the band's height writes it`() {
        projectionTab(output(band)) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            retypeNumberField(29, 40)

            assertEquals(40, get().stored().lowerThirdHeightPercent)
        }
    }

    @Test
    fun `a band height past the range is not stored`() {
        projectionTab(output(band)) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            retypeNumberField(29, 400)

            assertEquals(29, get().stored().lowerThirdHeightPercent)
        }
    }

    // ── The two translation rows ──────────────────────────────────────────────

    @Test
    fun `the strip carries the divider switch`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            toggleCheckbox("Show divider between translations", scroll = false)

            assertTrue(get().stored().multiTranslationDivider, "it ships off and must have come on")
        }
    }

    @Test
    fun `the divider switch goes back off`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            toggleCheckbox("Show divider between translations", scroll = false)
            toggleCheckbox("Show divider between translations", scroll = false)

            assertFalse(get().stored().multiTranslationDivider)
        }
    }

    @Test
    fun `the strip carries the spacing between translations`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            retypeNumberField(17, 60)

            assertEquals(60, get().stored().multiTranslationSpacing)
        }
    }

    @Test
    fun `the spacing can be pulled negative`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            retypeNumberField(17, -20)

            assertEquals(-20, get().stored().multiTranslationSpacing)
        }
    }

    @Test
    fun `a spacing past the range is not stored`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            retypeNumberField(17, 500)

            assertEquals(17, get().stored().multiTranslationSpacing)
        }
    }

    @Test
    fun `the strip writes nothing belonging to the verse text`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            retypeNumberField(11, 12)

            assertEquals(
                BibleTranslationSettings().textFontSize,
                get().stored().translationList()[0].textFontSize,
            )
        }
    }
}
