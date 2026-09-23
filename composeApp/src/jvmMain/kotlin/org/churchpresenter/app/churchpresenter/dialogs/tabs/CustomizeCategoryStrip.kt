package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.background
import churchpresenter.composeapp.generated.resources.bible_split_long_verses
import churchpresenter.composeapp.generated.resources.customize_type_default
import churchpresenter.composeapp.generated.resources.customize_type_lottie
import churchpresenter.composeapp.generated.resources.lower_third_animation
import churchpresenter.composeapp.generated.resources.bible_translation_divider
import churchpresenter.composeapp.generated.resources.bible_translation_spacing
import churchpresenter.composeapp.generated.resources.words_suffix
import churchpresenter.composeapp.generated.resources.bottom
import churchpresenter.composeapp.generated.resources.end_of_song_spacing
import churchpresenter.composeapp.generated.resources.middle
import churchpresenter.composeapp.generated.resources.show
import churchpresenter.composeapp.generated.resources.song_auto_repeat_chorus
import churchpresenter.composeapp.generated.resources.song_section_label
import churchpresenter.composeapp.generated.resources.song_section_label_color
import churchpresenter.composeapp.generated.resources.song_section_label_enabled
import churchpresenter.composeapp.generated.resources.song_section_label_font_size
import churchpresenter.composeapp.generated.resources.word_wrap
import churchpresenter.composeapp.generated.resources.customize_layout
import churchpresenter.composeapp.generated.resources.customize_marker
import churchpresenter.composeapp.generated.resources.left
import churchpresenter.composeapp.generated.resources.lower_third_size
import churchpresenter.composeapp.generated.resources.right
import churchpresenter.composeapp.generated.resources.top
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbarGutter
import org.churchpresenter.app.churchpresenter.viewmodel.LONG_VERSE_WORDS_MAX
import org.churchpresenter.app.churchpresenter.viewmodel.LONG_VERSE_WORDS_MIN
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.OutputStyleScope
import org.churchpresenter.settings.SongSectionLabel
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/**
 * The settings that belong to the whole category rather than to one element, under the preview.
 *
 * Margins, fades and the band's geometry are properties of the *picture*, not of the verse text or
 * the reference inside it — so they sit beside the picture, where an edit and the thing it moves
 * are in the same glance. Keeping them in the element column instead would mean either repeating
 * them under every chip or hiding them under an arbitrary one.
 *
 * Built from the same [NumberControl]/[ToggleControl]/[SliderControl] the panes use, so a margin
 * typed here and a margin typed on the global tab are the same field in the same box.
 */
@Composable
internal fun CustomizeCategoryStrip(
    pane: CustomizePane,
    /** Which chip is selected, so a row that cannot move *this* picture is not offered under it. */
    element: CustomizeElement?,
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    /** Opens another category on the rail, at a chosen chip -- see [LottieBandStripRow]. */
    onNavigate: (CustomizePane, CustomizeElement) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lowerThird = LocalOutputStyleScope.current == OutputStyleScope.LOWER_THIRD
    // Background and the stage monitor have nothing at this level: a background surface is entirely
    // per-element, and the stage monitor's zones carry their own geometry.
    if (pane == CustomizePane.BACKGROUND || pane == CustomizePane.STAGE_MONITOR) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = SettingsScrollbarGutter, top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (pane) {
            CustomizePane.BIBLE -> {
            BibleStrip(
                bs = settings.bibleSettings,
                lowerThird = lowerThird,
            ) { transform ->
                onSettingsChange { s -> s.copy(bibleSettings = transform(s.bibleSettings)) }
            }
            if (lowerThird) {
                LottieBandStripRow(
                    lottiePath = settings.backgroundSettings.bibleLowerThirdBackground.backgroundLottie,
                    isLottie = settings.backgroundSettings.bibleLowerThirdBackground.backgroundType ==
                        Constants.BACKGROUND_LOTTIE,
                    onOpen = { onNavigate(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE) },
                )
            }
            }
            CustomizePane.SONGS -> {
            SongStrip(
                ss = settings.songSettings,
                lowerThird = lowerThird,
                // The title slide is a heading and its credits. It does not wrap lyrics, does not
                // repeat a chorus, takes its vertical alignment from its own control in the pane
                // rather than from the lyrics', and carries no end-of-song marker -- so under that
                // chip those rows would sit beside a picture none of them can change.
                lyricSlide = element != CustomizeElement.SONG_TITLE_SLIDE,
            ) { transform ->
                onSettingsChange { s -> s.copy(songSettings = transform(s.songSettings)) }
            }
            if (lowerThird) {
                LottieBandStripRow(
                    lottiePath = settings.backgroundSettings.songLowerThirdBackground.backgroundLottie,
                    isLottie = settings.backgroundSettings.songLowerThirdBackground.backgroundType ==
                        Constants.BACKGROUND_LOTTIE,
                    onOpen = { onNavigate(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_SONG) },
                )
            }
            }
            // Ruled out by the guard above; the compiler sees the same and calls an `else` here
            // redundant.
            CustomizePane.BACKGROUND, CustomizePane.STAGE_MONITOR -> Unit
        }
    }
}

