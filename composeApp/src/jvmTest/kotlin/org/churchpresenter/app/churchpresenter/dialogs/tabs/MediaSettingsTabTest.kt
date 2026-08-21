package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.PictureSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The media/slideshow settings tab: every control driven, and everything it displays asserted.
 *
 * The tab is pure View wiring over [AppSettings] — each control reads one field and hands back a
 * copy with that field replaced. What is worth pinning is that each copy-lambda targets the field it
 * belongs to (a mis-wired one writes silently to the wrong setting), that the two sliders honour
 * their ranges and the transition slider's 50ms snap, and that the animation dropdown maps in both
 * directions between the stored constant and the label an operator reads.
 *
 * Settings are held in test state and fed straight back in, exactly as `OptionsDialog` does, so each
 * interaction is followed through to the text it changes. Nothing in the tab is modified for these
 * tests: no parameter, no test tag, no widened member.
 *
 * Neither slider's track nor the dropdown's trigger carries semantics of its own, so they are driven
 * by position — derived from the measured bounds of the text beside them, never from a fixed pixel.
 * Slider assertions are on the range ends and on the snap invariant rather than on a pixel-to-value
 * mapping, which shifts with window size and platform font metrics.
 */
@OptIn(ExperimentalTestApi::class)
class MediaSettingsTabTest {

    /** Holds the tab's settings the way its real caller does, so changes feed back into the UI. */
    private class Harness {
        var current by mutableStateOf(AppSettings())
    }

    private fun ComposeUiTest.showTab(initial: AppSettings = AppSettings()): Harness {
        val harness = Harness().apply { current = initial }
        setContent {
            MaterialTheme {
                MediaSettingsTab(
                    settings = harness.current,
                    onSettingsChange = { transform -> harness.current = transform(harness.current) },
                )
            }
        }
        return harness
    }

    // ── What the tab displays ─────────────────────────────────────────────────

    @Test
    fun `both sections and every control label render`() = runComposeUiTest {
        showTab()

        listOf(
            "Media Slideshow Settings",
            "Transition Settings",
            "Auto-scroll interval:",
            "Loop",
            "Animate Keynote presentations",
            "Transition Duration:",
            "Animation Type:",
        ).forEach { label ->
            onAllNodesWithText(label, substring = true).onFirst()
                .assertExists("\"$label\" must render on the tab")
        }
    }

    @Test
    fun `the whole tab is visible, not merely present in the tree`() = runComposeUiTest {
        showTab()

        // A semantics node exists even when it is scrolled out of the viewport, which is how a
        // control can be "found" and still be unclickable. These assert visibility instead: the
        // first and the last control on the tab must both be on screen at the default window size.
        onNodeWithText("Auto-scroll interval:").assertIsDisplayed()
        onNodeWithText("Animation Type:").assertIsDisplayed()
        onNodeWithText("Crossfade").assertIsDisplayed()
        onAllNodes(isToggleable())[0].assertIsDisplayed()
        onAllNodes(isToggleable())[1].assertIsDisplayed()
    }

    @Test
    fun `the sliders label the values they are showing`() = runComposeUiTest {
        showTab(
            AppSettings(
                pictureSettings = PictureSettings(autoScrollInterval = 7f, transitionDuration = 650f)
            )
        )

        onNodeWithText("7s").assertExists("the interval slider reads out whole seconds")
        onNodeWithText("650ms").assertExists("the transition slider reads out milliseconds")
    }

    @Test
    fun `a fractional interval is read out as whole seconds`() = runComposeUiTest {
        showTab(AppSettings(pictureSettings = PictureSettings(autoScrollInterval = 12.8f)))

        onNodeWithText("12s").assertExists("the label truncates rather than showing 12.8")
    }

    // ── Checkboxes ────────────────────────────────────────────────────────────

    @Test
    fun `Loop is checked by default and unchecking it flips only that flag`() = runComposeUiTest {
        val harness = showTab()
        val before = harness.current.pictureSettings

        // Loop is the first of the tab's two checkboxes; Animate Keynote is the second.
        onAllNodes(isToggleable())[0].assertIsOn().performClick()
        waitForIdle()

        assertEquals(false, harness.current.pictureSettings.isLooping, "clicking Loop turns looping off")
        assertEquals(
            before.copy(isLooping = false),
            harness.current.pictureSettings,
            "no other picture setting may change",
        )
        onAllNodes(isToggleable())[0].assertIsOff()
    }

