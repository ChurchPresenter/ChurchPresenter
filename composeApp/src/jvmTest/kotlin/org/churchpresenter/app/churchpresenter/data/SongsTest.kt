package org.churchpresenter.app.churchpresenter.data

import org.churchpresenter.app.churchpresenter.utils.Constants
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Loading a legacy `.sps` songbook, and the lyric layout that comes out of it.
 *
 * The interesting half is [Songs.parseLyrics]. A `.sps` file stores a song as one field with two
 * markers in it — `@$` between sections and `@%` between lines — and the loader turns that into the
 * running order the operator actually steps through, which is not the order in the file: the chorus
 * is lifted out and repeated after every verse, with blank lines separating the sections. Whether a
 * section counts as a verse or a chorus is decided by its first line, in English or Russian.
 *
 * That reordering is invisible until a service, when the chorus either comes round after each verse
 * or does not. It is exercised here through the real file-loading path rather than in isolation.
 */
class SongsTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-songs-test").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    /** One song row in the `.sps` line format: number, title, category, key, author, composer, lyrics. */
    private fun row(
        number: String,
        title: String,
        lyrics: String = "",
        category: String = "1",
        key: String = "G",
        author: String = "",
        composer: String = "",
    ) = listOf(number, title, category, key, author, composer, lyrics).joinToString("#\$#")

    /** Writes a `.sps` file whose second header line names the songbook, and loads it. */
    private fun load(vararg rows: String, songbook: String = "Hymnal", name: String = "library.sps"): Songs {
        val file = File(dir, name)
        file.writeText("##SongPresenter\n##$songbook\n" + rows.joinToString("\n"), Charsets.UTF_8)
        return Songs().also { it.loadFromSps(file.absolutePath) }
    }

    /** Lyric sections joined with the markers the format uses. */
    private fun lyrics(vararg sections: List<String>) =
        sections.joinToString("@\$") { it.joinToString("@%") }

    // ── Loading a songbook ──────────────────────────────────────────────────────

    @Test
    fun `songs are read with their details`() {
        val songs = load(row("1", "Amazing Grace", author = "Newton", composer = "Excell", key = "G"))

        val song = songs.getSongs().single()
        assertEquals("1", song.number)
        assertEquals("Amazing Grace", song.title)
        assertEquals("Hymnal", song.songbook, "the songbook comes from the file's own header")
        assertEquals("Newton", song.author)
        assertEquals("Excell", song.composer)
        assertEquals("G", song.tune)
    }

    @Test
    fun `every song in the file is read`() {
        val songs = load(row("1", "First"), row("2", "Second"), row("3", "Third"))

        assertEquals(3, songs.getSongCount())
        assertEquals(listOf("First", "Second", "Third"), songs.getSongs().map { it.title })
    }

    @Test
    fun `loading a second songbook replaces the first`() {
        val songs = load(row("1", "First"))
        val second = File(dir, "other.sps").also {
            it.writeText("##SongPresenter\n##Other\n" + row("9", "Only This"), Charsets.UTF_8)
        }

        songs.loadFromSps(second.absolutePath)

        assertEquals(listOf("Only This"), songs.getSongs().map { it.title }, "loading is not appending")
    }

    @Test
    fun `a second songbook can be appended deliberately`() {
        val songs = load(row("1", "First"))
        val second = File(dir, "other.sps").also {
            it.writeText("##SongPresenter\n##Other\n" + row("9", "Also This"), Charsets.UTF_8)
        }

        songs.loadFromSpsAppend(second.absolutePath)

        assertEquals(listOf("First", "Also This"), songs.getSongs().map { it.title })
        assertEquals(listOf("Hymnal", "Other"), songs.getSongs().map { it.songbook })
    }

    @Test
    fun `a line with too few fields is skipped`() {
        val songs = load(row("1", "Good"), "2#\$#Not enough fields", row("3", "Also Good"))

        assertEquals(listOf("Good", "Also Good"), songs.getSongs().map { it.title })
    }

    @Test
    fun `a song with no lyrics still loads`() {
        assertTrue(load(row("1", "Amazing Grace")).getSongs().single().lyrics.isEmpty())
    }

    // ── The running order the operator steps through ────────────────────────────

    @Test
    fun `a song with one verse reads in order`() {
        val songs = load(row("1", "Simple", lyrics = lyrics(listOf("Verse 1", "Amazing grace", "how sweet"))))

        assertEquals(
            listOf("[Verse 1]", "Amazing grace", "how sweet"),
            songs.getSongs().single().lyrics,
        )
    }

    @Test
    fun `verses are separated by a blank line`() {
        val songs = load(
            row("1", "Two verses", lyrics = lyrics(
                listOf("Verse 1", "First line"),
                listOf("Verse 2", "Second line"),
            ))
        )

        assertEquals(
            listOf("[Verse 1]", "First line", "", "[Verse 2]", "Second line"),
            songs.getSongs().single().lyrics,
        )
    }

    @Test
    fun `the chorus is repeated after every verse`() {
        val songs = load(
            row("1", "With chorus", lyrics = lyrics(
                listOf("Verse 1", "First verse line"),
                listOf("Chorus", "Chorus line"),
                listOf("Verse 2", "Second verse line"),
            ))
        )

        assertEquals(
            listOf(
                "[Verse 1]", "First verse line",
                "", "{Chorus}", "Chorus line",
                "", "[Verse 2]", "Second verse line",
                "", "{Chorus}", "Chorus line",
            ),
            songs.getSongs().single().lyrics,
            "the chorus is written once in the file but sung after each verse",
        )
    }

    @Test
    fun `the chorus is not left where it was written`() {
        val songs = load(
            row("1", "With chorus", lyrics = lyrics(
                listOf("Chorus", "Chorus line"),
                listOf("Verse 1", "Verse line"),
            ))
        )

        assertEquals(
            listOf("[Verse 1]", "Verse line", "", "{Chorus}", "Chorus line"),
            songs.getSongs().single().lyrics,
            "a chorus written first must still follow the verse, not precede it",
        )
    }

    @Test
    fun `there is no blank line left dangling at the end`() {
        val songs = load(row("1", "Simple", lyrics = lyrics(listOf("Verse 1", "Only line"))))

        assertTrue(songs.getSongs().single().lyrics.last().isNotBlank(), "a trailing blank draws an empty slide")
    }

    @Test
    fun `blank lines inside a section are dropped`() {
        val songs = load(row("1", "Padded", lyrics = "Verse 1@%@%First line@%   @%Second line"))

        assertEquals(listOf("[Verse 1]", "First line", "Second line"), songs.getSongs().single().lyrics)
    }

    @Test
    fun `an empty section is skipped`() {
        val songs = load(row("1", "Gappy", lyrics = "Verse 1@%Line@\$@\$Verse 2@%Other"))

        assertEquals(
            listOf("[Verse 1]", "Line", "", "[Verse 2]", "Other"),
            songs.getSongs().single().lyrics,
        )
    }

    // ── Deciding what is a verse and what is a chorus ───────────────────────────

    @Test
    fun `a verse and a chorus are recognised by their headings`() {
        val songs = load(row("1", "English", lyrics = lyrics(listOf("Verse 1", "a"), listOf("Chorus", "b"))))

        assertEquals(listOf("[Verse 1]", "a", "", "{Chorus}", "b"), songs.getSongs().single().lyrics)
    }

    @Test
    fun `a bridge is stepped through like a verse and a refrain like a chorus`() {
        val songs = load(row("1", "English", lyrics = lyrics(listOf("Bridge", "c"), listOf("Refrain", "d"))))

        assertEquals(
            listOf("[Bridge]", "c", "", "{Refrain}", "d"),
            songs.getSongs().single().lyrics,
            "a refrain is a chorus by another name, and a bridge is sung through like a verse",
        )
    }

    /**
     * Documents a KNOWN GAP: only the LAST chorus-like section survives. The loader keeps a single
     * chorus and repeats it after every verse, so a song written with two of them — a "Chorus" and
     * a separate "Refrain", or two different choruses — loses the first one entirely: it is skipped
     * where it was written and never repeated.
     *
     * Reachable from any songbook that uses both headings in one song. The fix is to keep the
     * chorus that most recently preceded each verse rather than one for the whole song; this
     * expectation then becomes the first chorus appearing after verse one.
     */
    @Test
    fun `a song with two choruses keeps only the last -- known gap`() {
        val songs = load(
            row("1", "Two choruses", lyrics = lyrics(
                listOf("Verse 1", "a"),
                listOf("Chorus", "first chorus"),
                listOf("Verse 2", "b"),
                listOf("Refrain", "second chorus"),
            ))
        )

        val text = songs.getSongs().single().lyrics
        assertTrue("first chorus" !in text, "current behaviour: the first chorus is dropped entirely: $text")
        assertEquals(2, text.count { it == "second chorus" }, "only the last one is repeated, after each verse")
    }

    @Test
    fun `russian section headers are recognised`() {
        val songs = load(
            row("1", "Russian", lyrics = lyrics(
                listOf("Куплет 1", "первая строка"),
                listOf("Припев", "припев строка"),
            ))
        )

        assertEquals(
            listOf("[Куплет 1]", "первая строка", "", "{Припев}", "припев строка"),
            songs.getSongs().single().lyrics,
            "a Russian songbook must lay out the same way as an English one",
        )
    }

    @Test
    fun `section headers are recognised whatever their case`() {
        val songs = load(
            row("1", "Shouty", lyrics = lyrics(listOf("VERSE 1", "a"), listOf("chorus", "b")))
        )

        val text = songs.getSongs().single().lyrics
        assertTrue("[VERSE 1]" in text, text.toString())
        assertTrue("{chorus}" in text, text.toString())
    }

    @Test
    fun `a section with an unrecognised heading is left as written`() {
        val songs = load(row("1", "Odd", lyrics = lyrics(listOf("Intro", "spoken line"))))

        assertEquals(
            listOf("Intro", "spoken line"),
            songs.getSongs().single().lyrics,
            "an unknown heading is neither a verse nor a chorus, so it is shown as it stands",
        )
    }

    @Test
    fun `a section already wrapped by the author is left alone`() {
        val songs = load(row("1", "Wrapped", lyrics = lyrics(listOf("[Verse 1]", "a"))))

        assertEquals(listOf("[Verse 1]", "a"), songs.getSongs().single().lyrics, "no double wrapping")
    }

    // ── Finding a song ──────────────────────────────────────────────────────────

    private fun library() = load(
        row("1", "Amazing Grace"),
        row("12", "How Great Thou Art"),
        row("120", "Amazing Love"),
    )

    @Test
    fun `an empty search returns the whole songbook`() {
        assertEquals(3, library().findSongs("").size)
    }

    @Test
    fun `searching matches part of a title, whatever the case`() {
        assertEquals(
            listOf("Amazing Grace", "Amazing Love"),
            library().findSongs("amazing", Constants.CONTAINS).map { it.title },
        )
    }

    @Test
    fun `searching matches a song number too`() {
        assertEquals(
            listOf("How Great Thou Art", "Amazing Love"),
            library().findSongs("12", Constants.CONTAINS).map { it.title },
            "typing a number is how most songs are found; 12 also appears inside 120",
        )
    }

    @Test
    fun `starts-with narrows to the beginning of the title or number`() {
        assertEquals(
            listOf("How Great Thou Art", "Amazing Love"),
            library().findSongs("12", Constants.STARTS_WITH).map { it.title },
        )
        assertEquals(
            listOf("Amazing Grace", "Amazing Love"),
            library().findSongs("Amazing", Constants.STARTS_WITH).map { it.title },
        )
    }

    @Test
    fun `exact match finds the one song`() {
        assertEquals(
            listOf("How Great Thou Art"),
            library().findSongs("12", Constants.EXACT_MATCH).map { it.title },
            "a number typed in full should not also offer 120",
        )
        assertEquals(
            listOf("Amazing Grace"),
            library().findSongs("amazing grace", Constants.EXACT_MATCH).map { it.title },
        )
    }

    @Test
    fun `a search matching nothing returns nothing`() {
        assertTrue(library().findSongs("nothing like this").isEmpty())
    }

    // ── Filtering the library ───────────────────────────────────────────────────

    @Test
    fun `a songbook filter narrows to that songbook`() {
        val songs = load(row("1", "First"))
        val other = File(dir, "other.sps").also {
            it.writeText("##SongPresenter\n##Songs of Praise\n" + row("9", "Second"), Charsets.UTF_8)
        }
        songs.loadFromSpsAppend(other.absolutePath)

        assertEquals(listOf("First"), songs.getSongsBySongbook("Hymnal").map { it.title })
        assertEquals(listOf("First", "Second"), songs.getSongsBySongbook("All songbooks").map { it.title })
    }

    @Test
    fun `a songbook filter matches part of the name`() {
        val songs = load(row("1", "First"), songbook = "Hymnal 2020")

        assertEquals(
            1,
            songs.getSongsBySongbook("Hymnal").size,
            "the picker offers the full name but a partial one must still match",
        )
    }

    /**
     * Documents CURRENT behaviour: the category filter returns everything, whatever is asked for.
     * The `.sps` format's category ids are not mapped to names anywhere in the loader, so there is
     * nothing to filter on — the source says as much. Worth pinning so the day categories are
     * implemented, this test fails rather than the filter silently staying a no-op.
     */
    @Test
    fun `the category filter does not filter -- known gap`() {
        val songs = library()

        assertEquals(3, songs.getSongsByCategory("All song categories").size)
        assertEquals(3, songs.getSongsByCategory("Anything at all").size)
    }

    // ── Editing in memory ───────────────────────────────────────────────────────

    @Test
    fun `a song can be replaced by number and songbook`() {
        val songs = library()
        val original = songs.getSongs().first { it.number == "12" }

        songs.updateSong(original, original.copy(title = "How Great Thou Art (revised)"))

        assertEquals("How Great Thou Art (revised)", songs.getSongs().first { it.number == "12" }.title)
        assertEquals(3, songs.getSongCount(), "editing must not add a second copy")
    }

    @Test
    fun `replacing a song that is not there changes nothing`() {
        val songs = library()

        songs.updateSong(
            SongItem(number = "999", title = "Ghost", songbook = "Hymnal"),
            SongItem(number = "999", title = "Ghost", songbook = "Hymnal"),
        )

        assertEquals(3, songs.getSongCount())
    }

    @Test
    fun `songs can be added to the library in memory`() {
        val songs = library()

        songs.addSongs(listOf(SongItem(number = "500", title = "Added", songbook = "Hymnal")))

        assertEquals(4, songs.getSongCount())
        assertEquals("Added", songs.getSongs().last().title)
    }

    @Test
    fun `the song list handed out is a copy`() {
        val songs = library()

        val handed = songs.getSongs()
        songs.addSongs(listOf(SongItem(number = "500", title = "Added", songbook = "Hymnal")))

        assertEquals(3, handed.size, "a caller holding the list must not see it change underneath them")
    }
}
