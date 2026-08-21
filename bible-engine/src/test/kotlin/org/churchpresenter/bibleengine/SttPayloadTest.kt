package org.churchpresenter.bibleengine

import org.churchpresenter.bibleengine.socket.transcriptionUpdate
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Reading the STT server's payloads.
 *
 * The correlation keys parsed here (`segment_id`, `session_id`) are what tie the STT database, the
 * engine's detection log and ChurchPresenter's live-references log into a single joinable record of
 * a service. A wrong value is worse than a missing one: it joins rows that have nothing to do with
 * each other.
 */
class SttPayloadTest {

    private fun payload(json: String) = transcriptionUpdate(JSONObject(json))

    // ── JSON null must not become the string "null" ─────────────────────────────
    // org.json's optString returns "null" for a JSON null rather than the supplied default, so the
    // obvious `optString(k, "").takeIf { it.isNotEmpty() }` accepts it as a real value. This was
    // live: detection-log rows and operator-flag rows carried "segmentId":"null", a fake join key
    // matching every other such row.

    @Test fun `a null segment id is absent, not the string null`() {
        val u = assertNotNull(payload("""{"segment_id":null,"in_progress":{"text":"for god so loved"}}"""))
        assertNull(u.segmentId)
    }

    @Test fun `a null session id is absent, not the string null`() {
        val u = assertNotNull(payload("""{"session_id":null,"in_progress":{"text":"for god so loved"}}"""))
        assertNull(u.sessionId)
    }

    @Test fun `a null speech type is absent, not the string null`() {
        val u = assertNotNull(payload("""{"speech_type":null,"in_progress":{"text":"for god so loved"}}"""))
        assertNull(u.speechType)
    }

    @Test fun `a null text does not put the word null into the transcript`() {
        // The worst case of the same coercion: "null" would be fed to detection as spoken words.
        val u = payload("""{"segments":[{"text":null},{"text":"for god so loved the world"}]}""")
        assertNotNull(u)
        assertEquals("for god so loved the world", u.text)
    }

    @Test fun `an in-progress object with null text does not contribute the word null`() {
        val u = payload("""{"segments":[{"text":"in the beginning"}],"in_progress":{"text":null}}""")
        assertNotNull(u)
        assertEquals("in the beginning", u.text)
    }

    // ── Ordinary values still parse ─────────────────────────────────────────────

    @Test fun `real ids are read`() {
        val u = assertNotNull(
            payload("""{"segment_id":"42","session_id":"S14","speech_type":"Speaking",
                        "in_progress":{"text":"for god so loved"}}""")
        )
        assertEquals("42", u.segmentId)
        assertEquals("S14", u.sessionId)
        assertEquals("Speaking", u.speechType)
    }

    @Test fun `a numeric row id stands in for a missing segment id`() {
        val u = assertNotNull(payload("""{"id":7,"in_progress":{"text":"for god so loved"}}"""))
        assertEquals("7", u.segmentId)
    }

    @Test fun `the newest segment supplies the id`() {
        val u = assertNotNull(
            payload("""{"segments":[{"text":"a","segment_id":"1"},{"text":"b","segment_id":"2"}]}""")
        )
        assertEquals("2", u.segmentId)
    }

    @Test fun `a payload with no text at all yields nothing`() {
        assertNull(payload("""{"segment_id":"42"}"""))
        assertNull(payload("""{"segments":[],"in_progress":null}"""))
    }

    @Test fun `blank and whitespace ids are treated as absent`() {
        val u = assertNotNull(payload("""{"segment_id":"   ","in_progress":{"text":"for god so loved"}}"""))
        assertNull(u.segmentId)
    }
}
