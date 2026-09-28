@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The song editor's chord tools, driven through `EditSongContent` and read back from what Save
 * hands over — the song is what has to be right, not the preview.
 *
 * Issue #648 is the first test: the palette used to offer chords in the *previewed* key and write
 * them into the unshifted text, so a D picked while "in D" was stored as whatever D sat below the
 * preview's offset — and the key was then guessed afresh from the first chord, moving again.
 */
class EditSongChordsTest {

    private object Label {
        const val SAVE = "Save"
        const val KEY_UP = "Show the chords of the key above — the song is not changed"
        const val TRANSPOSE_UP = "Transpose up"
        const val TRANSPOSE_DOWN = "Transpose down"
        const val SECOND_LANGUAGE = "Language 2"
        const val BUILD_A_CHORD = "BUILD A CHORD"
    }

    /** The lyrics box among the editor's typed fields; `EditSongContentTest` pins the order. */
    private val LYRICS_FIELD = 7

    private fun song(lyrics: List<String>, secondaryLyrics: List<String> = emptyList()) = SongItem(
        number = "1",
        title = "Test",
        songbook = "Book",
        lyrics = lyrics,
        secondaryLyrics = secondaryLyrics,
        sourceFile = "/songs/book/1.sps",
    )

    private fun editor(song: SongItem, block: ComposeUiTest.(() -> SongItem?) -> Unit) {
        var saved: SongItem? = null
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    EditSongContent(
                        song = song,
                        songbooks = listOf("Book"),
                        existingSongs = emptyList(),
                        isNewSong = false,
                        theme = ThemeMode.LIGHT,
                        onDismiss = {},
                        onSave = { s, _ -> saved = s },
                    )
                }
            }
            block { saved }
        }
    }

    private fun ComposeUiTest.tap(text: String) {
        onNodeWithText(text).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.press(description: String) {
        onNodeWithContentDescription(description).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.showsExactly(text: String): Boolean =
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    // ── Issue #648 ──────────────────────────────────────────────────────────────

    @Test
    fun `a chord picked from another key's palette is stored as picked, and the key stays put`() =
        editor(song(listOf("[Verse 1]", "Amazing grace"))) { saved ->
            // No chords yet, so the palette opens in C; two steps up is D.
            press(Label.KEY_UP)
            press(Label.KEY_UP)
            assertTrue(showsExactly("C#dim"), "the palette should now offer the chords of D")

            tap("Em")
            // Still D: the inserted chord must not be read back as a new key.
            assertTrue(showsExactly("C#dim"), "inserting a chord must not move the palette's key")

            tap(Label.SAVE)
            assertTrue(saved()!!.lyrics.joinToString("\n").contains("[Em]"), "stored ${saved()!!.lyrics}")
        }

    @Test
    fun `stepping the palette's key leaves the song's chords as written`() =
        editor(song(listOf("[Verse 1]", "[G]Amazing [C]grace"))) { saved ->
            press(Label.KEY_UP)
            tap(Label.SAVE)
            assertEquals(listOf("[Verse 1]", "[G]Amazing [C]grace"), saved()!!.lyrics)
        }

    // ── Transpose ───────────────────────────────────────────────────────────────

    @Test
    fun `transposing rewrites every chord in every language, and Save keeps it`() =
        editor(song(listOf("[Verse 1]", "[G]Amazing [C]grace"))) { saved ->
            // A second language, typed into its own pane as an operator would.
            tap(Label.SECOND_LANGUAGE)
            onAllNodes(hasSetTextAction())[LYRICS_FIELD].performTextReplacement("[Verse 1]\n[G]Blagodat")
            waitForIdle()

            press(Label.TRANSPOSE_UP)
            press(Label.TRANSPOSE_UP)
            tap(Label.SAVE)
            assertEquals(listOf("[Verse 1]", "[A]Amazing [D]grace"), saved()!!.lyrics)
            assertEquals(listOf("[Verse 1]", "[A]Blagodat"), saved()!!.secondaryLyrics)
        }

    @Test
    fun `the palette follows the song when it is transposed`() =
        editor(song(listOf("[G]one"))) { _ ->
            assertTrue(showsExactly("F#dim"))
            press(Label.TRANSPOSE_UP)
            press(Label.TRANSPOSE_UP)
            assertTrue(showsExactly("G#dim"), "a song moved from G to A offers the chords of A")
        }

    @Test
    fun `reset puts the chords back in the key they were written in`() =
        editor(song(listOf("[G]Amazing [Em]grace"))) { saved ->
            press(Label.TRANSPOSE_DOWN)
            press(Label.TRANSPOSE_DOWN)
            press(Label.TRANSPOSE_DOWN)
            tap("-3 — reset")
            tap(Label.SAVE)
            assertEquals(listOf("[G]Amazing [Em]grace"), saved()!!.lyrics)
        }

    @Test
    fun `a second language whose lines open on chords is kept, not taken for headers`() =
        editor(song(listOf("[Verse 1]", "Amazing grace"))) { saved ->
            tap(Label.SECOND_LANGUAGE)
            onAllNodes(hasSetTextAction())[LYRICS_FIELD].performTextReplacement("[Verse 1]\n[G]Blagodat")
            waitForIdle()
            tap(Label.SAVE)
            assertEquals(listOf("[Verse 1]", "[G]Blagodat"), saved()!!.secondaryLyrics)
        }

    // ── The chord picker ────────────────────────────────────────────────────────

    @Test
    fun `the picker is folded away until asked for`() =
        editor(song(listOf("[G]one"))) { _ ->
            assertTrue(!showsExactly("Insert G"), "the picker's chips should not take the song's room")
            tap(Label.BUILD_A_CHORD)
            assertTrue(showsExactly("Insert G"))
            tap(Label.BUILD_A_CHORD)
            assertTrue(!showsExactly("Insert G"))
        }

    @Test
    fun `the picker inserts the root and type chosen`() =
        editor(song(listOf("[G]one"))) { saved ->
            tap(Label.BUILD_A_CHORD)
            tap("F#")
            tap("m7")
            tap("Insert F#m7")
            tap(Label.SAVE)
            assertTrue(saved()!!.lyrics.joinToString("\n").contains("[F#m7]"), "stored ${saved()!!.lyrics}")
        }

    @Test
    fun `the picker starts on the song's key and a major chord`() =
        editor(song(listOf("[Bb]one"))) { _ ->
            tap(Label.BUILD_A_CHORD)
            assertTrue(showsExactly("Insert Bb"))
            tap("m7♭5")
            assertTrue(showsExactly("Insert Bbm7b5"))
        }

    // ── The cursor through a rewrite ────────────────────────────────────────────

    @Test
    fun `transposing keeps the cursor in its place among the words`() {
        val before = TextFieldValue("[C]one [C]two", TextRange(10))
        val after = transposeValue(before, 1, flats = false)
        assertEquals("[C#]one [C#]two", after.text)
        // The cursor sat just after the second chord; both chords grew by one character.
        assertEquals(TextRange(12), after.selection)
    }

    @Test
    fun `transposing by nothing hands the value back untouched`() {
        val before = TextFieldValue("[C]one", TextRange(2))
        assertEquals(before, transposeValue(before, 0, flats = false))
    }
}
