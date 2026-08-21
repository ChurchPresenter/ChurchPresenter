package org.churchpresenter.converter.song

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * EasySlides exports: one XML file that is a whole library. The cases that matter are the ones the
 * format does not spell out — `[region N]` lines that are layout and not lyrics, a `<Contents>` with
 * no markers at all, and a `<Sequence>` whose letters do not mean what they look like.
 */
class EasySlidesConverterTest {

    private val temp: File = Files.createTempDirectory("converter-easyslides-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun item(title: String, contents: String, number: String = "", sequence: String = ""): String =
        "<Item><Title1>$title</Title1><SongNumber>$number</SongNumber><Writer>John Newton</Writer>" +
            "<Copyright>Public Domain</Copyright><Sequence>$sequence</Sequence>" +
            "<Contents>$contents</Contents></Item>"

    private fun exportFile(vararg items: String): File =
        File(temp, "export.xml").apply {
            writeText("<EasiSlides>${items.joinToString("")}</EasiSlides>", Charsets.UTF_8)
        }

    @Test
    fun `one file holds the whole library`() {
        val file = exportFile(item("Amazing Grace", "[V1]\nLine"), item("Be Thou My Vision", "[V1]\nLine"))

        assertEquals(listOf("Amazing Grace", "Be Thou My Vision"), EasySlidesConverter.parse(file).map { it.title })
    }

    @Test
    fun `region lines are layout instructions and never reach the lyrics`() {
        val file = exportFile(item("Grace", "[V1]\nAmazing grace\n[region 2]\n[C]\nPraise"))

        val sections = EasySlidesConverter.parse(file).single().sections

        assertEquals(listOf("Verse 1", "Chorus"), sections.map { it.label })
        assertEquals(listOf("Amazing grace"), sections.first().lines)
    }

    @Test
    fun `contents with no markers is read as blank-line separated verses`() {
        val file = exportFile(item("Grace", "Amazing grace\nhow sweet\n\nTwas grace\nthat taught"))

        val sections = EasySlidesConverter.parse(file).single().sections

        assertEquals(listOf("Verse 1", "Verse 2"), sections.map { it.label })
        assertEquals(listOf("Amazing grace", "how sweet"), sections.first().lines)
        assertEquals(listOf("Twas grace", "that taught"), sections.last().lines)
    }

    @Test
    fun `a marker that is only a number is the verse EasySlides means by it`() {
        val contents = "[1]\nAmazing grace\n[2]\nTwas grace\n[chorus]\nPraise\n[chorus 2]\nPraise again"
        val file = exportFile(item("Grace", contents))

        val sections = EasySlidesConverter.parse(file).single().sections

        assertEquals(listOf("Verse 1", "Verse 2", "Chorus", "Chorus 2"), sections.map { it.label })
    }

    @Test
    fun `sequence letters name sections, and t is the second chorus rather than a tag`() {
        assertEquals(
            listOf("Verse 1", "Chorus", "Verse 2", "Chorus 2"),
            EasySlidesConverter.sequenceLabels("1,c,2,t"),
        )
        assertEquals(
            listOf("Pre-Chorus", "Pre-Chorus 2", "Bridge", "Bridge 2", "Ending"),
            EasySlidesConverter.sequenceLabels("p q b w e"),
        )
    }

    @Test
    fun `converting writes one file per song, numbered by the library's own numbering`() {
        val file = exportFile(item("Amazing Grace", "[V1]\nLine", number = "12"), item("Be Thou", "[V1]\nLine"))
        val out = File(temp, "out")

        val result = EasySlidesConverter.convert(file, out)

        assertEquals(listOf("0012 - Amazing Grace.song", "Be Thou.song"), result.outputFiles.map { it.name })
        assertTrue(result.errors.isEmpty(), result.errors.toString())
        assertTrue(result.outputFiles.first().readText().contains("author: John Newton"))
    }

    @Test
    fun `two songs sharing a title both survive instead of one overwriting the other`() {
        val file = exportFile(item("Grace", "[V1]\nOne"), item("Grace", "[V1]\nTwo"))
        val out = File(temp, "out-dupes")

        val written = EasySlidesConverter.convert(file, out).outputFiles

        assertEquals(listOf("Grace.song", "Grace (2).song"), written.map { it.name })
        assertTrue(written.all { it.exists() })
    }

    @Test
    fun `a file with no Item elements is reported rather than written as an empty library`() {
        val file = File(temp, "empty.xml").apply { writeText("<EasiSlides></EasiSlides>", Charsets.UTF_8) }

        val result = EasySlidesConverter.convert(file, File(temp, "out-empty"))

        assertTrue(result.outputFiles.isEmpty())
        assertEquals(1, result.errors.size, result.errors.toString())
    }
}
