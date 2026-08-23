@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.churchpresenter.ui.showsExactly

/**
 * Split-browse mode: the right-hand live-chapter panel that lets the operator read ahead in the
 * chapter that is on screen while browsing somewhere else on the left.
 *
 * See `BibleTabTestSupport.kt` for the harness.
 */
class BibleTabSplitViewTest {

    /** The harness with split-browse mode turned on. */
    private fun splitBibleTab(
        presenter: PresenterManager? = null,
        block: ComposeUiTest.(vm: BibleViewModel, reports: BibleReports) -> Unit,
    ) = bibleTab(
        settings = { it.copy(bibleSettings = it.bibleSettings.copy(splitBrowseMode = true)) },
        presenter = presenter,
        block = block,
    )

    private fun ComposeUiTest.press(key: Key) {
        onRoot().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    @Test
    fun `the live panel is not shown unless split browse is on`() = bibleTab { _, _ ->
        assertEquals(
            1,
            countOnScreen("1. In the beginning God created the heaven and the earth."),
            "one copy of the verse — the browser's",
        )
    }

    @Test
    fun `split browse shows the live chapter beside the browser`() = splitBibleTab { _, _ ->
        // Seeded from the opening selection so the panel isn't blank before the first go-live.
        assertEquals(
            2,
            countOnScreen("1. In the beginning God created the heaven and the earth."),
            "the same verse in both the browser and the live panel",
        )
    }

    @Test
    fun `the live panel keeps showing the live chapter while the browser moves away`() =
        splitBibleTab { vm, _ ->
            onNodeWithText("John").performClick()
            waitForIdle()

            assertEquals(2, vm.selectedBookIndex.value, "the browser moved to John")
            assertTrue(
                showsExactly("1. In the beginning was the Word."),
                "John 1 is in the browser",
            )
            assertEquals(
                1,
                countOnScreen("1. In the beginning God created the heaven and the earth."),
                "and Genesis 1 is still in the live panel",
            )
        }

    @Test
    fun `clicking a verse in the live panel puts it on screen`() = splitBibleTab { _, reports ->
        livePanelVerse("3. And God said, Let there be light.").performClick()
        waitForIdle()

        val live = reports.live?.single()
        assertEquals("Genesis", live?.bookName)
        assertEquals(1, live?.chapter)
        assertEquals(3, live?.verseNumber, "the verse clicked in the live panel went live")
        assertEquals(
            listOf(Presenting.BIBLE),
            reports.presenting,
            "and the host was told the Bible is presenting",
        )
    }

    @Test
    fun `pressing down in split mode advances the live panel to the next verse`() = splitBibleTab { _, reports ->
        press(Key.DirectionDown)

        val live = reports.live?.single()
        assertEquals(2, live?.verseNumber)
        assertEquals("And the earth was without form, and void.", live?.verseText)
        assertEquals(listOf(Presenting.BIBLE), reports.presenting)
    }

    @Test
    fun `pressing up after down returns to the previous verse`() = splitBibleTab { _, reports ->
        press(Key.DirectionDown)
        press(Key.DirectionUp)

        assertEquals(1, reports.live?.single()?.verseNumber)
    }

    @Test
    fun `pressing down past the chapter's last verse does not move further`() = splitBibleTab { _, reports ->
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        assertEquals(3, reports.live?.single()?.verseNumber, "Genesis 1 has three verses in the fixture")

        press(Key.DirectionDown)

        assertEquals(3, reports.live?.single()?.verseNumber, "there is no fourth verse to land on")
    }

    @Test
    fun `arrow keys are ignored while the search field has focus`() = splitBibleTab { _, reports ->
        bibleSearchBox().requestFocus()
        waitForIdle()

        press(Key.DirectionDown)

        assertNull(reports.live, "a focused search field must own arrow keys, not the live panel")
    }

    @Test
    fun `going live from a different chapter updates the live panel to it`() {
        val presenter = PresenterManager()
        splitBibleTab(presenter = presenter) { _, reports ->
            onNodeWithText("John").performClick()
            waitForIdle()
            onNodeWithText("3").performClick()
            waitForIdle()
            onNodeWithText("16. For God so loved the world.").performClick()
            waitForIdle()
            actionButton(BibleLabel.GO_LIVE).performClick()
            waitForIdle()

            runOnIdle { presenter.setDisplayedVerses(reports.live!!) }
            waitForIdle()

            assertEquals(
                2,
                countOnScreen("16. For God so loved the world."),
                "the newly live verse now appears in both the browser and the live panel",
            )
        }
    }
}
