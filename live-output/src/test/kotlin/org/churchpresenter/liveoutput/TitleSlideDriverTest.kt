package org.churchpresenter.liveoutput

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.presenter.BibleBandPhase
import org.churchpresenter.presenter.LottieBandTestSupport
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.withTitleSlideLottie
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A song title slide played as its own Lottie (#971), on the title slide's own clock: in when the
 * title slide comes on screen, out before the first lyric replaces it, and out on clear.
 */
@OptIn(ExperimentalTestApi::class)
class TitleSlideDriverTest {

    private val dir = Files.createTempDirectory("title-slide-driver").toFile()
    private val template = LottieBandTestSupport.writeTemplate(
        dir,
        cfg = BibleLottieGenConfig(
            canvasW = 960, canvasH = 540,
            bgInSeconds = 0.1f, textInSeconds = 0.1f, holdSeconds = 0.2f, textOutSeconds = 0.1f, bgOutSeconds = 0.1f,
        ),
    )
    private val title =
        LyricSection(type = Constants.SECTION_TYPE_TITLE_SLIDE, title = "Amazing Grace", songNumber = 427)
    private val verse1 = LyricSection(type = "verse", title = "Amazing Grace", lines = listOf("Amazing grace"))
    private val phases = mutableListOf<BibleBandPhase>()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    /** One screen on a profile whose title slide plays the template -- on the lower third with [lowerThird]. */
    private fun settings(path: String = template.path, lowerThird: Boolean = false): AppSettings {
        val profile = OutputProfile(
            id = "p",
            songSettings = SongSettings().withTitleSlideLottie(lowerThird, path),
        ).let { if (lowerThird) it.copy(displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL) else it }
        val base = AppSettings()
        return base.copy(
            projectionSettings = base.projectionSettings.copy(
                outputProfiles = listOf(profile),
                screenAssignments = listOf(ScreenAssignment(activeProfileId = "p")),
            ),
        )
    }

    private fun ComposeUiTest.effects(manager: PresenterManager, settings: AppSettings) = setContent {
        PresenterTransitionEffects(manager, settings)
        LaunchedEffect(Unit) { snapshotFlow { manager.titleSlideClock.value.phase }.collect { phases += it } }
    }

    private fun ComposeUiTest.goLiveOnTitle(manager: PresenterManager) {
        manager.setLyricSection(title)
        manager.setPresentingMode(Presenting.LYRICS)
        waitUntil("the title played in and holds") { manager.titleSlideClock.value.phase == BibleBandPhase.HOLD }
    }

    @Test
    fun `the title slide's template is found through each screen's profile`() {
        assertEquals(template.path, titleSlideLottiePath(settings()))
        assertEquals(template.path, titleSlideLottiePath(settings(lowerThird = true)))
        assertNull(titleSlideLottiePath(settings(path = "")))
        assertNull(titleSlideLottiePath(AppSettings()), "no screen, no template")
    }

    @Test
    fun `going live on the title slide plays it in and holds`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(manager, settings())
        goLiveOnTitle(manager)
        assertEquals(listOf(BibleBandPhase.IDLE, BibleBandPhase.ENTER, BibleBandPhase.HOLD), phases)
        assertEquals(title, manager.displayedLyricSection.value)
    }

    @Test
    fun `the first lyric waits for the title to play out`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(manager, settings())
        goLiveOnTitle(manager)
        manager.setSongDisplaySectionIndex(1)
        manager.setLyricSection(verse1)
        waitUntil("verse 1 is on the output") { manager.displayedLyricSection.value == verse1 }
        assertEquals(
            listOf(
                BibleBandPhase.IDLE, BibleBandPhase.ENTER, BibleBandPhase.HOLD,
                BibleBandPhase.EXIT, BibleBandPhase.IDLE,
            ),
            phases,
        )
    }

    @Test
    fun `clearing on the title slide plays it out and leaves nothing live`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(manager, settings())
        goLiveOnTitle(manager)
        manager.requestClearDisplay()
        waitUntil("the display cleared") { manager.slideContent.value == Presenting.NONE }
        assertTrue(BibleBandPhase.EXIT in phases, "the exit played: $phases")
        assertEquals(BibleBandPhase.IDLE, manager.titleSlideClock.value.phase)
        assertEquals(1f, manager.songTransitionAlpha.value, "the classic fade did not run")
    }

    @Test
    fun `without a template the title clock never moves`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(manager, settings(path = ""))
        manager.setLyricSection(title)
        manager.setPresentingMode(Presenting.LYRICS)
        manager.setSongDisplaySectionIndex(1)
        manager.setLyricSection(verse1)
        waitUntil("verse 1 is on the output") { manager.displayedLyricSection.value == verse1 }
        assertEquals(listOf(BibleBandPhase.IDLE), phases)
    }

    @Test
    fun `a section that is not a title slide never plays it`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(manager, settings())
        manager.setLyricSection(verse1)
        manager.setPresentingMode(Presenting.LYRICS)
        waitUntil("verse 1 is on the output") { manager.displayedLyricSection.value == verse1 }
        assertFalse(manager.showsTitleSlide())
        assertEquals(listOf(BibleBandPhase.IDLE), phases)
    }
}
