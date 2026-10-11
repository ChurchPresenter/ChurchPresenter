@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.companionsurface.CompanionSatelliteViewModel
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongFileParser
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.liveoutput.PresenterTransitionEffects
import org.churchpresenter.schedule.SCHEDULE_ROW_CARD_TAG
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.Tabs
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A service, start to finish, in one session of the composed root: two songs and a Bible reading
 * on the schedule, each taken live in turn, then cleared. Every other suite starts each step from a
 * fresh app; this one keeps the app between them, which is where a step that leaves something
 * behind -- a song's slide under the Bible, a reading still up after Clear -- shows.
 *
 * The output callbacks drive a real [PresenterManager] as the app's own wiring does, and the
 * output's [PresenterTransitionEffects] run beside the root, as the output windows run them, so a
 * Clear finishes the way it does on air.
 */
class ServiceHappyPathTest : MainDesktopComposeHarness() {

    private fun song(number: Int, title: String, verses: List<String>) {
        val book = File(dir, "Hymnal").apply { mkdirs() }
        SongFileParser().writeSongFile(
            SongItem(
                number = "$number",
                title = title,
                songbook = "Hymnal",
                lyrics = verses.flatMapIndexed { i, line -> listOf("[Verse ${i + 1}]", line) },
            ),
            File(book, "$number - $title.song").absolutePath,
        )
    }

    private fun service(): AppSettings {
        song(1, "Opening Hymn", listOf("opening first verse", "opening second verse"))
        song(2, "Closing Hymn", listOf("closing first verse"))
        SpbFixture.spbFile(
            dir,
            name = "test.spb",
            content = SpbFixture.buildContent(
                title = "Test Bible",
                books = listOf(SpbFixture.Book(1, "Genesis", 1)),
                verses = (1..3).map { SpbFixture.Verse(1, 1, it, "Genesis one verse $it") },
            ),
        )
        return settings().copy(
            bibleSettings = BibleSettings(
                storageDirectory = dir.absolutePath,
                primaryBible = "test.spb",
                translations = listOf(BibleTranslationSettings(fileName = "test.spb")),
            ),
            hiddenTabs = Tabs.entries.filter { it != Tabs.SONGS && it != Tabs.BIBLE }.map { it.name }.toSet(),
        )
    }

    private fun ComposeUiTest.goLiveOnRow(index: Int) {
        onAllNodes(hasContentDescription("Go Live") and hasAnyAncestor(hasTestTag(SCHEDULE_ROW_CARD_TAG)))[index]
            .performClick()
        waitForIdle()
    }

    @Test
    fun `a service runs from the first song to the last clear without leaving anything behind`() = runComposeUiTest {
        val settings = service()
        val presenter = PresenterManager()
        val pushed = mutableListOf<LyricSection>()
        var actions = ScheduleActions()
        setContent {
            MaterialTheme {
                MainDesktop(
                    appSettings = settings,
                    presenterManager = presenter,
                    companionSatelliteViewModel = CompanionSatelliteViewModel(),
                    // As MainWindowChrome wires them: through the Preview bus, which passes
                    // straight to the air while preview mode is off.
                    live = LiveOutputCallbacks(
                        presenting = { presenter.previewBus.present(it) },
                        onVerseSelected = { presenter.previewBus.forVerses(it).setSelectedVerses(it) },
                        onSongItemSelected = { section ->
                            pushed += section
                            presenter.previewBus.forSong(section).setLyricSection(section)
                        },
                        onAllSectionsChanged = { sections ->
                            presenter.previewBus.forSong(sections.firstOrNull()).setAllLyricSections(sections)
                        },
                        onSectionIndexChanged = { presenter.previewBus.songStepTarget.setSongDisplaySectionIndex(it) },
                        onLineIndexChanged = { presenter.previewBus.songStepTarget.setSongDisplayLineIndex(it) },
                    ),
                    publish = MainDesktopPublishers(onScheduleActionsReady = { actions = it }),
                )
                PresenterTransitionEffects(presenter, settings)
            }
        }
        waitForIdle()
        actions.addSong(1, "Opening Hymn", "Hymnal", "Hymnal::1")
        actions.addBibleVerse("Genesis", 1, 2, "Genesis one verse 2", "", 1)
        actions.addSong(2, "Closing Hymn", "Hymnal", "Hymnal::2")
        waitForIdle()

        // The opening song, from its row.
        goLiveOnRow(0)
        waitUntil(timeoutMillis = 5_000) { presenter.slideContent.value == Presenting.LYRICS }
        assertTrue("opening first verse" in pushed.last().lines.joinToString(), "the song's first verse is up")

        // Its next verse, as the operator steps it.
        press(Key.DirectionDown)
        waitUntil(timeoutMillis = 5_000) { "opening second verse" in pushed.last().lines.joinToString() }

        // The reading takes the song's place.
        takeLive("Genesis")
        waitUntil(timeoutMillis = 5_000) { presenter.slideContent.value == Presenting.BIBLE }
        assertEquals(2, presenter.selectedVerses.value.single().verseNumber, "the verse on the row")
        assertFalse(presenter.isLive(Presenting.LYRICS), "no song left under the reading")

        // The closing song takes the reading's place.
        goLiveOnRow(2)
        waitUntil(timeoutMillis = 5_000) { presenter.slideContent.value == Presenting.LYRICS }
        assertTrue("closing first verse" in pushed.last().lines.joinToString(), "the closing song, not the opening one")
        assertFalse(presenter.isLive(Presenting.BIBLE), "no reading left under the song")

        // And Clear takes everything down.
        press(Key.Escape)
        waitUntil(timeoutMillis = 5_000) { !presenter.anythingLive }
        assertEquals(Presenting.NONE, presenter.slideContent.value)
    }
}
