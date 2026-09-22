package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.animation_crossfade
import churchpresenter.composeapp.generated.resources.bible_translation_divider
import churchpresenter.composeapp.generated.resources.bible_translation_spacing
import churchpresenter.composeapp.generated.resources.bilingual_left_right
import churchpresenter.composeapp.generated.resources.bilingual_top_bottom
import churchpresenter.composeapp.generated.resources.bottom
import churchpresenter.composeapp.generated.resources.end_of_song_spacing
import churchpresenter.composeapp.generated.resources.middle
import churchpresenter.composeapp.generated.resources.show
import churchpresenter.composeapp.generated.resources.song_auto_repeat_chorus
import churchpresenter.composeapp.generated.resources.word_wrap
import churchpresenter.composeapp.generated.resources.customize_group_margins
import churchpresenter.composeapp.generated.resources.customize_bilingual
import churchpresenter.composeapp.generated.resources.customize_layout
import churchpresenter.composeapp.generated.resources.customize_marker
import churchpresenter.composeapp.generated.resources.customize_motion
import churchpresenter.composeapp.generated.resources.fade_in
import churchpresenter.composeapp.generated.resources.fade_out
import churchpresenter.composeapp.generated.resources.left
import churchpresenter.composeapp.generated.resources.lower_third_size
import churchpresenter.composeapp.generated.resources.right
import churchpresenter.composeapp.generated.resources.top
import churchpresenter.composeapp.generated.resources.transition_duration
import churchpresenter.composeapp.generated.resources.content_region
import churchpresenter.composeapp.generated.resources.content_region_width
import churchpresenter.composeapp.generated.resources.content_region_x_offset
import churchpresenter.composeapp.generated.resources.content_region_y_offset
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbarGutter
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.ContentRegion
import org.churchpresenter.settings.DictionarySettings
import org.churchpresenter.settings.OutputStyleScope
import org.churchpresenter.settings.ScreenAssignment
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
    assignment: ScreenAssignment,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
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
            CustomizePane.BIBLE -> BibleStrip(
                bs = settings.bibleSettings,
                lowerThird = lowerThird,
                // Two translations have to be on this screen before their arrangement means
                // anything. `bibleTranslations` empty means "all of them", which is the usual case.
                parallel = assignment.bibleMode != Constants.SONG_LANG_OFF &&
                    (assignment.bibleTranslations.size > 1 || assignment.bibleTranslations.isEmpty()) &&
                    settings.bibleSettings.translationList().size > 1,
            ) { transform ->
                onSettingsChange { s -> s.copy(bibleSettings = transform(s.bibleSettings)) }
            }
            CustomizePane.SONGS -> SongStrip(
                ss = settings.songSettings,
                lowerThird = lowerThird,
                // The title slide is a heading and its credits. It does not wrap lyrics, does not
                // repeat a chorus, takes its vertical alignment from its own control in the pane
                // rather than from the lyrics', and carries no end-of-song marker -- so under that
                // chip those rows would sit beside a picture none of them can change.
                lyricSlide = element != CustomizeElement.SONG_TITLE_SLIDE,
                // This output's own language mode, which is what decides whether two languages
                // actually land on it -- the global tab asks the same question of the whole
                // install. A single-language screen has nothing to arrange.
                bilingual = assignment.songMode == Constants.SONG_LANG_BOTH,
            ) { transform ->
                onSettingsChange { s -> s.copy(songSettings = transform(s.songSettings)) }
            }
            CustomizePane.DICTIONARY -> DictionaryStrip(settings.dictionarySettings) { transform ->
                onSettingsChange { s -> s.copy(dictionarySettings = transform(s.dictionarySettings)) }
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
    parallel: Boolean,
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
    }
    // Full screen only: a lower third's own width IS the band, so narrowing/shifting it the way a
    // video mixer split would is meaningless there -- the band already leaves the rest of the
    // screen alone.
    if (!lowerThird) {
        ContentRegionStripRow(bs.contentRegion) { v -> update { it.copy(contentRegion = v) } }
    }
    // The two shapes keep separate values: a full screen stacks by default and a band splits by
    // default, which is what they have always drawn, so one shared field could not have preserved
    // both. Whichever shape this output is, it edits its own.
    if (parallel) {
        StripRow(stringResource(Res.string.customize_bilingual)) {
            ChoiceControl(
                options = listOf(
                    Constants.BILINGUAL_SIDE_BY_SIDE to stringResource(Res.string.bilingual_left_right),
                    Constants.BILINGUAL_TOP_BOTTOM to stringResource(Res.string.bilingual_top_bottom),
                ),
                selected = if (lowerThird) bs.bilingualLayoutLowerThird else bs.bilingualLayout,
                onSelect = { v ->
                    update {
                        if (lowerThird) it.copy(bilingualLayoutLowerThird = v)
                        else it.copy(bilingualLayout = v)
                    }
                },
            )
        }
    }
}

