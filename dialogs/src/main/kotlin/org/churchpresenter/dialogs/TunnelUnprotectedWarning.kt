package org.churchpresenter.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.AppShape
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.tunnel_unprotected_warning
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource

/**
 * Whether the server answers a tunnel's visitors with no key: protection off, or on with no key set.
 * The tunnel forwards the whole server, not only the page it is opened for, so that is every
 * control route reachable from the internet.
 */
internal fun tunnelUnprotected(apiKeyEnabled: Boolean, apiKey: String): Boolean =
    !apiKeyEnabled || apiKey.isBlank()

/**
 * Says, beside the tunnel's controls, that public access opens the whole server while no API key
 * protects it. It warns only; the tunnel starts all the same.
 */
@Composable
internal fun TunnelUnprotectedWarning(apiKeyEnabled: Boolean, apiKey: String) {
    if (!tunnelUnprotected(apiKeyEnabled, apiKey)) return
    val colors = MaterialTheme.semantic
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(colors.warningContainer, AppShape(6.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = colors.onWarningContainer,
            modifier = Modifier.size(16.dp),
        )
        Text(
            stringResource(Res.string.tunnel_unprotected_warning),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onWarningContainer,
        )
    }
}
