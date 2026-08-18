package converter.song

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The MediaShout script walk driven straight from JSON, and the container checks around it.
 *
 * `scriptModel.json` is written by a .NET serializer, so a property is a bare value in one script
 * and a `{"$type":…,"$value":…}` wrapper in the next, and anything the operator never filled in is
 * simply absent. Every case here is a shape that must not throw or silently drop a song.
 */
class MediaShoutEdgeCasesTest {

    private val temp: File = Files.createTempDirectory("mediashout-edges").toFile()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun songsOf(script: String) =
        MediaShoutConverter.songsOf(json.parseToJsonElement(script) as JsonObject)

    private fun rtf(vararg lines: String): String {
        val document = """{\rtf1\ansi\ansicpg1252{\fonttbl{\f0\fnil\fcharset0 Arial;}}\pard """ +
            lines.joinToString("""\par """) + "}"
        return document.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private fun textItem(vararg lines: String) =
        """{"TypeId":"VisualItem+Text","Properties":{"Text":"${rtf(*lines)}"}}"""

    private fun page(properties: String, items: String) =
        """{"TypeId":"Page","Properties":{$properties},"Items":[$items]}"""

    private fun lyricCue(properties: String, vararg pages: String) =
        """{"TypeId":"Cue","Properties":{$properties,"Type":1},"Pages":[${pages.joinToString(",")}]}"""

    // ── The cue list ──────────────────────────────────────────────────────────

    @Test
    fun `a script with no cue list at all yields no songs`() {
        assertTrue(songsOf("""{}""").isEmpty())
        assertTrue(songsOf("""{"Cues":null}""").isEmpty())
    }

    @Test
    fun `entries in the cue list that are not objects are stepped over`() {
        val cue = lyricCue(""""Name":"Hymn"""", page(""""Name":"Verse 1"""", textItem("a line")))
        assertEquals(listOf("Hymn"), songsOf("""{"Cues":["stray",7,null,$cue]}""").map { it.title })
    }

    @Test
    fun `a cue with no properties is not a lyric cue`() {
        assertTrue(songsOf("""{"Cues":[{"TypeId":"Cue","Pages":[]}]}""").isEmpty())
    }

    @Test
    fun `a cue type is read whether it is a bare number or a wrapped one`() {
        val bare = """{"Properties":{"Name":"Bare","Type":1},"Pages":[${page(""""Name":"V1"""", textItem("x"))}]}"""
        val wrapped = """{"Properties":{"Name":"Wrapped","Type":{"${'$'}value":1}},
            |"Pages":[${page(""""Name":"V1"""", textItem("y"))}]}""".trimMargin().replace("\n", "")
        assertEquals(listOf("Bare", "Wrapped"), songsOf("""{"Cues":[$bare,$wrapped]}""").map { it.title })
    }

    @Test
    fun `a cue type that is neither a number nor a wrapper is not a song`() {
        val cue = """{"Properties":{"Name":"Odd","Type":["array"]},
            |"Pages":[${page(""""Name":"V1"""", textItem("x"))}]}""".trimMargin().replace("\n", "")
        assertTrue(songsOf("""{"Cues":[$cue]}""").isEmpty())
    }

    @Test
    fun `a lyric cue with nothing presentable in it is dropped rather than written empty`() {
        assertTrue(songsOf("""{"Cues":[${lyricCue(""""Name":"Empty"""")}]}""").isEmpty())
    }

    @Test
    fun `a song the operator never named is still written`() {
        val cue = lyricCue(""""Name":""""", page(""""Name":"Verse 1"""", textItem("a line")))
        assertEquals("Song", songsOf("""{"Cues":[$cue]}""").single().title)
    }

    // ── Pages ─────────────────────────────────────────────────────────────────

    @Test
    fun `a page marked skipped is not part of the song`() {
        val cue = lyricCue(
            """"Name":"Hymn"""",
            page(""""Name":"Verse 1","IsSkipped":"true"""", textItem("skipped line")),
            page(""""Name":"Verse 2"""", textItem("kept line")),
        )
        val song = songsOf("""{"Cues":[$cue]}""").single()
        assertEquals(listOf(listOf("kept line")), song.sections.map { it.lines })
    }

    @Test
    fun `a page with no items and a page with no text item are both skipped`() {
        val cue = lyricCue(
            """"Name":"Hymn"""",
            """{"TypeId":"Page","Properties":{"Name":"Empty"}}""",
            page(""""Name":"Picture"""", """{"TypeId":"VisualItem+Image","Properties":{"Path":"x.png"}}"""),
            page(""""Name":"Verse 1"""", textItem("the only line")),
        )
        assertEquals(listOf(listOf("the only line")), songsOf("""{"Cues":[$cue]}""").single().sections.map { it.lines })
    }

    @Test
    fun `a text item that holds nothing is not a section`() {
        val cue = lyricCue(
            """"Name":"Hymn"""",
            page(""""Name":"Blank"""", """{"TypeId":"VisualItem+Text","Properties":{"Text":"   "}}"""),
            page(""""Name":"Verse 1"""", textItem("real line")),
        )
        assertEquals(1, songsOf("""{"Cues":[$cue]}""").single().sections.size)
    }

    @Test
    fun `an RTF body with only blank lines leaves the page out`() {
        val cue = lyricCue(
            """"Name":"Hymn"""",
            page(""""Name":"Blank"""", textItem("", "   ")),
            page(""""Name":"Verse 1"""", textItem("real line")),
        )
        assertEquals(1, songsOf("""{"Cues":[$cue]}""").single().sections.size)
    }

    @Test
    fun `the operator's own page name wins over the stock one`() {
        val cue = lyricCue(
            """"Name":"Hymn"""",
            page(""""Name":"Page 1","CustomName":"Chorus"""", textItem("sing it again")),
        )
        assertEquals(listOf("Chorus"), songsOf("""{"Cues":[$cue]}""").single().sections.map { it.label })
    }

    @Test
    fun `a page name that is not a section label is replaced by the numbering`() {
        val cue = lyricCue(
            """"Name":"Hymn"""",
            page(""""Name":"Slide with the good picture"""", textItem("first")),
            page(""""Name":"Verse 2"""", textItem("second")),
        )
        assertEquals(listOf("Verse 1", "Verse 2"), songsOf("""{"Cues":[$cue]}""").single().sections.map { it.label })
    }

    // ── The container ─────────────────────────────────────────────────────────

    private fun container(zip: ByteArray, magic: String = "sc7x", zipOffset: Int? = null, zipLength: Int? = null): ByteArray {
        val thumbnail = ByteArray(8) { 0x7f }
        val header = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
        header.put(magic.toByteArray(Charsets.US_ASCII))
        header.putInt(20).putInt(thumbnail.size)
        header.putInt(zipOffset ?: (20 + thumbnail.size)).putInt(zipLength ?: zip.size)
        return header.array() + thumbnail + zip
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray =
        ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { stream ->
                entries.forEach { (name, body) ->
                    stream.putNextEntry(ZipEntry(name))
                    stream.write(body.toByteArray(Charsets.UTF_8))
                    stream.closeEntry()
                }
            }
        }.toByteArray()

    @Test
    fun `a file shorter than the header is not a script`() {
        val stub = File(temp, "stub.sc7x").apply { writeBytes(ByteArray(4)) }
        assertFalse(MediaShoutConverter.isScript(stub))
        assertFailsWith<IllegalArgumentException> { MediaShoutConverter.parse(stub) }
    }

    @Test
    fun `a directory named like a script is not one`() {
        assertFalse(MediaShoutConverter.isScript(File(temp, "folder.sc7x").apply { mkdirs() }))
    }

    @Test
    fun `a header pointing outside the file is refused`() {
        val file = File(temp, "bad-offset.sc7x").apply {
            writeBytes(container(zipOf("scriptModel.json" to "{}"), zipOffset = 999_999))
        }
        val error = assertFailsWith<IllegalArgumentException> { MediaShoutConverter.parse(file) }
        assertTrue(error.message!!.contains("readable archive"), "got '${error.message}'")
    }

    @Test
    fun `a header claiming an empty archive is refused`() {
        val file = File(temp, "empty-zip.sc7x").apply {
            writeBytes(container(zipOf("scriptModel.json" to "{}"), zipLength = 0))
        }
        assertFailsWith<IllegalArgumentException> { MediaShoutConverter.parse(file) }
    }

    @Test
    fun `an archive with no script model in it is refused by name`() {
        val file = File(temp, "no-model.sc7x").apply {
            writeBytes(container(zipOf("thumbnail.png" to "not json", "notes.txt" to "hello")))
        }
        val error = assertFailsWith<IllegalArgumentException> { MediaShoutConverter.parse(file) }
        assertTrue(error.message!!.contains("scriptModel.json"), "got '${error.message}'")
    }

    @Test
    fun `the script model is found wherever the archive filed it`() {
        val script = """{"Cues":[${lyricCue(""""Name":"Hymn"""", page(""""Name":"Verse 1"""", textItem("a line")))}]}"""
        val file = File(temp, "nested.sc7x").apply {
            writeBytes(container(zipOf("data/scriptModel.json" to script)))
        }
        assertEquals(listOf("Hymn"), MediaShoutConverter.parse(file).map { it.title })
    }

    @Test
    fun `a script model that is not an object reads as a script with no songs`() {
        val file = File(temp, "array-model.sc7x").apply {
            writeBytes(container(zipOf("scriptModel.json" to """["not","an","object"]""")))
        }
        assertTrue(MediaShoutConverter.parse(file).isEmpty())
    }

    @Test
    fun `a file whose magic is wrong is reported rather than parsed`() {
        val file = File(temp, "wrong-magic.sc7x").apply {
            writeBytes(container(zipOf("scriptModel.json" to "{}"), magic = "zzzz"))
        }
        assertFalse(MediaShoutConverter.isScript(file))
        assertTrue(MediaShoutConverter.convert(file, File(temp, "out")).errors.isNotEmpty())
    }
}
