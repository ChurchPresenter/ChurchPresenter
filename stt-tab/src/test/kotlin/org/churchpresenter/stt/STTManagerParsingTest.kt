package org.churchpresenter.stt

import org.json.JSONObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How [STTManager] turns the STT server's socket payloads into caption state.
 *
 * The three `handle*Update` parsers are the pure core (the socket wiring around them needs a live
 * server); they are `internal`, so they're called directly with hand-built [JSONObject]s and the
 * result read back through the public state. What matters: optional fields fall back to sane
 * defaults, translation prefers `translated_text` but degrades to `text`, `in_progress` may arrive
 * as a string OR an object OR null, and highlighted words whose colour group is disabled are dropped.
 */
class STTManagerParsingTest {

    private val created = mutableListOf<STTManager>()

    private fun manager() = STTManager().also { created.add(it) }

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
    }

    private fun STTManager.transcription(json: String) = handleTranscriptionUpdate(JSONObject(json))
    private fun STTManager.translation(json: String) = handleTranslationUpdate(JSONObject(json))
    private fun STTManager.highlighting(json: String) = handleWordHighlightingUpdate(JSONObject(json))

    // ── Transcription ────────────────────────────────────────────────────────────

    @Test
    fun `a transcription segment carries all its fields`() {
        val stt = manager()

        stt.transcription(
            """{"segments":[{"id":7,"timestamp":"00:01","text":"hello","start":0.5,"end":1.5,"completed":true}]}""",
        )

        val seg = stt.segments.single()
        assertEquals(7, seg.id)
        assertEquals("00:01", seg.timestamp)
        assertEquals("hello", seg.text)
        assertEquals(0.5, seg.start)
        assertEquals(1.5, seg.end)
        assertEquals(true, seg.completed)
    }

    @Test
    fun `missing segment fields fall back to defaults`() {
        val stt = manager()

        stt.transcription("""{"segments":[{}]}""")

        val seg = stt.segments.single()
        assertEquals(0, seg.id, "the array index is the fallback id")
        assertEquals("", seg.text)
        assertEquals(0.0, seg.start)
        assertEquals(true, seg.completed, "a segment defaults to completed")
    }

    @Test
    fun `in-progress text is read as a plain string`() {
        val stt = manager()
        stt.transcription("""{"in_progress":"typing now"}""")
        assertEquals("typing now", stt.inProgressText.value)
    }

    @Test
    fun `a null or missing in-progress becomes empty`() {
        val stt = manager()
        stt.transcription("""{"in_progress":"seed"}""")
        stt.transcription("""{"in_progress":null}""")
        assertEquals("", stt.inProgressText.value)

        stt.transcription("""{"in_progress":"seed"}""")
        stt.transcription("""{}""")
        assertEquals("", stt.inProgressText.value, "a missing key is treated the same as null")
    }

    @Test
    fun `a payload with no segments array leaves the segments untouched`() {
        val stt = manager()
        stt.transcription("""{"segments":[{"id":1,"text":"kept"}]}""")

        stt.transcription("""{"in_progress":"just a progress update"}""")

        assertEquals("kept", stt.segments.single().text, "a progress-only update must not wipe the segment list")
    }

    // ── Translation ──────────────────────────────────────────────────────────────

    @Test
    fun `a translation segment prefers translated_text`() {
        val stt = manager()

        stt.translation(
            """{"segments":[{"id":1,"translated_text":"hola","text":"hello"}],"target_language_name":"Spanish"}""",
        )

        assertEquals("hola", stt.translationSegments.single().text)
        assertEquals("Spanish", stt.translationLanguage.value)
    }

    @Test
    fun `a translation segment falls back to text when the translation is blank`() {
        val stt = manager()

        stt.translation("""{"segments":[{"translated_text":"","text":"original"}]}""")

        assertEquals("original", stt.translationSegments.single().text)
    }

    @Test
    fun `in-progress translation may be an object with translated_text`() {
        val stt = manager()
        stt.translation("""{"in_progress":{"translated_text":"escribiendo"}}""")
        assertEquals("escribiendo", stt.inProgressTranslation.value)
    }

    @Test
    fun `in-progress translation may also be a plain string`() {
        val stt = manager()
        stt.translation("""{"in_progress":"partial"}""")
        assertEquals("partial", stt.inProgressTranslation.value)
    }

    @Test
    fun `an in-progress value of some other type is shown as its own text`() {
        // `in_progress` is whatever the STT server put there — the two parsers accept a string and
        // (for translation) an object, and everything else falls through to `toString()`. A server
        // sending a bare number is the realistic case, and it has to reach the screen as the
        // characters the speaker is part-way through rather than being dropped to blank.
        val stt = manager()

        stt.transcription("""{"in_progress":2026}""")
        assertEquals("2026", stt.inProgressText.value)

        stt.translation("""{"in_progress":2026}""")
        assertEquals("2026", stt.inProgressTranslation.value)
    }

    @Test
    fun `an in-progress array does not crash the caption`() {
        // The other shape a loose server can produce. Nothing renders an array usefully, but the
        // parser must not throw — this runs on every socket frame while someone is speaking, and
        // an exception here stops captions for the rest of the service.
        val stt = manager()

        stt.transcription("""{"in_progress":["a","b"]}""")

        assertTrue(stt.inProgressText.value.isNotEmpty(), "something readable, rather than a blank line")
    }

    // ── Word highlighting ────────────────────────────────────────────────────────

    @Test
    fun `a highlighted word carries its colour and flags`() {
        val stt = manager()

        stt.highlighting(
            """{"enabled":true,"words":[{"word":"Jesus","color":"#ff0000","case_sensitive":true,"is_regex":true}]}""",
        )

        assertTrue(stt.wordHighlightingEnabled.value)
        val w = stt.highlightedWords.single()
        assertEquals("Jesus", w.word)
        assertEquals("#ff0000", w.color)
        assertEquals(true, w.caseSensitive)
        assertEquals(true, w.isRegex)
    }

    @Test
    fun `a word with no colour defaults to yellow`() {
        val stt = manager()
        stt.highlighting("""{"words":[{"word":"grace"}]}""")
        assertEquals("#ffff00", stt.highlightedWords.single().color)
    }

    @Test
    @Suppress("MaxLineLength")
    fun `words in a disabled colour group are dropped`() {
        val stt = manager()

        stt.highlighting(
            """{"words":[{"word":"keep","color":"#00ff00"},{"word":"drop","color":"#ff0000"}],"disabled_colors":["#ff0000"]}""",
        )

        assertEquals(listOf("keep"), stt.highlightedWords.map { it.word }, "a disabled colour group is filtered out")
    }

    @Test
    fun `highlighting defaults to enabled and clears the word list each update`() {
        val stt = manager()
        stt.highlighting("""{"words":[{"word":"first","color":"#111"}]}""")

        stt.highlighting("""{}""") // no words, no enabled flag

        assertTrue(stt.wordHighlightingEnabled.value, "enabled defaults to true when the flag is absent")
        assertTrue(stt.highlightedWords.isEmpty(), "each update replaces the previous word list")
    }

    // ── JSON null is absence, never the string "null" ────────────────────────────
    // org.json's optString returns "null" for a JSON null instead of the supplied default, so a
    // server sending an explicit null used to put the literal word on screen.

    @Test
    fun `a null caption text does not put the word null on screen`() {
        val stt = manager()

        stt.transcription("""{"segments":[{"id":1,"text":null,"timestamp":null}]}""")

        val seg = stt.segments.single()
        assertEquals("", seg.text, "a null text must read as empty, not as \"null\"")
        assertEquals("", seg.timestamp)
    }

    @Test
    fun `a null translated_text falls back to the original text`() {
        // Doubly broken before: "null" is not blank, so ifBlank never reached the fallback AND
        // the word "null" was shown in place of the translation.
        val stt = manager()

        stt.translation("""{"segments":[{"id":1,"translated_text":null,"text":"original"}]}""")

        assertEquals("original", stt.translationSegments.single().text)
    }

    @Test
    fun `a null in-progress translation is empty`() {
        val stt = manager()

        stt.translation("""{"in_progress":{"translated_text":null}}""")

        assertEquals("", stt.inProgressTranslation.value)
    }

    @Test
    fun `a null target language reads as unset`() {
        val stt = manager()

        stt.translation("""{"target_language_name":null}""")

        assertEquals("", stt.translationLanguage.value)
    }

    @Test
    fun `a null highlighted word and colour do not become the string null`() {
        val stt = manager()

        stt.highlighting("""{"words":[{"word":null,"color":null}]}""")

        val w = stt.highlightedWords.single()
        assertEquals("", w.word, "a null word must not highlight every occurrence of \"null\"")
        assertEquals("#ffff00", w.color, "a null colour falls back to the default, not to \"null\"")
    }

    @Test
    fun `a null colour is not silently disabled by a null entry in disabled_colors`() {
        // Both nulls used to collapse to the same string "null", so the word matched the disabled
        // set and vanished from the captions entirely — a dropped highlight, not just a cosmetic one.
        val stt = manager()

        stt.highlighting("""{"words":[{"word":"grace","color":null}],"disabled_colors":[null]}""")

        assertEquals(listOf("grace"), stt.highlightedWords.map { it.word }, "the word must survive")
        assertEquals("#ffff00", stt.highlightedWords.single().color)
    }

    // ── Trivial state ────────────────────────────────────────────────────────────

    @Test
    fun `setLive reflects whether STT is being projected`() {
        val stt = manager()
        stt.setLive(true); assertTrue(stt.isLive.value)
        stt.setLive(false); assertTrue(!stt.isLive.value)
    }

    @Test
    fun `a translation payload with no segments array leaves the translation segments untouched`() {
        val stt = manager()
        stt.translation("""{"segments":[{"id":1,"translated_text":"hola"}]}""")

        stt.translation("""{"target_language_name":"Spanish"}""")

        assertEquals(1, stt.translationSegments.size)
        assertEquals("Spanish", stt.translationLanguage.value)
    }

    @Test
    fun `an empty segments array clears what was there`() {
        val stt = manager()
        stt.transcription("""{"segments":[{"id":1,"text":"first"}]}""")

        stt.transcription("""{"segments":[]}""")

        assertTrue(stt.segments.isEmpty())
    }
}
