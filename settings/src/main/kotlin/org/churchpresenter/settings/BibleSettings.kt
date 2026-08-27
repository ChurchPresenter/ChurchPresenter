package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.settings.utils.Constants

/**
 * One independently configurable translation in the parallel Bible stack.
 *
 * Carries the full appearance profile a single Bible used to get from the primary/secondary field
 * pairs: full-screen and lower-third, text and reference. [lowerThirdEnabled] is per translation
 * because a lower third is a narrow band -- a stack that reads well full screen may want only its
 * first one or two languages down there.
 */
@Serializable
data class BibleTranslationSettings(
    val fileName: String = "",
    /**
     * What this church calls the translation, overriding the `##Title:` its module carries.
     *
     * A module names itself in its own header and its abbreviation is derived from that title, so
     * "King James Version" labels every verse "KJV" whether or not that is what the congregation
     * calls it -- and neither is editable without a text editor, nor survives a redownload. Blank
     * means "use what the module says", which is what makes clearing the field the way to undo a
     * rename. [customAbbreviation] is separate because it is the string that goes on screen beside
     * the verse, and a church can want one without the other.
     */
    val customName: String = "",
    val customAbbreviation: String = "",
    val textColor: String = "#FFFFFF",
    val textFontType: String = "Arial",
    val textFontSize: Int = 70,
    val lowerThirdTextFontSize: Int = 28,
    val textHorizontalAlignment: String = Constants.LEFT,
    val lowerThirdTextHorizontalAlignment: String = Constants.LEFT,
    val lowerThirdEnabled: Boolean = true,
    val textBold: Boolean = false,
    val textItalic: Boolean = false,
    val textUnderline: Boolean = false,
    val textShadow: Boolean = false,
    val lowerThirdTextColor: String = "#FFFFFF",
    val lowerThirdTextFontType: String = "Arial",
    val lowerThirdTextBold: Boolean = false,
    val lowerThirdTextItalic: Boolean = false,
    val lowerThirdTextUnderline: Boolean = false,
    val lowerThirdTextShadow: Boolean = false,
    val referenceColor: String = "#FFFFFF",
    val referenceFontType: String = "Arial",
    val referenceFontSize: Int = 70,
    val lowerThirdReferenceFontSize: Int = 24,
    val referencePosition: String = "Below",
    val lowerThirdReferencePosition: String = "Below",
    val referenceHorizontalAlignment: String = Constants.RIGHT,
    val lowerThirdReferenceHorizontalAlignment: String = Constants.RIGHT,
    val showAbbreviation: Boolean = false,
    val referenceBold: Boolean = false,
    val referenceItalic: Boolean = false,
    val referenceUnderline: Boolean = false,
    val referenceShadow: Boolean = false,
    val lowerThirdReferenceColor: String = "#FFFFFF",
    val lowerThirdReferenceFontType: String = "Arial",
    val lowerThirdReferenceBold: Boolean = false,
    val lowerThirdReferenceItalic: Boolean = false,
    val lowerThirdReferenceUnderline: Boolean = false,
    val lowerThirdReferenceShadow: Boolean = false,
    val textShadowColor: String = "#000000",
    val textShadowSize: Int = 100,
    val textShadowOpacity: Int = 90,
    val lowerThirdTextShadowColor: String = "#000000",
    val lowerThirdTextShadowSize: Int = 100,
    val lowerThirdTextShadowOpacity: Int = 90,
    val referenceShadowColor: String = "#000000",
    val referenceShadowSize: Int = 100,
    val referenceShadowOpacity: Int = 90,
    val lowerThirdReferenceShadowColor: String = "#000000",
    val lowerThirdReferenceShadowSize: Int = 100,
    val lowerThirdReferenceShadowOpacity: Int = 90,

    // Struck-through text, alongside the bold/italic/underline flags above. Stored as its own flag
    // rather than folded into the underline one because Compose composes the two decorations.
    val textStrikethrough: Boolean = false,
    val lowerThirdTextStrikethrough: Boolean = false,
    val referenceStrikethrough: Boolean = false,
    val lowerThirdReferenceStrikethrough: Boolean = false,

    // Tracking, in points at the configured font size, added between every character. Negative
    // tightens. The presenter scales it with the rest of the type, so a value set against one
    // output resolution still reads the same on another.
    val textLetterSpacing: Int = 0,
    val lowerThirdTextLetterSpacing: Int = 0,
    val referenceLetterSpacing: Int = 0,
    val lowerThirdReferenceLetterSpacing: Int = 0,

    // Extra space added at each word break, on top of whatever the face's own space glyph is.
    // Compose has no `wordSpacing`, so it is drawn by widening the spaces themselves -- see
    // `bibleDisplayText`.
    val textWordSpacing: Int = 0,
    val lowerThirdTextWordSpacing: Int = 0,
    val referenceWordSpacing: Int = 0,
    val lowerThirdReferenceWordSpacing: Int = 0,

    // One of [Constants.TEXT_TRANSFORM_NONE], `_UPPERCASE`, `_LOWERCASE` or `_CAPITALIZE`, applied
    // as the text is drawn.
    val textTransform: String = Constants.TEXT_TRANSFORM_NONE,
    val lowerThirdTextTransform: String = Constants.TEXT_TRANSFORM_NONE,
    val referenceTransform: String = Constants.TEXT_TRANSFORM_NONE,
    val lowerThirdReferenceTransform: String = Constants.TEXT_TRANSFORM_NONE,
)

