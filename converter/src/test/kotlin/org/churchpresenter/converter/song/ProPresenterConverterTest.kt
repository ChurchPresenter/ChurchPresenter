package org.churchpresenter.converter.song

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Run against real ProPresenter documents rather than hand-written ones. A version 7 file is
 * protocol buffers and a version 6 file nests its lyrics four containers deep in base64 RTF; an
 * approximation of either would pass while the real thing failed.
 */
class ProPresenterConverterTest {

    private val temp: File = Files.createTempDirectory("propresenter-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun sample(name: String): File =
        File(javaClass.classLoader.getResource("propresenter/$name")!!.toURI())

    @Test
    fun `version 4 reads its slides, which it keeps without any grouping`() {
        val song = ProPresenterConverter.parse(sample("v4-be-near.pro4"))

        assertEquals("Be Near", song.title)
        assertEquals("2003", song.copyright)
        assertEquals(6, song.sections.size)
        assertEquals("Be near O God", song.sections.first().lines.first())
    }

    @Test
    fun `version 5 takes its section names from the slide groups`() {
        val song = ProPresenterConverter.parse(sample("v5-be-near.pro5"))

        assertEquals("Be Near", song.title)
        assertEquals("Shane Bernard", song.author)
        assertEquals(
            listOf("Verse 1", "Verse 1", "Bridge", "Chorus", "Post-Chorus", "Verse 2", "Verse 2", "Ending"),
            song.sections.map { it.label },
        )
    }

    @Test
    fun `version 6 reads the base64 RTF held in an NSString rather than an attribute`() {
        val song = ProPresenterConverter.parse(sample("v6-amazing-grace.pro6"))

        assertEquals("Amazing Grace", song.title)
        assertEquals("John Newton", song.author)
        assertEquals(8, song.sections.size)
        assertEquals("Amazing grace how sweet the sound", song.sections.first().lines.first())
        assertEquals(listOf("Verse 1", "Verse 1", "Verse 2", "Verse 2"), song.sections.take(4).map { it.label })
    }

    @Test
    fun `version 7 reads the protobuf document, its CCLI data and its arrangement`() {
        val song = ProPresenterConverter.parse(sample("v7-come-thou-fount.pro"))

        assertEquals("Come Thou Fount", song.title)
        assertEquals("Robert Robinson | John Wyeth", song.author)
        assertEquals("108389", song.ccli)
        assertEquals(listOf("Verse 1", "Verse 2", "Verse 3"), song.sections.map { it.label })
        assertEquals("Here I raise mine Ebenezer, Hither by Thy great help I come;", song.sections[1].lines.first())
    }

    @Test
    fun `version 7 slides come out in arrangement order, not the order the cues are stored in`() {
        val song = ProPresenterConverter.parse(sample("v7-at-the-cross.pro"))

        assertEquals("At the Cross", song.title)
        assertEquals("I know a place", song.sections.first().lines.first())
        assertEquals("At the cross", song.sections[1].lines.first())
    }

    @Test
    fun `the empty template slide every document opens with is dropped`() {
        // Both v7 samples carry a "Blank" group holding one slide with no lyrics in it.
        for (name in listOf("v7-come-thou-fount.pro", "v7-at-the-cross.pro")) {
            val song = ProPresenterConverter.parse(sample(name))
            assertTrue(song.sections.all { it.lines.isNotEmpty() }, name)
            assertFalse(song.sections.any { it.label.equals("Blank", ignoreCase = true) }, name)
        }
    }

    @Test
    fun `a group named only for the song does not become the section name`() {
        // Every lyric slide of this document sits in one group called "Song", which says nothing
        // about which section it is — so the sections are numbered instead.
        val song = ProPresenterConverter.parse(sample("v7-come-thou-fount.pro"))
        assertFalse(song.sections.any { it.label == "Song" }, song.sections.map { it.label }.toString())
    }

    @Test
    fun `the stray punctuation run ProPresenter writes ahead of the lyrics is not sung`() {
        val song = ProPresenterConverter.parse(sample("v7-at-the-cross.pro"))
        assertTrue(song.sections.flatMap { it.lines }.none { it.trim() == "'," }, song.sections.toString())
    }

    @Test
    fun `converting writes a song file with the sections in it`() {
        val out = File(temp, "come-thou-fount.song")
        ProPresenterConverter.convert(sample("v7-come-thou-fount.pro"), out)

        val text = out.readText()
        assertTrue(text.startsWith("---"), text.take(40))
        assertTrue(text.contains("author: Robert Robinson | John Wyeth"), text.take(200))
        assertTrue(text.contains("[Primary]\ntitle: Come Thou Fount"), text.take(300))
        assertTrue(text.contains("[Verse 2]"), text)
    }

    @Test
    fun `every version is reachable through the registry entry the rail selects`() {
        assertEquals(
            listOf("pro", "pro4", "pro5", "pro6"),
            SongFormatConverters.byId("propresenter").extensions,
        )
    }

    @Test
    fun `describe reports what the preview list shows`() {
        val info = ProPresenterFormat.describe(sample("v6-amazing-grace.pro6"))

        assertEquals("Amazing Grace", info.title)
        assertEquals(8, info.sectionCount)
    }
}
