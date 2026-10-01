package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import org.churchpresenter.app.churchpresenter.composables.initialPassClickable
import org.churchpresenter.app.churchpresenter.composables.finalPassClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Warning
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.atem_upload_error
import churchpresenter.composeapp.generated.resources.atem_uploading_image
import churchpresenter.composeapp.generated.resources.atem_uploading_video
import churchpresenter.composeapp.generated.resources.atem_processing
import churchpresenter.composeapp.generated.resources.confirm_delete
import churchpresenter.composeapp.generated.resources.confirm_delete_file
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.scanning_directory
import churchpresenter.composeapp.generated.resources.no_lottie_files
import churchpresenter.composeapp.generated.resources.no_directory_selected
import churchpresenter.composeapp.generated.resources.tooltip_remove
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.swing.JOptionPane
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.LottieFonts
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.composables.PreviewOutputPicker
import org.churchpresenter.app.churchpresenter.composables.rememberPreviewOutput
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import churchpresenter.composeapp.generated.resources.generate_lower_third
import java.awt.Window
import java.io.File
import javax.swing.SwingUtilities
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.clip
import org.churchpresenter.app.churchpresenter.utils.PreviewOutput

/** The file list, its drag handle, and the preview column. */
@Composable
internal fun LowerThirdTabScope.LowerThirdBody(modifier: Modifier) {
    Row(modifier = modifier.fillMaxSize()) {
        // Left column — file list (resizable) + generate button
        LowerThirdFileList()

        LowerThirdListDragHandle()

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Which output this lower third stands for. A lower-third Lottie fills the whole output
            // surface -- LowerThirdPresenter draws it fillMaxSize/ContentScale.Fit and never reads
            // isLowerThirdVertical, which is a text-stacking flag -- so the preview wants the
            // output's full shape. The warning below must name the SAME screen the preview draws,
            // or it reports a mismatch against a monitor the animation never reaches.
            val previewOutput = rememberPreviewOutput(
                appSettings, Constants.PREVIEW_TAB_LOWER_THIRD, Presenting.LOWER_THIRD
            )

            LowerThirdTitleBar(previewOutput)

            // Upload status and the preview share one card.
            LowerThirdPreviewCard(previewOutput, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LowerThirdTabScope.LowerThirdFileList() {
    // Read once here, in composition, and handed to the list as a fixed copy: read inside the list
    // builder, a rescan landing mid-measure gave the item count and the items different lists.
    val lottieFiles = lottieFiles
    val lottieFilesOrNull = lottieFilesOrNull
    Column(
        modifier = Modifier
            .width(listWidthDp)
            .fillMaxHeight()
            .padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
            .bibleListCard()
    ) {
        val listState = rememberLazyListState()
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 6.dp, top = 6.dp, end = 10.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(rowPad(2.dp))
            ) {
                if (lottieFiles.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Three states, not one. A folder that was never chosen, a folder
                            // still being read and a folder with nothing in it are three
                            // different things to be told, and saying the same words for all of
                            // them leaves an operator with a mistyped path looking for files
                            // that were never going to appear.
                            Text(
                                text = when {
                                    lottieFolder.isEmpty() ->
                                        stringResource(Res.string.no_directory_selected)
                                    lottieFilesOrNull == null ->
                                        stringResource(Res.string.scanning_directory)
                                    else -> stringResource(Res.string.no_lottie_files)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                } else {
                    items(lottieFiles) { file ->
                    LowerThirdFileRow(file)
                    }
                }
            }
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(listState)
            )
        }

        RaisedButton(
            onClick = {
                onOpenLottieGen(appSettings.streamingSettings.lowerThirdFolder) {
                    scope.launch { refreshKey++ }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            shape = AppShape(8.dp)
        ) {
            Text(stringResource(Res.string.generate_lower_third), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun LowerThirdTabScope.LowerThirdFileRow(file: File) {
    val isSelected = selectedFile?.absolutePath == file.absolutePath
    val confirmTitle = stringResource(Res.string.confirm_delete)
    val confirmMsg = stringResource(Res.string.confirm_delete_file, file.name)
    val (rowHover, rowHovered) = rememberRowHover()
    val rowColors = bibleRowColors(isSelected, rowHovered)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = rowSpan(LOWER_THIRD_LIST_ROW_HEIGHT))
            .clip(BibleListRowShape)
            .background(rowColors.background)
            .hoverable(rowHover)
            .finalPassClickable { selectedFile = file; isPlaying = false }
            .padding(start = 10.dp, end = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // The full name on hover: a long one is ellipsized in the row.
            Box(modifier = Modifier.weight(1f)) {
                Tooltip(file.nameWithoutExtension) {
                    Text(
                        text = file.nameWithoutExtension,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = rowColors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                painter = painterResource(Res.drawable.ic_close),
                contentDescription = stringResource(Res.string.tooltip_remove),
                modifier = Modifier.size(14.dp).initialPassClickable {
                    SwingUtilities.invokeLater {
                        val result = JOptionPane.showConfirmDialog(
                            Window.getWindows().firstOrNull { it.isActive },
                            confirmMsg, confirmTitle,
                            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
                        )
                        if (result == JOptionPane.YES_OPTION) {
                            file.delete()
                            if (selectedFile?.absolutePath == file.absolutePath) selectedFile = null
                            refreshKey++
                        }
                    }
                },
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}

/** Resizes the list, and saves the width when the drag ends. */
@Composable
private fun LowerThirdTabScope.LowerThirdListDragHandle() {
    DragHandle(
        onDragEnd = {
            val newWidthDp = with(density) { listWidthPx.toDp().value.toInt() }
            onSettingsChangeState.value { s ->
                if (isMaximized) s.copy(maximizedLayout = s.maximizedLayout.copy(lowerThirdListWidthDp = newWidthDp))
                else s.copy(windowedLayout = s.windowedLayout.copy(lowerThirdListWidthDp = newWidthDp))
            }
        },
    ) { delta ->
        listWidthPx = (listWidthPx + delta)
            .coerceIn(
                with(density) { 100.dp.toPx() },
                with(density) { 600.dp.toPx() }
            )
    }
}

@Composable
private fun LowerThirdTabScope.LowerThirdPreviewCard(previewOutput: PreviewOutput, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxWidth()
            .padding(end = 4.dp, bottom = 4.dp)
            .bibleListCard()
    ) {
        // ── ATEM upload status ────────────────────────────────────
        val upload = remoteUpload
        if (upload != null && upload.error == null) {
            val uploadingMsg = if (upload.processing) stringResource(Res.string.atem_processing, upload.name)
                else if (upload.clip) stringResource(Res.string.atem_uploading_video, upload.name, upload.slot)
                else stringResource(Res.string.atem_uploading_image, upload.name, upload.slot)
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    uploadingMsg,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                LinearProgressIndicator(progress = { upload.progress }, modifier = Modifier.fillMaxWidth())
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        val err = upload?.error
        if (err != null) {
            Text(
                stringResource(Res.string.atem_upload_error, err),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        // ── Lottie preview ────────────────────────────────────────
        PreviewOutputPicker(
            settings = appSettings,
            tabId = Constants.PREVIEW_TAB_LOWER_THIRD,
            mode = Presenting.LOWER_THIRD,
            onSettingsChange = onSettingsChange,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp), contentAlignment = Alignment.Center) {
            Box(
                // The ratio alone, against the loose area around it: it takes the largest box of
                // that shape that fits both ways. A fillMaxSize in front pins the minimum to the
                // whole area, so a portrait output kept the full width and ran off the top and
                // bottom, over the tab bar.
                modifier = Modifier
                    .aspectRatio(previewOutput.size.aspectRatio)
                    .testTag(LOWER_THIRD_PREVIEW_TAG)
                    .background(Color.Black, AppShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (canPlay) {
                    Image(painter = rememberLottiePainter(
                        composition = composition,
                        progress = { animatedProgress.value },
                        fontManager = LottieFonts,
                        enableTextGrouping = groupsText,
                    ), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                } else if (selectedFile != null && isCompositionLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                } else if (selectedFile != null) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}
