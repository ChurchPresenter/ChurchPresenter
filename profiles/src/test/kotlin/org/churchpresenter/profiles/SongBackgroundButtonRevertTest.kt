package org.churchpresenter.profiles

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/**
 * The song editor's background panel keeps its edits live, so the preview follows them -- which
 * makes Cancel the thing that has to put the background back as it was when the panel opened.
 */
@OptIn(ExperimentalTestApi::class)
class SongBackgroundButtonRevertTest {

    private val opened = SongBackground(type = SongBackgroundType.COLOR, color = "#2a1130")

    private fun panel(body: ComposeUiTest.(get: () -> Pair<SongBackground, Boolean>) -> Unit) = runComposeUiTest {
        var background by mutableStateOf(opened)
        var expanded by mutableStateOf(true)
        setContent {
            MaterialTheme {
                SongBackgroundButton(
                    background = background,
                    lowerThirdBackground = SongBackground(),
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    onBackgroundChange = { background = it },
                    onLowerThirdBackgroundChange = {},
                    sampleLine = "Amazing grace",
                    onApplyToSongbook = null,
                )
            }
        }
        waitForIdle()
        body { background to expanded }
    }

    /** Taps the colour tile captioned [name] -- its swatch, which sits just above the caption. */
    private fun ComposeUiTest.pickColor(name: String) {
        onAllNodesWithText(name).onLast().performTouchInput { click(Offset(width / 2f, -SWATCH_ABOVE_CAPTION)) }
        waitForIdle()
    }

    @Test
    fun `Cancel puts back the background the panel opened on`() = panel { get ->
        pickColor("Deep Navy")
        assertNotEquals(opened, get().first, "the edit is live while the panel is open")

        onAllNodesWithText("Cancel").onLast().performClick()
        waitForIdle()

        assertEquals(opened, get().first)
        assertFalse(get().second, "and the panel closed")
    }

    @Test
    fun `closing with the X is a cancel too`() = panel { get ->
        pickColor("Deep Navy")
        assertNotEquals(opened, get().first)

        onAllNodesWithContentDescription("Close").onLast().performClick()
        waitForIdle()

        assertEquals(opened, get().first)
        assertFalse(get().second)
    }

    @Test
    fun `a tap on the dim, blur and opacity tracks sets each one`() = panel { get ->
        tapTrack("DIM")
        assertNotEquals(opened.dim, get().first.dim)
        tapTrack("BLUR")
        assertNotEquals(opened.blur, get().first.blur)
        tapTrack("BACKGROUND OPACITY")
        assertNotEquals(opened.opacity, get().first.opacity)
    }

    private fun ComposeUiTest.tapTrack(caption: String) {
        val node = onAllNodesWithText(caption).onLast()
        runCatching { node.performScrollTo() }
        node.performTouchInput { click(Offset(width / 2f, height + TRACK_BELOW_CAPTION.toPx())) }
        waitForIdle()
    }

    private companion object {
        const val SWATCH_ABOVE_CAPTION = 12f
        val TRACK_BELOW_CAPTION = 11.dp
    }
}
