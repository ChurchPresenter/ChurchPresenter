package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.app.churchpresenter.presenter.PresentedBlock
import org.churchpresenter.app.churchpresenter.presenter.songBoxKey
import org.churchpresenter.app.churchpresenter.presenter.titleSlideBoxKey
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.boxAt
import org.churchpresenter.settings.withBox

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
 * dragged by its body to move it on its own.
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
        positions = PositionsReset(
            moved = song.layoutExtras.elementShifts.keys.any { it.onOutput(lowerThird) } ||
                song.layoutExtras.textBoxes.any { (key, box) -> box.enabled && key.onOutput(lowerThird) },
        ) {
            update { s ->
                val shifts = s.layoutExtras.elementShifts.filterKeys { !it.onOutput(lowerThird) }
                // Boxes are turned off, not forgotten: turning one back on puts it where it was.
                val boxes = s.layoutExtras.textBoxes.mapValues { (key, box) ->
                    if (key.onOutput(lowerThird)) box.copy(enabled = false) else box
                }
                s.copy(layoutExtras = s.layoutExtras.copy(elementShifts = shifts, textBoxes = boxes))
            }
        },
        boxes = songBoxTargets(profile, targets, edit, update),
    )
}

/** Whether the move stored under this key is on [lowerThird]'s output -- see `songElementShiftKey`. */
private fun String.onOutput(lowerThird: Boolean): Boolean = endsWith(LOWER_THIRD_KEY_SUFFIX) == lowerThird

private const val LOWER_THIRD_KEY_SUFFIX = "@LT"

/**
 * Every element the preview may draw, each a block: the title slide's heading and credits under its
 * chip, and otherwise the number, the title, the section label and -- per language -- the lyrics and
 * the look-ahead.
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
    val titleSlide = targets.element.value == CustomizeElement.SONG_TITLE_SLIDE
    val blocks = songBlocks(profile, titleSlide)
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

/**
 * The song's boxes on this output, one handle each -- an element whose languages share a box is one
 * handle -- and the one the Text rows point at; null while none is turned on.
 */
private fun songBoxTargets(
    profile: OutputProfile,
    targets: SongTargets,
    edit: SongEdit,
    update: ((SongSettings) -> SongSettings) -> Unit,
): BoxTargets? {
    val song = edit.song
    val lowerThird = profile.isLowerThird
    val titleSlide = targets.element.value == CustomizeElement.SONG_TITLE_SLIDE
    val handles = songBlocks(profile, titleSlide).mapNotNull { block ->
        val key = if (titleSlide) {
            song.titleSlideBoxKey(block.element, lowerThird, block.language)
        } else {
            song.songBoxKey(block.element, lowerThird, block.language)
        }
        val box = song.layoutExtras.textBoxes.boxAt(key)
        if (!box.enabled) return@mapNotNull null
        BoxHandle(
            key = key,
            box = box,
            onChange = { changed ->
                update {
                    val extras = it.layoutExtras
                    it.copy(layoutExtras = extras.copy(textBoxes = extras.textBoxes.withBox(key, changed)))
                }
            },
            onPick = { block.pickIn(targets, profile) },
        )
    }.distinctBy { it.key }
    if (handles.isEmpty()) return null
    return BoxTargets(handles, edit.boxTarget(lowerThird, titleSlide)?.key, song.layoutExtras.textBoxOptions)
}

/**
 * Every element the preview may draw on this slide kind, as blocks: the title slide's heading and
 * credits, or the number, the title, the section label and -- per language -- the lyrics, the
 * look-ahead and the next section.
 */
private fun songBlocks(profile: OutputProfile, titleSlide: Boolean): List<SongBlock> {
    val lowerThird = profile.isLowerThird
    val languages = styleLanguagesFor(profile.songMode, profile.songTranslations).map { it.translation }
    return if (titleSlide) {
        listOf(SongBlock(SongStyleElement.TITLE_SLIDE_NUMBER, null, lowerThird, true)) +
            languages.map { SongBlock(SongStyleElement.TITLE, it, lowerThird, true) } +
            TITLE_SLIDE_ELEMENTS.filter { it.isCredit }.map { SongBlock(it, null, lowerThird, true) }
    } else {
        listOf(
            SongBlock(SongStyleElement.NUMBER, null, lowerThird, false),
            SongBlock(SongStyleElement.TITLE, languages.first(), lowerThird, false),
            SongBlock(SongStyleElement.SECTION_LABEL, null, lowerThird, false),
        ) + listOf(SongStyleElement.LYRICS, SongStyleElement.LOOK_AHEAD, SongStyleElement.NEXT_SECTION)
            .flatMap { element -> languages.map { SongBlock(element, it, lowerThird, false) } }
    }
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
    SongStyleElement.SECTION_LABEL -> CustomizeElement.SONG_SECTION_LABEL
    SongStyleElement.LYRICS -> CustomizeElement.SONG_LYRICS
    else -> CustomizeElement.SONG_TITLE_SLIDE
}
