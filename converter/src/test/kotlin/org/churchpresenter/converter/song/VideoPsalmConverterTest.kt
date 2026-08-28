package org.churchpresenter.converter.song

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * VideoPsalm song books. The cases here are the ways the file's verse array differs from the song's
 * structure: choruses stored once per singing, ids that stop being written past the ninth verse, and
 * the end marker the last verse carries.
 */
class VideoPsalmConverterTest {

    private val temp: File = Files.createTempDirectory("converter-videopsalm-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun book(songs: String, title: String = "Песнь Возрождения"): File =
        File(temp, "songbook.json").apply {
            writeText("﻿{Abbreviation:\"PV\",Songs:[$songs],IsCompressed:0,\nText:\"$title\"}", Charsets.UTF_8)
        }

    private fun verse(text: String, tag: Int? = null, id: Int? = null): String {
        val fields = listOfNotNull(tag?.let { "Tag:$it" }, id?.let { "ID:$it" }, "\nText:\"$text\"")
        return "{${fields.joinToString(",")}}"
    }

    @Test
    fun `the book's own title and the songs under it are read`() {
        val file = book("""{ID:1,Alias:"7",Author:"И. Проханов",Guid:"x",Verses:[${verse("Слава")}],
            |Text:"Слава Богу"}""".trimMargin())
        val parsed = VideoPsalmConverter.parse(file)

        assertEquals("Песнь Возрождения", parsed.title)
        assertEquals(1, parsed.songs.size)
        assertEquals("Слава Богу", parsed.songs[0].title)
        assertEquals("И. Проханов", parsed.songs[0].author)
        assertEquals("7", parsed.songs[0].number)
    }

    @Test
    fun `the number printed in the book wins over the song's position in it`() {
        val file = book("""{ID:1,Alias:"104",Verses:[${verse("Line")}],Text:"Song"}""")
        assertEquals("104", VideoPsalmConverter.parse(file).songs[0].number)
    }

    @Test
    fun `a song with no alias falls back to its id`() {
        val file = book("""{ID:12,Verses:[${verse("Line")}],Text:"Song"}""")
        assertEquals("12", VideoPsalmConverter.parse(file).songs[0].number)
    }

    @Test
    fun `a verse's line breaks are its lines`() {
        val file = book("""{Verses:[${verse("Слава Богу,\nпоём Ему")}],Text:"Song"}""")
        val sections = VideoPsalmConverter.parse(file).songs[0].sections

        assertEquals(listOf("Слава Богу,", "поём Ему"), sections[0].lines)
    }

    @Test
    fun `a tag names the section and an id numbers it`() {
        val file = book(
            """{Verses:[${verse("one")},${verse("hook", tag = 1)},${verse("two", id = 2)},
               |${verse("part", tag = 3)}],Text:"Song"}""".trimMargin()
        )
        val labels = VideoPsalmConverter.parse(file).songs[0].sections.map { it.label }

        assertEquals(listOf("Verse 1", "Chorus", "Verse 2", "Bridge"), labels)
    }

    @Test
    fun `a tag the format does not define keeps the section under its own number`() {
        val file = book("""{Verses:[${verse("odd", tag = 42)}],Text:"Song"}""")
        assertEquals(listOf("Section 42"), VideoPsalmConverter.parse(file).songs[0].sections.map { it.label })
    }

    @Test
    fun `a chorus stored once per singing becomes one section`() {
        val file = book(
            """{Verses:[${verse("one")},${verse("hook", tag = 1)},${verse("two", id = 2)},
               |${verse("hook", tag = 1)},${verse("three", id = 3)},
               |${verse("hook", tag = 1, id = 2)}],Text:"Song"}""".trimMargin()
        )
        val sections = VideoPsalmConverter.parse(file).songs[0].sections

        assertEquals(listOf("Verse 1", "Chorus", "Verse 2", "Verse 3"), sections.map { it.label })
    }

    @Test
    fun `verses past the ninth, which the book stops numbering, keep counting up`() {
        val verses = (1..12).joinToString(",") { verse("line $it", id = if (it <= 9) it else null) }
        val file = book("""{Verses:[$verses],Text:"Song"}""")
        val labels = VideoPsalmConverter.parse(file).songs[0].sections.map { it.label }

        assertEquals((1..12).map { "Verse $it" }, labels)
    }

    @Test
    fun `the end marker the last verse carries is not a lyric`() {
        val stars = book("""{Verses:[${verse("last line\n***")}],Text:"Song"}""")
        val arrows = book("""{Verses:[${verse("last line\n \n<><><>")}],Text:"Song"}""")

        assertEquals(listOf("last line"), VideoPsalmConverter.parse(stars).songs[0].sections[0].lines)
        assertEquals(listOf("last line"), VideoPsalmConverter.parse(arrows).songs[0].sections[0].lines)
    }

    @Test
    fun `a repeat marker in the lyrics is left alone`() {
        val file = book("""{Verses:[${verse("||: Благодарность мою :||")}],Text:"Song"}""")
        assertEquals(listOf("||: Благодарность мою :||"), VideoPsalmConverter.parse(file).songs[0].sections[0].lines)
    }

    @Test
    fun `an empty verse is dropped rather than becoming a blank section`() {
        val file = book("""{Verses:[${verse("")},${verse("real")}],Text:"Song"}""")
        val sections = VideoPsalmConverter.parse(file).songs[0].sections

        assertEquals(listOf("Verse 1"), sections.map { it.label })
        assertEquals(listOf("real"), sections[0].lines)
    }

    @Test
    fun `credits stored across several lines are flattened onto one`() {
        val file = book(
            """{Author:"И. Проханов,\nЕ. Gebhardt",Composer:"W. Doane",Copyright:"1905\nPublic domain",
               |CCLI:"1234",Verses:[${verse("line")}],Text:"Song"}""".trimMargin()
        )
        val song = VideoPsalmConverter.parse(file).songs[0]

        assertEquals("И. Проханов, Е. Gebhardt", song.author)
        assertEquals("W. Doane", song.composer)
        assertEquals("1905 Public domain", song.copyright)
        assertEquals("1234", song.ccli)
    }

    @Test
    fun `the sequence is read for the preview`() {
        val file = book("""{Sequence:"V1 C1 V2 C2 ",Verses:[${verse("line")}],Text:"Song"}""")
        assertEquals(
            listOf("Verse 1", "Chorus 1", "Verse 2", "Chorus 2"),
            VideoPsalmConverter.parse(file).songs[0].sequence
        )
    }

    @Test
    fun `every letter the sequence names a section with is understood`() {
        assertEquals(
            listOf("Verse 1", "Chorus 1", "Chorus 2", "Pre-Chorus 1", "Bridge 1", "Tag 1"),
            VideoPsalmConverter.sequenceLabels("V1 C1 R2 P1 B1 T1")
        )
        assertEquals(
            listOf("Intro", "Outro", "Slide 2", "Instrumental", "Other"),
            VideoPsalmConverter.sequenceLabels("E O S2 I N")
        )
    }

    @Test
    fun `a sequence token that is not a section letter is passed through`() {
        assertEquals(listOf("Verse 4", "Coda"), VideoPsalmConverter.sequenceLabels("4, Coda"))
    }

    @Test
    fun `converting writes one file per song into a folder named after the book`() {
        val file = book(
            """{Alias:"1",Author:"И. Проханов",CCLI:"1234",Verses:[${verse("one")},${verse("hook", tag = 1)}],
               |Text:"Слава Богу"},{Alias:"2",Verses:[${verse("two")}],Text:"Вторая"}""".trimMargin()
        )
        val result = VideoPsalmConverter.convert(file, temp)

        assertEquals(emptyList(), result.errors)
        val folder = File(temp, "Песнь Возрождения")
        assertEquals(
            listOf("0001 - Слава Богу.song", "0002 - Вторая.song"),
            result.outputFiles.map { it.name }
        )
        assertTrue(result.outputFiles.all { it.parentFile == folder }, folder.path)

        val written = File(folder, "0001 - Слава Богу.song").readText()
        assertTrue(written.contains("author: И. Проханов"), written)
        assertTrue(written.contains("ccli: 1234"), written)
        assertTrue(written.contains("title: Слава Богу"), written)
        assertTrue(written.contains("[Verse 1]\none"), written)
        assertTrue(written.contains("[Chorus]\nhook"), written)
    }

    @Test
    fun `two songs sharing a title and a number both survive`() {
        val file = book(
            """{Alias:"1",Verses:[${verse("one")}],Text:"Осанна"},
               |{Alias:"1",Verses:[${verse("two")}],Text:"Осанна"}""".trimMargin()
        )
        assertEquals(
            listOf("0001 - Осанна.song", "0001 - Осанна (2).song"),
            VideoPsalmConverter.convert(file, temp).outputFiles.map { it.name }
        )
    }

    @Test
    fun `a book with no songs is reported rather than written as an empty folder`() {
        val file = book("")
        val result = VideoPsalmConverter.convert(file, temp)

        assertEquals(emptyList(), result.outputFiles)
        assertEquals(listOf("No songs in songbook.json"), result.errors)
        assertTrue(temp.listFiles().orEmpty().none { it.isDirectory }, temp.list()?.toList().toString())
    }

    @Test
    fun `a book with no title of its own is filed under the file name`() {
        val file = book("""{Verses:[${verse("line")}],Text:"Song"}""", title = "")
        assertEquals("songbook", VideoPsalmConverter.targetFolderName(file))
    }

    @Test
    fun `a title the filesystem would refuse is made safe`() {
        val file = book("""{Verses:[${verse("line")}],Text:"Song"}""", title = "Book: 1/2")
        assertEquals("Book 1 2", VideoPsalmConverter.targetFolderName(file))
    }

    @Test
    fun `a file that is not a song book at all names no folder and converts nothing`() {
        val file = File(temp, "notes.json").apply { writeText("not a song book", Charsets.UTF_8) }

        assertEquals("notes", VideoPsalmConverter.targetFolderName(file))
        assertEquals(listOf("No songs in notes.json"), VideoPsalmConverter.convert(file, temp).errors)
    }

    @Test
    fun `the rail entry describes the book without converting it`() {
        val file = book(
            """{Alias:"1",Sequence:"V1 C1",Verses:[${verse("one")},${verse("hook", tag = 1)}],Text:"Слава"},
               |{Alias:"2",Verses:[${verse("two")}],Text:"Вторая"}""".trimMargin()
        )
        val info = VideoPsalmFormat.describe(file)

        assertEquals("Песнь Возрождения", info.title)
        assertEquals(2, info.songCount)
        assertEquals(2, info.sectionCount)
        assertEquals(listOf("Verse 1", "Chorus 1"), info.verseOrder)
        assertEquals("Песнь Возрождения", VideoPsalmFormat.outputNameFor(file))
    }

    @Test
    fun `it converts through the registry, which is how the panel reaches it`() {
        val file = book("""{Alias:"1",Verses:[${verse("one")}],Text:"Слава"}""")
        val result = SongFormatConverters.byId("videopsalm").convert(file, temp)

        assertEquals(listOf("0001 - Слава.song"), result.outputFiles.map { it.name })
        assertEquals(listOf("json"), VideoPsalmFormat.extensions)
    }

    @Test
    fun `converting without an output folder is refused rather than scattering a book`() {
        val file = book("""{Alias:"1",Verses:[${verse("one")}],Text:"Слава"}""")
        assertFailsWith<IllegalArgumentException> { VideoPsalmFormat.convert(file, null) }
    }

    @Test
    fun `a book with no title of its own is previewed under the file name`() {
        val file = book("""{Verses:[${verse("line")}],Text:"Song"}""", title = "")
        assertEquals("songbook", VideoPsalmFormat.describe(file).title)
    }
}
