package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.SongSettings

/**
 * Whether [element] appears on no slide, the first only, or every one -- for [target]'s output.
 *
 * Only the number and the title answer this: the lyrics are the slide, and the look-ahead lines
 * follow whether the output has a look-ahead at all. `null` for the rest, which is what tells the
 * element row to leave the control out rather than show one that writes nowhere.
 *
 * These four fields lost their UI when this tab was rewritten -- the columns that held them were
 * left behind unreferenced -- so a song number on the lower third could be seen and not switched
 * off. Reinstated here, beside the chunk control, because "when does this element appear" and "how
 * much of the song is on a slide" are the same kind of question about the same two outputs.
 */
internal fun SongSettings.showFor(element: SongStyleElement, target: SongStyleTarget): String? = when {
    element == SongStyleElement.NUMBER && target.isLowerThird -> showNumberLowerThird
    element == SongStyleElement.NUMBER -> showNumber
    element == SongStyleElement.TITLE && target.isLowerThird -> titleLowerThirdDisplay
    element == SongStyleElement.TITLE -> titleDisplay
    else -> null
}

/**
 * True when the number and the title land in the same place on [target], so their order matters.
 *
 * Where they sit apart -- one above the lyrics, one below -- the slide's own layout already answers
 * which comes first, and offering a switch for it would be offering a choice with no effect.
 */
internal fun SongSettings.numberSharesTitlePosition(target: SongStyleTarget): Boolean =
    if (target.isLowerThird) {
        songNumberLowerThirdPosition == titleLowerThirdPosition &&
            songNumberLowerThirdHorizontalAlignment == titleLowerThirdHorizontalAlignment
    } else {
        songNumberPosition == titlePosition &&
            songNumberHorizontalAlignment == titleHorizontalAlignment
    }

/** The inverse of [showFor]; a no-op for an element that has no such setting. */
internal fun SongSettings.withShow(
    element: SongStyleElement,
    target: SongStyleTarget,
    value: String,
): SongSettings = when {
    element == SongStyleElement.NUMBER && target.isLowerThird -> copy(showNumberLowerThird = value)
    element == SongStyleElement.NUMBER -> copy(showNumber = value)
    element == SongStyleElement.TITLE && target.isLowerThird -> copy(titleLowerThirdDisplay = value)
    element == SongStyleElement.TITLE -> copy(titleDisplay = value)
    else -> this
}