    @Test
    fun `Loop can be turned back on`() = runComposeUiTest {
        val harness = showTab(AppSettings(pictureSettings = PictureSettings(isLooping = false)))

        onAllNodes(isToggleable())[0].assertIsOff().performClick()
        waitForIdle()

        assertEquals(true, harness.current.pictureSettings.isLooping)
        onAllNodes(isToggleable())[0].assertIsOn()
    }

    @Test
    fun `unchecking Animate Keynote flips only the presentation flag`() = runComposeUiTest {
        val harness = showTab()
        val beforePictures = harness.current.pictureSettings

        onAllNodes(isToggleable())[1].assertIsOn().performClick()
        waitForIdle()

        assertEquals(
            false,
            harness.current.presentationSettings.animateKeynote,
            "clicking it falls back to static Keynote slides",
        )
        assertEquals(beforePictures, harness.current.pictureSettings, "the picture settings are untouched")
        onAllNodes(isToggleable())[1].assertIsOff()
    }

    @Test
    fun `Animate Keynote can be turned back on`() = runComposeUiTest {
        val harness = showTab(
            AppSettings(
                presentationSettings = AppSettings().presentationSettings.copy(animateKeynote = false)
            )
        )

        onAllNodes(isToggleable())[1].assertIsOff().performClick()
        waitForIdle()

        assertEquals(true, harness.current.presentationSettings.animateKeynote)
        onAllNodes(isToggleable())[1].assertIsOn()
    }

    @Test
    fun `each checkbox reflects the setting it was given`() = runComposeUiTest {
        showTab(
            AppSettings(
                pictureSettings = PictureSettings(isLooping = false),
                presentationSettings = AppSettings().presentationSettings.copy(animateKeynote = true),
            )
        )

        onAllNodes(isToggleable()).assertCountEquals(2)
        onAllNodes(isToggleable())[0].assertIsOff()
        onAllNodes(isToggleable())[1].assertIsOn()
    }

    // ── Sliders ───────────────────────────────────────────────────────────────

    /**
     * Taps a slider track at [fraction] of its width.
     *
     * The track is a bare `Box` with pointer input and no semantics, so it cannot be matched. It sits
     * between the row's fixed 140dp label and the value read-out at the trailing end, with
     * `Arrangement.spacedBy(10.dp)` before that read-out — so both edges come from the measured
     * bounds of those two text nodes rather than from a fixed coordinate.
     */
    private fun ComposeUiTest.tapSlider(rowLabel: String, valueLabel: String, fraction: Float) {
        waitForIdle()
        val label = onNodeWithText(rowLabel, substring = true).fetchSemanticsNode()
        val readout = onNodeWithText(valueLabel).fetchSemanticsNode()
        val left = label.boundsInRoot.right
        val right = readout.boundsInRoot.left - 10f * label.layoutInfo.density.density
        val y = readout.boundsInRoot.center.y
        assertTrue(right > left, "the slider track must have measurable width (left=$left right=$right)")

        onRoot().performTouchInput { click(Offset(left + (right - left) * fraction, y)) }
        waitForIdle()
    }

    @Test
    fun `tapping the far end of the interval slider selects the longest interval`() = runComposeUiTest {
        val harness = showTab()
        val before = harness.current.pictureSettings

        tapSlider("Auto-scroll interval:", "5s", fraction = 1f)

        assertEquals(30f, harness.current.pictureSettings.autoScrollInterval, "the range tops out at 30s")
        onNodeWithText("30s").assertExists("and the read-out follows immediately")
        assertEquals(
            before.copy(autoScrollInterval = 30f),
            harness.current.pictureSettings,
            "no other picture setting may change",
        )
    }

    @Test
    fun `tapping the near end of the interval slider selects the shortest interval`() = runComposeUiTest {
        val harness = showTab(AppSettings(pictureSettings = PictureSettings(autoScrollInterval = 20f)))

        tapSlider("Auto-scroll interval:", "20s", fraction = 0f)

        assertEquals(1f, harness.current.pictureSettings.autoScrollInterval, "the range starts at 1s")
        onNodeWithText("1s").assertExists()
    }

    @Test
    fun `tapping the middle of the interval slider lands between the ends`() = runComposeUiTest {
        val harness = showTab()

        tapSlider("Auto-scroll interval:", "5s", fraction = 0.5f)

        val interval = harness.current.pictureSettings.autoScrollInterval
        assertTrue(interval > 1f && interval < 30f, "a mid-track tap lands inside the range, was $interval")
    }

