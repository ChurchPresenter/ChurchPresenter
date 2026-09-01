package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.customize_group_card
import churchpresenter.composeapp.generated.resources.customize_group_definition
import churchpresenter.composeapp.generated.resources.customize_group_reference
import churchpresenter.composeapp.generated.resources.customize_group_verse_text
import churchpresenter.composeapp.generated.resources.customize_group_word
import churchpresenter.composeapp.generated.resources.dictionary_settings_kjv_usage
import churchpresenter.composeapp.generated.resources.dictionary_settings_reference_text
import churchpresenter.composeapp.generated.resources.song_element_look_ahead
import churchpresenter.composeapp.generated.resources.song_element_lyrics
import churchpresenter.composeapp.generated.resources.song_element_next_section
import churchpresenter.composeapp.generated.resources.song_element_number
import churchpresenter.composeapp.generated.resources.song_element_title
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
    DICTIONARY_WORD,
    DICTIONARY_REFERENCE,
    DICTIONARY_DEFINITION,
    DICTIONARY_KJV,
    DICTIONARY_CARD,
    BACKGROUND_DEFAULT,
    BACKGROUND_BIBLE,
    BACKGROUND_SONG,
}

/**
 * The elements [pane] offers, in chip order.
 *
 * The Stage Monitor has none: its pane is a zone layout picker rather than a set of styled
 * elements, so it keeps the whole column and shows no chips at all.
 */
internal fun customizeElements(pane: CustomizePane): List<CustomizeElement> = when (pane) {
    CustomizePane.STAGE_MONITOR -> emptyList()
    CustomizePane.BIBLE -> listOf(CustomizeElement.BIBLE_TEXT, CustomizeElement.BIBLE_REFERENCE)
    CustomizePane.SONGS -> listOf(
        CustomizeElement.SONG_LYRICS,
        CustomizeElement.SONG_TITLE,
        CustomizeElement.SONG_NUMBER,
        CustomizeElement.SONG_LOOK_AHEAD,
        CustomizeElement.SONG_NEXT_SECTION,
    )
    CustomizePane.DICTIONARY -> listOf(
        CustomizeElement.DICTIONARY_WORD,
        CustomizeElement.DICTIONARY_REFERENCE,
        CustomizeElement.DICTIONARY_DEFINITION,
        CustomizeElement.DICTIONARY_KJV,
        CustomizeElement.DICTIONARY_CARD,
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
    CustomizeElement.DICTIONARY_WORD -> stringResource(Res.string.customize_group_word)
    CustomizeElement.DICTIONARY_REFERENCE -> stringResource(Res.string.dictionary_settings_reference_text)
    CustomizeElement.DICTIONARY_DEFINITION -> stringResource(Res.string.customize_group_definition)
    CustomizeElement.DICTIONARY_KJV -> stringResource(Res.string.dictionary_settings_kjv_usage)
    CustomizeElement.DICTIONARY_CARD -> stringResource(Res.string.customize_group_card)
    // Named by the surface rather than by the output's shape: the chip says "Bible", and whether
    // that writes the full-screen or the lower-third surface is the output's business, not a
    // choice the operator makes here.
    CustomizeElement.BACKGROUND_DEFAULT,
    CustomizeElement.BACKGROUND_BIBLE,
    CustomizeElement.BACKGROUND_SONG,
    -> backgroundScopeTitle(backgroundScope(lowerThird = false))
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
