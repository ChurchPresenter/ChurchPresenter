@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import core.models.songs.SongItem
import org.churchpresenter.app.churchpresenter.models.SongTuning
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The song editor's rules: what it puts in the fields when it opens, when it will let a song be
 * saved, and what it hands back when it is.
 *
 * A song saved wrong is wrong in the library from then on, so the assertions here are mostly about
 * the `SongItem` that comes out of Save rather than about the fields looking right. Three things the
 * editor *derives* rather than passes through get a test each: the digits-only song number, the
 * title auto-filled from the lyrics of a brand-new song, and the secondary lyrics discarded when
 * they are nothing but section headers.
 *
 * `EditSongDialog` opens a `DialogWindow`, which cannot be composed headless, so the window's body
 * was lifted into `EditSongContent` — an extraction, no logic moved or changed — and that is what
 * these drive. The `Window` call and its sizing are what remain uncovered.
 *
 * Fields carry their captions as separate nodes rather than in their own semantics, so they are
 * addressed by ordinal. `the editor lays its fields out in a known order` pins those ordinals, so a
 * reordering fails loudly there instead of quietly changing what every other test types into.
 */
class EditSongContentTest {

    /**
     * Ordinals among the editor's text inputs, in composition order.
     *
     * The songbook is absent on purpose: it is a read-only dropdown anchor rather than a typed field,
     * so it publishes no set-text action and is reached through [songbook] instead.
     */
    private object Field {
        const val TITLE = 0
        const val SECONDARY_TITLE = 1
        const val NUMBER = 2
        const val AUTHOR = 3
        const val COMPOSER = 4
        const val CCLI = 5
        const val TUNE = 6
        const val LYRICS = 7
        const val COUNT = 8

        /** Where the songbook lands once Add New turns it into a typed field. */
        const val SONGBOOK_TYPED = 2

        // With a stage monitor configured, capo and tempo are inserted after Tune and before the
        // lyrics box, which moves down to 9. Verified against the rendered tree, not assumed — the
        // constant that used to live here put TEMPO at 7, which is where capo actually is.
        const val CAPO = 7
        const val TEMPO = 8
        const val COUNT_WITH_TUNING = 10
    }

    private object Label {
        const val SAVE = "Save"
        const val SECONDARY_PANE = "Secondary"
        const val CANCEL = "Cancel"
        const val ADD_NEW = "Add New..."

        // Card captions go through CardLabel, which uppercases them — so these must be matched in
        // upper case. Asserting on "Tempo" finds nothing whether the field is present or not, which
        // makes an absence check pass for the wrong reason.
        const val TEMPO_CAPTION = "TEMPO"
        const val CAPO_CAPTION = "CAPO"
    }

    /**
     * The songbook control: the one field carrying a value that refuses typed text, because it is a
     * dropdown anchor. Matching on that pair reaches it without depending on where it sits.
     */
    private val readOnlyField = SemanticsMatcher("a field with a value but no set-text action") {
        it.config.getOrNull(SemanticsProperties.EditableText) != null &&
            it.config.getOrNull(SemanticsActions.SetText) == null
    }

    private class Saved {
        var song: SongItem? = null
        var tuning: SongTuning? = null
        var dismissed = 0
    }

    private fun aSong(
        number: String = "42",
        title: String = "Amazing Grace",
        songbook: String = "Hymnal",
        lyrics: List<String> = listOf("[Verse 1]", "Amazing grace how sweet the sound"),
        secondaryLyrics: List<String> = emptyList(),
        sourceFile: String = "/songs/hymnal/42.sps",
    ) = SongItem(
        number = number,
        title = title,
        songbook = songbook,
        lyrics = lyrics,
        secondaryLyrics = secondaryLyrics,
        sourceFile = sourceFile,
    )

    /** A song with nothing filled in, as the New Song command hands one over. */
    private fun blankSong() = aSong(number = "", title = "", songbook = "", lyrics = emptyList(), sourceFile = "")

