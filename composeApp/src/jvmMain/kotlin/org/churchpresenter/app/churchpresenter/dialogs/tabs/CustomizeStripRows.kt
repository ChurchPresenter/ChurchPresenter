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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.profile_song_position
import churchpresenter.composeapp.generated.resources.element_offset_x
import churchpresenter.composeapp.generated.resources.element_offset_y
import org.churchpresenter.settings.ElementOffset
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
 * Positions one element in the frame: a switch that takes it out of the flow, then where it goes.
 *
 * Off is the default and means "laid out as it always was", which is the only honest default --
 * see [ElementOffset]. On, the sliders read 0 flush to the start, 50 centred, 100 flush to the end,
 * and the element cannot leave the frame at any value, which is why there is nothing to warn about.
 *
 * [verticalOnly] for an element whose content fills the width and so has no horizontal room to move
 * through -- the lyrics block. Offering a dead slider there would be worse than offering none.
 */
@Composable
internal fun ElementOffsetStripRow(
    label: String,
    offset: ElementOffset?,
    verticalOnly: Boolean = false,
    tagPrefix: String,
    onChange: (ElementOffset?) -> Unit,
) {
    StripRow(label) {
        ToggleControl(
            label = stringResource(Res.string.profile_song_position),
            checked = offset != null,
            onCheckedChange = { on -> onChange(if (on) ElementOffset() else null) },
            modifier = Modifier.testTag("${tagPrefix}_enabled"),
        )
        if (offset != null) {
            if (!verticalOnly) {
                NumberControl(
                    label = stringResource(Res.string.element_offset_x),
                    value = offset.xPercent,
                    onValueChange = { v -> onChange(offset.copy(xPercent = v)) },
                    range = ElementOffset.PERCENT_RANGE,
                    width = MARGIN_FIELD_WIDTH,
                )
            }
            NumberControl(
                label = stringResource(Res.string.element_offset_y),
                value = offset.yPercent,
                onValueChange = { v -> onChange(offset.copy(yPercent = v)) },
                range = ElementOffset.PERCENT_RANGE,
                width = MARGIN_FIELD_WIDTH,
            )
        }
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
