package org.churchpresenter.converter.song

import java.io.File
import java.nio.file.Files
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * ProPresenter documents in the states the four shipped samples do not cover: no CCLI metadata,
 * a slide holding a picture rather than words, a payload that is not decodable, and a version 7
 * file that is not a version 7 file.
 *
 * The group names are the interesting part. They are free text, and a document written from the
 * stock template puts the whole song in one group called `Song` — so a name is only taken as a
 * section name when it means something, or when the document uses more than one. Getting that
 * wrong labels every verse of a song "Song".
 */
class ProPresenterEdgeCasesTest {

    private val temp: File = Files.createTempDirectory("propresenter-edges").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun rtf(vararg lines: String): String {
        val document = """{\rtf1\ansi\ansicpg1252{\fonttbl{\f0\fnil\fcharset0 Arial;}}\pard """ +
            lines.joinToString("""\par """) + "}"
        return Base64.getEncoder().encodeToString(document.toByteArray(Charsets.ISO_8859_1))
    }

    /** A version 5 document: slides live in named groups, each carrying its RTF as an attribute. */
    private fun pro5(name: String, attributes: String = "", groups: String): File =
        File(temp, name).apply {
            writeText(
                """<?xml version="1.0" encoding="utf-8"?>
                   <RVPresentationDocument versionNumber="500" $attributes>
                     <groups>$groups</groups>
                   </RVPresentationDocument>""",
                Charsets.UTF_8,
            )
        }

    private fun group(name: String, vararg slides: String) =
        """<RVSlideGrouping name="$name"><slides>${slides.joinToString("")}</slides></RVSlideGrouping>"""

    private fun slide(payload: String) =
        """<RVDisplaySlide><elements><RVTextElement RTFData="$payload"/></elements></RVDisplaySlide>"""

    // ── Metadata ──────────────────────────────────────────────────────────────

    @Test
    fun `a document with no CCLI title is named after its file`() {
        val file = pro5("Amazing Grace.pro5", groups = group("Verse 1", slide(rtf("Amazing grace"))))
        assertEquals("Amazing Grace", ProPresenterConverter.parse(file).title)
    }

