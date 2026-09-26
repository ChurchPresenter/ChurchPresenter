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

    /**
     * Words before the first header, a verse split across a different number of slides, and a verse
     * the second language has not got at all.
     */
    private val partial = SongItem(
        number = "2",
        title = "Partial",
        songbook = "Hymnal",
        lyrics = listOf("Opening", "[Verse 1]", "One", "Two", "[---]", "Three", "", "[Verse 2]", "Four"),
    ).withTranslations(
        listOf(
            SongTranslation(title = "Жарым", lyrics = listOf("Ачылыш", "", "[Verse 1]", "Бир", "Эки", "Үч")),
        ),
    )

    @Test
    fun `a section without a header, one split differently and one never translated are each told apart`() =
        compare(song = partial) {
            assertTrue(isShowing("Section 1"), "words before the first header are numbered")
            assertTrue(isShowingText("1 slides · L1 has 2"), "same lines, different slides")
            assertTrue(isShowing("Missing in L2"))
            assertTrue(isShowing("Missing"))
            assertTrue(isShowing("Add the Language 2 text for Verse 2…"))
        }

    @Test
    fun `a verse typed into a slot the language never had is saved under that verse's header`() {
        var saved: SongItem? = null
        compare(song = partial, onSave = { saved = it }) {
            clickFirst("Verse 2")
            onAllNodes(hasSetTextAction())[5].performTextReplacement("Төрт")
            waitForIdle()
            click("Save Changes")
        }

        assertEquals(
            listOf("Ачылыш", "", "[Verse 1]", "Бир", "Эки", "Үч", "", "[Verse 2]", "Төрт"),
            checkNotNull(saved).extraTranslations()[0].lyrics,
        )
    }

    @Test
    fun `only problems on a song that lines up says so instead of showing nothing`() = compare(
        song = grace.withTranslations(
            listOf(
                SongTranslation(
                    title = "Благодать",
                    lyrics = listOf("[Куплет 1]", "Один", "Два", "", "[Припев]", "Три"),
                ),
            ),
        ),
    ) {
        assertTrue(isShowing("All sections line up"))
        click("Only problems")

        assertTrue(isShowing("Every section lines up across the languages shown."))
        clickFirst("Verse 1")
        assertTrue(
            isShowing("Every section lines up across the languages shown."),
            "a hidden section is not scrolled to",
        )
    }

    @Test
    fun `a hidden language can be shown again`() = compare {
        clickFirst("Kyrgyz")
        clickFirst("Kyrgyz")

        assertEquals(2, countShowing("Kyrgyz"))
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

    @Test
    fun `a song whose languages line up but lack a title or lyrics is flagged with each problem`() = withLibrary(
        songs = listOf(
            SongItem(number = "3", title = "Grace", lyrics = listOf("[Verse 1]", "One")).withTranslations(
                listOf(
                    SongTranslation(lyrics = listOf("[Куплет 1]", "Один")),
                    SongTranslation(title = "Ырайым"),
                ),
            ).copy(sourceFile = ""),
        ),
    ) { _ ->
        onNodeWithContentDescription("Language 2 has no title\nLanguage 3 has no lyrics").assertExists()
        onNodeWithContentDescription("Compare translations").assertExists()
    }
}
