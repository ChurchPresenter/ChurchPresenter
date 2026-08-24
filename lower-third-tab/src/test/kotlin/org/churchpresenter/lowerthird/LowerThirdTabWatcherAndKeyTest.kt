@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lowerthird

import androidx.compose.ui.test.performClick
import org.churchpresenter.atem.AtemMediaSlot
import org.churchpresenter.atem.AtemState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText
import org.churchpresenter.ui.showsContainingText

/**
 * The two things the tab does on its own: watching the preset folder, and driving the switcher's
 * upstream key when a lower third goes live.
 *
 * Both matter because the operator does not trigger them. A designer saving a new file into the
 * folder — from the bundled generator or from anywhere else — should see it appear without anyone
 * reopening the tab. And arming the key changes what Go Live *is*: it drives the switcher instead of
 * the local output, not as well as it, which is worth having written down.
 *
 * See `LowerThirdTabTestSupport.kt` for the harness.
 */
class LowerThirdTabWatcherAndKeyTest {

    private fun state(
        stills: List<AtemMediaSlot> = listOf(
            AtemMediaSlot(index = 0, name = "Welcome", isUsed = true),
            AtemMediaSlot(index = 1, name = "", isUsed = true),
            AtemMediaSlot(index = 2, name = "", isUsed = false),
        ),
    ) = AtemState(
        fps = 25.0,
        videoMode = "1080p25",
        stillSlots = stills,
        clipSlots = listOf(AtemMediaSlot(index = 0, name = "Clip A", isUsed = true)),
        clipMaxFrames = listOf(600),
    )

    // ── The folder watcher ──────────────────────────────────────────────────────

    @Test
    fun `a Lottie dropped into the folder appears without reopening the tab`() {
        val folder = lottieFolder("Welcome")
        try {
            lowerThirdTab(folder = folder) { _ ->
                File(folder, "Dropped In.json").writeText(lottieSized(320, 180))

                // The watcher is a real `WatchService` on a background thread, so the wait ends on
                // the row appearing rather than on a fixed pause.
                waitUntil("the new preset to be picked up", 10_000) {
                    showsContainingText("Dropped In")
                }
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `a non-Lottie file dropped into the folder is not offered as a preset`() {
        val folder = lottieFolder("Welcome")
        try {
            lowerThirdTab(folder = folder) { _ ->
                File(folder, "notes.txt").writeText("nothing to do with lower thirds")
                File(folder, "Also Dropped.json").writeText(lottieSized(320, 180))

                // Waiting on the *Lottie* is what makes this safe: by the time it shows, the
                // watcher has been through the batch that carried the text file too.
                waitUntil("the new preset to be picked up", 10_000) {
                    showsContainingText("Also Dropped")
                }

                assertTrue(!showsContainingText("notes"), renderedText().toString())
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    // ── How a slot is described ─────────────────────────────────────────────────

    @Test
    fun `a used slot with no name says so rather than looking empty`() =
        lowerThirdTab(atemReachable = true, queryAtemState = { _, _ -> state() }) { _ ->
            openAtemDialog()
            openSlotDropdown()

            assertTrue(fieldShows("in use"), "slot 2 holds something unnamed: ${renderedText()}")
            assertTrue(fieldShows("empty"), "slot 3 holds nothing: ${renderedText()}")
        }

    @Test
    fun `a configured slot the switcher does not report is still named`() =
        lowerThirdTab(
            atemReachable = true,
            queryAtemState = { _, _ -> state(stills = listOf(AtemMediaSlot(index = 4, name = "Far", isUsed = true))) },
        ) { _ ->
            openAtemDialog()

            // The pool starts at slot 5, so the configured default is not in it; the dialog snaps to
            // one that exists rather than showing a slot the switcher has never heard of.
            assertTrue(fieldShows("Far"), renderedText().toString())
        }

    // ── Go Live with the key armed ──────────────────────────────────────────────

    @Test
    fun `going live with the key armed drives the switcher instead of the local output`() =
        lowerThirdTab(
            atemReachable = true,
            queryAtemState = { _, _ -> state() },
            settings = { it.copy(atemSettings = it.atemSettings.copy(goLiveKey = true)) },
        ) { reports ->
            selectPreset("Welcome")

            ltButton(LowerThirdLabel.GO_LIVE).performClick()
            waitForIdle()

            // Pinning what the tab *does*, which is an either/or: with the key armed the click runs
            // `LowerThirdSequencer` and `onGoLive` is not called at all. Worth knowing, because it
            // means a church that arms the key and then loses the switcher gets nothing on the local
            // output either — the two paths are exclusive rather than additive.
            assertTrue(reports.live.isEmpty(), "the armed key takes the click: ${reports.live}")
        }

    @Test
    fun `going live with the key disarmed sends to the local output`() =
        lowerThirdTab(
            atemReachable = true,
            queryAtemState = { _, _ -> state() },
            settings = { it.copy(atemSettings = it.atemSettings.copy(goLiveKey = false)) },
        ) { reports ->
            selectPreset("Welcome")

            ltButton(LowerThirdLabel.GO_LIVE).performClick()
            waitForIdle()

            assertEquals(listOf("Welcome"), reports.live)
            assertTrue(reports.liveJson?.isNotBlank() == true, "the animation itself, not just its name")
        }

    // Not tested, and worth saying why:
    //
    //  * The key *failure* — `LowerThirdSequencer.run` against an unroutable switcher does not come
    //    back inside 30s, so an assertion on `atemError` would be a test whose cost is a timeout.
    //  * The no-declared-duration fallback (`lottieDurationMs ?: totalDurationMs`) — a Lottie with
    //    no `ip`/`op` does not parse into a composition either, so the preset never becomes
    //    selectable and the Go Live button it guards is never enabled.

    /**
     * Opens the slot dropdown.
     *
     * Matched on "Slot 1" — the label `atemSlotLabel` builds for whichever slot is chosen — rather
     * than on "Slot", which is also the caption above the control.
     */
    private fun androidx.compose.ui.test.ComposeUiTest.openSlotDropdown() {
        onAllNodes(androidx.compose.ui.test.hasText("Slot 1", substring = true))[0].performClick()
        waitForIdle()
    }

    /** Whether any field reads [text] — the slot controls hold theirs in `EditableText`. */
    private fun androidx.compose.ui.test.ComposeUiTest.fieldShows(text: String): Boolean =
        onAllNodes(androidx.compose.ui.test.hasText(text, substring = true))
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .isNotEmpty()
}
