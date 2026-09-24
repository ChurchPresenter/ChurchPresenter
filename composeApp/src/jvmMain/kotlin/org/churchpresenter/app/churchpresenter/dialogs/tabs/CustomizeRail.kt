package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.display_fullscreen
import churchpresenter.composeapp.generated.resources.display_lower_third
import churchpresenter.composeapp.generated.resources.display_stage_monitor
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/**
 * Small shared pieces of the Profiles tab's editor: the display mode's name and the section caption.
 *
 * This file used to hold the category rail down the editor's left edge; the categories are the
 * Style tabs above the control column now (`ProfileEditor`'s `StyleTabs`), which keep its test tags.
 */

private val CAPTION_SIZE = 10.sp

private val CAPTION_TRACKING = 0.9.sp

@Composable
internal fun displayModeLabel(mode: String): String = when (shownDisplayMode(mode)) {
    Constants.DISPLAY_MODE_STAGE_MONITOR -> stringResource(Res.string.display_stage_monitor)
    Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL -> stringResource(Res.string.display_lower_third)
    else -> stringResource(Res.string.display_fullscreen)
}

/** The small uppercase accent caption the dialog's sections are titled with. */
@Composable
internal fun CustomizeCaption(label: String) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        fontSize = CAPTION_SIZE,
        letterSpacing = CAPTION_TRACKING,
        color = MaterialTheme.colorScheme.tertiary,
    )
}
