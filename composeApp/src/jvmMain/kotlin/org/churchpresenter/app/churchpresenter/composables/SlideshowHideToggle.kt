package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.hide_from_slideshow
import churchpresenter.composeapp.generated.resources.show_in_slideshow
import churchpresenter.composeapp.generated.resources.slideshow_hidden
import org.churchpresenter.theme.components.KeyIconButton
import org.jetbrains.compose.resources.stringResource

/** Test handle for a tile's hide/show button: this plus the tile's position. */
internal const val SLIDESHOW_HIDE_TOGGLE_TAG = "slideshow_hide_toggle_"

/** How faint a hidden picture or slide's thumbnail is drawn. */
internal const val HIDDEN_TILE_ALPHA = 0.3f

/**
 * The label over a hidden tile's thumbnail, so a skipped picture or slide reads as hidden at a
 * glance rather than only as a faint one.
 */
@Composable
fun HiddenBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.Filled.VisibilityOff,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            stringResource(Res.string.slideshow_hidden),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/**
 * The eye on a picture or slide tile (#676): open while it is part of the slideshow, crossed out
 * while it is hidden and skipped by Next, Previous and auto-advance.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SlideshowHideToggle(
    hidden: Boolean,
    position: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(if (hidden) Res.string.show_in_slideshow else Res.string.hide_from_slideshow)
    TooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = 4.dp,
            ) {
                Text(
                    label,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.BottomCenter,
            offset = DpOffset(0.dp, 4.dp),
        ),
        modifier = modifier,
    ) {
        KeyIconButton(
            onClick = onToggle,
            modifier = Modifier.size(20.dp).testTag(SLIDESHOW_HIDE_TOGGLE_TAG + position),
        ) {
            Icon(
                if (hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = label,
                modifier = Modifier.size(14.dp),
                tint = if (hidden) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