@Composable
private fun BibleStrip(
    bs: BibleSettings,
    lowerThird: Boolean,
    update: ((BibleSettings) -> BibleSettings) -> Unit,
) {
    MarginsStripRow(
        top = bs.marginTop,
        bottom = bs.marginBottom,
        left = bs.marginLeft,
        right = bs.marginRight,
        onTop = { v -> update { it.copy(marginTop = v) } },
        onBottom = { v -> update { it.copy(marginBottom = v) } },
        onLeft = { v -> update { it.copy(marginLeft = v) } },
        onRight = { v -> update { it.copy(marginRight = v) } },
    )
    MotionStripRow(
        fadeIn = bs.fadeIn,
        fadeOut = bs.fadeOut,
        crossfade = bs.crossfade,
        durationMs = bs.transitionDuration,
        onFadeIn = { v -> update { it.copy(fadeIn = v) } },
        onFadeOut = { v -> update { it.copy(fadeOut = v) } },
        onCrossfade = { v -> update { it.copy(crossfade = v) } },
        onDuration = { v -> update { it.copy(transitionDuration = v) } },
    )
    StripRow(stringResource(Res.string.customize_layout)) {
        if (lowerThird) {
            NumberControl(
                label = stringResource(Res.string.lower_third_size),
                value = bs.lowerThirdHeightPercent,
                onValueChange = { v -> update { it.copy(lowerThirdHeightPercent = v) } },
                range = BAND_RANGE,
            )
        }
        ToggleControl(
            label = stringResource(Res.string.bible_translation_divider),
            checked = bs.multiTranslationDivider,
            onCheckedChange = { v -> update { it.copy(multiTranslationDivider = v) } },
        )
        NumberControl(
            label = stringResource(Res.string.bible_translation_spacing),
            value = bs.multiTranslationSpacing,
            onValueChange = { v -> update { it.copy(multiTranslationSpacing = v) } },
            range = SPACING_RANGE_MIN..SPACING_RANGE_MAX,
        )
        ToggleControl(
            label = stringResource(Res.string.bible_split_long_verses),
            checked = bs.splitLongVerses,
            onCheckedChange = { v -> update { it.copy(splitLongVerses = v) } },
        )
        if (bs.splitLongVerses) {
            NumberControl(
                label = stringResource(Res.string.words_suffix),
                value = bs.longVerseWordCount,
                onValueChange = { v -> update { it.copy(longVerseWordCount = v) } },
                range = LONG_VERSE_WORDS_MIN..LONG_VERSE_WORDS_MAX,
            )
        }
    }
    // Full screen only: a lower third's own width IS the band, so narrowing/shifting it the way a
    // video mixer split would is meaningless there -- the band already leaves the rest of the
    // screen alone.
    if (!lowerThird) {
        ContentRegionStripRow(bs.contentRegion) { v -> update { it.copy(contentRegion = v) } }
    }
}

/**
 * A signpost to the Lottie band, which lives under Background rather than here.
 *
 * The band is a *background* -- `BackgroundConfig.backgroundType == BACKGROUND_LOTTIE` on this
 * category's lower-third surface -- so its controls are on the Background category's own pane. An
 * operator thinking "the animated band behind my verses" looks under Bible or Songs, though, and
 * before this row there was nothing there to find and nothing saying where to look: the Bible tab's
 * old `LowerThirdAnimationSection` was that door and went with the tab.
 */
