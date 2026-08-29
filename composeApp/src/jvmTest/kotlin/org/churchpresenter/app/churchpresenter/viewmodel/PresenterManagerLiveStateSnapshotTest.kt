package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [PresenterManager.snapshotLiveState] and [PresenterManager.restoreLiveState], which the settings
 * dialog's on-screen preview uses to borrow the outputs and give them back.
 *
 * The thing worth pinning is that the *whole* set comes back: the preview overwrites ten pieces of
 * state, and a restore that missed one would leave the operator's screen showing a sample verse's
 * section index against their own song, which is the kind of wrongness nobody notices until it is
 * live.
 */
class PresenterManagerLiveStateSnapshotTest {

    private fun verse(text: String) = SelectedVerse(bookName = "John", chapter = 3, verseNumber = 16, verseText = text)

    private fun section(line: String) = LyricSection(title = "A song", lines = listOf(line))

    /** A manager holding a full house of live song-and-Bible state. */
    private fun liveManager() = PresenterManager().apply {
        setSelectedVerses(listOf(verse("the operator's verse")))
        setDisplayedVerses(listOf(verse("the operator's verse")))
        setAllLyricSections(listOf(section("their first line"), section("their second")))
        setLyricSection(section("their first line"))
        setDisplayedLyricSection(section("their first line"))
        setSongDisplaySectionIndex(1)
        setSongDisplayLineIndex(2)
        setShowPresenterWindow(false)
        setPresentingMode(Presenting.LYRICS)
    }

    @Test
    fun `a preview that takes over the outputs gives every field back`() {
        val pm = liveManager()
        val snapshot = pm.snapshotLiveState()

        // What the on-screen preview does to it.
        pm.setShowPresenterWindow(true)
        pm.setAllLyricSections(listOf(section("Amazing grace")))
        pm.setSongDisplaySectionIndex(0)
        pm.setSongDisplayLineIndex(-1)
        pm.setLyricSection(section("Amazing grace"))
        pm.setDisplayedLyricSection(section("Amazing grace"))
        pm.setSelectedVerses(listOf(verse("the sample verse")))
        pm.setDisplayedVerses(listOf(verse("the sample verse")))
        pm.setPresentingMode(Presenting.BIBLE)

        pm.restoreLiveState(snapshot)

        assertEquals(Presenting.LYRICS, pm.presentingMode.value)
        assertEquals("the operator's verse", pm.selectedVerses.value.single().verseText)
        assertEquals("the operator's verse", pm.selectedVerse.value.verseText)
        assertEquals("the operator's verse", pm.displayedVerses.value.single().verseText)
        assertEquals(listOf("their first line", "their second"), pm.allLyricSections.value.map { it.lines.first() })
        assertEquals("their first line", pm.lyricSection.value.lines.single())
        assertEquals("their first line", pm.displayedLyricSection.value.lines.single())
        assertEquals(1, pm.songDisplaySectionIndex.value)
        assertEquals(2, pm.songDisplayLineIndex.value)
        assertEquals(false, pm.showPresenterWindow.value, "a hidden output window stays hidden")
    }

    @Test
    fun `restoring bumps the lyric version so an identical section still redraws`() {
        val pm = liveManager()
        val snapshot = pm.snapshotLiveState()
        val before = pm.lyricSectionVersion.value

        pm.setLyricSection(section("Amazing grace"))
        pm.restoreLiveState(snapshot)

        assertTrue(
            pm.lyricSectionVersion.value > before,
            "the presenter keys off the version, so a restore to the same content has to bump it",
        )
    }

    @Test
    fun `a snapshot taken from a blank manager restores it to blank`() {
        val pm = PresenterManager()
        val snapshot = pm.snapshotLiveState()

        pm.setSelectedVerses(listOf(verse("the sample verse")))
        pm.setPresentingMode(Presenting.BIBLE)
        pm.restoreLiveState(snapshot)

        assertEquals(Presenting.NONE, pm.presentingMode.value, "nothing was live, so nothing comes back live")
        assertEquals(emptyList(), pm.selectedVerses.value)
    }

    // ── The draft-settings channel ──────────────────────────────────────────────

    @Test
    fun `the preview settings override is null until a preview sets one`() {
        val pm = PresenterManager()
        assertNull(pm.previewSettingsOverride.value)

        val draft = AppSettings(songSettings = SongSettings(lyricsFontSize = 123))
        pm.setPreviewSettingsOverride(draft)
        assertEquals(123, pm.previewSettingsOverride.value?.songSettings?.lyricsFontSize)

        pm.setPreviewSettingsOverride(null)
        assertNull(pm.previewSettingsOverride.value, "and the outputs go back to the saved settings")
    }
}
