package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings

/**
 * The Bible pane's "All" chip: one edit written to every translation of the stack.
 *
 * The same choice the Songs pane's language row leads with. It is a deliberate pick rather than
 * the pane's only mode -- an earlier version wrote every edit to the whole stack with no way to
 * style one translation alone -- and it writes **only what changed**: raising the size under All
 * raises it on each translation and leaves a second language's own colour exactly as it was.
 */
internal const val ALL_TRANSLATIONS = -1

/** [translationIndex] as the pane will use it: All only means something with two or more to style. */
internal fun effectiveTranslationIndex(translationIndex: Int, stackSize: Int): Int = when {
    translationIndex == ALL_TRANSLATIONS && stackSize > 1 -> ALL_TRANSLATIONS
    else -> translationIndex.coerceIn(0, (stackSize - 1).coerceAtLeast(0))
}

/** [transform] applied to every translation of the stack, one by one. */
internal fun BibleSettings.updateEveryTranslation(
    transform: (BibleTranslationSettings) -> BibleTranslationSettings,
): BibleSettings = translationList().indices.fold(this) { bs, index -> bs.updateTranslation(index, transform) }

/**
 * This style with every property that differs between [before] and [after] taken from [after].
 *
 * [before] is what the controls showed and [after] is what they handed back, so the difference is
 * exactly the property the operator touched; everything else keeps this translation's own value.
 */
internal fun BibleElementStyle.withChangesFrom(before: BibleElementStyle, after: BibleElementStyle): BibleElementStyle {
    fun <T> pick(mine: T, was: T, now: T): T = if (now != was) now else mine
    return BibleElementStyle(
        color = pick(color, before.color, after.color),
        fontType = pick(fontType, before.fontType, after.fontType),
        fontSize = pick(fontSize, before.fontSize, after.fontSize),
        bold = pick(bold, before.bold, after.bold),
        italic = pick(italic, before.italic, after.italic),
        underline = pick(underline, before.underline, after.underline),
        strikethrough = pick(strikethrough, before.strikethrough, after.strikethrough),
        shadow = pick(shadow, before.shadow, after.shadow),
        shadowColor = pick(shadowColor, before.shadowColor, after.shadowColor),
        shadowSize = pick(shadowSize, before.shadowSize, after.shadowSize),
        shadowOpacity = pick(shadowOpacity, before.shadowOpacity, after.shadowOpacity),
        horizontalAlignment = pick(horizontalAlignment, before.horizontalAlignment, after.horizontalAlignment),
        position = pick(position, before.position, after.position),
        letterSpacing = pick(letterSpacing, before.letterSpacing, after.letterSpacing),
        wordSpacing = pick(wordSpacing, before.wordSpacing, after.wordSpacing),
        transform = pick(transform, before.transform, after.transform),
        backdrop = pick(backdrop, before.backdrop, after.backdrop),
        outline = pick(outline, before.outline, after.outline),
    )
}
