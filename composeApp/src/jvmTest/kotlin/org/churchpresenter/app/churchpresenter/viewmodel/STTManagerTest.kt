package org.churchpresenter.app.churchpresenter.viewmodel

import org.json.JSONObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Live captions. Everything on screen during a service comes from one of three Socket.IO payloads
 * the speech-to-text server pushes, so the parsing of those payloads *is* the feature: a dropped
 * field shows the congregation a blank line, and a mis-read `in_progress` leaves the half-spoken
 * sentence stuck on screen after the speaker has moved on.
 *
 * The three handlers are private and only reachable from a socket callback, so — as with
 * `CrashReporter.scrubPii` — they are invoked directly by reflection rather than left untested
 * behind a Socket.IO server that would have to be stood up for every case. Everything else here
 * goes through the public API.
 */
class STTManagerTest {

    private val created = mutableListOf<STTManager>()

    @AfterTest
    fun cleanup() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
    }

    private fun stt(): STTManager = STTManager().also { created.add(it) }

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    /** Invokes one of the private `handle*Update(JSONObject)` parsers. */
    private fun STTManager.handle(method: String, payload: String) {
        STTManager::class.java
            .getDeclaredMethod(method, JSONObject::class.java)
            .apply { isAccessible = true }
            .invoke(this, JSONObject(payload))
    }

    private fun STTManager.transcription(payload: String) = handle("handleTranscriptionUpdate", payload)
    private fun STTManager.translation(payload: String) = handle("handleTranslationUpdate", payload)
    private fun STTManager.highlighting(payload: String) = handle("handleWordHighlightingUpdate", payload)

    // ── Baseline ────────────────────────────────────────────────────────────────

    @Test
    fun `a fresh manager is disconnected with nothing to show`() {
        val s = stt()
        assertFalse(s.connected.value)
        assertFalse(s.connecting.value)
        assertFalse(s.connectError.value)
        assertFalse(s.reconnecting.value)
        assertTrue(s.segments.isEmpty())
        assertTrue(s.translationSegments.isEmpty())
        assertEquals("", s.inProgressText.value)
        assertEquals("", s.inProgressTranslation.value)
        assertEquals("", s.translationLanguage.value)
        assertTrue(s.highlightedWords.isEmpty())
        assertFalse(s.isLive.value)
    }

    @Test
    fun `word highlighting is on until the server says otherwise`() {
        assertTrue(stt().wordHighlightingEnabled.value, "a server that never sends the setting should still highlight")
    }

    // ── Going live ──────────────────────────────────────────────────────────────

    @Test
    fun `the live flag tracks what is being projected`() {
        val s = stt()
        s.setLive(true)
        assertTrue(s.isLive.value)
        s.setLive(false)
        assertFalse(s.isLive.value)
    }

    @Test
    fun `going live does not touch the connection or the transcript`() {
        val s = stt()
        s.transcription("""{"segments":[{"id":1,"text":"Good morning"}]}""")
        s.setLive(true)
        assertEquals(1, s.segments.size)
        assertFalse(s.connected.value)
    }

    // ── Connecting ──────────────────────────────────────────────────────────────

    @Test
    fun `an unusable server address is reported instead of leaving the ui spinning`() {
        val s = stt()
        s.connect("not a valid address")
        awaitUntil("the failed connection to be reported") { s.connectError.value }
        assertFalse(s.connecting.value, "a stuck spinner reads as 'still trying' when nothing is trying")
        assertFalse(s.connected.value)
    }

    @Test
    fun `disconnecting clears every connection flag`() {
        val s = stt()
        s.connect("not a valid address")
        awaitUntil("the failed connection to be reported") { s.connectError.value }

        s.disconnect()

        assertFalse(s.connected.value)
        assertFalse(s.connecting.value)
        assertFalse(s.connectError.value, "a stale error banner after an explicit disconnect is noise")
        assertFalse(s.reconnecting.value)
    }

    @Test
    fun `disconnecting without ever connecting is harmless`() {
        val s = stt()
        s.disconnect()
        s.disconnect()
        assertFalse(s.connected.value)
    }

    @Test
    fun `disposing an idle manager is harmless`() {
        val s = STTManager()
        s.dispose()
        s.dispose()
        assertFalse(s.connected.value)
    }

    @Test
    fun `disconnecting keeps the transcript on screen`() {
        val s = stt()
        s.transcription("""{"segments":[{"id":1,"text":"Good morning"}],"in_progress":"and welcome"}""")

        s.disconnect()

        assertEquals(1, s.segments.size, "the server going away must not blank what the room is reading")
        assertEquals("and welcome", s.inProgressText.value)
    }

    // ── Transcription payloads ──────────────────────────────────────────────────

    @Test
    fun `a transcription segment carries every field through`() {
        val s = stt()
        s.transcription(
            """{"segments":[{"id":7,"timestamp":"10:31:02","text":"For God so loved the world",
               "start":12.5,"end":15.25,"completed":true}]}"""
        )
        val seg = s.segments.single()
        assertEquals(7, seg.id)
        assertEquals("10:31:02", seg.timestamp)
        assertEquals("For God so loved the world", seg.text)
        assertEquals(12.5, seg.start)
        assertEquals(15.25, seg.end)
        assertTrue(seg.completed)
    }

    @Test
    fun `segments keep the order the server sent them in`() {
        val s = stt()
        s.transcription("""{"segments":[{"id":1,"text":"first"},{"id":2,"text":"second"},{"id":3,"text":"third"}]}""")
        assertEquals(listOf("first", "second", "third"), s.segments.map { it.text })
    }

    @Test
    fun `a segment missing every optional field still lands with usable defaults`() {
        val s = stt()
        s.transcription("""{"segments":[{},{}]}""")
        assertEquals(listOf(0, 1), s.segments.map { it.id }, "the index stands in for a missing id so keys stay unique")
        assertEquals(listOf("", ""), s.segments.map { it.timestamp })
        assertEquals(listOf("", ""), s.segments.map { it.text })
        assertEquals(0.0, s.segments[0].start)
        assertEquals(0.0, s.segments[0].end)
        assertTrue(s.segments[0].completed, "an unmarked segment is treated as final, not as a fragment")
    }

    @Test
    fun `an incomplete segment is marked as such`() {
        val s = stt()
        s.transcription("""{"segments":[{"id":1,"text":"half a sen","completed":false}]}""")
        assertFalse(s.segments.single().completed)
    }

    @Test
    fun `each update replaces the transcript rather than appending to it`() {
        val s = stt()
        s.transcription("""{"segments":[{"id":1,"text":"first"},{"id":2,"text":"second"}]}""")
        s.transcription("""{"segments":[{"id":1,"text":"first (corrected)"}]}""")
        assertEquals(listOf("first (corrected)"), s.segments.map { it.text }, "the server sends the whole transcript each time")
    }

    @Test
    fun `an empty segment list clears the transcript`() {
        val s = stt()
        s.transcription("""{"segments":[{"id":1,"text":"first"}]}""")
        s.transcription("""{"segments":[]}""")
        assertTrue(s.segments.isEmpty())
    }

    @Test
    fun `an update with no segments key leaves the transcript alone`() {
        val s = stt()
        s.transcription("""{"segments":[{"id":1,"text":"first"}]}""")
        s.transcription("""{"in_progress":"still talking"}""")
        assertEquals(listOf("first"), s.segments.map { it.text }, "an in-progress-only update must not wipe the transcript")
        assertEquals("still talking", s.inProgressText.value)
    }

    @Test
    fun `the in-progress line updates as the sentence is spoken`() {
        val s = stt()
        s.transcription("""{"in_progress":"For God"}""")
        assertEquals("For God", s.inProgressText.value)
        s.transcription("""{"in_progress":"For God so loved"}""")
        assertEquals("For God so loved", s.inProgressText.value)
    }

    @Test
    fun `a finished sentence clears the in-progress line`() {
        val s = stt()
        s.transcription("""{"in_progress":"For God so loved"}""")
        s.transcription("""{"segments":[{"id":1,"text":"For God so loved the world"}],"in_progress":null}""")
        assertEquals("", s.inProgressText.value, "a leftover fragment under the finished line reads as a stutter")
    }

    @Test
    fun `an absent in-progress field is the same as an empty one`() {
        val s = stt()
        s.transcription("""{"in_progress":"partial"}""")
        s.transcription("""{"segments":[]}""")
        assertEquals("", s.inProgressText.value)
    }

    @Test
    fun `a non-text in-progress value is rendered rather than dropped`() {
        val s = stt()
        s.transcription("""{"in_progress":42}""")
        assertEquals("42", s.inProgressText.value)
    }

    // ── Translation payloads ────────────────────────────────────────────────────

    @Test
    fun `a translation segment uses the translated text`() {
        val s = stt()
        s.translation("""{"segments":[{"id":1,"text":"For God so loved","translated_text":"Ибо так возлюбил Бог"}]}""")
        assertEquals("Ибо так возлюбил Бог", s.translationSegments.single().text)
    }

    @Test
    fun `a segment not yet translated falls back to the original text`() {
        val s = stt()
        s.translation("""{"segments":[{"id":1,"text":"For God so loved","translated_text":""}]}""")
        assertEquals(
            "For God so loved",
            s.translationSegments.single().text,
            "a blank line under a spoken sentence looks like the feed died"
        )
    }

    @Test
    fun `a segment with neither text lands as an empty line rather than being dropped`() {
        val s = stt()
        s.translation("""{"segments":[{"id":1}]}""")
        assertEquals(1, s.translationSegments.size, "dropping it would misalign the translation against the transcript")
        assertEquals("", s.translationSegments.single().text)
    }

    @Test
    fun `translation segments carry their own timing and ids`() {
        val s = stt()
        s.translation("""{"segments":[{"id":9,"timestamp":"10:31:02","translated_text":"привет","start":1.5,"end":2.5,"completed":false}]}""")
        val seg = s.translationSegments.single()
        assertEquals(9, seg.id)
        assertEquals("10:31:02", seg.timestamp)
        assertEquals(1.5, seg.start)
        assertEquals(2.5, seg.end)
        assertFalse(seg.completed)
    }

    @Test
    fun `each translation update replaces the previous one`() {
        val s = stt()
        s.translation("""{"segments":[{"id":1,"translated_text":"один"},{"id":2,"translated_text":"два"}]}""")
        s.translation("""{"segments":[{"id":1,"translated_text":"один"}]}""")
        assertEquals(listOf("один"), s.translationSegments.map { it.text })
    }

    @Test
    fun `a translation update with no segments key leaves them alone`() {
        val s = stt()
        s.translation("""{"segments":[{"id":1,"translated_text":"один"}]}""")
        s.translation("""{"target_language_name":"Russian"}""")
        assertEquals(1, s.translationSegments.size)
    }

    @Test
    fun `the in-progress translation arrives as an object`() {
        val s = stt()
        s.translation("""{"in_progress":{"text":"For God","translated_text":"Ибо"}}""")
        assertEquals("Ибо", s.inProgressTranslation.value, "the object form carries both languages; only the translation is shown")
    }

    @Test
    fun `the in-progress translation also arrives as a bare string`() {
        val s = stt()
        s.translation("""{"in_progress":"Ибо"}""")
        assertEquals("Ибо", s.inProgressTranslation.value)
    }

    @Test
    fun `an in-progress object with nothing translated yet shows nothing`() {
        val s = stt()
        s.translation("""{"in_progress":{"text":"For God"}}""")
        assertEquals("", s.inProgressTranslation.value)
    }

    @Test
    fun `a null or absent in-progress translation clears the line`() {
        val s = stt()
        s.translation("""{"in_progress":"Ибо"}""")
        s.translation("""{"in_progress":null}""")
        assertEquals("", s.inProgressTranslation.value)

        s.translation("""{"in_progress":"Ибо"}""")
        s.translation("""{"segments":[]}""")
        assertEquals("", s.inProgressTranslation.value)
    }

    @Test
    fun `the target language is reported for the label`() {
        val s = stt()
        s.translation("""{"target_language_name":"Russian","segments":[]}""")
        assertEquals("Russian", s.translationLanguage.value)
    }

    @Test
    fun `a payload without a target language clears the label`() {
        val s = stt()
        s.translation("""{"target_language_name":"Russian","segments":[]}""")
        s.translation("""{"segments":[]}""")
        assertEquals("", s.translationLanguage.value, "a stale language label would mislabel the new language")
    }

    @Test
    fun `transcript and translation are kept apart`() {
        val s = stt()
        s.transcription("""{"segments":[{"id":1,"text":"For God so loved"}],"in_progress":"the world"}""")
        s.translation("""{"segments":[{"id":1,"translated_text":"Ибо так возлюбил Бог"}],"in_progress":"мир"}""")

        assertEquals("For God so loved", s.segments.single().text)
        assertEquals("the world", s.inProgressText.value)
        assertEquals("Ибо так возлюбил Бог", s.translationSegments.single().text)
        assertEquals("мир", s.inProgressTranslation.value)
    }

    // ── Word highlighting payloads ──────────────────────────────────────────────

    @Test
    fun `highlighted words carry their colour and matching options`() {
        val s = stt()
        s.highlighting("""{"words":[{"word":"grace","color":"#ff0000","case_sensitive":true,"is_regex":true}]}""")
        val word = s.highlightedWords.single()
        assertEquals("grace", word.word)
        assertEquals("#ff0000", word.color)
        assertTrue(word.caseSensitive)
        assertTrue(word.isRegex)
    }

    @Test
    fun `a word with no options given is a plain case-insensitive match in yellow`() {
        val s = stt()
        s.highlighting("""{"words":[{"word":"grace"}]}""")
        val word = s.highlightedWords.single()
        assertEquals("#ffff00", word.color)
        assertFalse(word.caseSensitive)
        assertFalse(word.isRegex)
    }

    @Test
    fun `words in a switched-off colour group are dropped`() {
        val s = stt()
        s.highlighting(
            """{"words":[{"word":"grace","color":"#ff0000"},{"word":"mercy","color":"#00ff00"}],
               "disabled_colors":["#ff0000"]}"""
        )
        assertEquals(listOf("mercy"), s.highlightedWords.map { it.word })
    }

    @Test
    fun `switching off every colour group leaves no words`() {
        val s = stt()
        s.highlighting("""{"words":[{"word":"grace","color":"#ff0000"}],"disabled_colors":["#ff0000"]}""")
        assertTrue(s.highlightedWords.isEmpty())
    }

    @Test
    fun `the default colour can itself be switched off`() {
        val s = stt()
        s.highlighting("""{"words":[{"word":"grace"}],"disabled_colors":["#ffff00"]}""")
        assertTrue(s.highlightedWords.isEmpty(), "a word with no colour still belongs to the default group")
    }

    @Test
    fun `each update replaces the whole word list`() {
        val s = stt()
        s.highlighting("""{"words":[{"word":"grace"},{"word":"mercy"}]}""")
        s.highlighting("""{"words":[{"word":"grace"}]}""")
        assertEquals(listOf("grace"), s.highlightedWords.map { it.word }, "a removed word must stop highlighting")
    }

    @Test
    fun `a payload with no words clears the list`() {
        val s = stt()
        s.highlighting("""{"words":[{"word":"grace"}]}""")
        s.highlighting("""{"enabled":true}""")
        assertTrue(s.highlightedWords.isEmpty())
    }

    @Test
    fun `highlighting can be switched off wholesale and back on`() {
        val s = stt()
        s.highlighting("""{"enabled":false,"words":[{"word":"grace"}]}""")
        assertFalse(s.wordHighlightingEnabled.value)
        assertEquals(1, s.highlightedWords.size, "the words are kept so switching back on is instant")

        s.highlighting("""{"enabled":true,"words":[{"word":"grace"}]}""")
        assertTrue(s.wordHighlightingEnabled.value)
    }

    @Test
    fun `a payload that omits the enabled flag leaves highlighting on`() {
        val s = stt()
        s.highlighting("""{"words":[{"word":"grace"}]}""")
        assertTrue(s.wordHighlightingEnabled.value)
    }
}