    @Test
    fun `the author is taken from whichever attribute this version writes`() {
        val newer = pro5("a.pro5", attributes = """CCLIAuthor="John Newton"""", groups = group("V", slide(rtf("line"))))
        assertEquals("John Newton", ProPresenterConverter.parse(newer).author)

        val older = pro5("b.pro5", attributes = """artist="Chris Tomlin"""", groups = group("V", slide(rtf("line"))))
        assertEquals("Chris Tomlin", ProPresenterConverter.parse(older).author)

        val blank = pro5(
            "c.pro5",
            attributes = """CCLIAuthor="  " author="Fallback"""",
            groups = group("V", slide(rtf("l"))),
        )
        assertEquals("Fallback", ProPresenterConverter.parse(blank).author)
    }

    @Test
    fun `the copyright and licence number are read from either spelling`() {
        val one = pro5(
            "d.pro5",
            attributes = """CCLICopyrightInfo="1779" CCLILicenseNumber="22025"""",
            groups = group("V", slide(rtf("line"))),
        )
        assertEquals("1779", ProPresenterConverter.parse(one).copyright)
        assertEquals("22025", ProPresenterConverter.parse(one).ccli)

        val two = pro5(
            "e.pro5",
            attributes = """CCLICopyrightYear="1779" CCLISongNumber="22025"""",
            groups = group("V", slide(rtf("line"))),
        )
        assertEquals("1779", ProPresenterConverter.parse(two).copyright)
        assertEquals("22025", ProPresenterConverter.parse(two).ccli)
    }

    @Test
    fun `a document carrying no metadata at all comes back blank rather than missing`() {
        val file = pro5("f.pro5", groups = group("V", slide(rtf("line"))))
        val song = ProPresenterConverter.parse(file)
        assertEquals("", song.author)
        assertEquals("", song.copyright)
        assertEquals("", song.ccli)
    }

    // ── Group names ───────────────────────────────────────────────────────────

    @Test
    fun `a song written entirely into one unhelpful group is numbered instead`() {
        val file = pro5(
            "song-group.pro5",
            groups = group("Song", slide(rtf("first verse")), slide(rtf("second verse"))),
        )
        assertEquals(listOf("Verse 1", "Verse 2"), ProPresenterConverter.parse(file).sections.map { it.label })
    }

    @Test
    fun `a name the app has to translate is a section name even when it is the only one`() {
        val file = pro5("refrain.pro5", groups = group("Refrain", slide(rtf("praise"))))
        assertEquals(listOf("Chorus"), ProPresenterConverter.parse(file).sections.map { it.label })
    }

    @Test
    fun `a single group already spelled the app's own way is numbered, not trusted`() {
        // A name is trusted when it translates to something else, or when the document uses more
        // than one. `Chorus` on its own passes neither test, so it is indistinguishable from the
        // stock template's single group and the slides are numbered instead.
        val file = pro5("chorus.pro5", groups = group("Chorus", slide(rtf("praise"))))
        assertEquals(listOf("Verse 1"), ProPresenterConverter.parse(file).sections.map { it.label })
    }

    @Test
    fun `free-text names are kept when the document uses more than one`() {
        val file = pro5(
            "antiphon.pro5",
            groups = group("Antiphon", slide(rtf("first"))) + group("Cantor", slide(rtf("second"))),
        )
        assertEquals(listOf("Antiphon", "Cantor"), ProPresenterConverter.parse(file).sections.map { it.label })
    }

    @Test
    fun `the stock Blank group never names a section`() {
        val file = pro5(
            "blank.pro5",
            groups = group("Blank", slide(rtf("first"))) + group("Blank", slide(rtf("second"))),
        )
        assertEquals(listOf("Verse 1", "Verse 2"), ProPresenterConverter.parse(file).sections.map { it.label })
    }

    @Test
    fun `a version 4 document takes each slide's own label`() {
        val file = File(temp, "v4.pro4").apply {
            writeText(
                """<?xml version="1.0" encoding="utf-8"?>
                   <RVPresentationDocument versionNumber="400">
                     <slides>
                       <RVDisplaySlide label="Verse 1"><elements>
                         <RVTextElement RTFData="${rtf("first line")}"/></elements></RVDisplaySlide>
                       <RVDisplaySlide label="Chorus"><elements>
                         <RVTextElement RTFData="${rtf("praise")}"/></elements></RVDisplaySlide>
                     </slides>
                   </RVPresentationDocument>""",
                Charsets.UTF_8,
            )
        }
        assertEquals(listOf("Verse 1", "Chorus"), ProPresenterConverter.parse(file).sections.map { it.label })
    }

    // ── Slides that are not lyrics ────────────────────────────────────────────

    @Test
    fun `a slide holding a picture rather than words is left out`() {
        val picture = """<RVDisplaySlide><elements>
            <RVImageElement source="background.jpg"/></elements></RVDisplaySlide>"""
        val file = pro5("picture.pro5", groups = group("Verse 1", picture, slide(rtf("the only line"))))

        assertEquals(listOf(listOf("the only line")), ProPresenterConverter.parse(file).sections.map { it.lines })
    }

    @Test
    fun `a payload that is not decodable costs that slide, not the song`() {
        val file = pro5(
            "badpayload.pro5",
            // A single character is not a decodable payload: base64 needs at least two.
            groups = group("Verse 1", slide("A"), slide(rtf("the only line"))),
        )
        assertEquals(listOf(listOf("the only line")), ProPresenterConverter.parse(file).sections.map { it.lines })
    }

    @Test
    fun `a punctuation-only run ahead of the text is not a lyric line`() {
        // Some documents carry a stray `',` run on every slide; it is in the file, not a misread.
        val file = pro5("punctuation.pro5", groups = group("Verse 1", slide(rtf("',", "Amazing grace"))))
        assertEquals(listOf("Amazing grace"), ProPresenterConverter.parse(file).sections.single().lines)
    }

    @Test
    fun `a document with no lyrics anywhere yields no sections`() {
        val file = pro5("empty.pro5", groups = group("Verse 1", slide(rtf("   "))))
        assertTrue(ProPresenterConverter.parse(file).sections.isEmpty())
    }

    // ── What the file is ──────────────────────────────────────────────────────

    @Test
    fun `an XML document that is not a presentation is refused by its root element`() {
        val file = File(temp, "playlist.pro5").apply {
            writeText("""<?xml version="1.0"?><RVPlaylistDocument/>""", Charsets.UTF_8)
        }
        val error = assertFailsWith<IllegalArgumentException> { ProPresenterConverter.parse(file) }
        assertTrue(error.message!!.contains("RVPlaylistDocument"), error.message!!)
    }

    @Test
    fun `a pro file that is not a version 7 document is refused`() {
        val file = File(temp, "junk.pro").apply { writeBytes(ByteArray(64) { 0xff.toByte() }) }
        val error = assertFailsWith<IllegalArgumentException> { ProPresenterConverter.parse(file) }
        assertTrue(error.message!!.contains("ProPresenter 7"), error.message!!)
    }

    // ── Writing the song out ──────────────────────────────────────────────────

    @Test
    fun `converting into a folder that does not exist yet creates it`() {
        val file = pro5("write.pro5", attributes = """CCLISongTitle="Amazing Grace"""",
            groups = group("Verse 1", slide(rtf("Amazing grace"))))
        val out = File(temp, "nested/deeper/Amazing Grace.song")

        ProPresenterConverter.convert(file, out)

        assertTrue(out.isFile)
        assertTrue(out.readText().contains("Amazing grace"))
    }
}
