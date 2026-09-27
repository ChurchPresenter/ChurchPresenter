package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.canvas_dual_layout
import churchpresenter.composeapp.generated.resources.canvas_size
import churchpresenter.composeapp.generated.resources.canvas_size_custom
import churchpresenter.composeapp.generated.resources.canvas_size_match_output
import churchpresenter.composeapp.generated.resources.canvas_size_set
import churchpresenter.composeapp.generated.resources.canvas_size_tooltip
import churchpresenter.composeapp.generated.resources.ic_check
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.dialogs.tabs.PreviewShapePreset
import org.churchpresenter.app.churchpresenter.viewmodel.CANVAS_SIDE_RANGE
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.KeyIconButton
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Test handle for a scene row's canvas-size button. */
internal const val CANVAS_SIZE_BUTTON_TAG = "canvas_size_button"

/** Test handle for the menu's Landscape and portrait item. */
internal const val CANVAS_DUAL_LAYOUT_TAG = "canvas_dual_layout"

/** Test handle for the Custom row's Set button. */
internal const val CANVAS_SIZE_SET_TAG = "canvas_size_set"

/** Test handles for the Custom row's width and height fields. */
internal const val CANVAS_SIZE_WIDTH_TAG = "canvas_size_width"
internal const val CANVAS_SIZE_HEIGHT_TAG = "canvas_size_height"

private val CUSTOM_FIELD_WIDTH = 90.dp
private val CHECK_SIZE = 14.dp

/** `1920×1080` -- digits and a multiplication sign, the same in every language. */
private fun sizeText(width: Int, height: Int) = "$width×$height"

/**
 * A scene's canvas-size button and the menu it opens: the shapes the Profiles preview offers, one
 * entry per projection output, and a custom size.
 *
 * Choosing one hands the size to [onSetSize]; layers are fractions of the canvas, so they keep their
 * places and stretch with it. The current size is ticked wherever it appears.
 *
 * Above the sizes, Landscape and portrait turns the scene's second layout on or off through
 * [onDualLayoutChange], ticked while [dualLayout] is on. The sizes always set the main canvas; the
 * second one is the same canvas turned sideways, so it follows.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CanvasSizeMenu(
    width: Int,
    height: Int,
    outputs: List<CanvasOutputSize>,
    onSetSize: (Int, Int) -> Unit,
    dualLayout: Boolean = false,
    onDualLayoutChange: (Boolean) -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TooltipArea(
            tooltip = {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = MaterialTheme.shapes.extraSmall,
                    tonalElevation = 4.dp,
                ) {
                    Text(
                        stringResource(Res.string.canvas_size_tooltip, sizeText(width, height)),
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
        ) {
            KeyIconButton(
                onClick = { open = true },
                modifier = Modifier.size(20.dp).testTag(CANVAS_SIZE_BUTTON_TAG),
            ) {
                Icon(
                    Icons.Filled.AspectRatio,
                    contentDescription = stringResource(Res.string.canvas_size),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val choose: (Int, Int) -> Unit = { w, h ->
                onSetSize(w, h)
                open = false
            }
            DropdownMenuItem(
                text = {
                    Text(stringResource(Res.string.canvas_dual_layout), style = MaterialTheme.typography.bodySmall)
                },
                onClick = {
                    open = false
                    onDualLayoutChange(!dualLayout)
                },
                trailingIcon = { if (dualLayout) Tick() },
                modifier = Modifier.testTag(CANVAS_DUAL_LAYOUT_TAG),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PreviewShapePreset.entries.forEach { preset ->
                SizeItem(preset.label, preset.width, preset.height, width, height, choose)
            }
            if (outputs.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                outputs.forEach { output ->
                    SizeItem(
                        stringResource(Res.string.canvas_size_match_output, output.label),
                        output.width,
                        output.height,
                        width,
                        height,
                        choose,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CustomSizeRow(width, height, choose)
        }
    }
}

/** One size to pick, named, with its exact size beside it and a tick when it is the current one. */
@Composable
private fun SizeItem(
    label: String,
    itemWidth: Int,
    itemHeight: Int,
    currentWidth: Int,
    currentHeight: Int,
    onChoose: (Int, Int) -> Unit,
) {
    DropdownMenuItem(
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(label, style = MaterialTheme.typography.bodySmall)
                Text(
                    sizeText(itemWidth, itemHeight),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        onClick = { onChoose(itemWidth, itemHeight) },
        trailingIcon = { if (itemWidth == currentWidth && itemHeight == currentHeight) Tick() },
    )
}

/** The tick beside whichever menu item is on. */
@Composable
private fun Tick() {
    Icon(
        painterResource(Res.drawable.ic_check),
        contentDescription = null,
        modifier = Modifier.size(CHECK_SIZE),
    )
}

/** Any size, typed as width × height and applied with Set. */
@Composable
private fun CustomSizeRow(currentWidth: Int, currentHeight: Int, onChoose: (Int, Int) -> Unit) {
    var customWidth by remember(currentWidth) { mutableIntStateOf(currentWidth) }
    var customHeight by remember(currentHeight) { mutableIntStateOf(currentHeight) }
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.canvas_size_custom), style = MaterialTheme.typography.bodySmall)
        NumberSettingsTextField(
            modifier = Modifier.width(CUSTOM_FIELD_WIDTH).testTag(CANVAS_SIZE_WIDTH_TAG),
            initialText = customWidth,
            range = CANVAS_SIDE_RANGE,
            onValueChange = { customWidth = it },
        )
        Text("×", style = MaterialTheme.typography.bodySmall)
        NumberSettingsTextField(
            modifier = Modifier.width(CUSTOM_FIELD_WIDTH).testTag(CANVAS_SIZE_HEIGHT_TAG),
            initialText = customHeight,
            range = CANVAS_SIDE_RANGE,
            onValueChange = { customHeight = it },
        )
        KeyButton(
            onClick = { onChoose(customWidth, customHeight) },
            shape = AppShape(6.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            modifier = Modifier.testTag(CANVAS_SIZE_SET_TAG),
        ) {
            Text(stringResource(Res.string.canvas_size_set), style = MaterialTheme.typography.labelSmall)
        }
    }
}
