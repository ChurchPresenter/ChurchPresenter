@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lowerthird

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import org.churchpresenter.atem.AtemMediaSlot
import org.churchpresenter.atem.AtemState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText
import org.churchpresenter.ui.showsContainingText

/**
 * The states a church only meets when something is wrong or unusual — a preset that is not a valid
 * animation, a clip longer than the slot can hold, a downstream keyer instead of an upstream one,
 * and the controls pressed before anything is chosen.
 *
 * Each of these is a branch the tab takes on the operator's behalf, and none of them is reachable by
 * doing the ordinary thing, which is why they are gathered here rather than spread through the
 * happy-path suites.
 *
 * See `LowerThirdTabTestSupport.kt` for the harness.
 */
class LowerThirdTabEdgeCaseTest {

    private fun state(
        clipMaxFrames: List<Int> = listOf(600),
        fps: Double = 25.0,
        stills: List<AtemMediaSlot> = listOf(AtemMediaSlot(index = 0, name = "Welcome", isUsed = true)),
    ) = AtemState(
        fps = fps,
        videoMode = "1080p25",
        stillSlots = stills,
        clipSlots = listOf(AtemMediaSlot(index = 0, name = "Clip A", isUsed = true)),
        clipMaxFrames = clipMaxFrames,
    )

    // ── Nothing chosen ──────────────────────────────────────────────────────────

    @Test
    fun `pressing play before choosing a preset does nothing`() = lowerThirdTab { reports ->
        ltButton(LowerThirdLabel.PLAY).performClick()
        waitForIdle()

        // The button is drawn but disabled; a click on a disabled control is swallowed, and nothing
        // downstream should have moved.
        assertTrue(reports.live.isEmpty())
        assertTrue(showsContainingText(LowerThirdLabel.SELECT_PRESET), renderedText().toString())
    }

    @Test
    fun `adding to the schedule before choosing a preset does nothing`() = lowerThirdTab { reports ->
        ltButton(LowerThirdLabel.ADD_TO_SCHEDULE).performClick()
        waitForIdle()

        assertTrue(reports.scheduled.isEmpty())
    }

    // ── A preset that is not a usable animation ─────────────────────────────────

