@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.app.churchpresenter.composables.SavedTextBackdrops
import org.churchpresenter.core.models.text.TextBackdrop
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The text-backing buttons on the Dictionary settings tab — one on the word, one on the reference.
 * Neither had ever been clicked, so neither `onBackdropChange` had run.
 *
 * The word's button composes first, which is the order [word] and [reference] index by.
 */
class DictionarySettingsTabBackdropTest {

    private val savedFile = File(System.getProperty("user.home"), ".churchpresenter/saved_backdrops.json")

    private val chip = "Text backing"
    private val caret = "Text backing options"

    private val word = 0
    private val reference = 1

    @BeforeTest
    fun freshPresets() {
        savedFile.delete()
        SavedTextBackdrops.looks.clear()
    }

    @AfterTest
    fun cleanupPresets() {
        savedFile.delete()
        SavedTextBackdrops.looks.clear()
    }

    private fun ComposeUiTest.clickChip(which: Int) {
        onAllNodesWithContentDescription(chip)[which].performScrollTo().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.clickCaret(which: Int) {
        onAllNodesWithContentDescription(caret)[which].performScrollTo().performClick()
        waitForIdle()
    }

    // ── The buttons are there ─────────────────────────────────────────────────

    @Test
    fun `the word and the reference each carry a backing button`() = dictionaryTab { _ ->
        assertEquals(
            2,
            onAllNodesWithContentDescription(chip).fetchSemanticsNodes().size,
            "one for the word, one for the reference",
        )
        assertEquals(2, onAllNodesWithContentDescription(caret).fetchSemanticsNodes().size)
    }

    @Test
    fun `the definition and the KJV usage carry none`() = dictionaryTab { _ ->
        // Both are plain text blocks with a size and a color; only the two headline elements have
        // a backing of their own.
        assertEquals(2, onAllNodesWithContentDescription(chip).fetchSemanticsNodes().size)
    }

    // ── The word's ────────────────────────────────────────────────────────────

    @Test
    fun `the word's chip turns a fill on`() = dictionaryTab { get ->
        clickChip(word)
        assertTrue(get().dictionarySettings.wordBackdrop.lineBackground, "the fallback look is a fill")
    }

    @Test
    fun `the word's chip leaves the reference's backing alone`() = dictionaryTab { get ->
        clickChip(word)
        assertEquals(TextBackdrop(), get().dictionarySettings.referenceBackdrop)
    }

    @Test
    fun `the word's chip turns a live backing back off`() = dictionaryTab { get ->
        clickChip(word)
        assertTrue(get().dictionarySettings.wordBackdrop.lineBackground)
        clickChip(word)
        assertTrue(get().dictionarySettings.wordBackdrop.isEmpty)
    }

    @Test
    fun `the word's caret opens the dialog`() = dictionaryTab { _ ->
        clickCaret(word)
        onNodeWithText("STYLE").assertExists()
        onNodeWithText("Both").assertExists()
    }

    @Test
    fun `a look chosen in the word's dialog is stored`() = dictionaryTab { get ->
        clickCaret(word)
        onNodeWithText("Border").performClick()
        waitForIdle()

        val stored = get().dictionarySettings.wordBackdrop
        assertTrue(stored.border)
        assertFalse(stored.lineBackground)
    }

    // ── The reference's ───────────────────────────────────────────────────────

    @Test
    fun `the reference's chip turns a fill on`() = dictionaryTab { get ->
        clickChip(reference)
        assertTrue(get().dictionarySettings.referenceBackdrop.lineBackground)
    }

    @Test
    fun `the reference's chip leaves the word's backing alone`() = dictionaryTab { get ->
        clickChip(reference)
        assertEquals(TextBackdrop(), get().dictionarySettings.wordBackdrop)
    }

    @Test
    fun `a look chosen in the reference's dialog is stored`() = dictionaryTab { get ->
        clickCaret(reference)
        onNodeWithText("Both").performClick()
        waitForIdle()

        val stored = get().dictionarySettings.referenceBackdrop
        assertTrue(stored.lineBackground && stored.border)
        assertEquals(TextBackdrop(), get().dictionarySettings.wordBackdrop, "the word's is untouched")
    }

    @Test
    fun `the two backings are set independently`() = dictionaryTab { get ->
        clickChip(word)
        clickCaret(reference)
        onNodeWithText("Border").performClick()
        waitForIdle()

        val settings = get().dictionarySettings
        assertTrue(settings.wordBackdrop.lineBackground, "the word kept its fill")
        assertFalse(settings.wordBackdrop.border)
        assertTrue(settings.referenceBackdrop.border, "and the reference took its own border")
        assertFalse(settings.referenceBackdrop.lineBackground)
    }

    @Test
    fun `a backing writes nothing but itself`() = dictionaryTab { get ->
        val before = get().dictionarySettings
        clickChip(word)
        assertEquals(
            before.copy(wordBackdrop = get().dictionarySettings.wordBackdrop),
            get().dictionarySettings,
            "no other dictionary setting may move with it",
        )
    }
}
