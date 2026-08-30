package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.utils.Constants

/**
 * One of the two things a translation draws.
 *
 * The settings tab edits one at a time rather than showing both stacked, which is what lets a
 * single set of controls stand for what used to be four parallel copies of every field.
 */
internal enum class BibleStyleElement { TEXT, REFERENCE }

/** Which output the styling being edited belongs to. */
internal enum class BibleStyleTarget { FULL_SCREEN, LOWER_THIRD }

internal val BibleStyleTarget.isLowerThird: Boolean get() = this == BibleStyleTarget.LOWER_THIRD

/**
 * The appearance of one element on one output, lifted out of [BibleTranslationSettings].
 *
 * [BibleTranslationSettings] stores these four profiles as four parallel families of flat fields --
 * `text*`, `lowerThirdText*`, `reference*`, `lowerThirdReference*` -- which is right for a
 * serialized settings file and wrong for a panel that edits whichever one the operator has
 * selected. This is the shape the panel wants: read one out with [elementStyle], hand the edited
 * copy back with [withElementStyle], and no control has to know which of the sixty-odd fields it
 * is actually writing.
 *
 * Every property carries the same default its stored counterpart does, which is also what makes
 * `BibleElementStyle()` the value the panel's Reset button writes.
 */
internal data class BibleElementStyle(
    val color: String = "#FFFFFF",
    val fontType: String = "Arial",
    val fontSize: Int = 70,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val shadow: Boolean = false,
    val shadowColor: String = "#000000",
    val shadowSize: Int = 100,
    val shadowOpacity: Int = 90,
    val horizontalAlignment: String = Constants.LEFT,
    /** Reference only; [BibleStyleElement.TEXT] reads a fixed value and ignores it on write. */
    val position: String = Constants.POSITION_BELOW,
    val letterSpacing: Int = 0,
    val wordSpacing: Int = 0,
    val transform: String = Constants.TEXT_TRANSFORM_NONE,
)

/** What this translation currently draws [element] with on [target]. */
internal fun BibleTranslationSettings.elementStyle(
    element: BibleStyleElement,
    target: BibleStyleTarget,
): BibleElementStyle = when {
    element == BibleStyleElement.TEXT && !target.isLowerThird -> BibleElementStyle(
        color = textColor,
        fontType = textFontType,
        fontSize = textFontSize,
        bold = textBold,
        italic = textItalic,
        underline = textUnderline,
        strikethrough = textStrikethrough,
        shadow = textShadow,
        shadowColor = textShadowColor,
        shadowSize = textShadowSize,
        shadowOpacity = textShadowOpacity,
        horizontalAlignment = textHorizontalAlignment,
        letterSpacing = textLetterSpacing,
        wordSpacing = textWordSpacing,
        transform = textTransform,
    )
    element == BibleStyleElement.TEXT -> BibleElementStyle(
        color = lowerThirdTextColor,
        fontType = lowerThirdTextFontType,
        fontSize = lowerThirdTextFontSize,
        bold = lowerThirdTextBold,
        italic = lowerThirdTextItalic,
        underline = lowerThirdTextUnderline,
        strikethrough = lowerThirdTextStrikethrough,
        shadow = lowerThirdTextShadow,
        shadowColor = lowerThirdTextShadowColor,
        shadowSize = lowerThirdTextShadowSize,
        shadowOpacity = lowerThirdTextShadowOpacity,
        horizontalAlignment = lowerThirdTextHorizontalAlignment,
        letterSpacing = lowerThirdTextLetterSpacing,
        wordSpacing = lowerThirdTextWordSpacing,
        transform = lowerThirdTextTransform,
    )
    !target.isLowerThird -> BibleElementStyle(
        color = referenceColor,
        fontType = referenceFontType,
        fontSize = referenceFontSize,
        bold = referenceBold,
        italic = referenceItalic,
        underline = referenceUnderline,
        strikethrough = referenceStrikethrough,
        shadow = referenceShadow,
        shadowColor = referenceShadowColor,
        shadowSize = referenceShadowSize,
        shadowOpacity = referenceShadowOpacity,
        horizontalAlignment = referenceHorizontalAlignment,
        position = referencePosition,
        letterSpacing = referenceLetterSpacing,
        wordSpacing = referenceWordSpacing,
        transform = referenceTransform,
    )
    else -> BibleElementStyle(
        color = lowerThirdReferenceColor,
        fontType = lowerThirdReferenceFontType,
        fontSize = lowerThirdReferenceFontSize,
        bold = lowerThirdReferenceBold,
        italic = lowerThirdReferenceItalic,
        underline = lowerThirdReferenceUnderline,
        strikethrough = lowerThirdReferenceStrikethrough,
        shadow = lowerThirdReferenceShadow,
        shadowColor = lowerThirdReferenceShadowColor,
        shadowSize = lowerThirdReferenceShadowSize,
        shadowOpacity = lowerThirdReferenceShadowOpacity,
        horizontalAlignment = lowerThirdReferenceHorizontalAlignment,
        position = lowerThirdReferencePosition,
        letterSpacing = lowerThirdReferenceLetterSpacing,
        wordSpacing = lowerThirdReferenceWordSpacing,
        transform = lowerThirdReferenceTransform,
    )
}

