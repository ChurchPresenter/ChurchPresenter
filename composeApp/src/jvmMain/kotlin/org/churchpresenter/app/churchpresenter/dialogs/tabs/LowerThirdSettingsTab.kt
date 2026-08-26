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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.generate_lower_third
import churchpresenter.composeapp.generated.resources.lottie_files
import churchpresenter.composeapp.generated.resources.lottie_select_preset
import churchpresenter.composeapp.generated.resources.no_directory_selected
import churchpresenter.composeapp.generated.resources.no_lottie_files
import churchpresenter.composeapp.generated.resources.scanning_directory
import churchpresenter.composeapp.generated.resources.remove_lottie_file
import io.github.alexzhirkevich.compottie.LottieComposition
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.churchpresenter.app.churchpresenter.composables.ScanningRow
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbar
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbarGutter
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.utils.LottieFonts
import org.churchpresenter.app.churchpresenter.viewmodel.LowerThirdSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val PREVIEW_DEBOUNCE_MS = 400L
private const val COLUMN_WEIGHT = 0.48f
private const val SELECTION_BAR_WIDTH = 4f
private const val PREVIEW_ASPECT_W = 16f
private const val PREVIEW_ASPECT_H = 9f

@Composable
fun LowerThirdSettingsTab(
    settings: AppSettings,
    onOpenLottieGen: (outputDir: String, onFileSaved: (() -> Unit)?) -> Unit = { _, _ -> }
) {
    val viewModel = remember { LowerThirdSettingsViewModel() }

    // Keep viewModel folder in sync with settings
    LaunchedEffect(settings.streamingSettings.lowerThirdFolder) {
        val folder = settings.streamingSettings.lowerThirdFolder
        if (viewModel.lottieFolder != folder) viewModel.setFolder(folder)
    }

    val lottieFolder = viewModel.lottieFolder
    // Null while the folder is being read. Deciding whether a JSON file is a Lottie means reading
    // the whole of it, so this is the folder's full weight in bytes — it cannot run in composition.
    val lottieFiles by produceState<List<String>?>(null, lottieFolder, viewModel.refreshTrigger) {
        value = null
        value = withContext(Dispatchers.IO) { viewModel.filesInDirectory() }
    }
    val lottieFilesInDirectory = lottieFiles.orEmpty()
    val selectedFile = viewModel.selectedFile

    // Preview — debounced so we don't re-read on every keystroke, and read off the UI thread
    // because a lower third's JSON runs to megabytes.
    var debouncedPath by remember { mutableStateOf(viewModel.importSourcePath()) }
    LaunchedEffect(selectedFile, lottieFolder) {
        delay(PREVIEW_DEBOUNCE_MS)
        debouncedPath = viewModel.importSourcePath()
    }
    val previewJsonContent by produceState("", debouncedPath) {
        value = withContext(Dispatchers.IO) { viewModel.previewJsonContent() }
    }

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
    val scanningDirectoryStr = stringResource(Res.string.scanning_directory)

    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(end = SettingsScrollbarGutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

        // ── Left panel ──────────────────────────────────────────────
        SettingsSection(
            title = stringResource(Res.string.lottie_files),
            modifier = Modifier
                .weight(COLUMN_WEIGHT)
                .widthIn(min = 400.dp, max = 450.dp)
                .heightIn(min = 600.dp)
        ) {
            Text(
                text = "To trigger lower thirds via API visit the Server tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            LottieFileList(
                files = lottieFilesInDirectory,
                stillScanning = lottieFiles == null && lottieFolder.isNotEmpty(),
                selectedFile = selectedFile,
                emptyText = if (lottieFolder.isEmpty()) noDirectorySelectedStr else noLottieFilesStr,
                scanningText = scanningDirectoryStr,
                onSelect = { viewModel.selectFile(it) },
            )

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
        LottiePreviewPanel(
            selectedFile = selectedFile,
            previewJsonContent = previewJsonContent,
            composition = composition,
            progress = progress,
        )
    }
        SettingsScrollbar(scrollState)
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




/** The selected preset, drawn as it will play. */
@Composable
private fun RowScope.LottiePreviewPanel(
    selectedFile: String?,
    previewJsonContent: String,
    composition: LottieComposition?,
    progress: Float,
) {
    Column(
        modifier = Modifier
            .weight(COLUMN_WEIGHT)
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
                .aspectRatio(PREVIEW_ASPECT_W / PREVIEW_ASPECT_H)
                .background(Color.Black, RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (previewJsonContent.isNotBlank()) {
                Image(
                    painter = rememberLottiePainter(
                        composition = composition,
                        progress = { progress },
                        fontManager = LottieFonts
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

    }
}

/**
 * The Lottie files found in the folder, one clickable row each.
 *
 * [stillScanning] is kept apart from an empty [files]: "No Lottie files" is a verdict about
 * the folder, and until the read finishes it would be an unearned one.
 */
@Composable
private fun LottieFileList(
    files: List<String>,
    stillScanning: Boolean,
    selectedFile: String?,
    emptyText: String,
    scanningText: String,
    onSelect: (String) -> Unit,
) {
    val listAccentColor = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (stillScanning) {
            // "No Lottie files" is a verdict about the folder; until the read finishes
            // it would be an unearned one.
            Box(
                modifier = Modifier.fillMaxWidth().height(250.dp),
                contentAlignment = Alignment.Center
            ) {
                ScanningRow(scanningText)
            }
        } else if (files.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(250.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            files.forEach { fileName ->
                val isSelected = fileName == selectedFile
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface
                        )
                        .drawBehind {
                            if (isSelected) drawRect(color = listAccentColor, size = Size(SELECTION_BAR_WIDTH, size.height))
                        }
                        .clickable { onSelect(fileName) }
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
