package converter.song

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OpenSong's plain-text `<lyrics>` body, which is where every one of this format's conventions
 * lives: chord and comment lines that must not be sung, the leading space on lyric lines, and the
 * leading digit that groups several verses into one unnumbered block.
 */
class OpenSongConverterTest {

    private val temp: File = Files.createTempDirectory("converter-opensong-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun songFile(name: String, presentation: String, lyrics: String): File =
        File(temp, name).apply {
            writeText(
                "<song>" +
                    "<title>Amazing Grace</title>" +
                    "<author>John Newton</author>" +
                    "<copyright>Public Domain</copyright>" +
                    "<presentation>$presentation</presentation>" +
                    "<lyrics>$lyrics</lyrics>" +
                    "</song>",
                Charsets.UTF_8,
            )
        }

    @Test
    fun `metadata comes off the elements around the lyrics`() {
        val song = OpenSongConverter.parse(songFile("grace", "", "[V1]\n Line\n"))

        assertEquals("Amazing Grace", song.title)
        assertEquals("John Newton", song.author)
        assertEquals("Public Domain", song.copyright)
    }

    @Test
    fun `chord lines and comments are dropped rather than sung`() {
        val lyrics = "[V1]\n.G       C\n Amazing grace how sweet\n;a note to the musicians\n That saved a wretch\n"

        val sections = OpenSongConverter.parse(songFile("grace", "", lyrics)).sections

        assertEquals(listOf("Amazing grace how sweet", "That saved a wretch"), sections.single().lines)
    }

    @Test
    fun `the padding that lines syllables up under the chords does not reach the screen`() {
        val lyrics = "[V]\n.       D              D7\n1A______ma________zing grace! How   sweet the  sound!\n"

        val sections = OpenSongConverter.parse(songFile("grace", "", lyrics)).sections

        assertEquals(listOf("Amazing grace! How sweet the sound!"), sections.single().lines)
    }

    @Test
    fun `each marker opens a section, named the way ChurchPresenter names them`() {
        val lyrics = "[V1]\n One\n[C]\n Praise\n[V2]\n Two\n"

        val labels = OpenSongConverter.parse(songFile("grace", "", lyrics)).sections.map { it.label }

        assertEquals(listOf("Verse 1", "Chorus", "Verse 2"), labels)
    }

    @Test
    fun `a leading digit inside an unnumbered block says which verse the line belongs to`() {
        val lyrics = "[V]\n1First line of one\n1Second line of one\n2First line of two\n"

        val sections = OpenSongConverter.parse(songFile("grace", "", lyrics)).sections

        assertEquals(listOf("Verse 1", "Verse 2"), sections.map { it.label })
        assertEquals(listOf("First line of one", "Second line of one"), sections.first().lines)
        assertEquals(listOf("First line of two"), sections.last().lines)
    }

    @Test
    fun `a numbered marker keeps a lyric line that happens to start with a digit`() {
        val lyrics = "[V1]\n3 crosses stood on a hill\n"

        val sections = OpenSongConverter.parse(songFile("grace", "", lyrics)).sections

        assertEquals(listOf("3 crosses stood on a hill"), sections.single().lines)
    }

    @Test
    fun `presentation decides the order the sections are written in`() {
        val lyrics = "[V1]\n One\n[C]\n Praise\n"

        val labels = OpenSongConverter.parse(songFile("grace", "C V1", lyrics)).sections.map { it.label }

        assertEquals(listOf("Chorus", "Verse 1"), labels)
    }

    @Test
    fun `a section named in presentation but absent from the lyrics is skipped, not invented`() {
        val lyrics = "[V1]\n One\n"

        val song = OpenSongConverter.parse(songFile("grace", "V1 C B", lyrics))

        assertEquals(listOf("Verse 1"), song.sections.map { it.label })
        assertEquals(listOf("V1", "C", "B"), song.verseOrder)
    }

    @Test
    fun `converting writes a song file whose title survives an extensionless input`() {
        val input = songFile("Amazing Grace", "", "[V1]\n Line one\n")
        val output = File(temp, "out.song")

        OpenSongConverter.convert(input, output)

        val text = output.readText()
        assertTrue(text.contains("title: Amazing Grace"), text)
        assertTrue(text.contains("[Verse 1]\nLine one"), text)
        assertTrue(text.contains("author: John Newton"), text)
    }
}
