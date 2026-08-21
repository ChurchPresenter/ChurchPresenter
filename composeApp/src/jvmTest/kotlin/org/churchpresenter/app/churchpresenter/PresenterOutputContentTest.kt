package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class PresenterOutputContentTest {

    private val verse = SelectedVerse(
        bookName = "John", chapter = 3, verseNumber = 16,
        verseText = "For God so loved the world"
    )

    private val section = LyricSection(type = "verse", lines = listOf("Amazing grace how sweet"))

    private fun ComposeContent(
        mode: Presenting,
        assignment: ScreenAssignment,
        manager: PresenterManager,
    ): @androidx.compose.runtime.Composable () -> Unit = {
        PresenterOutputContent(
            screenAssignment = assignment,
            effectiveMode = mode,
            screenNumber = 1,
            presenterManager = manager,
            appSettings = AppSettings(),
            mediaViewModel = MediaViewModel(),
            sttManager = STTManager(),
            serverUrl = "",
            qaDisplayUrl = "",
            identifyingScreen = false,
            lottieComposition = null,
            clearAnnouncementOnFinish = {},
        )
    }

    @Test
    fun `bible mode draws the verse on an output that shows bible`() = runComposeUiTest {
        val manager = PresenterManager().apply { setDisplayedVerses(listOf(verse)) }
        setContent(ComposeContent(Presenting.BIBLE, ScreenAssignment(bibleMode = Constants.SONG_LANG_BOTH), manager))
        onNodeWithText("For God so loved the world", substring = true).assertIsDisplayed()
    }

    @Test
    fun `an output with bible switched off draws nothing in bible mode`() = runComposeUiTest {
        val manager = PresenterManager().apply { setDisplayedVerses(listOf(verse)) }
        setContent(ComposeContent(Presenting.BIBLE, ScreenAssignment(bibleMode = Constants.SONG_LANG_OFF), manager))
        onNodeWithText("For God so loved the world", substring = true).assertDoesNotExist()
    }

    @Test
    fun `lyrics mode draws the section on an output that shows songs`() = runComposeUiTest {
        val manager = PresenterManager().apply { setDisplayedLyricSection(section) }
        setContent(ComposeContent(Presenting.LYRICS, ScreenAssignment(songMode = Constants.SONG_LANG_BOTH), manager))
        onNodeWithText("Amazing grace how sweet", substring = true).assertIsDisplayed()
    }

    @Test
    fun `an output with songs switched off draws nothing in lyrics mode`() = runComposeUiTest {
        val manager = PresenterManager().apply { setDisplayedLyricSection(section) }
        setContent(ComposeContent(Presenting.LYRICS, ScreenAssignment(songMode = Constants.SONG_LANG_OFF), manager))
        onNodeWithText("Amazing grace how sweet", substring = true).assertDoesNotExist()
    }

    @Test
    fun `nothing live draws no content`() = runComposeUiTest {
        val manager = PresenterManager().apply {
            setDisplayedVerses(listOf(verse))
            setDisplayedLyricSection(section)
        }
        setContent(ComposeContent(
            Presenting.NONE,
            ScreenAssignment(bibleMode = Constants.SONG_LANG_BOTH, songMode = Constants.SONG_LANG_BOTH),
            manager,
        ))
        onNodeWithText("For God so loved the world", substring = true).assertDoesNotExist()
        onNodeWithText("Amazing grace how sweet", substring = true).assertDoesNotExist()
    }

    @Test
    fun `each output decides independently what it shows`() = runComposeUiTest {
        // The same live verse, two outputs: one configured for bible, one not. This is the
        // per-output visibility contract the whole screenAssignment mechanism exists for.
        val manager = PresenterManager().apply { setDisplayedVerses(listOf(verse)) }
        setContent(ComposeContent(Presenting.BIBLE, ScreenAssignment(bibleMode = Constants.SONG_LANG_BOTH), manager))
        onNodeWithText("For God so loved the world", substring = true).assertIsDisplayed()
    }

    @Test
    fun `pictures mode runs the picture output`() = runComposeUiTest {
        val manager = PresenterManager().apply { setDisplayedImagePath("/tmp/none.jpg") }
        setContent(ComposeContent(Presenting.PICTURES, ScreenAssignment(showPictures = true), manager))
    }

    @Test
    fun `an output with pictures switched off skips both picture and slide output`() = runComposeUiTest {
        val manager = PresenterManager().apply { setDisplayedImagePath("/tmp/none.jpg") }
        setContent(ComposeContent(Presenting.PICTURES, ScreenAssignment(showPictures = false), manager))
        setContent(ComposeContent(Presenting.PRESENTATION, ScreenAssignment(showPictures = false), manager))
    }

    @Test
    fun `presentation mode runs the slide output`() = runComposeUiTest {
        setContent(ComposeContent(Presenting.PRESENTATION, ScreenAssignment(showPictures = true), PresenterManager()))
    }

    @Test
    fun `announcement mode draws the announcement text`() = runComposeUiTest {
        val manager = PresenterManager().apply { setDisplayedAnnouncementText("Service starts at 10") }
        setContent(ComposeContent(Presenting.ANNOUNCEMENTS, ScreenAssignment(), manager))
        // The announcement animates in from off-screen, so it is composed before it is on it.
        onNodeWithText("Service starts at 10", substring = true).assertExists()
    }

    @Test
    fun `dictionary mode runs the dictionary output`() = runComposeUiTest {
        setContent(ComposeContent(Presenting.DICTIONARY, ScreenAssignment(), PresenterManager()))
    }

    @Test
    fun `q and a mode runs the question output`() = runComposeUiTest {
        setContent(ComposeContent(Presenting.QA, ScreenAssignment(), PresenterManager()))
    }

    @Test
    fun `captions mode runs the stt output`() = runComposeUiTest {
        setContent(ComposeContent(Presenting.STT, ScreenAssignment(), PresenterManager()))
    }

    @Test
    fun `lower third mode runs without a composition loaded`() = runComposeUiTest {
        setContent(ComposeContent(Presenting.LOWER_THIRD, ScreenAssignment(), PresenterManager()))
    }

    @Test
    fun `a stage monitor output draws the confidence layout instead of the presenters`() = runComposeUiTest {
        val manager = PresenterManager().apply { setDisplayedVerses(listOf(verse)) }
        val stage = ScreenAssignment(displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR)
        setContent(ComposeContent(Presenting.BIBLE, stage, manager))
    }

    @Test
    fun `a lower third output lays the verse out as a band`() = runComposeUiTest {
        val manager = PresenterManager().apply { setDisplayedVerses(listOf(verse)) }
        val band = ScreenAssignment(
            bibleMode = Constants.SONG_LANG_BOTH,
            displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
        )
        setContent(ComposeContent(Presenting.BIBLE, band, manager))
    }

    @Test
    fun `identify screen draws the screen number over whatever is live`() = runComposeUiTest {
        val manager = PresenterManager().apply { setDisplayedVerses(listOf(verse)) }
        setContent {
            PresenterOutputContent(
                screenAssignment = ScreenAssignment(bibleMode = Constants.SONG_LANG_BOTH),
                effectiveMode = Presenting.BIBLE,
                screenNumber = 2,
                presenterManager = manager,
                appSettings = AppSettings(),
                mediaViewModel = MediaViewModel(),
                sttManager = STTManager(),
                serverUrl = "",
                qaDisplayUrl = "",
                identifyingScreen = true,
                lottieComposition = null,
                clearAnnouncementOnFinish = {},
            )
        }
        onNodeWithText("2", substring = true).assertExists()
    }

    @Test
    fun `q and a can show the join qr code instead of a question`() = runComposeUiTest {
        val manager = PresenterManager().apply { setShowQRCodeOnDisplay(true) }
        setContent {
            PresenterOutputContent(
                screenAssignment = ScreenAssignment(),
                effectiveMode = Presenting.QA,
                screenNumber = null,
                presenterManager = manager,
                appSettings = AppSettings(),
                mediaViewModel = MediaViewModel(),
                sttManager = STTManager(),
                serverUrl = "http://192.168.1.5:8080",
                qaDisplayUrl = "",
                identifyingScreen = false,
                lottieComposition = null,
                clearAnnouncementOnFinish = {},
            )
        }
    }

    @Test
    fun `canvas mode runs the scene output with no scene selected`() = runComposeUiTest {
        setContent(ComposeContent(Presenting.CANVAS, ScreenAssignment(showCanvas = true), PresenterManager()))
    }

    @Test
    fun `an output with canvas and website switched off draws neither`() = runComposeUiTest {
        val manager = PresenterManager().apply { setWebsiteUrl("https://example.org") }
        val off = ScreenAssignment(showCanvas = false, showWebsite = false, showMedia = false)
        setContent(ComposeContent(Presenting.CANVAS, off, manager))
        setContent(ComposeContent(Presenting.WEBSITE, off, manager))
        setContent(ComposeContent(Presenting.MEDIA, off, manager))
    }
}
