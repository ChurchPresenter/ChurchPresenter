@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.bibleeditor.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.geometry.Offset
import org.churchpresenter.bibleeditor.EditorFixture.Companion.KJV_17
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MissingVersesDialogTest {

    private fun androidx.compose.ui.test.ComposeUiTest.openValidator() {
        click("Validate")
        awaitText("Missing verses")
    }

    @Test
    fun `it lists what is missing in the numbering the Bible matches and types the next one`() = withEditor { _, _ ->
        assertTrue(isShowing("1"), "the Validate button counts what is still missing")
        openValidator()
        assertTrue(isShowing("Russian Synodal · Synodal numbering · 3 books checked"))
        assertTrue(isShowing("Still missing"))
        assertTrue(isShowing("Missing (1)"))
        assertTrue(isShowing("Matthew 17:11"))
        click("Copy list")
        click("Type the next one")
        assertFalse(isShowing("Missing verses"))
        assertTrue(isShowing("Type verse 11 (missing in this Bible)"))
    }

    @Test
    fun `a verse typed back in moves to Restored, and View goes to it`() = withEditor { _, _ ->
        openValidator()
        click("Type it")
        draftField().performTextReplacement(KJV_17[10])
        click("Add verse 11")
        openValidator()
        assertTrue(isShowing("Every missing verse is restored"))
        assertTrue(isShowing("Save your changes to keep them."))
        click("Restored (1)")
        click("View")
        assertFalse(isShowing("Missing verses"))
        assertTrue(isShowing("ADDED"))
    }

    @Test
    fun `the tabs show what each holds`() = withEditor { _, _ ->
        openValidator()
        click("Restored (0)")
        assertTrue(isShowing("Nothing restored yet"))
        click("All (1)")
        assertTrue(isShowing("Matthew 17:11"))
        press("Close")
        assertFalse(isShowing("Missing verses"))
    }

    @Test
    fun `clicking outside the dialog closes it`() = withEditor { _, _ ->
        openValidator()
        onRoot().performMouseInput { click(Offset(5f, 5f)) }
        waitForIdle()
        assertFalse(isShowing("Missing verses"))
    }

    @Test
    fun `the KJV itself has nothing missing`() = withEditor { _, _ ->
        click("Russian Synodal")
        click("King James Version")
        openValidator()
        assertTrue(isShowing("King James Version · KJV numbering · 2 books checked"))
        assertTrue(isShowing("No missing verses"))
        assertTrue(isShowing("This Bible matches the reference verse for verse."))
    }
}
