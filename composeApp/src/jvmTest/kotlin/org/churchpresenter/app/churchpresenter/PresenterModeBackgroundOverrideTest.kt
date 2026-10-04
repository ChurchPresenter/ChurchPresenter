package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.Composable
import org.churchpresenter.app.churchpresenter.presenter.WEB_SNAPSHOT_TAG
import org.churchpresenter.presenter.LocalInMergedTile
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.stt.STTManager
import kotlin.test.Test
import kotlin.test.assertTrue

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
        profile: OutputProfile = OutputProfile(),
        showBg: Boolean = true,
    ): @Composable () -> Unit = {
        PresenterModeContent(
            mode = mode,
            profile = profile,
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

    /** A profile that shows nothing at all -- every kind of content switched off. */
    private val showsNothing = OutputProfile(
        bibleMode = Constants.SONG_LANG_OFF,
        songMode = Constants.SONG_LANG_OFF,
        showPictures = false,
        showMedia = false,
        showStreaming = false,
        showAnnouncements = false,
        showWebsite = false,
        showQA = false,
        showSTT = false,
        showDictionary = false,
        showCanvas = false,
    )

    @Test
    fun `a profile that hides a kind of content draws none of it`() {
        val manager = PresenterManager().apply {
            setDisplayedVerses(listOf(verse))
            setDisplayedLyricSection(section)
            setDisplayedAnnouncementText("Service starts at 10")
        }
        Presenting.entries.forEach { mode ->
            runComposeUiTest {
                setContent(content(mode, manager, override = null, profile = showsNothing))
                waitForIdle()
                listOf(verse.verseText, section.lines.first(), "Service starts at 10").forEach { text ->
                    assertTrue(
                        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty(),
                        "$mode drew '$text' on a profile that hides it",
                    )
                }
            }
        }
    }

    @Test
    fun `inside a merged tile a website is drawn from its snapshot, once there is one`() {
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalInMergedTile provides true) {
                    content(Presenting.WEBSITE, PresenterManager(), override = null)()
                }
            }
            onAllNodes(hasTestTag(WEB_SNAPSHOT_TAG)).assertCountEquals(0)
        }
        val withSnapshot = PresenterManager().apply { setWebSnapshot(ImageBitmap(4, 4)) }
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalInMergedTile provides true) {
                    content(Presenting.WEBSITE, withSnapshot, override = null)()
                }
            }
            onAllNodes(hasTestTag(WEB_SNAPSHOT_TAG)).assertCountEquals(1)
        }
    }

    @Test
    fun `a lower-third profile draws the verse and the lyrics in its band`() {
        val band = OutputProfile(displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)
        runComposeUiTest {
            setContent(content(Presenting.BIBLE, bibleManager(), override = null, profile = band))
            onNodeWithText(verse.verseText, substring = true).assertExists()
        }
        runComposeUiTest {
            setContent(content(Presenting.LYRICS, songManager(), override = null, profile = band))
            onNodeWithText(section.lines.first(), substring = true).assertExists()
        }
    }

    @Test
    fun `a profile with its backgrounds off still draws the words`() {
        val bare = OutputProfile(showBibleBackground = false, showSongsBackground = false)
        runComposeUiTest {
            setContent(content(Presenting.BIBLE, bibleManager(), override = null, profile = bare))
            onNodeWithText(verse.verseText, substring = true).assertExists()
        }
        runComposeUiTest {
            setContent(content(Presenting.LYRICS, songManager(), override = null, profile = bare))
            onNodeWithText(section.lines.first(), substring = true).assertExists()
        }
    }
}
