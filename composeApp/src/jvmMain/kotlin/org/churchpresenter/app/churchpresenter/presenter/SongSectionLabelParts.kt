package org.churchpresenter.app.churchpresenter.presenter

import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.SongSectionLabel
import org.churchpresenter.settings.utils.Constants

/**
 * The section label's own pieces, lifted out of `SongPresenter.kt`.
 *
 * Not because they do not belong there -- they are only used there -- but because that file is at
 * detekt's per-file function ceiling, which is the signal it has become large enough that anything
 * separable should be separate.
 */

/**
 * The label text the lyrics' auto-fit has to leave room for, or null when it costs them nothing.
 *
 * This decision is the whole of a bug that predates the label's styling: the fit's `reserved`
 * accumulator counted the title row, the number row, the look-ahead spacer and the gaps between
 * language blocks, and **never the section label at all** -- though the label sits above the lyrics
 * and takes height from them exactly as the title row does. With the label on, the lyrics were sized
 * for a box one label taller than the one they got, and could overflow.
 *
 * Null in three cases, and the third is the one worth naming: a label at the **bottom edge** is drawn
 * over the lyrics' area rather than beside them, as the title and number at that edge are, so
 * reserving for it would shrink the lyrics for height it does not take from them.
 *
 * The longest of them, because the fit has to hold for every section the song will show, not just
 * the one on screen -- the same reasoning the title and number reservations above it use.
 */
internal fun sectionLabelToReserve(
    label: SongSectionLabel,
    sections: List<LyricSection>,
    isLowerThird: Boolean,
): String? {
    if (!label.enabled) return null
    if (label.positionFor(isLowerThird) == Constants.BELOW_VERSE) return null
    return sections
        .mapNotNull { sectionLabelText(it, label, isTitleSlide = false) }
        .maxByOrNull { it.length }
        ?.takeIf { it.isNotEmpty() }
}

/**
 * The section label to draw, or null when there is nothing to draw or it is switched off.
 *
 * The brackets come from the data, not from here: `LyricSection.header` is the header line as the
 * song file wrote it, `[Verse 1]` or `{Chorus}`, and the label showed them because it drew that
 * string as it stood. `shouldShowText` has stripped them for its own purposes all along; this is
 * the same strip, so the label reads "Verse 1" on screen the way the settings preview always
 * promised it would. A song with no header falls back to its humanized type, which has no brackets
 * to lose.
 */
internal fun sectionLabelText(
    section: LyricSection,
    settings: SongSectionLabel,
    isTitleSlide: Boolean,
): String? {
    if (!settings.enabled || isTitleSlide) return null
    val header = section.header?.takeIf { it.isNotBlank() }
    val raw = header ?: section.type.replaceFirstChar { it.uppercase() }
    val stripped = raw.trim()
        .removePrefix("[").removePrefix("{")
        .removeSuffix("]").removeSuffix("}")
        .trim()
    return stripped.takeIf { it.isNotBlank() }
}
