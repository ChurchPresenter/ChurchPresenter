package org.churchpresenter.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_close_sheet
import org.jetbrains.compose.resources.stringResource

/**
 * The window's dialog shell, and the small parts its dialogs are assembled from.
 *
 * One file because the three dialogs have to agree: the design draws them all with the same header
 * (an icon badge, a title and a subtitle, a square close button), the same row card, the same tab
 * strip and the same footer rule. Building each dialog out of whatever Material 3 offered is what
 * made them drift apart in the first place. Their sizes are [SheetMetrics].
 *
 * A dialog laid out the way the design draws them: header, optional tab strip, scrolling body,
 * footer.
 *
 * The body is given as a [ColumnScope] block rather than a list so each dialog keeps its own
 * content; everything around it is fixed here so it cannot vary between them.
 */
@Composable
fun SheetScaffold(
    title: String,
    onDismiss: () -> Unit,
    width: Dp,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tabs: (@Composable RowScope.() -> Unit)? = null,
    footer: (@Composable RowScope.() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = SheetMetrics.radius,
        color = scheme.surface,
        tonalElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant),
        modifier = modifier.width(width),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
            ) {
                if (icon != null) {
                    Box(
                        Modifier
                            .size(SheetMetrics.headerIcon)
                            .clip(RoundedCornerShape(8.dp))
                            .background(scheme.primary.copy(alpha = ICON_TINT)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(15.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.5.sp),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Box(
                    Modifier
                        .size(SheetMetrics.closeButton)
                        .clip(RoundedCornerShape(7.dp))
                        .background(scheme.surfaceVariant.copy(alpha = BUTTON_TINT))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(Res.string.calendar_close_sheet),
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            HorizontalDivider()

            if (tabs != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 9.dp),
                    content = tabs,
                )
                HorizontalDivider()
            }

            Column(content = body)

            if (footer != null) {
                HorizontalDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 11.dp),
                    content = footer,
                )
            }
        }
    }
}

/** A tab in a sheet's strip — filled when selected, with no border either way. */
@Composable
fun SheetTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .height(SheetMetrics.tabHeight)
            .clip(RoundedCornerShape(7.dp))
            .background(
                if (selected) scheme.primary.copy(alpha = TAB_TINT) else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
        )
    }
}

/** The bordered row card every settings list is made of. */
@Composable
fun SettingCard(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 11.dp,
    verticalPadding: Dp = 9.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(SheetMetrics.cardRadius)
            .background(scheme.surfaceVariant.copy(alpha = CARD_TINT))
            .border(1.dp, scheme.outlineVariant.copy(alpha = CARD_BORDER), SheetMetrics.cardRadius)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        content = content,
    )
}

/** A card's two lines of text — 12sp title over a 10sp explanation. */
@Composable
fun RowScope.CardText(title: String, subtitle: String?) {
    Column(Modifier.weight(1f)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // An explanation is allowed a second line; the title stays on one.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The uppercase micro-heading above a list inside a sheet. */
@Composable
fun SheetOverline(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 9.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.85.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        modifier = modifier,
    )
}

/** A small square action inside a row — edit, delete. */
@Composable
fun SmallIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
    size: Dp = SheetMetrics.smallButton,
) {
    val scheme = MaterialTheme.colorScheme
    Hint(description) {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (destructive) scheme.error.copy(alpha = DESTRUCTIVE_TINT)
                    else scheme.surfaceVariant.copy(alpha = BUTTON_TINT)
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = description,
                tint = if (destructive) scheme.error else scheme.onSurfaceVariant,
                modifier = Modifier.size(size * ICON_RATIO),
            )
        }
    }
}

/** The dashed full-width control the design ends a settings list with. */
@Composable
fun DashedAddButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(SheetMetrics.cardRadius)
            .border(1.dp, scheme.primary.copy(alpha = DASHED_BORDER), SheetMetrics.cardRadius)
            .clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(13.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
            fontWeight = FontWeight.Bold,
            color = scheme.primary,
        )
    }
}

/** A quiet bordered action — Cancel, Insert, Browse. */
@Composable
fun QuietButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = SheetMetrics.doneHeight,
    accent: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (accent) {
                    scheme.primary.copy(alpha = ACCENT_TINT)
                } else {
                    scheme.surfaceVariant.copy(alpha = BUTTON_TINT)
                }
            )
            .border(
                width = 1.dp,
                color = if (accent) scheme.primary.copy(alpha = ACCENT_BORDER) else scheme.outlineVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            fontWeight = FontWeight.Bold,
            color = if (accent) scheme.primary else scheme.onSurfaceVariant,
            // A control's label never wraps: if it cannot fit, the layout around it is wrong and
            // should be fixed there rather than hidden by a two-line button.
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** The filled accent button a sheet's footer commits with. */
@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .height(SheetMetrics.doneHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) scheme.primary else scheme.primary.copy(alpha = DISABLED))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 17.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            fontWeight = FontWeight.Bold,
            color = scheme.onPrimary.copy(alpha = if (enabled) 1f else DISABLED),
            maxLines = 1,
            softWrap = false,
        )
    }
}

private const val ICON_TINT = 0.16f
private const val BUTTON_TINT = 0.5f

/** The outline of an accented quiet button -- the primary color, half strength. */
private const val ACCENT_BORDER = 0.5f
private const val TAB_TINT = 0.16f
private const val CARD_TINT = 0.4f
private const val CARD_BORDER = 0.6f
private const val DESTRUCTIVE_TINT = 0.14f
private const val DASHED_BORDER = 0.45f
private const val ACCENT_TINT = 0.14f
private const val ICON_RATIO = 0.5f
private const val DISABLED = 0.38f
