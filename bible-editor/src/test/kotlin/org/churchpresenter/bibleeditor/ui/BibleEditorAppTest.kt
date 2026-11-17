@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.bibleeditor.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlinx.coroutines.Dispatchers
import org.churchpresenter.bibleeditor.EditorFixture
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BibleEditorAppTest {

    @Test
    fun `it opens on the preferred Bible's first chapter`() = withEditor(openMatthew17 = false) { _, _ ->
        assertTrue(isShowing("Russian Synodal"))
        assertTrue(isShowing("RS · 2 of 2 verses"))
        assertTrue(isShowing("Complete, matches KJV"))
        assertTrue(isShowing("of 17"))
    }

    @Test
    fun `a folder with no Bibles says so`() {
        val empty = Files.createTempDirectory("cp-bible-editor-empty").toFile()
        try {
            runDesktopComposeUiTest(width = WINDOW_WIDTH, height = WINDOW_HEIGHT) {
                setContent {
                    ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                        BibleEditorApp(empty, File(empty, "data"), onClose = {}, io = Dispatchers.Unconfined,
                            tickClock = false)
                    }
                }
                awaitText("No Bible files found in ${empty.path}")
            }
        } finally {
            empty.deleteRecursively()
        }
    }

    @Test
    fun `the chapter steps with the arrows, the buttons and a typed number`() =
        withEditor(openMatthew17 = false) { _, _ ->
        press("Next chapter")
        assertTrue(isShowing("Matthew 2"))
        press("Previous chapter")
        assertTrue(isShowing("Matthew 1"))
        chapterField().performClick()
        chapterField().performKeyInput { pressKey(Key.DirectionUp) }
        waitForIdle()
        assertTrue(isShowing("Matthew 2"))
        chapterField().performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        assertTrue(isShowing("Matthew 1"))
        goToChapter(17)
        assertTrue(isShowing("Matthew 17"))
        goToChapter(40)
        assertTrue(isShowing("Matthew 17"), "a chapter the book does not have is not gone to")
        chapterField().performTextReplacement("3")
        chapterField().performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertTrue(isShowing("Matthew 17"), "Escape leaves the chapter as it was")
        chapterField().performTextReplacement("2")
        chapterField().performKeyInput { pressKey(Key.Tab) }
        waitForIdle()
        assertTrue(isShowing("Matthew 2"), "leaving the field goes to what was typed")
    }

    @Test
    fun `the book picker flags books with missing verses and opens the one picked`() = withEditor { _, _ ->
        click("Matthew")
        assertTrue(isShowingDescription("Has missing verses"))
        click("Mark")
        assertTrue(isShowing("Mark 1"))
    }

    @Test
    fun `the Bible picker counts each Bible's changes and opens the one picked`() =
        withEditor(EditorFixture.savedFixes()) { _, _ ->
            click("Russian Synodal")
            assertTrue(isShowing("2 changes"))
            click("King James Version")
            assertTrue(isShowing("KJV · 12 of 12 verses"))
            assertTrue(isShowing("Complete, matches KJV"))
            click("King James Version")
            click("Russian Synodal")
            assertTrue(isShowing("1 verse missing"))
        }

    @Test
    fun `the care note hides until next time`() = withEditor { _, _ ->
        assertTrue(isShowing("Handle with care. This is Holy Scripture.", substring = true))
        press("Hide until next time")
        assertFalse(isShowing("Handle with care. This is Holy Scripture.", substring = true))
    }

    @Test
    fun `the footer counts what is unsaved, saves it and discards it`() = withEditor { fixture, calls ->
        assertTrue(isShowing("No changes. The original Bible file is never overwritten."))
        onNodeWithText("Saved").assertIsNotEnabled()
        press("Remove verse")
        assertTrue(isShowing("1 change not saved yet"))
        assertTrue(isShowing("1 change in this chapter"))
        click("Save 1 change")
        assertEquals(1, calls.saved)
        assertEquals(1, fixture.store.load().size)
        assertTrue(isShowing("All changes saved · last Just now"))
        press("Remove verse")
        press("Remove verse")
        assertTrue(isShowing("2 changes not saved yet"))
        click("Discard unsaved")
        assertTrue(isShowing("All changes saved · last Just now"))
        click("Close")
        assertEquals(1, calls.closed)
    }

    @Test
    fun `History opens and closes from the header and its own button`() = withEditor { _, _ ->
        assertTrue(isShowing("This chapter"))
        press("Close history")
        assertFalse(isShowing("This chapter"))
        onNodeWithText("History").performClick()
        waitForIdle()
        assertTrue(isShowing("This chapter"))
        onAllNodesWithText("History")[0].performClick()
        waitForIdle()
        assertFalse(isShowing("This chapter"))
    }
}
