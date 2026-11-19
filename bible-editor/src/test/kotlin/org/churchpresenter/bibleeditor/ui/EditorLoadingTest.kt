@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.bibleeditor.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.churchpresenter.bibleedits.EditEntry
import org.churchpresenter.bibleedits.VerseChange
import org.churchpresenter.bibleeditor.EditorFixture
import org.churchpresenter.bibleeditor.EditorFixture.Companion.MATTHEW
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorLoadingTest {

    @Test
    fun `while the Bible is still being read the window waits on it`() {
        val gate = GateDispatcher(allowed = 1)
        withEditor(io = gate, awaitOpen = false) { _, _ ->
            awaitText("Russian Synodal")
            assertFalse(isShowing("Matthew 1"))
            assertTrue(isShowing("Loading Bibles…"), "the chapter waits for its Bible")
            assertFalse(isShowing("Complete, matches KJV"))
            click("Russian Synodal")
            click("Russian Synodal")
            click("Validate")
            assertTrue(isShowing("Checking…"))
            press("Close")
            runOnIdle { gate.release() }
            awaitText("Matthew 1")
        }
    }

    @Test
    fun `while the missing verses are being looked for the dialog says it is checking`() {
        val gate = GateDispatcher(allowed = 2)
        withEditor(io = gate, openMatthew17 = false) { _, _ ->
            click("Validate")
            assertTrue(isShowing("Checking…"))
            runOnIdle { gate.release() }
            awaitText("Russian Synodal compared with KJV · 2 books checked")
        }
    }

    @Test
    fun `with nothing to compare with, every tab is empty`() = withEditor(withReference = false) { _, _ ->
        click("Validate")
        click("All (0)")
        assertTrue(isShowing("No missing verses"))
    }

    @Test
    fun `a change to a Bible no longer on the shelf is listed by its file name`() =
        withEditor(listOf(EditEntry(1, EditorFixture.NOW, "gone.spb", MATTHEW, 17, 2,
            after = VerseChange(text = "x")))) { _, _ ->
            click("All Bibles")
            assertTrue(isShowing("40 17:2 · gone.spb"))
        }

    @Test
    fun `Enter alone starts a new line rather than saving`() = withEditor { _, _ ->
        press("Edit verse 3")
        draftField().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertTrue(isShowing("Edit verse 3"))
    }

    @Test
    fun `leaving the chapter box untouched stays on the chapter`() = withEditor { _, _ ->
        openChapterPicker()
        chapterField().performClick()
        chapterField().performKeyInput { pressKey(Key.Tab) }
        waitForIdle()
        assertTrue(isShowing("Matthew 17"))
    }
}
