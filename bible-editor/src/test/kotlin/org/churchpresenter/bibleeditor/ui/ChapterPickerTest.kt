@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.bibleeditor.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.bibleeditor.EditorFixture.Companion.KJV_17
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChapterPickerTest {

    @Test
    fun `the picker lays the book's chapters out and flags the ones missing verses`() =
        withEditor(openMatthew17 = false) { _, _ ->
            assertTrue(isShowingDescription("Some chapters have missing verses"))
            openChapterPicker()
            assertTrue(isShowing("Matthew"))
            assertTrue(isShowing("· 17 chapters"))
            assertTrue(isShowing("Has missing verses"))
            chapterField().performKeyInput { pressKey(Key.Enter) }
            waitForIdle()
            assertTrue(isShowingDescription("Chapter number"), "Enter with nothing typed stays put")
            click("17")
            assertTrue(isShowing("Matthew 17"))
            assertFalse(isShowingDescription("Chapter number"))
        }

    @Test
    fun `Next missing goes to the next missing verse and lights it up`() = withEditor(openMatthew17 = false) { _, _ ->
        openChapterPicker()
        assertTrue(isShowing("Next: Matthew 17:11"))
        click("Next missing")
        assertTrue(isShowing("Verse 11 is missing in this Bible"))
        openChapterPicker()
        assertTrue(isShowing("Next: Matthew 17:11"), "from the chapter itself it goes round to the same verse")
    }

    @Test
    fun `once every missing verse is typed in there is no next one`() = withEditor { _, _ ->
        click("Type verse 11")
        draftField().performTextReplacement(KJV_17[10])
        click("Add verse 11")
        assertFalse(isShowingDescription("Some chapters have missing verses"))
        openChapterPicker()
        assertTrue(isShowing("No missing verses in this Bible"))
        assertFalse(isShowing("Has missing verses"))
        onNodeWithText("Next missing").assertIsNotEnabled()
    }

    @Test
    fun `a book with nothing missing has no legend`() = withEditor { _, _ ->
        click("Matthew")
        click("Mark")
        openChapterPicker()
        assertTrue(isShowing("Mark"))
        assertFalse(isShowing("Has missing verses"))
        assertTrue(isShowing("Next: Matthew 17:11"), "the next missing verse is looked for across the Bible")
    }
}
