package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.customize_group_reference
import churchpresenter.composeapp.generated.resources.customize_group_verse_text
import churchpresenter.composeapp.generated.resources.song_element_look_ahead
import churchpresenter.composeapp.generated.resources.song_element_lyrics
import churchpresenter.composeapp.generated.resources.song_element_next_section
import churchpresenter.composeapp.generated.resources.song_element_number
import churchpresenter.composeapp.generated.resources.song_element_title
import churchpresenter.composeapp.generated.resources.song_target_title_slide
import org.churchpresenter.settings.OutputStyleScope
import org.jetbrains.compose.resources.stringResource

/**
 * One thing a category draws — the chips above the Customize dialog's control column.
 *
 * The panes used to stack every group of every category in one long scroll, which put six to nine
 * headings between the operator and the one line they came to restyle. An element is that heading
 * promoted to a selector: pick the element, and the column below shows only what styles it.
 *
 * This is the same move the global Bible and Song tabs already make with [BibleStyleElement] and
 * [SongStyleElement], and the Bible and Song chips map straight onto those. Dictionary and
 * Background have no such enum of their own, so their entries name the settings group directly.
 */
internal enum class CustomizeElement {
    BIBLE_TEXT,
    BIBLE_REFERENCE,
    SONG_LYRICS,
    SONG_TITLE,
    SONG_NUMBER,
    SONG_LOOK_AHEAD,
    SONG_NEXT_SECTION,
    SONG_TITLE_SLIDE,
    BACKGROUND_DEFAULT,
    BACKGROUND_BIBLE,
    BACKGROUND_SONG,
}

/**
 * The elements [pane] offers, in chip order.
 *
 * The whole-form categories ([isWholeForm]) have none: each is one form of its own rather than a
 * set of styled elements, so it keeps the whole column and shows no chips at all.
 */
internal fun customizeElements(pane: CustomizePane): List<CustomizeElement> = when (pane) {
    CustomizePane.STAGE_MONITOR,
    CustomizePane.CAPTIONS,
    CustomizePane.SUBTITLES,
    CustomizePane.QA,
    CustomizePane.DICTIONARY,
    -> emptyList()
    CustomizePane.BIBLE -> listOf(CustomizeElement.BIBLE_TEXT, CustomizeElement.BIBLE_REFERENCE)
    // No "Slide" entry: everything that belonged to the slide rather than to one thing drawn on it
    // -- the margins, the fades, the band height, the word wrap, the vertical alignment, the
    // end-of-song marker -- now sits on the strip beneath the preview, where the picture it moves is
    // in the same glance. The chip drew four sections, three of which the strip already carried.
    // Lyrics lead, as the Bible's verse text does: the first chip is also the one the pane opens on
    // (see [ProfileEditor]'s `element` default), and the lyrics are what an operator is
    // overwhelmingly here to style. Opening on the title slide also hid the arrangement control,
    // which belongs to the lyrics and is deliberately absent on a title slide -- so the one setting
    // this pane had just gained could not be found without guessing which chip held it.
    CustomizePane.SONGS -> listOf(
        CustomizeElement.SONG_LYRICS,
        CustomizeElement.SONG_TITLE_SLIDE,
        CustomizeElement.SONG_TITLE,
        CustomizeElement.SONG_NUMBER,
        CustomizeElement.SONG_LOOK_AHEAD,
        CustomizeElement.SONG_NEXT_SECTION,
    )
    CustomizePane.BACKGROUND -> listOf(
        CustomizeElement.BACKGROUND_DEFAULT,
        CustomizeElement.BACKGROUND_BIBLE,
        CustomizeElement.BACKGROUND_SONG,
    )
}

@Composable
internal fun CustomizeElement.label(): String = when (this) {
    CustomizeElement.BIBLE_TEXT -> stringResource(Res.string.customize_group_verse_text)
    CustomizeElement.BIBLE_REFERENCE -> stringResource(Res.string.customize_group_reference)
    CustomizeElement.SONG_LYRICS -> stringResource(Res.string.song_element_lyrics)
    CustomizeElement.SONG_TITLE -> stringResource(Res.string.song_element_title)
    CustomizeElement.SONG_NUMBER -> stringResource(Res.string.song_element_number)
    CustomizeElement.SONG_LOOK_AHEAD -> stringResource(Res.string.song_element_look_ahead)
    CustomizeElement.SONG_NEXT_SECTION -> stringResource(Res.string.song_element_next_section)
    CustomizeElement.SONG_TITLE_SLIDE -> stringResource(Res.string.song_target_title_slide)
    // Named for the surface this output actually writes. Which of the pair that is follows from
    // the output's own shape rather than from anything chosen here -- but the chip still has to
    // say which, because the title it produces names it: hardcoding the full-screen scope made a
    // Lower Third output's chips read "Bible - Full Screen" while they edited the band.
    CustomizeElement.BACKGROUND_DEFAULT,
    CustomizeElement.BACKGROUND_BIBLE,
    CustomizeElement.BACKGROUND_SONG,
    -> backgroundScopeTitle(
        backgroundScope(lowerThird = LocalOutputStyleScope.current == OutputStyleScope.LOWER_THIRD),
    )
}

/**
 * The background surface a Background chip stands for, on an output of this shape.
 *
 * A full-screen output and a lower third store their backgrounds in different surfaces, and an
 * output only ever draws one of the two — so the chip names the *surface* ("Default", "Bible") and
 * the output's own shape decides which of the pair it writes.
 *
 * Reads the full-screen scope for anything that is not a Background chip; nothing calls it there.
 */
internal fun CustomizeElement.backgroundScope(lowerThird: Boolean): BackgroundScope = when (this) {
    CustomizeElement.BACKGROUND_BIBLE ->
        if (lowerThird) BackgroundScope.BIBLE_LOWER_THIRD else BackgroundScope.BIBLE
    CustomizeElement.BACKGROUND_SONG ->
        if (lowerThird) BackgroundScope.SONG_LOWER_THIRD else BackgroundScope.SONG
    else -> if (lowerThird) BackgroundScope.DEFAULT_LOWER_THIRD else BackgroundScope.DEFAULT
}

/** Test handle for one element chip. */
internal fun elementChipTag(elementName: String): String = "customize_element_$elementName"
