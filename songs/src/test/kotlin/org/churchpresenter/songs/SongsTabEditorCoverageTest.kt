@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTuning
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.languageLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongsTabEditorCoverageTest {

    private class EditorSpy {
        var edit: SongEditorRequest? = null
        var new: SongEditorRequest? = null
        val slot: @Composable (SongEditorRequest) -> Unit = { r -> if (r.isNewSong) new = r else edit = r }
    }

    private fun ComposeUiTest.clickRow(title: String) {
        onAllNodes(hasText(title))[0].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.clickAction(label: String) {
        onAllNodes(hasContentDescription(label))[0].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.openEditorOn(title: String) {
        clickRow(title)
        clickAction("Edit Song")
    }

    @Test
    fun `the editor is closed until it is asked for`() {
        val spy = EditorSpy()
        songsTab(songEditor = spy.slot) { _, _ ->
            assertFalse(assertNotNull(spy.edit).isVisible)
            assertFalse(assertNotNull(spy.new).isVisible)
        }
    }

    @Test
    fun `Edit Song opens the editor on the selected song`() {
        val spy = EditorSpy()
        songsTab(songEditor = spy.slot) { _, _ ->
            openEditorOn("Amazing Grace")

            val request = assertNotNull(spy.edit)
            assertTrue(request.isVisible)
            assertFalse(request.isNewSong)
            assertEquals("Amazing Grace", request.song?.title)
            assertEquals(listOf("Chorus Book", "Hymnal"), request.songbooks)
            assertEquals(4, request.existingSongs.size)
        }
    }

    @Test
    fun `the editor carries the song's tuning`() {
        val spy = EditorSpy()
        songsTab(songEditor = spy.slot, songBpm = mapOf("Hymnal::1" to 96)) { _, _ ->
            openEditorOn("Amazing Grace")

            assertEquals(96, spy.edit?.tuning?.bpm)
        }
    }

    @Test
    fun `the editor shows chords as the setting says`() {
        val spy = EditorSpy()
        songsTab(songEditor = spy.slot, songSettings = SongSettings(editorShowChords = false)) { _, _ ->
            openEditorOn("Amazing Grace")

            assertEquals(false, spy.edit?.chordsVisible)
        }
    }

    @Test
    fun `the editor is told how long the song usually runs`() {
        val spy = EditorSpy()
        songsTab(
            songEditor = spy.slot,
            typicalSongSeconds = { song -> if (song.title == "Amazing Grace") 222 else null },
        ) { _, _ ->
            openEditorOn("Amazing Grace")

            assertEquals(222, spy.edit?.typicalSeconds)
        }
    }

    @Test
    fun `the editor is handed the install's language names`() {
        val spy = EditorSpy()
        songsTab(
            songEditor = spy.slot,
            songSettings = SongSettings(languageNames = listOf("English", "Español")),
        ) { _, _ ->
            openEditorOn("Amazing Grace")

            val names = assertNotNull(spy.edit).languageNames
            assertEquals("English", names[0])
            assertEquals("Español", names[1])
        }
    }

    @Test
    fun `dismissing the editor closes it`() {
        val spy = EditorSpy()
        songsTab(songEditor = spy.slot) { _, _ ->
            openEditorOn("Amazing Grace")
            runOnIdle { spy.edit!!.onDismiss() }
            waitForIdle()

            assertFalse(assertNotNull(spy.edit).isVisible)
        }
    }

    @Test
    fun `saving an edit rewrites the song, stores its tuning and closes the editor`() {
        val spy = EditorSpy()
        songsTab(songEditor = spy.slot) { vm, reports ->
            openEditorOn("Amazing Grace")
            val request = assertNotNull(spy.edit)
            val old = assertNotNull(request.song)
            runOnIdle { request.onSave(old.copy(lyrics = listOf("[Verse 1]", "rewritten line")), SongTuning(bpm = 80)) }
            waitForIdle()

            val saved = vm.filteredSongItems.value.single { it.title == "Amazing Grace" }
            assertTrue("rewritten line" in saved.lyrics, saved.lyrics.toString())
            assertEquals(80, reports.settingsAfterChange?.tuningFor(old.songId)?.bpm)
            assertFalse(assertNotNull(spy.edit).isVisible)
        }
    }

    @Test
    fun `saving an edit of a song that is not live pushes nothing`() {
        val spy = EditorSpy()
        songsTab(songEditor = spy.slot) { _, reports ->
            openEditorOn("Amazing Grace")
            val request = assertNotNull(spy.edit)
            runOnIdle { request.onSave(request.song!!.copy(lyrics = listOf("[Verse 1]", "quiet edit")), SongTuning()) }
            waitForIdle()

            assertNull(reports.selectedSection)
        }
    }

    @Test
    fun `saving an edit of the live song sends the new words out`() {
        val spy = EditorSpy()
        songsTab(songEditor = spy.slot, isPresenting = true) { _, reports ->
            clickRow("Amazing Grace")
            clickAction("Go Live")
            clickAction("Edit Song")
            val request = assertNotNull(spy.edit)
            runOnIdle {
                request.onSave(request.song!!.copy(lyrics = listOf("[Verse 1]", "new words")), SongTuning(bpm = 70))
            }
            waitForIdle()

            val sent = assertNotNull(reports.selectedSection)
            assertEquals(listOf("new words"), sent.lines)
            assertEquals(70, sent.bpm)
            assertEquals(0, reports.sectionIndex)
        }
    }

    @Test
    fun `turning chords off in the editor writes the setting`() {
        val spy = EditorSpy()
        songsTab(songEditor = spy.slot) { _, reports ->
            openEditorOn("Amazing Grace")
            runOnIdle { spy.edit!!.onChordsVisibleChange(false) }

            assertEquals(false, reports.settingsAfterChange?.songSettings?.editorShowChords)
        }
    }

    @Test
    fun `renaming the languages in the editor writes their names`() {
        val spy = EditorSpy()
        songsTab(songEditor = spy.slot) { _, reports ->
            openEditorOn("Amazing Grace")
            runOnIdle { spy.edit!!.onLanguageNamesChange(listOf("English", "Deutsch")) }

            val songs = assertNotNull(reports.settingsAfterChange).songSettings
            assertEquals("English", songs.languageLabel(0))
            assertEquals("Deutsch", songs.languageLabel(1))
        }
    }

    @Test
    fun `applying a background to the songbook reaches every song in it`() {
        val spy = EditorSpy()
        val band = SongBackground(type = SongBackgroundType.COLOR, color = "#2a1130", dim = 65)
        songsTab(songEditor = spy.slot) { vm, _ ->
            openEditorOn("Amazing Grace")
            runOnIdle { spy.edit!!.onApplyBackgroundToSongbook!!("Hymnal", band, band) }
            waitForIdle()

            val hymnal = vm.filteredSongItems.value.filter { it.songbook == "Hymnal" }
            assertEquals(3, hymnal.size)
            assertTrue(hymnal.all { it.background == band }, hymnal.map { it.background }.toString())
            assertFalse(vm.filteredSongItems.value.single { it.songbook == "Chorus Book" }.background.isCustom)
        }
    }

    @Test
    fun `New Song opens the editor on an empty template`() {
        val spy = EditorSpy()
        songsTab(songEditor = spy.slot) { _, _ ->
            clickAction("New Song")

            val request = assertNotNull(spy.new)
            assertTrue(request.isVisible)
            assertTrue(request.isNewSong)
            assertEquals("", request.song?.title)
            assertEquals("[Verse 1]", request.song?.lyrics?.first())
            assertEquals(4, request.existingSongs.size)
        }
    }

    @Test
    fun `saving a new song adds it to the library and closes the editor`() {
        val spy = EditorSpy()
        songsTab(songEditor = spy.slot) { vm, reports ->
            clickAction("New Song")
            val request = assertNotNull(spy.new)
            runOnIdle {
                request.onSave(
                    SongItem(
                        number = "50", title = "Fresh Song", songbook = "Hymnal",
                        lyrics = listOf("[Verse 1]", "fresh"),
                    ),
                    SongTuning(),
                )
            }
            waitForIdle()

            assertTrue(vm.filteredSongItems.value.any { it.title == "Fresh Song" })
            assertNull(reports.settingsAfterChange, "an untouched tuning stores nothing")
            assertFalse(assertNotNull(spy.new).isVisible)
        }
    }

    @Test
    fun `a new song saved with a tempo stores it`() {
        val spy = EditorSpy()
        songsTab(songEditor = spy.slot) { _, reports ->
            clickAction("New Song")
            val song = SongItem(
                number = "51", title = "Tempo Song", songbook = "Hymnal", lyrics = listOf("[Verse 1]", "x"),
            )
            runOnIdle { spy.new!!.onSave(song, SongTuning(bpm = 120)) }
            waitForIdle()

            assertEquals(120, reports.settingsAfterChange?.tuningFor(song.songId)?.bpm)
        }
    }

    @Test
    fun `a new song without a songbook is refused and the editor stays open`() {
        val spy = EditorSpy()
        songsTab(songEditor = spy.slot) { vm, _ ->
            clickAction("New Song")
            runOnIdle { spy.new!!.onSave(SongItem(number = "52", title = "Homeless", songbook = " "), SongTuning()) }
            waitForIdle()

            assertFalse(vm.filteredSongItems.value.any { it.title == "Homeless" })
            assertTrue(assertNotNull(spy.new).isVisible)
        }
    }

    @Test
    fun `dismissing the new-song editor closes it`() {
        val spy = EditorSpy()
        songsTab(songEditor = spy.slot) { _, _ ->
            clickAction("New Song")
            runOnIdle { spy.new!!.onDismiss() }
            waitForIdle()

            assertFalse(assertNotNull(spy.new).isVisible)
        }
    }

    @Test
    fun `the editor being open hides the way back to the live song`() {
        val spy = EditorSpy()
        songsTab(songEditor = spy.slot, isPresenting = true) { _, _ ->
            clickRow("Amazing Grace")
            clickAction("Go Live")
            search("Be Thou")
            assertTrue(onAllNodes(hasText("Back to Live")).fetchSemanticsNodes().isNotEmpty())

            clickAction("Edit Song")

            assertTrue(
                onAllNodes(hasText("Back to Live")).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty(),
            )
        }
    }
}
