package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.KeyButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.tab_label_margin_hint
import churchpresenter.composeapp.generated.resources.tab_label_margin_large
import churchpresenter.composeapp.generated.resources.tab_label_margin_normal
import churchpresenter.composeapp.generated.resources.tab_label_margin_normal_large
import churchpresenter.composeapp.generated.resources.tab_label_margin_small
import churchpresenter.composeapp.generated.resources.tab_label_margin_small_normal
import churchpresenter.composeapp.generated.resources.tab_label_style
import churchpresenter.composeapp.generated.resources.tab_label_style_button_hint
import churchpresenter.composeapp.generated.resources.tab_label_style_hint
import churchpresenter.composeapp.generated.resources.tab_label_style_icons
import churchpresenter.composeapp.generated.resources.tab_label_style_icons_and_text
import churchpresenter.composeapp.generated.resources.tab_label_style_text
import org.churchpresenter.app.churchpresenter.composables.ConditionalTooltipArea
import org.churchpresenter.settings.TabLabelMargin
import org.churchpresenter.settings.TabLabelStyle
import org.jetbrains.compose.resources.stringResource

@Composable
private fun tabLabelStyleName(style: TabLabelStyle): String = when (style) {
    TabLabelStyle.TEXT -> stringResource(Res.string.tab_label_style_text)
    TabLabelStyle.ICONS_AND_TEXT -> stringResource(Res.string.tab_label_style_icons_and_text)
    TabLabelStyle.ICONS -> stringResource(Res.string.tab_label_style_icons)
}

@Composable
private fun tabLabelMarginName(margin: TabLabelMargin): String = when (margin) {
    TabLabelMargin.SMALL -> stringResource(Res.string.tab_label_margin_small)
    TabLabelMargin.SMALL_NORMAL -> stringResource(Res.string.tab_label_margin_small_normal)
    TabLabelMargin.NORMAL -> stringResource(Res.string.tab_label_margin_normal)
    TabLabelMargin.NORMAL_LARGE -> stringResource(Res.string.tab_label_margin_normal_large)
    TabLabelMargin.LARGE -> stringResource(Res.string.tab_label_margin_large)
}

private fun tabLabelStyleIcon(style: TabLabelStyle): ImageVector = when (style) {
    TabLabelStyle.TEXT -> Icons.Filled.TextFields
    TabLabelStyle.ICONS_AND_TEXT -> Icons.Filled.Label
    TabLabelStyle.ICONS -> Icons.Filled.Category
}

/**
 * How the tab strips are labelled: two buttons stacked on the right, each showing its current choice
 * and moving to the next one on every press -- the label style above, the spacing below.
 */
@Composable
internal fun TabLabelsRow(
    style: TabLabelStyle,
    margin: TabLabelMargin,
    onStyleChange: (TabLabelStyle) -> Unit,
    onMarginChange: (TabLabelMargin) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.tab_label_style),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(Res.string.tab_label_style_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        // Max intrinsic width, so both buttons take the wider one's width and line up as a pair.
        Column(
            modifier = Modifier.width(IntrinsicSize.Max),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CycleButton(
                icon = tabLabelStyleIcon(style),
                label = tabLabelStyleName(style),
                tooltip = stringResource(Res.string.tab_label_style_button_hint),
                onClick = { onStyleChange(style.next()) },
                modifier = Modifier.testTag("tab_label_style_button"),
            )
            CycleButton(
                icon = Icons.Filled.SpaceBar,
                label = tabLabelMarginName(margin),
                tooltip = stringResource(Res.string.tab_label_margin_hint),
                onClick = { onMarginChange(margin.next()) },
                modifier = Modifier.testTag("tab_spacing_button"),
            )
        }
    }
}

/** One of [TabLabelsRow]'s buttons: the current choice, with [tooltip] saying what a press does. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CycleButton(
    icon: ImageVector,
    label: String,
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConditionalTooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = 4.dp
            ) {
                Text(
                    text = tooltip,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        KeyButton(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().height(34.dp),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Spacer(Modifier.weight(1f))
        }
    }
}
