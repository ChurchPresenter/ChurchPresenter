package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.RaisedSwitch

private val CARD_RADIUS = 10.dp
private val GROUP_GAP = 16.dp
private val CAPTION_GAP = 6.dp
private val ROW_MIN_HEIGHT = 46.dp
private val ROW_PADDING_H = 14.dp
private val ROW_PADDING_V = 6.dp
private val LABEL_MIN_WIDTH = 150.dp
private val LABEL_CONTROL_GAP = 12.dp
private val WRAP_GAP = 6.dp

/**
 * One titled card of settings: an uppercase caption with an optional action at its end (a reset
 * link), then a white card whose rows are separated by hairlines.
 *
 * [header] and [footer] are bands inside the card, above and below the rows -- the "Applies to"
 * strip, the "Comes from" strip -- and are drawn only while the card has a row to show: a group
 * whose every row is advanced, or filtered out by the search, disappears whole, caption and all,
 * rather than leaving an empty card behind.
 *
 * The rows are a column on the divider colour with a hairline gap between them, each row painting
 * itself in the card colour ([SettingsRow]), which is what draws the dividers without a row having
 * to know whether it is first.
 */
@Composable
internal fun SettingsGroup(
    caption: String,
    modifier: Modifier = Modifier,
    advanced: Boolean = false,
    action: (@Composable RowScope.() -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (advanced && LocalSettingsDetail.current == SettingsDetail.BASIC) return
    val palette = profilesPalette()
    val radius = CARD_RADIUS
    val topRounded = if (header == null) radius else 0.dp
    val bottomRounded = if (footer == null) radius else 0.dp
    val card = remember { CardBounds() }
    Layout(
        contents = listOf(
            {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GroupCaption(caption, Modifier.weight(1f))
                    action?.invoke(this)
                }
            },
            { header?.let { Box(Modifier.fillMaxWidth().clip(AppShape(radius, radius, 0.dp, 0.dp))) { it() } } },
            {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppShape(topRounded, topRounded, bottomRounded, bottomRounded))
                        .background(palette.rowDivider),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    content = content,
                )
            },
            { footer?.let { Box(Modifier.fillMaxWidth().clip(AppShape(0.dp, 0.dp, radius, radius))) { it() } } },
        ),
        modifier = modifier.fillMaxWidth().drawBehind {
            if (card.height <= 0f) return@drawBehind
            // The card's own outline, from the same AppShape the clips above use, so the drawn
            // corner and the clipped rows round off together.
            val outline = AppShape(radius).createOutline(Size(size.width, card.height), layoutDirection, this)
            translate(top = card.top) {
                drawOutline(outline, palette.card)
                drawOutline(outline, palette.cardBorder, style = Stroke(1.dp.toPx()))
            }
        },
    ) { measurables, constraints ->
        val (captionM, headerM, rowsM) = measurables
        val footerM = measurables[FOOTER_SLOT]
        val width = constraints.maxWidth
        val loose = Constraints(minWidth = width, maxWidth = width)
        val rows = rowsM.firstOrNull()?.measure(loose)
        if (rows == null || rows.height == 0) {
            card.height = 0f
            return@Layout layout(width, 0) {}
        }
        val captionP = captionM.firstOrNull()?.measure(loose)
        val headerP = headerM.firstOrNull()?.measure(loose)
        val footerP = footerM.firstOrNull()?.measure(loose)
        val gap = GROUP_GAP.roundToPx()
        val captionGap = CAPTION_GAP.roundToPx()
        val cardTop = gap + (captionP?.height ?: 0) + captionGap
        val cardHeight = (headerP?.height ?: 0) + rows.height + (footerP?.height ?: 0)
        card.top = cardTop.toFloat()
        card.height = cardHeight.toFloat()
        layout(width, cardTop + cardHeight) {
            captionP?.place(0, gap)
            var y = cardTop
            headerP?.let { it.place(0, y); y += it.height }
            rows.place(0, y)
            y += rows.height
            footerP?.place(0, y)
        }
    }
}

/** Where the card sits inside the group, written by the layout and read when it is drawn. */
/** Where the footer's content is among the group's four slots. */
private const val FOOTER_SLOT = 3

private class CardBounds {
    var top = 0f
    var height = 0f
}

