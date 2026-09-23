package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import churchpresenter.composeapp.generated.resources.bottom
import churchpresenter.composeapp.generated.resources.content_region
import churchpresenter.composeapp.generated.resources.content_region_width
import churchpresenter.composeapp.generated.resources.content_region_x_offset
import churchpresenter.composeapp.generated.resources.content_region_y_offset
import churchpresenter.composeapp.generated.resources.customize_group_margins
import churchpresenter.composeapp.generated.resources.customize_motion
import churchpresenter.composeapp.generated.resources.fade_in
import churchpresenter.composeapp.generated.resources.fade_out
import churchpresenter.composeapp.generated.resources.left
import churchpresenter.composeapp.generated.resources.right
import churchpresenter.composeapp.generated.resources.top
import churchpresenter.composeapp.generated.resources.transition_duration
import org.churchpresenter.settings.ContentRegion
import org.jetbrains.compose.resources.stringResource

/**
 * The rows the category strips are built from: margins, motion, the content region, and the caption
 * frame they all share.
 *
 * Split out of `CustomizeCategoryStrip.kt` when that file outgrew detekt's per-file function count.
 * These are the generic ones -- every category draws them the same way; the category-specific rows
 * stay beside the strip that uses them.
 */

/**
 * Width and X/Y offset for the whole content block -- the Customize dialog's own view of
 * [ContentRegionSection] on the global tabs, one field per output rather than one for the install.
 */
@Composable
internal fun ContentRegionStripRow(region: ContentRegion, onChange: (ContentRegion) -> Unit) {
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
internal fun MarginsStripRow(
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
internal fun MotionStripRow(
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
internal fun StripRow(label: String, content: @Composable () -> Unit) {
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
