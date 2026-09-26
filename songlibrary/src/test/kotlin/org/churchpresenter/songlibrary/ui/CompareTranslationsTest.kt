@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.songlibrary.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTranslation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompareTranslationsTest {

    /** Verse 1 is a line short in the second language; the chorus lines up. */
    private val grace = SongItem(
        number = "1",
        title = "Amazing Grace",
        songbook = "Hymnal",
        lyrics = listOf("[Verse 1]", "One", "Two", "", "[Chorus]", "Three"),
    ).withTranslations(
        listOf(
            SongTranslation(title = "Благодать", lyrics = listOf("[Куплет 1]", "Один", "", "[Припев]", "Три")),
            SongTranslation(
                label = "Kyrgyz",
                title = "Ырайым",
                lyrics = listOf("[Verse 1]", "Бир", "Эки", "", "[Chorus]", "Үч"),
            ),
        ),
    )

    private fun compare(
        song: SongItem = grace,
        onDismiss: () -> Unit = {},
        onSave: (SongItem) -> Unit = {},
        body: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent { Themed { CompareTranslationsContent(song, onDismiss, onSave) } }
        waitForIdle()
        body()
    }

    @Test
    fun `the section that does not line up is counted and the one that does is not`() = compare {
        assertTrue(isShowing("1 of 2 need attention"))
        assertTrue(isShowing("Line count differs"))
        assertTrue(isShowingText("1 lines · L1 has 2"), "the short language says what it has against the reference")
        assertTrue(isShowing("Lines up · 1 lines"))
    }

    @Test
    fun `only problems hides the sections that line up`() = compare {
        click("Only problems")

        assertFalse(isShowing("Lines up · 1 lines"))
        assertTrue(isShowingText("1 lines · L1 has 2"))
    }

    @Test
    fun `a language can be hidden, but never below two`() = compare {
        assertEquals(2, countShowing("Kyrgyz"), "its chip and its column head")

        clickFirst("Kyrgyz")
        assertEquals(1, countShowing("Kyrgyz"), "only the chip is left")

        clickFirst("Language 2")
        assertEquals(2, countShowing("Language 2"), "two languages are the fewest there is to compare")
    }

    @Test
    fun `fixing the short section and saving puts it back into that language only`() {
        var saved: SongItem? = null
        compare(onSave = { saved = it }) {
            assertTrue(isShowing("Done"))

            onAllNodes(hasSetTextAction())[1].performTextReplacement("Один\nДва")
            waitForIdle()

            assertTrue(isShowing("All sections line up"))
            click("Save Changes")
        }

        val song = checkNotNull(saved)
        assertEquals(
            listOf("[Куплет 1]", "Один", "Два", "", "[Припев]", "Три"),
            song.extraTranslations()[0].lyrics,
        )
        assertEquals(grace.lyrics, song.lyrics)
        assertEquals(grace.extraTranslations()[1], song.extraTranslations()[1])
    }

    @Test
    fun `cancel closes without saving, even after an edit`() {
        var dismissed = false
        var saved: SongItem? = null
        compare(onDismiss = { dismissed = true }, onSave = { saved = it }) {
            onAllNodes(hasSetTextAction())[1].performTextReplacement("Один\nДва")
            click("Cancel")
        }

        assertTrue(dismissed)
        assertNull(saved)
    }

    // ── The grid's side of it ─────────────────────────────────────────────────

    @Test
    fun `a one-language song's compare button is there but disabled`() = withLibrary { _ ->
        narrowToTitleOnly()

        val buttons = onAllNodesWithContentDescription("Only one language — nothing to compare")
        assertEquals(STOCK.size, buttons.fetchSemanticsNodes().size)
        buttons[0].assertIsNotEnabled()
    }

    @Test
    fun `a song whose languages disagree is flagged beside its tick`() = withLibrary(
        songs = listOf(grace.copy(sourceFile = "")),
    ) { _ ->
        onNodeWithContentDescription("1 sections don’t line up across languages").assertExists()
    }
}