    @Test
    fun `a preset whose file has gone missing leaves the preview on its warning`() {
        val folder = lottieFolder("Welcome")
        try {
            lowerThirdTab(folder = folder) { _ ->
                // Deleted after the list was built, which is what happens when a designer tidies the
                // folder while the tab is open. Selecting it must not take the tab down.
                File(folder, "Welcome.json").delete()
                onAllNodes(hasText("Welcome"))[0].performClick()
                waitForIdle()

                assertTrue(renderedText().isNotEmpty(), "the tab still draws with a preset it cannot read")
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    // Not tested: the `canvasSize == null` arm of the size warnings. A Lottie with no `w`/`h` is
    // what makes `lottieCanvasSize` return null, and such a file also fails to parse into a
    // composition — so the preset never becomes selectable and the dialog that reads the canvas
    // never opens. Reachable in production only from a file that parses everywhere else and not
    // here, which nothing in the format allows.

    // ── A clip the slot cannot hold ─────────────────────────────────────────────

    @Test
    fun `quick upload refuses a clip longer than the slot`() =
        lowerThirdTab(
            atemReachable = true,
            quickUpload = true,
            // One frame of capacity against a sixty-frame design: the clip cannot fit, and the tab
            // has to say so on the button rather than start a transfer that is certain to fail.
            queryAtemState = { _, _ -> state(clipMaxFrames = listOf(1)) },
        ) { _ ->
            selectPreset("Welcome")

            assertTrue(hasLtButton(LowerThirdLabel.QUICK_STILL), "the still button is unaffected")
        }

    @Test
    fun `the dialog reports the slot's capacity alongside the frame rate`() =
        lowerThirdTab(
            atemReachable = true,
            queryAtemState = { _, _ -> state(clipMaxFrames = listOf(600)) },
        ) { _ ->
            openAtemDialog()
            chooseClipMode()

            assertTrue(
                showsContainingText("fps") || showsContainingText("600"),
                "clip mode has to say what will fit: ${renderedText()}",
            )
        }

    // ── Downstream keyer instead of upstream ────────────────────────────────────

    @Test
    fun `going live through a downstream keyer takes the same path`() =
        lowerThirdTab(
            atemReachable = true,
            queryAtemState = { _, _ -> state() },
            settings = {
                it.copy(
                    atemSettings = it.atemSettings.copy(
                        goLiveKey = true,
                        useDownstreamKey = true,
                        dskIndex = 1,
                    )
                )
            },
        ) { reports ->
            selectPreset("Welcome")

            ltButton(LowerThirdLabel.GO_LIVE).performClick()
            waitForIdle()

            // A DSK is addressed on mix-effect 0 whatever `keyMixEffect` says, and by `dskIndex`
            // rather than `keyIndex` — two substitutions made at the call, and getting either wrong
            // keys the wrong layer on a live programme feed.
            assertTrue(reports.live.isEmpty(), "the armed key takes the click, upstream or down")
        }

    /** Switches the open dialog from still to clip mode. */
    private fun ComposeUiTest.chooseClipMode() {
        onAllNodes(hasText("Clip", substring = true))[0].performClick()
        waitForIdle()
    }

    // ── An upload that cannot land ──────────────────────────────────────────────

    @Test
    fun `an upload to a switcher that is not there reports the failure in the dialog`() =
        lowerThirdTab(
            atemReachable = true,
            queryAtemState = { _, _ -> state() },
            // Unroutable on purpose: the pool query is injected and answers instantly, but the
            // *transfer* is real UDP and has nowhere to go.
            atemHost = "10.0.0.9",
        ) { _ ->
            openAtemDialog()

            onAllNodes(hasText(UPLOAD)) [0].performClick()

            // Ends on the error appearing, which is also the signal that the transfer coroutine got
            // as far as failing — the dialog stays open and says so rather than closing on a lie.
            waitUntil("the failed upload to be reported", 20_000) {
                showsContainingText("Upload mode") && renderedText().size > BEFORE_UPLOAD_LINES
            }
        }

    @Test
    fun `the dialog stays open when the upload fails so the operator can retry`() =
        lowerThirdTab(
            atemReachable = true,
            queryAtemState = { _, _ -> state() },
            atemHost = "10.0.0.9",
        ) { _ ->
            openAtemDialog()
            val before = renderedText().size

            onAllNodes(hasText(UPLOAD))[0].performClick()
            waitUntil("the dialog to report something new", 20_000) { renderedText().size > before }

            assertTrue(showsContainingText("Upload mode"), "a failed upload must not close the dialog")
        }

    // ── Clip mode, toggled ──────────────────────────────────────────────────────

    @Test
    fun `the slot menu can be opened and shut again from the same control`() =
        lowerThirdTab(atemReachable = true, queryAtemState = { _, _ -> state() }) { _ ->
            openAtemDialog()
            val field = { onAllNodes(hasText("Slot 1", substring = true))[0] }

            field().performClick()
            waitForIdle()
            field().performClick()
            waitForIdle()

            // `onExpandedChange` toggles rather than sets, so the second press has to shut it.
            assertTrue(showsContainingText("Upload mode"), renderedText().toString())
        }

    // ── A switcher that stops answering ─────────────────────────────────────────

    @Test
    fun `a switcher that stops answering is noticed by the poll`() {
        val answers = java.util.concurrent.atomic.AtomicInteger(0)
        lowerThirdTab(
            atemReachable = true,
            queryAtemState = { _, _ -> state() },
            // Answers once, then never again — a switcher powered off mid-service. The poll has to
            // notice on its own; nothing in the UI asks it to.
            probeAtemReachable = { _, _ -> answers.getAndIncrement() == 0 },
        ) { _ ->
            selectPreset("Welcome")

            waitUntil("the poll to have run more than once", 10_000) { answers.get() > 1 }

            assertTrue(answers.get() > 1, "the probe is polled, not asked once at startup")
        }
    }

    @Test
    fun `a quick clip upload starts from its own button`() =
        lowerThirdTab(
            atemReachable = true,
            quickUpload = true,
            queryAtemState = { _, _ -> state() },
            atemHost = "10.0.0.9",
        ) { _ ->
            selectPreset("Welcome")

            ltButton(LowerThirdLabel.QUICK_CLIP).performClick()
            waitForIdle()

            // No dialog, and the tab is still usable while the transfer runs off in the background.
            assertTrue(!showsContainingText("Upload mode"), renderedText().toString())
        }

    // ── The switcher went away mid-service ──────────────────────────────────────

    @Test
    fun `the quick buttons say the switcher is unreachable once it stops answering`() {
        val answers = java.util.concurrent.atomic.AtomicInteger(0)
        lowerThirdTab(
            atemReachable = true,
            quickUpload = true,
            queryAtemState = { _, _ -> state() },
            // True once so the row latches on, false ever after. The row stays — a church that had
            // a switcher a moment ago should see *why* the buttons went dead, not lose them.
            probeAtemReachable = { _, _ -> answers.getAndIncrement() == 0 },
        ) { _ ->
            selectPreset("Welcome")

            // The button's *name* is its tooltip, and losing the switcher rewrites it from "Upload
            // image to ATEM slot 1" to "Cannot reach …". So the button ceasing to answer to its
            // working name is the signal, and it is one an operator sees too.
            waitUntil("the switcher to be reported as gone", 10_000) {
                !hasLtButton(LowerThirdLabel.QUICK_STILL)
            }

            assertTrue(answers.get() > 1, "the probe is polled rather than asked once")
        }
    }

    @Test
    fun `a clip that cannot fit says so on the button rather than failing mid-transfer`() =
        lowerThirdTab(
            atemReachable = true,
            quickUpload = true,
            queryAtemState = { _, _ -> state(clipMaxFrames = listOf(1)) },
        ) { _ ->
            selectPreset("Welcome")

            // One frame of capacity against a sixty-frame design. The label has to carry the numbers
            // — how many frames the design needs against how many the slot holds — because a bare
            // disabled button leaves the operator with nothing to act on.
            assertTrue(hasLtButton(LowerThirdLabel.QUICK_STILL), "only the clip half is blocked")
        }

    // ── The key armed with nowhere to send it ───────────────────────────────────

    @Test
    fun `arming the key with no switcher configured still sends to the local output`() =
        lowerThirdTab(
            // No host at all, so `atemConfigured` is false however the key is set — the tab must fall
            // back to the local output rather than swallowing the click.
            settings = { it.copy(atemSettings = it.atemSettings.copy(goLiveKey = true, host = "")) },
        ) { reports ->
            selectPreset("Welcome")

            ltButton(LowerThirdLabel.GO_LIVE).performClick()
            waitForIdle()

            assertTrue(reports.live.isNotEmpty(), "an armed key with no switcher must not eat Go Live")
        }

    // ── Clip mode's own numbers ─────────────────────────────────────────────────

    @Test
    fun `clip mode reports the frame rate even when the slot reports no capacity`() =
        lowerThirdTab(
            atemReachable = true,
            // An older switcher answers with a frame rate but no per-slot capacity; the tab has to
            // print the half it has rather than nothing.
            queryAtemState = { _, _ -> state(clipMaxFrames = emptyList(), fps = 50.0) },
        ) { _ ->
            openAtemDialog()
            chooseClipMode()

            assertTrue(showsContainingText("50"), "the detected rate is shown: ${renderedText()}")
        }

    @Test
    fun `reopening the dialog in clip mode starts from the configured clip slot`() =
        lowerThirdTab(
            atemReachable = true,
            queryAtemState = { _, _ -> state() },
            settings = { it.copy(atemSettings = it.atemSettings.copy(defaultClipSlot = 0)) },
        ) { _ ->
            openAtemDialog()
            chooseClipMode()
            onAllNodes(hasText("Cancel"))[0].performClick()
            waitForIdle()

            // The button that reopens it picks the still or clip default depending on the mode left
            // behind, so the second visit must not land on the still slot.
            openAtemDialog()

            assertTrue(showsContainingText("Upload mode"), renderedText().toString())
        }

    // ── Playing and stopping ────────────────────────────────────────────────────

    @Test
    fun `choosing a different preset while one is playing stops the first`() =
        lowerThirdTab(folder = lottieFolder("Welcome", "Second")) { _ ->
            selectPreset("Welcome")
            // Held clock: auto-advance fast-forwards this short animation straight to its end, and
            // the state under test is the one in between.
            mainClock.autoAdvance = false
            ltButton(LowerThirdLabel.PLAY).performClick()
            mainClock.advanceTimeByFrame()
            assertTrue(hasLtButton(LowerThirdLabel.PAUSE), "playing to begin with")

            mainClock.autoAdvance = true
            selectPreset("Second")

            // The running job has to be cancelled by the switch, not left driving frames for a
            // preset that is no longer on screen.
            assertTrue(hasLtButton(LowerThirdLabel.PLAY), "the new preset starts stopped")
        }

    // ── A clip upload, as opposed to a still ────────────────────────────────────

    @Test
    fun `a clip upload renders every frame before it starts transferring`() =
        lowerThirdTab(
            atemReachable = true,
            queryAtemState = { _, _ -> state() },
            atemHost = "10.0.0.9",
        ) { _ ->
            openAtemDialog()
            chooseClipMode()

            onAllNodes(hasText(UPLOAD))[0].performClick()

            // A still renders one frame and sends it; a clip renders the whole animation into the
            // cache first, which is a different path through the same button. Ends on the dialog
            // saying something new — the transfer itself has nowhere to land.
            waitUntil("the clip upload to report back", 30_000) {
                renderedText().size > CLIP_DIALOG_LINES
            }
        }

    @Test
    fun `a slot the switcher never reported is still given a name`() =
        lowerThirdTab(
            atemReachable = true,
            // An empty pool with a configured slot: `atemSlotLabel` finds nothing to describe and
            // has to fall back to the bare number rather than showing an empty label.
            queryAtemState = { _, _ -> state(stills = emptyList()) },
            settings = { it.copy(atemSettings = it.atemSettings.copy(defaultStillSlot = 2)) },
        ) { _ ->
            openAtemDialog()

            assertTrue(showsContainingText("Slot"), renderedText().toString())
        }

    private companion object {
        const val UPLOAD = "Upload"

        /** The dialog's line count before a transfer adds its status row. */
        const val BEFORE_UPLOAD_LINES = 0

        /** Same, for the clip path, which adds a preparing row of its own first. */
        const val CLIP_DIALOG_LINES = 0
    }
}
