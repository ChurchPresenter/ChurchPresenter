package org.churchpresenter.sharedui.composables

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.churchpresenter.hosanna.composables.button.IconButton
import org.churchpresenter.hosanna.composables.feedback.Tooltip
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ButtonIntent
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_playlist_add
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.utils.LocalShortcuts
import org.churchpresenter.sharedui.utils.label
import org.jetbrains.compose.resources.painterResource

private const val DIMMED_ALPHA = 0.5f

/**
 * Uniform "Go Live" button.
 *
 * [showsShortcut] adds the Go Live key to the tooltip on the tabs that answer it; it follows a
 * rebinding and drops out when the action is unbound.
 */
@Composable
fun GoLiveButton(
    onClick: () -> Unit,
    tooltipText: String,
    enabled: Boolean = true,
    dimmed: Boolean = false,
    showsShortcut: Boolean = false,
    modifier: Modifier = Modifier
) {
    val key = if (showsShortcut) LocalShortcuts.current.label(ShortcutAction.GO_LIVE).ifEmpty { null } else null
    Tooltip(
        text = tooltipText,
        modifier = if (dimmed) modifier.alpha(DIMMED_ALPHA) else modifier,
        shortcut = key,
    ) {
        IconButton(
            icon = rememberVectorPainter(Icons.Default.Tv),
            contentDescription = tooltipText,
            onClick = onClick,
            hierarchy = ButtonHierarchy.Primary,
            intent = ButtonIntent.Default,
            enabled = enabled,
        )
    }
}

/**
 * **Save preset** — sits to the left of [AddToScheduleButton] on the tabs whose item can be saved
 * for later: pictures, presentation, media, announcements and canvas. What it saves is the same
 * item Add to Schedule would add, kept in `presets.json` for the Calendar Manager to pick up.
 */
@Composable
fun SavePresetButton(
    onClick: () -> Unit,
    tooltipText: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Tooltip(text = tooltipText, modifier = modifier) {
        IconButton(
            icon = rememberVectorPainter(Icons.Filled.BookmarkAdd),
            contentDescription = tooltipText,
            onClick = onClick,
            hierarchy = ButtonHierarchy.Secondary,
            intent = ButtonIntent.Default,
            enabled = enabled,
        )
    }
}

/** Uniform "Add to Schedule" button. */
@Composable
fun AddToScheduleButton(
    onClick: () -> Unit,
    tooltipText: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Tooltip(text = tooltipText, modifier = modifier) {
        IconButton(
            icon = painterResource(Res.drawable.ic_playlist_add),
            contentDescription = tooltipText,
            onClick = onClick,
            hierarchy = ButtonHierarchy.Primary,
            intent = ButtonIntent.Neutral,
            enabled = enabled,
        )
    }
}
