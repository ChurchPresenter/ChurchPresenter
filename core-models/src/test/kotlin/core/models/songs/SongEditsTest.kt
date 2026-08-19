package core.models.songs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The edits a person has made but not yet saved.
 *
 * Everything here is about the promise the footer makes: nothing reaches the disk until Save, and
 * Revert puts back exactly what was read. A pending edit that survived a revert, or a saved song
 * still counted as changed, both show up as a Save button that will not go quiet.
 */
class SongEditsTest {

    private val root = File("/library")

    private fun song(title: String, number: String = "", songbook: String = "Hymns") = SongItem(
        sourceFile = File(root, "$songbook/$number - $title.song").absolutePath,
        number = number,
        title = title,
        songbook = songbook,
    )

    private val grace = song("Amazing Grace", number = "0001")
    private val vision = song("Be Thou My Vision", number = "0002")

    private fun edits() = SongEdits(listOf(grace, vision))

    @Test
    fun `a library nobody has touched is not dirty`() {
        val edits = edits()
        assertFalse(edits.isDirty)
        assertTrue(edits.changed.isEmpty())
        assertEquals(listOf("Amazing Grace", "Be Thou My Vision"), edits.songs.map { it.title })
    }

    @Test
    fun `an edited field shows on the song and marks the library dirty`() {
        val edits = edits()
        edits.edit(grace.sourceFile, SongField.AUTHOR, "John Newton")

        assertTrue(edits.isDirty)
        assertEquals(listOf("Amazing Grace"), edits.changed.map { it.title })
        assertEquals("John Newton", edits.songs.first().author)
    }

    @Test
    fun `every editable field can be set and read back`() {
        val edits = edits()
        for (field in SongField.entries) edits.edit(grace.sourceFile, field, "x")

        val edited = edits.songs.first()
        assertTrue(SongField.entries.all { it.of(edited) == "x" }, edited.toString())
    }

    @Test
    fun `an edit is trimmed, and a songbook loses the slashes around it`() {
        val edits = edits()
        edits.edit(grace.sourceFile, SongField.TITLE, "  Amazing Grace  ")
        edits.edit(grace.sourceFile, SongField.SONGBOOK, "/Kids/AM/")

        assertEquals("Amazing Grace", edits.songs.first().title)
        assertEquals("Kids/AM", edits.songs.first().songbook)
    }

    @Test
    fun `editing a field back to what it was leaves nothing to save`() {
        val edits = edits()
        edits.edit(grace.sourceFile, SongField.TITLE, "Something Else")
        edits.edit(grace.sourceFile, SongField.TITLE, "Amazing Grace")

        assertFalse(edits.isDirty)
    }

    @Test
    fun `an edit to a song that is not there changes nothing`() {
        val edits = edits()
        edits.edit(File(root, "Hymns/gone.song").absolutePath, SongField.TITLE, "Ghost")

        assertFalse(edits.isDirty)
    }

    @Test
    fun `reverting puts back exactly what was read`() {
        val edits = edits()
        edits.edit(grace.sourceFile, SongField.TITLE, "Changed")
        edits.edit(vision.sourceFile, SongField.SONGBOOK, "Kids")

        edits.revert()

        assertFalse(edits.isDirty)
        assertEquals(listOf("Amazing Grace", "Be Thou My Vision"), edits.songs.map { it.title })
        assertEquals("Hymns", edits.songs.last().songbook)
    }

    @Test
    fun `a saved library is clean again without being reloaded`() {
        val edits = edits()
        edits.edit(grace.sourceFile, SongField.AUTHOR, "John Newton")

        edits.markSaved()

        assertFalse(edits.isDirty)
        assertEquals("John Newton", edits.songs.first().author)
    }

