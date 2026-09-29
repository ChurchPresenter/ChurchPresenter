package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.profile_box
import churchpresenter.composeapp.generated.resources.profile_box_pick_language
import org.churchpresenter.app.churchpresenter.presenter.boxedPerLanguage
import org.churchpresenter.app.churchpresenter.presenter.songBoxKey
import org.churchpresenter.app.churchpresenter.presenter.titleSlideBoxKey
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.boxAt
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.withBox
import org.jetbrains.compose.resources.stringResource

/** The song elements that can be given a box -- every one a lyric slide draws. */
private val BOXABLE_SONG_ELEMENTS = setOf(
    SongStyleElement.NUMBER,
    SongStyleElement.TITLE,
    SongStyleElement.LYRICS,
    SongStyleElement.LOOK_AHEAD,
    SongStyleElement.NEXT_SECTION,
    SongStyleElement.SECTION_LABEL,
)

/**
 * The box the Songs page's rows edit for the element and language picked: its key, the box as
 * stored, and the language it is drawn for. [needsLanguage] when the element has a box per
 * language and All is picked, where there is no one box to edit.
 */
internal data class SongBoxTarget(
    val key: String,
    val box: TextBox,
    val element: SongStyleElement,
    val language: Int?,
    val needsLanguage: Boolean = false,
    /** Whether the box is the title slide's own rather than a lyric slide's. */
    val titleSlide: Boolean = false,
)

/** The box the rows edit for this edit's element and language, on a lyric slide or the title slide. */
internal fun SongEdit.boxTarget(lowerThird: Boolean, titleSlide: Boolean): SongBoxTarget? = if (titleSlide) {
    titleSlideBoxTarget(song, element, lowerThird, language, perLanguage)
} else {
    songBoxTarget(song, element, lowerThird, language, perLanguage)
}

/** What the rows edit, or null for an element with no box. */
private fun songBoxTarget(
    song: SongSettings,
    element: SongStyleElement,
    lowerThird: Boolean,
    editingLanguage: SongStyleLanguage?,
    perLanguage: Boolean,
): SongBoxTarget? {
    if (element !in BOXABLE_SONG_ELEMENTS) return null
    val ownPerLanguage = element.boxedPerLanguage && !song.layoutExtras.textBoxOptions.sharedLanguageBox
    val language = when {
        !ownPerLanguage -> null
        editingLanguage != null -> editingLanguage.translation
        perLanguage -> return SongBoxTarget("", TextBox(), element, null, needsLanguage = true)
        else -> 0
    }
    val key = song.songBoxKey(element, lowerThird, language)
    return SongBoxTarget(key, song.layoutExtras.textBoxes.boxAt(key), element, language)
}

/** The title slide's own box for [element] -- a title line has one per language, the rest one each. */
private fun titleSlideBoxTarget(
    song: SongSettings,
    element: SongStyleElement,
    lowerThird: Boolean,
    editingLanguage: SongStyleLanguage?,
    perLanguage: Boolean,
): SongBoxTarget {
    val ownPerLanguage = element == SongStyleElement.TITLE && !song.layoutExtras.textBoxOptions.sharedLanguageBox
    val language = when {
        !ownPerLanguage -> null
        editingLanguage != null -> editingLanguage.translation
        perLanguage -> return SongBoxTarget("", TextBox(), element, null, needsLanguage = true, titleSlide = true)
        else -> 0
    }
    val key = song.titleSlideBoxKey(element, lowerThird, language)
    return SongBoxTarget(key, song.layoutExtras.textBoxes.boxAt(key), element, language, titleSlide = true)
}

/**
 * Where [element]'s box starts the first time it is turned on, in percent of the whole screen --
 * near where the layout puts the element, so the picture changes as little as it can.
 */
internal fun defaultSongBox(element: SongStyleElement, language: Int?, titleSlide: Boolean = false): TextBox =
    if (titleSlide) defaultTitleSlideBox(element, language) else defaultLyricSlideBox(element, language)

/** Where a title slide element's box starts: the heading high in the middle, the credits in a column under it. */
private fun defaultTitleSlideBox(element: SongStyleElement, language: Int?): TextBox = when (element) {
    SongStyleElement.TITLE_SLIDE_NUMBER ->
        TextBox(xPercent = 2f, yPercent = 88f, widthPercent = 10f, heightPercent = 9f)
    SongStyleElement.TITLE -> TextBox(
        xPercent = 10f,
        yPercent = HEADING_TOP + (language ?: 0) * HEADING_STEP,
        widthPercent = 80f,
        heightPercent = HEADING_STEP,
    )
    else -> TextBox(
        xPercent = 20f,
        yPercent = CREDITS_TOP + CREDIT_ORDER.indexOf(element).coerceAtLeast(0) * CREDIT_STEP,
        widthPercent = 60f,
        heightPercent = CREDIT_HEIGHT,
    )
}

