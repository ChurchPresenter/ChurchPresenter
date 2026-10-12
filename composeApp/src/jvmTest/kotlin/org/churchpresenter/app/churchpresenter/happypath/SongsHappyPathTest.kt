@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.happypath

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.songs.verseMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Songs tab's own Add to Schedule, as `:songs` tags it -- the rows carry one each too. */
private const val ADD_SELECTED_TAG = "songs_addSelectedToSchedule"

class SongsHappyPathTest : HappyPathHarness() {

    private fun library(): AppSettings {
        song(
            1,
            "Amazing Grace",
            listOf(
                "Verse 1" to "amazing grace how sweet the sound",
                "Verse 2" to "twas grace that taught my heart to fear",
                "Verse 3" to "through many dangers toils and snares",
            ),
        )
        song(2, "Be Thou My Vision", listOf("Verse 1" to "be thou my vision o lord of my heart"))
        song(12, "How Great Thou Art", listOf("Verse 1" to "o lord my god when i in awesome wonder"))
        return settings(Tabs.SONGS, songSettings = verseMode())
    }

    private fun ComposeUiTest.search(query: String) {
        onAllNodes(hasSetTextAction())[0].performTextReplacement(query)
        waitForIdle()
    }

    private fun ComposeUiTest.listed(title: String): Boolean =
        onAllNodes(hasText(title)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    private fun ComposeUiTest.awaitLyrics(app: HappyApp, line: String) {
        await("\"$line\" is the song on air") {
            app.presenter.slideContent.value == Presenting.LYRICS &&
                line in app.presenter.displayedLyricSection.value.lines.joinToString()
        }
    }

    @Test
    fun `a song found by title goes live`() = happyPath(library()) { app ->
        search("Amazing")
        assertTrue(listed("Amazing Grace"))
        assertFalse(listed("Be Thou My Vision"), "the search narrows the list")

        clickText("Amazing Grace")
        goLive()

        awaitLyrics(app, "amazing grace how sweet the sound")
        awaitOnOutput(app, "amazing grace how sweet the sound")
    }

    @Test
    fun `a song found by number goes live`() = happyPath(library()) { app ->
        search("12")
        assertTrue(listed("How Great Thou Art"))
        assertFalse(listed("Amazing Grace"), "a number finds that song alone")

        clickText("How Great Thou Art")
        goLive()

        awaitLyrics(app, "o lord my god when i in awesome wonder")
        awaitOnOutput(app, "o lord my god when i in awesome wonder")
    }

    @Test
    fun `the live song steps forward and back`() = happyPath(library()) { app ->
        clickText("Amazing Grace")
        goLive()
        awaitLyrics(app, "amazing grace how sweet the sound")

        press(Key.DirectionDown)
        awaitLyrics(app, "twas grace that taught my heart to fear")
        awaitOnOutput(app, "twas grace that taught my heart to fear")

        press(Key.DirectionUp)
        awaitLyrics(app, "amazing grace how sweet the sound")
        awaitOnOutput(app, "amazing grace how sweet the sound")
    }

    @Test
    fun `clicking a section of the live song puts it on air`() = happyPath(library()) { app ->
        clickText("Amazing Grace")
        goLive()
        awaitLyrics(app, "amazing grace how sweet the sound")

        clickText("through many dangers toils and snares")

        awaitLyrics(app, "through many dangers toils and snares")
        awaitOnOutput(app, "through many dangers toils and snares")
    }

    @Test
    fun `a song added to the schedule becomes a row`() = happyPath(library()) { app ->
        clickText("Be Thou My Vision")
        onNodeWithTag(ADD_SELECTED_TAG).performClick()
        waitForIdle()

        await("the schedule holds the song") { scheduleShows("Be Thou My Vision") }
        assertEquals(1, app.schedule().size)
        assertTrue(app.schedule().single().displayText.contains("Be Thou My Vision"))
    }

    @Test
    fun `a starred song shows under favorites`() = happyPath(library()) { _ ->
        val row = onAllNodes(hasText("Amazing Grace")).fetchSemanticsNodes()
            .minBy { it.boundsInRoot.left }.boundsInRoot
        val stars = onAllNodes(hasContentDescription("Add to favorites"))
        val star = stars.fetchSemanticsNodes().indexOfFirst { it.boundsInRoot.center.y in row.top..row.bottom }
        assertTrue(star >= 0, "the row carries a star")
        stars[star].performClick()
        waitForIdle()

        await("the favorites panel lists the song") { listed("1. Amazing Grace") }
        assertTrue(listed("Favorites"))
    }
}