/**
 * This translation with [element] on [target] set to [style], and everything else untouched.
 *
 * The inverse of [elementStyle] -- read, `copy` the one property a control changed, write back.
 * [BibleElementStyle.position] is dropped for [BibleStyleElement.TEXT], which has nowhere to store
 * it: only the reference moves above or below.
 */
internal fun BibleTranslationSettings.withElementStyle(
    element: BibleStyleElement,
    target: BibleStyleTarget,
    style: BibleElementStyle,
): BibleTranslationSettings = when {
    element == BibleStyleElement.TEXT && !target.isLowerThird -> withFullScreenText(style)
    element == BibleStyleElement.TEXT -> withLowerThirdText(style)
    !target.isLowerThird -> withFullScreenReference(style)
    else -> withLowerThirdReference(style)
}

private fun BibleTranslationSettings.withFullScreenText(s: BibleElementStyle) = copy(
    textColor = s.color,
    textFontType = s.fontType,
    textFontSize = s.fontSize,
    textBold = s.bold,
    textItalic = s.italic,
    textUnderline = s.underline,
    textStrikethrough = s.strikethrough,
    textShadow = s.shadow,
    textShadowColor = s.shadowColor,
    textShadowSize = s.shadowSize,
    textShadowOpacity = s.shadowOpacity,
    textHorizontalAlignment = s.horizontalAlignment,
    textLetterSpacing = s.letterSpacing,
    textWordSpacing = s.wordSpacing,
    textTransform = s.transform,
)

private fun BibleTranslationSettings.withLowerThirdText(s: BibleElementStyle) = copy(
    lowerThirdTextColor = s.color,
    lowerThirdTextFontType = s.fontType,
    lowerThirdTextFontSize = s.fontSize,
    lowerThirdTextBold = s.bold,
    lowerThirdTextItalic = s.italic,
    lowerThirdTextUnderline = s.underline,
    lowerThirdTextStrikethrough = s.strikethrough,
    lowerThirdTextShadow = s.shadow,
    lowerThirdTextShadowColor = s.shadowColor,
    lowerThirdTextShadowSize = s.shadowSize,
    lowerThirdTextShadowOpacity = s.shadowOpacity,
    lowerThirdTextHorizontalAlignment = s.horizontalAlignment,
    lowerThirdTextLetterSpacing = s.letterSpacing,
    lowerThirdTextWordSpacing = s.wordSpacing,
    lowerThirdTextTransform = s.transform,
)

private fun BibleTranslationSettings.withFullScreenReference(s: BibleElementStyle) = copy(
    referenceColor = s.color,
    referenceFontType = s.fontType,
    referenceFontSize = s.fontSize,
    referenceBold = s.bold,
    referenceItalic = s.italic,
    referenceUnderline = s.underline,
    referenceStrikethrough = s.strikethrough,
    referenceShadow = s.shadow,
    referenceShadowColor = s.shadowColor,
    referenceShadowSize = s.shadowSize,
    referenceShadowOpacity = s.shadowOpacity,
    referenceHorizontalAlignment = s.horizontalAlignment,
    referencePosition = s.position,
    referenceLetterSpacing = s.letterSpacing,
    referenceWordSpacing = s.wordSpacing,
    referenceTransform = s.transform,
)

private fun BibleTranslationSettings.withLowerThirdReference(s: BibleElementStyle) = copy(
    lowerThirdReferenceColor = s.color,
    lowerThirdReferenceFontType = s.fontType,
    lowerThirdReferenceFontSize = s.fontSize,
    lowerThirdReferenceBold = s.bold,
    lowerThirdReferenceItalic = s.italic,
    lowerThirdReferenceUnderline = s.underline,
    lowerThirdReferenceStrikethrough = s.strikethrough,
    lowerThirdReferenceShadow = s.shadow,
    lowerThirdReferenceShadowColor = s.shadowColor,
    lowerThirdReferenceShadowSize = s.shadowSize,
    lowerThirdReferenceShadowOpacity = s.shadowOpacity,
    lowerThirdReferenceHorizontalAlignment = s.horizontalAlignment,
    lowerThirdReferencePosition = s.position,
    lowerThirdReferenceLetterSpacing = s.letterSpacing,
    lowerThirdReferenceWordSpacing = s.wordSpacing,
    lowerThirdReferenceTransform = s.transform,
)

/**
 * What this element on this output looks like before anyone has touched it.
 *
 * Read off a default [BibleTranslationSettings] rather than written out again here, so the Reset
 * button and the stored defaults can never drift apart -- the lower third's own smaller default
 * font sizes included.
 */
internal fun defaultElementStyle(
    element: BibleStyleElement,
    target: BibleStyleTarget,
): BibleElementStyle = BibleTranslationSettings().elementStyle(element, target)