// The accessors are one per stored profile field (translation lookup, the two style profiles, the
// stack edits); splitting them out would separate them from the data they read.
@Suppress("TooManyFunctions")
@Serializable
data class BibleSettings(
    // Bible file management
    val storageDirectory: String = "",
    val bibleFiles: List<String> = emptyList(),

    // Bible selection
    val primaryBible: String = "",
    val secondaryBible: String = "",
    /**
     * The presentation stack, in order; the first is the navigation bible.
     *
     * Source of truth. [primaryBible]/[secondaryBible] and their styling fields above are retained
     * only so an older build can still read a settings file written by this one -- see
     * [migrateTranslations].
     */
    val translations: List<BibleTranslationSettings> = emptyList(),
    val multiTranslationSpacing: Int = 24,
    val multiTranslationDivider: Boolean = false,

    // Bible tab column widths (dp); 0 = use default
    val bibleColWidthBook: Int = 200,
    val bibleColWidthChapter: Int = 120,

    // Global vertical alignment (affects all 4 sections)
    val verticalAlignment: String = Constants.BOTTOM,

    // Primary Bible text
    val primaryBibleColor: String = "#FFFFFF",
    val primaryBibleFontType: String = "Arial",
    val primaryBibleFontSize: Int = 70,
    val primaryBibleLowerThirdFontSize: Int = 32,
    val primaryBibleHorizontalAlignment: String = Constants.LEFT,
    val primaryBibleLowerThirdHorizontalAlignment: String = Constants.LEFT,
    val primaryBibleBold: Boolean = false,
    val primaryBibleItalic: Boolean = false,
    val primaryBibleUnderline: Boolean = false,
    val primaryBibleShadow: Boolean = false,
    val primaryBibleLowerThirdColor: String = "#FFFFFF",
    val primaryBibleLowerThirdFontType: String = "Arial",
    val primaryBibleLowerThirdBold: Boolean = false,
    val primaryBibleLowerThirdItalic: Boolean = false,
    val primaryBibleLowerThirdUnderline: Boolean = false,
    val primaryBibleLowerThirdShadow: Boolean = false,

    // Primary Bible book reference
    val primaryReferenceColor: String = "#FFFFFF",
    val primaryReferenceFontType: String = "Arial",
    val primaryReferenceFontSize: Int = 70,
    val primaryReferenceLowerThirdFontSize: Int = 24,
    val primaryReferencePosition: String = "Below", // "Above" or "Below"
    val primaryReferenceLowerThirdPosition: String = "Below",
    val primaryReferenceHorizontalAlignment: String = Constants.RIGHT,
    val primaryReferenceLowerThirdHorizontalAlignment: String = Constants.RIGHT,
    val primaryShowAbbreviation: Boolean = false,
    val primaryReferenceBold: Boolean = false,
    val primaryReferenceItalic: Boolean = false,
    val primaryReferenceUnderline: Boolean = false,
    val primaryReferenceShadow: Boolean = false,
    val primaryReferenceLowerThirdColor: String = "#FFFFFF",
    val primaryReferenceLowerThirdFontType: String = "Arial",
    val primaryReferenceLowerThirdBold: Boolean = false,
    val primaryReferenceLowerThirdItalic: Boolean = false,
    val primaryReferenceLowerThirdUnderline: Boolean = false,
    val primaryReferenceLowerThirdShadow: Boolean = false,

    // Secondary Bible text
    val secondaryBibleColor: String = "#FFFFFF",
    val secondaryBibleFontType: String = "Arial",
    val secondaryBibleFontSize: Int = 70,
    val secondaryBibleLowerThirdFontSize: Int = 28,
    val secondaryBibleHorizontalAlignment: String = Constants.LEFT,
    val secondaryBibleLowerThirdHorizontalAlignment: String = Constants.LEFT,
    val secondaryBibleLowerThirdEnabled: Boolean = true,
    val secondaryBibleBold: Boolean = false,
    val secondaryBibleItalic: Boolean = false,
    val secondaryBibleUnderline: Boolean = false,
    val secondaryBibleShadow: Boolean = false,
    val secondaryBibleLowerThirdColor: String = "#FFFFFF",
    val secondaryBibleLowerThirdFontType: String = "Arial",
    val secondaryBibleLowerThirdBold: Boolean = false,
    val secondaryBibleLowerThirdItalic: Boolean = false,
    val secondaryBibleLowerThirdUnderline: Boolean = false,
    val secondaryBibleLowerThirdShadow: Boolean = false,

    // Secondary Bible book reference
    val secondaryReferenceColor: String = "#FFFFFF",
    val secondaryReferenceFontType: String = "Arial",
    val secondaryReferenceFontSize: Int = 70,
    val secondaryReferenceLowerThirdFontSize: Int = 24,
    val secondaryReferencePosition: String = "Below", // "Above" or "Below"
    val secondaryReferenceLowerThirdPosition: String = "Below",
    val secondaryReferenceHorizontalAlignment: String = Constants.RIGHT,
    val secondaryReferenceLowerThirdHorizontalAlignment: String = Constants.RIGHT,
    val secondaryShowAbbreviation: Boolean = false,
    val secondaryReferenceBold: Boolean = false,
    val secondaryReferenceItalic: Boolean = false,
    val secondaryReferenceUnderline: Boolean = false,
    val secondaryReferenceShadow: Boolean = false,
    val secondaryReferenceLowerThirdColor: String = "#FFFFFF",
    val secondaryReferenceLowerThirdFontType: String = "Arial",
    val secondaryReferenceLowerThirdBold: Boolean = false,
    val secondaryReferenceLowerThirdItalic: Boolean = false,
    val secondaryReferenceLowerThirdUnderline: Boolean = false,
    val secondaryReferenceLowerThirdShadow: Boolean = false,

    // Language for captions
    val captionLanguage: String = "Interface", // "Interface" or "Database"

    // Text margins (additional padding inside global projection offsets)
    val marginTop: Int = 54,
    val marginBottom: Int = 54,
    val marginLeft: Int = 96,
    val marginRight: Int = 96,

    // Shadow customization — per-element
    val primaryBibleShadowColor: String = "#000000",
    val primaryBibleShadowSize: Int = 100,
    val primaryBibleShadowOpacity: Int = 90,
    val primaryBibleLowerThirdShadowColor: String = "#000000",
    val primaryBibleLowerThirdShadowSize: Int = 100,
    val primaryBibleLowerThirdShadowOpacity: Int = 90,

    val primaryReferenceShadowColor: String = "#000000",
    val primaryReferenceShadowSize: Int = 100,
    val primaryReferenceShadowOpacity: Int = 90,
    val primaryReferenceLowerThirdShadowColor: String = "#000000",
    val primaryReferenceLowerThirdShadowSize: Int = 100,
    val primaryReferenceLowerThirdShadowOpacity: Int = 90,

    val secondaryBibleShadowColor: String = "#000000",
    val secondaryBibleShadowSize: Int = 100,
    val secondaryBibleShadowOpacity: Int = 90,
    val secondaryBibleLowerThirdShadowColor: String = "#000000",
    val secondaryBibleLowerThirdShadowSize: Int = 100,
    val secondaryBibleLowerThirdShadowOpacity: Int = 90,

    val secondaryReferenceShadowColor: String = "#000000",
    val secondaryReferenceShadowSize: Int = 100,
    val secondaryReferenceShadowOpacity: Int = 90,
    val secondaryReferenceLowerThirdShadowColor: String = "#000000",
    val secondaryReferenceLowerThirdShadowSize: Int = 100,
    val secondaryReferenceLowerThirdShadowOpacity: Int = 90,

    // Transition animation
    val fadeIn: Boolean = true,
    val fadeOut: Boolean = true,
    val crossfade: Boolean = false,
    val transitionDuration: Float = 500f,
    val splitBrowseMode: Boolean = false,
    val splitLivePanelWidth: Int = 300,
    val crossReferencesEnabled: Boolean = true,
    val crossReferencesPanel: Boolean = false,
) {
    /**
     * The translations to present, in order. The first is the navigation bible.
     *
     * Falls back to the pre-list [primaryBible]/[secondaryBible] pair for settings written before
     * the list existed, so a file that has never been through [migrateTranslations] still presents
     * correctly rather than showing nothing.
     */
    fun translationList(): List<BibleTranslationSettings> =
        translations.ifEmpty { legacyTranslationList() }

    /** Stable key used by the Bible UI to detect when its loaded module set must be refreshed. */
    fun translationSelectionKey(): List<String> = translationList().map { it.fileName }

    /**
     * Fills [translations] from the legacy primary/secondary pair when it is empty.
     *
     * Both the one-time conversion of a pre-list settings file and, since it is idempotent, the
     * repair the load path applies on every read to keep the two in step — see
     * `SettingsManager.repaired`.
     *
     * The old primary/secondary fields are deliberately left in place rather than cleared: they are
     * what an older build reads, so a user who rolls back keeps their setup instead of opening a
     * blank Bible panel. They are no longer read by anything but this conversion.
     */
    fun migrateTranslations(): BibleSettings =
        if (translations.isNotEmpty()) this else copy(translations = legacyTranslationList())

    private fun legacyTranslationList(): List<BibleTranslationSettings> =
        buildList {
            if (primaryBible.isNotEmpty()) add(primaryTranslation())
            if (secondaryBible.isNotEmpty()) add(secondaryTranslation())
        }

    fun withTranslations(value: List<BibleTranslationSettings>): BibleSettings {
        // Capped here rather than only at [addTranslation], so a hand-edited or rolled-forward
        // settings file is bounded by the same rule the UI is.
        val cleaned = value.filter { it.fileName.isNotBlank() }
            .distinctBy { it.fileName }
            .take(Constants.MAX_BIBLE_TRANSLATIONS)
        // The retained legacy names track the first two of the stack. Their styling is deliberately
        // left frozen at whatever the conversion wrote -- but keeping the *selection* current is
        // cheap, and it is what makes the rollback story true rather than nominal: an older build
        // opens the bibles the operator is actually using, not the ones they used months ago.
        return copy(
            translations = cleaned,
            primaryBible = cleaned.getOrNull(0)?.fileName ?: "",
            secondaryBible = cleaned.getOrNull(1)?.fileName ?: "",
        )
    }

    fun updateTranslation(
        index: Int,
        transform: (BibleTranslationSettings) -> BibleTranslationSettings,
    ): BibleSettings {
        val current = translationList()
        if (index !in current.indices) return this
        return withTranslations(current.toMutableList().also { it[index] = transform(it[index]) })
    }

    fun addTranslation(fileName: String): BibleSettings {
        val current = translationList()
        // Refused rather than added-and-truncated: dropping the entry the operator just picked while
        // the picker reports success is the one outcome worse than not offering the add at all.
        if (fileName.isBlank() ||
            current.size >= Constants.MAX_BIBLE_TRANSLATIONS ||
            current.any { it.fileName == fileName }
        ) return this
        return withTranslations(current + BibleTranslationSettings(fileName = fileName))
    }

    fun removeTranslation(index: Int): BibleSettings =
        withTranslations(translationList().filterIndexed { itemIndex, _ -> itemIndex != index })

    fun moveTranslation(index: Int, offset: Int): BibleSettings {
        val target = index + offset
        val current = translationList()
        if (index !in current.indices || target !in current.indices) return this
        return withTranslations(current.toMutableList().also {
            val item = it.removeAt(index)
            it.add(target, item)
        })
    }

    /**
     * The renamed titles alone, keyed by file name -- what a picker needs to name its entries.
     *
     * Trimmed here rather than where the field is typed into: the settings field stores what it
     * shows, so trimming on the way in would delete the space the operator has just pressed before
     * the next letter arrives, and "King James Version" could not be typed at all -- it stuck at
     * "King". Blank names are dropped for the same reason the loader treats one as no rename: the
     * fallback is the module's own title, and an entry mapping to "" would blank the name rather
     * than leave it alone.
     */
    fun customNames(): Map<String, String> = translationList()
        .associate { it.fileName to it.customName.trim() }
        .filterValues { it.isNotBlank() }

    /** [fileName]'s rename as configured, or the empty pair when it has not been renamed. */
    fun customNameOf(fileName: String): Pair<String, String> =
        translationList().firstOrNull { it.fileName == fileName }
            ?.let { it.customName to it.customAbbreviation }
            ?: ("" to "")

    /** The renames in stack order — the key a caller watches to notice one being typed. */
    fun customNameKey(): List<String> =
        translationList().flatMap { listOf(it.customName, it.customAbbreviation) }

    private fun primaryTranslation() = BibleTranslationSettings(
        fileName = primaryBible,
        textColor = primaryBibleColor, textFontType = primaryBibleFontType,
        textFontSize = primaryBibleFontSize, lowerThirdTextFontSize = primaryBibleLowerThirdFontSize,
        textHorizontalAlignment = primaryBibleHorizontalAlignment,
        lowerThirdTextHorizontalAlignment = primaryBibleLowerThirdHorizontalAlignment,
        textBold = primaryBibleBold, textItalic = primaryBibleItalic,
        textUnderline = primaryBibleUnderline, textShadow = primaryBibleShadow,
        lowerThirdTextColor = primaryBibleLowerThirdColor,
        lowerThirdTextFontType = primaryBibleLowerThirdFontType,
        lowerThirdTextBold = primaryBibleLowerThirdBold, lowerThirdTextItalic = primaryBibleLowerThirdItalic,
        lowerThirdTextUnderline = primaryBibleLowerThirdUnderline, lowerThirdTextShadow = primaryBibleLowerThirdShadow,
        referenceColor = primaryReferenceColor, referenceFontType = primaryReferenceFontType,
        referenceFontSize = primaryReferenceFontSize,
        lowerThirdReferenceFontSize = primaryReferenceLowerThirdFontSize,
        referencePosition = primaryReferencePosition,
        lowerThirdReferencePosition = primaryReferenceLowerThirdPosition,
        referenceHorizontalAlignment = primaryReferenceHorizontalAlignment,
        lowerThirdReferenceHorizontalAlignment = primaryReferenceLowerThirdHorizontalAlignment,
        showAbbreviation = primaryShowAbbreviation,
        referenceBold = primaryReferenceBold, referenceItalic = primaryReferenceItalic,
        referenceUnderline = primaryReferenceUnderline, referenceShadow = primaryReferenceShadow,
        lowerThirdReferenceColor = primaryReferenceLowerThirdColor,
        lowerThirdReferenceFontType = primaryReferenceLowerThirdFontType,
        lowerThirdReferenceBold = primaryReferenceLowerThirdBold,
        lowerThirdReferenceItalic = primaryReferenceLowerThirdItalic,
        lowerThirdReferenceUnderline = primaryReferenceLowerThirdUnderline,
        lowerThirdReferenceShadow = primaryReferenceLowerThirdShadow,
        textShadowColor = primaryBibleShadowColor, textShadowSize = primaryBibleShadowSize,
        textShadowOpacity = primaryBibleShadowOpacity,
        lowerThirdTextShadowColor = primaryBibleLowerThirdShadowColor,
        lowerThirdTextShadowSize = primaryBibleLowerThirdShadowSize,
        lowerThirdTextShadowOpacity = primaryBibleLowerThirdShadowOpacity,
        referenceShadowColor = primaryReferenceShadowColor,
        referenceShadowSize = primaryReferenceShadowSize, referenceShadowOpacity = primaryReferenceShadowOpacity,
        lowerThirdReferenceShadowColor = primaryReferenceLowerThirdShadowColor,
        lowerThirdReferenceShadowSize = primaryReferenceLowerThirdShadowSize,
        lowerThirdReferenceShadowOpacity = primaryReferenceLowerThirdShadowOpacity,
    )

    private fun secondaryTranslation() = BibleTranslationSettings(
        fileName = secondaryBible,
        textColor = secondaryBibleColor, textFontType = secondaryBibleFontType,
        textFontSize = secondaryBibleFontSize, lowerThirdTextFontSize = secondaryBibleLowerThirdFontSize,
        textHorizontalAlignment = secondaryBibleHorizontalAlignment,
        lowerThirdTextHorizontalAlignment = secondaryBibleLowerThirdHorizontalAlignment,
        lowerThirdEnabled = secondaryBibleLowerThirdEnabled,
        textBold = secondaryBibleBold, textItalic = secondaryBibleItalic,
        textUnderline = secondaryBibleUnderline, textShadow = secondaryBibleShadow,
        lowerThirdTextColor = secondaryBibleLowerThirdColor,
        lowerThirdTextFontType = secondaryBibleLowerThirdFontType,
        lowerThirdTextBold = secondaryBibleLowerThirdBold, lowerThirdTextItalic = secondaryBibleLowerThirdItalic,
        lowerThirdTextUnderline = secondaryBibleLowerThirdUnderline,
        lowerThirdTextShadow = secondaryBibleLowerThirdShadow,
        referenceColor = secondaryReferenceColor, referenceFontType = secondaryReferenceFontType,
        referenceFontSize = secondaryReferenceFontSize,
        lowerThirdReferenceFontSize = secondaryReferenceLowerThirdFontSize,
        referencePosition = secondaryReferencePosition,
        lowerThirdReferencePosition = secondaryReferenceLowerThirdPosition,
        referenceHorizontalAlignment = secondaryReferenceHorizontalAlignment,
        lowerThirdReferenceHorizontalAlignment = secondaryReferenceLowerThirdHorizontalAlignment,
        showAbbreviation = secondaryShowAbbreviation,
        referenceBold = secondaryReferenceBold, referenceItalic = secondaryReferenceItalic,
        referenceUnderline = secondaryReferenceUnderline, referenceShadow = secondaryReferenceShadow,
        lowerThirdReferenceColor = secondaryReferenceLowerThirdColor,
        lowerThirdReferenceFontType = secondaryReferenceLowerThirdFontType,
        lowerThirdReferenceBold = secondaryReferenceLowerThirdBold,
        lowerThirdReferenceItalic = secondaryReferenceLowerThirdItalic,
        lowerThirdReferenceUnderline = secondaryReferenceLowerThirdUnderline,
        lowerThirdReferenceShadow = secondaryReferenceLowerThirdShadow,
        textShadowColor = secondaryBibleShadowColor, textShadowSize = secondaryBibleShadowSize,
        textShadowOpacity = secondaryBibleShadowOpacity,
        lowerThirdTextShadowColor = secondaryBibleLowerThirdShadowColor,
        lowerThirdTextShadowSize = secondaryBibleLowerThirdShadowSize,
        lowerThirdTextShadowOpacity = secondaryBibleLowerThirdShadowOpacity,
        referenceShadowColor = secondaryReferenceShadowColor,
        referenceShadowSize = secondaryReferenceShadowSize, referenceShadowOpacity = secondaryReferenceShadowOpacity,
        lowerThirdReferenceShadowColor = secondaryReferenceLowerThirdShadowColor,
        lowerThirdReferenceShadowSize = secondaryReferenceLowerThirdShadowSize,
        lowerThirdReferenceShadowOpacity = secondaryReferenceLowerThirdShadowOpacity,
    )

    /**
     * Returns a copy with the first two translations exchanged.
     *
     * This is the Bible tab's swap button: with the list model it is a reorder, where it used to be
     * a field-by-field exchange of some ninety primary/secondary styling values.
     */
    fun swapped() = moveTranslation(0, 1)
}
