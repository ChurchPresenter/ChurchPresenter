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
    fun `a line is an answer only when no word is the main voice`() {
        assertTrue(SecondVoice.isAnswerLine("2>That saved"))
        assertTrue(SecondVoice.isAnswerLine("  2>(That saved)  "))
        assertFalse(SecondVoice.isAnswerLine("Was blind, but 2>(now I see)"))
        assertFalse(SecondVoice.isAnswerLine("plain"))
        assertFalse(SecondVoice.isAnswerLine("2>(   )"))
    }

    @Test
    fun `asVoiceLine marks a line once`() {
        val two = VoiceKey(SecondVoice.SECOND)
        assertEquals("2>Amazing grace", SecondVoice.asVoiceLine("Amazing grace", two))
        assertEquals(
            "2>Was blind, but now I see",
            SecondVoice.asVoiceLine("Was blind, but 2>(now I see)", VoiceKey(SecondVoice.SECOND)),
        )
        assertEquals("2>already", SecondVoice.asVoiceLine("2>already", VoiceKey(SecondVoice.SECOND)))
    }

    // ── Line-mode groups ────────────────────────────────────────────────────────

    @Test
    fun `a main line and the voice-2 line answering it pair, the rest show alone`() {
        val lines = listOf("Amazing grace", "2>That saved", "I once was lost", "Was blind, but 2>(now I see)")
        assertEquals(listOf(0..1, 2..2, 3..3), SecondVoice.lineGroups(lines))
    }

    @Test
    fun `answer lines with no main line before them show alone, and a main line takes every answer after it`() {
        val lines = listOf("2>first", "2>second", "main", "2>answer", "2>extra")
        assertEquals(listOf(0..0, 1..1, 2..4), SecondVoice.lineGroups(lines))
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

    // ── The third voice ─────────────────────────────────────────────────────────

    @Test
    fun `a voice-3 directive switches to voice 3, and any other number to the main voice`() {
        assertEquals(SecondVoice.THIRD, SecondVoice.voiceDirectiveOf("[voice: 3]"))
        assertEquals(SecondVoice.MAIN, SecondVoice.voiceDirectiveOf("[voice: 4]"))
        assertEquals(SecondVoice.MAIN, SecondVoice.voiceDirectiveOf("[voice: 0]"))
    }

    @Test
    fun `a 3 mark puts a whole line in voice 3`() {
        assertEquals(
            listOf(VoiceRun("By the grace of God", SecondVoice.THIRD)),
            SecondVoice.runs(" 3> By the grace of God"),
        )
        assertEquals(SecondVoice.THIRD, wholeLineKeyOf("3>words")?.voice)
        assertEquals(SecondVoice.SECOND, wholeLineKeyOf("  2>words")?.voice)
        assertNull(wholeLineKeyOf("3>(words)")?.voice, "a bracket after the mark makes it a run, not a line")
        assertNull(wholeLineKeyOf("4>words")?.voice, "there is no fourth voice")
    }

    @Test
    fun `voice-2 and voice-3 runs sit in one line`() {
        assertEquals(
            listOf(
                VoiceRun("Lord, have mercy. ", SecondVoice.MAIN),
                VoiceRun("Christ, have mercy.", SecondVoice.SECOND),
                VoiceRun(" ", SecondVoice.MAIN),
                VoiceRun("Lord, have mercy.", SecondVoice.THIRD),
            ),
            SecondVoice.runs("Lord, have mercy. 2>(Christ, have mercy.) 3>(Lord, have mercy.)"),
        )
    }

    @Test
    fun `a voice-2 run inside a voice-3 line keeps its own voice`() {
        assertEquals(
            listOf(VoiceRun("all ", SecondVoice.THIRD), VoiceRun("but this", SecondVoice.SECOND)),
            SecondVoice.runs("3>all 2>(but this)"),
        )
        assertEquals(listOf(VoiceRun("", SecondVoice.THIRD)), SecondVoice.runs("3>"))
    }

    @Test
    fun `the third voice's markup comes off in plain text`() {
        assertEquals("I do and amen", SecondVoice.plain("3>I do 2>(and) amen"))
        assertTrue(SecondVoice.hasMarkup("x 3>(y)"))
        assertFalse(SecondVoice.hasMarkup("3 > not a mark"))
        assertEquals("3>", SecondVoice.markOf(SecondVoice.THIRD))
    }

    @Test
    fun `a line all in other voices is an answer, whichever voices they are`() {
        assertTrue(SecondVoice.isAnswerLine("3>By grace"))
        assertTrue(SecondVoice.isAnswerLine("2>(yes) 3>(amen)"))
        assertFalse(SecondVoice.isAnswerLine("Lord 3>(amen)"))
    }

    @Test
    fun `asVoiceLine marks a line in voice 3 and leaves an answer as it is`() {
        assertEquals("3>Amen", SecondVoice.asVoiceLine("Amen", VoiceKey(SecondVoice.THIRD)))
        assertEquals("2>Already", SecondVoice.asVoiceLine("2>Already", VoiceKey(SecondVoice.THIRD)))
    }

    @Test
    fun `a main line takes every voice-2 and voice-3 answer after it as one group`() {
        val lines = listOf("The Lord be with you", "2>And also with you", "3>Lift up your hearts", "Amen", "3>Amen")
        assertEquals(listOf(0..2, 3..4), SecondVoice.lineGroups(lines))
        assertEquals(0..2, SecondVoice.lineGroupAt(lines, 2))
    }

    // ── A colour of its own ─────────────────────────────────────────────────────

    @Test
    fun `a hex mark puts a whole line in that colour`() {
        assertEquals(
            listOf(VoiceRun("I will.", SecondVoice.CUSTOM, "#4DABF7")),
            SecondVoice.runs(" #4DABF7> I will."),
        )
        assertEquals(VoiceKey.color("#4DABF7"), wholeLineKeyOf("#4DABF7>I will."))
        assertEquals(VoiceKey.color("#804DABF7"), wholeLineKeyOf("#804DABF7>with alpha"))
    }

    @Test
    fun `a hex run colours just its words, beside voice runs`() {
        assertEquals(
            listOf(
                VoiceRun("Will you? ", SecondVoice.MAIN),
                VoiceRun("I will.", SecondVoice.CUSTOM, "#F783AC"),
                VoiceRun(" ", SecondVoice.MAIN),
                VoiceRun("We will.", SecondVoice.SECOND),
            ),
            SecondVoice.runs("Will you? #F783AC>(I will.) 2>(We will.)"),
        )
    }

    @Test
    fun `runs of different colours stay apart and runs of one colour merge`() {
        assertEquals(
            listOf(VoiceRun("ab", SecondVoice.CUSTOM, "#111111"), VoiceRun("c", SecondVoice.CUSTOM, "#222222")),
            SecondVoice.runs("#111111>(a)#111111>(b)#222222>(c)"),
        )
    }

    @Test
    fun `something that is not a hex colour is not a mark`() {
        assertFalse(SecondVoice.hasMarkup("#12345>short"))
        assertFalse(SecondVoice.hasMarkup("#GGGGGG>not hex"))
        assertFalse(SecondVoice.hasMarkup("We're #1> the rest"))
        assertNull(wholeLineKeyOf("#E91E63>(a run)"), "a bracket after the mark makes it a run")
    }

    @Test
    fun `a coloured line comes off in plain text and counts as an answer`() {
        assertEquals("I will.", SecondVoice.plain("#4DABF7>I will."))
        assertTrue(SecondVoice.isAnswerLine("#4DABF7>I will."))
        assertTrue(SecondVoice.isAnswerLine("#4DABF7>(I) #F783AC>(will)"))
        assertEquals(
            listOf(0..2, 3..3),
            SecondVoice.lineGroups(listOf("Will you?", "#4DABF7>I will.", "#F783AC>I will.", "Amen")),
        )
    }

    @Test
    fun `a color directive opens a block in that colour, and color or voice 1 closes it`() {
        assertEquals(VoiceKey.color("#F783AC"), blockKeyOf("[color: #F783AC]"))
        assertEquals(VoiceKey.color("#F783AC"), blockKeyOf("  [Colour:#F783AC]  "))
        assertEquals(VoiceKey.MAIN, blockKeyOf("[color]"))
        assertEquals(VoiceKey.MAIN, blockKeyOf("[color: red]"), "a colour that is not hex ends the block")
        assertEquals(VoiceKey(SecondVoice.THIRD), blockKeyOf("[voice: 3]"))
        assertEquals(VoiceKey.MAIN, blockKeyOf("[voice: 1]"))
        assertNull(blockKeyOf("[Verse 1]"))
        assertTrue(SecondVoice.isVoiceDirective("[color: #F783AC]"))
        assertFalse(ChordTransposer.isSectionHeader("[color: #F783AC]"), "a directive is not a section")
    }

    @Test
    fun `a block line is written with the block's mark`() {
        val pink = VoiceKey.color("#F783AC")
        assertEquals("#F783AC>I give you this ring.", SecondVoice.asVoiceLine("I give you this ring.", pink))
        assertEquals("#F783AC>", VoiceKey.color("#F783AC").mark)
        assertEquals("3>", VoiceKey(SecondVoice.THIRD).mark)
        assertEquals(VoiceKey.color("#ABCDEF"), keyOfMark("#ABCDEF"))
        assertEquals(VoiceKey(SecondVoice.SECOND), keyOfMark("2"))
    }
}
