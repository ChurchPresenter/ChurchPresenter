@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lowerthird

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.atem.AtemMediaSlot
import org.churchpresenter.atem.AtemState
import kotlin.test.Test
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText
import org.churchpresenter.ui.showsContainingText

/**
 * The ATEM upload dialog — where a lower third is sent into a switcher's media pool before it can be
 * keyed over the programme feed.
 *
 * **This is the part of the tab that was unreachable.** The dialog builds itself from a live query of
 * the switcher, and that query is UDP with a **5s socket timeout**: no fast connection-refused, so
 * every test that opened it used to cost five seconds whether or not hardware existed. That timeout,
 * not the missing hardware, is what capped this tab at ~42% and got it written off as blocked.
 *
 * With the query injected, all of it runs instantly and deterministically — including the states no
 * hardware could reliably reproduce on demand: an unreachable switcher, a pool whose slots do not
 * include the configured default, and a device reporting a different frame rate than the settings
 * assume.
 *
 * See `LowerThirdTabTestSupport.kt` for the harness.
 */
class LowerThirdAtemDialogTest {

    /** The upload button's tooltip, which is also its content description. */

    private fun state(
        fps: Double = 25.0,
        stills: List<AtemMediaSlot> = listOf(
            AtemMediaSlot(index = 0, name = "Welcome", isUsed = true),
            AtemMediaSlot(index = 1, name = "", isUsed = false),
        ),
        clips: List<AtemMediaSlot> = listOf(AtemMediaSlot(index = 0, name = "Clip A", isUsed = true)),
        clipMaxFrames: List<Int> = listOf(600),
    ) = AtemState(
        fps = fps,
        videoMode = "1080p25",
        stillSlots = stills,
        clipSlots = clips,
        clipMaxFrames = clipMaxFrames,
    )

    // openAtemDialog() now lives in LowerThirdTabTestSupport.kt, shared with LowerThirdAtemDialogExtraTest.

    @Test
    fun `the dialog opens and offers both upload modes`() =
        lowerThirdTab(atemReachable = true, queryAtemState = { _, _ -> state() }) { _ ->
            openAtemDialog()

            onNodeWithText("Send to ATEM").assertExists("the dialog must open")
            onNodeWithText("Upload mode").assertExists("still or clip is the first choice")
        }

    @Test
    fun `the switcher's still slots are listed with their names`() =
        lowerThirdTab(atemReachable = true, queryAtemState = { _, _ -> state() }) { _ ->
            openAtemDialog()

            // The operator picks a slot by what is already in it — overwriting the wrong one replaces
            // a graphic that may be live in another scene.
            assertTrue(
                showsContainingText("Welcome"),
                "an occupied slot must show what occupies it: ${renderedText()}",
            )
        }

    @Test
    fun `an unreachable switcher is reported rather than silently empty`() =
        lowerThirdTab(atemReachable = true, queryAtemState = { _, _ -> error("connection refused") }) { _ ->
            openAtemDialog()

            // The failure path is the one that matters most: without it the dialog would show an
            // empty slot list, which looks like a switcher with no media rather than no switcher.
            assertTrue(
                showsContainingText("Could not load slots") || showsContainingText("connection refused"),
                "the error must reach the operator: ${renderedText()}",
            )
        }

    @Test
    fun `switching to clip mode re-queries and lists the clip slots instead`() {
        val queried = mutableListOf<String>()
        lowerThirdTab(
            atemReachable = true,
            queryAtemState = { host, _ ->
                queried += host
                state()
            },
        ) { _ ->
            openAtemDialog()
            val afterOpen = queried.size

            // The clip row. Both modes are LabeledRadioButtons now, so the row -- control and label
            // together -- is the one selectable node, and clicking anywhere on it selects the mode.
            onAllNodes(isSelectable())[1].performClick()
            waitForIdle()

            // Stills and clips are separate pools, so the mode toggle has to go back to the device
            // rather than filter what it already has.
            assertTrue(queried.size > afterOpen, "changing mode must re-read the pool")
            // The chosen slot sits in a field, so it is EditableText and never appears in
            // renderedText(); onNodeWithText matches it.
            onNodeWithText("Clip A", substring = true).assertExists()
        }
    }

    @Test
    fun `the dialog can be dismissed`() =
        lowerThirdTab(atemReachable = true, queryAtemState = { _, _ -> state() }) { _ ->
            openAtemDialog()
            onNodeWithText("Send to ATEM").assertExists()

            onNodeWithText("Cancel").performClick()
            waitForIdle()

            onNode(hasText("Upload mode")).assertDoesNotExist()
        }

    @Test
    fun `a pool that does not contain the configured slot snaps to one that exists`() =
        lowerThirdTab(
            atemReachable = true,
            // The default slot is 0; this device's pool starts at 4, which happens when a switcher
            // is shared between sites or the settings were copied from another machine.
            queryAtemState = { _, _ ->
                state(stills = listOf(AtemMediaSlot(index = 4, name = "Site B", isUsed = true)))
            },
        ) { _ ->
            openAtemDialog()

            // Without the snap the dialog would sit on a slot the device does not have and the
            // upload would fail at the far end, after the transfer. The slot field is EditableText,
            // so this is onNodeWithText rather than a renderedText() scan.
            onNodeWithText("Site B", substring = true).assertExists()
        }
}
