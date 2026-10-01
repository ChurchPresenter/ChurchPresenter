package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.churchpresenter.app.churchpresenter.viewmodel.STTSegment
import org.churchpresenter.settings.CAPTION_BREAK_SEGMENT
import org.churchpresenter.settings.CAPTION_BREAK_SENTENCE
import org.churchpresenter.settings.CAPTION_STYLE_POP_ON
import org.churchpresenter.settings.CAPTION_STYLE_TICKER
import org.churchpresenter.settings.CaptionReading
import org.churchpresenter.settings.STTSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How a side's caption is shaped before it is drawn -- the line breaks, wrapping, capitals and
 * dimming of the reading settings -- and how fast it is let onto the screen.
 */
class CaptionBodyTest {

    private fun segment(text: String, id: Int = 1) =
        STTSegment(id = id, timestamp = "", text = text, start = 0.0, end = 1.0, completed = true)

    private val sermon = listOf(
        segment("Grace and peace to you. Let us pray.", id = 1),
        segment("The Lord is my shepherd", id = 2),
    )

    // ── Line breaks ─────────────────────────────────────────────────────────────

    @Test
    fun `with breaks off the segments run on, each one a unit for the dimming`() {
        val body = captionBody(sermon, null, CaptionReading(), allCaps = false)
        assertEquals("Grace and peace to you. Let us pray. The Lord is my shepherd", body.text)
        assertEquals(
            listOf("Grace and peace to you. Let us pray.", "The Lord is my shepherd"),
            body.units.map(body.text::substring),
        )
        assertNull(body.inProgressStart)
    }

    @Test
    fun `a phrase each starts every segment on a line of its own, a blank line between when asked`() {
        val reading = CaptionReading(lineBreaks = CAPTION_BREAK_SEGMENT)
        assertEquals(
            "Grace and peace to you. Let us pray.\nThe Lord is my shepherd",
            captionBody(sermon, null, reading, allCaps = false).text,
        )
        assertEquals(
            "Grace and peace to you. Let us pray.\n\nThe Lord is my shepherd",
            captionBody(sermon, null, reading.copy(blankLineBetween = true), allCaps = false).text,
        )
    }

    @Test
    fun `a sentence each breaks inside a segment and joins across one`() {
        val body = captionBody(sermon, "Amen", CaptionReading(lineBreaks = CAPTION_BREAK_SENTENCE), allCaps = false)
        assertEquals("Grace and peace to you.\nLet us pray.\nThe Lord is my shepherd\nAmen", body.text)
        assertEquals(4, body.units.size)
        assertEquals("Amen", body.text.substring(body.inProgressStart!!), "in-progress words start a line of their own")
    }

    @Test
    fun `in-progress words follow the segments and are marked where they start`() {
        val body = captionBody(sermon.take(1), "I will fear", CaptionReading(), allCaps = false)
        assertEquals("I will fear", body.text.substring(body.inProgressStart!!))
        assertEquals(captionBody(emptyList(), "  ", CaptionReading(), allCaps = false).text, "", "blank is nothing")
    }

    // ── Wrapping and capitals ───────────────────────────────────────────────────

    @Test
    fun `wrapping at a character count turns spaces into breaks and keeps every character in place`() {
        val text = "The Lord is my shepherd I shall not want"
        val wrapped = wrapAtChars(text, 12)
        assertEquals(text.length, wrapped.length)
        assertTrue(wrapped.split('\n').all { it.length <= 12 }, wrapped)
        assertEquals(text, wrapped.replace('\n', ' '))
        assertEquals(text, wrapAtChars(text, 0), "0 wraps at the edge only")
        assertEquals("Hallelujah!\nAmen", wrapAtChars("Hallelujah! Amen", 5), "a long word keeps a line to itself")
    }

    @Test
    fun `capitals change no character's place, so the ranges found before still hold`() {
        assertEquals("STRASSE", "strasse".let(::upperCaseInPlace))
        assertEquals(1, upperCaseInPlace("ß").length, "a letter whose capital is two letters stays one")
        val body = captionBody(sermon, null, CaptionReading(maxCharsPerLine = 15), allCaps = true)
        assertEquals(body.text.uppercase().length, body.text.length)
        assertTrue(body.text.none { it.isLowerCase() })
    }

    // ── Dimming ─────────────────────────────────────────────────────────────────

    @Test
    fun `older units dim by the step, never below the floor, and nothing dims when it is off`() {
        val reading = CaptionReading(dimOlderLines = true, dimStepPercent = 30, dimFloorPercent = 50)
        assertEquals(listOf(0.5f, 0.7f, 1f), unitAlphas(3, reading).map { (it * 100).toInt() / 100f })
        assertEquals(listOf(1f, 1f, 1f), unitAlphas(3, CaptionReading()))
        assertEquals(emptyList(), unitAlphas(0, reading))
    }

    @Test
    fun `the painted caption dims older units and leaves the in-progress words their own shade`() {
        val reading = CaptionReading(dimOlderLines = true, dimStepPercent = 50, dimFloorPercent = 10)
        val body = captionBody(sermon, "Amen", reading, allCaps = false)
        val painted = buildDisplayText(body, hasSegments = true, reading, CaptionInk(Color.White, emptyList(), null))
        fun alphaAt(index: Int) = painted.spanStyles.first { index in it.start until it.end }.item.color.alpha
        assertEquals(0.5f, alphaAt(0), 0.01f)
        assertEquals(1f, alphaAt(body.units[1].first), 0.01f)
        assertEquals(0.6f, alphaAt(body.inProgressStart!!), 0.01f)
    }