    @Test
    fun `tapping the far end of the transition slider selects the longest duration`() = runComposeUiTest {
        val harness = showTab()
        val before = harness.current.pictureSettings

        tapSlider("Transition Duration:", "500ms", fraction = 1f)

        assertEquals(2000f, harness.current.pictureSettings.transitionDuration, "the range tops out at 2000ms")
        onNodeWithText("2000ms").assertExists()
        assertEquals(
            before.copy(transitionDuration = 2000f),
            harness.current.pictureSettings,
            "no other picture setting may change",
        )
    }

    @Test
    fun `tapping the near end of the transition slider selects the shortest duration`() = runComposeUiTest {
        val harness = showTab(AppSettings(pictureSettings = PictureSettings(transitionDuration = 1200f)))

        tapSlider("Transition Duration:", "1200ms", fraction = 0f)

        assertEquals(100f, harness.current.pictureSettings.transitionDuration, "the range starts at 100ms")
        onNodeWithText("100ms").assertExists()
    }

    @Test
    fun `the transition slider snaps to fifty-millisecond steps`() = runComposeUiTest {
        val harness = showTab()

        // A third of the way along the track is a raw value the snap has to round down.
        tapSlider("Transition Duration:", "500ms", fraction = 1f / 3f)

        val duration = harness.current.pictureSettings.transitionDuration
        assertEquals(0f, duration % 50f, "every duration the slider produces is a multiple of 50ms, was $duration")
        assertTrue(duration in 100f..2000f, "and inside the range, was $duration")
        onNodeWithText("${duration.toInt()}ms").assertExists("the read-out shows the snapped value")
    }

    // ── Animation type dropdown ───────────────────────────────────────────────

    private val animationLabels = listOf("Crossfade", "Fade", "Slide Left", "Slide Right", "None")

    /** Opens the animation-type menu by clicking the selector, which shows the current choice. */
    private fun ComposeUiTest.openAnimationDropdown() {
        waitForIdle()
        val shown = animationLabels.first { onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText(shown).onFirst().performClick()
        waitForIdle()
    }

    @Test
    fun `the dropdown names the stored animation type`() = runComposeUiTest {
        val harness = showTab()

        // One render, walked through every stored value the tab knows how to name.
        listOf(
            Constants.ANIMATION_CROSSFADE to "Crossfade",
            Constants.ANIMATION_FADE to "Fade",
            Constants.ANIMATION_SLIDE_LEFT to "Slide Left",
            Constants.ANIMATION_SLIDE_RIGHT to "Slide Right",
            Constants.ANIMATION_NONE to "None",
        ).forEach { (stored, shown) ->
            harness.current = harness.current.copy(
                pictureSettings = harness.current.pictureSettings.copy(animationType = stored)
            )
            waitForIdle()
            onAllNodesWithText(shown).onFirst().assertExists("$stored must read as \"$shown\"")
        }
    }

    @Test
    fun `an animation type this build does not know falls back to Crossfade`() = runComposeUiTest {
        showTab(AppSettings(pictureSettings = PictureSettings(animationType = "SPIN_AROUND")))

        onNodeWithText("Crossfade")
            .assertExists("an unrecognised stored value must name a real option, not itself")
        onAllNodesWithText("SPIN_AROUND").assertCountEquals(0)
    }

    @Test
    fun `the dropdown offers every animation type`() = runComposeUiTest {
        showTab()

        openAnimationDropdown()

        animationLabels.forEach { option ->
            onAllNodesWithText(option).onFirst().assertExists("\"$option\" must be offered")
        }
        // Crossfade is both the closed selector's text and a menu entry; the others appear once.
        onAllNodesWithText("Crossfade").assertCountEquals(2)
    }

    @Test
    fun `choosing each animation type stores its own constant`() = runComposeUiTest {
        val harness = showTab()
        val before = harness.current.pictureSettings

        listOf(
            "Fade" to Constants.ANIMATION_FADE,
            "Slide Left" to Constants.ANIMATION_SLIDE_LEFT,
            "Slide Right" to Constants.ANIMATION_SLIDE_RIGHT,
            "None" to Constants.ANIMATION_NONE,
            "Crossfade" to Constants.ANIMATION_CROSSFADE,
        ).forEach { (option, stored) ->
            openAnimationDropdown()
            // With the menu open, the wanted label is the last match — the closed selector above it
            // still reads as the previous choice.
            onAllNodesWithText(option).onLast().performClick()
            waitForIdle()

            assertEquals(
                stored,
                harness.current.pictureSettings.animationType,
                "choosing \"$option\" must store $stored",
            )
            assertEquals(
                before.copy(animationType = stored),
                harness.current.pictureSettings,
                "and change nothing else",
            )
        }
    }
}
