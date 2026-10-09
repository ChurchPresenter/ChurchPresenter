package org.churchpresenter.core.models.songs

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * A song written to a `.song` file reads back as the same song, and writing what was read gives
 * the same file byte for byte -- over generated songs, not only the fixtures in
 * [SongFileParserTest]: credits, chords, section headings, blank lines inside a section, words in
 * more than one script, and a second language.
 *
 * What the format cannot hold is not generated: a lyric line the reader takes as structure (a
 * language tag, a `title:` line, a `---` fence), and blank lines at either end of a section, which
 * the reader trims by design. The seed is pinned, so every run checks the same songs.
 */
class SongFileRoundTripPropertyTest {

    private val dir = Files.createTempDirectory("song-roundtrip")

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private val word = Arb.element(
        "Amazing", "grace", "how", "sweet", "the", "sound", "Слава", "Богу", "ąę", "it's", "1,2",
    )
    private val chord = Arb.element("[G]", "[C/E]", "[Am7]", "[Dsus4]", "[F#m7b5]", "")
    private val lyricLine = Arb.bind(Arb.list(word, 1..6), chord) { words, c -> c + words.joinToString(" ") }
    private val heading = Arb.element("[Verse 1]", "[Chorus]", "[Bridge]", "[Verse 2]")
    private val inner = Arb.bind(Arb.int(0..5), lyricLine, heading) { kind, line, head ->
        when (kind) {
            0 -> ""
            1 -> head
            else -> line
        }
    }
    private val lyrics = Arb.bind(lyricLine, Arb.list(inner, 0..12), lyricLine) { first, middle, last ->
        listOf(first) + middle + last
    }
    private val title = Arb.list(word, 1..4).map { it.joinToString(" ") }
    private val credit = Arb.list(word, 1..3).map { it.joinToString(" ") }.orNull()

    private val translation = Arb.bind(title, lyrics) { t, l ->
        SongTranslation(label = "", title = t, lyrics = l)
    }.orNull()

    private val song = Arb.bind(
        title, lyrics, credit, credit, credit, translation,
    ) { t, l, author, composer, ccli, second ->
        SongItem(
            number = "",
            title = t,
            author = author.orEmpty(),
            composer = composer.orEmpty(),
            ccliNumber = ccli.orEmpty(),
            lyrics = l,
        ).withTranslations(listOfNotNull(second))
    }

    @Test
    fun `a written song reads back as itself, and writes back the same file`() = runBlocking<Unit> {
        val parser = SongFileParser()
        val file = dir.resolve("song.$SONG_EXTENSION")
        checkAll(PropTestConfig(seed = 20_261_008L, iterations = 300), song) { original ->
            parser.writeSongFile(original, file.toString())
            val written = file.readText()
            val read = assertNotNull(parser.parseSongContent(written, file.toString()), "did not parse:\n$written")

            assertEquals(original.title, read.title, written)
            assertEquals(original.author, read.author, written)
            assertEquals(original.composer, read.composer, written)
            assertEquals(original.ccliNumber, read.ccliNumber, written)
            assertEquals(original.lyrics, read.lyrics, written)
            assertEquals(original.extraTranslations(), read.extraTranslations(), written)

            parser.writeSongFile(read, file.toString())
            assertEquals(written, file.readText(), "a second write changed the file")
        }
    }
}
