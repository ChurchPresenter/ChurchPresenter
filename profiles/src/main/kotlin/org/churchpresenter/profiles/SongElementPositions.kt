package org.churchpresenter.profiles

import org.churchpresenter.presenter.SongStyleElement
import org.churchpresenter.settings.SongSettings

/**
 * Where the elements placed without a `position` field of their own sit -- the section label and the
 * next-section line -- or null for any other element.
 *
 * The title and the number keep theirs in [SongElementStyle.position], per language as the rest of
 * their look is. These two store it in `SongLayoutExtras`, one value per output: [SongSettings]
 * has no constructor slot left for more flat fields.
 */
internal fun SongSettings.storedPosition(element: SongStyleElement, lowerThird: Boolean): String? = when (element) {
    SongStyleElement.SECTION_LABEL -> layoutExtras.sectionLabel.positionFor(lowerThird)
    SongStyleElement.NEXT_SECTION -> layoutExtras.nextSectionPosition.positionFor(lowerThird)
    else -> null
}

/** The inverse of [storedPosition]; unchanged for an element it does not cover. */
internal fun SongSettings.withStoredPosition(
    element: SongStyleElement,
    lowerThird: Boolean,
    position: String,
): SongSettings = when (element) {
    SongStyleElement.SECTION_LABEL -> {
        val label = layoutExtras.sectionLabel
        val placed = if (lowerThird) label.copy(lowerThirdPosition = position) else label.copy(position = position)
        copy(layoutExtras = layoutExtras.copy(sectionLabel = placed))
    }
    SongStyleElement.NEXT_SECTION -> copy(
        layoutExtras = layoutExtras.copy(
            nextSectionPosition = layoutExtras.nextSectionPosition.withPosition(lowerThird, position),
        ),
    )
    else -> this
}
