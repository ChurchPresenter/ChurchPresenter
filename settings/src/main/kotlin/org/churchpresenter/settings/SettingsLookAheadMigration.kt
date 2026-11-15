package org.churchpresenter.settings

/**
 * Schema version 27. The look-ahead output's current lines are styled by Lyrics.
 *
 * A song output with look-ahead on used to draw the lines being sung with a style of their own,
 * "Look Ahead", and ignore its Lyrics style, so that profile had three song text styles for two
 * kinds of text. Lyrics now styles the current lines everywhere, and "Look Ahead" names the
 * upcoming lines (the next section). Each profile with look-ahead on gets the look-ahead style it
 * was drawing copied into Lyrics, along with that element's boxes and moves, so its screens look
 * the same after the upgrade. A profile with look-ahead off never drew that style and keeps its
 * Lyrics as it is.
 *
 * A linked profile stores every value in full and lists its own in `overrides`; anything the
 * migration changed on it, or on a master it follows, becomes its own. Otherwise the next link
 * resolution would replace it with the master's value, and a profile with look-ahead off following
 * one with it on would pick up the master's look-ahead style.
 */
internal fun migrateLookAheadIntoLyrics(settings: AppSettings): AppSettings {
    val projection = settings.projectionSettings
    val before = projection.outputProfiles.associateBy { it.id }
    val migrated = projection.outputProfiles.map { profile ->
        if (!profile.songLookAhead) return@map profile
        profile.copy(songSettings = profile.songSettings.withLookAheadAsLyrics())
    }
    val changed = migrated.associate { it.id to changedPaths(before.getValue(it.id), it) }
    if (changed.values.all { it.isEmpty() }) return settings
    val withOverrides = migrated.map { profile ->
        if (profile.parentId == null) return@map profile
        val masters = setOf(profile.parentId) + profile.sectionMasters.values
        val own = changed.getValue(profile.id) + masters.flatMap { changed[it].orEmpty() }
        if (own.isEmpty()) profile else profile.copy(overrides = profile.overrides + own)
    }
    return settings.copy(projectionSettings = projection.copy(outputProfiles = withOverrides))
}

/** These settings drawing their current lines as their look-ahead style did. */
internal fun SongSettings.withLookAheadAsLyrics(): SongSettings {
    val extras = layoutExtras
    val allLanguages = extras.allLanguages
    return copy(
        lyricsColor = lookAheadColor,
        lyricsFontType = lookAheadFontType,
        lyricsFontSize = lookAheadFontSize,
        lyricsFontSizeAutoFit = lookAheadFontSizeAutoFit,
        lyricsBold = lookAheadBold,
        lyricsItalic = lookAheadItalic,
        lyricsUnderline = lookAheadUnderline,
        lyricsStrikethrough = lookAheadStrikethrough,
        lyricsShadow = lookAheadShadow,
        lyricsShadowColor = lookAheadShadowColor,
        lyricsShadowSize = lookAheadShadowSize,
        lyricsShadowOpacity = lookAheadShadowOpacity,
        lyricsHorizontalAlignment = lookAheadHorizontalAlignment,
        lyricsLetterSpacing = lookAheadLetterSpacing,
        lyricsWordSpacing = lookAheadWordSpacing,
        lyricsTransform = lookAheadTransform,
        lyricsBackdrop = lookAheadBackdrop,
        lyricsLowerThirdColor = lowerThirdLookAheadColor,
        lyricsLowerThirdFontType = lowerThirdLookAheadFontType,
        lyricsLowerThirdFontSize = lowerThirdLookAheadFontSize,
        lyricsLowerThirdFontSizeAutoFit = lowerThirdLookAheadFontSizeAutoFit,
        lyricsLowerThirdBold = lowerThirdLookAheadBold,
        lyricsLowerThirdItalic = lowerThirdLookAheadItalic,
        lyricsLowerThirdUnderline = lowerThirdLookAheadUnderline,
        lyricsLowerThirdStrikethrough = lowerThirdLookAheadStrikethrough,
        lyricsLowerThirdShadow = lowerThirdLookAheadShadow,
        lyricsLowerThirdShadowColor = lowerThirdLookAheadShadowColor,
        lyricsLowerThirdShadowSize = lowerThirdLookAheadShadowSize,
        lyricsLowerThirdShadowOpacity = lowerThirdLookAheadShadowOpacity,
        lyricsLowerThirdHorizontalAlignment = lowerThirdLookAheadHorizontalAlignment,
        lyricsLowerThirdLetterSpacing = lowerThirdLookAheadLetterSpacing,
        lyricsLowerThirdWordSpacing = lowerThirdLookAheadWordSpacing,
        lyricsLowerThirdTransform = lowerThirdLookAheadTransform,
        lyricsLowerThirdBackdrop = lowerThirdLookAheadBackdrop,
        outlines = outlines.copy(lyrics = outlines.lookAhead, lyricsLowerThird = outlines.lookAheadLowerThird),
        translations = translations.map { it.withLookAheadAsLyrics() },
        layoutExtras = extras.copy(
            allLanguages = allLanguages.copy(
                style = allLanguages.style.withLookAheadAsLyrics(),
                firstLanguageOwnKeys = allLanguages.firstLanguageOwnKeys.withLookAheadKeysAsLyrics(),
            ),
            textBoxes = extras.textBoxes.withLookAheadKeysAsLyrics(),
            elementShifts = extras.elementShifts.withLookAheadKeysAsLyrics(),
        ),
    )
}

/**
 * One language's look with its look-ahead profiles as its lyrics. The chord colour is the lyrics'
 * own: the look-ahead profile kept the default it was seeded with and was never edited.
 */
private fun SongTranslationSettings.withLookAheadAsLyrics(): SongTranslationSettings = copy(
    lyrics = lookAhead.copy(chordColor = lyrics.chordColor),
    lyricsLowerThird = lookAheadLowerThird.copy(chordColor = lyricsLowerThird.chordColor),
)

private const val LOOK_AHEAD_KEY = "LOOK_AHEAD"
private const val LYRICS_KEY = "LYRICS"

/** The All layer's own-value keys, `lookAhead.color` and `lookAheadLowerThird.color`, as the lyrics'. */
private fun Set<String>.withLookAheadKeysAsLyrics(): Set<String> =
    filterNot { it.startsWith("lyrics.") || it.startsWith("lyricsLowerThird.") }
        .map { if (it.startsWith("lookAhead")) "lyrics" + it.removePrefix("lookAhead") else it }
        .toSet()

/**
 * The entries stored under the old look-ahead element's keys moved to the lyrics' keys. The lyrics
 * entries they replace were never drawn on a look-ahead output, so they are dropped.
 */
private fun <T> Map<String, T>.withLookAheadKeysAsLyrics(): Map<String, T> {
    val kept = filterKeys { !it.isElementKey(LYRICS_KEY) && !it.isElementKey(LOOK_AHEAD_KEY) }
    val moved = filterKeys { it.isElementKey(LOOK_AHEAD_KEY) }
        .mapKeys { (key, _) -> LYRICS_KEY + key.removePrefix(LOOK_AHEAD_KEY) }
    return kept + moved
}

/** True for a box or move key of [element]: its name, then a language (`#1`) or `@LT`, or nothing. */
private fun String.isElementKey(element: String): Boolean =
    startsWith(element) && (length == element.length || this[element.length] == '#' || this[element.length] == '@')
