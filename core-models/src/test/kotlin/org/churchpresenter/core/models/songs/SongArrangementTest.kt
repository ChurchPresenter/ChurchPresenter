package org.churchpresenter.core.models.songs

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongArrangementTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun createTempDir() {
        tempDir = Files.createTempDirectory("cp-arrangement-test").toFile()
    }

    @AfterTest
    fun deleteTempDir() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `no arrangement keys give the defaults`() {
        assertEquals(SongArrangement(), songArrangementFrom(emptyMap()))
    }

    @Test
    fun `the order is split on bars and blank entries dropped`() {
        val arrangement = songArrangementFrom(mapOf(SONG_ORDER_KEY to "[Verse 1] |  {Chorus} || [Verse 2]"))
        assertEquals(listOf("[Verse 1]", "{Chorus}", "[Verse 2]"), arrangement.sectionOrder)
    }

    @Test
    fun `notes read back with their line breaks and backslashes`() {
        val notes = "Capo 2\nPiano intro \\ 4 bars"
        val written = escapeNotes(notes)
        assertEquals("Capo 2\\nPiano intro \\\\ 4 bars", written)
        assertEquals(notes, songArrangementFrom(mapOf(SONG_NOTES_KEY to written)).notes)
    }

    @Test
    fun `a windows line break in notes is written as one break`() {
        assertEquals("a\\nb", escapeNotes("a\r\nb"))
    }

    @Test
    fun `a lone backslash in notes is kept as written`() {
        assertEquals("a\\tb\\", unescapeNotes("a\\tb\\"))
    }

    @Test
    fun `cues read back from the header`() {
        val cues = mapOf("[Verse 1]" to "Piano only", "{Chorus}#2" to "Key change ↑")
        val fields = songArrangementFields(SongItem(number = "1", title = "T", sectionNotes = cues)).toMap()
        val written = assertNotNull(fields[SONG_CUES_KEY])
        assertEquals(cues, songArrangementFrom(mapOf(SONG_CUES_KEY to written)).sectionNotes)
    }

    @Test
    fun `blank cues are not written and kept ones are trimmed`() {
        val song = SongItem(
            number = "1",
            title = "T",
            sectionNotes = mapOf("[Verse 1]" to "  Choir in ", "[Verse 2]" to " "),
        )
        val written = songArrangementFields(song).toMap().getValue(SONG_CUES_KEY)
        assertEquals(mapOf("[Verse 1]" to "Choir in"), decodeCues(written))
    }

    @Test
    fun `a song without order notes or cues writes no arrangement line`() {
        assertTrue(songArrangementFields(SongItem(number = "1", title = "T", notes = "  ")).isEmpty())
    }

    @Test
    fun `the arrangement lines come in a fixed order`() {
        val song = SongItem(
            number = "1",
            title = "T",
            sectionOrder = listOf("[Verse 1]", "{Chorus}"),
            notes = "Slow",
            sectionNotes = mapOf("[Verse 1]" to "Piano"),
        )
        val fields = songArrangementFields(song)
        assertEquals(listOf(SONG_ORDER_KEY, SONG_NOTES_KEY, SONG_CUES_KEY), fields.map { it.first })
        assertEquals("[Verse 1] | {Chorus}", fields.first().second)
    }

    @Test
    fun `cues that are not a json object read as none`() {
        assertEquals(emptyMap(), decodeCues(""))
        assertEquals(emptyMap(), decodeCues("Verse 1=Piano"))
        assertEquals(emptyMap(), decodeCues("[\"a\"]"))
        assertEquals(emptyMap(), decodeCues("{\"a\": 1"))
    }

    @Test
    fun `order notes and cues survive a save and a reload`() {
        val song = SongItem(
            number = "",
            title = "Amazing Grace",
            lyrics = listOf("[Verse 1]", "Amazing grace", "{Chorus}", "Praise"),
            sectionOrder = listOf("[Verse 1]", "{Chorus}", "[Verse 1]"),
            notes = "Capo 2\nLast verse a cappella",
            sectionNotes = mapOf("[Verse 1]" to "Piano only", "{Chorus}" to "Full band: \"loud\""),
        )
        val file = File(tempDir, "Amazing Grace.song")
        val parser = SongFileParser()
        parser.writeSongFile(song, file.path)
        val read = assertNotNull(parser.parseSongFile(file.path))
        assertEquals(song.sectionOrder, read.sectionOrder)
        assertEquals(song.notes, read.notes)
        assertEquals(song.sectionNotes, read.sectionNotes)
        assertTrue(read.extraHeader.isEmpty())
    }

    @Test
    fun `section keys number repeated and untitled headers`() {
        assertEquals(
            listOf("#1", "[Verse 1]", "{Chorus}", "{Chorus}#2", "#5"),
            sectionKeysOf(listOf(null, "[Verse 1]", " {Chorus} ", "{Chorus}", "  ")),
        )
    }

    @Test
    fun `the next sung section steps over skipped ones`() {
        val sections = listOf(
            LyricSection(header = "a"),
            LyricSection(header = "b", skipped = true),
            LyricSection(header = "c"),
        )
        assertEquals("c", nextSungSection(sections, 0)?.header)
        assertEquals("a", nextSungSection(sections, -3)?.header)
        assertNull(nextSungSection(sections, 2))
    }

    @Test
    fun `an order key reads as its bare name`() {
        assertEquals("Verse 1", sectionOrderLabel("[Verse 1]"))
        assertEquals("Chorus", sectionOrderLabel("{Chorus}#2"))
        assertEquals("", sectionOrderLabel("#3"))
    }

    @Test
    fun `an order key abbreviates to its first letter and its number`() {
        assertEquals("V1", sectionOrderAbbreviation("[Verse 1]"))
        assertEquals("C", sectionOrderAbbreviation("{Chorus}#2"))
        assertEquals("B2", sectionOrderAbbreviation("[bridge 2]"))
        assertEquals("К1", sectionOrderAbbreviation("[1 куплет]"))
        assertEquals("12", sectionOrderAbbreviation("[12]"))
        assertEquals("#3", sectionOrderAbbreviation("#3"))
    }
}
