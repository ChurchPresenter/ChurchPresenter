package org.churchpresenter.app.churchpresenter.dialogs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The one-click colour row under the song background hex field.
 *
 * A church settles on one or two backgrounds and reaches for them every week, so the operator's own
 * recents lead and the shipped suggestions fill whatever is left — dropping off one at a time as
 * their own list grows.
 */
class BackgroundSwatchesTest {

    @Test
    fun `an install that has picked nothing yet is offered the shipped suggestions`() {
        assertEquals(SONG_BACKGROUND_SUGGESTIONS, backgroundSwatches(emptyList()))
    }

    @Test
    fun `the row is always the configured length`() {
        assertEquals(SONG_BACKGROUND_SUGGESTION_COUNT, backgroundSwatches(emptyList()).size)
        assertEquals(SONG_BACKGROUND_SUGGESTION_COUNT, backgroundSwatches(listOf("#FF0000")).size)
    }

    @Test
    fun `a colour the operator picked leads the row`() {
        val row = backgroundSwatches(listOf("#FF0000"))
        assertEquals("#FF0000", row.first())
        assertEquals(SONG_BACKGROUND_SUGGESTION_COUNT, row.size)
    }

    @Test
    fun `the operator's recents keep the order they were given in`() {
        val row = backgroundSwatches(listOf("#FF0000", "#00FF00", "#0000FF"))
        assertEquals(listOf("#FF0000", "#00FF00", "#0000FF"), row.take(3))
    }

    @Test
    fun `each recent pushes one shipped suggestion off the end`() {
        val one = backgroundSwatches(listOf("#FF0000"))
        assertEquals(SONG_BACKGROUND_SUGGESTIONS.dropLast(1), one.drop(1))
    }

    @Test
    fun `a full row of recents leaves no room for a suggestion`() {
        val mine = (1..SONG_BACKGROUND_SUGGESTION_COUNT).map { "#%06X".format(it * 0x111111) }
        assertEquals(mine, backgroundSwatches(mine))
    }

    @Test
    fun `more recents than fit are cut to the row length, newest first`() {
        val mine = (1..20).map { "#%06X".format(it * 0x010101) }
        val row = backgroundSwatches(mine)
        assertEquals(mine.take(SONG_BACKGROUND_SUGGESTION_COUNT), row)
    }

    @Test
    fun `a recent that is also a shipped suggestion appears once`() {
        val shipped = SONG_BACKGROUND_SUGGESTIONS.first()
        val row = backgroundSwatches(listOf(shipped))
        assertEquals(1, row.count { it.equals(shipped, ignoreCase = true) })
    }

    @Test
    fun `the same colour typed in a different case is one colour, not two`() {
        val shipped = SONG_BACKGROUND_SUGGESTIONS[1]
        val row = backgroundSwatches(listOf(shipped.uppercase()))
        assertEquals(1, row.count { it.equals(shipped, ignoreCase = true) })
        assertEquals(shipped.uppercase(), row.first(), "the operator's own spelling is what is kept")
    }

    @Test
    fun `a recents list with a repeat in it offers that colour once`() {
        val row = backgroundSwatches(listOf("#ABCDEF", "#abcdef", "#123456"))
        assertEquals(listOf("#ABCDEF", "#123456"), row.take(2))
    }

    @Test
    fun `a shorter row can be asked for`() {
        assertEquals(2, backgroundSwatches(listOf("#FF0000"), limit = 2).size)
    }

    @Test
    fun `a row of nothing is a valid ask`() {
        assertEquals(emptyList(), backgroundSwatches(listOf("#FF0000"), limit = 0))
    }

    @Test
    fun `no colour ever appears twice, whatever the recents are`() {
        val row = backgroundSwatches(SONG_BACKGROUND_SUGGESTIONS.reversed().map { it.uppercase() })
        assertEquals(row.size, row.map { it.uppercase() }.distinct().size)
        assertTrue(row.isNotEmpty())
    }
}
