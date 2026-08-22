@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import kotlin.test.Test

class AppPreviewDictionaryScreenshotTest {

    @Test
    fun `the dictionary tab`() = appPreview("dictionary", Tabs.DICTIONARY) {
        onAllNodes(hasSetTextAction())[0].performTextReplacement("H2617")
        waitForIdle()
        // the search field also reads "H2617", so the row is addressed by its transliteration
        selectUntilFullyScanned()
        // Clearing the search puts all 14,197 entries back in the list; the selection survives it.
        onAllNodes(hasSetTextAction())[0].performTextReplacement("")
        waitForIdle()
        waitForTheListToSettle()
        goLive()
    }

    /**
     * Waits for the list to stop moving before the shot is taken.
     *
     * Clearing the search puts the selected entry ~2,600 rows down a 14,197-row list, and
     * `DictionaryTab` brings it back into view with `animateScrollToItem` — an animation, started
     * from a `LaunchedEffect`, that `waitForIdle` returns out of the middle of. The capture then
     * lands at whatever offset the scroll had reached, so a different set of rows (and a different
     * scrollbar thumb) is drawn on every run. It looks stable on a fast machine and is not: this is
     * the "row heights are not stable between runs" the screenshot notes recorded, and it is why
     * this picture kept turning up in the pipeline's diff for changes that never touched it.
     *
     * The settled position is its own signal — the selected row's top stops moving — so this ends
     * on that rather than on a pause, and fails loudly if the list never comes to rest.
     */
    private fun ComposeUiTest.waitForTheListToSettle() {
        val deadline = System.currentTimeMillis() + RENDER_TIMEOUT_MS
        var previous: Float? = null
        while (System.currentTimeMillis() < deadline) {
            waitForIdle()
            val top = onAllNodes(hasText("chêçêd", substring = true))
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .firstOrNull()?.boundsInRoot?.top
            if (top != null && top == previous) return
            previous = top
        }
        error("the dictionary list never stopped scrolling — the selected row sat at $previous")
    }

    /**
     * Selects the entry, and keeps re-selecting it until In Scripture reports the whole of it.
     *
     * The interlinear index loads in the background, and `DictionaryViewModel` queries it once, at
     * the moment an entry is selected — so a selection made while it is still loading gets whatever
     * had been indexed by then and keeps it. Observed here across runs: 8, 18, 80, 181 and 211
     * verses for the same entry, which is why this screenshot was a different picture on every
     * recording, by 4.2% of the image.
     *
     * Re-selecting re-queries, so the complete count is the load's own positive signal. It is a
     * fixed number because the data is fixed; the wait ends on it and the check below fails loudly
     * if it never arrives rather than capturing something half-scanned.
     */
    private fun ComposeUiTest.selectUntilFullyScanned() {
        val deadline = System.currentTimeMillis() + RENDER_TIMEOUT_MS
        var seen: String? = null
        while (System.currentTimeMillis() < deadline) {
            // The dictionary is read and parsed on `Dispatchers.IO`, which `waitForIdle` does not
            // wait for, so the row is not there on the first pass. Wait for the row itself rather
            // than for the composition — the deadline below is what fails if it never arrives.
            if (onAllNodes(hasText("chêçêd", substring = true))
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()) {
                waitForIdle()
                continue
            }
            onAllNodes(hasText("chêçêd", substring = true))[0].performClick()
            waitForIdle()
            seen = onAllNodesWithText(SCRIPTURE_COUNT_PREFIX, substring = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString() }
            if (seen == FULLY_SCANNED) return
        }
        error("In Scripture never reached \"$FULLY_SCANNED\" — it stopped at \"$seen\"")
    }

    private companion object {
        const val SCRIPTURE_COUNT_PREFIX = "Found in"

        /** What H2617 settles on once the whole interlinear index is loaded. */
        const val FULLY_SCANNED = "Found in 211 verses"
    }
}
