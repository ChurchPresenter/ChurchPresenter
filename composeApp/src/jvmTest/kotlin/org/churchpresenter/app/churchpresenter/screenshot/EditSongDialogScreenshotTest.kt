@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.app.churchpresenter.dialogs.EditSongContent
import org.churchpresenter.core.models.songs.SongTuning
import org.churchpresenter.theme.ChurchPresenterTheme
import kotlin.test.Test
import org.churchpresenter.ui.screenshot.captureTo
import org.churchpresenter.ui.screenshot.stackedThemes

/**
 * The song editor, in both themes.
 *
 * Shot through `EditSongContent` — the body `EditSongDialog`'s `DialogWindow` wraps, extracted so it
 * can be composed headless — and boxed at the window's own 1120x760, so a reviewer sees the editor
 * at the size it really opens at.
 *
 * The editor's own `theme` parameter is passed to match the palette being captured. It is what the
 * chord preview colours its section labels from, so a light shot rendered with the dark value would
 * put dark-theme inks on a light page.
 */
class EditSongDialogScreenshotTest {

    private fun shoot(
        name: String,
        song: SongItem = amazingGrace(),
        existingSongs: List<SongItem> = emptyList(),
        isNewSong: Boolean = false,
        songbooks: List<String> = listOf("Hymnal", "Chorus Book", "Christmas"),
        tuning: SongTuning = SongTuning(),
        showTuningFields: Boolean = false,
        chordsVisible: Boolean = true,
        rootIndex: Int = 0,
        drive: ComposeUiTest.() -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        runComposeUiTest {
            setContent {
                ChurchPresenterTheme(themeMode = mode) {
                    Box(Modifier.size(1120.dp, 760.dp)) {
                        EditSongContent(
                            song = song,
                            songbooks = songbooks,
                            existingSongs = existingSongs,
                            isNewSong = isNewSong,
                            theme = mode,
                            tuning = tuning,
                            showTuningFields = showTuningFields,
                            chordsVisible = chordsVisible,
                            onChordsVisibleChange = {},
                            onDismiss = {},
                            onSave = { _, _ -> },
                        )
                    }
                }
            }
            drive()
            waitForIdle()
            captureTo(file, rootIndex)
        }
    }

    // ── Opening it ──────────────────────────────────────────────────────────────────────────────

    /** The shape most songs in a library really have: a title, a book, a number and the words. */
    @Test
    fun `editing a song with only the essentials filled in`() = shoot("editing", song = sparse())

    @Test
    fun `a brand new song`() = shoot("new_song", song = blankSong(), isNewSong = true)

    /** Every field a song can carry — author, composer, CCLI number, tune. */
    @Test
    fun `a song filled in as completely as it can be`() = shoot("fully_filled")

    /** Chords in the lyrics, previewed beside them and coloured by section. */
    @Test
    fun `a song with chords`() = shoot("with_chords", song = withChords())

    @Test
    fun `the chord column turned off`() = shoot("chords_hidden", song = withChords(), chordsVisible = false)

    // ── The second language ─────────────────────────────────────────────────────────────────────

    @Test
    fun `the secondary pane`() = shoot("secondary_pane", song = bilingual()) {
        onNodeWithText(SECONDARY_PANE).performClick()
        waitForIdle()
    }

    @Test
    fun `a bilingual song on its primary pane`() = shoot("bilingual", song = bilingual())

    // ── Things that stop a save ─────────────────────────────────────────────────────────────────

    /** Another song already carries this number in this book, so Save is shut and says why. */
    @Test
    fun `a duplicate number`() = shoot(
        "duplicate_number",
        song = amazingGrace().copy(sourceFile = ""),
        isNewSong = true,
        existingSongs = listOf(amazingGrace()),
    )

    // Not shot: an empty editor with Save shut. That is exactly what `new_song` above is — a brand
    // new song opens with nothing in it — and the two render byte for byte the same.

    // ── Tuning, shown only when a stage monitor wants it ────────────────────────────────────────

    @Test
    fun `the capo and tempo fields`() = shoot(
        "tuning_fields",
        song = withChords(),
        tuning = SongTuning(bpm = 72, capo = 2),
        showTuningFields = true,
    )

    // ── Menus ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the songbook picker`() = shoot("songbook_menu", rootIndex = 1) {
        onNodeWithText("Hymnal").performClick()
        waitForIdle()
    }

    /** "Add New…" turns the read-only songbook into a field to type a new book's name into. */
    @Test
    fun `naming a new songbook`() = shoot("songbook_new") {
        onNodeWithText("Hymnal").performClick()
        waitForIdle()
        onAllNodesWithText(ADD_NEW)[0].performClick()
        waitForIdle()
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────

    private fun amazingGrace() = SongItem(
        number = "42",
        title = "Amazing Grace",
        songbook = "Hymnal",
        tune = "New Britain",
        author = "John Newton",
        composer = "Traditional",
        ccliNumber = "22025",
        lyrics = listOf(
            "[Verse 1]",
            "Amazing grace how sweet the sound",
            "That saved a wretch like me",
            "I once was lost but now am found",
            "Was blind but now I see",
            "",
            "{Chorus}",
            "Praise the Lord, praise the Lord",
            "Let the earth hear His voice",
        ),
        sourceFile = "/songs/hymnal/42.sps",
    )

    private fun withChords() = amazingGrace().copy(
        lyrics = listOf(
            "[Verse 1]",
            "A[G]mazing grace how [G7]sweet the [C]sound",
            "That [G]saved a wretch like [D]me",
            "",
            "{Chorus}",
            "[C]Praise the Lord, [G]praise the Lord",
            "",
            "(Bridge)",
            "Let the [Em]earth hear His [D]voice",
        ),
    )

    private fun bilingual() = amazingGrace().copy(
        secondaryTitle = "Sublime Gracia",
        secondaryLyrics = listOf(
            "[Verse 1]",
            "Sublime gracia del Señor",
            "Que a un pecador salvó",
            "",
            "{Chorus}",
            "Alabad al Señor, alabad al Señor",
        ),
    )

    private fun sparse() = amazingGrace().copy(tune = "", author = "", composer = "", ccliNumber = "")

    private fun blankSong() =
        SongItem(number = "", title = "", songbook = "", lyrics = emptyList(), sourceFile = "")

    private companion object {
        const val SECTION = "editSongDialog"

        const val SECONDARY_PANE = "Secondary"
        const val ADD_NEW = "Add New..."
    }
}
