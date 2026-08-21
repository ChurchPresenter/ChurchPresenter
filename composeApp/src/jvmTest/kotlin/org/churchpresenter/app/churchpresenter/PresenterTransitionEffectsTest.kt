package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PresenterTransitionEffectsTest {

    private val verse = SelectedVerse(
        bookName = "John", chapter = 3, verseNumber = 16, verseText = "For God so loved the world"
    )
    private val section = LyricSection(type = "verse", lines = listOf("Amazing grace"))

    private fun ComposeUiTest.effects(
        manager: PresenterManager,
        settings: AppSettings = AppSettings(),
    ) = setContent { PresenterTransitionEffects(manager, settings) }

    @Test
    fun `a selected verse becomes the displayed verse`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(manager)
        manager.setSelectedVerses(listOf(verse))
        waitUntil("the verse reached the output") { manager.displayedVerses.value.isNotEmpty() }
        assertEquals(listOf(verse), manager.displayedVerses.value)
    }

    @Test
    fun `a verse arriving fully fades it in`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(manager)
        manager.setSelectedVerses(listOf(verse))
        waitUntil("the fade finished") { manager.bibleTransitionAlpha.value == 1f }
        assertEquals(1f, manager.bibleTransitionAlpha.value)
    }

    @Test
    fun `a selected lyric section becomes the displayed section`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(manager)
        manager.setLyricSection(section)
        waitUntil("the section reached the output") {
            manager.displayedLyricSection.value.lines.isNotEmpty()
        }
        assertEquals(section.lines, manager.displayedLyricSection.value.lines)
    }

    @Test
    fun `a lyric section arriving fully fades it in`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(manager)
        manager.setLyricSection(section)
        waitUntil("the fade finished") { manager.songTransitionAlpha.value == 1f }
        assertEquals(1f, manager.songTransitionAlpha.value)
    }

    @Test
    fun `a selected picture becomes the displayed picture`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(manager)
        manager.setSelectedImagePath("/tmp/photo.jpg")
        waitUntil("the picture reached the output") { manager.displayedImagePath.value != null }
        assertEquals("/tmp/photo.jpg", manager.displayedImagePath.value)
    }

    @Test
    fun `a picture arriving is fully opaque once its transition ends`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(manager)
        manager.setSelectedImagePath("/tmp/photo.jpg")
        waitUntil("the picture transition finished") { manager.pictureTransitionAlpha.value == 1f }
        assertEquals(1f, manager.pictureTransitionAlpha.value)
    }

    @Test
    fun `an announcement becomes the displayed announcement`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(manager)
        manager.setAnnouncementText("Service starts at 10")
        waitUntil("the announcement reached the output") {
            manager.displayedAnnouncementText.value.isNotEmpty()
        }
        assertEquals("Service starts at 10", manager.displayedAnnouncementText.value)
    }

    @Test
    fun `clearing the display fades the live content away`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(manager)
        manager.setSelectedVerses(listOf(verse))
        waitUntil("the verse is live") { manager.bibleTransitionAlpha.value == 1f }
        manager.setPresentingMode(Presenting.BIBLE)
        manager.requestClearDisplay()
        waitUntil("the fade-out ran") { manager.bibleTransitionAlpha.value < 1f }
        assertTrue(manager.bibleTransitionAlpha.value < 1f)
    }

    @Test
    fun `a second verse replaces the first on the output`() = runComposeUiTest {
        val manager = PresenterManager()
        val next = verse.copy(verseNumber = 17, verseText = "For God did not send his Son")
        effects(manager)
        manager.setSelectedVerses(listOf(verse))
        waitUntil("the first verse is live") { manager.displayedVerses.value.isNotEmpty() }
        manager.setSelectedVerses(listOf(next))
        waitUntil("the second verse replaced it") { manager.displayedVerses.value == listOf(next) }
        assertEquals(listOf(next), manager.displayedVerses.value)
    }

    @Test
    fun `composing with nothing selected leaves the output empty`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(manager)
        waitForIdle()
        assertTrue(manager.displayedVerses.value.isEmpty())
        assertTrue(manager.displayedAnnouncementText.value.isEmpty())
    }
}
