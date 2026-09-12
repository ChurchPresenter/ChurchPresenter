package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.presenter.BibleBandPhase
import org.churchpresenter.app.churchpresenter.presenter.LottieBandTestSupport
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LottieBandDriverTest {

    private val dir = Files.createTempDirectory("band-driver").toFile()
    private val template = LottieBandTestSupport.writeTemplate(
        dir,
        cfg = BibleLottieGenConfig(
            canvasW = 960, canvasH = 180,
            bgInSeconds = 0.1f, textInSeconds = 0.1f, holdSeconds = 0.2f, textOutSeconds = 0.1f, bgOutSeconds = 0.1f,
        ),
    )
    private val verse =
        SelectedVerse(bookName = "John", chapter = 3, verseNumber = 16, verseText = "For God so loved the world")
    private val next = verse.copy(verseNumber = 17, verseText = "For God did not send his Son")
    private val phases = mutableListOf<BibleBandPhase>()

    /** The phases a band goes through on go live and then one text change. */
    private val enterThenSwap = listOf(
        BibleBandPhase.IDLE, BibleBandPhase.ENTER, BibleBandPhase.HOLD,
        BibleBandPhase.TEXT_OUT, BibleBandPhase.TEXT_IN, BibleBandPhase.HOLD,
    )

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun lottie() =
        BackgroundConfig(backgroundType = Constants.BACKGROUND_LOTTIE, backgroundLottie = template.path)

    private fun settings(bible: Boolean = true, song: Boolean = false) = AppSettings(
        backgroundSettings = BackgroundSettings(
            bibleLowerThirdBackground = if (bible) lottie() else BackgroundConfig(),
            songLowerThirdBackground = if (song) lottie() else BackgroundConfig(),
        ),
    )

    private fun ComposeUiTest.effects(manager: PresenterManager, settings: AppSettings) = setContent {
        PresenterTransitionEffects(manager, settings)
        LaunchedEffect(Unit) { snapshotFlow { manager.lottieBandClock.value.phase }.collect { phases += it } }
    }

    private fun ComposeUiTest.goLive(manager: PresenterManager) {
        manager.setSelectedVerses(listOf(verse))
        manager.setPresentingMode(Presenting.BIBLE)
        waitUntil("the band entered and holds") { manager.lottieBandClock.value.phase == BibleBandPhase.HOLD }
    }

    @Test
    fun `going live plays the entrance and holds with the verse on the output`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(manager, settings())
        goLive(manager)
        assertEquals(listOf(verse), manager.displayedVerses.value)
        assertEquals(listOf(BibleBandPhase.IDLE, BibleBandPhase.ENTER, BibleBandPhase.HOLD), phases)
    }

    @Test
    fun `a verse change while the band holds swaps the text out and in`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(manager, settings())
        goLive(manager)
        manager.setSelectedVerses(listOf(next))
        waitUntil("the next verse is on the output") {
            manager.displayedVerses.value == listOf(next) && manager.lottieBandClock.value.phase == BibleBandPhase.HOLD
        }
        assertEquals(enterThenSwap, phases)
    }

    @Test
    fun `clearing plays the exit, drops the clock to idle and leaves nothing live`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(manager, settings())
        goLive(manager)
        manager.requestClearDisplay()
        waitUntil("the display cleared") { manager.presentingMode.value == Presenting.NONE }
        assertEquals(BibleBandPhase.IDLE, manager.lottieBandClock.value.phase)
        assertTrue(BibleBandPhase.EXIT in phases, "the exit played: $phases")
        assertEquals(1f, manager.bibleTransitionAlpha.value, "the classic fade did not run")
    }

    @Test
    fun `without a band the classic path is untouched and the clock stays idle`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(manager, settings(bible = false))
        manager.setSelectedVerses(listOf(verse))
        manager.setPresentingMode(Presenting.BIBLE)
        waitUntil("the verse reached the output") { manager.displayedVerses.value == listOf(verse) }
        assertEquals(listOf(BibleBandPhase.IDLE), phases)
    }

    @Test
    fun `a song band enters on go live and swaps its text when the line moves`() = runComposeUiTest {
        val manager = PresenterManager()
        val section = LyricSection(type = "verse", lines = listOf("Amazing grace", "how sweet the sound"))
        effects(manager, settings(bible = false, song = true))
        manager.setLyricSection(section)
        manager.setPresentingMode(Presenting.LYRICS)
        waitUntil("the band holds") { manager.lottieBandClock.value.phase == BibleBandPhase.HOLD }
        assertEquals(section.lines, manager.displayedLyricSection.value.lines)
        manager.setSongDisplayLineIndex(1)
        waitUntil("the next line is on the band") {
            manager.bandSongLineIndex.value == 1 && manager.lottieBandClock.value.phase == BibleBandPhase.HOLD
        }
        assertEquals(enterThenSwap, phases)
    }
}
