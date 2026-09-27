package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.app.churchpresenter.presenter.PresentedBlock
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.SongSettings

/**
 * The languages the Songs Text strip offers beside All for [element]: every language reaching this
 * output, the first included, when there are two or more and the element has a look per language;
 * none otherwise, since the element is then drawn the same in every language.
 */
internal fun songLanguagesOffered(profile: OutputProfile, element: SongStyleElement): List<SongStyleLanguage> {
    val shown = styleLanguagesFor(profile.songMode, profile.songTranslations)
    return if (shown.size > 1 && element in SECOND_LANGUAGE_ELEMENTS) shown else emptyList()
}

/** Where the Songs Text rows point: the element, the title slide's own one, and the language. */
internal class SongTargets(
    val element: Adjustable<CustomizeElement>,
    val slideElement: Adjustable<SongStyleElement>,
    val language: Adjustable<SongStyleLanguage?>,
)

/** The element [element] on [slide] is, as the Text rows address it. */
internal fun SongTargets.styleElement(): SongStyleElement =
    if (element.value == CustomizeElement.SONG_TITLE_SLIDE) slideElement.value else element.value.toSongStyleElement()

/** One element the preview can draw, as a block of its own: [language] is its slot where it has one per language. */
private class SongBlock(val element: SongStyleElement, val language: Int?, lowerThird: Boolean, titleSlide: Boolean) {
    val key = songShiftKey(element, lowerThird, language, titleSlide)
}

/**
 * The Adjust handles on the Songs page: its margins and block, the size of what the Text rows point
 * at, and every element on the slide as a block of its own -- clicked to point the rows at it, and
 * moved on its own by its blue dot.
 */
internal fun songAdjustModel(
    draft: AppSettings,
    profile: OutputProfile,
    targets: SongTargets,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
): AdjustModel {
    val song = draft.songSettings
    val lowerThird = profile.isLowerThird
    val target = if (lowerThird) SongStyleTarget.LOWER_THIRD else SongStyleTarget.FULL_SCREEN
    val styleElement = targets.styleElement()
    val offered = songLanguagesOffered(profile, styleElement)
    val editing = targets.language.value?.takeIf { it in offered }
    val update: ((SongSettings) -> SongSettings) -> Unit = { transform ->
        onSettingsChange { s -> s.copy(songSettings = transform(s.songSettings)) }
    }
    val edit = SongEdit(song, styleElement, target, editing, offered.isNotEmpty(), update)
    val style = edit.style
    val margins = Margins(song.marginTop, song.marginBottom, song.marginLeft, song.marginRight)
    return AdjustModel(
        margins = Adjustable(margins) { m ->
            update { it.copy(marginTop = m.top, marginBottom = m.bottom, marginLeft = m.left, marginRight = m.right) }
        },
        alignment = Adjustable(song.lyricsAlignment) { a ->
            update {
                val extras = it.layoutExtras
                it.copy(
                    lyricsAlignment = a,
                    layoutExtras = extras.copy(contentRegion = extras.contentRegion.copy(yOffsetPercent = 0)),
                )
            }
        },
        region = if (lowerThird) {
            null
        } else {
            Adjustable(song.layoutExtras.contentRegion) { r ->
                update { it.copy(layoutExtras = it.layoutExtras.copy(contentRegion = r)) }
            }
        },
        textSize = Adjustable(style.fontSize) { v -> edit.write(style.copy(fontSize = v)) },
        band = if (lowerThird) {
            Adjustable(song.lowerThirdHeightPercent) { v -> update { it.copy(lowerThirdHeightPercent = v) } }
        } else {
            null
        },
        blocks = songBlockTargets(profile, targets, edit, update),
        positions = PositionsReset(moved = song.layoutExtras.elementShifts.keys.any { it.onOutput(lowerThird) }) {
            update { s ->
                val shifts = s.layoutExtras.elementShifts.filterKeys { !it.onOutput(lowerThird) }
                s.copy(layoutExtras = s.layoutExtras.copy(elementShifts = shifts))
            }
        },
    )
}

