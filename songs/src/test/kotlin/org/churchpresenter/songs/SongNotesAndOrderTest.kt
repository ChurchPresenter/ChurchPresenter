package org.churchpresenter.songs

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.settings.SongColumnId
import org.churchpresenter.settings.SongSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class SongNotesAndOrderTest {

    private val arranged = SongFixture(
        number = "12",
        title = "Amazing Grace",
        lyrics = listOf(
            "[Verse 1]", "Amazing grace how sweet the sound",
            "[---]", "That saved a wretch like me",
            "{Chorus}", "Praise the Lord",
            "[Verse 2]", "Twas grace that taught my heart",
        ),
        sectionOrder = listOf("[Verse 1]", "{Chorus}", "[Verse 2]", "{Chorus}", "[Verse 1]", "{Chorus}", "[Verse 2]"),
        notes = "Capo 2 for the band. Piano intro, 4 bars.",
        sectionNotes = mapOf("[Verse 1]" to "Piano only", "[Verse 2]" to "Key change"),
    )

    private val plain = SongFixture(number = "45", title = "Be Thou My Vision")

    // The test window leaves the list little room beside the lyrics panel: with the Number column
    // hidden and the narrowest Title, the Order column is inside it and can be hovered.
    private val narrowTitle = SongSettings(colWidthTitle = 60)

    private val withOrderColumn = listOf(
        SongColumnId.NUMBER, SongColumnId.TITLE, SongColumnId.ORDER, SongColumnId.ADD_TO_SCHEDULE,
        SongColumnId.FAVORITES,
    )

    @Test
    fun `the open song's notes show above its lyrics and open on a click`() =
        songsTab(songs = listOf(arranged)) { _, _ ->
            onNodeWithText("Amazing Grace").performClick()
            waitForIdle()

            onNodeWithTag(SONG_NOTES_TAG).performClick()
            waitForIdle()

            onNodeWithText("Capo 2 for the band. Piano intro, 4 bars.").assertExists()
            onNodeWithText("SONG").assertExists()
        }

    @Test
    fun `a song without notes shows no notes card`() = songsTab(songs = listOf(plain)) { _, _ ->
        onNodeWithText("Be Thou My Vision").performClick()
        waitForIdle()

        onAllNodesWithTag(SONG_NOTES_TAG).assertCountEquals(0)
    }

    @Test
    fun `the notes card's pencil opens the song in the editor`() {
        var editing: SongItem? = null
        songsTab(
            songs = listOf(arranged),
            songEditor = { request -> if (request.isVisible && !request.isNewSong) editing = request.song },
        ) { _, _ ->
            onNodeWithText("Amazing Grace").performClick()
            waitForIdle()
            assertNull(editing)

            onNodeWithContentDescription("Edit in song").performClick()
            waitForIdle()

            assertEquals("Amazing Grace", editing?.title)
        }
    }

    @Test
    fun `a section's cue shows beside its label, once per section`() =
        songsTab(songs = listOf(arranged), songSettings = verseMode()) { _, _ ->
            onNodeWithText("Amazing Grace").performClick()
            waitForIdle()

            // Verse 1 is split across two slides and sung twice; its cue is on the first slide only.
            onAllNodesWithTag(SECTION_CUE_TAG + "[Verse 1]").assertCountEquals(2)
            onAllNodesWithText("Key change").assertCountEquals(2)
            onAllNodesWithTag(SECTION_CUE_TAG + "{Chorus}").assertCountEquals(0)
        }

    @Test
    fun `the order column shows the first sections and how many more there are`() =
        songsTab(
            songs = listOf(arranged, plain),
            songSettings = narrowTitle,
            hiddenCols = setOf(SongColumnId.NUMBER),
            colOrder = withOrderColumn,
        ) { _, _ ->
            onNodeWithText("Order").assertExists()
            onAllNodesWithText("V1").assertCountEquals(2)
            onAllNodesWithText("C").assertCountEquals(2)
            onNodeWithText("+2").assertExists()
            onNodeWithText("As written").assertExists()
        }

    @Test
    fun `hovering the order shows all of it`() =
        songsTab(
            songs = listOf(arranged),
            songSettings = narrowTitle,
            hiddenCols = setOf(SongColumnId.NUMBER),
            colOrder = withOrderColumn,
        ) { _, _ ->
            onAllNodesWithText("V1")[0].performMouseInput { moveTo(center) }
            mainClock.advanceTimeBy(TOOLTIP_WAIT_MS)
            waitForIdle()

            onNodeWithText("V1 C V2 C V1 C V2").assertExists()
        }

    @Test
    fun `the order column starts hidden`() = songsTab(songs = listOf(arranged), hiddenCols = emptySet()) { _, _ ->
        onAllNodesWithText("+2").assertCountEquals(0)
    }

    private companion object {
        const val TOOLTIP_WAIT_MS = 1_000L
    }
}
