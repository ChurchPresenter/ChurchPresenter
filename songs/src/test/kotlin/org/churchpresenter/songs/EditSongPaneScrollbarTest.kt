@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertTrue

class EditSongPaneScrollbarTest {

    private val firstLine = "Line 1 of verse 1"

    private val longSong = SongItem(
        number = "1",
        title = "Long",
        songbook = "Book",
        lyrics = (1..12).flatMap { verse ->
            listOf("[Verse $verse]") + (1..4).map { line -> "Line $line of verse $verse" } + ""
        },
        sourceFile = "/songs/book/1.song",
    )

    private val outputs = AppSettings(
        projectionSettings = ProjectionSettings(
            outputProfiles = listOf(
                OutputProfile(id = "main", name = "Main"),
                OutputProfile(
                    id = "band",
                    name = "Stream",
                    displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
                ),
            ),
            screenAssignments = listOf(ScreenAssignment(activeProfileId = "main")),
        ),
    )

    private fun editor(block: ComposeUiTest.() -> Unit) = runComposeUiTest {
        setContent {
            MaterialTheme {
                EditSongContent(
                    backgroundButton = testBackgroundButton,
                    song = longSong,
                    songbooks = listOf("Book"),
                    existingSongs = emptyList(),
                    isNewSong = false,
                    theme = ThemeMode.LIGHT,
                    appSettings = outputs,
                    onDismiss = {},
                    onSave = { _, _ -> },
                )
            }
        }
        waitForIdle()
        block()
    }

    private fun ComposeUiTest.openTab(tag: String) {
        onNodeWithTag(tag).performClick()
        waitUntil { onAllNodesWithText(firstLine).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Without a bar on the pane's edge the press lands on nothing, so [content] stays where it is. */
    private fun ComposeUiTest.assertDraggingTheBarScrolls(content: SemanticsNodeInteraction) {
        val before = content.fetchSemanticsNode().boundsInRoot.top
        onRoot().performMouseInput {
            moveTo(Offset(width - BAR_INSET, before + THUMB_INSET))
            press()
            moveBy(Offset(0f, DRAG))
            release()
        }
        waitForIdle()
        val after = content.fetchSemanticsNode().boundsInRoot.top
        assertTrue(after < before, "the pane should have scrolled up from $before, is at $after")
    }

    @Test
    fun `the Output tab scrolls by its bar`() = editor {
        assertDraggingTheBarScrolls(onNodeWithTag(OUTPUT_PREVIEW_FULL_TAG))
    }

    @Test
    fun `the Chords tab scrolls by its bar`() = editor {
        openTab(RIGHT_PANE_CHORDS_TAG)
        assertDraggingTheBarScrolls(onNodeWithText(firstLine))
    }

    @Test
    fun `the In order tab scrolls by its bar`() = editor {
        openTab(RIGHT_PANE_IN_ORDER_TAG)
        assertDraggingTheBarScrolls(onNodeWithText(firstLine))
    }

    private companion object {
        const val BAR_INSET = 3f
        const val THUMB_INSET = 2f
        const val DRAG = 60f
    }
}