    @OptIn(ExperimentalTestApi::class)
    private fun editor(
        song: SongItem = aSong(),
        existingSongs: List<SongItem> = emptyList(),
        isNewSong: Boolean = false,
        songbooks: List<String> = emptyList(),
        tuning: SongTuning = SongTuning(),
        showTuningFields: Boolean = false,
        block: ComposeUiTest.(Saved) -> Unit,
    ) {
        val saved = Saved()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    EditSongContent(
                        song = song,
                        songbooks = songbooks,
                        existingSongs = existingSongs,
                        isNewSong = isNewSong,
                        theme = ThemeMode.LIGHT,
                        tuning = tuning,
                        showTuningFields = showTuningFields,
                        onDismiss = { saved.dismissed++ },
                        onSave = { song, savedTuning -> saved.song = song; saved.tuning = savedTuning },
                    )
                }
            }
            block(saved)
        }
    }

    private fun ComposeUiTest.field(ordinal: Int): SemanticsNodeInteraction =
        onAllNodes(hasSetTextAction())[ordinal]

    private fun ComposeUiTest.fieldCount(): Int =
        onAllNodes(hasSetTextAction()).fetchSemanticsNodes(atLeastOneRootRequired = false).size

    private fun ComposeUiTest.songbook(): SemanticsNodeInteraction = onNode(readOnlyField)

    private fun ComposeUiTest.titleText(): String =
        field(Field.TITLE).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text ?: ""

    private fun ComposeUiTest.type(ordinal: Int, text: String) {
        field(ordinal).performTextReplacement(text)
        waitForIdle()
    }

    private fun ComposeUiTest.tap(text: String) {
        onNodeWithText(text).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.save() = tap(Label.SAVE)

    /**
     * Moves the editor onto its second-language pane. The two sets of lyrics share one box now, so
     * reaching the secondary ones means switching to them first.
     */
    private fun ComposeUiTest.secondaryPane() = tap(Label.SECONDARY_PANE)

    // ── Structure ───────────────────────────────────────────────────────────────

    @Test
    fun `the editor lays its fields out in a known order`() = editor { _ ->
        assertEquals(
            Field.COUNT,
            fieldCount(),
            "the editor offers eight typed fields; the other tests address them by position",
        )
        field(Field.NUMBER).assertTextContains("42")
        field(Field.TITLE).assertTextContains("Amazing Grace")
        songbook().assertTextContains("Hymnal")
    }

    // ── What it opens with ──────────────────────────────────────────────────────

    @Test
    fun `a song number carrying punctuation is reduced to its digits`() =
        editor(aSong(number = "3.1")) { saved ->
            // The library keys on the number, so "3.1" has to become a number, not stay a label.
            field(Field.NUMBER).assertTextContains("31")
            save()
            assertEquals("31", saved.song?.number, "every non-digit is dropped, the digits kept in order")
        }

    @Test
    fun `typing an all-digit replacement into the number field is accepted`() = editor { saved ->
        type(Field.NUMBER, "123")
        save()
        assertEquals("123", saved.song?.number)
    }

    @Test
    fun `typing a letter into the number field is rejected outright`() = editor { saved ->
        type(Field.NUMBER, "12a")
        save()
        assertEquals(
            "42",
            saved.song?.number,
            "a keystroke that would introduce a non-digit must leave the field untouched",
        )
    }

    // ── Saving ──────────────────────────────────────────────────────────────────

    @Test
    fun `saving hands back every edited field`() = editor { saved ->
        type(Field.TITLE, "Be Thou My Vision")
        type(Field.AUTHOR, "Dallan Forgaill")
        type(Field.COMPOSER, "Traditional Irish")
        type(Field.CCLI, "30639")
        type(Field.TUNE, "SLANE")
        type(Field.SECONDARY_TITLE, "Sei Du mein Ziel")
        save()

        val song = saved.song
        assertEquals("Be Thou My Vision", song?.title)
        assertEquals("Dallan Forgaill", song?.author)
        assertEquals("Traditional Irish", song?.composer)
        assertEquals("30639", song?.ccliNumber)
        assertEquals("SLANE", song?.tune)
        assertEquals("Sei Du mein Ziel", song?.secondaryTitle)
    }

    @Test
    fun `lyrics are saved as one entry per line`() = editor { saved ->
        type(Field.LYRICS, "[Verse 1]\nAmazing grace\nhow sweet the sound")
        save()
        assertEquals(
            listOf("[Verse 1]", "Amazing grace", "how sweet the sound"),
            saved.song?.lyrics,
            "each line becomes its own entry, section headers included",
        )
    }

    @Test
    fun `a curly-brace section header is saved just like a square-bracket one`() = editor { saved ->
        type(Field.LYRICS, "{Chorus}\nAmazing grace")
        save()
        assertEquals(listOf("{Chorus}", "Amazing grace"), saved.song?.lyrics)
    }

    @Test
    fun `the song's file on disk is carried through untouched`() =
        editor(aSong(sourceFile = "/songs/hymnal/42.sps")) { saved ->
            type(Field.TITLE, "Renamed Entirely")
            save()
            assertEquals(
                "/songs/hymnal/42.sps",
                saved.song?.sourceFile,
                "an edit rewrites the song in place, so it must not lose track of which file that is",
            )
        }

    @Test
    fun `secondary lyrics that are only section headers are discarded`() = editor { saved ->
        // A second language left with nothing but the structure copied over is not a translation.
        secondaryPane()
        type(Field.LYRICS, "[Verse 1]\n\n[Chorus]\n  ")
        save()
        assertEquals(
            emptyList(),
            saved.song?.secondaryLyrics,
            "headers and blank lines alone must not be stored as a second language",
        )
    }

    @Test
    fun `secondary lyrics with real content are kept`() = editor { saved ->
        secondaryPane()
        type(Field.LYRICS, "[Verse 1]\nO Gnade Gottes")
        save()
        assertEquals(
            listOf("[Verse 1]", "O Gnade Gottes"),
            saved.song?.secondaryLyrics,
            "one line of actual text makes the whole thing a translation worth keeping",
        )
    }

    // ── Refusing to save ────────────────────────────────────────────────────────

    @Test
    fun `a song duplicating another's number, title and songbook cannot be saved`() {
        val existing = aSong(sourceFile = "/a.sps")
        editor(song = aSong(sourceFile = "/b.sps"), existingSongs = listOf(existing)) { _ ->
            onNodeWithText(Label.SAVE).assertIsNotEnabled()
        }
    }

    @Test
    fun `a song does not count as a duplicate of itself`() {
        val self = aSong(sourceFile = "/a.sps")
        editor(song = self, existingSongs = listOf(self)) { _ ->
            // Editing a song already in the library must not lock its own Save button.
            onNodeWithText(Label.SAVE).assertIsEnabled()
        }
    }

    @Test
    fun `the duplicate check ignores case in the title and songbook`() {
        val existing = aSong(title = "amazing grace", songbook = "hymnal", sourceFile = "/a.sps")
        editor(song = aSong(sourceFile = "/b.sps"), existingSongs = listOf(existing)) { _ ->
            onNodeWithText(Label.SAVE).assertIsNotEnabled()
        }
    }

    @Test
    fun `a different number is not a duplicate`() {
        val existing = aSong(number = "42", sourceFile = "/a.sps")
        editor(song = aSong(number = "43", sourceFile = "/b.sps"), existingSongs = listOf(existing)) { _ ->
            onNodeWithText(Label.SAVE).assertIsEnabled()
        }
    }

    @Test
    fun `the same number and songbook with a different title is not a duplicate`() {
        val existing = aSong(number = "42", title = "Amazing Grace", songbook = "Hymnal", sourceFile = "/a.sps")
        editor(
            song = aSong(number = "42", title = "A Different Song", songbook = "Hymnal", sourceFile = "/b.sps"),
            existingSongs = listOf(existing),
        ) { _ ->
            onNodeWithText(Label.SAVE).assertIsEnabled()
        }
    }

    @Test
    fun `the same number and title with a different songbook is not a duplicate`() {
        val existing = aSong(number = "42", title = "Amazing Grace", songbook = "Hymnal", sourceFile = "/a.sps")
        editor(
            song = aSong(number = "42", title = "Amazing Grace", songbook = "Chorus Book", sourceFile = "/b.sps"),
            existingSongs = listOf(existing),
        ) { _ ->
            onNodeWithText(Label.SAVE).assertIsEnabled()
        }
    }

    @Test
    fun `a new song cannot be saved until it has both a title and a songbook`() =
        editor(song = blankSong(), isNewSong = true) { _ ->
            onNodeWithText(Label.SAVE).assertIsNotEnabled()
        }

    @Test
    fun `a new song with a title but no songbook still cannot be saved`() =
        editor(song = blankSong(), isNewSong = true) { _ ->
            type(Field.TITLE, "Something New")
            onNodeWithText(Label.SAVE).assertIsNotEnabled()
        }

    @Test
    fun `a new song with a songbook but no title still cannot be saved`() =
        editor(song = blankSong(), isNewSong = true, songbooks = listOf("Hymnal")) { _ ->
            songbook().performClick()
            waitForIdle()
            tap("Hymnal")

            onNodeWithText(Label.SAVE).assertIsNotEnabled()
        }

    @Test
    fun `an existing song is saveable as it stands`() = editor { _ ->
        onNodeWithText(Label.SAVE).assertIsEnabled()
    }

    // ── The title a new song gets for free ──────────────────────────────────────

    @Test
    fun `a new song takes its title from the first real line of the lyrics`() =
        editor(song = blankSong(), isNewSong = true) { _ ->
            type(Field.LYRICS, "[Verse 1]\nAmazing grace how sweet the sound")
            field(Field.TITLE).assertTextContains("Amazing grace how sweet the sound")
        }

    @Test
    fun `section headers and blank lines are skipped when guessing the title`() =
        editor(song = blankSong(), isNewSong = true) { _ ->
            type(Field.LYRICS, "\n[Chorus]\n\n  Be thou my vision  \nO Lord of my heart")
            field(Field.TITLE).assertTextContains("Be thou my vision")
        }

    @Test
    fun `lyrics with nothing but section headers guess a blank title`() =
        editor(song = blankSong(), isNewSong = true) { _ ->
            type(Field.LYRICS, "[Verse 1]\n\n[Chorus]")
            assertEquals("", titleText())
        }

    @Test
    fun `a title typed by hand is not overwritten by later lyric edits`() =
        editor(song = blankSong(), isNewSong = true) { _ ->
            type(Field.TITLE, "My Own Title")
            type(Field.LYRICS, "[Verse 1]\nSome first line entirely unlike it")
            field(Field.TITLE).assertTextContains("My Own Title")
        }

    @Test
    fun `an existing song's title is never guessed over`() = editor(aSong(title = "Amazing Grace")) { _ ->
        type(Field.LYRICS, "[Verse 1]\nA completely different first line")
        field(Field.TITLE).assertTextContains("Amazing Grace")
    }

    @Test
    fun `clearing a hand-typed title lets the lyrics guess it again`() =
        editor(song = blankSong(), isNewSong = true) { _ ->
            type(Field.TITLE, "My Own Title")
            type(Field.TITLE, "")
            type(Field.LYRICS, "[Verse 1]\nA fresh first line")

            field(Field.TITLE).assertTextContains("A fresh first line")
        }

    // ── Choosing a songbook ─────────────────────────────────────────────────────

    @Test
    fun `the songbook is picked from those already in the library`() =
        editor(song = blankSong(), isNewSong = true, songbooks = listOf("Hymnal", "Chorus Book")) { saved ->
            type(Field.TITLE, "Something New")
            onNodeWithText(Label.SAVE).assertIsNotEnabled()

            songbook().performClick()
            waitForIdle()
            tap("Chorus Book")

            songbook().assertTextContains("Chorus Book")
            onNodeWithText(Label.SAVE).assertIsEnabled()
            save()
            assertEquals("Chorus Book", saved.song?.songbook, "the songbook chosen must be the one saved")
        }

    @Test
    fun `a songbook the library does not have yet can be typed in instead`() =
        editor(song = blankSong(), isNewSong = true, songbooks = listOf("Hymnal")) { saved ->
            type(Field.TITLE, "Something New")

            songbook().performClick()
            waitForIdle()
            tap(Label.ADD_NEW)

            // Add New turns the picker into a ninth, typeable field.
            assertEquals(Field.COUNT + 1, fieldCount(), "the songbook becomes a typed field")
            type(Field.SONGBOOK_TYPED, "Youth Songbook")
            save()
            assertEquals("Youth Songbook", saved.song?.songbook)
        }

    // ── Leaving ─────────────────────────────────────────────────────────────────

    @Test
    fun `Cancel closes without saving`() = editor { saved ->
        type(Field.TITLE, "Edited But Abandoned")
        tap(Label.CANCEL)
        assertEquals(1, saved.dismissed)
        assertNull(saved.song, "an abandoned edit must not reach the library")
    }

    // ── Tempo and capo ──────────────────────────────────────────────────────────
    //
    // These are per-machine settings, not part of the song file, and only the stage monitor reads
    // them — so the editor offers them only where there is one. They still ride through Save on
    // every machine, which is the part that is easy to get wrong.

    private fun ComposeUiTest.fieldText(ordinal: Int): String =
        field(ordinal).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text ?: ""

    @Test
    fun `without a stage monitor the tempo and capo fields are not offered`() = editor { _ ->
        assertEquals(Field.COUNT, fieldCount())
        onAllNodesWithText(Label.TEMPO_CAPTION).assertCountEquals(0)
        onAllNodesWithText(Label.CAPO_CAPTION).assertCountEquals(0)
    }

    @Test
    fun `a stage monitor adds them after the tune field`() =
        editor(showTuningFields = true) { _ ->
            assertEquals(Field.COUNT_WITH_TUNING, fieldCount())
            onNodeWithText(Label.TEMPO_CAPTION).assertExists()
            onNodeWithText(Label.CAPO_CAPTION).assertExists()
        }

    @Test
    fun `a stored tempo and capo are offered for editing`() =
        editor(tuning = SongTuning(bpm = 120, capo = 3), showTuningFields = true) { _ ->
            assertEquals("120", fieldText(Field.TEMPO))
            assertEquals("3", fieldText(Field.CAPO))
        }

    @Test
    fun `an unset tempo is an empty box, not a zero`() =
        editor(tuning = SongTuning(), showTuningFields = true) { _ ->
            // 0 is the "off" value, so showing it would read as a deliberate setting of zero rather
            // than as nothing set.
            assertEquals("", fieldText(Field.TEMPO))
            assertEquals("", fieldText(Field.CAPO))
        }

    @Test
    fun `edited tempo and capo come back through Save`() =
        editor(showTuningFields = true) { saved ->
            type(Field.TEMPO, "96")
            type(Field.CAPO, "2")
            save()

            assertEquals(96, saved.tuning?.bpm)
            assertEquals(2, saved.tuning?.capo)
        }

    @Test
    fun `a tempo or capo beyond what an instrument can do is clamped on the way out`() =
        editor(showTuningFields = true) { saved ->
            // The boxes accept three and two digits respectively, so these are reachable by typing.
            type(Field.TEMPO, "999")
            type(Field.CAPO, "99")
            save()

            assertEquals(300, saved.tuning?.bpm, "300bpm is the ceiling")
            assertEquals(12, saved.tuning?.capo, "a guitar has twelve usable frets here")
        }

    @Test
    fun `editing on a machine with no stage monitor keeps the tuning someone else set`() =
        editor(tuning = SongTuning(bpm = 120, capo = 3)) { saved ->
            // The fields are hidden here, but their buffers are still seeded from the stored tuning
            // and still feed Save. Were they not, editing a lyric on the booth machine would wipe
            // the tempo set on the one driving the stage monitor.
            type(Field.TITLE, "Retitled From The Booth")
            save()

            assertEquals(120, saved.tuning?.bpm)
            assertEquals(3, saved.tuning?.capo)
        }
}
