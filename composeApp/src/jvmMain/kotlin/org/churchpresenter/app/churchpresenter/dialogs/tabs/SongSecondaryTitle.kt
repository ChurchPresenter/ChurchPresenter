package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.SongSettings

/**
 * The second **title**'s profile, and the accessors that let one set of controls point at either
 * language's.
 *
 * Beside [SongStyleLanguage] rather than in it: the lyrics' half of this was already a file's worth,
 * and the two together are more functions than one file is allowed to carry.
 */

/**
 * The second **title**'s profile as the panel should show it: its own once it has one, and the
 * first title's until then -- the same fallback [secondaryLyricsStyle] makes for the lyrics.
 *
 * A bilingual song has two titles. The title slide draws both of them when the output shows both
 * languages, and a lyric slide draws whichever one the output asks for; until this record is
 * switched on, every one of those is drawn in the first title's profile.
 */
internal fun SongSettings.secondaryTitleStyle(target: SongStyleTarget): SongElementStyle =
    if (secondaryTitleLanguage.enabled) {
        secondaryTitleLanguage.styleFor(target.isLowerThird).toElementStyle()
    } else {
        titleStyleFor(target)
    }

/**
 * These settings with the second title set to [style].
 *
 * Seeds **both** outputs from the first title on that first edit, for the reason
 * [withSecondaryLyricsStyle] does: the flag covers the whole record, so the moment it goes on the
 * output that was not being styled starts reading its stored profile too -- and that profile is the
 * class default until something is put in it.
 */
internal fun SongSettings.withSecondaryTitleStyle(
    target: SongStyleTarget,
    style: SongElementStyle,
): SongSettings {
    val seeded = if (secondaryTitleLanguage.enabled) {
        secondaryTitleLanguage
    } else {
        secondaryTitleLanguage.copy(
            fullScreen = titleStyleFor(SongStyleTarget.FULL_SCREEN).toLyricStyle(),
            lowerThird = titleStyleFor(SongStyleTarget.LOWER_THIRD).toLyricStyle(),
        )
    }
    return copy(secondaryTitleLanguage = seeded.withStyle(target.isLowerThird, style.toLyricStyle()))
}

/** Back to being drawn like the first title, which is what Reset means for the second. */
internal fun SongSettings.withSecondaryTitleFollowingPrimary(): SongSettings =
    copy(secondaryTitleLanguage = secondaryTitleLanguage.copy(enabled = false))

/**
 * The profile [element] is drawn from in [language] -- one definition for every surface that offers
 * the switch, so the global tab and the per-output dialog cannot disagree about what it points at.
 *
 * Only the lyrics and the title have a second profile. Everything else is drawn once whatever
 * language the words are in, so asking for its second gives its only one.
 */
internal fun SongSettings.elementStyle(
    element: SongStyleElement,
    target: SongStyleTarget,
    language: SongStyleLanguage,
): SongElementStyle = when {
    !language.isSecondary -> elementStyle(element, target)
    element == SongStyleElement.LYRICS -> secondaryLyricsStyle(target)
    element == SongStyleElement.TITLE -> secondaryTitleStyle(target)
    else -> elementStyle(element, target)
}

/** [elementStyle]'s inverse: write [style] to whichever profile that triple names. */
internal fun SongSettings.withElementStyle(
    element: SongStyleElement,
    target: SongStyleTarget,
    language: SongStyleLanguage,
    style: SongElementStyle,
): SongSettings = when {
    !language.isSecondary -> withElementStyle(element, target, style)
    element == SongStyleElement.LYRICS -> withSecondaryLyricsStyle(target, style)
    element == SongStyleElement.TITLE -> withSecondaryTitleStyle(target, style)
    else -> withElementStyle(element, target, style)
}

/**
 * What Reset means for that triple.
 *
 * For a second language it is not "back to the factory look" but "back to being drawn like the
 * first" -- the state it is in until something is typed, and the only way back to it.
 */
internal fun SongSettings.withElementReset(
    element: SongStyleElement,
    target: SongStyleTarget,
    language: SongStyleLanguage,
): SongSettings = when {
    !language.isSecondary -> withElementStyle(element, target, defaultSongElementStyle(element, target))
    element == SongStyleElement.LYRICS -> withSecondaryLyricsFollowingPrimary()
    element == SongStyleElement.TITLE -> withSecondaryTitleFollowingPrimary()
    else -> withElementStyle(element, target, defaultSongElementStyle(element, target))
}

/** The elements that have a second language's profile -- the two a song carries twice. */
internal val SECOND_LANGUAGE_ELEMENTS = listOf(SongStyleElement.LYRICS, SongStyleElement.TITLE)

/** The first title's profile, which the second falls back to and is seeded from. */
private fun SongSettings.titleStyleFor(target: SongStyleTarget): SongElementStyle =
    elementStyle(SongStyleElement.TITLE, target)
