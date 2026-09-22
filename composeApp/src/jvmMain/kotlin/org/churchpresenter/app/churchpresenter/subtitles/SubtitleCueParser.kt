package org.churchpresenter.app.churchpresenter.subtitles

import java.io.File

/**
 * Parses SRT and WebVTT subtitle files into [SubtitleCue]s the app renders itself, so their look
 * (font, colour, size, outline) is under `MediaSettings` rather than libVLC's baked-in freetype
 * renderer.
 *
 * Deliberately not extended to `.ass`/`.ssa`/`.sub`: those carry per-cue style overrides, karaoke
 * timing and positioning that a plain text-and-timestamp model can't represent, and VLC keeps
 * rendering them exactly as before -- [parseSubtitleFile] returns an empty list for them, which is
 * the caller's signal to fall back to handing VLC the file directly.
 */
object SubtitleCueParser {

    private val srtTimecode = Regex("""(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})""")
    private val srtArrow = Regex(
        """(\d{1,2}:\d{2}:\d{2}[,.]\d{1,3})\s*-->\s*(\d{1,2}:\d{2}:\d{2}[,.]\d{1,3})"""
    )
    private val vttTimecode = Regex("""(?:(\d{1,2}):)?(\d{2}):(\d{2})[.](\d{1,3})""")
    private val vttArrow = Regex(
        """((?:\d{1,2}:)?\d{2}:\d{2}[.]\d{1,3})\s*-->\s*((?:\d{1,2}:)?\d{2}:\d{2}[.]\d{1,3})"""
    )

    /** Dispatches on [file]'s extension; an unsupported one (or a file that fails to parse) yields `emptyList()`. */
    fun parseSubtitleFile(file: File): List<SubtitleCue> {
        if (!file.isFile) return emptyList()
        return try {
            when (file.extension.lowercase()) {
                "srt" -> parseSrt(file.readText())
                "vtt" -> parseWebVtt(file.readText())
                else -> emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Parses a SubRip (`.srt`) file's blocks: an index line, a `-->` timecode line, then text. */
    fun parseSrt(text: String): List<SubtitleCue> =
        text.replace("\r\n", "\n").split(Regex("\n\\s*\n"))
            .mapNotNull { block -> parseBlock(block, srtArrow, ::parseSrtTimecode) }

    /** Parses a WebVTT (`.vtt`) file. The leading `WEBVTT` header and any `NOTE` blocks are skipped. */
    fun parseWebVtt(text: String): List<SubtitleCue> =
        text.replace("\r\n", "\n").split(Regex("\n\\s*\n"))
            .mapNotNull { block -> parseBlock(block, vttArrow, ::parseVttTimecode) }

    /** One cue block: an optional identifier line, the `-->` line (with optional trailing cue settings), then text. */
    private fun parseBlock(
        block: String,
        arrow: Regex,
        toMs: (String) -> Long?,
    ): SubtitleCue? {
        val lines = block.lines().filter { it.isNotBlank() }
        val arrowLineIndex = lines.indexOfFirst { arrow.containsMatchIn(it) }
        if (arrowLineIndex < 0) return null
        val match = arrow.find(lines[arrowLineIndex])
        val start = match?.let { toMs(it.groupValues[1]) }
        val end = match?.let { toMs(it.groupValues[2]) }
        val cueText = lines.drop(arrowLineIndex + 1).joinToString("\n").trim()
        return if (start != null && end != null && cueText.isNotEmpty()) {
            SubtitleCue(start, end, stripMarkup(cueText))
        } else {
            null
        }
    }

    /** Drops WebVTT/SRT inline markup tags (`<b>`, `<i>`, `<c.classname>`, …) — plain text only, no per-run styling. */
    private fun stripMarkup(text: String): String = text.replace(Regex("<[^>]*>"), "")

    private fun parseSrtTimecode(value: String): Long? = toMs(srtTimecode, value)

    private fun parseVttTimecode(value: String): Long? = toMs(vttTimecode, value)

    private fun toMs(pattern: Regex, value: String): Long? {
        val match = pattern.matchEntire(value.trim()) ?: return null
        val hours = match.groupValues[1]
        val minutes = match.groupValues[2]
        val seconds = match.groupValues[3]
        val millis = match.groupValues[4]
        val ms = millis.padEnd(MILLIS_DIGITS, '0').take(MILLIS_DIGITS)
        return hours.ifBlank { "0" }.toLong() * MS_PER_HOUR +
            minutes.toLong() * MS_PER_MINUTE +
            seconds.toLong() * MS_PER_SECOND +
            ms.toLong()
    }

    private const val MILLIS_DIGITS = 3
    private const val MS_PER_SECOND = 1_000L
    private const val MS_PER_MINUTE = 60_000L
    private const val MS_PER_HOUR = 3_600_000L

    /** The cue whose window contains [positionMs], or `null` between cues. Cues are assumed chronological. */
    fun activeCueAt(cues: List<SubtitleCue>, positionMs: Long): SubtitleCue? =
        cues.firstOrNull { positionMs in it.startMs..it.endMs }
}
