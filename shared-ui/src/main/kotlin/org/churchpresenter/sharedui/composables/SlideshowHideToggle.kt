package org.churchpresenter.sharedui.composables

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.hide_from_slideshow
import org.churchpresenter.strings.generated.resources.show_in_slideshow
import org.churchpresenter.strings.generated.resources.slideshow_hidden
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.hosanna.composables.button.IconToggleButton
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ButtonIntent
import org.churchpresenter.hosanna.composables.model.IconButtonSize
import org.churchpresenter.hosanna.composables.feedback.Tooltip
import androidx.compose.ui.graphics.vector.rememberVectorPainter

/** Test handle for a tile's hide/show button: this plus the tile's position. */
const val SLIDESHOW_HIDE_TOGGLE_TAG = "slideshow_hide_toggle_"

/** How faint a hidden picture or slide's thumbnail is drawn. */
const val HIDDEN_TILE_ALPHA = 0.3f

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
@Composable
fun SlideshowHideToggle(
    hidden: Boolean,
    position: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(if (hidden) Res.string.show_in_slideshow else Res.string.hide_from_slideshow)
    Tooltip(text = label, modifier = modifier) {
        IconToggleButton(
            icon = rememberVectorPainter(if (hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility),
            contentDescription = label,
            checked = hidden,
            onCheckedChange = { onToggle() },
            modifier = Modifier.testTag(SLIDESHOW_HIDE_TOGGLE_TAG + position),
            size = IconButtonSize.Small,
            hierarchy = ButtonHierarchy.Tertiary,
            intent = if (hidden) ButtonIntent.Critical else ButtonIntent.Neutral,
        )
    }
}
