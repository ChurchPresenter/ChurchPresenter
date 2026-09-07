package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.ok
import churchpresenter.composeapp.generated.resources.output_resolution_custom
import churchpresenter.composeapp.generated.resources.output_resolution_custom_title
import churchpresenter.composeapp.generated.resources.output_resolution_height
import churchpresenter.composeapp.generated.resources.output_resolution_width
import org.churchpresenter.app.churchpresenter.utils.OUTPUT_RESOLUTIONS
import org.churchpresenter.app.churchpresenter.utils.formatAspectRatio
import org.jetbrains.compose.resources.stringResource

/** The widest and narrowest an output may be set to. Wide enough for 8K, narrow enough to stay sane. */
private val RESOLUTION_RANGE = 16..8192

/** How a resolution is written everywhere in the UI: `1920×1080`. */
fun formatResolution(width: Int, height: Int): String = "$width×$height"

/**
 * Picks an output's resolution: the common shapes from a menu, anything else by typing it.
 *
 * One control for every output that has a resolution -- the dev fallback windows, the Browser
 * Source outputs and the NDI outputs. Those last two carried a copy each of the same four 16:9
 * sizes, which is why none of them could be set to the 4:3 or ultrawide shape they were meant to
 * be standing in for. The list is [OUTPUT_RESOLUTIONS]; the custom entry covers the rest.
 */
@Composable
fun ResolutionPicker(
    label: String,
    width: Int,
    height: Int,
    cellWidth: Dp,
    labelHeight: Dp,
    onChange: (width: Int, height: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var customOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier.width(cellWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.fillMaxWidth().height(labelHeight),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedButton(
            shape = RoundedCornerShape(6.dp),
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = formatResolution(width, height),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            OUTPUT_RESOLUTIONS.forEach { size ->
                DropdownMenuItem(
                    text = {
                        // The shape beside the numbers: 2560x1080 says nothing about being an
                        // ultrawide until it says 21:9 next to it.
                        Text(
                            text = "${formatResolution(size.width, size.height)}" +
                                "  ${formatAspectRatio(size.width, size.height)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    onClick = { expanded = false; onChange(size.width, size.height) },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(Res.string.output_resolution_custom),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                onClick = { expanded = false; customOpen = true },
            )
        }
    }

    if (customOpen) {
        CustomResolutionDialog(
            width = width,
            height = height,
            onDismiss = { customOpen = false },
            onConfirm = { w, h -> customOpen = false; onChange(w, h) },
        )
    }
}

/** Two number fields and an OK, for a resolution the menu does not offer. */
@Composable
private fun CustomResolutionDialog(
    width: Int,
    height: Int,
    onDismiss: () -> Unit,
    onConfirm: (width: Int, height: Int) -> Unit,
) {
    // Held here rather than applied per keystroke: a half-typed "19" is a real resolution as far as
    // the field is concerned, and applying it would resize the window under the operator's hands.
    var draftWidth by remember { mutableStateOf(width) }
    var draftHeight by remember { mutableStateOf(height) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(12.dp),
        title = { Text(stringResource(Res.string.output_resolution_custom_title)) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberSettingsTextField(
                    label = stringResource(Res.string.output_resolution_width),
                    initialText = draftWidth,
                    range = RESOLUTION_RANGE,
                    onValueChange = { draftWidth = it },
                )
                NumberSettingsTextField(
                    label = stringResource(Res.string.output_resolution_height),
                    initialText = draftHeight,
                    range = RESOLUTION_RANGE,
                    onValueChange = { draftHeight = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draftWidth, draftHeight) }) {
                Text(stringResource(Res.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}
