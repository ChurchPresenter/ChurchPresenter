@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import org.churchpresenter.settings.withLook
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The profile header: the display mode, and the switches saying which kinds of content this
 * profile's outputs draw.
 *
 * The content switches are **not** gated on the display mode. `showsContentFor` is display-mode
 * agnostic -- a stage monitor obeys `showPictures`, `showQA` and the rest exactly as a full screen
 * does -- so hiding those switches on a stage monitor left an operator no way to stop a confidence
 * screen showing the dictionary card.
 */
class ProfileHeaderTest {

    private fun doc(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) = profileDocument(mode = mode)

    /**
     * A display-mode segment.
     *
     * By role, not text alone: "Lower Third" is also the start of the "Lower Third Background"
     * content switch, so text on its own matches two nodes and the click fails on the ambiguity.
     */
    private fun SkikoComposeUiTest.displayMode(label: String) =
        onNode(hasTextExactly(label) and hasClickAction() and !isToggleable())

    // ── Display mode ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the display mode can be switched to a lower third and back`() {
        profilesTab(doc()) { get ->
            displayMode("Lower third").performClick()
            waitForIdle()
            assertTrue(get().profile().isLowerThird, "it must now be a band")

            displayMode("Full screen").performClick()
            waitForIdle()
            assertEquals(Constants.DISPLAY_MODE_FULLSCREEN, get().profile().displayMode)
        }
    }

    @Test
    fun `a stage monitor is its own display mode`() {
        profilesTab(doc()) { get ->
            displayMode("Stage monitor").performClick()
            waitForIdle()

            assertEquals(Constants.DISPLAY_MODE_STAGE_MONITOR, get().profile().displayMode)
        }
    }

    @Test
    fun `a stage monitor offers only its own pane`() {
        profilesTab(doc(Constants.DISPLAY_MODE_STAGE_MONITOR)) { _ ->
            // It draws its own zones; it never draws the full-screen or lower-third Bible and Song
            // profiles, so those rails are not offered at all.
            onNodeWithTag(railTag(CustomizePane.STAGE_MONITOR.name)).assertExists()
            onNodeWithTag(railTag(CustomizePane.BIBLE.name)).assertDoesNotExist()
            onNodeWithTag(railTag(CustomizePane.SONGS.name)).assertDoesNotExist()
        }
    }

    @Test
    fun `a full screen and a band both offer the three styling panes`() {
        for (mode in listOf(Constants.DISPLAY_MODE_FULLSCREEN, Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)) {
            profilesTab(doc(mode)) { _ ->
                onNodeWithTag(railTag(CustomizePane.BIBLE.name)).assertExists()
                onNodeWithTag(railTag(CustomizePane.SONGS.name)).assertExists()
                onNodeWithTag(railTag(CustomizePane.BACKGROUND.name)).assertExists()
            }
        }
    }

    // ── The content switches ────────────────────────────────────────────────────────────────────

    @Test
    fun `each content switch writes its own flag and no other`() {
        profilesTab(doc()) { get ->
            contentSwitch("Canvas").performClick()
            waitForIdle()

            assertFalse(get().profile().look.slide.canvas, "Canvas ships on and must have gone off")
            assertTrue(get().profile().look.slide.qa, "the switch beside it must not move")
            assertTrue(get().profile().look.media.pictures)
        }
    }

    @Test
    fun `switching scripture off is stored as the language mode, not a flag`() {
        profilesTab(doc()) { get ->
            contentSwitch("Bible").performClick()
            waitForIdle()

            assertEquals(Constants.SONG_LANG_OFF, get().profile().bibleMode)
            assertFalse(get().profile().showBible)
        }
    }

    @Test
    fun `switching songs off is stored the same way`() {
        profilesTab(doc()) { get ->
            contentSwitch("Songs").performClick()
            waitForIdle()

            assertEquals(Constants.SONG_LANG_OFF, get().profile().songMode)
            assertFalse(get().profile().showSongs)
        }
    }

    @Test
    fun `the look-ahead switch is offered only while songs are on`() {
        profilesTab(doc()) { _ ->
            contentSwitch("Song look-ahead").assertExists()
            contentSwitch("Songs").performClick()
            waitForIdle()

            // With songs off there is no look-ahead to show.
            contentSwitch("Song look-ahead").assertDoesNotExist()
        }
    }

    @Test
    fun `a stage monitor still gets the ordinary content switches`() {
        profilesTab(doc(Constants.DISPLAY_MODE_STAGE_MONITOR)) { get ->
            contentSwitch("Dictionary").performClick()
            waitForIdle()

            assertFalse(
                get().profile().look.slide.dictionary,
                "a confidence screen must be able to stop showing the dictionary card",
            )
        }
    }

    @Test
    fun `the chords switch belongs to the stage monitor alone`() {
        profilesTab(doc(Constants.DISPLAY_MODE_STAGE_MONITOR)) { get ->
            contentSwitch("Show Chords").performClick()
            waitForIdle()
            assertEquals(!OutputProfile().showChords, get().profile().showChords)
        }
        profilesTab(doc()) { _ ->
            contentSwitch("Show Chords").assertDoesNotExist()
        }
    }

    @Test
    fun `the subtitles switch is there, having never had one anywhere else`() {
        profilesTab(doc()) { get ->
            val before = get().profile().look.media.subtitles
            contentSwitch("Subtitles").performClick()
            waitForIdle()

            assertEquals(!before, get().profile().look.media.subtitles)
        }
    }

    /**
     * Every content switch, driven.
     *
     * Rendering a switch proves nothing about the lambda behind it -- JaCoCo scores each one as its
     * own method, and a switch never clicked leaves its write uncovered and its wiring unproven. A
     * row wired to the flag beside its own is exactly the mistake this catches, and reading the
     * list does not.
     *
     * "Lower Third" is the streaming switch's caption, not the display mode's: the display mode is
     * a segment rather than a toggle, so the two are told apart by role.
     */
    @Test
    fun `every content switch flips its own flag`() {
        val switches: List<Pair<String, (OutputProfile) -> Boolean>> = listOf(
            "Pictures/Presentation" to { p -> p.look.media.pictures },
            "Media" to { p -> p.look.media.video },
            "Subtitles" to { p -> p.look.media.subtitles },
            "Lower Third" to { p -> p.look.graphics },
            "Announcements" to { p -> p.look.announcements },
            "Web" to { p -> p.look.slide.web },
            "Canvas" to { p -> p.look.slide.canvas },
            "Q&A" to { p -> p.look.slide.qa },
            "Live captions" to { p -> p.look.captions },
            "Dictionary" to { p -> p.look.slide.dictionary },
            "Background" to { p -> p.look.background.fullscreen },
            "Lower Third Background" to { p -> p.look.background.lowerThird },
            "Bible Background" to { p -> p.look.background.bible },
            "Songs Background" to { p -> p.look.background.songs },
            "Song look-ahead" to { p -> p.songLookAhead },
        )
        for ((label, read) in switches) {
            profilesTab(doc()) { get ->
                val before = read(get().profile())
                contentSwitch(label).performClick()
                waitForIdle()

                assertEquals(!before, read(get().profile()), "$label must flip its own flag")
            }
        }
    }

    @Test
    fun `flipping one switch leaves every other alone`() {
        profilesTab(doc()) { get ->
            val before = get().profile()
            contentSwitch("Media").performClick()
            waitForIdle()

            val after = get().profile()
            assertEquals(!before.look.media.video, after.look.media.video)
            val moved = before.withLook { copy(media = media.copy(video = after.look.media.video)) }
            assertEquals(moved, after, "nothing else moved")
        }
    }
}
