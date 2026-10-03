@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BibleTabHoldPillTest {

    private fun ComposeUiTest.clickable() =
        onAllNodes(hasText("⌘/Ctrl Shift") and hasClickAction())
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .isNotEmpty()

    @Test
    fun `clicking the hold pill toggles Bible Hold and notifies Instance Link`() {
        val presenter = FakeBibleOutput()
        var sentHold: Boolean? = null
        bibleTab(presenter = presenter, onInstanceLinkSendBibleHold = { sentHold = it }) { _, _ ->
            assertFalse(presenter.bibleHold.value)

            onNodeWithText("⌘/Ctrl Shift").performClick()
            waitForIdle()

            assertTrue(presenter.bibleHold.value)
            assertEquals(true, sentHold)

            onNodeWithText("⌘/Ctrl Shift").performClick()
            waitForIdle()

            assertFalse(presenter.bibleHold.value)
            assertEquals(false, sentHold)
        }
    }

    @Test
    fun `with no presenter manager the pill is not clickable`() = bibleTab { _, _ ->
        assertFalse(clickable())
    }

    @Test
    fun `in split browse mode the pill is not clickable even with a presenter manager`() {
        val presenter = FakeBibleOutput()
        bibleTab(
            settings = { it.copy(bibleSettings = it.bibleSettings.copy(splitBrowseMode = true)) },
            presenter = presenter,
        ) { _, _ ->
            assertFalse(clickable())
        }
    }

    @Test
    fun `with no Instance Link callback wired, the click still toggles Bible Hold locally`() {
        val presenter = FakeBibleOutput()
        bibleTab(presenter = presenter) { _, _ ->
            onNodeWithText("⌘/Ctrl Shift").performClick()
            waitForIdle()

            assertTrue(presenter.bibleHold.value)
        }
    }

    // ── What the pill says it is for ────────────────────────────────────────────

    private fun ComposeUiTest.countOf(text: String) =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes(false).size

    /** The tooltip body only composes while the pointer is over the pill. */
    private fun ComposeUiTest.hoverPill() {
        onNodeWithText("⌘/Ctrl Shift").performMouseInput { moveTo(center) }
        waitForIdle()
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
    }

    @Test
    fun `without a presenter the pill explains the keyboard selection instead`() {
        // The pill does double duty: with an output attached it is the Hold Live toggle, and without
        // one it is nothing but a legend for Ctrl/Shift click — which is the only place multi-verse
        // selection is explained at all.
        bibleTab { _, _ ->
            val before = countOf(SELECTION_HINT)

            hoverPill()

            assertEquals(before + 1, countOf(SELECTION_HINT))
        }
    }

    @Test
    fun `with a presenter it names the hold instead of the shortcut`() {
        // The positive twin: the two branches share one tooltip, so a pill stuck on either string
        // would still pass the other test on its own.
        bibleTab(presenter = FakeBibleOutput()) { _, _ ->
            val beforeHold = countOf("Hold Live")
            val beforeHint = countOf(SELECTION_HINT)

            hoverPill()

            assertEquals(beforeHold + 1, countOf("Hold Live"))
            assertEquals(beforeHint, countOf(SELECTION_HINT), "the shortcut legend belongs to the other state")
        }
    }

    private companion object {
        /**
         * Asserted as a literal, deliberately. It was a hardcoded English string in `BibleTab` until
         * this change moved it into `strings.xml`, and spelling it out here is what proves the
         * resource still renders the same words rather than an empty or mangled lookup.
         */
        const val SELECTION_HINT = "Ctrl+Click to toggle, Shift+Click for range"
    }
}
