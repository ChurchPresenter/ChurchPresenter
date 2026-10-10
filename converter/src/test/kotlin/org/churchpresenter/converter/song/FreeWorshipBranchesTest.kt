package org.churchpresenter.converter.song

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FreeWorshipBranchesTest {

    private val temp: File = Files.createTempDirectory("freeworship-branches").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun bytes(name: String, vararg data: Int): File =
        File(temp, name).apply { writeBytes(ByteArray(data.size) { data[it].toByte() }) }

    private fun xml(name: String, body: String): File =
        File(temp, name).apply { writeText("<?xml version=\"1.0\"?><song>$body</song>", Charsets.UTF_8) }

    @Test
    fun `bytes that only start like a byte order mark are read as UTF-8`() {
        assertEquals("A", FreeWorshipConverter.readXml(bytes("one", 0x41)))
        assertTrue(FreeWorshipConverter.readXml(bytes("ff", 0xFF, 0x41)).endsWith("A"))
        assertTrue(FreeWorshipConverter.readXml(bytes("fe", 0xFE, 0x41)).endsWith("A"))
        assertTrue(FreeWorshipConverter.readXml(bytes("ef", 0xEF, 0x41, 0x42)).endsWith("AB"))
        assertTrue(FreeWorshipConverter.readXml(bytes("efbb", 0xEF, 0xBB, 0x41, 0x42)).endsWith("AB"))
        assertEquals("", FreeWorshipConverter.readXml(bytes("empty")))
    }

    @Test
    fun `an empty properties block leaves every field blank`() {
        val song = FreeWorshipConverter.parse(
            xml("p.xml", "<properties/><lyrics><verse name=\"v1\"><lines>line</lines></verse></lyrics>"),
        )
        assertEquals("", song.title)
        assertEquals("", song.author)
        assertEquals("", song.copyright)
        assertTrue(song.verseOrder.isEmpty())
        assertEquals(1, song.sections.size)
    }

    @Test
    fun `a blank verse order is no order at all`() {
        val song = FreeWorshipConverter.parse(
            xml(
                "o.xml",
                "<properties><verseOrder>   </verseOrder><titles><title> </title></titles></properties>" +
                    "<lyrics><verse name=\"c\"><lines>chorus</lines></verse>" +
                    "<verse name=\"v1\"><lines>verse</lines></verse></lyrics>",
            ),
        )
        assertTrue(song.verseOrder.isEmpty())
        assertEquals("", song.title)
        assertEquals(listOf("chorus", "verse"), song.sections.map { it.text })
    }

    @Test
    fun `an unknown inline element keeps the words inside it`() {
        val song = FreeWorshipConverter.parse(
            xml(
                "t.xml",
                "<lyrics><verse name=\"v1\"><lines>Amazing <tag name=\"b\">grace</tag> found</lines></verse></lyrics>",
            ),
        )
        assertEquals("Amazing grace found", song.sections.single().text)
    }
}
