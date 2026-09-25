package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.customize_bible
import churchpresenter.composeapp.generated.resources.customize_songs
import churchpresenter.composeapp.generated.resources.output_profile_add_language
import churchpresenter.composeapp.generated.resources.output_profile_add_translation
import churchpresenter.composeapp.generated.resources.output_profile_bible_count
import churchpresenter.composeapp.generated.resources.output_profile_bible_none_loaded
import churchpresenter.composeapp.generated.resources.output_profile_bible_off
import churchpresenter.composeapp.generated.resources.output_profile_bible_order_header
import churchpresenter.composeapp.generated.resources.output_profile_song_count
import churchpresenter.composeapp.generated.resources.output_profile_song_order_header
import churchpresenter.composeapp.generated.resources.output_profile_songs_off
import org.churchpresenter.settings.OutputProfile
import org.jetbrains.compose.resources.stringResource

/**
 * The profile's Bible source: which translations of the stack it draws, and in what order.
 *
 * The stack itself -- which Bibles are loaded, what each is called on screen -- is the Bible tab's,
 * and is one list for every output; a profile picks from it and orders what it picked.
 */
@Composable
internal fun BibleSourcePicker(
    profile: OutputProfile,
    stack: List<TranslationChoiceDisplay>,
    onProfileChange: (OutputProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = shownBiblePositions(profile, stack.size)
    OrderedSourcePicker(
        items = stack,
        shown = shown,
        strings = OrderedSourceStrings(
            label = stringResource(Res.string.customize_bible),
            offLabel = stringResource(Res.string.output_profile_bible_off),
            countFormat = stringResource(Res.string.output_profile_bible_count, shown.size, stack.size),
            noneLoaded = stringResource(Res.string.output_profile_bible_none_loaded),
            orderHeader = stringResource(Res.string.output_profile_bible_order_header),
            addHeader = stringResource(Res.string.output_profile_add_translation),
        ),
        tags = OrderedSourceTags(
            trigger = BIBLE_SOURCE_TRIGGER_TAG,
            orderRow = ::bibleOrderRowTag,
            addRow = ::bibleAddRowTag,
        ),
        onWrite = { next -> onProfileChange(withBiblePositions(profile, next, stack.size)) },
        modifier = modifier,
    )
}

/**
 * The profile's song source: which of a song's language slots it draws, and in what order.
 *
 * The order was always honoured -- `songLanguageSelection` reads `songTranslations` as the list it
 * is -- but nothing could write one: the checklist this replaces sorted its selection, so a profile
 * could say "the first and the third" and never "the third above the first". The Bible had the
 * ordered widget from the day profiles gained a stack; this is the same one.
 */
@Composable
internal fun SongSourcePicker(
    profile: OutputProfile,
    languages: List<TranslationChoiceDisplay>,
    onProfileChange: (OutputProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = shownSongPositions(profile, languages.size)
    OrderedSourcePicker(
        items = languages,
        shown = shown,
        strings = OrderedSourceStrings(
            label = stringResource(Res.string.customize_songs),
            offLabel = stringResource(Res.string.output_profile_songs_off),
            countFormat = stringResource(Res.string.output_profile_song_count, shown.size, languages.size),
            noneLoaded = stringResource(Res.string.output_profile_bible_none_loaded),
            orderHeader = stringResource(Res.string.output_profile_song_order_header),
            addHeader = stringResource(Res.string.output_profile_add_language),
        ),
        tags = OrderedSourceTags(
            trigger = SONG_SOURCE_TRIGGER_TAG,
            orderRow = ::songOrderRowTag,
            addRow = ::songAddRowTag,
        ),
        onWrite = { next -> onProfileChange(withSongPositions(profile, next, languages.size)) },
        modifier = modifier,
    )
}

/** Test handle for the song source's closed field. */
internal const val SONG_SOURCE_TRIGGER_TAG = "profile_song_source"

/** Test handle for the [slot]th language the profile draws, in its drawing order. */
internal fun songOrderRowTag(slot: Int): String = "profile_song_order_$slot"

/** Test handle for the "add" row of the language at slot [index]. */
internal fun songAddRowTag(index: Int): String = "profile_song_add_$index"
