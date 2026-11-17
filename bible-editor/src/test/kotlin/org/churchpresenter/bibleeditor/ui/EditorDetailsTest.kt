@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.bibleeditor.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlinx.coroutines.Dispatchers
import org.churchpresenter.bibleeditor.EditorFixture
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.assertEquals
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import org.churchpresenter.bibleedits.EditEntry
import org.churchpresenter.bibleedits.VerseChange
import org.churchpresenter.bibleeditor.EditorFixture.Companion.HOUR
import org.churchpresenter.bibleeditor.EditorFixture.Companion.MATTHEW
import org.churchpresenter.bibleeditor.EditorFixture.Companion.NOW
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorDetailsTest {

    private fun entry(id: Long, ago: Long, verse: Int, before: VerseChange?, after: VerseChange?,
        revertOf: Long? = null) =
        EditEntry(id, NOW - ago, "rst.spb", MATTHEW, 17, verse, before, after, revertOf, original = "Original $verse.")

    /** One of every kind: edited, added, removed, restored and reverted, from minutes to days ago. */
    private val everyKind = listOf(
        entry(1, 72 * HOUR, 3, null, VerseChange(removed = true)),
        entry(2, 30 * HOUR, 3, VerseChange(removed = true), null),
        entry(3, 2 * HOUR, 11, null, VerseChange(text = "Howbeit.", added = true)),
        entry(4, HOUR / 2, 4, null, VerseChange(text = "Then answered Peter.")),
        entry(5, 5 * 60_000L, 4, VerseChange(text = "Then answered Peter."), null, revertOf = 4),
    )

    @Test
    fun `the history names every kind of change and when it was made`() = withEditor(everyKind) { _, _ ->
        listOf("EDITED", "ADDED", "REMOVED", "RESTORED", "REVERTED").forEach { assertTrue(isShowing(it), it) }
        assertTrue(isShowing("5 min ago"))
        assertTrue(isShowing("30 min ago"))
        assertTrue(isShowing("YESTERDAY"))
        assertTrue(isShowing("Undid the change from 30 min ago"))
        assertTrue(isShowing("Today, ", substring = true).not())
    }

    @Test
    fun `while every Bible is listed, another Bible's books are shown by number`() = withEditor(everyKind) { _, _ ->
        click("All Bibles")
        click("Russian Synodal")
        click("King James Version")
        assertTrue(isShowing("40 17:4 · RS"))
    }

    @Test
    fun `picking the Bible already open changes nothing`() = withEditor { _, _ ->
        press("Remove verse")
        click("Russian Synodal")
        click("Russian Synodal")
        assertTrue(isShowing("REMOVED"))
    }

    @Test
    fun `Command Enter saves too, and an unchanged draft does not`() = withEditor { _, _ ->
        press("Edit verse 3")
        draftField().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Enter) } }
        waitForIdle()
        assertTrue(isShowing("Edit verse 3"), "nothing changed, so nothing was saved")
        draftField().performTextReplacement("Moses and Elias.")
        draftField().performKeyInput { withKeyDown(Key.MetaLeft) { pressKey(Key.Enter) } }
        waitForIdle()
        assertFalse(isShowing("Edit verse 3"))
        assertTrue(isShowing("Moses and Elias."))
    }

    @Test
    fun `the keypad's Enter goes to the chapter typed`() = withEditor(openMatthew17 = false) { _, _ ->
        openChapterPicker()
        chapterField().performTextReplacement("17")
        chapterField().performKeyInput { pressKey(Key.NumPadEnter) }
        waitForIdle()
        assertTrue(isShowing("Matthew 17"))
        openChapterPicker()
        chapterField().performClick()
        chapterField().performKeyInput { pressKey(Key.A) }
        waitForIdle()
        assertTrue(isShowing("Matthew 17"))
    }

    @Test
    fun `opening one verse puts down the one being typed`() = withEditor { _, _ ->
        press("Edit verse 3")
        click("Type verse 11")
        assertFalse(isShowing("Edit verse 3"))
        assertTrue(isShowing("Type verse 11 (missing in this Bible)"))
        press("Edit verse 3")
        assertFalse(isShowing("Type verse 11 (missing in this Bible)"))
    }
}

class EditorDismissTest {

    @Test
    fun `a picker closes when the pointer goes down outside it`() = withEditor { _, _ ->
        click("Russian Synodal")
        assertTrue(isShowing("King James Version"))
        clickOutside()
        assertFalse(isShowing("King James Version"))
        click("Matthew")
        assertTrue(isShowing("Mark"))
        clickOutside()
        assertFalse(isShowing("Mark"))
        openChapterPicker()
        assertTrue(isShowingDescription("Chapter number"))
        clickOutside()
        assertFalse(isShowingDescription("Chapter number"))
    }

    @Test
    fun `a click inside the missing verses dialog keeps it open`() = withEditor { _, _ ->
        click("Validate")
        // The dialog merges its title into one node; its top-left corner is padding, not a control.
        onAllNodesWithText("Missing verses")[0].performMouseInput { click(Offset(8f, 8f)) }
        waitForIdle()
        assertTrue(isShowing("Missing verses"))
    }

    @Test
    fun `the window saves with no one listening`() {
        val fixture = EditorFixture()
        try {
            runDesktopComposeUiTest(width = WINDOW_WIDTH, height = 1800) {
                setContent {
                    ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                        BibleEditorApp(fixture.bibles, fixture.data, onClose = {}, io = Dispatchers.Unconfined,
                            tickClock = false)
                    }
                }
                awaitText("Matthew 1")
                press("Remove verse")
                click("Save 1 change")
                assertEquals(1, fixture.store.load().size)
            }
        } finally {
            fixture.delete()
        }
    }

    private fun ComposeUiTest.clickOutside() {
        onAllNodes(isRoot())[0].performMouseInput { click(Offset(400f, 700f)) }
        waitForIdle()
    }
}