@Composable
private fun LottieBandStripRow(lottiePath: String, isLottie: Boolean, onOpen: () -> Unit) {
    StripRow(stringResource(Res.string.lower_third_animation)) {
        Text(
            text = when {
                !isLottie -> stringResource(Res.string.customize_type_default)
                lottiePath.isBlank() -> stringResource(Res.string.customize_type_lottie)
                else -> lottiePath.substringAfterLast('/').substringBeforeLast('.')
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = LOTTIE_NAME_MAX_WIDTH),
        )
        OutlinedButton(
            onClick = onOpen,
            shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        ) {
            Text(
                text = stringResource(Res.string.background),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

/** Enough for a template's name; the button beside it must stay on the row. */
private val LOTTIE_NAME_MAX_WIDTH = 150.dp

/** Lines between the last line of a song and the marker after it; the global tab's own cap. */
private val END_OF_SONG_SPACING_RANGE = 0..20

@Composable
private fun SongStrip(
    ss: SongSettings,
    lowerThird: Boolean,
    lyricSlide: Boolean,
    update: ((SongSettings) -> SongSettings) -> Unit,
) {
    MarginsStripRow(
        top = ss.marginTop,
        bottom = ss.marginBottom,
        left = ss.marginLeft,
        right = ss.marginRight,
        onTop = { v -> update { it.copy(marginTop = v) } },
        onBottom = { v -> update { it.copy(marginBottom = v) } },
        onLeft = { v -> update { it.copy(marginLeft = v) } },
        onRight = { v -> update { it.copy(marginRight = v) } },
    )
    MotionStripRow(
        fadeIn = ss.fadeIn,
        fadeOut = ss.fadeOut,
        crossfade = ss.crossfade,
        durationMs = ss.transitionDuration,
        onFadeIn = { v -> update { it.copy(fadeIn = v) } },
        onFadeOut = { v -> update { it.copy(fadeOut = v) } },
        onCrossfade = { v -> update { it.copy(crossfade = v) } },
        onDuration = { v -> update { it.copy(transitionDuration = v) } },
    )
    // The slide itself: how the lyrics are laid out on it and how it ends. These were a chip of
    // their own until they joined the strip -- and that chip's other three sections were the
    // margins, the fades and the band height, all three of which are already on this strip, so it
    // was showing the operator the same settings twice under two different headings.
    if (lowerThird || lyricSlide) {
        StripRow(stringResource(Res.string.customize_layout)) {
            if (lowerThird) {
                NumberControl(
                    label = stringResource(Res.string.lower_third_size),
                    value = ss.lowerThirdHeightPercent,
                    onValueChange = { v -> update { it.copy(lowerThirdHeightPercent = v) } },
                    range = BAND_RANGE,
                )
            }
            if (lyricSlide) {
                ToggleControl(
                    label = stringResource(Res.string.word_wrap),
                    checked = ss.wordWrap,
                    onCheckedChange = { v -> update { it.copy(wordWrap = v) } },
                )
                ToggleControl(
                    label = stringResource(Res.string.song_auto_repeat_chorus),
                    checked = ss.autoRepeatChorus,
                    onCheckedChange = { v -> update { it.copy(autoRepeatChorus = v) } },
                )
                // The lyric slides' own vertical alignment. The title slide keeps a separate one,
                // which is why this row is absent rather than disabled under that chip: two
                // controls both reading "Top / Middle / Bottom" on one screen, one of them inert,
                // is worse than one control in the place that owns it.
                ChoiceControl(
                    options = listOf(
                        Constants.TOP to stringResource(Res.string.top),
                        Constants.MIDDLE to stringResource(Res.string.middle),
                        Constants.BOTTOM to stringResource(Res.string.bottom),
                    ),
                    selected = ss.lyricsAlignment,
                    onSelect = { v -> update { it.copy(lyricsAlignment = v) } },
                )
            }
        }
    }
    // The marker after the last line, and how far below it sits. Off means the spacing changes
    // nothing, so the field follows the switch rather than standing beside it doing nothing.
    if (lyricSlide) {
        StripRow(stringResource(Res.string.customize_marker)) {
        ToggleControl(
            label = stringResource(Res.string.show),
            checked = ss.showEndOfSongIndicator,
            onCheckedChange = { v -> update { it.copy(showEndOfSongIndicator = v) } },
        )
        if (ss.showEndOfSongIndicator) {
            NumberControl(
                    label = stringResource(Res.string.end_of_song_spacing).removeSuffix(":"),
                    value = ss.endOfSongIndicatorSpacing,
                    onValueChange = { v -> update { it.copy(endOfSongIndicatorSpacing = v) } },
                    range = END_OF_SONG_SPACING_RANGE,
                )
            }
        }
    }
    // Full screen only, for the same reason as the Bible strip's own: a lower third's width already
    // is the band, so there is nothing here for it to narrow or shift.
    if (!lowerThird) {
        ContentRegionStripRow(ss.layoutExtras.contentRegion) { v ->
            update { it.copy(layoutExtras = it.layoutExtras.copy(contentRegion = v)) }
        }
    }
    if (lyricSlide) {
        SectionLabelStripRow(ss.layoutExtras.sectionLabel) { transform ->
            update { song ->
                val extras = song.layoutExtras.copy(sectionLabel = transform(song.layoutExtras.sectionLabel))
                song.copy(layoutExtras = extras)
            }
        }
    }
}

/**
 * The current section's own label ("Verse 1", "Chorus"), drawn above the lyrics on every output
 * that shows them -- a property of the slide as a whole rather than of one element, like the
 * marker in [SongStrip].
 */
@Composable
private fun SectionLabelStripRow(label: SongSectionLabel, update: ((SongSectionLabel) -> SongSectionLabel) -> Unit) {
    StripRow(stringResource(Res.string.song_section_label)) {
        ToggleControl(
            label = stringResource(Res.string.song_section_label_enabled),
            checked = label.enabled,
            onCheckedChange = { v -> update { it.copy(enabled = v) } },
        )
        if (label.enabled) {
            NumberControl(
                label = stringResource(Res.string.song_section_label_font_size),
                value = label.fontSize,
                onValueChange = { v -> update { it.copy(fontSize = v) } },
                range = SongSectionLabel.FONT_SIZE_RANGE,
            )
            ColorControl(
                label = stringResource(Res.string.song_section_label_color),
                color = label.color,
                onColorChange = { v -> update { it.copy(color = v) } },
            )
        }
    }
}

