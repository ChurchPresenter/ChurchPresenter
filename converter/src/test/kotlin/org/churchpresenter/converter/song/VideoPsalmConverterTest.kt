package org.churchpresenter.converter.song

import java.io.File
import java.nio.file.Files
import org.churchpresenter.core.models.songs.SongFileParser
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * VideoPsalm song books. The cases here are the ways the file's verse array differs from the song's
 * structure: choruses stored once per singing, ids that stop being written past the ninth verse, the
 * end marker the last verse carries, and the color markup a bilingual book styles it with.
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
        val file =
            book("""{ID:1,Alias:"7",Author:"И. Проханов",Guid:"x",Verses:[${verse("Слава")}],
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
    fun `the end marker is still dropped when the book wrote it inside its own colour`() {
        val file = book("""{Verses:[${verse("last line\n<cFF00D800>***</c>")}],Text:"Song"}""")
        assertEquals(listOf("last line"), VideoPsalmConverter.parse(file).songs[0].sections[0].lines)
    }

    @Test
    fun `the colour a bilingual book gives its second language is not imported`() {
        val text = "We praise Thee, O God!\n<cFF00D800>Nous Te louons, Dieu!\nPour Ton Fils bien-aimé,</c>"
        val file = book("""{Verses:[${verse(text)}],Text:"Song"}""")

        assertEquals(
            listOf("We praise Thee, O God!", "Nous Te louons, Dieu!", "Pour Ton Fils bien-aimé,"),
            VideoPsalmConverter.parse(file).songs[0].sections[0].lines,
        )
    }

    @Test
    fun `colour markup in a title or an author is not imported either`() {
        val file =
            book("""{Author:"<cFF64FF64>W. Kethe</c>",Verses:[${verse("Line")}],
            |Text:"<cFF64FF64>All People That on Earth Do Dwell</c>"}""".trimMargin())
        val song = VideoPsalmConverter.parse(file).songs[0]

        assertEquals("All People That on Earth Do Dwell", song.title)
        assertEquals("W. Kethe", song.author)
    }

    @Test
    fun `the rule between two languages splits the verse into the song's two halves`() {
        val text = "We praise Thee, O God!\nFor the Son of Thy love\n----------------\n" +
            "Nous Te louons, Dieu!\nPour Ton Fils bien-aimé,"
        val file = book("""{Verses:[${verse(text)}],Text:"Song"}""")
        val song = VideoPsalmConverter.parse(file).songs[0]

        assertEquals(listOf("We praise Thee, O God!", "For the Son of Thy love"), song.sections[0].lines)
        assertEquals(listOf("Nous Te louons, Dieu!", "Pour Ton Fils bien-aimé,"), song.secondarySections[0].lines)
        assertEquals(song.sections[0].label, song.secondarySections[0].label, "both halves under one label")
    }

    @Test
    fun `a book with one language has no second half at all`() {
        val file = book("""{Verses:[${verse("Amazing grace")}],Text:"Song"}""")
        assertTrue(VideoPsalmConverter.parse(file).songs[0].secondarySections.isEmpty())
    }

    @Test
    fun `a verse the book never translated holds its place in the second half`() {
        val translated = "Second verse\n----\nDeuxième couplet"
        val file = book("""{Verses:[${verse("First verse")},${verse(translated)}],Text:"Song"}""")
        val song = VideoPsalmConverter.parse(file).songs[0]

        assertEquals(2, song.secondarySections.size, "the app pairs the halves by position")
        assertEquals(emptyList(), song.secondarySections[0].lines)
        assertEquals(listOf("Deuxième couplet"), song.secondarySections[1].lines)
    }

    @Test
    fun `both languages are written into the one song file`() {
        val text = "English line\n----------------\nLigne française"
        val file = book("""{Verses:[${verse(text)}],Text:"Song"}""")
        val out = File(temp, "out").apply { mkdirs() }
        VideoPsalmConverter.convert(file, out)
        val written = out.walkTopDown().first { it.extension == "song" }.readText()

        assertTrue(written.contains("[Secondary]"), "the translation needs its own half")
        assertTrue(
            written.indexOf("English line") < written.indexOf("[Secondary]"),
            "the verse belongs in the primary half",
        )
        assertTrue(written.indexOf("Ligne française") > written.indexOf("[Secondary]"))
        assertFalse(written.contains("----"), "the rule between the two is not a lyric")
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

    private fun mark(tag: Int? = null, id: Int? = null): String =
        "{${listOfNotNull(tag?.let { "Tag:$it" }, id?.let { "ID:$it" }).joinToString(",")}}"

    private fun lines(first: Int, vararg text: String): String =
        text.mapIndexed { index, line -> verse(line, id = first + index) }.joinToString(",")

    @Test
    fun `lines brought in from EasyWorship are put back together under the entry that names them`() {
        val file = book(
            """{Verses:[${mark(tag = 4, id = 0)},${verse("LOOK AND LIVE")},${mark()},
               |${lines(2, "I've a message from the Lord", "Halleluyah", "'Tis recorded in His word", "Halleluyah")},
               |${mark(tag = 1, id = 0)},${lines(8, "Look and live", "My brother, live")},
               |${mark(id = 2)},${lines(3, "A message full of love", "Halleluyah")}],
               |Text:"LOOK AND LIVE"}""".trimMargin()
        )
        val sections = VideoPsalmConverter.parse(file).songs[0].sections

        assertEquals(listOf("Tag", "Verse 1", "Chorus", "Verse 2"), sections.map { it.label })
        assertEquals(listOf("LOOK AND LIVE"), sections[0].lines)
        assertEquals(
            listOf("I've a message from the Lord", "Halleluyah", "'Tis recorded in His word", "Halleluyah"),
            sections[1].lines,
        )
        assertEquals(listOf("Look and live", "My brother, live"), sections[2].lines)
        assertEquals(listOf("A message full of love", "Halleluyah"), sections[3].lines)
    }

    @Test
    fun `a chorus written out line by line every time it is sung becomes one section`() {
        val file = book(
            """{Verses:[${mark()},${lines(2, "first", "verse")},${mark(tag = 1)},${lines(4, "the", "chorus")},
               |${mark(id = 2)},${lines(3, "second", "verse")},${mark(tag = 1)},${lines(5, "the", "chorus")}],
               |Text:"Song"}""".trimMargin()
        )
        val sections = VideoPsalmConverter.parse(file).songs[0].sections

        assertEquals(listOf("Verse 1", "Chorus", "Verse 2"), sections.map { it.label })
        assertEquals(listOf("the", "chorus"), sections[1].lines)
    }

    @Test
    fun `a slide of several lines is continued by the line its count numbers on to`() {
        val file = book(
            """{Verses:[${mark()},${verse("one\ntwo\nthree", id = 2)},${lines(5, "four", "five")}],
               |Text:"Song"}""".trimMargin()
        )
        val sections = VideoPsalmConverter.parse(file).songs[0].sections

        assertEquals(listOf("Verse 1"), sections.map { it.label })
        assertEquals(listOf("one", "two", "three", "four", "five"), sections[0].lines)
    }

    @Test
    fun `a whole verse followed by the next verse number stays a section of its own`() {
        val file = book(
            """{Verses:[${mark(tag = 4)},${lines(2, "a", "b")},
               |${mark(tag = 4)},${verse("one\ntwo\nthree\nfour", id = 5)},${verse("next\nverse", id = 6)}],
               |Text:"Song"}""".trimMargin()
        )
        val sections = VideoPsalmConverter.parse(file).songs[0].sections

        assertEquals(listOf("Tag 1", "Tag 2", "Verse 6"), sections.map { it.label })
        assertEquals(listOf("next", "verse"), sections[2].lines)
    }

    @Test
    fun `a song VideoPsalm wrote itself keeps each verse after an empty slide`() {
        val file = book(
            """{Verses:[${mark(tag = 4, id = 0)},${verse("TITLE", tag = 9, id = 0)},${verse("one\nverse")},
               |${verse("the\nchorus", tag = 1, id = 0)},${verse("two\nverse", id = 2)}],Text:"Song"}""".trimMargin()
        )
        val labels = VideoPsalmConverter.parse(file).songs[0].sections.map { it.label }

        assertEquals(listOf("Other", "Verse 1", "Chorus", "Verse 2"), labels)
    }

    @Test
    fun `an id of 0 is no number, as VideoPsalm shows it`() {
        val file = book("""{Verses:[${verse("one")},${verse("hook", tag = 1, id = 0)}],Text:"Song"}""")
        assertEquals(listOf("Verse 1", "Chorus"), VideoPsalmConverter.parse(file).songs[0].sections.map { it.label })
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
    fun `a printed number with the book's name in front numbers the song by its digits`() {
        val file = book(
            """{ID:1,Alias:"WP Scores 001",Verses:[${verse("one")}],Text:"Watch and pray"},
               |{ID:236,Alias:"WP Scores 236",Verses:[${verse("two")}],Text:"Faith of our fathers"},
               |{ID:237,Alias:"WP Scores 236",Verses:[${verse("three")}],Text:"Faith of our mothers"},
               |{ID:238,Alias:"WP Scores 237",Verses:[${verse("four")}],Text:"Come to Jesus"}""".trimMargin(),
            title = "WP Scores",
        )
        val result = VideoPsalmConverter.convert(file, temp)

        assertEquals(
            listOf(
                "0001 - Watch and pray.song",
                "0236 - Faith of our fathers.song",
                "0236 - Faith of our mothers.song",
                "0237 - Come to Jesus.song",
            ),
            result.outputFiles.map { it.name },
        )
        assertTrue(result.outputFiles.none { it.readText().contains("alias:") })
    }

    @Test
    fun `a printed number that is not a number becomes the alias, and the position the number`() {
        val file = book("""{ID:3,Alias:"12a",Verses:[${verse("one")}],Text:"Song"}""")
        val written = VideoPsalmConverter.convert(file, temp).outputFiles.single()

        assertEquals("0003 - Song.song", written.name)
        assertTrue(written.readText().contains("alias: 12a"), written.readText())
    }

    @Test
    fun `the app reads back the number and alias the converter wrote`() {
        val file = book(
            """{ID:1,Alias:"WP Scores 001",Verses:[${verse("one")}],Text:"First"},
               |{ID:2,Alias:"12a",Verses:[${verse("two")}],Text:"Second"},
               |{ID:3,Alias:"104",Verses:[${verse("three")}],Text:"Third"}""".trimMargin()
        )
        val parser = SongFileParser()
        val songs = VideoPsalmConverter.convert(file, temp).outputFiles
            .map { assertNotNull(parser.parseSongFile(it.absolutePath)) }

        assertEquals(listOf("1" to "", "2" to "12a", "104" to ""), songs.map { it.number.trimStart('0') to it.alias })
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
