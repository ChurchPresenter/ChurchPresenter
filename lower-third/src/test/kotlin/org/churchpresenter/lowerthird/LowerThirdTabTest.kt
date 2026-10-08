@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lowerthird

import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.settings.StreamingSettings
import org.churchpresenter.atem.AtemUploadStatus
import androidx.compose.ui.test.performMouseInput
import org.churchpresenter.sharedui.testing.showsContainingText
import org.churchpresenter.settings.AppSettings
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.WindowLayoutSettings
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import org.churchpresenter.sharedui.utils.OutputSize
import org.churchpresenter.sharedui.utils.PreviewOutput
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.sharedui.testing.renderedText
import org.churchpresenter.sharedui.testing.showsExactly

/** 3 frames at 30fps = 100ms — fast enough to play through to completion inside a test. */
private const val QUICK_LOTTIE = """{"v":"5.7.4","fr":30,"ip":0,"op":3,"w":1920,"h":1080,"layers":[]}"""

/**
 * The Lower Third tab: a preset picker over a folder of Lottie animations.
 *
 * An operator picks a preset before the service and fires it during, so what matters is that the
 * list reflects the folder, that nothing can be fired until something is chosen, and that what
 * reaches the output is the animation that was picked rather than a name that has to be resolved
 * again later.
 *
 * The ATEM upload panel is not driven here — it reaches a switcher over the network, and everything
 * decidable before that is covered by `CompanionServerLowerThirdTest`.
 *
 * See `LowerThirdTabTestSupport.kt` for the harness.
 */
class LowerThirdTabTest {

    // ── The preset list ─────────────────────────────────────────────────────────

    @Test
    fun `every Lottie in the folder is listed, and nothing else`() = lowerThirdTab { _ ->
        assertTrue(showsExactly("Welcome"), "got ${renderedText()}")
        assertTrue(showsExactly("Speaker Name"))
        assertFalse(showsExactly("notes"), "a non-Lottie file in the folder is not a preset")
    }

