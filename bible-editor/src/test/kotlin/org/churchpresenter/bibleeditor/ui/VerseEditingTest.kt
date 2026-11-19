@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.bibleeditor.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import org.churchpresenter.bibleeditor.EditorFixture
import org.churchpresenter.bibleeditor.EditorFixture.Companion.KJV_17
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerseEditingTest {

    @Test
    fun `a typo is fixed in the card, which shows the change before it is saved`() = withEditor { _, _ ->
        press("Edit verse 2")
        assertTrue(isShowing("Edit verse 2"))
        assertTrue(isShowing("The original stays in the Bible file; History keeps every version."))
        onNodeWithText("Save edit").assertIsNotEnabled()
        draftField().performTextReplacement(KJV_17[1])
        assertTrue(isShowing("YOUR CHANGE"))
        click("Save edit")
        assertFalse(isShowing("YOUR CHANGE"))
        assertTrue(isShowing("EDITED"))
        assertTrue(isShowing(KJV_17[1]))
    }

    @Test
    fun `double-clicking a verse opens it, and Escape puts it down`() = withEditor { _, _ ->
        onAllNodesWithText(KJV_17[2])[0].performMouseInput { doubleClick() }
        waitForIdle()
        assertTrue(isShowing("Edit verse 3"))
        draftField().performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertFalse(isShowing("Edit verse 3"))
    }

    @Test
    fun `Ctrl Enter saves, Cancel does not`() = withEditor { _, _ ->
        press("Edit verse 4")
        draftField().performTextReplacement("Then answered Peter.")
        draftField().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Enter) } }
        waitForIdle()
        assertTrue(isShowing("Then answered Peter."))
        press("Edit verse 5")
        draftField().performTextReplacement("Not this.")
        click("Cancel")
        assertFalse(isShowing("Not this."))
    }

    @Test
    fun `a missing verse is typed into its gap`() = withEditor { _, _ ->
        click("Type verse 11")
        assertTrue(isShowing("Type verse 11 (missing in this Bible)"))
        assertTrue(isShowing("It goes in as verse 11, right where it is missing."))
        draftField().performTextReplacement(KJV_17[10])
        assertFalse(isShowing("YOUR CHANGE"))
        click("Add verse 11")
        assertTrue(isShowing("ADDED"))
        assertTrue(isShowing("Complete, matches KJV"))
        press("Take out the verse you added")
        assertTrue(isShowing("1 verse missing"))
    }

    @Test
    fun `a removed verse is struck out until it is restored`() = withEditor { _, _ ->
        press("Remove verse")
        assertTrue(isShowing("REMOVED"))
        assertTrue(isShowing("RS · 10 of 12 verses"))
        click("Restore")
        assertTrue(isShowing("RS · 11 of 12 verses"))
        assertTrue(isShowing("RESTORED"), "the history lists the restore")
    }

    @Test
    fun `a changed verse's tag and button open its history`() = withEditor(EditorFixture.savedFixes()) { _, _ ->
        press("History of verse 9")
        assertTrue(isShowing("Only Matthew 17:9"))
        press("Show all")
        assertFalse(isShowing("Only Matthew 17:9"))
        press("Close history")
        onAllNodesWithText("EDITED")[0].performMouseInput { click() }
        waitForIdle()
        assertTrue(isShowing("Only Matthew 17:2"))
    }
}
