package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.core.models.text.TextOutline
import org.churchpresenter.settings.DictionarySettings

/**
 * The stored field behind each control of one dictionary part, or null where the part has no such
 * setting -- the definition has no face of its own, the usage line no style at all.
 */
internal data class DictionaryFieldNames(
    val color: String,
    val size: String,
    val font: String? = null,
    val bold: String? = null,
    val italic: String? = null,
    val shadow: String? = null,
    val backdrop: String? = null,
    val outline: String? = null,
)

/** One part's look, with a null wherever [names] has no field. */
internal data class DictionaryLook(
    val names: DictionaryFieldNames,
    val color: String,
    val fontSize: Int,
    val fontType: String? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val shadow: Boolean? = null,
    val shadowColor: String = "",
    val shadowSize: Int = 0,
    val shadowOpacity: Int = 0,
    val backdrop: TextBackdrop? = null,
    val outline: TextOutline? = null,
)

/** The four parts of a dictionary card, each styled apart and each shown or not. */
internal enum class DictionaryPart(val showField: String, val names: DictionaryFieldNames) {
    WORD(
        "showWord",
        DictionaryFieldNames(
            color = "wordColor", size = "wordFontSize", font = "wordFontType", bold = "wordBold",
            italic = "wordItalic", shadow = "wordShadow", backdrop = "wordBackdrop", outline = "wordOutline",
        ),
    ),
    REFERENCE(
        "showReference",
        DictionaryFieldNames(
            color = "referenceColor", size = "referenceFontSize", font = "referenceFontType",
            shadow = "referenceShadow", backdrop = "referenceBackdrop", outline = "referenceOutline",
        ),
    ),
    DEFINITION(
        "showDefinition",
        DictionaryFieldNames(
            color = "definitionColor", size = "definitionFontSize", backdrop = "definitionBackdrop",
            outline = "definitionOutline",
        ),
    ),
    KJV_USAGE("showKjvUsage", DictionaryFieldNames(color = "kjvUsageColor", size = "kjvUsageFontSize"));

    /** Every field this part's rows write, for its group's Revert. */
    val fields: List<String>
        get() = with(names) { listOfNotNull(color, size, font, bold, italic, shadow, backdrop, outline) } +
            if (names.shadow != null) listOf(
                "${names.shadow}Color",
                "${names.shadow}Size",
                "${names.shadow}Opacity",
            ) else emptyList()

    fun shown(ds: DictionarySettings): Boolean = when (this) {
        WORD -> ds.showWord
        REFERENCE -> ds.showReference
        DEFINITION -> ds.showDefinition
        KJV_USAGE -> ds.showKjvUsage
    }

    fun withShown(ds: DictionarySettings, on: Boolean): DictionarySettings = when (this) {
        WORD -> ds.copy(showWord = on)
        REFERENCE -> ds.copy(showReference = on)
        DEFINITION -> ds.copy(showDefinition = on)
        KJV_USAGE -> ds.copy(showKjvUsage = on)
    }

    fun look(ds: DictionarySettings): DictionaryLook = when (this) {
        WORD -> DictionaryLook(
            names, ds.wordColor, ds.wordFontSize, ds.wordFontType, ds.wordBold, ds.wordItalic, ds.wordShadow,
            ds.wordShadowColor, ds.wordShadowSize, ds.wordShadowOpacity, ds.wordBackdrop, ds.wordOutline,
        )
        REFERENCE -> DictionaryLook(
            names, ds.referenceColor, ds.referenceFontSize, ds.referenceFontType, shadow = ds.referenceShadow,
            shadowColor = ds.referenceShadowColor, shadowSize = ds.referenceShadowSize,
            shadowOpacity = ds.referenceShadowOpacity, backdrop = ds.referenceBackdrop, outline = ds.referenceOutline,
        )
        DEFINITION -> DictionaryLook(
            names, ds.definitionColor, ds.definitionFontSize,
            backdrop = ds.definitionBackdrop, outline = ds.definitionOutline,
        )
        KJV_USAGE -> DictionaryLook(names, ds.kjvUsageColor, ds.kjvUsageFontSize)
    }

    fun withLook(ds: DictionarySettings, l: DictionaryLook): DictionarySettings = when (this) {
        WORD -> ds.copy(
            wordColor = l.color, wordFontSize = l.fontSize, wordFontType = l.fontType ?: ds.wordFontType,
            wordBold = l.bold ?: ds.wordBold, wordItalic = l.italic ?: ds.wordItalic,
            wordShadow = l.shadow ?: ds.wordShadow, wordShadowColor = l.shadowColor, wordShadowSize = l.shadowSize,
            wordShadowOpacity = l.shadowOpacity, wordBackdrop = l.backdrop ?: ds.wordBackdrop,
            wordOutline = l.outline ?: ds.wordOutline,
        )
        REFERENCE -> ds.copy(
            referenceColor = l.color, referenceFontSize = l.fontSize,
            referenceFontType = l.fontType ?: ds.referenceFontType, referenceShadow = l.shadow ?: ds.referenceShadow,
            referenceShadowColor = l.shadowColor, referenceShadowSize = l.shadowSize,
            referenceShadowOpacity = l.shadowOpacity, referenceBackdrop = l.backdrop ?: ds.referenceBackdrop,
            referenceOutline = l.outline ?: ds.referenceOutline,
        )
        DEFINITION -> ds.copy(
            definitionColor = l.color, definitionFontSize = l.fontSize,
            definitionBackdrop = l.backdrop ?: ds.definitionBackdrop,
            definitionOutline = l.outline ?: ds.definitionOutline,
        )
        KJV_USAGE -> ds.copy(kjvUsageColor = l.color, kjvUsageFontSize = l.fontSize)
    }
}