/** Lines between the last line of a song and the marker after it; the global tab's own cap. */
private val END_OF_SONG_SPACING_RANGE = 0..20

@Composable
private fun SongStrip(
    ss: SongSettings,
    lowerThird: Boolean,
    bilingual: Boolean,
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
    // How the two languages sit against each other -- the one control the global Song tab has at
    // this level that this dialog was missing. It is a property of the slide rather than of the
    // lyrics or the title, so it belongs on the strip and not under a chip; and it is one stored
    // value serving both shapes, exactly as `SongPresenter` reads it (a vertical band ignores
    // side-by-side and stacks regardless, having no width to split).
    if (bilingual) {
        StripRow(stringResource(Res.string.customize_bilingual)) {
            ChoiceControl(
                options = listOf(
                    Constants.BILINGUAL_SIDE_BY_SIDE to stringResource(Res.string.bilingual_left_right),
                    Constants.BILINGUAL_TOP_BOTTOM to stringResource(Res.string.bilingual_top_bottom),
                ),
                selected = ss.bilingualLayout,
                onSelect = { v -> update { it.copy(bilingualLayout = v) } },
            )
        }
    }
}

/** The dictionary card is centred on screen and has no margins of its own — only its fades. */
@Composable
private fun DictionaryStrip(ds: DictionarySettings, update: ((DictionarySettings) -> DictionarySettings) -> Unit) {
    MotionStripRow(
        fadeIn = ds.fadeIn,
        fadeOut = ds.fadeOut,
        crossfade = null,
        durationMs = ds.transitionDuration,
        onFadeIn = { v -> update { it.copy(fadeIn = v) } },
        onFadeOut = { v -> update { it.copy(fadeOut = v) } },
        onCrossfade = {},
        onDuration = { v -> update { it.copy(transitionDuration = v) } },
    )
}

/**
 * Width and X/Y offset for the whole content block -- the Customize dialog's own view of
 * [ContentRegionSection] on the global tabs, one field per output rather than one for the install.
 */
@Composable
private fun ContentRegionStripRow(region: ContentRegion, onChange: (ContentRegion) -> Unit) {
    StripRow(stringResource(Res.string.content_region)) {
        NumberControl(
            label = stringResource(Res.string.content_region_width),
            value = region.widthPercent,
            onValueChange = { v -> onChange(region.copy(widthPercent = v)) },
            range = ContentRegion.WIDTH_RANGE,
            width = MARGIN_FIELD_WIDTH,
        )
        NumberControl(
            label = stringResource(Res.string.content_region_x_offset),
            value = region.xOffsetPercent,
            onValueChange = { v -> onChange(region.copy(xOffsetPercent = v)) },
            range = ContentRegion.OFFSET_RANGE,
            width = MARGIN_FIELD_WIDTH,
        )
        NumberControl(
            label = stringResource(Res.string.content_region_y_offset),
            value = region.yOffsetPercent,
            onValueChange = { v -> onChange(region.copy(yOffsetPercent = v)) },
            range = ContentRegion.OFFSET_RANGE,
            width = MARGIN_FIELD_WIDTH,
        )
    }
}

