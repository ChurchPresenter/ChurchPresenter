package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.companion_satellite_no_host
import org.churchpresenter.app.churchpresenter.composables.CompanionConnectionChipRow
import org.churchpresenter.app.churchpresenter.composables.CompanionSurfacePanel
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.models.CompanionSurfacePlacement
import org.churchpresenter.app.churchpresenter.viewmodel.CompanionSatelliteViewModel
import org.jetbrains.compose.resources.stringResource

@Composable
fun CompanionSurfaceTab(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    viewModel: CompanionSatelliteViewModel
) {
    val connections = appSettings.companionSatelliteConnections.filter { it.showInTab }
    var selectedId by remember { mutableStateOf(connections.firstOrNull()?.id) }
    LaunchedEffect(connections.map { it.id }) {
        if (connections.none { it.id == selectedId }) selectedId = connections.firstOrNull()?.id
    }
    val current = connections.find { it.id == selectedId }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        if (connections.size > 1) {
            CompanionConnectionChipRow(
                connections = connections,
                selectedId = selectedId,
                onSelect = { selectedId = it }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (current == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(Res.string.companion_satellite_no_host),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            CompanionSurfacePanel(
                connection = current,
                placement = CompanionSurfacePlacement.TAB,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
