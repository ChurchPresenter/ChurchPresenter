package org.churchpresenter.converter.song

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * No MediaShout script is published anywhere, so these build one to the documented layout — a
 * header of offsets, a thumbnail, and a zip holding `scriptModel.json` — rather than assert against
 * a real file. That covers the container and the JSON walk; it cannot confirm that a script saved by
 * MediaShout 7 matches the layout, which needs a real file to check.
 */
class MediaShoutConverterTest {

    private val temp: File = Files.createTempDirectory("mediashout-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    /** The lines as one RTF document, escaped for the JSON string it is embedded in. */
    private fun rtf(vararg lines: String): String {
        val document = """{\rtf1\ansi\ansicpg1252{\fonttbl{\f0\fnil\fcharset0 Arial;}}\pard """ +
            lines.joinToString("""\par """) + "}"
        return document.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private fun script(json: String, name: String = "service.sc7x"): File {
        val zip = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { stream ->
                stream.putNextEntry(ZipEntry("scriptModel.json"))
                stream.write(json.toByteArray(Charsets.UTF_8))
                stream.closeEntry()
            }
        }.toByteArray()

        // A stand-in for the PNG thumbnail: the reader locates the zip through the header, so what
        // sits between them only has to occupy the space the header says it does.
        val thumbnail = ByteArray(32) { 0x7f }
        val header = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
        header.put("sc7x".toByteArray(Charsets.US_ASCII))
        header.putInt(20).putInt(thumbnail.size)
        header.putInt(20 + thumbnail.size + 1).putInt(zip.size)

        return File(temp, name).apply {
            writeBytes(header.array() + thumbnail + byteArrayOf(0) + zip)
        }
    }

    private fun page(name: String, vararg text: String) =
        """{"TypeId":"Page","Properties":{"Name":"$name","CustomName":""},
           |"Items":[{"TypeId":"VisualItem+Text","Properties":{"Text":"${rtf(*text)}",
           |"Type":{"${'$'}type":"polino.model.Enums.TextItemType, polino.model","${'$'}value":1}}}]}"""
            .trimMargin().replace("\n", "")

    private fun lyricCue(title: String, vararg pages: String) =
        """{"TypeId":"Cue","Properties":{"Name":"$title",
           |"Type":{"${'$'}type":"polino.model.Enums.CueType, polino.model","${'$'}value":1}},
           |"Pages":[${pages.joinToString(",")}]}""".trimMargin().replace("\n", "")

    @Test
    fun `a script yields one song per lyric cue, with a section per page`() {
        val file = script(
            """{"Cues":[${lyricCue(
                "How Great Thou Art",
                page("Verse 1", "O Lord my God", "When I in awesome wonder"),
                page("Chorus", "Then sings my soul"),
            )}]}"""
        )

        val song = MediaShoutConverter.parse(file).single()
        assertEquals("How Great Thou Art", song.title)
        assertEquals(listOf("Verse 1", "Chorus"), song.sections.map { it.label })
        assertEquals(listOf("O Lord my God", "When I in awesome wonder"), song.sections.first().lines)
    }

    @Test
    fun `a cue that is not a song is left where it is`() {
        val notASong = """{"TypeId":"Cue","Properties":{"Name":"Sermon","Type":{"${'$'}value":3}},
            |"Pages":[${page("Point 1", "Three things")}]}""".trimMargin().replace("\n", "")
        val file = script("""{"Cues":[$notASong,${lyricCue("Hymn", page("Verse 1", "line"))}]}""")

        assertEquals(listOf("Hymn"), MediaShoutConverter.parse(file).map { it.title })
    }

    @Test
    fun `a page whose name says nothing about the section is numbered`() {
        val file = script(
            """{"Cues":[${lyricCue("Hymn", page("Page 1", "first"), page("Page 2", "second"))}]}"""
        )
        assertEquals(listOf("Verse 1", "Verse 2"), MediaShoutConverter.parse(file).single().sections.map { it.label })
    }

    @Test
    fun `a script with no songs in it reports that rather than writing nothing quietly`() {
        val result = MediaShoutConverter.convert(script("""{"Cues":[]}"""), File(temp, "out"))

        assertTrue(result.outputFiles.isEmpty())
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `converting writes a song file per song`() {
        val file = script("""{"Cues":[${lyricCue("Hymn", page("Verse 1", "a line"))}]}""")
        val result = MediaShoutConverter.convert(file, File(temp, "out"))

        assertTrue(result.errors.isEmpty(), result.errors.toString())
        assertEquals(listOf("Hymn.song"), result.outputFiles.map { it.name })
        assertTrue(result.outputFiles.single().readText().contains("a line"))
    }

    @Test
    fun `a file that is not a script is reported, not read as one`() {
        val notAScript = File(temp, "notes.sc7x").apply { writeText("this is not a MediaShout script") }

        assertTrue(!MediaShoutConverter.isScript(notAScript))
        assertTrue(MediaShoutConverter.convert(notAScript, File(temp, "out")).errors.isNotEmpty())
    }

    @Test
    fun `the sc7 variant is accepted too, since it differs only in the media it carries`() {
        val file = script("""{"Cues":[${lyricCue("Hymn", page("Verse 1", "a line"))}]}""", name = "service.sc7")

        assertTrue(MediaShoutConverter.isScript(file))
        assertEquals(listOf("Hymn"), MediaShoutConverter.parse(file).map { it.title })
    }
}