/** The group's caption: small, bold, tracked out, upper case. */
@Composable
internal fun GroupCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.tertiary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** A small link at the end of a group caption -- "Reset to defaults", "Revert to Sanctuary". */
@Composable
internal fun GroupCaptionAction(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector = Icons.Filled.RestartAlt,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(AppShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = profilesPalette().faintText)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/**
 * One setting: its label and an optional sub-line on the left, its control on the right.
 *
 * The control keeps its natural width and the label takes what is left; when that would leave the
 * label narrower than it can be read, the control drops onto a line of its own under the label,
 * aligned to the end, instead of either one being clipped.
 *
 * [advanced] rows are left out in Basic, and a row the search does not match is left out
 * altogether -- by returning before emitting anything, which is how [SettingsGroup] learns its
 * card is empty. [searchTerms] are extra words the search should find the row by.
 *
 * [leading] sits before the label (Phase 4's override dot), and [decorate] wraps the control --
 * both for a linked profile to mark where a value comes from without every row knowing why.
 */
@Composable
internal fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    advanced: Boolean = false,
    searchTerms: String? = null,
    leading: (@Composable () -> Unit)? = null,
    control: @Composable RowScope.() -> Unit,
) {
    if (advanced && LocalSettingsDetail.current == SettingsDetail.BASIC) return
    if (!matchesSettingsQuery(label, sub, searchTerms)) return
    val palette = profilesPalette()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.card)
            .heightIn(min = ROW_MIN_HEIGHT)
            .padding(horizontal = ROW_PADDING_H, vertical = ROW_PADDING_V),
        contentAlignment = Alignment.CenterStart,
    ) {
        LabelAndControl(
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (leading != null) {
                        leading()
                        Spacer(Modifier.size(7.dp))
                    }
                    Column {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (!sub.isNullOrBlank()) {
                            Text(
                                text = sub,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = palette.faintText,
                            )
                        }
                    }
                }
            },
            control = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = control,
                )
            },
        )
    }
}

/**
 * A setting that is only on or off: the whole row toggles it, label included, and the switch at its
 * end shows which -- so the words are as much a target as the switch, as a labelled checkbox's are.
 */
@Composable
internal fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
    advanced: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    /** Controls before the switch that belong to it while it is on -- auto-fit's scope. */
    extra: @Composable RowScope.() -> Unit = {},
) {
    SettingsRow(
        label = label,
        sub = sub,
        advanced = advanced,
        leading = leading,
        modifier = modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        extra()
        RaisedSwitch(checked = checked, onCheckedChange = null)
    }
}

/**
 * A row with no label column at all -- a note, a list, a control as wide as the card.
 * Left out under the same rules as [SettingsRow].
 */
@Composable
internal fun SettingsWideRow(
    modifier: Modifier = Modifier,
    advanced: Boolean = false,
    searchTerms: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (advanced && LocalSettingsDetail.current == SettingsDetail.BASIC) return
    if (!matchesSettingsQuery(searchTerms)) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(profilesPalette().card)
            .heightIn(min = ROW_MIN_HEIGHT)
            .padding(horizontal = ROW_PADDING_H, vertical = ROW_PADDING_V + 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** Label left, control right; the control wraps under the label when both will not fit. */
@Composable
private fun LabelAndControl(label: @Composable () -> Unit, control: @Composable () -> Unit) {
    Layout(contents = listOf(label, control)) { (labelM, controlM), constraints ->
        val width = constraints.maxWidth
        val gap = LABEL_CONTROL_GAP.roundToPx()
        val controlP = controlM.first().measure(Constraints(maxWidth = width))
        val side = width - controlP.width - gap >= LABEL_MIN_WIDTH.roundToPx()
        if (side) {
            val labelP = labelM.first().measure(Constraints(maxWidth = width - controlP.width - gap))
            val height = maxOf(labelP.height, controlP.height)
            layout(width, height) {
                labelP.place(0, (height - labelP.height) / 2)
                controlP.place(width - controlP.width, (height - controlP.height) / 2)
            }
        } else {
            val labelP = labelM.first().measure(Constraints(maxWidth = width))
            val wrapGap = WRAP_GAP.roundToPx()
            layout(width, labelP.height + wrapGap + controlP.height) {
                labelP.place(0, 0)
                controlP.place((width - controlP.width).coerceAtLeast(0), labelP.height + wrapGap)
            }
        }
    }
}
