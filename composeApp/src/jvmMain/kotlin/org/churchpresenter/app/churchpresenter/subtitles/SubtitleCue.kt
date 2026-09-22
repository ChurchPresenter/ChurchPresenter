package org.churchpresenter.app.churchpresenter.subtitles

/**
 * One subtitle line the app renders itself, timed against the media's playback position.
 *
 * Not `@Serializable` — this lives only in memory for the lifetime of a loaded clip, parsed fresh
 * from the subtitle file each time one is chosen. [text] may already contain embedded newlines
 * (a cue spanning two lines in the source file).
 */
data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)