/** Where a lyric slide element's box starts: roughly where the layout draws it. */
private fun defaultLyricSlideBox(element: SongStyleElement, language: Int?): TextBox = when (element) {
    SongStyleElement.NUMBER -> TextBox(xPercent = 2f, yPercent = 2f, widthPercent = 10f, heightPercent = 9f)
    SongStyleElement.TITLE -> TextBox(xPercent = 10f, yPercent = 3f, widthPercent = 80f, heightPercent = 10f)
    SongStyleElement.SECTION_LABEL -> TextBox(xPercent = 30f, yPercent = 12f, widthPercent = 40f, heightPercent = 8f)
    SongStyleElement.NEXT_SECTION -> TextBox(xPercent = 10f, yPercent = 80f, widthPercent = 80f, heightPercent = 15f)
    // Lyrics: each language a band of its own down the screen.
    else -> TextBox(
        xPercent = 5f,
        yPercent = LYRIC_TOP + (language ?: 0).coerceIn(0, LYRIC_SLOTS - 1) * LYRIC_STEP,
        widthPercent = 90f,
        heightPercent = LYRIC_HEIGHT,
    )
}

private const val HEADING_TOP = 22f
private const val HEADING_STEP = 12f
private const val CREDITS_TOP = 58f
private const val CREDIT_STEP = 8f
private const val CREDIT_HEIGHT = 7f

private val CREDIT_ORDER =
    listOf(SongStyleElement.AUTHOR, SongStyleElement.COMPOSER, SongStyleElement.CCLI, SongStyleElement.TEMPO)

private const val LYRIC_SLOTS = 4
private const val LYRIC_TOP = 22f
private const val LYRIC_STEP = 19f
private const val LYRIC_HEIGHT = 18f

private val SONG_BOX_PATHS = listOf("songSettings.layoutExtras.textBoxes")
private val SONG_BOX_OPTION_PATHS = listOf("songSettings.layoutExtras.textBoxOptions")

/**
 * The Songs page's box rows for [target], or -- where the element has a box per language and All
 * is picked -- a line saying to pick a language first.
 */
@Composable
internal fun SongBoxRows(
    target: SongBoxTarget?,
    song: SongSettings,
    lowerThird: Boolean,
    perLanguage: Boolean,
    updateSong: ((SongSettings) -> SongSettings) -> Unit,
) {
    if (target == null) return
    if (target.needsLanguage) {
        SettingsRow(
            stringResource(Res.string.profile_box),
            sub = stringResource(Res.string.profile_box_pick_language),
        ) {}
        return
    }
    TextBoxRows(
        box = target.box,
        onBox = { box ->
            updateSong {
                val extras = it.layoutExtras
                it.copy(layoutExtras = extras.copy(textBoxes = extras.textBoxes.withBox(target.key, box)))
            }
        },
        startBox = defaultSongBox(target.element, target.language, target.titleSlide),
        options = song.layoutExtras.textBoxOptions,
        onOptions = { options ->
            updateSong { it.copy(layoutExtras = it.layoutExtras.copy(textBoxOptions = options)) }
        },
        perLanguage = perLanguage &&
            (if (target.titleSlide) target.element == SongStyleElement.TITLE else target.element.boxedPerLanguage),
        lowerThird = lowerThird,
        boxPaths = SONG_BOX_PATHS,
        optionPaths = SONG_BOX_OPTION_PATHS,
    )
}

/**
 * Whether [element] offers its Position row: it has a place to choose, and is not the title slide's
 * or [elsewhere] -- boxed -- or a number pinned to a corner, which is drawn over the slide and never
 * in the row this places.
 */
internal fun SongSettings.offersPosition(element: SongStyleElement, lowerThird: Boolean, elsewhere: Boolean): Boolean {
    val cornered = element == SongStyleElement.NUMBER && numberCorner(lowerThird) != Constants.NONE
    val placed = element.hasPosition || storedPosition(element, lowerThird) != null
    return placed && !elsewhere && !cornered
}
