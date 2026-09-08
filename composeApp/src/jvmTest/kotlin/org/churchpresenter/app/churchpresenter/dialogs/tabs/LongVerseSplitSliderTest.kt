@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.viewmodel.LONG_VERSE_WORDS_MAX
import org.churchpresenter.app.churchpresenter.viewmodel.LONG_VERSE_WORDS_OFF
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The one control that says both whether a long verse is broken across two slides and how long
 * "long" is.
 *
 * `SlimSlider` publishes no semantics of its own, so the track is driven by injecting a click at a
 * computed coordinate inside a host of known width — the approach [SlimSliderTest] documents. The
 * caption line is what the assertions read: it is the only place the current stop is written out.
 */
class LongVerseSplitSliderTest {

    private val hostWidth = 200.dp

    private fun settings(splitting: Boolean, words: Int) =
        AppSettings(bibleSettings = BibleSettings(splitLongVerses = splitting, longVerseWordCount = words))

    private fun slider(
        initial: AppSettings,
        block: ComposeUiTest.(get: () -> AppSettings) -> Unit,
    ) = runComposeUiTest {
        var current = initial
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(current) }
                Box(Modifier.testTag("host").width(hostWidth)) {
                    LongVerseSplitSlider(
                        settings = state,
                        onSettingsChange = { transform -> state = transform(state); current = state },
                    )
                }
            }
        }
        block { current }
    }

    /**
     * Taps the track at [fraction] of its width.
     *
     * The track is the last thing in the column, so the tap goes a few pixels above the host's
     * bottom edge — the caption row above it would swallow a tap aimed at the middle.
     */
    private fun ComposeUiTest.tapTrack(fraction: Float) {
        val size = onNodeWithTag("host").fetchSemanticsNode().size
        onNodeWithTag("host").performTouchInput {
            click(Offset(size.width * fraction, size.height - 4f))
        }
        waitForIdle()
    }

    // ── What the caption says ─────────────────────────────────────────────────

    @Test
    fun `the control names itself`() = slider(settings(true, 45)) { _ ->
        onNodeWithText("Split long verses across two slides").assertExists()
    }

    @Test
    fun `a threshold is shown as a word count`() = slider(settings(true, 45)) { _ ->
        onNodeWithText("45 words").assertExists("the stop must be written out, not only drawn")
    }

    @Test
    fun `splitting turned off is shown as Off rather than as a number`() = slider(settings(false, 45)) { _ ->
        onNodeWithText("Off").assertExists()
        onNodeWithText("45 words").assertDoesNotExist()
    }

    @Test
    fun `a stored count below the usable range is shown clamped`() = slider(settings(true, 3)) { _ ->
        onNodeWithText("25 words").assertExists("a hand-edited settings file must not move the handle off the track")
    }

    @Test
    fun `a stored count above the usable range is shown clamped`() = slider(settings(true, 900)) { _ ->
        onNodeWithText("$LONG_VERSE_WORDS_MAX words").assertExists()
    }

    @Test
    fun `the widest threshold the track offers is its maximum`() = slider(settings(true, LONG_VERSE_WORDS_MAX)) { _ ->
        onNodeWithText("60 words").assertExists()
    }

    // ── Driving the track ─────────────────────────────────────────────────────

    @Test
    fun `dropping the handle at the far end stores the widest threshold`() = slider(settings(true, 30)) { get ->
        tapTrack(1f)
        assertTrue(get().bibleSettings.splitLongVerses)
        assertEquals(LONG_VERSE_WORDS_MAX, get().bibleSettings.longVerseWordCount)
    }

    @Test
    fun `dropping the handle at the first stop turns splitting off`() = slider(settings(true, 45)) { get ->
        tapTrack(0f)
        assertFalse(get().bibleSettings.splitLongVerses, "the first stop is Off, not a threshold")
    }

    @Test
    fun `turning splitting off leaves the tuned threshold alone`() = slider(settings(true, 45)) { get ->
        tapTrack(0f)
        assertEquals(
            45,
            get().bibleSettings.longVerseWordCount,
            "coming back from Off must return the operator to the number they had",
        )
    }

    @Test
    fun `dropping the handle in the middle stores a middling threshold`() = slider(settings(true, 25)) { get ->
        tapTrack(0.5f)
        assertTrue(get().bibleSettings.splitLongVerses)
        assertEquals(40, get().bibleSettings.longVerseWordCount, "halfway along a 20..60 track")
    }

    @Test
    fun `a stop past Off turns splitting back on`() = slider(settings(false, 50)) { get ->
        tapTrack(0.5f)
        assertTrue(get().bibleSettings.splitLongVerses, "moving off the first stop switches splitting on")
        assertEquals(40, get().bibleSettings.longVerseWordCount)
    }

    @Test
    fun `the caption follows the handle`() = slider(settings(true, 25)) { _ ->
        tapTrack(1f)
        onNodeWithText("60 words").assertExists("the number shown must be the number stored")
    }

    @Test
    fun `Off is one step below the usable range`() {
        assertEquals(20, LONG_VERSE_WORDS_OFF)
    }
}