/** Whether the move stored under this key is on [lowerThird]'s output -- see `songElementShiftKey`. */
private fun String.onOutput(lowerThird: Boolean): Boolean = endsWith(LOWER_THIRD_KEY_SUFFIX) == lowerThird

private const val LOWER_THIRD_KEY_SUFFIX = "@LT"

/**
 * Every element the preview may draw, each a block: the title slide's heading and credits under its
 * chip, and otherwise the number, the title and -- per language -- the lyrics and the look-ahead.
 */
private fun songBlockTargets(
    profile: OutputProfile,
    targets: SongTargets,
    edit: SongEdit,
    update: ((SongSettings) -> SongSettings) -> Unit,
): BlockTargets {
    val song = edit.song
    val styleElement = edit.element
    val editing = edit.language
    val lowerThird = profile.isLowerThird
    val languages = styleLanguagesFor(profile.songMode, profile.songTranslations).map { it.translation }
    val titleSlide = targets.element.value == CustomizeElement.SONG_TITLE_SLIDE
    val blocks = if (titleSlide) {
        listOf(SongBlock(SongStyleElement.TITLE_SLIDE_NUMBER, null, lowerThird, true)) +
            languages.map { SongBlock(SongStyleElement.TITLE, it, lowerThird, true) } +
            TITLE_SLIDE_ELEMENTS.filter { it.isCredit }.map { SongBlock(it, null, lowerThird, true) }
    } else {
        listOf(
            SongBlock(SongStyleElement.NUMBER, null, lowerThird, false),
            SongBlock(SongStyleElement.TITLE, languages.first(), lowerThird, false),
        ) + listOf(SongStyleElement.LYRICS, SongStyleElement.LOOK_AHEAD, SongStyleElement.NEXT_SECTION)
            .flatMap { element -> languages.map { SongBlock(element, it, lowerThird, false) } }
    }
    val selected = blocks.indexOfFirst {
        it.element == styleElement && (it.language == null || it.language == (editing?.translation ?: it.language))
    }.takeIf { it >= 0 }
    // Under All, or where the element has no look per language, the element moves as a whole.
    val shiftKey = songShiftKey(styleElement, lowerThird, editing?.translation, titleSlide)
    return BlockTargets(
        kind = PresentedBlock.Kind.ELEMENT,
        keys = blocks.map { it.key },
        selected = selected,
        onSelect = { index -> blocks[index].pickIn(targets, profile) },
        shift = Adjustable(song.shiftAt(shiftKey)) { (x, y) -> update { it.shiftedAt(shiftKey, x, y) } },
    )
}

/** Points the Text rows at this block's element -- and at its language, where the page offers them. */
private fun SongBlock.pickIn(targets: SongTargets, profile: OutputProfile) {
    if (element.onTitleSlide && targets.element.value == CustomizeElement.SONG_TITLE_SLIDE) {
        targets.slideElement.onChange(element)
    } else {
        targets.element.onChange(element.customizeElement())
    }
    val offered = songLanguagesOffered(profile, element)
    val slot = language
    if (slot != null && offered.isNotEmpty()) {
        targets.language.onChange(offered.firstOrNull { it.translation == slot })
    }
}

/** The Songs strip's element chip for [this]. */
private fun SongStyleElement.customizeElement(): CustomizeElement = when (this) {
    SongStyleElement.NUMBER -> CustomizeElement.SONG_NUMBER
    SongStyleElement.TITLE -> CustomizeElement.SONG_TITLE
    SongStyleElement.LOOK_AHEAD -> CustomizeElement.SONG_LOOK_AHEAD
    SongStyleElement.NEXT_SECTION -> CustomizeElement.SONG_NEXT_SECTION
    SongStyleElement.LYRICS -> CustomizeElement.SONG_LYRICS
    else -> CustomizeElement.SONG_TITLE_SLIDE
}
