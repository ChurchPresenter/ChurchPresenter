@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The switch that gives an output appearance of its own, and the Reset that takes it away again.
 * Until it is on, a category follows the global settings and stores nothing.
 */
class ProjectionCustomizeOverrideTest {

    private fun output() = AppSettings(
        songSettings = SongSettings(lyricsFontSize = 61),
        bibleSettings = BibleSettings(
            translations = listOf(BibleTranslationSettings(fileName = "kjv.spb", textFontSize = 55)),
        ),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(ScreenAssignment(displayMode = Constants.DISPLAY_MODE_FULLSCREEN)),
        ),
    )

    private fun AppSettings.assignment(): ScreenAssignment = projectionSettings.screenAssignments[0]

    private fun ComposeUiTest.flipOverride() {
        onNodeWithTag(CUSTOMIZE_OVERRIDE_SWITCH_TAG).performClick()
        waitForIdle()
    }

    // ── Before it is switched on ──────────────────────────────────────────────

    @Test
    fun `a fresh output stores no override for any category`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS, override = false)
            val assignment = get().assignment()
            assertNull(assignment.songOverride)
            assertNull(assignment.bibleOverride)
            assertNull(assignment.backgroundOverride)
            assertNull(assignment.dictionaryOverride)
        }
    }

    @Test
    fun `the switch starts off`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS, override = false)
            onNodeWithTag(CUSTOMIZE_OVERRIDE_SWITCH_TAG).assertIsOff()
        }
    }

    @Test
    fun `the header counts nothing customized`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS, override = false)
            onNodeWithTag(CUSTOMIZE_STATUS_TAG).assertExists()
            onNodeWithText("0 of 4 customized").assertExists()
        }
    }

    @Test
    fun `Reset hands a customized category back to the global settings`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            retypeNumberField(61, 90)
            assertEquals(90, assertNotNull(get().assignment().songOverride).lyricsFontSize)

            onNodeWithText("Reset to global").performClick()
            waitForIdle()

            assertNull(
                get().assignment().songOverride,
                "Reset gives the category up entirely rather than restoring values into it",
            )
        }
    }

    // ── Switching it on ───────────────────────────────────────────────────────

    @Test
    fun `switching it on gives the output its own Songs`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS, override = false)
            flipOverride()
            assertNotNull(get().assignment().songOverride)
        }
    }

    @Test
    fun `the override starts as a copy of the global settings`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS, override = false)
            flipOverride()
            assertEquals(
                61,
                assertNotNull(get().assignment().songOverride).lyricsFontSize,
                "an output starts where the global settings are, not at the defaults",
            )
        }
    }

    @Test
    fun `switching it on reads as on`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS, override = false)
            flipOverride()
            onNodeWithTag(CUSTOMIZE_OVERRIDE_SWITCH_TAG).assertIsOn()
        }
    }

    @Test
    fun `the header counts the category once it is customized`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS, override = false)
            flipOverride()
            onNodeWithText("1 of 4 customized").assertExists()
        }
    }

    @Test
    fun `a customized category offers to be reset`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS, override = false)
            flipOverride()
            onNodeWithText("Reset to global").assertExists()
        }
    }

    @Test
    fun `switching one category on leaves the others following`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS, override = false)
            flipOverride()

            val assignment = get().assignment()
            assertNotNull(assignment.songOverride)
            assertNull(assignment.bibleOverride, "the Bible must still follow the global settings")
            assertNull(assignment.backgroundOverride)
        }
    }

    // ── Switching it back off ─────────────────────────────────────────────────

    @Test
    fun `switching it back off gives the category up`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS, override = false)
            flipOverride()
            flipOverride()
            assertNull(get().assignment().songOverride)
        }
    }

    @Test
    fun `edits made while it was on are dropped with it`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            retypeNumberField(61, 90)
            assertEquals(90, assertNotNull(get().assignment().songOverride).lyricsFontSize)

            flipOverride()
            assertNull(get().assignment().songOverride, "following the global settings again means storing nothing")
        }
    }

    @Test
    fun `the header counts back down`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS, override = false)
            flipOverride()
            onNodeWithText("1 of 4 customized").assertExists()
            flipOverride()
            onNodeWithText("0 of 4 customized").assertExists()
        }
    }

    // ── Each category on its own ──────────────────────────────────────────────

    @Test
    fun `the Bible category is overridden on its own`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE, override = false)
            flipOverride()

            val assignment = get().assignment()
            assertNotNull(assignment.bibleOverride)
            assertNull(assignment.songOverride)
        }
    }

    @Test
    fun `the Background category is overridden on its own`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, override = false)
            flipOverride()

            val assignment = get().assignment()
            assertNotNull(assignment.backgroundOverride)
            assertNull(assignment.songOverride)
        }
    }

    @Test
    fun `the Dictionary category is overridden on its own`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.DICTIONARY, override = false)
            flipOverride()

            val assignment = get().assignment()
            assertNotNull(assignment.dictionaryOverride)
            assertNull(assignment.bibleOverride)
        }
    }

    @Test
    fun `two categories can be customized at once`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS, override = false)
            flipOverride()
            onNodeWithTag(railTag(CustomizePane.BIBLE.name)).performClick()
            waitForIdle()
            flipOverride()

            val assignment = get().assignment()
            assertNotNull(assignment.songOverride)
            assertNotNull(assignment.bibleOverride)
            onNodeWithText("2 of 4 customized").assertExists()
        }
    }

    @Test
    fun `the Bible override starts from the global translation stack`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BIBLE, override = false)
            flipOverride()

            assertEquals(
                55,
                assertNotNull(get().assignment().bibleOverride).translationList()[0].textFontSize,
                "the output starts where the global Bible settings are",
            )
        }
    }

    @Test
    fun `the stage monitor is one of the four categories counted`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS, override = false)
            // Four: Bible, Songs, Background, Dictionary. The stage monitor's pane has no elements
            // and is counted separately from these.
            onNodeWithText("0 of 4 customized").assertExists()
        }
    }
}
