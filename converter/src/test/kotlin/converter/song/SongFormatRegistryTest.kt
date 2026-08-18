package converter.song

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The format registry the Songs tab drives.
 *
 * The UI looks a format up by the id its rail entry carries and then asks it for everything else —
 * extensions for the picker, whether an output folder is required, what the input becomes. So a
 * mismatch between a rail id and a converter id would present a working-looking panel that converts
 * with the wrong format, which is what the id test below exists to prevent.
 */
class SongFormatRegistryTest {

    private val temp: File = Files.createTempDirectory("converter-registry-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    @Test
    fun `every registered format is reachable by its own id`() {
        for (format in SongFormatConverters.all) {
            assertSame(format, SongFormatConverters.byId(format.id), format.id)
        }
    }

    @Test
    fun `ids are unique`() {
        val ids = SongFormatConverters.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size, ids.toString())
    }

    @Test
    fun `an unknown id falls back rather than throwing at the UI`() {
        assertSame(SongBeamerFormat, SongFormatConverters.byId("songshowplus"))
    }

    @Test
    fun `every format offers at least one extension for the picker`() {
        for (format in SongFormatConverters.all) {
            assertTrue(format.extensions.isNotEmpty(), format.id)
            assertTrue(format.extensions.none { it.startsWith(".") }, "${format.id}: ${format.extensions}")
        }
    }

    @Test
    fun `the formats that fan one input out into many files demand an output folder`() {
        assertTrue(SoftProjectorFormat.needsOutputFolder)
        assertTrue(DocumentFormat.needsOutputFolder)
        assertTrue(EasySlidesFormat.needsOutputFolder)
        assertTrue(QueleaFormat.needsOutputFolder)
        assertTrue(OpenLpFormat.needsOutputFolder)
        assertTrue(EasyWorshipFormat.needsOutputFolder)
        assertTrue(MediaShoutFormat.needsOutputFolder)
        // These write one .song beside each input, so "same as input" is a valid destination.
        assertTrue(!SongBeamerFormat.needsOutputFolder)
        assertTrue(!FreeWorshipFormat.needsOutputFolder)
        assertTrue(!OpenSongFormat.needsOutputFolder)
        assertTrue(!FreeShowFormat.needsOutputFolder)
        assertTrue(!ProPresenterFormat.needsOutputFolder)
    }

    @Test
    fun `every format converts a whole selection in one run`() {
        // A migration is never one file. A format that took a single input at a time — .sps did —
        // turns a library move into the same click repeated once per song book.
        for (format in SongFormatConverters.all) {
            assertTrue(format.allowsMultipleFiles, format.id)
        }
    }

    @Test
    fun `only OpenSong claims files that carry no extension`() {
        assertTrue(OpenSongFormat.acceptsExtensionlessFiles)
        assertEquals(
            listOf("opensong"),
            SongFormatConverters.all.filter { it.acceptsExtensionlessFiles }.map { it.id },
        )
    }

    @Test
    fun `SongBeamer converts through the registry and reports what it wrote`() {
        val input = File(temp, "hymn.sng").apply {
            writeText("#Title=Hymn\n#VerseOrder=Verse 1\n---\nVerse 1\nline one\n", Charsets.UTF_8)
        }

        val result = SongBeamerFormat.convert(input, null)

        assertEquals(listOf("hymn.song"), result.outputFiles.map { it.name })
        assertTrue(result.errors.isEmpty(), result.errors.toString())
        assertTrue(result.outputFiles.single().exists())
    }

    @Test
    fun `a null output folder writes beside the input`() {
        val nested = File(temp, "nested").apply { mkdirs() }
        val input = File(nested, "hymn.sng").apply { writeText("#Title=Hymn\n---\nVerse 1\nline\n", Charsets.UTF_8) }

        val written = SongBeamerFormat.convert(input, null).outputFiles.single()

        assertEquals(nested.canonicalFile, written.parentFile.canonicalFile)
    }

    @Test
    fun `an explicit output folder is used instead`() {
        val out = File(temp, "out").apply { mkdirs() }
        val input = File(temp, "hymn.sng").apply { writeText("#Title=Hymn\n---\nVerse 1\nline\n", Charsets.UTF_8) }

        val written = SongBeamerFormat.convert(input, out).outputFiles.single()

        assertEquals(out.canonicalFile, written.parentFile.canonicalFile)
    }

    @Test
    fun `describe reports the title and section count the preview list shows`() {
        val input = File(temp, "hymn.sng").apply {
            writeText("#Title=Hymn\n#VerseOrder=Verse 1,Chorus\n---\nVerse 1\na\n---\nChorus\nb\n", Charsets.UTF_8)
        }

        val info = SongBeamerFormat.describe(input)

        assertEquals("Hymn", info.title)
        assertEquals(2, info.sectionCount)
        assertEquals(listOf("Verse 1", "Chorus"), info.verseOrder)
    }

    @Test
    fun `output names carry the song extension for the per-file formats`() {
        assertEquals("hymn.song", SongBeamerFormat.outputNameFor(File("hymn.sng")))
        assertEquals("hymn.song", FreeWorshipFormat.outputNameFor(File("hymn.xml")))
    }
}
