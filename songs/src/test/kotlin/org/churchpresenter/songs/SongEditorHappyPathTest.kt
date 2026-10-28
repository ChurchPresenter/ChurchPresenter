@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val EDITOR_TAG = "happy-path-editor"

/** Ordinals among the editor's typed fields; `EditSongContentTest` pins the order. */
private const val TITLE_FIELD = 0
private const val LYRICS_FIELD = 8

/**
 * Writing and changing songs from the Songs tab, with the real editor in the tab's editor slot --
 * the app opens it in a `DialogWindow`, which cannot be composed headless, so its content stands in.
 */
class SongEditorHappyPathTest {

    /** The editor the app opens, composed in place of its window while the tab asks for it. */
    private val realEditor: @Composable (SongEditorRequest) -> Unit = { request ->
        if (request.isVisible) {
            Box(Modifier.testTag(EDITOR_TAG)) {
                EditSongContent(
                    song = request.song ?: error("the tab always hands the editor a song"),
                    backgroundButton = testBackgroundButton,
                    songbooks = request.songbooks,
                    existingSongs = request.existingSongs,
                    isNewSong = request.isNewSong,
                    theme = ThemeMode.LIGHT,
                    chordsVisible = request.chordsVisible,
                    onDismiss = request.onDismiss,
                    onSave = request.onSave,
                )
            }
        }
    }

    private val inEditor = hasAnyAncestor(hasTestTag(EDITOR_TAG))

    private fun ComposeUiTest.type(field: Int, text: String) {
        onAllNodes(hasSetTextAction() and inEditor)[field].performTextReplacement(text)
        waitForIdle()
    }

    private fun ComposeUiTest.click(description: String) {
        onAllNodes(hasContentDescription(description))[0].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.save() {
        onAllNodes(hasText("Save") and inEditor)[0].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.editorIsOpen(): Boolean =
        onAllNodes(hasTestTag(EDITOR_TAG)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    private fun ComposeUiTest.goLiveWith(title: String) {
        onAllNodes(hasText(title))[0].performClick()
        waitForIdle()
        click("Go Live")
    }

    /** The editor's songbook: a read-only dropdown anchor, then the book picked from its menu. */
    private fun ComposeUiTest.pickSongbook(name: String) {
        val anchor = SemanticsMatcher("the songbook dropdown") {
            it.config.getOrNull(SemanticsProperties.EditableText) != null &&
                it.config.getOrNull(SemanticsActions.SetText) == null
        }
        onAllNodes(anchor and inEditor)[0].performClick()
        waitForIdle()
        val items = onAllNodes(hasText(name))
        items[items.fetchSemanticsNodes().lastIndex].performClick()
        waitForIdle()
    }

    @Test
    fun `a new song is written, saved and goes live`() = songsTab(songEditor = realEditor) { vm, reports ->
        click("New Song")
        type(LYRICS_FIELD, "[Verse 1]\nmorning has broken like the first morning")
        type(TITLE_FIELD, "Morning Has Broken")
        pickSongbook("Hymnal")
        save()

        assertTrue(!editorIsOpen(), "Save closes the editor")
        assertTrue(vm.filteredSongItems.value.any { it.title == "Morning Has Broken" }, "the song is in the library")

        goLiveWith("Morning Has Broken")
        assertEquals(listOf(Presenting.LYRICS), reports.presenting)
        assertTrue("morning has broken like the first morning" in reports.selectedSection?.lines.orEmpty())
    }

    @Test
    fun `an edited song goes live with its new words`() = songsTab(songEditor = realEditor) { vm, reports ->
        onAllNodes(hasText("Amazing Grace"))[0].performClick()
        waitForIdle()
        click("Edit Song")
        type(LYRICS_FIELD, "[Verse 1]\nthrough many dangers toils and snares")
        save()

        val saved = vm.filteredSongItems.value.single { it.title == "Amazing Grace" }
        assertTrue("through many dangers toils and snares" in saved.lyrics, saved.lyrics.toString())

        goLiveWith("Amazing Grace")
        assertTrue("through many dangers toils and snares" in reports.selectedSection?.lines.orEmpty())
    }

    @Test
    fun `a transposed song keeps its new key`() = songsTab(
        songs = listOf(
            SongFixture(number = "1", title = "Amazing Grace", lyrics = listOf("[Verse 1]", "[G]Amazing [C]grace")),
        ),
        songEditor = realEditor,
    ) { vm, _ ->
        onAllNodes(hasText("Amazing Grace"))[0].performClick()
        waitForIdle()
        click("Edit Song")
        click("Transpose up")
        click("Transpose up")
        save()

        assertEquals(
            listOf("[Verse 1]", "[A]Amazing [D]grace"),
            vm.filteredSongItems.value.single().lyrics,
            "Save keeps the new key",
        )
    }
}
