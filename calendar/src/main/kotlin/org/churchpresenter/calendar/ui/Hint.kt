package org.churchpresenter.calendar.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * The hover hint for a control that is only an icon.
 *
 * `contentDescription` is read by a screen reader and shown to nobody else, so an icon button with
 * nothing but that is unlabelled for everyone looking at it. This is the app's own tooltip --
 * inverse surface, small text, below the control -- in the one place this module draws it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Hint(text: String, content: @Composable () -> Unit) {
    TooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = HINT_ELEVATION,
            ) {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        },
        delayMillis = HINT_DELAY_MILLIS,
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.BottomCenter,
            offset = DpOffset(0.dp, 4.dp),
        ),
        content = content,
    )
}

private val HINT_ELEVATION = 4.dp
private const val HINT_DELAY_MILLIS = 400