    // ── Pacing ──────────────────────────────────────────────────────────────────

    @Test
    fun `the reveal pace follows the drip feed, the reading limit, and the presentation`() {
        val drip = STTSettings(dripFeedEnabled = true, dripFeedSpeed = 25)
        assertEquals(RevealPace(25, RevealUnit.LETTER), revealPace(drip))
        val limited = CaptionReading(readingSpeedLimit = true, readingSpeedCps = 10)
        assertEquals(RevealPace(100, RevealUnit.LETTER), revealPace(drip.copy(reading = limited)), "it slows typing")
        assertEquals(
            RevealPace(100, RevealUnit.WORD),
            revealPace(STTSettings(dripFeedEnabled = false, reading = limited)),
        )
        assertNull(revealPace(STTSettings(dripFeedEnabled = false)))
        assertEquals(
            RevealPace(100, RevealUnit.SEGMENT),
            revealPace(drip.copy(reading = limited.copy(style = CAPTION_STYLE_POP_ON))),
            "pop-on never types: the limit holds whole segments back",
        )
        assertNull(revealPace(drip.copy(reading = CaptionReading(style = CAPTION_STYLE_POP_ON))))
        assertEquals(
            RevealPace(100, RevealUnit.WORD),
            revealPace(drip.copy(reading = limited.copy(style = CAPTION_STYLE_TICKER))),
        )
    }

    @Test
    fun `a step ends at the next word or the next segment`() {
        val text = captionText(sermon)
        assertEquals(5, nextWordEnd(text, 0))
        assertEquals(9, nextWordEnd(text, 5), "past the space, to the end of the next word")
        assertEquals(text.length, nextWordEnd(text, text.length))
        assertEquals("Grace and peace to you. Let us pray.".length, nextSegmentEnd(sermon, 0))
        assertEquals(text.length, nextSegmentEnd(sermon, 40))
    }

    // ── Ticker ──────────────────────────────────────────────────────────────────

    @Test
    fun `a ticker is fed only what is new, running on when the caption only grew`() {
        val (first, runsOn) = newTickerText("", AnnotatedString("Grace and peace"))
        assertEquals("Grace and peace" to false, first.text to runsOn)
        val (grown, grownRunsOn) = newTickerText("Grace and peace", AnnotatedString("Grace and peace to you"))
        assertEquals(" to you" to true, grown.text to grownRunsOn)
        val (scrolled, _) = newTickerText("Grace and peace to you", AnnotatedString("peace to you. Let us pray"))
        assertEquals(". Let us pray", scrolled.text, "the window dropped words off the front")
        val (rewritten, rewrittenRunsOn) = newTickerText("Grace", AnnotatedString("Amen\nAmen"))
        assertEquals("Amen Amen" to false, rewritten.text to rewrittenRunsOn, "line breaks become spaces")
    }

    // ── Two languages ───────────────────────────────────────────────────────────

    @Test
    fun `interleaving pairs each segment with its translation by id, in the order asked`() {
        val english = listOf(segment("Grace and peace.", 1), segment("Let us pray.", 2), segment("Amen.", 3))
        val spanish = listOf(segment("Oremos.", 2), segment("Gracia y paz.", 1))
        val ink = CaptionInk(Color.White, emptyList(), null)
        val look = TextStyle(fontSize = 20.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold)
        val side = { segs: List<STTSegment>, progress: String? -> CaptionSide(segs, progress, ink, allCaps = false) }
        val s = STTSettings()
        val first = interleavedCaption(side(english, null), side(spanish, null), look, translationFirst = false, s)
        assertEquals("Grace and peace.\nGracia y paz.\nLet us pray.\nOremos.\nAmen.", first.text)
        val italic = first.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }
            .map { first.text.substring(it.start, it.end) }
        assertEquals(listOf("Gracia y paz.", "Oremos."), italic, "the translation carries its own look")
        val inverse = interleavedCaption(
            side(english.take(1), "And"), side(spanish, "Y"), look, translationFirst = true, s,
        )
        assertEquals("Gracia y paz.\nGrace and peace.\nY\nAnd", inverse.text)
    }

    @Test
    fun `the translation's look is the transcript's with its own size, weight and slant`() {
        val base = TextStyle(fontSize = 40.sp, lineHeight = 52.sp, fontWeight = FontWeight.Normal)
        assertEquals(base.fontSize, translationTextStyle(base, STTSettings()).fontSize)
        val own = translationTextStyle(
            base,
            STTSettings(translationFontSize = 30, translationBold = true, translationItalic = true, lineSpacing = 120),
        )
        assertEquals(30.sp, own.fontSize)
        assertEquals(36.sp, own.lineHeight)
        assertEquals(FontWeight.Bold, own.fontWeight)
        assertEquals(FontStyle.Italic, own.fontStyle)
        assertFalse(translationTextStyle(base, STTSettings(translationItalic = false)).fontStyle == FontStyle.Italic)
    }
}
