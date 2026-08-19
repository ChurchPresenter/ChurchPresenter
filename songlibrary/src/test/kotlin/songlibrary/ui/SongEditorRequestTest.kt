package songlibrary.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The request a row's Edit button hands to whoever is hosting the window.
 *
 * This is the module's whole editor API: the app answers it with its own Edit Song dialog, so what
 * it carries — the song, the books it could be filed under, every other song for a clash check —
 * and where its two callbacks go is a contract, not an implementation detail.
 */
class SongEditorRequestTest {

    private val song = STOCK.first()
    private val books = listOf("Hymnal", "Chorus Book")

    @Test
    fun `it carries the song and everything an editor needs to check it`() {
        val request = SongEditorRequest(song, books, STOCK, onSave = {}, onDismiss = {})

        assertSame(song, request.song)
        assertEquals(books, request.songbooks)
        assertEquals(STOCK, request.allSongs, "the whole library, so a clashing number can be found")
    }

    @Test
    fun `saving hands the edited song back`() {
        var saved: core.models.songs.SongItem? = null
        val request = SongEditorRequest(song, books, STOCK, onSave = { saved = it }, onDismiss = {})

        val edited = song.copy(author = "Charles Wesley")
        request.onSave(edited)

        assertEquals(edited, saved)
        assertEquals("Charles Wesley", saved?.author, "the edit is not lost on the way back")
    }

    @Test
    fun `dismissing asks for no save`() {
        var saves = 0
        var dismissed = false
        val request = SongEditorRequest(
            song,
            books,
            STOCK,
            onSave = { saves++ },
            onDismiss = { dismissed = true },
        )

        request.onDismiss()

        assertTrue(dismissed)
        assertEquals(0, saves)
    }

    @Test
    fun `two requests for the same song are the same request`() {
        val one = SongEditorRequest(song, books, STOCK, onSave = {}, onDismiss = {})
        val same = one.copy()
        val other = one.copy(song = song.copy(title = "Something Else"))

        assertEquals(one, same)
        assertEquals(one.hashCode(), same.hashCode())
        assertTrue(one != other)
        assertTrue(one.toString().contains("Amazing Grace"), "it says which song it is when logged")
    }
}
