package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_arrow_down
import churchpresenter.composeapp.generated.resources.ic_arrow_right
import org.jetbrains.compose.resources.painterResource

@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    /** Trailing content for the header row, drawn at its right edge. */
    headerTrailing: (@Composable () -> Unit)? = null,
    /**
     * Lets the header collapse its body away.
     *
     * For panels that repeat a long block per item — a settings page with one section per Bible
     * translation is unreadable at four translations if all of them are open at once. Sections are
     * fixed open by default, which is every existing use.
     */
    collapsible: Boolean = false,
    expanded: Boolean = true,
    onExpandedChange: (Boolean) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .then(
                    if (collapsible) Modifier.clickable { onExpandedChange(!expanded) } else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
            )
            if (collapsible) {
                Icon(
                    painter =
                        painterResource(if (expanded) Res.drawable.ic_arrow_down else Res.drawable.ic_arrow_right),
                    contentDescription = null,
                    modifier = Modifier.padding(start = 6.dp).size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 9.dp)
            )
            if (headerTrailing != null) {
                Spacer(Modifier.weight(1f))
                Box(modifier = Modifier.padding(end = 6.dp)) { headerTrailing() }
            }
        }
        if (!collapsible || expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                content = content
            )
        }
    }
}

/**
 * Drop needed to sit a top-aligned label on the centre line of the first control beside it: half the
 * difference between the shared 42.dp field height ([DropdownSettingsField], [ColorPickerField]) and
 * the 20.dp line box of `bodyMedium`.
 */
val SettingRowFirstControlOffset: Dp = (42.dp - 20.dp) / 2

@Composable
fun SettingRow(
    label: String,
    width: Dp = 120.dp,
    /** Centred suits the usual single control. Pass [Alignment.Top] when [content] is a tall stack,
     *  where a centred label floats halfway down the block instead of naming its first row. */
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    /** With [Alignment.Top], nudges the label down onto the first control's centre line rather than
     *  leaving it flush with the top edge. [SettingRowFirstControlOffset] fits a standard field. */
    labelTopPadding: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = verticalAlignment
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(width).padding(top = labelTopPadding)
        )
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}