    @Test
    fun `every preset listed is handed to the pre-render, and nothing else`() = lowerThirdTab { reports ->
        waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) { reports.preRendered.size == 2 }
        assertEquals(listOf("Speaker Name", "Welcome"), reports.preRendered.sorted())
    }

    @Test
    fun `with no folder configured the tab says so, rather than that the folder is empty`() =
        lowerThirdTab(folder = null) { _ ->
            assertTrue(showsExactly(LowerThirdLabel.NO_FOLDER), "got ${renderedText()}")
        }

    @Test
    fun `an empty folder says the folder is empty, not that none is configured`() =
        lowerThirdTab(folder = lottieFolder()) { _ ->
            // The folder exists but holds only the non-Lottie file. A configured-but-empty folder and
            // an unconfigured one used to read identically, which left the operator no way to tell a
            // mistyped path from a folder they had simply not filled yet.
            assertTrue(showsExactly(LowerThirdLabel.NO_FILES), "got ${renderedText()}")
        }

    // ── Choosing one ────────────────────────────────────────────────────────────

    @Test
    fun `nothing can be fired until a preset is chosen`() = lowerThirdTab { _ ->
        assertTrue(showsExactly(LowerThirdLabel.SELECT_PRESET), "the preview explains itself")
        ltButton(LowerThirdLabel.GO_LIVE).assertIsNotEnabled()
        ltButton(LowerThirdLabel.ADD_TO_SCHEDULE).assertIsNotEnabled()
    }

    @Test
    fun `choosing a preset opens it and enables the actions`() = lowerThirdTab { _ ->
        selectPreset("Welcome")

        assertFalse(showsExactly(LowerThirdLabel.SELECT_PRESET), "the placeholder is gone")
        assertTrue(showsExactly("Welcome"), "and the chosen preset is named")
        ltButton(LowerThirdLabel.GO_LIVE).assertIsEnabled()
        ltButton(LowerThirdLabel.ADD_TO_SCHEDULE).assertIsEnabled()
    }

    @Test
    fun `choosing a different preset replaces the first`() = lowerThirdTab { reports ->
        selectPreset("Welcome")
        selectPreset("Speaker Name")
        ltButton(LowerThirdLabel.GO_LIVE).performClick()
        waitForIdle()

        assertEquals(listOf("Speaker Name"), reports.live, "the last choice is what fires")
    }

    // ── Firing it ───────────────────────────────────────────────────────────────

    @Test
    fun `going live sends the animation itself, not just its name`() = lowerThirdTab { reports ->
        selectPreset("Welcome")
        ltButton(LowerThirdLabel.GO_LIVE).performClick()
        waitForIdle()

        assertEquals(listOf("Welcome"), reports.live)
        // The output is handed the Lottie JSON so it can play without going back to disk — a name
        // alone would leave the animation to be resolved again on the far side.
        assertEquals(LOWER_THIRD_LOTTIE, reports.liveJson)
    }

    @Test
    fun `adding to the schedule carries the preset and its pause settings`() =
        lowerThirdTab { reports ->
            selectPreset("Welcome")
            ltButton(LowerThirdLabel.ADD_TO_SCHEDULE).performClick()
            waitForIdle()

            val item = reports.scheduled.single()
            assertEquals("Welcome", item[1], "the label the schedule row shows")
            assertEquals(false, item[2], "pause-at-frame defaults off")
        }

    @Test
    fun `the same preset can be fired more than once`() = lowerThirdTab { reports ->
        // A lower third is often shown again later in the service.
        selectPreset("Welcome")
        ltButton(LowerThirdLabel.GO_LIVE).performClick()
        waitForIdle()
        ltButton(LowerThirdLabel.GO_LIVE).performClick()
        waitForIdle()

        assertEquals(listOf("Welcome", "Welcome"), reports.live)
    }

    // ── Play / Pause ────────────────────────────────────────────────────────────

    // These pause the test's own frame clock and advance it by exactly one frame instead of
    // calling waitForIdle(): with auto-advance on, waitForIdle() fast-forwards straight through
    // the whole tween in one go, so isPlaying is already back to false before the assertion runs
    // — a genuinely playing/paused state is real but transient, and only observable this way.

    @Test
    fun `pressing play starts the animation and offers to pause instead`() = lowerThirdTab { _ ->
        selectPreset("Welcome")
        assertTrue(hasLtButton(LowerThirdLabel.PLAY), "stopped to begin with")

        mainClock.autoAdvance = false
        ltButton(LowerThirdLabel.PLAY).performClick()
        mainClock.advanceTimeByFrame()

        assertTrue(hasLtButton(LowerThirdLabel.PAUSE), "the same button now offers to pause")
        assertFalse(hasLtButton(LowerThirdLabel.PLAY))
    }

    @Test
    fun `pressing pause stops it again`() = lowerThirdTab { _ ->
        selectPreset("Welcome")
        mainClock.autoAdvance = false
        ltButton(LowerThirdLabel.PLAY).performClick()
        mainClock.advanceTimeByFrame()

        ltButton(LowerThirdLabel.PAUSE).performClick()
        mainClock.advanceTimeByFrame()

        assertTrue(hasLtButton(LowerThirdLabel.PLAY), "back to offering a play")
    }

    @Test
    fun `playing through to the end and pressing play again restarts from the beginning`() =
        lowerThirdTab(folder = lottieFolderWithContent("Quick" to QUICK_LOTTIE)) { _ ->
            selectPreset("Quick")
            ltButton(LowerThirdLabel.PLAY).performClick()
            waitForIdle() // auto-advance fast-forwards the short animation to completion

            assertTrue(hasLtButton(LowerThirdLabel.PLAY), "must have finished and returned to Play")

            mainClock.autoAdvance = false
            ltButton(LowerThirdLabel.PLAY).performClick()
            mainClock.advanceTimeByFrame()

            assertTrue(hasLtButton(LowerThirdLabel.PAUSE), "pressing play again must restart it, not sit idle")
        }

    // ── Managing the folder ─────────────────────────────────────────────────────

    @Test
    fun `each preset has its own remove button`() = lowerThirdTab { _ ->
        assertEquals(
            2,
            onAllNodesWithContentDescription(LowerThirdLabel.REMOVE)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .size,
            "one per preset",
        )
    }

    @Test
    fun `the generator is offered even with no presets yet`() =
        lowerThirdTab(folder = lottieFolder()) { _ ->
            // Otherwise a new user with an empty folder has no way forward from this tab.
            assertTrue(showsExactly(LowerThirdLabel.GENERATE), "got ${renderedText()}")
        }

    // ── The preview ─────────────────────────────────────────────────────────────

    @Test
    fun `a portrait output's preview fits inside the tab instead of running over it`() = lowerThirdTab(
        // A 1080x1920 browser source, as the app would pick it for this tab's preview.
        previewOutput = PreviewOutput(
            key = "browser-source-0",
            label = "Browser Source 1",
            size = OutputSize(1080, 1920),
            showsMode = true,
            assignment = ScreenAssignment(browserSourceWidth = 1080, browserSourceHeight = 1920),
        ),
    ) { _ ->
        waitForIdle()
        val root = onRoot().getBoundsInRoot()
        val preview = onNodeWithTag(LOWER_THIRD_PREVIEW_TAG).getBoundsInRoot()

        // Keeping the full width made the box taller than the tab, centred, so its top ran up over
        // the header and the tab bar.
        assertTrue(preview.top >= root.top, "preview top ${preview.top} is above the tab")
        assertTrue(preview.bottom <= root.bottom, "preview bottom ${preview.bottom} is below the tab")
        assertTrue(
            preview.bottom - preview.top > preview.right - preview.left,
            "the preview keeps the output's portrait shape",
        )
    }

    @Test
    fun `dragging the list divider widens the list and remembers it`() =
        lowerThirdTab(folder = lottieFolder("Welcome")) { reports ->
            onNodeWithTag(LOWER_THIRD_LIST_DIVIDER_TAG).performTouchInput {
                down(center)
                moveBy(Offset(LIST_DRAG_PX, 0f))
                up()
            }
            waitForIdle()

            val saved = reports.settings!!.maximizedLayout.lowerThirdListWidthDp
            assertTrue(saved > WindowLayoutSettings().lowerThirdListWidthDp, "the dragged width is kept: $saved")
        }

    @Test
    fun `a preset whose animation will not load cannot go live`() =
        lowerThirdTab(folder = lottieFolderWithContent("Broken" to """{"v":"5.7.4","layers":[{"ty":}]}""")) { _ ->
            onAllNodesWithText("Broken")[0].performClick()
            waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                onAllNodesWithText(LowerThirdLabel.SELECT_PRESET).fetchSemanticsNodes().isEmpty()
            }
            waitForIdle()

            ltButton(LowerThirdLabel.GO_LIVE).assertIsNotEnabled()
        }

    @Test
    fun `the tab draws with only its settings, as a preview composes it`() = runComposeUiTest {
        setContent { LowerThirdTab(appSettings = AppSettings(), preRender = { _, _ -> }) }
        waitForIdle()

        assertTrue(showsContainingText(LowerThirdLabel.NO_FOLDER), "no folder is set: ${renderedText()}")
    }

    @Test
    fun `with nothing wired, the buttons are safe to press`() {
        val folder = lottieFolder("Welcome")
        try {
            runComposeUiTest {
                setContent {
                    val streaming = StreamingSettings(lowerThirdFolder = folder.absolutePath)
                    LowerThirdTab(appSettings = AppSettings(streamingSettings = streaming), preRender = { _, _ -> })
                }
                awaitPresetScan()
                selectPreset("Welcome")

                ltButton(LowerThirdLabel.ADD_TO_SCHEDULE).performClick()
                ltButton(LowerThirdLabel.GO_LIVE).performClick()
                onNodeWithText("Generate").performClick()
                onNodeWithTag(LOWER_THIRD_LIST_DIVIDER_TAG).performTouchInput {
                    down(center)
                    moveBy(Offset(LIST_DRAG_PX, 0f))
                    up()
                }
                waitForIdle()

                assertTrue(showsContainingText("Welcome"), "the preset is still open: ${renderedText()}")
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `hovering a preset names it in full`() =
        lowerThirdTab(folder = lottieFolder("Sunday morning welcome and notices")) { _ ->
            val name = "Sunday morning welcome and notices"
            val before = onAllNodesWithText(name).fetchSemanticsNodes().size

            onAllNodesWithText(name)[0].performMouseInput { moveTo(center) }
            mainClock.advanceTimeBy(TOOLTIP_DELAY_MS)
            waitForIdle()

            assertTrue(onAllNodesWithText(name).fetchSemanticsNodes().size > before, "the tooltip shows the name")
        }

    @Test
    fun `a failed remote upload is shown, then clears itself`() =
        lowerThirdTab(folder = lottieFolder("Welcome")) { _ ->
            val id = AtemUploadStatus.begin("Welcome", clip = false, slot = 1)
            try {
                AtemUploadStatus.fail(id, "slot is locked")
                waitForIdle()
                assertTrue(showsContainingText("slot is locked"), "a Stream Deck upload failed: ${renderedText()}")

                mainClock.advanceTimeBy(LOWER_THIRD_UPLOAD_ERROR_DISPLAY_MS + 1)
                waitForIdle()

                assertFalse(showsContainingText("slot is locked"), "the error goes after a while")
            } finally {
                AtemUploadStatus.clear(id)
            }
        }
}

private const val LIST_DRAG_PX = 60f

/** Past `TooltipArea`'s open delay. */
private const val TOOLTIP_DELAY_MS = 600L
