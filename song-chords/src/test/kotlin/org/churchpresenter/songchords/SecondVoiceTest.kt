package org.churchpresenter.songchords

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecondVoiceTest {

    // ── The [voice: n] directive ────────────────────────────────────────────────

    @Test
    fun `voice 2 switches to the second voice and anything else back to the main one`() {
        assertEquals(SecondVoice.SECOND, SecondVoice.voiceDirectiveOf("[voice: 2]"))
        assertEquals(SecondVoice.SECOND, SecondVoice.voiceDirectiveOf("  [ VOICE :2 ]  "))
        assertEquals(SecondVoice.MAIN, SecondVoice.voiceDirectiveOf("[voice: 1]"))
        assertEquals(SecondVoice.MAIN, SecondVoice.voiceDirectiveOf("[voice: men]"))
        assertEquals(SecondVoice.MAIN, SecondVoice.voiceDirectiveOf("[voice:]"))
    }

    @Test
    fun `a section header or a lyric is not a voice directive`() {
        assertNull(SecondVoice.voiceDirectiveOf("[Verse 1]"))
        assertNull(SecondVoice.voiceDirectiveOf("voice: 2"))
        assertNull(SecondVoice.voiceDirectiveOf("[voice: 2] and words"))
        assertFalse(SecondVoice.isVoiceDirective("[background: blue]"))
        assertTrue(SecondVoice.isVoiceDirective("[voice: 2]"))
    }

    @Test
    fun `a voice directive is not a section header`() {
        assertFalse(ChordTransposer.isSectionHeader("[voice: 2]"))
        assertTrue(ChordTransposer.isSectionHeader("[Verse 1]"))
    }

    // ── Runs ────────────────────────────────────────────────────────────────────

    @Test
    fun `a line with no markup is one main-voice run`() {
        assertEquals(listOf(VoiceRun("Amazing grace", SecondVoice.MAIN)), SecondVoice.runs("Amazing grace"))
        assertEquals(listOf(VoiceRun("", SecondVoice.MAIN)), SecondVoice.runs(""))
        assertFalse(SecondVoice.hasMarkup("Amazing grace"))
    }

    @Test
    fun `a 2 prefix puts the whole line in voice 2 with its leading space gone`() {
        assertEquals(
            listOf(VoiceRun("That saved a wretch", SecondVoice.SECOND)),
            SecondVoice.runs("  2> That saved a wretch"),
        )
    }

    @Test
    fun `a bracketed span puts only its words in voice 2`() {
        assertEquals(
            listOf(
                VoiceRun("Was blind, but ", SecondVoice.MAIN),
                VoiceRun("now I see", SecondVoice.SECOND),
                VoiceRun(" today", SecondVoice.MAIN),
            ),
            SecondVoice.runs("Was blind, but 2>(now I see) today"),
        )
    }

    @Test
    fun `brackets inside a span stay in it`() {
        assertEquals(
            listOf(
                VoiceRun("sing ", SecondVoice.MAIN),
                VoiceRun("(echo) now", SecondVoice.SECOND),
                VoiceRun("!", SecondVoice.MAIN),
            ),
            SecondVoice.runs("sing 2>((echo) now)!"),
        )
    }

    @Test
    fun `an unclosed span runs to the end of the line`() {
        assertEquals(
            listOf(VoiceRun("one ", SecondVoice.MAIN), VoiceRun("two three", SecondVoice.SECOND)),
            SecondVoice.runs("one 2>(two three"),
        )
    }

    @Test
    fun `spans side by side merge and empty spans vanish`() {
        assertEquals(listOf(VoiceRun("ab", SecondVoice.SECOND)), SecondVoice.runs("2>(a)2>(b)"))
        assertEquals(listOf(VoiceRun("xy", SecondVoice.MAIN)), SecondVoice.runs("x2>()y"))
    }

    @Test
    fun `a 2 line holding spans is still all voice 2`() {
        assertEquals(listOf(VoiceRun("all of this", SecondVoice.SECOND)), SecondVoice.runs("2>all 2>(of) this"))
    }

    @Test
    fun `a span at the start of a line is a span, not a whole-line prefix`() {
        assertEquals(
            listOf(VoiceRun("now", SecondVoice.SECOND), VoiceRun(" then", SecondVoice.MAIN)),
            SecondVoice.runs("2>(now) then"),
        )
    }

    // ── Plain text ──────────────────────────────────────────────────────────────

    @Test
    fun `plain takes the markup out and keeps chords`() {
        assertEquals("Was blind, but [G]now I see", SecondVoice.plain("Was blind, but 2>([G]now I see)"))
        assertEquals("That saved", SecondVoice.plain("2>That saved"))
        assertEquals("untouched  line", SecondVoice.plain("untouched  line"))
    }

    // ── Whole voice-2 lines ─────────────────────────────────────────────────────

    @Test
    fun `a line is voice 2 only when every word is`() {
        assertTrue(SecondVoice.isSecondVoiceLine("2>That saved"))
        assertTrue(SecondVoice.isSecondVoiceLine("  2>(That saved)  "))
        assertFalse(SecondVoice.isSecondVoiceLine("Was blind, but 2>(now I see)"))
        assertFalse(SecondVoice.isSecondVoiceLine("plain"))
        assertFalse(SecondVoice.isSecondVoiceLine("2>(   )"))
    }

    @Test
    fun `asSecondVoiceLine marks a line once`() {
        assertEquals("2>Amazing grace", SecondVoice.asSecondVoiceLine("Amazing grace"))
        assertEquals("2>Was blind, but now I see", SecondVoice.asSecondVoiceLine("Was blind, but 2>(now I see)"))
        assertEquals("2>already", SecondVoice.asSecondVoiceLine("2>already"))
    }

    // ── Line-mode groups ────────────────────────────────────────────────────────

    @Test
    fun `a main line and the voice-2 line answering it pair, the rest show alone`() {
        val lines = listOf("Amazing grace", "2>That saved", "I once was lost", "Was blind, but 2>(now I see)")
        assertEquals(listOf(0..1, 2..2, 3..3), SecondVoice.lineGroups(lines))
    }

    @Test
    fun `a voice-2 line with no main line before it shows alone`() {
        val lines = listOf("2>first", "2>second", "main", "2>answer", "2>extra")
        assertEquals(listOf(0..0, 1..1, 2..3, 4..4), SecondVoice.lineGroups(lines))
    }

    @Test
    fun `a line with only some words in voice 2 does not pair with the line before it`() {
        assertEquals(listOf(0..0, 1..1), SecondVoice.lineGroups(listOf("main", "words 2>(and more)")))
    }

    @Test
    fun `lineGroupAt finds the group either line of a pair is in`() {
        val lines = listOf("Amazing grace", "2>That saved", "I once was lost")
        assertEquals(0..1, SecondVoice.lineGroupAt(lines, 0))
        assertEquals(0..1, SecondVoice.lineGroupAt(lines, 1))
        assertEquals(2..2, SecondVoice.lineGroupAt(lines, 2))
        assertEquals(7..7, SecondVoice.lineGroupAt(lines, 7))
        assertEquals(emptyList(), SecondVoice.lineGroups(emptyList()))
    }
}
