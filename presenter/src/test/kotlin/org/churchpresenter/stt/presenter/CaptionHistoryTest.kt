package org.churchpresenter.stt.presenter

import org.churchpresenter.core.models.stt.STTSegment
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How an output keeps one side's captions while the server's window slides: what extends the
 * history, what starts it afresh, and where it may be cut.
 */
class CaptionHistoryTest {

    private fun segment(id: Int, text: String = "words of $id") =
        STTSegment(id = id, timestamp = "", text = text, start = 0.0, end = 1.0, completed = true)

    private fun window(vararg ids: Int) = ids.map { segment(it) }

    private fun CaptionHistory.ids() = segments.map { it.id }

    // ── Extending ───────────────────────────────────────────────────────────────

    @Test
    fun `new segments at the end are appended`() {
        val history = CaptionHistory()
        history.merge(window(1, 2), epoch = 0)

        history.merge(window(1, 2, 3), epoch = 0)

        assertEquals(listOf(1, 2, 3), history.ids())
        assertEquals(1, history.generation, "only the first window started it")
    }

    @Test
    fun `segments the window drops from its front are kept`() {
        val history = CaptionHistory()
        history.merge(window(1, 2, 3), epoch = 0)

        history.merge(window(2, 3, 4), epoch = 0)

        assertEquals(listOf(1, 2, 3, 4), history.ids())
        assertEquals(1, history.generation)
    }

    @Test
    fun `one segment corrected in place is replaced where it is`() {
        val history = CaptionHistory()
        history.merge(window(1, 2, 3), epoch = 0)

        history.merge(listOf(segment(1), segment(2, "put right"), segment(3)), epoch = 0)

        assertEquals("put right", history.segments[1].text)
        assertEquals(1, history.generation)
    }

    @Test
    fun `captions arriving into an empty history are new, not a backlog`() {
        val history = CaptionHistory()
        history.merge(emptyList(), epoch = 0)
        val opened = history.generation

        history.merge(window(1), epoch = 0)

        assertEquals(listOf(1), history.ids())
        assertEquals(opened, history.generation)
    }

    @Test
    fun `the same window twice changes nothing`() {
        val history = CaptionHistory()
        val first = history.merge(window(1, 2), epoch = 0)

        val again = history.merge(window(1, 2), epoch = 0)

        assertEquals(first, again)
        assertEquals(1, history.generation)
    }

    // ── Starting afresh ─────────────────────────────────────────────────────────

    @Test
    fun `a new epoch starts afresh even when the window would extend`() {
        val history = CaptionHistory()
        history.merge(window(1, 2), epoch = 0)

        history.merge(window(1, 2, 3), epoch = 1)

        assertEquals(listOf(1, 2, 3), history.ids())
        assertEquals(2, history.generation)
    }

    @Test
    fun `an emptied window starts afresh`() {
        val history = CaptionHistory()
        history.merge(window(1, 2), epoch = 0)

        history.merge(emptyList(), epoch = 0)

        assertEquals(emptyList(), history.ids())
        assertEquals(2, history.generation)
    }

    @Test
    fun `a window out of id order starts afresh`() {
        val history = CaptionHistory()
        history.merge(window(1, 2), epoch = 0)

        history.merge(window(1, 3, 2), epoch = 0)

        assertEquals(listOf(1, 3, 2), history.ids())
        assertEquals(2, history.generation)
    }

    @Test
    fun `a segment missing from the middle starts afresh`() {
        val history = CaptionHistory()
        history.merge(window(1, 2, 3), epoch = 0)

        history.merge(window(1, 3, 4), epoch = 0)

        assertEquals(listOf(1, 3, 4), history.ids())
        assertEquals(2, history.generation)
    }

    @Test
    fun `more than one correction at once starts afresh`() {
        val history = CaptionHistory()
        history.merge(window(1, 2, 3), epoch = 0)

        history.merge(listOf(segment(1, "one"), segment(2, "two"), segment(3)), epoch = 0)

        assertEquals(2, history.generation)
    }

    @Test
    fun `a window that has scrolled past everything kept starts afresh`() {
        val history = CaptionHistory()
        history.merge(window(1, 2), epoch = 0)

        history.merge(window(5, 6), epoch = 0)

        assertEquals(listOf(5, 6), history.ids())
        assertEquals(2, history.generation)
    }

    @Test
    fun `a window wholly older than what is kept starts afresh`() {
        val history = CaptionHistory()
        history.merge(window(4, 5), epoch = 0)
        history.cutAt(id = 5, offset = 0)

        history.merge(window(1, 2), epoch = 0)

        assertEquals(listOf(1, 2), history.ids())
        assertEquals(2, history.generation)
    }

    // ── Cutting ─────────────────────────────────────────────────────────────────

    @Test
    fun `a cut drops the segments before it and draws its own from the offset`() {
        val history = CaptionHistory()
        history.merge(listOf(segment(1), segment(2, "first line second line"), segment(3)), epoch = 0)

        history.cutAt(id = 2, offset = 11)

        assertEquals(listOf(2, 3), history.ids())
        assertEquals(11, history.offsetIn(2))
        assertEquals(0, history.offsetIn(3))
        val (shown, dropped) = history.cutFront(history.segments)
        assertEquals("second line", shown.first().text)
        assertEquals(11, dropped)
    }

    @Test
    fun `segments older than the cut leave the history even while the window still has them`() {
        val history = CaptionHistory()
        history.merge(window(1, 2, 3), epoch = 0)
        history.cutAt(id = 2, offset = 0)

        history.merge(window(1, 2, 3, 4), epoch = 0)

        assertEquals(listOf(2, 3, 4), history.ids())
        assertEquals(1, history.generation)
    }

    @Test
    fun `a cut at a segment no longer kept is ignored`() {
        val history = CaptionHistory()
        history.merge(window(1, 2), epoch = 0)

        history.cutAt(id = 9, offset = 3)

        assertEquals(listOf(1, 2), history.ids())
        assertEquals(0, history.offsetIn(9))
    }

    @Test
    fun `a correction of the cut segment moves the cut to its start`() {
        val history = CaptionHistory()
        history.merge(listOf(segment(1, "first line second line"), segment(2)), epoch = 0)
        history.cutAt(id = 1, offset = 11)

        history.merge(listOf(segment(1, "first line put right"), segment(2)), epoch = 0)

        assertEquals(0, history.offsetIn(1))
        assertEquals("first line put right", history.cutFront(history.segments).first.first().text)
    }

    @Test
    fun `cutting the front leaves a reveal that has not reached the cut segment alone`() {
        val history = CaptionHistory()
        history.merge(window(1, 2), epoch = 0)
        history.cutAt(id = 2, offset = 3)

        val (shown, dropped) = history.cutFront(listOf(segment(5)))

        assertEquals(listOf(5), shown.map { it.id })
        assertEquals(0, dropped)
    }

    @Test
    fun `with no cut the front is left as it is`() {
        val history = CaptionHistory()
        history.merge(window(1, 2), epoch = 0)

        assertEquals(history.segments to 0, history.cutFront(history.segments))
    }
}
