package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.companion_satellite_no_host
import churchpresenter.composeapp.generated.resources.companion_satellite_status_connecting
import churchpresenter.composeapp.generated.resources.companion_satellite_status_disconnected
import churchpresenter.composeapp.generated.resources.companion_satellite_status_error
import companionsatellite.CompanionConnectionStatus
import org.churchpresenter.app.churchpresenter.data.settings.CompanionSatelliteSettings
import org.churchpresenter.app.churchpresenter.models.CompanionButtonState
import org.churchpresenter.app.churchpresenter.models.CompanionConnectionUiState
import org.churchpresenter.app.churchpresenter.models.CompanionSurfacePlacement
import org.churchpresenter.app.churchpresenter.models.CompanionSurfaceSlot
import org.churchpresenter.app.churchpresenter.utils.Utils
import org.churchpresenter.app.churchpresenter.viewmodel.CompanionSatelliteViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * Renders one Companion Satellite connection's live button grid at a given [placement] — status
 * text, brightness dimming overlay, and the button grid itself. Shared by the main Companion tab
 * and the left/right sidebar panels so the rendering logic isn't duplicated three times.
 */
@Composable
fun CompanionSurfacePanel(
    connection: CompanionSatelliteSettings,
    placement: CompanionSurfacePlacement,
    viewModel: CompanionSatelliteViewModel,
    modifier: Modifier = Modifier,
    /** True for the left/right sidebars: sizes this panel to exactly the height its configured
     * grid needs (rows * cell size) instead of filling all the space it's given. This lets the
     * sibling content sharing that sidebar column (the schedule list, the live preview) grow into
     * whatever's left via its own `weight(1f)`, rather than the two being forced into a fixed
     * 50/50 split regardless of how few rows this grid actually needs. False (the Tab placement,
     * which has the whole tab to itself) keeps the panel filling all available space. */
    sizeToContent: Boolean = false
) {
    val slot = CompanionSurfaceSlot(connection.id, placement)
    val state = viewModel.connectionStates[slot] ?: CompanionConnectionUiState(slot)
    val hasHost = connection.host.isNotBlank()

    Column(modifier = modifier) {
        // Only surface a status label when something needs attention — once connected, the live
        // grid itself is confirmation enough and a persistent "Connected" label is just clutter.
        if (state.status != CompanionConnectionStatus.CONNECTED) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val (statusText, statusColor) = when (state.status) {
                    CompanionConnectionStatus.CONNECTING ->
                        stringResource(Res.string.companion_satellite_status_connecting) to Color(0xFFFFC107)
                    CompanionConnectionStatus.ERROR ->
                        stringResource(Res.string.companion_satellite_status_error, state.errorMessage) to MaterialTheme.colorScheme.error
                    else ->
                        stringResource(Res.string.companion_satellite_status_disconnected) to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                }
                Text(statusText, style = MaterialTheme.typography.bodyMedium, color = statusColor)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        if (!hasHost) {
            Box(
                modifier = if (sizeToContent) Modifier.fillMaxWidth().height(80.dp) else Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(Res.string.companion_satellite_no_host),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
            return
        }

        // Companion pushes 0-100; dim the grid proportionally like a real surface's backlight would.
        val dimAlpha = ((100 - state.brightness.coerceIn(0, 100)) / 100f) * 0.85f
        val columns = connection.columnsFor(placement)
        val rows = connection.rowsFor(placement)
        val maxButtonSizeDp = connection.maxButtonSizeDpFor(placement)
        val gridSpacing = 6.dp
        val verticalContentPadding = 8.dp

        val buttonCells: LazyGridScope.() -> Unit = {
            itemsIndexed(viewModel.buttonsFor(slot)) { _, button ->
                CompanionButtonCell(
                    button = button,
                    dimAlpha = dimAlpha,
                    enabled = state.status == CompanionConnectionStatus.CONNECTED,
                    onClick = { viewModel.pressButton(slot, button.index) }
                )
            }
        }

        if (sizeToContent) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val cellSize = if (maxButtonSizeDp > 0) {
                    minOf(maxButtonSizeDp.dp, (maxWidth - gridSpacing * (columns - 1)) / columns)
                } else {
                    (maxWidth - gridSpacing * (columns - 1)) / columns
                }
                val gridWidth = cellSize * columns + gridSpacing * (columns - 1)
                val gridHeight = cellSize * rows + gridSpacing * (rows - 1) + verticalContentPadding * 2
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    LazyVerticalGrid(
                        modifier = Modifier.height(gridHeight).width(gridWidth),
                        columns = GridCells.Fixed(columns),
                        horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                        verticalArrangement = Arrangement.spacedBy(gridSpacing),
                        contentPadding = PaddingValues(vertical = verticalContentPadding),
                        userScrollEnabled = false,
                        content = buttonCells
                    )
                }
            }
        } else {
            val gridWidthModifier = if (maxButtonSizeDp > 0) {
                Modifier.widthIn(max = maxButtonSizeDp.dp * columns + gridSpacing * (columns - 1))
            } else {
                Modifier.fillMaxWidth()
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyVerticalGrid(
                    modifier = Modifier.fillMaxHeight().then(gridWidthModifier),
                    columns = GridCells.Fixed(columns),
                    horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                    verticalArrangement = Arrangement.spacedBy(gridSpacing),
                    contentPadding = PaddingValues(vertical = verticalContentPadding),
                    content = buttonCells
                )
            }
        }
    }
}

@Composable
private fun CompanionButtonCell(
    button: CompanionButtonState,
    dimAlpha: Float,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = button.color?.let { Utils.parseHexColor(it) }
        ?: MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = button.bitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = button.text,
                modifier = Modifier.fillMaxSize()
            )
        } else if (button.text.isNotBlank()) {
            val textColor = button.textColor?.let { Utils.parseHexColor(it) }
                ?: MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                button.text,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(4.dp)
            )
        }
        if (dimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dimAlpha))
            )
        }
    }
}
