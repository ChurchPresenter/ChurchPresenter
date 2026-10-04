package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.presenter.isSamePageAs
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `PresenterManager.displayedSongPosition`: the section list, section and line the outputs draw the
 * displayed section against. The operator's position moves the moment a slide is clicked and the
 * displayed section a frame or more later, so an output pairing the two drew the old section at the
 * new one's place in the song -- its look-ahead two verses on. The two must move as one.
 */
@OptIn(ExperimentalTestApi::class)
class DisplayedSongPositionTest {

    private val song = List(3) { n ->
        LyricSection(header = "[Verse ${n + 1}]", type = Constants.SECTION_TYPE_VERSE, lines = listOf("v$n a", "v$n b"))
    }

    private fun PresenterManager.push(section: Int, line: Int = 0) {
        setAllLyricSections(song)
        setSongDisplaySectionIndex(section)
        setSongDisplayLineIndex(line)
        setLyricSection(song[section])
    }

    @Test
    fun `the displayed position never points at a section other than the displayed one`() = runComposeUiTest {
        val manager = PresenterManager()
        setContent { PresenterTransitionEffects(manager, AppSettings()) }
        manager.push(0)
        waitForIdle()
        mainClock.autoAdvance = false

        manager.push(1)
        repeat(FRAMES) { frame ->
            val position = manager.displayedSongPosition.value
            val shown = manager.displayedLyricSection.value
            assertTrue(
                position.allSections.getOrNull(position.sectionIndex)?.isSamePageAs(shown) == true,
                "frame $frame: the outputs drew ${shown.header} at section ${position.sectionIndex}",
            )
            mainClock.advanceTimeByFrame()
        }
        assertEquals(1, manager.displayedSongPosition.value.sectionIndex)
    }

    @Test
    fun `a line stepped within the displayed section reaches the outputs`() = runComposeUiTest {
        val manager = PresenterManager()
        setContent { PresenterTransitionEffects(manager, AppSettings()) }
        manager.push(0)
        waitForIdle()

        manager.setSongDisplayLineIndex(1)
        waitUntil("the outputs moved to the second line") { manager.displayedSongPosition.value.lineIndex == 1 }
        assertEquals("[Verse 1]", manager.displayedLyricSection.value.header)
    }

    private companion object {
        const val FRAMES = 6
    }
}
