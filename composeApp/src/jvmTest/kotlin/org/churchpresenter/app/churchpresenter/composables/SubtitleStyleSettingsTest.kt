@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SENTINEL_FONT
import org.churchpresenter.app.churchpresenter.dialogs.tabs.pickFont
import org.churchpresenter.app.churchpresenter.dialogs.tabs.recolor
import org.churchpresenter.app.churchpresenter.dialogs.tabs.retypeNumberField
import org.churchpresenter.app.churchpresenter.dialogs.tabs.tapSliderTrack
import org.churchpresenter.app.churchpresenter.dialogs.tabs.uniquelyNamedFont
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.MediaSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The subtitle style controls (`MediaSubtitleSettingsDialog`'s own content): every field driven,
 * and each asserted to land on the `MediaSettings` field it belongs to. Mounted directly rather
 * than through the dialog, the same way `MediaSettingsTabTest` drives its tab -- `DialogWindow`
 * opens a real OS window a plain `runComposeUiTest` cannot mount into, and the styling logic lives
 * entirely in this composable, not in the window chrome around it.
 */
class SubtitleStyleSettingsTest {

    private fun subtitleStyle(
        mediaSettings: MediaSettings = MediaSettings(),
        block: ComposeUiTest.(get: () -> MediaSettings) -> Unit,
    ) = runComposeUiTest {
        var current = mediaSettings
        setContent {
            MaterialTheme {
                var settings by mutableStateOf(AppSettings(mediaSettings = current))
                // The real dialog hosts this inside a scrolling Column (MediaSubtitleSettingsDialog);
                // several shared locators (recolor, retypeNumberField) scroll their target into view,
                // which needs a real scrollable ancestor to do.
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    SubtitleStyleSettings(
                        settings = settings,
                        onSettingsChange = { transform ->
                            settings = transform(settings)
                            current = settings.mediaSettings
                        },
                    )
                }
            }
        }
        block { current }
    }

    // ── Colour fields ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the text colour field stores the confirmed hex`() = subtitleStyle { get ->
        recolor(fromHex = "#FFFFFF", toHex = "#ABCDEF")
        assertTrue(get().textColor.equals("#ABCDEF", ignoreCase = true))
    }

    @Test
    fun `the background colour field stores the confirmed hex`() = subtitleStyle { get ->
        recolor(fromHex = "transparent", toHex = "#334455")
        assertTrue(get().backgroundColor.equals("#334455", ignoreCase = true))
    }

    // ── Bold / italic / underline / shadow ──────────────────────────────────────────────────

    @Test
    fun `the style buttons toggle bold italic underline and shadow independently`() = subtitleStyle { get ->
        onNodeWithText("B").performClick()
        waitForIdle()
        assertEquals(true, get().bold)
        assertEquals(false, get().italic)

        onNodeWithText("I").performClick()
        waitForIdle()
        assertEquals(true, get().italic)
        assertEquals(true, get().bold, "bold must stay on")

        onNodeWithText("U").performClick()
        waitForIdle()
        assertEquals(true, get().underline)

        onNodeWithText("S").performClick()
        waitForIdle()
        assertEquals(true, get().shadow)
        assertEquals(true, get().bold, "earlier toggles must be untouched")
    }

    @Test
    fun `the shadow detail row appears only once shadow is on, and its fields store independently`() =
        subtitleStyle(
            mediaSettings = MediaSettings(shadow = true, shadowColor = "#654321", shadowSize = 120, shadowOpacity = 60),
        ) { get ->
            recolor(fromHex = "#654321", toHex = "#0F0F0F")
            assertTrue(get().shadowColor.equals("#0F0F0F", ignoreCase = true))

            retypeNumberField(showing = 120, to = 200)
            assertEquals(200, get().shadowSize)
            assertEquals(60, get().shadowOpacity, "opacity must be untouched by the size edit")
        }

    // ── Font and size ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the font dropdown stores the picked family`() {
        val target = uniquelyNamedFont()
        subtitleStyle(mediaSettings = MediaSettings(fontType = SENTINEL_FONT)) { get ->
            pickFont(showing = SENTINEL_FONT, to = target)
            assertEquals(target, get().fontType)
        }
    }

    @Test
    fun `the size field stores a value within range and rejects one outside it`() = subtitleStyle { get ->
        retypeNumberField(showing = 42, to = 72)
        assertEquals(72, get().fontSize)
        retypeNumberField(showing = 72, to = 400)
        assertEquals(72, get().fontSize, "400 is above the 8..200 range")
    }

    // ── Position grid ────────────────────────────────────────────────────────────────────────

    @Test
    fun `every position button stores its own constant`() = subtitleStyle { get ->
        val positions = listOf(
            "TL" to Constants.TOP_LEFT,
            "TC" to Constants.TOP_CENTER,
            "TR" to Constants.TOP_RIGHT,
            "CL" to Constants.CENTER_LEFT,
            "C" to Constants.CENTER,
            "CR" to Constants.CENTER_RIGHT,
            "BL" to Constants.BOTTOM_LEFT,
            "BC" to Constants.BOTTOM_CENTER,
            "BR" to Constants.BOTTOM_RIGHT,
        )
        for ((label, constant) in positions) {
            onNode(hasClickAction() and hasText(label)).performClick()
            waitForIdle()
            assertEquals(constant, get().position, "clicking $label must store $constant")
        }
    }

    // ── Opacity slider ───────────────────────────────────────────────────────────────────────

    @Test
    fun `tapping the far right of the opacity slider stores close to 100`() = subtitleStyle { get ->
        tapSliderTrack(caption = "Opacity:", readout = "0%", fraction = 1f)
        // 99 rather than 100: the track's right edge cannot be tapped, so the last reachable
        // stop is a pixel short of it. See tapSliderTrack's own doc comment.
        assertTrue(get().backgroundOpacity >= 99)
    }

    @Test
    fun `tapping the middle of the opacity slider stores roughly half`() =
        subtitleStyle(mediaSettings = MediaSettings(backgroundOpacity = 100)) { get ->
            tapSliderTrack(caption = "Opacity:", readout = "100%", fraction = 0.5f)
            assertTrue(get().backgroundOpacity in 25..75, "expected roughly half, was ${get().backgroundOpacity}")
        }

    // ── Max lines ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `max lines stores a value within its range`() = subtitleStyle { get ->
        retypeNumberField(showing = 2, to = 5)
        assertEquals(5, get().maxLines)
        retypeNumberField(showing = 5, to = 50)
        assertEquals(5, get().maxLines, "50 is above the 1..10 range")
    }

}
