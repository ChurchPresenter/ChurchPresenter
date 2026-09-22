package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Shadow
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import org.churchpresenter.app.churchpresenter.utils.combinedTextDecoration
import org.churchpresenter.app.churchpresenter.utils.spacingEm
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.MAX_SONG_TRANSLATIONS
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.SongTextStyle
import org.churchpresenter.settings.translationSettings

/**
 * What one language contributes to one slide: the words now, and the words next.
 *
 * [index] is the language's position in the song — `0` is the primary — and is what decides which
 * styling profile draws it and which backdrop block it reports to.
 */
internal data class SongLanguageBlock(
    val index: Int,
    val lines: List<String>,
    val lookAheadLines: List<String>,
) {
    /** Where the look-ahead starts once the two are drawn as one run, or `-1` when there is none. */
    val lookAheadStart: Int get() = if (lookAheadLines.isEmpty()) -1 else lines.size

    /** The two runs concatenated, which is the order they are drawn in. */
    val allLines: List<String> get() = lines + lookAheadLines
}

/**
 * How a slide is being chopped up, which every language is chopped up the same way by.
 *
 * One record rather than four loose flags because both functions below need all four, and a song may
 * now be drawn in four languages -- passing them separately per language was the shape that made
 * these parameter lists unreadable.
 */
internal data class SongSlideModes(
    val lookAheadEnabled: Boolean,
    val isLineMode: Boolean,
    val laIsLineMode: Boolean,
    val lineIndex: Int,
)

/**
 * The slice of [lines] this slide shows: the one line at [SongSlideModes.lineIndex] in line mode,
 * all of them otherwise.
 *
 * A language with no lines slices to none, which is what makes a song translated into two of its
 * four configured languages draw two blocks rather than two blocks and two empty columns.
 */
internal fun slideLinesFor(lines: List<String>, modes: SongSlideModes): List<String> =
    if (modes.isLineMode && modes.lineIndex >= 0 && modes.lineIndex < lines.size) {
        listOf(lines[modes.lineIndex])
    } else {
        lines
    }

/**
 * The look-ahead slice for one language: what comes after [SongSlideModes.lineIndex], whether that
 * is the next line of this section or the start of [nextLines].
 *
 * Written once and called per language. It used to exist twice over — once for the primary and once
 * for the secondary — with the two copies differing only in which list they read, which is why the
 * secondary's version quietly kept a bug the primary's did not.
 */
internal fun lookAheadLinesFor(
    lines: List<String>,
    nextLines: List<String>,
    modes: SongSlideModes,
): List<String> {
    val lineIndex = modes.lineIndex
    // Nothing at all unless this output actually shows a look-ahead. Without this guard a plain
    // line-mode slide drew the *next* line beside the current one, because "the line after this
    // one" exists whether or not anyone asked to see it.
    val bothLineMode = modes.lookAheadEnabled && modes.laIsLineMode && modes.isLineMode
    // In line mode the look-ahead is literally "the line after this one", so a next line inside this
    // section wins over the start of the following one; it only crosses a section boundary once
    // there is nothing left here.
    val nextLineHere = bothLineMode && lineIndex >= 0 && lineIndex + 1 < lines.size
    return when {
        nextLineHere -> listOf(lines[lineIndex + 1])
        nextLines.isEmpty() -> emptyList()
        modes.laIsLineMode -> listOf(nextLines.first())
        else -> nextLines
    }
}

/**
 * The blocks to draw for [section], one per language in [languages], in that order.
 *
 * Languages the song does not carry are dropped rather than drawn empty, and if that leaves nothing
 * the primary is drawn — an output configured only for a language this song was never translated
 * into shows the words rather than an empty screen.
 */
internal fun songLanguageBlocks(
    section: LyricSection,
    nextSection: LyricSection?,
    languages: List<Int>,
    modes: SongSlideModes,
): List<SongLanguageBlock> {
    val available = section.allLanguageLines()
    val next = nextSection?.allLanguageLines() ?: emptyList()
    val chosen = languages.filter { it in available.indices && available[it].isNotEmpty() }
        .ifEmpty { listOf(0) }
    return chosen.map { language ->
        val own = available.getOrElse(language) { emptyList() }
        SongLanguageBlock(
            index = language,
            lines = slideLinesFor(own, modes),
            lookAheadLines = lookAheadLinesFor(own, next.getOrElse(language) { emptyList() }, modes),
        )
    }
}

/**
 * Everything one language needs to draw a line of text.
 *
 * Gathered into a record because a slide now draws up to [MAX_SONG_TRANSLATIONS] of them and each
 * may look different. `SongPresenter` resolved these five as loose `val`s while there was only ever
 * one set of them; per language they have to travel together.
 */
internal data class SongLineStyling(
    val profile: SongTextStyle,
    val color: Color,
    val fontFamily: FontFamily,
    val fontSize: TextUnit,
    val textStyle: TextStyle,
)

/**
 * [SongLineStyling] derived from a stored [profile] — the path taken only by a language that has
 * asked for a look of its own.
 *
 * [autoFitFontSize] is the size the fit settled on, or `null` where this element does not auto-fit.
 * It is a *ceiling* rather than the answer: the configured size still wins when it is the smaller of
 * the two, which is what lets one language be deliberately set smaller than the rest.
 *
 * [shadowOf] is passed in rather than built here because the presenter's own shadow scaling reads
 * the output's scale factor, and a second copy of that arithmetic would drift from it.
 */
internal fun songLineStyling(
    profile: SongTextStyle,
    autoFitFontSize: Int?,
    scaleFactor: Float,
    isKey: Boolean,
    shadowOf: (color: String, size: Int, opacity: Int) -> Shadow,
): SongLineStyling {
    val size = (autoFitFontSize ?: profile.fontSize).coerceAtMost(profile.fontSize)
    val shadow = if (profile.shadow) {
        shadowOf(profile.shadowColor, profile.shadowSize, profile.shadowOpacity)
    } else {
        null
    }
    return SongLineStyling(
        profile = profile,
        // A key output is a matte: every language is drawn white on it, whatever colour it is
        // configured with, exactly as the primary already was.
        color = if (isKey) Color.White else parseHexColor(profile.color),
        fontFamily = systemFontFamilyOrDefault(profile.fontType),
        fontSize = (size * scaleFactor).sp,
        textStyle = TextStyle(
            fontWeight = if (profile.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (profile.italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = combinedTextDecoration(profile.underline, profile.strikethrough),
            letterSpacing = spacingEm(profile.letterSpacing, profile.fontSize).em,
            shadow = shadow,
        ),
    )
}

/**
 * Whether language [index] draws with a look of its own — `0`, the primary, never does.
 *
 * The primary *is* the look every other language inherits, so asking whether it overrides itself is
 * meaningless; answering `false` is what routes it to the already-resolved styling.
 */
internal fun SongSettings.languageOverridesStyle(index: Int): Boolean =
    index > 0 && translationSettings(index - 1).overrideStyle
