package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.ElementOffset
import org.churchpresenter.settings.OutputElementOffset
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.SongTitleSlideOffsets

/**
 * Reading and writing one title-slide element's offset, in the shape the settings panel wants.
 *
 * Its own file rather than beside [elementStyle] in `SongElementStyle.kt` because that file had
 * reached detekt's per-file function ceiling, which is the signal that anything separable should be
 * separate -- the same reason `SongSectionLabelParts.kt` exists.
 */

/**
 * Where [element] sits on the title slide for [target], or null while it is laid out in the stack.
 *
 * Null for every element the title slide does not draw, and for the number -- which has a corner and
 * a nudge of its own instead, on `SongTitleSlideNumber`. A caller does not have to check either: an
 * element with nowhere to store an offset simply has none.
 */
internal fun SongSettings.titleSlideOffset(
    element: SongStyleElement,
    target: SongStyleTarget,
): ElementOffset? = layoutExtras.titleSlideOffsets
    .slotFor(element)
    ?.forOutput(target.isLowerThird)

/** [titleSlideOffset]'s inverse: these settings with [element]'s offset on [target] set to [value]. */
internal fun SongSettings.withTitleSlideOffset(
    element: SongStyleElement,
    target: SongStyleTarget,
    value: ElementOffset?,
): SongSettings {
    val offsets = layoutExtras.titleSlideOffsets
    val slot = offsets.slotFor(element)?.withOutput(target.isLowerThird, value) ?: return this
    val next = when (element) {
        SongStyleElement.TITLE -> offsets.copy(title = slot)
        SongStyleElement.AUTHOR -> offsets.copy(author = slot)
        SongStyleElement.COMPOSER -> offsets.copy(composer = slot)
        SongStyleElement.CCLI -> offsets.copy(ccli = slot)
        SongStyleElement.TEMPO -> offsets.copy(tempo = slot)
        else -> return this
    }
    return copy(layoutExtras = layoutExtras.copy(titleSlideOffsets = next))
}

/** The pair [element] stores its two outputs' offsets in, or null when it has no slot. */
private fun SongTitleSlideOffsets.slotFor(element: SongStyleElement): OutputElementOffset? = when (element) {
    SongStyleElement.TITLE -> title
    SongStyleElement.AUTHOR -> author
    SongStyleElement.COMPOSER -> composer
    SongStyleElement.CCLI -> ccli
    SongStyleElement.TEMPO -> tempo
    else -> null
}

/** Test handle for one title-slide element's positioning switch. */
internal fun titleSlideOffsetTag(element: SongStyleElement): String =
    "title_slide_offset_${element.name.lowercase()}"
