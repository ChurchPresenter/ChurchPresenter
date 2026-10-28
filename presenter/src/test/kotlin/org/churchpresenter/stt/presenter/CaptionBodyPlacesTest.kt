package org.churchpresenter.stt.presenter

import org.churchpresenter.core.models.stt.STTSegment
import org.churchpresenter.settings.CAPTION_BREAK_SEGMENT
import org.churchpresenter.settings.CAPTION_BREAK_SENTENCE
import org.churchpresenter.settings.CaptionReading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Where a place in the segments is drawn in a side's shaped text, and back: what lets a reveal's
 * cursor and a layout's line starts be told in each other's terms whatever the line breaks.
 */
class CaptionBodyPlacesTest {

    private fun segment(text: String, id: Int) =
        STTSegment(id = id, timestamp = "", text = text, start = 0.0, end = 1.0, completed = true)

    private val blankLineBetween = CaptionReading(lineBreaks = CAPTION_BREAK_SEGMENT, blankLineBetween = true)

    // Joined: "Grace and peace. Let us pray." (29) + " " + "Amen" -- the first has two sentences
    private val said = listOf(segment("Grace and peace. Let us pray.", id = 4), segment("Amen", id = 7))

    @Test
    fun `run on, a place is drawn where it is`() {
        val body = captionBody(said, null, CaptionReading(), allCaps = false)

        assertEquals(31, body.shapedAt(31))
        assertEquals(31, body.joinedAt(31))
        assertEquals(SegmentPoint(7, 1), body.pointAt(31))
    }

    @Test
    fun `with a blank line between segments the places after it move down by the extra break`() {
        val body = captionBody(said, null, blankLineBetween, allCaps = false)

        assertEquals("Grace and peace. Let us pray.\n\nAmen", body.text)
        // "m" of Amen: joined 31, drawn one further on for the second break
        assertEquals(32, body.shapedAt(31))
        assertEquals(31, body.joinedAt(32))
        assertEquals(SegmentPoint(7, 1), body.pointAt(32))
    }

    @Test
    fun `a place on the break between two segments is the start of the next`() {
        val body = captionBody(said, null, blankLineBetween, allCaps = false)

        assertEquals(31, body.shapedAt(30))
        assertEquals(30, body.joinedAt(30))
        assertEquals(SegmentPoint(7, 0), body.pointAt(30))
    }

    @Test
    fun `sentences on lines of their own are followed into the segment they came from`() {
        val body = captionBody(said, null, CaptionReading(lineBreaks = CAPTION_BREAK_SENTENCE), false)

        assertEquals("Grace and peace.\nLet us pray.\nAmen", body.text)
        assertEquals(SegmentPoint(4, 17), body.pointAt(17))
        assertEquals(17, body.shapedAt(17))
    }

    @Test
    fun `the end of the text is the end of the last segment`() {
        val body = captionBody(said, null, CaptionReading(), allCaps = false)

        assertEquals(body.text.length, body.shapedAt(99))
        assertEquals(34, body.joinedAt(99))
        assertEquals(SegmentPoint(7, 4), body.pointAt(body.text.length))
    }

    @Test
    fun `the words still being spoken belong to no segment`() {
        val body = captionBody(said, "and also", CaptionReading(), allCaps = false)

        assertNull(body.pointAt(body.text.length - 2))
    }

    @Test
    fun `nothing said has nothing to point at`() {
        val body = captionBody(emptyList(), null, CaptionReading(), allCaps = false)

        assertEquals(0, body.shapedAt(5))
        assertEquals(0, body.joinedAt(5))
        assertNull(body.pointAt(0))
    }

    @Test
    fun `the newest segments start where the oldest of them does`() {
        val body = captionBody(said, null, CaptionReading(), allCaps = false)

        assertEquals(30, body.newestStart(1))
        assertEquals(0, body.newestStart(2), "no more segments than that: from the start")
        assertEquals(0, body.newestStart(0), "0 keeps them all")
    }
}