/** The four insets, in the order the mockup reads them: top, bottom, left, right. */
@Composable
private fun MarginsStripRow(
    top: Int,
    bottom: Int,
    left: Int,
    right: Int,
    onTop: (Int) -> Unit,
    onBottom: (Int) -> Unit,
    onLeft: (Int) -> Unit,
    onRight: (Int) -> Unit,
) {
    // All four on one line, which is the only way they read as a set -- so they are given a width
    // rather than taking the generous one `NumberControl` derives from its caption. Four of those
    // came to more than this column and the last one wrapped underneath the other three.
    StripRow(stringResource(Res.string.customize_group_margins)) {
        NumberControl(stringResource(Res.string.top), top, onTop, MARGIN_RANGE, width = MARGIN_FIELD_WIDTH)
        NumberControl(stringResource(Res.string.bottom), bottom, onBottom, MARGIN_RANGE, width = MARGIN_FIELD_WIDTH)
        NumberControl(stringResource(Res.string.left), left, onLeft, MARGIN_RANGE, width = MARGIN_FIELD_WIDTH)
        NumberControl(stringResource(Res.string.right), right, onRight, MARGIN_RANGE, width = MARGIN_FIELD_WIDTH)
    }
}

/**
 * Fade in, fade out, crossfade and how long they take.
 *
 * [crossfade] is `null` for a category that has none — the dictionary card does not cross-dissolve
 * into the next one — which leaves the box out rather than showing one that writes nowhere.
 */
@Composable
private fun MotionStripRow(
    fadeIn: Boolean,
    fadeOut: Boolean,
    crossfade: Boolean?,
    durationMs: Float,
    onFadeIn: (Boolean) -> Unit,
    onFadeOut: (Boolean) -> Unit,
    onCrossfade: (Boolean) -> Unit,
    onDuration: (Float) -> Unit,
) {
    StripRow(stringResource(Res.string.customize_motion)) {
        ToggleControl(stringResource(Res.string.fade_in), fadeIn, onFadeIn)
        ToggleControl(stringResource(Res.string.fade_out), fadeOut, onFadeOut)
        if (crossfade != null) {
            ToggleControl(stringResource(Res.string.animation_crossfade), crossfade, onCrossfade)
        }
        NumberControl(
            label = stringResource(Res.string.transition_duration),
            value = durationMs.toInt(),
            onValueChange = { v -> onDuration(v.toFloat()) },
            range = DURATION_RANGE,
        )
    }
}

/**
 * One line of the strip: an uppercase caption in a fixed gutter, then whatever the line holds.
 *
 * Flowed rather than a plain row, so a narrow dialog wraps the four margin fields onto a second
 * line instead of squeezing them until their captions truncate — the same reason [CustomizeGroup]
 * flows its cells.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StripRow(label: String, content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = STRIP_CAPTION_SIZE,
            letterSpacing = STRIP_CAPTION_TRACKING,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.width(STRIP_CAPTION_WIDTH),
        )
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            // A line's height is set by its tallest item -- the boxed number field -- and FlowRow
            // tops its items out by default, so the fade checkboxes beside one sat against the top
            // of the line rather than on its centre line.
            itemVerticalAlignment = Alignment.CenterVertically,
            content = { content() },
        )
    }
}

/**
 * Four of these and their gaps are what this column has room for beside the caption, and no less
 * than "BOTTOM" needs: the caption is drawn inside the field, so at 74 it came out as "BOTT…".
 *
 * The budget is 72 for the caption gutter plus four of these plus three 8dp gaps, against the 402
 * the column has inside its padding -- which leaves exactly 76 each.
 */
private val MARGIN_FIELD_WIDTH = 76.dp

/**
 * Wide enough for the longest caption on one line.
 *
 * "BILINGUAL" is nine bold uppercase characters with tracking, which comes to about 65dp -- so at
 * 58 it wrapped, and a two-line caption pushes its whole row taller than every other row on the
 * strip. The budget is what the margins row needs: four 74dp fields and three 8dp gaps is 320, and
 * the column has 402 inside its padding, so anything up to 82 here still keeps them on one line.
 */
private val STRIP_CAPTION_WIDTH = 72.dp
private val STRIP_CAPTION_SIZE = 10.sp
private val STRIP_CAPTION_TRACKING = 0.9.sp
