package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.utils.OUTPUT_RESOLUTIONS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The shared resolution control, exercised through what it does rather than how it looks.
 *
 * It replaced two copies of a 16:9-only dropdown, and is now the one control behind the dev
 * fallback windows, the Browser Source outputs and the NDI outputs — so a mistake here is a
 * mistake in all three at once.
 */
@OptIn(ExperimentalTestApi::class)
class ResolutionPickerTest {

    /** Renders the picker over [width] x [height] and hands back whatever it last reported. */
    private fun picker(
        width: Int = 1920,
        height: Int = 1080,
        block: androidx.compose.ui.test.ComposeUiTest.(read: () -> Pair<Int, Int>?) -> Unit,
    ) = runComposeUiTest {
        var picked: Pair<Int, Int>? = null
        setContent {
            MaterialTheme {
                ResolutionPicker(
                    label = "Resolution",
                    width = width,
                    height = height,
                    cellWidth = 120.dp,
                    labelHeight = 20.dp,
                    onChange = { w, h -> picked = w to h },
                )
            }
        }
        waitForIdle()
        block { picked }
    }

    @Test
    fun `the button shows the current resolution`() = picker { _ ->
        onNodeWithText("1920×1080").assertExists()
    }

    @Test
    fun `picking a preset reports both dimensions`() = picker { read ->
        onNodeWithText("1920×1080").performClick()
        waitForIdle()
        onNodeWithText("1024×768", substring = true).performClick()
        waitForIdle()
        assertEquals(1024 to 768, read(), "the whole size must be reported, not just the width")
    }

    @Test
    fun `the menu offers a portrait output`() = picker { read ->
        onNodeWithText("1920×1080").performClick()
        waitForIdle()
        val portrait = OUTPUT_RESOLUTIONS.first { it.aspectRatio < 1f }
        onNodeWithText("${portrait.width}×${portrait.height}", substring = true).performClick()
        waitForIdle()
        assertEquals(portrait.width to portrait.height, read())
    }

    @Test
    fun `a custom resolution is reported once confirmed`() = picker { read ->
        onNodeWithText("1920×1080").performClick()
        waitForIdle()
        onNodeWithText("Custom", substring = true).performClick()
        waitForIdle()

        // The two fields are told apart by what they already hold — 1920 is the width, 1080 the
        // height. The label above each is drawn uppercased in its own Text, not on the field.
        onNodeWithText("1920").performTextReplacement("3000")
        waitForIdle()
        onNodeWithText("OK").performClick()
        waitForIdle()

        assertEquals(3000, read()?.first, "the typed width must reach the caller")
    }

    /**
     * Cancelling reports nothing.
     *
     * The draft is held inside the dialog precisely so a half-typed number never reaches the
     * output — applying per keystroke would resize a live window as it was being typed.
     */
    @Test
    fun `cancelling a custom resolution changes nothing`() = picker { read ->
        onNodeWithText("1920×1080").performClick()
        waitForIdle()
        onNodeWithText("Custom", substring = true).performClick()
        waitForIdle()
        onNodeWithText("Cancel").performClick()
        waitForIdle()

        assertNull(read(), "a cancelled dialog must report no change at all")
    }
}
