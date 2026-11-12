package org.churchpresenter.presenter

import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.songLanguageSelection
import org.churchpresenter.settings.utils.Constants

/*
 * A title slide played through a Lower Third preset (#1017). The Lower Third generator names its
 * text layers `Name`, `Info` and `Detail` rather than the band's `TextN`/`ReferenceN`, so a preset
 * from the Lower Third folder is filled by those names: the number and title in `Name`, the credits
 * across `Info` and `Detail`. Only the words change -- the preset keeps its own fonts, colours and
 * places, which are what it was designed with.
 */

/** The Lower Third generator's text layers, top line first. */
internal const val PRESET_LAYER_NAME = "Name"
internal const val PRESET_LAYER_INFO = "Info"
internal const val PRESET_LAYER_DETAIL = "Detail"

/** Between the number and the title, and between titles, on a preset's one `Name` line. */
private const val HEADING_SEPARATOR = " – "

/**
 * Whether this is a Lower Third preset rather than a band template: it has the preset's text layers
 * and none of the band's. A preset is drawn over the whole output, the way the Lower Third tab
 * plays it, rather than squeezed into the band's height.
 */
internal val BibleLottieTemplate.isLowerThirdPreset: Boolean
    get() = !hasLayer(BibleLottieTemplate.LAYER_TEXT_1) &&
        listOf(PRESET_LAYER_NAME, PRESET_LAYER_INFO, PRESET_LAYER_DETAIL).any(::hasLayer)

/**
 * How many `ReferenceN` lines the template has, counted from `Reference1` up to the first one
 * missing -- 0 when it has none. A file can have more text lines than reference lines.
 */
internal val BibleLottieTemplate.referenceSlotCount: Int
    get() = BibleLottieTemplate.TEXT_SLOT_LAYERS.indexOfFirst { (_, reference) -> !hasLayer(reference) }
        .let { if (it == -1) BibleLottieTemplate.TEXT_SLOT_LAYERS.size else it }

/**
 * A title slide's lines, split into its headings -- the number and each language's title -- and
 * its credits, as plain text. [language] is the languages the output shows.
 */
internal fun titleSlideHeadingsAndCredits(
    section: LyricSection,
    settings: SongSettings,
    language: String,
): Pair<List<String>, List<String>> {
    val languages = songLanguageSelection(language, emptyList(), section.translations.size + 1)
    val lines = titleSlideLines(section, settings, languages)
    // TITLE_SLIDE_NUMBER, because `titleSlideLines` is what produced these: the lyric slides'
    // NUMBER never appears in them.
    val headings = lines.filter {
        it.element == SongStyleElement.TITLE || it.element == SongStyleElement.TITLE_SLIDE_NUMBER
    }
    val credits = lines.filter { it !in headings }
    return headings.map { it.plainText } to credits.map { it.plainText }
}

/**
 * What a title slide puts in a Lower Third preset's text layers, by layer name -- empty when
 * [section] is not a title slide or the file has none of those layers.
 */
internal fun titleSlidePresetTexts(
    section: LyricSection,
    settings: SongSettings,
    languageDisplay: String,
    layerNames: Set<String>,
): Map<String, String> {
    if (section.type != Constants.SECTION_TYPE_TITLE_SLIDE) return emptyMap()
    val creditLayers = listOf(PRESET_LAYER_INFO, PRESET_LAYER_DETAIL).filter { it in layerNames }
    val hasName = PRESET_LAYER_NAME in layerNames
    if (!hasName && creditLayers.isEmpty()) return emptyMap()
    val language = languageDisplay.ifBlank { settings.lowerThirdLanguageDisplay }
    val (headings, credits) = titleSlideHeadingsAndCredits(section, settings, language)
    val creditLines = splitEvenly(credits, creditLayers.size)
    return buildMap {
        if (hasName) put(PRESET_LAYER_NAME, headings.joinToString(HEADING_SEPARATOR))
        creditLayers.forEachIndexed { i, layer -> put(layer, creditLines.getOrElse(i) { "" }) }
    }
}
