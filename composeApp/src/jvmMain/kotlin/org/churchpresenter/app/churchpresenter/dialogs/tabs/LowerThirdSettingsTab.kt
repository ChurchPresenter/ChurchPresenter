package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bottom
import churchpresenter.composeapp.generated.resources.display_lower_third
import churchpresenter.composeapp.generated.resources.generate_lower_third
import churchpresenter.composeapp.generated.resources.left
import churchpresenter.composeapp.generated.resources.lottie_files
import churchpresenter.composeapp.generated.resources.lottie_select_preset
import churchpresenter.composeapp.generated.resources.no_directory_selected
import churchpresenter.composeapp.generated.resources.no_lottie_files
import churchpresenter.composeapp.generated.resources.remove_lottie_file
import churchpresenter.composeapp.generated.resources.right
import churchpresenter.composeapp.generated.resources.top
import churchpresenter.composeapp.generated.resources.window_position
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.viewmodel.LowerThirdSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.delay

@Composable
fun LowerThirdSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onOpenLottieGen: (outputDir: String, onFileSaved: (() -> Unit)?) -> Unit = { _, _ -> }
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { LowerThirdSettingsViewModel() }

    // Keep viewModel folder in sync with settings
    LaunchedEffect(settings.streamingSettings.lowerThirdFolder) {
        val folder = settings.streamingSettings.lowerThirdFolder
        if (viewModel.lottieFolder != folder) viewModel.setFolder(folder)
    }

    val lottieFolder = viewModel.lottieFolder
    val lottieFilesInDirectory = remember(lottieFolder, viewModel.refreshTrigger) {
        viewModel.filesInDirectory()
    }
    val selectedFile = viewModel.selectedFile

    // Preview — debounced so we don't re-read on every keystroke
    var debouncedPath by remember { mutableStateOf(viewModel.importSourcePath()) }
    LaunchedEffect(selectedFile, lottieFolder) {
        delay(400)
        debouncedPath = viewModel.importSourcePath()
    }
    val previewJsonContent = remember(debouncedPath) { viewModel.previewJsonContent() }

    val composition by rememberLottieComposition(key = previewJsonContent) {
        LottieCompositionSpec.JsonString(previewJsonContent.ifBlank { "{}" })
    }
    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = previewJsonContent.isNotBlank(),
        iterations = Int.MAX_VALUE
    )

    val noDirectorySelectedStr = stringResource(Res.string.no_directory_selected)
    val noLottieFilesStr = stringResource(Res.string.no_lottie_files)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

        // ── Left panel ──────────────────────────────────────────────
        SettingsSection(
            title = stringResource(Res.string.lottie_files),
            modifier = Modifier
                .weight(0.48f)
                .widthIn(min = 400.dp, max = 450.dp)
                .heightIn(min = 600.dp)
        ) {
            Text(
                text = "To trigger lower thirds via API visit the Server tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(8.dp)
                ) {
                    if (lottieFilesInDirectory.isEmpty()) {
                        Text(
                            text = if (lottieFolder.isEmpty()) noDirectorySelectedStr else noLottieFilesStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        lottieFilesInDirectory.forEach { fileName ->
                            val isSelected = fileName == selectedFile
                            Text(
                                text = fileName,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent
                                    )
                                    .clickable { viewModel.selectFile(fileName) }
                                    .padding(vertical = 4.dp, horizontal = 8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModernButton(
                    text = stringResource(Res.string.remove_lottie_file),
                    backgroundColor = MaterialTheme.colorScheme.errorContainer,
                    onClick = { viewModel.removeSelectedFile() }
                )
                ModernButton(
                    text = stringResource(Res.string.generate_lower_third),
                    backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                    onClick = {
                        onOpenLottieGen(settings.streamingSettings.lowerThirdFolder) {
                            viewModel.onFileSavedFromGenerator()
                        }
                    }
                )
            }
        }

        // ── Right panel — live preview ───────────────────────────────
        Column(
            modifier = Modifier
                .weight(0.48f)
                .widthIn(min = 400.dp, max = 450.dp)
                .heightIn(min = 600.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = selectedFile ?: stringResource(Res.string.lottie_select_preset),
                style = MaterialTheme.typography.bodyMedium,
                color = if (previewJsonContent.isNotBlank()) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            )

            // Lottie preview box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black, RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (previewJsonContent.isNotBlank()) {
                    Image(
                        painter = rememberLottiePainter(
                            composition = composition,
                            progress = { progress }
                        ),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = stringResource(Res.string.lottie_select_preset),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
            }

            // ── Window Position ──────────────────────────────────────
            HorizontalDivider()
            Text(
                text = stringResource(Res.string.window_position),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            )

            val streaming = settings.streamingSettings

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.top), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                NumberSettingsTextField(
                    initialText = streaming.windowTop,
                    onValueChange = { v -> onSettingsChange { s -> s.copy(streamingSettings = s.streamingSettings.copy(windowTop = v)) } },
                    range = 0..10000
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(Res.string.left), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    NumberSettingsTextField(
                        initialText = streaming.windowLeft,
                        onValueChange = { v -> onSettingsChange { s -> s.copy(streamingSettings = s.streamingSettings.copy(windowLeft = v)) } },
                        range = 0..10000
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .height(80.dp)
                        .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(settings.projectionSettings.lowerThirdHeightPercent / 100f)
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
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(Res.string.right), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    NumberSettingsTextField(
                        initialText = streaming.windowRight,
                        onValueChange = { v -> onSettingsChange { s -> s.copy(streamingSettings = s.streamingSettings.copy(windowRight = v)) } },
                        range = 0..10000
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.bottom), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                NumberSettingsTextField(
                    initialText = streaming.windowBottom,
                    onValueChange = { v -> onSettingsChange { s -> s.copy(streamingSettings = s.streamingSettings.copy(windowBottom = v)) } },
                    range = 0..10000
                )
            }
        }
    }
    }
}


@Composable
private fun ModernButton(
    text: String,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium)
    }
}


