package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.customize_theme_preview_body
import churchpresenter.composeapp.generated.resources.customize_theme_preview_button
import churchpresenter.composeapp.generated.resources.customize_theme_preview_chip
import churchpresenter.composeapp.generated.resources.customize_theme_preview_error
import churchpresenter.composeapp.generated.resources.customize_theme_preview_field
import churchpresenter.composeapp.generated.resources.customize_theme_preview_heading
import churchpresenter.composeapp.generated.resources.customize_theme_preview_secondary
import churchpresenter.composeapp.generated.resources.customize_theme_preview_song
import churchpresenter.composeapp.generated.resources.customize_theme_preview_song_selected
import churchpresenter.composeapp.generated.resources.customize_theme_preview_success
import churchpresenter.composeapp.generated.resources.customize_theme_preview_warning
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.components.RaisedFilterChip
import org.churchpresenter.theme.components.SettingsTextField
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource

private val PREVIEW_GAP = 6.dp
private val PREVIEW_BUTTON_PADDING = PaddingValues(horizontal = 14.dp, vertical = 8.dp)

/**
 * A small slice of the app drawn in the draft theme: a page, a card with a selected and a plain row,
 * a field, the buttons, a chip, and the three status colours.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemePreviewCard() {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize().background(scheme.background).padding(8.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.surfaceContainer, RoundedCornerShape(8.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(PREVIEW_GAP),
        ) {
            Text(
                stringResource(Res.string.customize_theme_preview_heading),
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
            )
            Text(
                stringResource(Res.string.customize_theme_preview_body),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            PreviewRow(stringResource(Res.string.customize_theme_preview_song_selected), selected = true)
            PreviewRow(stringResource(Res.string.customize_theme_preview_song), selected = false)
            SettingsTextField(
                value = "",
                onValueChange = {},
                placeholder = { Text(stringResource(Res.string.customize_theme_preview_field), maxLines = 1) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // Rows that wrap rather than clip: at Extra large the narrow column cannot hold them on one line.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(PREVIEW_GAP),
            ) {
                // Tighter than a button's default padding, so both fit the column at the default size.
                RaisedButton(onClick = {}, contentPadding = PREVIEW_BUTTON_PADDING) {
                    Text(stringResource(Res.string.customize_theme_preview_button), maxLines = 1)
                }
                KeyButton(onClick = {}, contentPadding = PREVIEW_BUTTON_PADDING) {
                    Text(stringResource(Res.string.customize_theme_preview_secondary), maxLines = 1)
                }
            }
            RaisedFilterChip(
                selected = true,
                onClick = {},
                label = { Text(stringResource(Res.string.customize_theme_preview_chip)) },
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(PREVIEW_GAP),
            ) {
                val status = MaterialTheme.semantic
                StatusPill(stringResource(Res.string.customize_theme_preview_success), status.success, status.onSuccess)
                StatusPill(stringResource(Res.string.customize_theme_preview_warning), status.warning, status.onWarning)
                StatusPill(stringResource(Res.string.customize_theme_preview_error), scheme.error, scheme.onError)
            }
        }
    }
}

/** A list row as the schedule and song lists draw one: the selected row filled, with its stripe. */
@Composable
private fun PreviewRow(text: String, selected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
                RoundedCornerShape(6.dp),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(28.dp)
                .background(if (selected) MaterialTheme.semantic.marker else Color.Transparent),
        )
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusPill(text: String, fill: Color, content: Color) {
    Text(
        text,
        modifier = Modifier.background(fill, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = content,
        maxLines = 1,
    )
}
