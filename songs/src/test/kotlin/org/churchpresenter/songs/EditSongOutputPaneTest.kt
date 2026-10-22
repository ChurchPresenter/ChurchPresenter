@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTextOverrides
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The song editor's Output tab: the slide under the cursor on both kinds of output, a verse or a
 * line at a time, the song's own look, and the Voice 2 button — read back from what Save hands over.
 */
class EditSongOutputPaneTest {

    private object Label {
        const val SAVE = "Save"
        const val VERSE = "1 Verse"
        const val LINE = "1 Line"
        const val LARGER = "Larger"
        const val SMALLER = "Smaller"
        const val VOICE_2 = "Voice 2"
        const val LOOK = "This song's look"
    }

    /** The lyrics box among the editor's typed fields; `EditSongContentTest` pins the order. */
    private val LYRICS_FIELD = 7

    private val lyrics = listOf("[Verse 1]", "Amazing grace", "2>That saved", "I once was lost")

    private fun song(look: SongTextOverrides = SongTextOverrides()) = SongItem(
        number = "1",
        title = "Grace",
        songbook = "Book",
        lyrics = lyrics,
        sourceFile = "/songs/book/1.song",
        textOverrides = look,
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

    private fun editor(
        song: SongItem = song(),
        appSettings: AppSettings? = outputs,
        block: ComposeUiTest.(() -> SongItem?) -> Unit,
    ) {
        var saved: SongItem? = null
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    EditSongContent(
                        backgroundButton = testBackgroundButton,
                        song = song,
                        songbooks = listOf("Book"),
                        existingSongs = emptyList(),
                        isNewSong = false,
                        theme = ThemeMode.LIGHT,
                        appSettings = appSettings,
                        onDismiss = {},
                        onSave = { s, _ -> saved = s },
                    )
                }
            }
            block { saved }
        }
    }

    private fun ComposeUiTest.tap(text: String) {
        onNodeWithText(text).performClick()
        waitForIdle()
    }

    /** The look card sits below the previews, so each control is scrolled to before it is pressed. */
    private fun ComposeUiTest.press(description: String) {
        onNodeWithContentDescription(description).performScrollTo().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.reset(key: String) {
        onNodeWithTag(SONG_LOOK_RESET_TAG + key).performScrollTo().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.tab(tag: String) {
        onNodeWithTag(tag).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.caretAt(offset: Int) {
        onAllNodes(hasSetTextAction())[LYRICS_FIELD].performTextInputSelection(TextRange(offset))
        waitForIdle()
    }

    private fun ComposeUiTest.onFullPreview(text: String) =
        onAllNodes(hasText(text, substring = true) and hasAnyAncestor(hasTestTag(OUTPUT_PREVIEW_FULL_TAG)))

    private val body = lyrics.joinToString("\n")

    // ── The tabs ────────────────────────────────────────────────────────────────

    @Test
    fun `the editor opens on the output and Chords swaps it for the chord pane`() = editor {
        onNodeWithTag(OUTPUT_PREVIEW_FULL_TAG).assertExists()
        onNodeWithTag(OUTPUT_PREVIEW_BAND_TAG).assertExists()
        onNodeWithText(Label.LOOK).assertExists()

        tab(RIGHT_PANE_CHORDS_TAG)
        onNodeWithTag(OUTPUT_PREVIEW_FULL_TAG).assertDoesNotExist()

        tab(RIGHT_PANE_OUTPUT_TAG)
        onNodeWithTag(OUTPUT_PREVIEW_FULL_TAG).assertExists()
    }

    @Test
    fun `without the app's settings there is only the chord pane`() = editor(appSettings = null) {
        onNodeWithTag(RIGHT_PANE_OUTPUT_TAG).assertDoesNotExist()
        onNodeWithTag(OUTPUT_PREVIEW_FULL_TAG).assertDoesNotExist()
    }

    @Test
    fun `with no lyrics the output says what it will show`() = editor(song = song().copy(lyrics = emptyList())) {
        onNodeWithTag(OUTPUT_PREVIEW_FULL_TAG).assertDoesNotExist()
        onNodeWithText("Type some lyrics", substring = true).assertExists()
        onNodeWithText(Label.LOOK).assertExists("the look can be set before there are words")
    }

    // ── A verse or a line at a time ─────────────────────────────────────────────

    @Test
    fun `1 Verse shows the whole slide and 1 Line the line under the cursor with its answer`() = editor {
        caretAt(body.indexOf("grace"))
        assertTrue(onFullPreview("I once was lost").fetchSemanticsNodes().isNotEmpty(), "the verse is all there")

        tap(Label.LINE)
        assertTrue(onFullPreview("Amazing grace").fetchSemanticsNodes().isNotEmpty())
        assertTrue(onFullPreview("That saved").fetchSemanticsNodes().isNotEmpty(), "the answer shows with its line")
        assertTrue(onFullPreview("I once was lost").fetchSemanticsNodes().isEmpty(), "the next line waits")

        caretAt(body.indexOf("once"))
        assertTrue(
            onFullPreview("I once was lost").fetchSemanticsNodes().isNotEmpty(),
            "the preview follows the cursor",
        )
        assertTrue(onFullPreview("Amazing grace").fetchSemanticsNodes().isEmpty())

        tap(Label.VERSE)
        assertTrue(onFullPreview("Amazing grace").fetchSemanticsNodes().isNotEmpty())
    }

    // ── The song's own look ─────────────────────────────────────────────────────

    @Test
    fun `the size steps in tens and its reset hands it back to the profile`() = editor { saved ->
        onNodeWithTag(SONG_LOOK_RESET_TAG + "size").assertDoesNotExist()

        press(Label.LARGER)
        press(Label.LARGER)
        onNodeWithText("120%").assertExists()
        press(Label.SMALLER)
        tap(Label.SAVE)
        assertEquals(110, saved()?.textOverrides?.sizePercent)

        reset("size")
        onNodeWithText("100%").assertExists()
        tap(Label.SAVE)
        assertEquals(SongTextOverrides(), saved()?.textOverrides)
    }

    @Test
    fun `the size stops at the top of its range`() = editor(song = song(SongTextOverrides(sizePercent = 200))) {
        onNodeWithContentDescription(Label.LARGER).assertIsNotEnabled()
        press(Label.SMALLER)
        onNodeWithText("190%").assertExists()
        onNodeWithContentDescription(Label.LARGER).assertIsEnabled()
    }

    @Test
    fun `the size stops at the bottom of its range`() = editor(song = song(SongTextOverrides(sizePercent = 60))) {
        press(Label.SMALLER)
        onNodeWithText("50%").assertExists()
        onNodeWithContentDescription(Label.SMALLER).assertIsNotEnabled()
    }

    @Test
    fun `each of the song's own values has a reset, and each reset drops only its own`() = editor(
        song = song(SongTextOverrides("Georgia", sizePercent = 90, color = "#A8D8FF", secondVoiceColor = "#FF8A65")),
    ) { saved ->
        reset("font")
        tap(Label.SAVE)
        assertEquals(
            SongTextOverrides(sizePercent = 90, color = "#A8D8FF", secondVoiceColor = "#FF8A65"),
            saved()?.textOverrides,
        )

        reset("color")
        reset("voice2")
        tap(Label.SAVE)
        assertEquals(SongTextOverrides(sizePercent = 90), saved()?.textOverrides)
        onNodeWithTag(SONG_LOOK_RESET_TAG + "voice2").assertDoesNotExist()
    }

    // ── The Voice 2 button ──────────────────────────────────────────────────────

    @Test
    fun `Voice 2 marks the cursor's line and pressing it again unmarks it`() = editor { saved ->
        caretAt(body.indexOf("once"))

        tap(Label.VOICE_2)
        tap(Label.SAVE)
        assertEquals(listOf("[Verse 1]", "Amazing grace", "2>That saved", "2>I once was lost"), saved()?.lyrics)

        tap(Label.VOICE_2)
        tap(Label.SAVE)
        assertEquals(lyrics, saved()?.lyrics)
    }

    @Test
    fun `the voice markup is in the legend`() = editor {
        onNodeWithText("2>line").assertExists()
        onNodeWithText("2>(words)").assertExists()
    }
}
