package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.core.models.text.TextOutline
import org.churchpresenter.settings.utils.Constants

/**
 * How the app-rendered subtitle overlay looks -- font, colour, size, backdrop and outline.
 *
 * Only applies to subtitles the app parses and draws itself (SRT/WebVTT sidecar files); an
 * embedded track or an `.ass`/`.ssa`/`.sub` file is still baked into the frame by VLC's own
 * renderer and unaffected by any of this. See `SubtitleCueParser`'s doc comment for why.
 *
 * Deliberately global, not a per-output sparse override like [ScreenAssignment.stageMonitorOverride]
 * -- the same shape [STTSettings] uses. [ScreenAssignment.showSubtitles] is the one thing that does
 * vary per output: whether this styling is shown there at all.
 */
@Serializable
data class MediaSettings(
    val textColor: String = "#FFFFFF",
    val backgroundColor: String = "transparent",
    val backgroundOpacity: Int = 0,
    val fontSize: Int = 42,
    val lineSpacing: Int = 130, // line height as percentage of font size (100 = no extra spacing)
    val fontType: String = "Arial",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val shadow: Boolean = false,
    val shadowColor: String = "#000000",
    val shadowSize: Int = 100,
    val shadowOpacity: Int = 78,
    /** The band behind each line and the box around the block. */
    val backdrop: TextBackdrop = TextBackdrop(),
    /** The stroke drawn around the glyphs, under the fill. */
    val outline: TextOutline = TextOutline(),
    val position: String = Constants.BOTTOM_CENTER,
    val maxLines: Int = 2,
)
