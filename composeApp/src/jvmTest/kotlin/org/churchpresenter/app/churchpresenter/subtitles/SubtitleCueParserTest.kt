package org.churchpresenter.app.churchpresenter.subtitles

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SRT/WebVTT cues, parsed into what [SubtitleOverlay][org.churchpresenter.app.churchpresenter.presenter]
 * actually needs: a start, an end and the text — nothing depends on file layout beyond that, so
 * these tests exercise timing and text extraction, not incidental formatting.
 */
class SubtitleCueParserTest {

    // ── SRT ─────────────────────────────────────────────────────────────

    @Test
    fun `srt cues are parsed with their timing`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:04,000
            Hello there

            2
            00:00:05,500 --> 00:00:08,250
            Second line
        """.trimIndent()

        val cues = SubtitleCueParser.parseSrt(srt)

        assertEquals(
            listOf(
                SubtitleCue(1_000, 4_000, "Hello there"),
                SubtitleCue(5_500, 8_250, "Second line"),
            ),
            cues,
        )
    }

    @Test
    fun `srt cue text spanning two lines keeps its line break`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:02,000
            Line one
            Line two
        """.trimIndent()

        assertEquals("Line one\nLine two", SubtitleCueParser.parseSrt(srt).single().text)
    }

    @Test
    fun `srt inline markup is stripped to plain text`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:02,000
            <b>Blessed</b> are the <i>peacemakers</i>
        """.trimIndent()

        assertEquals("Blessed are the peacemakers", SubtitleCueParser.parseSrt(srt).single().text)
    }

    // ── WebVTT ──────────────────────────────────────────────────────────

    @Test
    fun `vtt cues are parsed, header and cue settings ignored`() {
        val vtt = """
            WEBVTT

            00:00:01.000 --> 00:00:04.000 align:start position:10%
            Hello there

            00:01:05.500 --> 00:01:08.250
            An hour marker
        """.trimIndent()

        val cues = SubtitleCueParser.parseWebVtt(vtt)

        assertEquals(
            listOf(
                SubtitleCue(1_000, 4_000, "Hello there"),
                SubtitleCue(65_500, 68_250, "An hour marker"),
            ),
            cues,
        )
    }

    @Test
    fun `vtt cue identifiers are skipped`() {
        val vtt = """
            WEBVTT

            greeting
            00:00:01.000 --> 00:00:02.000
            Hi
        """.trimIndent()

        assertEquals(SubtitleCue(1_000, 2_000, "Hi"), SubtitleCueParser.parseWebVtt(vtt).single())
    }

    @Test
    fun `vtt timecodes without an hour component parse with hours treated as zero`() {
        val vtt = """
            WEBVTT

            01:02.500 --> 01:05.000
            Short form
        """.trimIndent()

        assertEquals(SubtitleCue(62_500, 65_000, "Short form"), SubtitleCueParser.parseWebVtt(vtt).single())
    }

    @Test
    fun `a block whose text is blank after the arrow line yields no cue`() {
        val srt = "1\n00:00:01,000 --> 00:00:02,000\n"
        assertTrue(SubtitleCueParser.parseSrt(srt).isEmpty())
    }

    // ── parseSubtitleFile dispatch ──────────────────────────────────────

    @Test
    fun `an srt file on disk is parsed by extension`() {
        val temp = kotlin.io.path.createTempFile(suffix = ".srt").toFile()
        temp.writeText("1\n00:00:01,000 --> 00:00:02,000\nHi\n")
        assertEquals(listOf(SubtitleCue(1_000, 2_000, "Hi")), SubtitleCueParser.parseSubtitleFile(temp))
        temp.delete()
    }

    @Test
    fun `a vtt file on disk is parsed by extension`() {
        val temp = kotlin.io.path.createTempFile(suffix = ".vtt").toFile()
        temp.writeText("WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHi\n")
        assertEquals(listOf(SubtitleCue(1_000, 2_000, "Hi")), SubtitleCueParser.parseSubtitleFile(temp))
        temp.delete()
    }

    @Test
    fun `an unsupported extension yields no cues, signalling the VLC fallback`() {
        val temp = kotlin.io.path.createTempFile(suffix = ".ass").toFile()
        temp.writeText("[Script Info]\n")
        assertTrue(SubtitleCueParser.parseSubtitleFile(temp).isEmpty())
        temp.delete()
    }

    @Test
    fun `a missing file yields no cues rather than throwing`() {
        assertTrue(SubtitleCueParser.parseSubtitleFile(File("/no/such/file.srt")).isEmpty())
    }

    // ── activeCueAt ─────────────────────────────────────────────────────

    @Test
    fun `activeCueAt finds the cue whose window contains the position`() {
        val cues = listOf(SubtitleCue(1_000, 4_000, "first"), SubtitleCue(5_000, 8_000, "second"))
        assertEquals(cues[0], SubtitleCueParser.activeCueAt(cues, 2_000))
        assertEquals(cues[1], SubtitleCueParser.activeCueAt(cues, 8_000))
    }

    @Test
    fun `activeCueAt returns null between cues`() {
        val cues = listOf(SubtitleCue(1_000, 2_000, "first"), SubtitleCue(5_000, 6_000, "second"))
        assertNull(SubtitleCueParser.activeCueAt(cues, 3_000))
    }
}
