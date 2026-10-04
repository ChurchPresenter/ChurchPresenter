package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.presenter.SongStyleElement
import org.churchpresenter.presenter.songShiftKey
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "Applies to": a value set with one translation or language picked is that one's own, marked with
 * an "Only … ×" chip; All reaches every other one; and the chip hands the value back.
 */
@OptIn(ExperimentalTestApi::class)
class ProfileTargetingTest {

    private val stack = BibleSettings(
        translations = listOf(
            BibleTranslationSettings(fileName = "kjv.spb", customAbbreviation = "KJV", textFontSize = 70),
            BibleTranslationSettings(fileName = "rst.spb", customAbbreviation = "RST", textFontSize = 70),
        ),
    )

    private fun sizes(get: () -> org.churchpresenter.settings.AppSettings) =
        get().bible().translationList().map { it.textFontSize }

    @Test
    fun `KJV at its own size keeps it through All, and its chip gives it back`() =
        profilesTab(profileDocument(bible = stack)) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            onNodeWithTag(translationChipTag(0)).performScrollTo().performClick()
            waitForIdle()
            typeInRow("Size", 50)
            assertEquals(listOf(50, 70), sizes(get))
            onNodeWithText("Only KJV").assertExists()
            onNodeWithTag(translationChipTag(ALL_TRANSLATIONS)).performScrollTo().performClick()
            waitForIdle()
            typeInRow("Size", 80)
            assertEquals(listOf(50, 80), sizes(get))
            onNodeWithTag(translationChipTag(0)).performScrollTo().performClick()
            waitForIdle()
            tap(TARGET_CHIP_TAG)
            assertEquals(listOf(80, 80), sizes(get))
            assertEquals(0, countTag(TARGET_CHIP_TAG))
        }

    @Test
    fun `a picked translation moves on its own`() = profilesTab(profileDocument(bible = stack)) { get ->
        openCustomizePane(CustomizePane.BIBLE)
        assertEquals(0, countTag(SHIFT_X_TAG), "under All there is nothing to move alone")
        onNodeWithTag(translationChipTag(1)).performScrollTo().performClick()
        waitForIdle()
        typeInRow("Move X / Y", 40, nth = 0)
        typeInRow("Move X / Y", -20, nth = 1)
        val rst = get().bible().translationList()[1]
        assertEquals(40, rst.shiftX)
        assertEquals(-20, rst.shiftY)
        assertEquals(0, get().bible().translationList()[0].shiftX)
    }

    @Test
    fun `on a lower third the move is the band's own`() =
        profilesTab(profileDocument(mode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL, bible = stack)) { get ->
            openCustomizePane(CustomizePane.BIBLE)
            onNodeWithTag(translationChipTag(1)).performScrollTo().performClick()
            waitForIdle()
            typeInRow("Move X / Y", 12, nth = 0)
            typeInRow("Move X / Y", 8, nth = 1)
            val rst = get().bible().translationList()[1]
            assertEquals(12, rst.lowerThirdShiftX)
            assertEquals(8, rst.lowerThirdShiftY)
            assertEquals(0, rst.shiftX)
        }

    @Test
    fun `a song language at its own size keeps it through All, and its chip gives it back`() =
        profilesTab(profileDocument()) { get ->
            openCustomizePane(CustomizePane.SONGS)
            tap(songLanguageTag(SongStyleLanguage.SECONDARY))
            typeInRow("Size", 50)
            assertEquals(50, get().song().translations[0].lyrics.fontSize)
            assertTrue(onAllNodes(androidx.compose.ui.test.hasText("Only ",
                    substring = true)).fetchSemanticsNodes().isNotEmpty())
            tap(SONG_ALL_LANGUAGES_TAG)
            typeInRow("Size", 80)
            assertEquals(80, get().song().lyricsFontSize)
            assertEquals(50, get().song().translations[0].lyrics.fontSize)
            tap(songLanguageTag(SongStyleLanguage.SECONDARY))
            tap(TARGET_CHIP_TAG)
            assertEquals(80, get().song().translations[0].lyrics.fontSize)
        }

    @Test
    fun `a picked song language moves on its own`() = profilesTab(profileDocument()) { get ->
        openCustomizePane(CustomizePane.SONGS)
        tap(songLanguageTag(SongStyleLanguage.SECONDARY))
        typeInRow("Move X / Y", 30, nth = 0)
        typeInRow("Move X / Y", 10, nth = 1)
        val key = songShiftKey(SongStyleElement.LYRICS, lowerThird = false, language = 1)
        assertEquals(30 to 10, get().song().shiftAt(key))
    }

    @Test
    fun `an element drawn the same in every language offers no language to pick`() =
        profilesTab(profileDocument()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_NUMBER)
            assertEquals(0, countTag(songLanguageTag(SongStyleLanguage.SECONDARY)))
        }
}
