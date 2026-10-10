package org.churchpresenter.converter.song

import org.churchpresenter.converter.song.ProtoWriter.bytes
import org.churchpresenter.converter.song.ProtoWriter.concat
import org.churchpresenter.converter.song.ProtoWriter.message
import org.churchpresenter.converter.song.ProtoWriter.number
import org.churchpresenter.converter.song.ProtoWriter.string
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProPresenter7SyntheticTest {

    private val temp: File = Files.createTempDirectory("propresenter7-synthetic").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun rtf(vararg lines: String): ByteArray =
        ("""{\rtf1\ansi{\fonttbl{\f0 Arial;}}\pard """ + lines.joinToString("""\par """) + "}")
            .toByteArray(Charsets.ISO_8859_1)

    private fun textElement(text: ByteArray) =
        message(1, message(1, message(13, bytes(5, text))))

    private fun cue(id: String?, vararg actions: ByteArray): ByteArray = concat(
        if (id != null) message(1, string(1, id)) else ByteArray(0),
        *actions,
    )

    private fun slideAction(vararg elements: ByteArray) =
        message(10, message(23, message(2, message(1, *elements))))

    private fun cueField(content: ByteArray) = bytes(13, content)

    private fun group(name: String?, vararg ids: String?): ByteArray = message(
        12,
        if (name != null) message(1, string(2, name)) else ByteArray(0),
        *ids.map { if (it != null) message(2, string(1, it)) else message(2, number(2, 1)) }.toTypedArray(),
    )

    private fun write(name: String, vararg parts: ByteArray): File =
        File(temp, name).apply { writeBytes(concat(*parts)) }

    @Test
    fun `without CCLI data the presentation name is the title and the metadata is blank`() {
        val file = write(
            "x.pro",
            string(3, "  Presentation Name "),
            cueField(cue("a", slideAction(textElement(rtf("first line"))))),
        )
        val song = ProPresenterConverter.parse(file)
        assertEquals("Presentation Name", song.title)
        assertEquals("", song.author)
        assertEquals("", song.copyright)
        assertEquals("", song.ccli)
        assertEquals(listOf(listOf("first line")), song.sections.map { it.lines })
    }

    @Test
    fun `without any name the file name is the title`() {
        val file = write("Fallback Title.pro", string(3, ""), cueField(cue("a", slideAction(textElement(rtf("x"))))))
        assertEquals("Fallback Title", ProPresenterConverter.parse(file).title)
    }

    @Test
    fun `CCLI data supplies the title, the artist credits when the author is blank, publisher and number`() {
        val ccli = message(
            14,
            string(1, "  "),
            string(2, "Artist"),
            string(3, "Song Title"),
            string(4, " Pub "),
            number(6, 1234),
        )
        val file = write("y.pro", string(3, "Ignored"), ccli, cueField(cue("a", slideAction(textElement(rtf("x"))))))
        val song = ProPresenterConverter.parse(file)
        assertEquals("Song Title", song.title)
        assertEquals("Artist", song.author)
        assertEquals("Pub", song.copyright)
        assertEquals("1234", song.ccli)
    }

    @Test
    fun `a blank CCLI title and a zero song number fall back`() {
        val ccli = message(14, string(3, " "), number(6, 0))
        val file = write("z.pro", string(3, "Name"), ccli, cueField(cue("a", slideAction(textElement(rtf("x"))))))
        val song = ProPresenterConverter.parse(file)
        assertEquals("Name", song.title)
        assertEquals("", song.ccli)
        assertEquals("", song.author)
    }

    @Test
    fun `the arrangement orders the cues and skips identifiers it cannot read`() {
        val file = write(
            "arr.pro",
            cueField(cue("one", slideAction(textElement(rtf("first"))))),
            cueField(cue("two", slideAction(textElement(rtf("second"))))),
            cueField(cue(null, slideAction(textElement(rtf("orphan"))))),
            group("Chorus", "two", null),
            group(null, "one", "missing"),
        )
        val song = ProPresenterConverter.parse(file)
        assertEquals(listOf(listOf("second"), listOf("first")), song.sections.map { it.lines })
    }

    @Test
    fun `a cue's text comes from the first action and element that carries any`() {
        val emptyText = textElement(rtf("   "))
        val noGraphics = message(1, string(9, "shape"))
        val noText = message(1, message(1, string(9, "no text")))
        val nonSlideAction = message(10, string(9, "audio"))
        val file = write(
            "cue.pro",
            cueField(
                cue(
                    "a",
                    nonSlideAction,
                    slideAction(noGraphics, noText, emptyText),
                    slideAction(textElement(rtf("found it"))),
                ),
            ),
            cueField(cue("b", nonSlideAction)),
        )
        val song = ProPresenterConverter.parse(file)
        assertEquals(listOf(listOf("found it")), song.sections.map { it.lines })
    }

    @Test
    fun `cues are read in file order when there is no arrangement`() {
        val file = write(
            "order.pro",
            cueField(cue("a", slideAction(textElement(rtf("alpha"))))),
            cueField(cue("b", slideAction(textElement(rtf("beta"))))),
        )
        val song = ProPresenterConverter.parse(file)
        assertEquals(listOf("alpha", "beta"), song.sections.map { it.lines.single() })
        assertTrue(song.sections.all { it.label.startsWith("Verse") })
    }
}