    @Test
    fun `lyrics are edited too, which the grid does not show but the editor changes`() {
        val edits = edits()
        edits.editLyrics(grace.sourceFile, listOf("[Verse 1]", "Amazing grace"), listOf("Чудна благодать"))

        assertTrue(edits.isDirty)
        assertEquals(listOf("[Verse 1]", "Amazing grace"), edits.songs.first().lyrics)
        assertEquals(listOf("Чудна благодать"), edits.songs.first().secondaryLyrics)
    }

    @Test
    fun `lyrics edited on a song that is not there change nothing`() {
        val edits = edits()
        edits.editLyrics("/library/gone.song", listOf("x"), emptyList())

        assertFalse(edits.isDirty)
    }

    // ── Batch edits ───────────────────────────────────────────────────────────

    @Test
    fun `a batch edit writes the named fields on every song it names`() {
        val edits = edits()
        edits.editAll(
            listOf(grace.sourceFile, vision.sourceFile),
            mapOf(SongField.SONGBOOK to "Christmas 2026", SongField.AUTHOR to "Traditional"),
        )

        assertTrue(edits.songs.all { it.songbook == "Christmas 2026" && it.author == "Traditional" })
    }

    @Test
    fun `a batch edit leaves the fields it does not name alone`() {
        val edits = edits()
        edits.editAll(listOf(grace.sourceFile), mapOf(SongField.AUTHOR to "Traditional"))

        assertEquals("0001", edits.songs.first().number)
        assertEquals("Amazing Grace", edits.songs.first().title)
    }

    @Test
    fun `a batch edit can clear a field on purpose`() {
        // Clearing the composer across a selection is a thing people do; refusing a blank would
        // mean opening every song to do it by hand.
        val edits = SongEdits(listOf(grace.copy(composer = "Someone")))
        edits.editAll(listOf(grace.sourceFile), mapOf(SongField.COMPOSER to ""))

        assertEquals("", edits.songs.first().composer)
        assertTrue(edits.isDirty)
    }

    @Test
    fun `a batch edit over no songs changes nothing`() {
        val edits = edits()
        edits.editAll(emptyList(), mapOf(SongField.AUTHOR to "Traditional"))

        assertFalse(edits.isDirty)
    }

    // ── Songs that arrive and leave ───────────────────────────────────────────

    @Test
    fun `a song added from outside the grid arrives clean`() {
        val edits = edits()
        val added = song("New Song", songbook = "Hymns")

        edits.add(added)

        assertEquals(3, edits.songs.size)
        assertFalse(edits.isDirty, "it is already on disk; the grid has nothing to write")
    }

    @Test
    fun `a removed song is gone from the grid and from what would be saved`() {
        val edits = edits()
        edits.edit(grace.sourceFile, SongField.AUTHOR, "John Newton")

        edits.remove(listOf(grace.sourceFile))

        assertEquals(listOf("Be Thou My Vision"), edits.songs.map { it.title })
        assertFalse(edits.isDirty)
    }

    // ── What Duplicate and New Song start from ────────────────────────────────

    @Test
    fun `a copy says it is one and starts without a number`() {
        val copy = edits().copyOf(grace.copy(author = "John Newton"), "(copy)")

        assertEquals("Amazing Grace (copy)", copy.title)
        assertEquals("", copy.number, "two songs sharing a number is worse than one without")
        assertEquals("John Newton", copy.author, "everything else comes with it")
    }

    @Test
    fun `a new song is filed in the songbook that was being looked at`() {
        val blank = edits().blank(root, "Kids/AM", "New Song")

        assertEquals("Kids/AM", blank.songbook)
        assertEquals(
            "Kids/AM/New Song.song",
            File(blank.sourceFile).toRelativeString(root).replace(File.separatorChar, '/'),
        )
    }

    @Test
    fun `a new song with no songbook selected is filed at the top of the library`() {
        val blank = edits().blank(root, "", "New Song")

        assertEquals("", blank.songbook)
        assertEquals("New Song.song", File(blank.sourceFile).name)
    }
}
