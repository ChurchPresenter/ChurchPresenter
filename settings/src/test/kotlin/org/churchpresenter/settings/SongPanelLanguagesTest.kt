package org.churchpresenter.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/** Which of a song's languages the Songs tab's lyrics panel shows, and how the eye changes that. */
class SongPanelLanguagesTest {

    @Test
    fun `with nothing hidden every language shows, in slot order`() =
        assertEquals(listOf(0, 1, 2), AppSettings().panelLanguageSlots(3))

    @Test
    fun `a hidden language is left out`() =
        assertEquals(listOf(0, 2), AppSettings(songPanelHiddenLanguages = listOf(1)).panelLanguageSlots(3))

    @Test
    fun `a hidden slot the song does not have changes nothing`() =
        assertEquals(listOf(0, 1), AppSettings(songPanelHiddenLanguages = listOf(3)).panelLanguageSlots(2))

    @Test
    fun `with every language hidden the first still shows`() =
        assertEquals(listOf(0), AppSettings(songPanelHiddenLanguages = listOf(0, 1)).panelLanguageSlots(2))

    @Test
    fun `a song with no count is read as one language`() =
        assertEquals(listOf(0), AppSettings().panelLanguageSlots(0))

    @Test
    fun `hiding a language adds it once, kept sorted`() {
        val hidden = AppSettings(songPanelHiddenLanguages = listOf(2))
            .withPanelLanguageShown(0, shown = false)
            .withPanelLanguageShown(2, shown = false)
        assertEquals(listOf(0, 2), hidden.songPanelHiddenLanguages)
    }

    @Test
    fun `showing a language takes it out`() =
        assertEquals(
            listOf(2),
            AppSettings(songPanelHiddenLanguages = listOf(1, 2)).withPanelLanguageShown(1, shown = true)
                .songPanelHiddenLanguages,
        )
}
