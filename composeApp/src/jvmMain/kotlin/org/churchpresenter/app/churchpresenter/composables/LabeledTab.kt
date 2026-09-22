package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabIndicatorScope
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.TabLabelMargin
import org.churchpresenter.settings.TabLabelStyle

private val TAB_ICON_SIZE = 20.dp

val LABELED_TAB_MIN_WIDTH = 100.dp
private val LABELED_TAB_MIN_WIDTH_SMALL = 64.dp
private val LABELED_TAB_MIN_WIDTH_SMALL_NORMAL = 82.dp
private val LABELED_TAB_MIN_WIDTH_NORMAL_LARGE = 114.dp
private val LABELED_TAB_MIN_WIDTH_LARGE = 128.dp

/**
 * The narrowest a tab in a row of [style] may be, at [margin].
 *
 * Only the icon-only tabs need a floor, to stay a comfortable target. A named tab is as wide as its
 * name asks: a floor there padded short names like Bible and Songs out to the width of the longest,
 * which is what pushed the last tabs of the strip behind the scroll arrow.
 */
fun labeledTabMinWidth(style: TabLabelStyle, margin: TabLabelMargin = TabLabelMargin.NORMAL): Dp =
    if (style != TabLabelStyle.ICONS) 0.dp
    else when (margin) {
        TabLabelMargin.SMALL -> LABELED_TAB_MIN_WIDTH_SMALL
        TabLabelMargin.SMALL_NORMAL -> LABELED_TAB_MIN_WIDTH_SMALL_NORMAL
        TabLabelMargin.NORMAL -> LABELED_TAB_MIN_WIDTH
        TabLabelMargin.NORMAL_LARGE -> LABELED_TAB_MIN_WIDTH_NORMAL_LARGE
        TabLabelMargin.LARGE -> LABELED_TAB_MIN_WIDTH_LARGE
    }

/** Space either side of a named tab's content: Material pads 16dp, too much for a strip of a dozen. */
private val NAMED_TAB_HORIZONTAL_PADDING = 6.dp
private val NAMED_TAB_HORIZONTAL_PADDING_SMALL = 2.dp
private val NAMED_TAB_HORIZONTAL_PADDING_SMALL_NORMAL = 4.dp
private val NAMED_TAB_HORIZONTAL_PADDING_NORMAL_LARGE = 9.dp
private val NAMED_TAB_HORIZONTAL_PADDING_LARGE = 12.dp

private fun namedTabHorizontalPadding(margin: TabLabelMargin): Dp = when (margin) {
    TabLabelMargin.SMALL -> NAMED_TAB_HORIZONTAL_PADDING_SMALL
    TabLabelMargin.SMALL_NORMAL -> NAMED_TAB_HORIZONTAL_PADDING_SMALL_NORMAL
    TabLabelMargin.NORMAL -> NAMED_TAB_HORIZONTAL_PADDING
    TabLabelMargin.NORMAL_LARGE -> NAMED_TAB_HORIZONTAL_PADDING_NORMAL_LARGE
    TabLabelMargin.LARGE -> NAMED_TAB_HORIZONTAL_PADDING_LARGE
}
private val NAMED_TAB_HEIGHT = 48.dp

@Composable
fun TabIndicatorScope.LabeledTabIndicator(selectedTabIndex: Int) {
    TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(selectedTabIndex), width = Dp.Unspecified)
}

@Composable
fun LabeledTab(
    name: String,
    icon: ImageVector,
    selected: Boolean,
    labelStyle: TabLabelStyle,
    onClick: () -> Unit,
    textStyle: TextStyle? = null,
    color: Color = Color.Unspecified,
    labelMargin: TabLabelMargin = TabLabelMargin.NORMAL,
) {
    when (labelStyle) {
        TabLabelStyle.TEXT -> NamedTab(selected, onClick, labelMargin) { TabName(name, textStyle, color) }
        TabLabelStyle.ICONS_AND_TEXT -> NamedTab(selected, onClick, labelMargin) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabIcon(icon, color)
                TabName(name, textStyle, color)
            }
        }
        TabLabelStyle.ICONS -> TabNameTooltip(name) {
            Tab(
                selected = selected,
                onClick = onClick,
                modifier = Modifier.widthIn(min = labeledTabMinWidth(labelStyle, labelMargin)),
                icon = { TabIcon(icon, color, contentDescription = name) },
            )
        }
    }
}

@Composable
private fun NamedTab(
    selected: Boolean,
    onClick: () -> Unit,
    margin: TabLabelMargin,
    content: @Composable () -> Unit,
) {
    Tab(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.height(NAMED_TAB_HEIGHT),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = namedTabHorizontalPadding(margin)),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

@Composable
private fun TabName(name: String, textStyle: TextStyle?, color: Color) {
    if (textStyle != null) {
        Text(text = name, style = textStyle, color = color, maxLines = 1, softWrap = false)
    } else {
        Text(text = name, color = color, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun TabIcon(icon: ImageVector, color: Color, contentDescription: String? = null) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = color.takeOrElse { LocalContentColor.current },
        modifier = Modifier.size(TAB_ICON_SIZE),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabNameTooltip(name: String, content: @Composable () -> Unit) {
    ConditionalTooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = 4.dp
            ) {
                Text(
                    text = name,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        },
        content = content,
    )
}
