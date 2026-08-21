package converter.library

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cleaning control characters out of imported lyrics.
 *
 * Some SPS exports use a vertical tab where a newline belongs and leave stray null bytes behind.
 * Both render as invisible junk in the app, so they are repaired on the way in.
 */
class TextUtilsTest {

    private val temp: File = Files.createTempDirectory("converter-text-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    @Test
    fun `a vertical tab becomes a line break`() {
        assertEquals("line one\nline two", TextUtils.sanitizeLyricText("line one\u000Bline two"))
    }

    @Test
    fun `null bytes are stripped`() {
        assertEquals("clean", TextUtils.sanitizeLyricText("cl\u0000ean"))
    }

    @Test
    fun `trailing whitespace is trimmed per line but blank lines are kept`() {
        // Blank lines separate sections, so collapsing them would merge verses.
        assertEquals("a\n\nb", TextUtils.sanitizeLyricText("a   \n   \nb\t"))
    }

    @Test
    fun `already-clean text is returned unchanged`() {
        val clean = "Amazing grace\nhow sweet the sound"
        assertEquals(clean, TextUtils.sanitizeLyricText(clean))
    }

    @Test
    fun `non-ASCII lyrics are untouched`() {
        val russian = "Слава Богу\nво веки веков"
        assertEquals(russian, TextUtils.sanitizeLyricText(russian))
    }

    @Test
    fun `a file needing no repair is not rewritten`() {
        val file = File(temp, "clean.song")
        file.writeText("Amazing grace", Charsets.UTF_8)
        assertTrue(!TextUtils.sanitizeFile(file), "reported as unmodified")
    }

    @Test
    fun `a file with control characters is repaired in place and reported`() {
        val file = File(temp, "dirty.song")
        file.writeText("line one\u000Bline\u0000 two", Charsets.UTF_8)
        assertTrue(TextUtils.sanitizeFile(file), "reported as modified")
        assertEquals("line one\nline two", file.readText(Charsets.UTF_8))
    }

    @Test
    fun `scanning finds only the song files carrying control characters`() {
        File(temp, "clean.song").writeText("fine", Charsets.UTF_8)
        val dirty = File(temp, "dirty.song")
        dirty.writeText("bad\u000Btext", Charsets.UTF_8)
        // A non-song file with the same problem is not this tool's business.
        File(temp, "other.txt").writeText("bad\u000Btext", Charsets.UTF_8)

        assertEquals(
            listOf(dirty.canonicalPath),
            TextUtils.findFilesWithControlChars(temp).map { it.canonicalPath },
        )
    }
}
