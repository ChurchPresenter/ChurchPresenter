package org.churchpresenter.profiles

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ProfileGroupResetsTest {

    private val band = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL

    private fun SkikoComposeUiTest.pressReset() {
        val resets = onAllNodes(hasTextExactly("Reset to defaults") and hasClickAction())
        assertEquals(1, resets.fetchSemanticsNodes().size, "only the changed group offers a reset")
        resets[0].performScrollTo().performClick()
        waitForIdle()
    }

    @Test
    fun `the Bible placement resets`() =
        profilesTab(profileDocument(bible = BibleSettings(verticalAlignment = Constants.TOP))) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            pressReset()
            assertEquals(BibleSettings().verticalAlignment, get().bible().verticalAlignment)
        }

    @Test
    fun `the Bible band height resets`() =
        profilesTab(profileDocument(mode = band, bible = BibleSettings(lowerThirdHeightPercent = 40))) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            pressReset()
            assertEquals(BibleSettings().lowerThirdHeightPercent, get().bible().lowerThirdHeightPercent)
        }

    @Test
    fun `the Bible fades reset`() = profilesTab(profileDocument(bible = BibleSettings(fadeIn = false))) { get ->
        openCustomizePane(CustomizePane.BIBLE)
        pressReset()
        assertTrue(get().bible().fadeIn)
    }

    @Test
    fun `long verses split at a word count, and the translations group resets`() =
        profilesTab(profileDocument(bible = BibleSettings(splitLongVerses = true))) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            stepUp("Split long verses across two slides")
            assertTrue(get().bible().longVerseWordCount > BibleSettings().longVerseWordCount)
            pressReset()
            assertEquals(BibleSettings().splitLongVerses, get().bible().splitLongVerses)
        }

    @Test
    fun `the song placement resets`() =
        profilesTab(profileDocument(song = SongSettings(lyricsAlignment = Constants.TOP))) { get ->
            openCustomizePane(CustomizePane.SONGS)
            pressReset()
            assertEquals(SongSettings().lyricsAlignment, get().song().lyricsAlignment)
        }

    @Test
    fun `the song band height resets`() =
        profilesTab(profileDocument(mode = band, song = SongSettings(lowerThirdHeightPercent = 40))) { get ->
            openCustomizePane(CustomizePane.SONGS)
            pressReset()
            assertEquals(SongSettings().lowerThirdHeightPercent, get().song().lowerThirdHeightPercent)
        }

    @Test
    fun `the song fades reset`() = profilesTab(profileDocument(song = SongSettings(fadeIn = false))) { get ->
        openCustomizePane(CustomizePane.SONGS)
        pressReset()
        assertTrue(get().song().fadeIn)
    }

    @Test
    fun `the lyrics can be placed freely, and auto fit per slide`() = profilesTab(profileDocument()) { get ->
        openCustomizePane(CustomizePane.SONGS)
        tap("${LYRICS_OFFSET_TAG}_enabled")
        assertNotNull(get().song().layoutExtras.lyricsOffset)
        chooseSegment("Each slide")
        assertTrue(get().song().autoFitEachSlide(false))
    }

    @Test
    fun `the title slide sits where it is put`() =
        profilesTab(profileDocument(song = SongSettings(titleSlideEnabled = true))) { get ->
            openCustomizePane(CustomizePane.SONGS)
            inRow("Title slide position", hasTextExactly("Top")).performClick()
            waitForIdle()
            assertEquals(Constants.TOP, get().song().titleSlideVerticalAlignment)
        }

    @Test
    fun `Open Background leaves the songs for the background page`() = profilesTab(profileDocument()) { _ ->
        openCustomizePane(CustomizePane.SONGS)
        onNodeWithText("Open Background").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Title slide").assertDoesNotExist()
    }
}
