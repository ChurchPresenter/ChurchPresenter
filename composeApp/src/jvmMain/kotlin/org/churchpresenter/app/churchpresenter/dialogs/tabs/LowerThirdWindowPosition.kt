package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bottom
import churchpresenter.composeapp.generated.resources.display_lower_third
import churchpresenter.composeapp.generated.resources.left
import churchpresenter.composeapp.generated.resources.right
import churchpresenter.composeapp.generated.resources.top
import churchpresenter.composeapp.generated.resources.window_position
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.app.churchpresenter.composables.TvScreenBox
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.StreamingSettings
import org.jetbrains.compose.resources.stringResource

/**
 * The lower third's window padding, drawn as four insets around a mock TV screen.
 *
 * Moved out of the Lower Third settings tab and into the per-output Customize dialog: the padding
 * positions the graphic inside one output's frame, and a sanctuary projector, a foyer TV and a
 * Browser Source overlay do not agree on where that is. `LowerThirdPresenter` reads the four values
 * from whatever `AppSettings` its output was resolved with, so nothing about the drawing changed.
 *
 * The band drawn behind the Top field is sized from the global `lowerThirdHeightPercent`, which is
 * still one setting for the whole install and lives on the Projection tab.
 */
@Composable
internal fun LowerThirdWindowPositionSection(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    fun updateInsets(transform: StreamingSettings.() -> StreamingSettings) {
        onSettingsChange { s -> s.copy(streamingSettings = s.streamingSettings.transform()) }
    }
    SettingsSection(title = stringResource(Res.string.window_position)) {
        val streaming = settings.streamingSettings

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NumberSettingsTextField(
                    modifier = Modifier.width(100.dp).offset(y = 42.dp),
                    label = stringResource(Res.string.left),
                    initialText = streaming.windowLeft,
                    onValueChange = { v -> updateInsets { copy(windowLeft = v) } },
                    range = 0..10000
                )
                TvScreenBox(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .height(180.dp)
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val bandHeight = maxHeight * (settings.projectionSettings.lowerThirdHeightPercent / 100f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(bandHeight)
                                .align(Alignment.BottomCenter)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                        ) {
                            Text(
                                text = stringResource(Res.string.display_lower_third),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        // Top field sits just above the lower-third band, centered on the screen.
                        NumberSettingsTextField(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = -bandHeight)
                                .width(100.dp),
                            label = stringResource(Res.string.top),
                            initialText = streaming.windowTop,
                            onValueChange = { v -> updateInsets { copy(windowTop = v) } },
                            range = 0..10000
                        )
                    }
                }
                NumberSettingsTextField(
                    modifier = Modifier.width(100.dp).offset(y = 42.dp),
                    label = stringResource(Res.string.right),
                    initialText = streaming.windowRight,
                    onValueChange = { v -> updateInsets { copy(windowRight = v) } },
                    range = 0..10000
                )
            }

            NumberSettingsTextField(
                modifier = Modifier.width(100.dp),
                label = stringResource(Res.string.bottom),
                initialText = streaming.windowBottom,
                onValueChange = { v -> updateInsets { copy(windowBottom = v) } },
                range = 0..10000
            )
        }
    }
}
