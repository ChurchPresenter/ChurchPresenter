package org.churchpresenter.app.churchpresenter.viewmodel

import kotlinx.coroutines.Dispatchers
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.SongSettings
import org.churchpresenter.core.models.songs.LyricSection
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SongsViewModelChordFoldingTest {

    private lateinit var dir: File
    private lateinit var model: SongsViewModel

    @BeforeTest
    fun create() {
        dir = Files.createTempDirectory("cp-chord-fold").toFile()
        model = SongsViewModel(
            AppSettings(songSettings = SongSettings(storageDirectory = dir.absolutePath)),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
            enableFolderWatcher = false,
        )
    }

    @AfterTest
    fun cleanUp() {
        runCatching { model.dispose() }
        dir.deleteRecursively()
    }

    private fun words(header: String, vararg lines: String, chords: List<String> = emptyList()) =
        LyricSection(header = header, type = "verse", lines = lines.toList(), chordLines = chords)

    private fun chordsOnly(header: String, vararg chords: String) =
        LyricSection(header = header, type = "verse", lines = emptyList(), chordLines = chords.toList())

    private fun fold(vararg sections: LyricSection) = model.foldChordOnlySections(sections.toList())

    @Test
    fun `an intro is folded onto the section that follows it`() {
        val folded = fold(
            chordsOnly("[Intro]", "[G] [C] [D]"),
            words("[Verse 1]", "Amazing grace"),
        )

        assertEquals(1, folded.size)
        assertEquals(listOf("Amazing grace"), folded[0].lines)
        assertEquals(listOf("[Intro]", "[G] [C] [D]", "Amazing grace"), folded[0].chordLines)
    }

    @Test
    fun `a folded intro keeps the verse's own chart underneath it`() {
        val folded = fold(
            chordsOnly("[Intro]", "[G] [C]"),
            words("[Verse 1]", "Amazing grace", chords = listOf("[G]Amazing [C]grace")),
        )

        assertEquals(listOf("[Intro]", "[G] [C]", "[G]Amazing [C]grace"), folded[0].chordLines)
    }

    @Test
    fun `an intro with no header of its own carries only its chords`() {
        val folded = fold(
            LyricSection(type = "verse", chordLines = listOf("[G] [C]")),
            words("[Verse 1]", "Amazing grace", chords = listOf("[G]Amazing")),
        )

        assertEquals(listOf("[G] [C]", "[G]Amazing"), folded[0].chordLines)
    }

    @Test
    fun `an intro with a blank header carries only its chords`() {
        val folded = fold(
            LyricSection(header = "   ", type = "verse", chordLines = listOf("[G] [C]")),
            words("[Verse 1]", "Amazing grace", chords = listOf("[G]Amazing")),
        )

        assertEquals(listOf("[G] [C]", "[G]Amazing"), folded[0].chordLines)
    }

    @Test
    fun `an outro goes onto the section before it`() {
        val folded = fold(
            words("[Verse 1]", "Amazing grace", chords = listOf("[G]Amazing")),
            chordsOnly("[Outro]", "[C] [G]"),
        )

        assertEquals(1, folded.size)
        assertEquals(listOf("[G]Amazing", "[Outro]", "[C] [G]"), folded[0].chordLines)
    }

    @Test
    fun `an outro after a chordless verse takes that verse's words as the chart`() {
        val folded = fold(
            words("[Verse 1]", "Amazing grace"),
            chordsOnly("[Outro]", "[C] [G]"),
        )

        assertEquals(listOf("Amazing grace", "[Outro]", "[C] [G]"), folded[0].chordLines)
    }

    @Test
    fun `a song that is nothing but chords is left as it was written`() {
        val onlyChords = listOf(chordsOnly("[Intro]", "[G] [C]"))

        assertEquals(onlyChords, model.foldChordOnlySections(onlyChords))
    }

    @Test
    fun `several chord-only sections in a row fold together onto the next words`() {
        val folded = fold(
            chordsOnly("[Intro]", "[G]"),
            chordsOnly("[Turnaround]", "[C]"),
            words("[Verse 1]", "Amazing grace"),
        )

        assertEquals(1, folded.size)
        assertEquals(
            listOf("[Intro]", "[G]", "[Turnaround]", "[C]", "Amazing grace"),
            folded[0].chordLines,
        )
    }

    @Test
    fun `an intro folds only onto the first section after it`() {
        val folded = fold(
            chordsOnly("[Intro]", "[G]"),
            words("[Verse 1]", "Amazing grace"),
            words("[Verse 2]", "Twas grace that taught"),
        )

        assertEquals(2, folded.size)
        assertEquals(listOf("[Intro]", "[G]", "Amazing grace"), folded[0].chordLines)
        assertEquals(emptyList<String>(), folded[1].chordLines)
    }

    @Test
    fun `a song with no chord-only sections is unchanged`() {
        val sections = listOf(
            words("[Verse 1]", "Amazing grace", chords = listOf("[G]Amazing")),
            words("[Verse 2]", "Twas grace"),
        )

        assertEquals(sections, model.foldChordOnlySections(sections))
    }

    @Test
    fun `an empty song folds to nothing`() {
        assertEquals(emptyList<LyricSection>(), model.foldChordOnlySections(emptyList()))
    }
}
