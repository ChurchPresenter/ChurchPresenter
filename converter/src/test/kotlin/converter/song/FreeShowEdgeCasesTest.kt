package converter.song

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * FreeShow shows that are missing the parts a hand-written fixture always has.
 *
 * A `.show` is one big JSON object written by an Electron app, so every field is optional in
 * practice: no `meta`, a layout naming a slide that was deleted, a text item with no lines. None of
 * those may throw — the file is the user's only copy of the song.
 */
class FreeShowEdgeCasesTest {

    private val temp: File = Files.createTempDirectory("freeshow-edges").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun showFile(name: String, body: String): File =
        File(temp, name).apply { writeText(body, Charsets.UTF_8) }

    private fun slide(group: String?, vararg lines: String, children: String = ""): String {
        val groupJson = if (group == null) "null" else "\"$group\""
        val lineJson = lines.joinToString(",") { """{"align":"","text":[{"value":"$it","style":""}]}""" }
        val childJson = if (children.isEmpty()) "" else ""","children":[$children]"""
        return """{"group":$groupJson,"items":[{"type":"text","lines":[$lineJson]}]$childJson}"""
    }

    // ── The outer wrapper ─────────────────────────────────────────────────────

    @Test
    fun `a file that is neither an object nor an array of them is refused`() {
        val file = showFile("string.show", """"just a string"""")
        assertFailsWith<IllegalArgumentException> { FreeShowConverter.parse(file) }
    }

    @Test
    fun `an array holding no object at all is refused`() {
        val file = showFile("empty-array.show", """["show-id", 7]""")
        assertFailsWith<IllegalArgumentException> { FreeShowConverter.parse(file) }
    }

    // ── Metadata ──────────────────────────────────────────────────────────────

    @Test
    fun `the meta title wins over the show name`() {
        val file = showFile(
            "titles.show",
            """{"name":"file name","meta":{"title":"Real Title","author":"John Newton",
               "copyright":"Public Domain","number":"42"},
               "slides":{"a":${slide("Verse 1", "line")}},"layouts":{}}""",
        )
        val song = FreeShowConverter.parse(file)
        assertEquals("Real Title", song.title)
        assertEquals("John Newton", song.author)
        assertEquals("Public Domain", song.copyright)
        assertEquals("42", song.number)
    }

    @Test
    fun `a show with no meta block at all falls back to its name`() {
        val file = showFile("nometa.show", """{"name":"Amazing Grace","slides":{},"layouts":{}}""")
        val song = FreeShowConverter.parse(file)
        assertEquals("Amazing Grace", song.title)
        assertEquals("", song.author)
        assertEquals("", song.copyright)
        assertEquals("", song.number)
    }

    @Test
    fun `the artist stands in when no author is named`() {
        val file = showFile(
            "artist.show",
            """{"name":"x","meta":{"artist":"Chris Tomlin"},"slides":{},"layouts":{}}""",
        )
        assertEquals("Chris Tomlin", FreeShowConverter.parse(file).author)
    }

    @Test
    fun `a null metadata value reads as absent rather than as the word null`() {
        val file = showFile(
            "nulls.show",
            """{"name":"Named","meta":{"title":null,"author":null},"slides":{},"layouts":{}}""",
        )
        val song = FreeShowConverter.parse(file)
        assertEquals("Named", song.title)
        assertEquals("", song.author)
    }

    @Test
    fun `a show with no name anywhere is titled after its file`() {
        val file = showFile("Fallback Name.show", """{"slides":{"a":${slide("Verse 1", "line")}},"layouts":{}}""")
        val out = File(temp, "out.song")
        FreeShowConverter.convert(file, out)
        assertTrue(out.readText().contains("title: Fallback Name"))
    }

    // ── Slides and layouts ────────────────────────────────────────────────────

    @Test
    fun `a show with no slides object yields no sections`() {
        val file = showFile("noslides.show", """{"name":"x","layouts":{}}""")
        assertTrue(FreeShowConverter.parse(file).sections.isEmpty())
    }

    @Test
    fun `a layout naming a slide that is gone skips it rather than failing`() {
        val file = showFile(
            "missing-slide.show",
            """{"name":"x","settings":{"activeLayout":"L1"},"slides":{"a":${slide("Verse 1", "kept")}},
               "layouts":{"L1":{"slides":[{"id":"deleted"},{"id":"a"}]}}}""",
        )
        assertEquals(listOf(listOf("kept")), FreeShowConverter.parse(file).sections.map { it.lines })
    }

    @Test
    fun `a layout entry with no id is passed over`() {
        val file = showFile(
            "no-id.show",
            """{"name":"x","settings":{"activeLayout":"L1"},"slides":{"a":${slide("Verse 1", "kept")}},
               "layouts":{"L1":{"slides":[{"id":""},"stray",{"id":"a"}]}}}""",
        )
        assertEquals(1, FreeShowConverter.parse(file).sections.size)
    }

    @Test
    fun `a show whose active layout does not exist uses the one it has`() {
        val file = showFile(
            "wrong-active.show",
            """{"name":"x","settings":{"activeLayout":"gone"},"slides":{"a":${slide("Chorus", "kept")}},
               "layouts":{"L1":{"slides":[{"id":"a"}]}}}""",
        )
        assertEquals(listOf("Chorus"), FreeShowConverter.parse(file).sections.map { it.label })
    }

    @Test
    fun `a show with no layouts at all falls back to the order the slides are stored in`() {
        val file = showFile(
            "nolayout.show",
            """{"name":"x","slides":{"a":${slide("Verse 1", "first")},"b":${slide("Chorus", "second")}}}""",
        )
        assertEquals(listOf("Verse 1", "Chorus"), FreeShowConverter.parse(file).sections.map { it.label })
    }

    @Test
    fun `a layout with an empty slide list falls back the same way`() {
        val file = showFile(
            "emptylayout.show",
            """{"name":"x","settings":{"activeLayout":"L1"},"slides":{"a":${slide("Verse 1", "only")}},
               "layouts":{"L1":{"slides":[]}}}""",
        )
        assertEquals(1, FreeShowConverter.parse(file).sections.size)
    }

    @Test
    fun `a child slide's lines are part of the section that owns it`() {
        val file = showFile(
            "children.show",
            """{"name":"x","settings":{"activeLayout":"L1"},
               "slides":{"a":${slide("Verse 1", "parent line", children = """"b","gone"""")},
                         "b":${slide(null, "child line")}},
               "layouts":{"L1":{"slides":[{"id":"a"}]}}}""",
        )
        assertEquals(listOf("parent line", "child line"), FreeShowConverter.parse(file).sections.single().lines)
    }

    @Test
    fun `an unnamed slide group is a verse`() {
        val file = showFile(
            "nogroup.show",
            """{"name":"x","slides":{"a":${slide(null, "a line")}},"layouts":{}}""",
        )
        assertEquals(listOf("Verse"), FreeShowConverter.parse(file).sections.map { it.label })
    }

    @Test
    fun `a slide with nothing on it is not a section`() {
        val file = showFile(
            "blank-slide.show",
            """{"name":"x","slides":{"a":{"group":"Verse 1","items":[{"type":"text","lines":[]}]},
               "b":${slide("Chorus", "kept")}},"layouts":{}}""",
        )
        assertEquals(listOf("Chorus"), FreeShowConverter.parse(file).sections.map { it.label })
    }

    @Test
    fun `an item that is not text and a line with no text run add nothing`() {
        val file = showFile(
            "odd-items.show",
            """{"name":"x","slides":{"a":{"group":"Verse 1","items":[
                 {"type":"media","src":"x.png"},
                 {"type":"text","lines":[{"align":""},{"align":"","text":[]},
                                         {"align":"","text":[{"value":"  "}]},
                                         {"align":"","text":[{"value":"kept"}]}]}]}},"layouts":{}}""",
        )
        assertEquals(listOf("kept"), FreeShowConverter.parse(file).sections.single().lines)
    }

    @Test
    fun `styled runs within one line are joined back into it`() {
        val file = showFile(
            "runs.show",
            """{"name":"x","slides":{"a":{"group":"Verse 1","items":[{"type":"text","lines":[
                 {"align":"","text":[{"value":"Amazing "},{"value":"grace"}]}]}]}},"layouts":{}}""",
        )
        assertEquals(listOf("Amazing grace"), FreeShowConverter.parse(file).sections.single().lines)
    }
}
