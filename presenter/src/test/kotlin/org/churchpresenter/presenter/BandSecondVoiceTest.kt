package org.churchpresenter.presenter

import androidx.compose.ui.geometry.Rect
import io.github.alexzhirkevich.compottie.internal.helpers.text.TextJustify
import org.churchpresenter.core.models.songs.SongTextOverrides
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.lottiegen.band.SlotLayout
import org.churchpresenter.lottiegen.band.TextAnimation
import org.churchpresenter.settings.SongSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Where the second voice's colour goes on a Lottie band, when it shows, and a song's own look
 * over the profile's.
 */
class BandSecondVoiceTest {

    // ── The rectangles the voice pass is cut to ─────────────────────────────────

    private val box = LottieSlotBox(x = 100f, y = 0f, w = 200f, h = 100f)

    /** Every character 10 px wide, so the expected edges are easy to read. */
    private val tenEach: (Char) -> Float = { 10f }

    private fun layout(text: String, ranges: List<IntRange>, lines: List<String>, justify: TextJustify) =
        BandVoiceLayout(text, ranges, lines, justify, box, top = 20f, lineHeight = 30f)

    @Test
    fun `a left-justified run starts at the box's left edge plus the words before it`() {
        val text = "but now I see"
        val rects = secondVoiceRects(layout(text, listOf(4..12), listOf(text), TextJustify.Left), tenEach)

        assertEquals(listOf(Rect(140f, 20f, 230f, 50f)), rects)
    }

    @Test
    fun `right and centre justification move the run with the line`() {
        val text = "abcd"
        val ranges = listOf(2..3)

        assertEquals(
            listOf(Rect(280f, 20f, 300f, 50f)),
            secondVoiceRects(layout(text, ranges, listOf(text), TextJustify.Right), tenEach),
        )
        assertEquals(
            listOf(Rect(200f, 20f, 220f, 50f)),
            secondVoiceRects(layout(text, ranges, listOf(text), TextJustify.Center), tenEach),
        )
    }

    @Test
    fun `a wrapped run gives one rectangle per line it sits on`() {
        // "aa bbb cc" wrapped after "aa bbb"; the second voice sings "bbb cc".
        val text = "aa bbb cc"
        val rects = secondVoiceRects(
            layout(text, listOf(3..8), listOf("aa bbb", "cc"), TextJustify.Left),
            tenEach,
        )

        assertEquals(listOf(Rect(130f, 20f, 160f, 50f), Rect(100f, 50f, 120f, 80f)), rects)
    }

    @Test
    fun `two runs on a line are two rectangles, and a line with none gives none`() {
        val text = "a b c\nplain"
        val rects = secondVoiceRects(
            layout(text, listOf(0..0, 4..4), listOf("a b c", "plain"), TextJustify.Left),
            tenEach,
        )

        assertEquals(listOf(Rect(100f, 20f, 110f, 50f), Rect(140f, 20f, 150f, 50f)), rects)
    }

    @Test
    fun `an empty line or one not found in the text is skipped`() {
        val rects = secondVoiceRects(
            layout("abc", listOf(0..2), listOf("", "zzz", "abc"), TextJustify.Left),
            tenEach,
        )

        assertEquals(listOf(Rect(100f, 80f, 130f, 110f)), rects, "the third row, all of it")
    }

    // ── When the pass shows ─────────────────────────────────────────────────────

    private fun template(animation: TextAnimation) = assertNotNull(
        parseBibleLottieTemplate(
            LottieBandTestSupport.templateJson(
                BibleLottieGenConfig(
                    canvasW = 960, canvasH = 180, layout = SlotLayout.SINGLE,
                    bgInSeconds = 0.5f, textInSeconds = 0.5f, holdSeconds = 1f,
                    textOutSeconds = 0.5f, bgOutSeconds = 0.5f,
                    textAnimation = animation,
                ),
            ),
        ),
    )

    private val text = setOf("Text1")

    @Test
    fun `text that only fades stands still, so the pass always shows`() {
        val fade = template(TextAnimation.FADE)

        assertTrue(fade.movingTextLayers.isEmpty())
        assertTrue(voicePassShows(fade, text, 0f))
        assertTrue(voicePassShows(fade, text, fade.totalFrames))
    }

    @Test
    fun `text that slides shows the pass only while it is held`() {
        val slide = template(TextAnimation.SLIDE_UP)
        val textIn = slide.segment(BibleLottieTemplate.SEGMENT_TEXT_IN)
        val textOut = slide.segment(BibleLottieTemplate.SEGMENT_TEXT_OUT)

        assertTrue("Text1" in slide.movingTextLayers)
        assertFalse(voicePassShows(slide, text, textIn.startFrame), "still sliding in")
        assertTrue(voicePassShows(slide, text, textIn.endFrame))
        assertTrue(voicePassShows(slide, text, textOut.startFrame))
        assertFalse(voicePassShows(slide, text, textOut.endFrame), "sliding out")
    }

    @Test
    fun `text the player types in waits for the typing to finish`() {
        val typed = template(TextAnimation.TYPEWRITER)

        assertFalse(voicePassShows(typed, text, typed.segment(BibleLottieTemplate.SEGMENT_TEXT_IN).startFrame))
        assertTrue(voicePassShows(typed, text, typed.segment(BibleLottieTemplate.SEGMENT_HOLD).startFrame))
    }

    @Test
    fun `a layer the pass does not draw does not hold it back`() {
        val slide = template(TextAnimation.SLIDE_UP)

        assertEquals(BandTextMotion.NONE, slide.meta.textMotion, "the file moves the text, not the player")
        assertTrue(voicePassShows(slide, setOf("NotMoving"), 0f))
    }

    // ── A song's own look over the profile's ────────────────────────────────────

    @Test
    fun `no overrides hand back the settings themselves`() {
        val settings = SongSettings()

        assertSame(settings, settings.withTextOverrides(SongTextOverrides()))
    }

    @Test
    fun `every override reaches full screen and the lower third`() {
        val base = SongSettings(lyricsFontSize = 60, lyricsLowerThirdFontSize = 40)

        val out = base.withTextOverrides(SongTextOverrides("Georgia", 150, "#A8D8FF", "#FF8A65"))

        assertEquals("Georgia", out.lyricsFontType)
        assertEquals("Georgia", out.lyricsLowerThirdFontType)
        assertEquals(90, out.lyricsFontSize)
        assertEquals(60, out.lyricsLowerThirdFontSize)
        assertEquals("#A8D8FF", out.lyricsColor)
        assertEquals("#A8D8FF", out.lyricsLowerThirdColor)
        assertEquals("#FF8A65", out.layoutExtras.secondVoiceColor)
    }

    @Test
    fun `what the song leaves blank stays the profile's`() {
        val base = SongSettings(lyricsFontType = "Inter", lyricsColor = "#FFFFFF")

        val out = base.withTextOverrides(SongTextOverrides(sizePercent = 90))

        assertEquals("Inter", out.lyricsFontType)
        assertEquals("#FFFFFF", out.lyricsColor)
        assertEquals(base.layoutExtras.secondVoiceColor, out.layoutExtras.secondVoiceColor)
    }
}
