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
import org.churchpresenter.settings.TabLabelStyle

private val TAB_ICON_SIZE = 20.dp

val LABELED_TAB_MIN_WIDTH = 100.dp

/**
 * The narrowest a tab in a row of [style] may be.
 *
 * Only the icon-only tabs need a floor, to stay a comfortable target. A named tab is as wide as its
 * name asks: a floor there padded short names like Bible and Songs out to the width of the longest,
 * which is what pushed the last tabs of the strip behind the scroll arrow.
 */
fun labeledTabMinWidth(style: TabLabelStyle): Dp =
    if (style == TabLabelStyle.ICONS) LABELED_TAB_MIN_WIDTH else 0.dp

/** Space either side of a named tab's content: Material pads 16dp, too much for a strip of a dozen. */
private val NAMED_TAB_HORIZONTAL_PADDING = 10.dp
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
) {
    when (labelStyle) {
        TabLabelStyle.TEXT -> NamedTab(selected, onClick) { TabName(name, textStyle, color) }
        TabLabelStyle.ICONS_AND_TEXT -> NamedTab(selected, onClick) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                modifier = Modifier.widthIn(min = LABELED_TAB_MIN_WIDTH),
                icon = { TabIcon(icon, color, contentDescription = name) },
            )
        }
    }
}

@Composable
private fun NamedTab(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Tab(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.height(NAMED_TAB_HEIGHT),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = NAMED_TAB_HORIZONTAL_PADDING),
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
