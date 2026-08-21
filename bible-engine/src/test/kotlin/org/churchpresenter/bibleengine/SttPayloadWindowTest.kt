package org.churchpresenter.bibleengine

import org.churchpresenter.bibleengine.socket.transcriptionUpdate
import org.churchpresenter.bibleengine.socket.translationUpdate
import org.churchpresenter.bibleengine.socket.windowedText
import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SttPayloadWindowTest {

    private fun payload(json: String) = JSONObject(json)

    @Test
    fun `an empty window yields nothing`() {
        assertNull(windowedText(emptyList(), null))
        assertNull(windowedText(emptyList(), ""))
        assertNull(windowedText(emptyList(), "   "))
    }

    @Test
    fun `blank completed segments are dropped`() {
        assertNull(windowedText(listOf("", "   "), null))
        assertEquals("real", windowedText(listOf("", "real"), null))
    }

    @Test
    fun `only the last two completed segments are kept`() {
        assertEquals("b c", windowedText(listOf("a", "b", "c"), null))
    }

    @Test
    fun `the in-progress segment comes last`() {
        assertEquals("a b now", windowedText(listOf("a", "b"), "now"))
    }

    @Test
    fun `segments and in-progress are trimmed`() {
        assertEquals("a b", windowedText(listOf("  a  "), " b "))
    }

    @Test
    fun `a payload with no text at all parses to null`() {
        assertNull(transcriptionUpdate(payload("""{}""")))
        assertNull(translationUpdate(payload("""{"segments":[]}""")))
    }

    @Test
    fun `an in-progress plain string is used directly`() {
        val update = assertNotNull(transcriptionUpdate(payload("""{"in_progress":"hello there"}""")))

        assertEquals("hello there", update.text)
    }

    @Test
    fun `an in-progress object supplies the text field`() {
        val update = assertNotNull(transcriptionUpdate(payload("""{"in_progress":{"text":"hello"}}""")))

        assertEquals("hello", update.text)
    }

    @Test
    fun `a non-string non-object in-progress is ignored`() {
        assertNull(transcriptionUpdate(payload("""{"in_progress":42}""")))
    }

    @Test
    fun `the translation parser reads the translated_text field`() {
        val json = """{"segments":[{"text":"original","translated_text":"translated"}]}"""

        assertEquals("translated", assertNotNull(translationUpdate(payload(json))).text)
        assertEquals("original", assertNotNull(transcriptionUpdate(payload(json))).text)
    }

    @Test
    fun `only the last two segments contribute text`() {
        val json = """{"segments":[{"text":"one"},{"text":"two"},{"text":"three"}]}"""

        assertEquals("two three", assertNotNull(transcriptionUpdate(payload(json))).text)
    }

    @Test
    fun `an integer id stands in for a missing segment_id on the in-progress object`() {
        val json = """{"in_progress":{"text":"hi","id":77}}"""

        assertEquals("77", assertNotNull(transcriptionUpdate(payload(json))).segmentId)
    }

    @Test
    fun `an explicit segment_id wins over the integer id`() {
        val json = """{"in_progress":{"text":"hi","id":77,"segment_id":"seg-9"}}"""

        assertEquals("seg-9", assertNotNull(transcriptionUpdate(payload(json))).segmentId)
    }

    @Test
    fun `a top-level segment id is the last resort`() {
        val json = """{"in_progress":{"text":"hi"},"segment_id":"top"}"""

        assertEquals("top", assertNotNull(transcriptionUpdate(payload(json))).segmentId)
    }

    @Test
    fun `start_time is preferred over start`() {
        val json = """{"in_progress":{"text":"hi","start_time":4.5,"start":1.0}}"""

        assertEquals(4.5, assertNotNull(transcriptionUpdate(payload(json))).startTime)
    }

    @Test
    fun `start is used when start_time is absent`() {
        val json = """{"in_progress":{"text":"hi","start":2.25}}"""

        assertEquals(2.25, assertNotNull(transcriptionUpdate(payload(json))).startTime)
    }

    @Test
    fun `a start time on the newest completed segment is used when in-progress has none`() {
        val json = """{"segments":[{"text":"a","start":9.0}]}"""

        assertEquals(9.0, assertNotNull(transcriptionUpdate(payload(json))).startTime)
    }

    @Test
    fun `a top-level start time is the last resort`() {
        val json = """{"in_progress":{"text":"hi"},"start_time":6.0}"""

        assertEquals(6.0, assertNotNull(transcriptionUpdate(payload(json))).startTime)
    }

    @Test
    fun `a payload with no timing reports none`() {
        assertNull(assertNotNull(transcriptionUpdate(payload("""{"in_progress":"hi"}"""))).startTime)
    }

    @Test
    fun `an empty segments array falls through to the top level`() {
        val json = """{"segments":[],"in_progress":"hi","segment_id":"top","start":1.5}"""
        val update = assertNotNull(transcriptionUpdate(payload(json)))

        assertEquals("top", update.segmentId)
        assertEquals(1.5, update.startTime)
    }

    @Test
    fun `speech type and session id are read when present`() {
        val json = """{"in_progress":"hi","speech_type":"Music","session_id":"S01"}"""
        val update = assertNotNull(transcriptionUpdate(payload(json)))

        assertEquals("Music", update.speechType)
        assertEquals("S01", update.sessionId)
    }

    @Test
    fun `a segments array holding a null entry does not break parsing`() {
        val segments = JSONArray().put(JSONObject.NULL).put(JSONObject().put("text", "kept"))
        val update = assertNotNull(transcriptionUpdate(JSONObject().put("segments", segments)))

        assertEquals("kept", update.text)
    }
}
