package org.churchpresenter.converter.song

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * FreeShow `.show` files. Everything tested here is a way the file's own order differs from the
 * singing order: the wrapping `[id, show]` pair, the slides *map* whose keys are not a sequence, and
 * child slides that belong to their parent's section.
 */
class FreeShowConverterTest {

    private val temp: File = Files.createTempDirectory("converter-freeshow-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun slide(group: String?, vararg lines: String): String {
        val groupJson = if (group == null) "null" else "\"$group\""
        val lineJson = lines.joinToString(",") { """{"align":"","text":[{"value":"$it","style":""}]}""" }
        return """{"group":$groupJson,"color":"#fff","items":[{"type":"text","lines":[$lineJson]}]}"""
    }

    private fun show(slides: String, layout: String, wrapped: Boolean = true, meta: String = ""): File {
        val body = """
            {"name":"Amazing Grace","settings":{"activeLayout":"L1"},
             "meta":{$meta},
             "slides":{$slides},
             "layouts":{"L1":{"name":"Default","slides":[$layout]}}}
        """.trimIndent()
        val text = if (wrapped) """["show-id",$body]""" else body
        return File(temp, "grace.show").apply { writeText(text, Charsets.UTF_8) }
    }

    @Test
    fun `the show object is unwrapped from the id it is paired with on disk`() {
        val file = show(
            slides = """"a":${slide("Verse 1", "Amazing grace")}""",
            layout = """{"id":"a"}""",
        )

        assertEquals("Amazing Grace", FreeShowConverter.parse(file).title)
    }

    @Test
    fun `a bare show object is read too`() {
        val file = show(
            slides = """"a":${slide("Verse 1", "Amazing grace")}""",
            layout = """{"id":"a"}""",
            wrapped = false,
        )

        assertEquals(listOf("Verse 1"), FreeShowConverter.parse(file).sections.map { it.label })
    }

    @Test
    fun `the active layout decides the order, not the order the slides map happens to list`() {
        val file = show(
            slides = """"a":${slide("Verse 1", "One")},"b":${slide("Chorus", "Praise")}""",
            layout = """{"id":"b"},{"id":"a"}""",
        )

        assertEquals(listOf("Chorus", "Verse 1"), FreeShowConverter.parse(file).sections.map { it.label })
    }

    @Test
    fun `a child slide continues its parent's section rather than opening one of its own`() {
        val parent = """{"group":"Verse 1","color":"#fff","children":["a2"],""" +
            """"items":[{"type":"text","lines":[{"text":[{"value":"Amazing grace"}]}]}]}"""
        val file = show(
            slides = """"a":$parent,"a2":${slide(null, "How sweet the sound")}""",
            layout = """{"id":"a"}""",
        )

        val sections = FreeShowConverter.parse(file).sections

        assertEquals(1, sections.size, sections.map { it.label }.toString())
        assertEquals(listOf("Amazing grace", "How sweet the sound"), sections.single().lines)
    }

    @Test
    fun `meta fills the title and author when it carries them`() {
        val file = show(
            slides = """"a":${slide("Verse 1", "One")}""",
            layout = """{"id":"a"}""",
            meta = """"title":"Grace","author":"John Newton","copyright":"Public Domain"""",
        )

        val song = FreeShowConverter.parse(file)

        assertEquals("Grace", song.title)
        assertEquals("John Newton", song.author)
        assertEquals("Public Domain", song.copyright)
    }

    @Test
    fun `a null meta field does not become the word null in the song file`() {
        val file = show(
            slides = """"a":${slide("Verse 1", "One")}""",
            layout = """{"id":"a"}""",
            meta = """"title":null,"author":null""",
        )
        val output = File(temp, "out.song")

        FreeShowConverter.convert(file, output)

        val text = output.readText()
        assertTrue(text.contains("title: Amazing Grace"), text)
        assertTrue(!text.contains("null"), text)
    }
}
