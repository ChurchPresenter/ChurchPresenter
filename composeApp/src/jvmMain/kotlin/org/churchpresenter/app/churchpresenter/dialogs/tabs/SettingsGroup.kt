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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.profile_group_fold
import org.churchpresenter.strings.generated.resources.profile_group_open
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.RaisedSwitch
import org.jetbrains.compose.resources.stringResource

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
 *
 * Where [LocalFoldedGroups] is provided, the caption folds the group away: it becomes a card of its
 * own with [summary] at its end, and the amber dot when a linked profile has made one of [paths] its
 * own. [key] names the group among its page's for that, and so must not change. A folded group's
 * rows are still composed and measured -- only not placed -- so one with nothing to show is still
 * left out whole; and while a search is typed, a group with a match opens without its fold being
 * forgotten. A group without a caption never folds.
 */
@Composable
internal fun SettingsGroup(
    caption: String,
    key: String,
    modifier: Modifier = Modifier,
    advanced: Boolean = false,
    action: (@Composable RowScope.() -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    /** The settings the group edits: on a linked profile its caption offers to revert them instead. */
    paths: List<String> = emptyList(),
    /** What a folded group says at its end -- "Bottom · margins 40". */
    summary: (@Composable () -> String)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (advanced && LocalSettingsDetail.current == SettingsDetail.BASIC) return
    val palette = profilesPalette()
    val link = LocalProfileLink.current
    val shownAction = linkedGroupAction(link, paths, action)
    val folds = LocalFoldedGroups.current?.takeIf { caption.isNotEmpty() }
    if (folds != null) {
        // Keyed on the page's set, which outlives the holder rebuilt on every fold.
        val present = folds.present
        DisposableEffect(present, key) {
            present += key
            onDispose { present -= key }
        }
    }
    val folded = folds != null && key in folds.folded && LocalSettingsQuery.current.isBlank()
    val radius = CARD_RADIUS
    val topRounded = if (header == null) radius else 0.dp
    val bottomRounded = if (footer == null) radius else 0.dp
    val card = remember { CardBounds() }
    Layout(
        contents = listOf(
            {
                when {
                    folds == null -> Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GroupCaption(caption, Modifier.weight(1f))
                        shownAction?.invoke(this)
                    }
                    folded -> FoldableCaption(caption, folded = true, onToggle = { folds.toggle(key) }, key = key) {
                        if (link?.follows(paths) == true && link.owns(paths)) OverrideDot()
                        summary?.let { GroupSummary(it()) }
                    }
                    else -> FoldableCaption(caption, folded = false, onToggle = { folds.toggle(key) }, key = key) {
                        shownAction?.invoke(this)
                    }
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
        if (folded && captionP != null) {
            // Only the caption, as a card of its own: the rows stay composed, unplaced.
            val gap = GROUP_GAP.roundToPx()
            card.top = gap.toFloat()
            card.height = captionP.height.toFloat()
            return@Layout layout(width, gap + captionP.height) { captionP.place(0, gap) }
        }
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

/** Where the footer's content is among the group's four slots. */
private const val FOOTER_SLOT = 3

/**
 * A caption that folds its group: the whole line is the button, a chevron at its start saying which
 * way it goes. Folded, it is padded into a card of its own; [end] follows the caption either way --
 * the summary while folded, the caption's action while open.
 */
@Composable
private fun FoldableCaption(
    caption: String,
    folded: Boolean,
    onToggle: () -> Unit,
    key: String,
    end: @Composable RowScope.() -> Unit,
) {
    val padding = if (folded) Modifier.padding(horizontal = 12.dp, vertical = 11.dp) else Modifier.padding(4.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShape(CARD_RADIUS))
            .clickable(role = Role.Button, onClick = onToggle)
            .testTag(groupFoldTag(key))
            .then(padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            if (folded) Icons.Filled.ChevronRight else Icons.Filled.KeyboardArrowDown,
            contentDescription = stringResource(
                if (folded) Res.string.profile_group_open else Res.string.profile_group_fold,
            ),
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GroupCaption(caption, Modifier.weight(1f))
        end()
    }
}

/** A folded group's one-line summary, at the end of its caption. */
@Composable
private fun GroupSummary(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Where the card sits inside the group, written by the layout and read when it is drawn. */

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
 * [paths] are the stored settings the row edits. On a linked profile they decide how it is marked:
 * an amber dot, the master's value and Revert when the profile has made one of them its own, a
 * dashed frame when it takes them from its master -- and, under Only changes, whether it is drawn.
 */
@Composable
internal fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    advanced: Boolean = false,
    searchTerms: String? = null,
    leading: (@Composable () -> Unit)? = null,
    paths: List<String> = emptyList(),
    control: @Composable RowScope.() -> Unit,
) {
    if (advanced && LocalSettingsDetail.current == SettingsDetail.BASIC) return
    if (!matchesSettingsQuery(label, sub, searchTerms)) return
    val link = LocalProfileLink.current?.takeIf { it.isLinked }
    val own = link?.owns(paths) == true
    if (link != null && link.onlyChanges && !own) return
    val shownLeading: (@Composable () -> Unit)? = if (own) {
        { OverrideDot() }
    } else {
        leading
    }
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
                    if (shownLeading != null) {
                        shownLeading()
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
                ) {
                    TargetChip(paths)
                    LinkedControl(link, paths, control)
                }
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
    paths: List<String> = emptyList(),
    /** Controls before the switch that belong to it while it is on -- auto-fit's scope. */
    extra: @Composable RowScope.() -> Unit = {},
) {
    SettingsRow(
        label = label,
        sub = sub,
        advanced = advanced,
        leading = leading,
        paths = paths,
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
