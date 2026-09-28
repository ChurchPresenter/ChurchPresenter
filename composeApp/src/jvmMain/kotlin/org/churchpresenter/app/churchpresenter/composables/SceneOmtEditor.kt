package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.canvas_ndi_refresh
import churchpresenter.composeapp.generated.resources.canvas_ndi_source
import churchpresenter.composeapp.generated.resources.canvas_omt_address
import churchpresenter.composeapp.generated.resources.canvas_omt_address_help
import churchpresenter.composeapp.generated.resources.canvas_omt_none_found
import churchpresenter.composeapp.generated.resources.canvas_omt_preview
import churchpresenter.composeapp.generated.resources.canvas_omt_preview_help
import churchpresenter.composeapp.generated.resources.canvas_omt_searching
import churchpresenter.composeapp.generated.resources.canvas_omt_unavailable
import churchpresenter.composeapp.generated.resources.canvas_source_omt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.presenter.OmtManager
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.omt.OmtRuntimeStatus
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.DropdownSelector
import org.churchpresenter.theme.components.RaisedButton
import org.jetbrains.compose.resources.stringResource

/**
 * How many times the panel looks, a second apart, each time it opens or Refresh is pressed.
 *
 * `libomt` discovers in the background from its first use, so the list grows over the first
 * seconds; a handful of looks catches that without a loop that outlives the reason for it.
 */
private const val DISCOVERY_LOOKS = 5
private const val DISCOVERY_STEP_MS = 1_000L

/**
 * Where an OMT layer's source is chosen: from what discovery has found, or by typing an address.
 *
 * Simpler than [NdiProperties] in one respect — `libomt` runs one discovery for the whole process,
 * so there is nothing to acquire and release — and it offers one thing that one does not: an
 * `omt://host:port` address, which reaches a source on another subnet with no discovery at all.
 *
 * The configured source stays in the list when discovery cannot see it, for the reason NDI's does.
 */
@Composable
internal fun OmtProperties(
    source: SceneSource.OmtSource,
    onUpdate: (SceneSource) -> Unit,
    /**
     * Whether the library is loaded. Collected rather than read once, so a library loaded while the
     * panel is open brings it to life; a parameter so a test pins it.
     */
    status: OmtRuntimeStatus = OmtManager.status.collectAsState().value,
    /** What discovery has found. A parameter so a test supplies the network. */
    discover: () -> List<String> = OmtManager::discoverSources,
    /** How far apart the looks are. A parameter so a test does not wait out real seconds. */
    lookStepMs: Long = DISCOVERY_STEP_MS,
) {
    Text(
        stringResource(Res.string.canvas_source_omt),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (!status.isReady) {
        Text(
            text = stringResource(Res.string.canvas_omt_unavailable),
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        return
    }

    var discovered by remember { mutableStateOf<List<String>>(emptyList()) }
    var looked by remember { mutableStateOf(false) }
    var looking by remember { mutableStateOf(true) }
    var looks by remember { mutableStateOf(0) }

    // One effect for the opening looks and every refresh, cancelled with the panel.
    LaunchedEffect(looks) {
        looking = true
        repeat(DISCOVERY_LOOKS) { attempt ->
            if (attempt > 0) delay(lookStepMs)
            discovered = withContext(Dispatchers.IO) { discover() }
            looked = true
        }
        looking = false
    }

    RaisedButton(
        onClick = { looks++ },
        enabled = !looking,
        modifier = Modifier.fillMaxWidth(),
        shape = AppShape(8.dp)
    ) {
        Text(stringResource(Res.string.canvas_ndi_refresh), style = MaterialTheme.typography.labelSmall)
    }

    val names = omtSourceChoices(discovered, source.sourceAddress)
    if (names.isEmpty()) {
        Text(
            text = if (looked && !looking) stringResource(Res.string.canvas_omt_none_found)
                   else stringResource(Res.string.canvas_omt_searching),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    } else {
        DropdownSelector(
            label = stringResource(Res.string.canvas_ndi_source),
            items = names,
            selected = source.sourceAddress,
            onSelectedChange = { chosen -> onUpdate(source.copy(sourceAddress = chosen)) },
            modifier = Modifier.fillMaxWidth()
        )
    }

    PropertyCommitTextField(
        label = stringResource(Res.string.canvas_omt_address),
        value = source.sourceAddress,
        onCommit = { typed -> onUpdate(source.copy(sourceAddress = typed)) },
    )
    Text(
        text = stringResource(Res.string.canvas_omt_address_help),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
    )

    LabeledCheckbox(
        checked = source.preview,
        onCheckedChange = { onUpdate(source.copy(preview = it)) },
        label = stringResource(Res.string.canvas_omt_preview),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        spacing = 4.dp,
    )
    Text(
        text = stringResource(Res.string.canvas_omt_preview_help),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
    )
}

/** What discovery found, plus [configured] when it is not among them — see [ndiSourceChoices]. */
internal fun omtSourceChoices(discovered: List<String>, configured: String): List<String> =
    if (configured.isBlank() || configured in discovered) discovered else discovered + configured
