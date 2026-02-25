package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import java.awt.GraphicsEnvironment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bottom
import churchpresenter.composeapp.generated.resources.content_announcements
import churchpresenter.composeapp.generated.resources.content_bible
import churchpresenter.composeapp.generated.resources.content_media
import churchpresenter.composeapp.generated.resources.content_pictures
import churchpresenter.composeapp.generated.resources.content_songs
import churchpresenter.composeapp.generated.resources.content_streaming
import churchpresenter.composeapp.generated.resources.detected_screens
import churchpresenter.composeapp.generated.resources.display_fullscreen
import churchpresenter.composeapp.generated.resources.display_lower_third
import churchpresenter.composeapp.generated.resources.display_mode
import churchpresenter.composeapp.generated.resources.identify_screen
import churchpresenter.composeapp.generated.resources.left
import churchpresenter.composeapp.generated.resources.num_screens_label
import churchpresenter.composeapp.generated.resources.presenter_windows_count
import churchpresenter.composeapp.generated.resources.projection_position_help
import churchpresenter.composeapp.generated.resources.right
import churchpresenter.composeapp.generated.resources.screen
import churchpresenter.composeapp.generated.resources.screen_assignment
import churchpresenter.composeapp.generated.resources.screen_col_label
import churchpresenter.composeapp.generated.resources.top
import churchpresenter.composeapp.generated.resources.window_position
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.ScreenAssignment
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProjectionSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onIdentifyScreen: () -> Unit = {}
) {
    val proj = settings.projectionSettings

    // Detect physical screens once; derive presenter window count automatically.
    val detectedScreens = remember {
        GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.size
    }
    val presenterWindowCount = (detectedScreens - 1).coerceIn(1, 4)

    // Auto-save whenever the detected count differs from what's stored.
    LaunchedEffect(presenterWindowCount) {
        if (proj.numberOfWindows != presenterWindowCount) {
            onSettingsChange { s ->
                s.copy(projectionSettings = s.projectionSettings.copy(numberOfWindows = presenterWindowCount))
            }
        }
    }

    val numScreens = presenterWindowCount
    val screenAssignments = listOf(proj.screen1Assignment, proj.screen2Assignment, proj.screen3Assignment, proj.screen4Assignment)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Screen Assignment Grid ──────────────────────────────────────────
        SectionHeader(stringResource(Res.string.screen_assignment))
        Spacer(modifier = Modifier.height(4.dp))

        // Detected screens info + Identify button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(Res.string.detected_screens, detectedScreens),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(Res.string.presenter_windows_count, presenterWindowCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = { onIdentifyScreen() }) {
                Text(
                    text = stringResource(Res.string.identify_screen),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Grid table — screens are rows (left), content types are columns (top)
        val screenLabelWidth = 70.dp
        val cellWidth = 80.dp

        val bibleLabel = stringResource(Res.string.content_bible)
        val songsLabel = stringResource(Res.string.content_songs)
        val picturesLabel = stringResource(Res.string.content_pictures)
        val mediaLabel = stringResource(Res.string.content_media)
        val streamingLabel = stringResource(Res.string.content_streaming)
        val announcementsLabel = stringResource(Res.string.content_announcements)
        val fullScreenLabel = stringResource(Res.string.display_fullscreen)
        val lowerThirdLabel = stringResource(Res.string.display_lower_third)

        data class ContentCol(
            val label: String,
            val getter: (ScreenAssignment) -> Boolean,
            val setter: (ScreenAssignment, Boolean) -> ScreenAssignment
        )

        val contentCols = listOf(
            ContentCol(bibleLabel, { it.showBible }, { a, v -> a.copy(showBible = v) }),
            ContentCol(songsLabel, { it.showSongs }, { a, v -> a.copy(showSongs = v) }),
            ContentCol(picturesLabel, { it.showPictures }, { a, v -> a.copy(showPictures = v) }),
            ContentCol(mediaLabel, { it.showMedia }, { a, v -> a.copy(showMedia = v) }),
            ContentCol(streamingLabel, { it.showStreaming }, { a, v -> a.copy(showStreaming = v) }),
            ContentCol(announcementsLabel, { it.showAnnouncements }, { a, v -> a.copy(showAnnouncements = v) }),
        )

        val displayModes = listOf(
            fullScreenLabel to Constants.DISPLAY_MODE_FULLSCREEN,
            lowerThirdLabel to Constants.DISPLAY_MODE_LOWER_THIRD
        )

        // Header row: blank screen label cell + content column headers + display mode headers + identify
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.width(screenLabelWidth))
            contentCols.forEach { col ->
                Text(
                    text = col.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(cellWidth)
                )
            }
            Text(
                text = stringResource(Res.string.display_mode),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(cellWidth * 2)
            )
        }

        // Sub-header for display mode columns
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.width(screenLabelWidth + cellWidth * contentCols.size))
            displayModes.forEach { (modeLabel, _) ->
                Text(
                    text = modeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(cellWidth)
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

        // One row per screen
        for (i in 0 until numScreens) {
            val assignment = screenAssignments[i]

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Screen label
                Text(
                    text = stringResource(Res.string.screen_col_label, i + 1),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(screenLabelWidth)
                )

                // Checkbox cells for each content type
                contentCols.forEach { col ->
                    Box(modifier = Modifier.width(cellWidth), contentAlignment = Alignment.Center) {
                        Checkbox(
                            checked = col.getter(assignment),
                            onCheckedChange = { checked ->
                                val updated = col.setter(assignment, checked)
                                onSettingsChange { s ->
                                    when (i) {
                                        0 -> s.copy(projectionSettings = s.projectionSettings.copy(screen1Assignment = updated))
                                        1 -> s.copy(projectionSettings = s.projectionSettings.copy(screen2Assignment = updated))
                                        2 -> s.copy(projectionSettings = s.projectionSettings.copy(screen3Assignment = updated))
                                        else -> s.copy(projectionSettings = s.projectionSettings.copy(screen4Assignment = updated))
                                    }
                                }
                            }
                        )
                    }
                }

                // Radio button cells for display mode
                displayModes.forEach { (_, modeValue) ->
                    Box(modifier = Modifier.width(cellWidth), contentAlignment = Alignment.Center) {
                        RadioButton(
                            selected = assignment.displayMode == modeValue,
                            onClick = {
                                val updated = assignment.copy(displayMode = modeValue)
                                onSettingsChange { s ->
                                    when (i) {
                                        0 -> s.copy(projectionSettings = s.projectionSettings.copy(screen1Assignment = updated))
                                        1 -> s.copy(projectionSettings = s.projectionSettings.copy(screen2Assignment = updated))
                                        2 -> s.copy(projectionSettings = s.projectionSettings.copy(screen3Assignment = updated))
                                        else -> s.copy(projectionSettings = s.projectionSettings.copy(screen4Assignment = updated))
                                    }
                                }
                            }
                        )
                    }
                }
            }

            if (i < numScreens - 1) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Window Position Settings ────────────────────────────────────────
        SectionHeader(stringResource(Res.string.window_position))
        Spacer(modifier = Modifier.height(8.dp))

        // Visual representation box with position fields
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(180.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top position
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(Res.string.top),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    NumberSettingsTextField(
                        initialText = proj.windowTop,
                        onValueChange = { value ->
                            onSettingsChange { s ->
                                s.copy(projectionSettings = s.projectionSettings.copy(windowTop = value))
                            }
                        },
                        range = 0..10000
                    )
                }

                // Middle row - Left and Right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left position
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(Res.string.left),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        NumberSettingsTextField(
                            initialText = proj.windowLeft,
                            onValueChange = { value ->
                                onSettingsChange { s ->
                                    s.copy(projectionSettings = s.projectionSettings.copy(windowLeft = value))
                                }
                            },
                            range = 0..10000
                        )
                    }

                    // Center indicator
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(Res.string.screen),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // Right position
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(Res.string.right),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        NumberSettingsTextField(
                            initialText = proj.windowRight,
                            onValueChange = { value ->
                                onSettingsChange { s ->
                                    s.copy(projectionSettings = s.projectionSettings.copy(windowRight = value))
                                }
                            },
                            range = 0..10000
                        )
                    }
                }

                // Bottom position
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(Res.string.bottom),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    NumberSettingsTextField(
                        initialText = proj.windowBottom,
                        onValueChange = { value ->
                            onSettingsChange { s ->
                                s.copy(projectionSettings = s.projectionSettings.copy(windowBottom = value))
                            }
                        },
                        range = 0..10000
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Help text
        Text(
            text = stringResource(Res.string.projection_position_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Column {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

