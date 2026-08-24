@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lowerthird

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import org.churchpresenter.atem.AtemMediaSlot
import org.churchpresenter.atem.AtemState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText
import org.churchpresenter.ui.showsContainingText

/**
 * The controls around the preset list — the generator button, the panel's drag handle, the ATEM
 * key toggle and the one-click upload pair.
 *
 * These are the parts of the tab that do something to the *rest of the app* rather than to the
 * animation: they open another window, write a panel width into the saved layout, arm the switcher's
 * upstream key, or start a transfer without going through the dialog.
 *
 * See `LowerThirdTabTestSupport.kt` for the harness.
 */
class LowerThirdTabControlsTest {

    private fun state() = AtemState(
        fps = 25.0,
        videoMode = "1080p25",
        stillSlots = listOf(AtemMediaSlot(index = 0, name = "Welcome", isUsed = true)),
        clipSlots = listOf(AtemMediaSlot(index = 0, name = "Clip A", isUsed = true)),
        clipMaxFrames = listOf(600),
    )

    // ── The generator ───────────────────────────────────────────────────────────

    @Test
    fun `Generate opens the generator pointed at the preset folder`() = lowerThirdTab { reports ->
        clickGenerate()

        assertEquals(1, reports.generatorOpenedFor.size)
        assertTrue(
            reports.generatorOpenedFor.single().isNotEmpty(),
            "the generator has to be told where to save, or its output lands nowhere the tab looks",
        )
    }

    @Test
    fun `a lower third saved by the generator appears in the list without reopening the tab`() =
        lowerThirdTab(folder = lottieFolder("Welcome")) { reports ->
            clickGenerate()
            val onSaved = requireNotNull(reports.generatorOnFileSaved) { "the generator was given no callback" }

            // The generator writes into the folder the tab is watching and then calls back; that
            // callback is the only thing that makes the new file show up.
            File(reports.generatorOpenedFor.single(), "Second.json").writeText(lottieSized(320, 180))
            onSaved()
            waitForIdle()

            assertTrue(showsContainingText("Second"), renderedText().toString())
        }

    // ── The drag handle ─────────────────────────────────────────────────────────

    @Test
    fun `dragging the handle saves the new panel width into the maximised layout`() =
        lowerThirdTab(isWindowMaximized = true) { reports ->
            val before = reports.settingsChanges

            dragHandle(by = 60f)

            assertTrue(
                reports.settingsChanges > before,
                "letting go of the handle has to persist the width, or it resets on the next launch",
            )
        }

    @Test
    fun `dragging the handle in a windowed main window saves the windowed layout instead`() =
        lowerThirdTab(isWindowMaximized = false) { reports ->
            val before = reports.settingsChanges

            dragHandle(by = 60f)

            // The two layouts are stored separately: a width chosen while windowed must not follow
            // the operator into full screen, where the panel has room it does not need.
            assertTrue(reports.settingsChanges > before)
        }

    // ── The ATEM key toggle ─────────────────────────────────────────────────────

    @Test
    fun `the key toggle arms and disarms going live on the switcher`() =
        lowerThirdTab(atemReachable = true, queryAtemState = { _, _ -> state() }) { reports ->
            selectPreset("Welcome")

            ltButton(LowerThirdLabel.GO_LIVE_KEY).performClick()
            waitForIdle()
            val afterArming = reports.settingsChanges

            ltButton(LowerThirdLabel.GO_LIVE_KEY).performClick()
            waitForIdle()

            assertTrue(afterArming > 0, "arming the key is a setting, not a one-off")
            assertTrue(reports.settingsChanges > afterArming, "and so is disarming it again")
        }

    @Test
    fun `the key toggle starts from whatever the settings already say`() =
        lowerThirdTab(
            atemReachable = true,
            queryAtemState = { _, _ -> state() },
            settings = { it.copy(atemSettings = it.atemSettings.copy(goLiveKey = true, host = "10.0.0.9")) },
        ) { _ ->
            selectPreset("Welcome")

            assertTrue(hasLtButton(LowerThirdLabel.GO_LIVE_KEY), "an armed key is still a button, not a fixture")
        }

    // ── One-click upload ────────────────────────────────────────────────────────

    @Test
    fun `the quick still button uploads without opening the dialog`() =
        lowerThirdTab(
            atemReachable = true,
            quickUpload = true,
            queryAtemState = { _, _ -> state() },
        ) { _ ->
            selectPreset("Welcome")

            ltButton(LowerThirdLabel.QUICK_STILL).performClick()
            waitForIdle()

            // The unroutable default host means the transfer cannot land; what is being pinned is
            // that the button starts one at all rather than opening the dialog.
            assertTrue(
                !showsContainingText("Upload mode"),
                "quick upload must not open the dialog: ${renderedText()}",
            )
        }

    @Test
    fun `the quick clip button is offered beside the still one`() =
        lowerThirdTab(
            atemReachable = true,
            quickUpload = true,
            queryAtemState = { _, _ -> state() },
        ) { _ ->
            selectPreset("Welcome")

            assertTrue(hasLtButton(LowerThirdLabel.QUICK_STILL))
            assertTrue(hasLtButton(LowerThirdLabel.QUICK_CLIP))
        }

    // ── Driving ─────────────────────────────────────────────────────────────────

    private fun ComposeUiTest.clickGenerate() {
        onAllNodes(hasText(LowerThirdLabel.GENERATE) and hasClickAction())[0].performClick()
        waitForIdle()
    }

    /**
     * Drags the panel's resize handle by [by] pixels and lets go.
     *
     * The handle is a 6dp `Box` with a `draggable` modifier and no semantics of its own, so it is
     * found by shape: the only node that narrow running the full height of the tab. `onDragStopped`
     * is what saves the width, so the gesture has to end with a release, not just a move.
     */
    private fun ComposeUiTest.dragHandle(by: Float) {
        // Generate spans the preset panel, so its right edge *is* the panel's — and the handle sits
        // immediately beyond it, past a 1dp divider. Derived rather than guessed at a fraction of
        // the width, which depends on a default panel size this test has no business knowing.
        val panel = onAllNodes(hasText(LowerThirdLabel.GENERATE) and hasClickAction())[0]
            .fetchSemanticsNode().boundsInRoot
        val handleX = panel.right + GENERATE_PADDING_PX + HANDLE_CENTRE_OFFSET_PX
        val y = onRoot().fetchSemanticsNode().size.height / 2f
        onRoot().performMouseInput {
            moveTo(Offset(handleX, y))
            press()
            moveTo(Offset(handleX + by, y))
            release()
        }
        waitForIdle()
    }

    private companion object {
        /** Generate is inset from the panel edge by `padding(horizontal = 12.dp)`. */
        const val GENERATE_PADDING_PX = 12f

        /** Past the 1dp divider, into the middle of the 6dp handle. */
        const val HANDLE_CENTRE_OFFSET_PX = 4f
    }
}
