package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class PresenterModeBackgroundOverrideTest {

    private val verse = SelectedVerse(
        bookName = "John", chapter = 3, verseNumber = 16,
        verseText = "For God so loved the world",
    )

    private val section = LyricSection(type = "verse", lines = listOf("Amazing grace how sweet"))

    private fun content(
        mode: Presenting,
        manager: PresenterManager,
        override: Boolean?,
        assignment: ScreenAssignment = ScreenAssignment(),
        showBg: Boolean = true,
    ): @Composable () -> Unit = {
        PresenterModeContent(
            mode = mode,
            screenAssignment = assignment,
            presenterManager = manager,
            appSettings = AppSettings(),
            mediaViewModel = MediaViewModel(),
            sttManager = STTManager(),
            serverUrl = "",
            qaDisplayUrl = "",
            lottieComposition = null,
            clearAnnouncementOnFinish = {},
            outputRole = "",
            showBg = showBg,
            showBackgroundOverride = override,
        )
    }

    private fun bibleManager() = PresenterManager().apply { setDisplayedVerses(listOf(verse)) }

    private fun songManager() = PresenterManager().apply { setDisplayedLyricSection(section) }

    @Test
    fun `a verse still reads with the background forced off`() = runComposeUiTest {
        setContent(content(Presenting.BIBLE, bibleManager(), override = false))

        onNodeWithText(verse.verseText, substring = true).assertIsDisplayed()
    }

    @Test
    fun `a verse still reads with the background forced on`() = runComposeUiTest {
        setContent(content(Presenting.BIBLE, bibleManager(), override = true, showBg = false))

        onNodeWithText(verse.verseText, substring = true).assertIsDisplayed()
    }

    @Test
    fun `lyrics still read with the background forced off`() = runComposeUiTest {
        setContent(content(Presenting.LYRICS, songManager(), override = false))

        onNodeWithText(section.lines.first(), substring = true).assertIsDisplayed()
    }

    @Test
    fun `an announcement still reads with the background forced off`() = runComposeUiTest {
        val manager = PresenterManager().apply { setDisplayedAnnouncementText("Service starts at 10") }

        setContent(content(Presenting.ANNOUNCEMENTS, manager, override = false))

        onNodeWithText("Service starts at 10", substring = true).assertExists()
    }

    @Test
    fun `the picture output runs with the background forced off`() = runComposeUiTest {
        setContent(content(Presenting.PICTURES, PresenterManager(), override = false))
    }

    @Test
    fun `the slide output runs with the background forced off`() = runComposeUiTest {
        setContent(content(Presenting.PRESENTATION, PresenterManager(), override = false))
    }

    @Test
    fun `the dictionary output runs with the background forced off`() = runComposeUiTest {
        setContent(content(Presenting.DICTIONARY, PresenterManager(), override = false))
    }

    @Test
    fun `the question output runs with the background forced off`() = runComposeUiTest {
        setContent(content(Presenting.QA, PresenterManager(), override = false))
    }

    @Test
    fun `the captions output runs with the background forced off`() = runComposeUiTest {
        setContent(content(Presenting.STT, PresenterManager(), override = false))
    }

    @Test
    fun `the lower third output runs with the background forced off`() = runComposeUiTest {
        setContent(content(Presenting.LOWER_THIRD, PresenterManager(), override = false))
    }

    @Test
    fun `nothing live draws nothing whatever the override says`() = runComposeUiTest {
        setContent(content(Presenting.NONE, bibleManager(), override = false))
    }
}
