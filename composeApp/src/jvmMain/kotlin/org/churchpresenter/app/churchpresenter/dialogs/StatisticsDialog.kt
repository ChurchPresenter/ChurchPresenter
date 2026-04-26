package org.churchpresenter.app.churchpresenter.dialogs

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.rememberDialogState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.clear_statistics
import churchpresenter.composeapp.generated.resources.close
import churchpresenter.composeapp.generated.resources.export_to_xls
import churchpresenter.composeapp.generated.resources.file_chooser_save_statistics
import churchpresenter.composeapp.generated.resources.file_filter_xls
import churchpresenter.composeapp.generated.resources.statistics
import churchpresenter.composeapp.generated.resources.statistics_exported_error
import churchpresenter.composeapp.generated.resources.statistics_exported_success
import churchpresenter.composeapp.generated.resources.top_songs
import churchpresenter.composeapp.generated.resources.top_verses
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import org.churchpresenter.app.churchpresenter.ui.theme.AppThemeWrapper
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.jetbrains.compose.resources.stringResource
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun StatisticsDialog(
    isVisible: Boolean,
    theme: ThemeMode,
    statisticsManager: StatisticsManager,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    val mainWindowState = LocalMainWindowState.current
    val coroutineScope = rememberCoroutineScope()

    var topSongsBySongbook by remember { mutableStateOf(statisticsManager.getTopSongsBySongbook()) }
    var topVersesByBible by remember { mutableStateOf(statisticsManager.getTopVersesByBible()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val successMsg = stringResource(Res.string.statistics_exported_success)
    val errorMsg = stringResource(Res.string.statistics_exported_error)
    val saveTitle = stringResource(Res.string.file_chooser_save_statistics)
    val filterDesc = stringResource(Res.string.file_filter_xls)

    Dialog(
        onCloseRequest = onDismiss,
        state = rememberDialogState(
            position = centeredOnMainWindow(mainWindowState, 700.dp, 600.dp),
            width = 700.dp,
            height = 600.dp
        ),
        title = stringResource(Res.string.statistics),
        resizable = true
    ) {
        AppThemeWrapper(theme = theme) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Content
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Top Songs
                            if (topSongsBySongbook.isEmpty()) {
                                Text(
                                    text = stringResource(Res.string.top_songs),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text("—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                for ((songbook, songs) in topSongsBySongbook) {
                                    val header = if (songbook.isNotEmpty())
                                        "${stringResource(Res.string.top_songs)} ($songbook)"
                                    else stringResource(Res.string.top_songs)

                                    Text(text = header, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(4.dp))

                                    songs.forEachIndexed { index, entry ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${index + 1}.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.width(28.dp)
                                            )
                                            Text(
                                                text = "#${entry.songNumber} ${entry.title}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "${entry.count}",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }

                            // Top Verses
                            if (topVersesByBible.isEmpty()) {
                                Text(
                                    text = stringResource(Res.string.top_verses),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text("—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                for ((bibleName, verses) in topVersesByBible) {
                                    val header = if (bibleName.isNotEmpty())
                                        "${stringResource(Res.string.top_verses)} ($bibleName)"
                                    else stringResource(Res.string.top_verses)

                                    Text(text = header, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(4.dp))

                                    verses.forEachIndexed { index, entry ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${index + 1}.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.width(28.dp)
                                            )
                                            Text(
                                                text = "${entry.bookName} ${entry.chapter}:${entry.verseNumber}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "${entry.count}",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        }
                    }

                    // Status message
                    if (statusMessage != null) {
                        Text(
                            text = statusMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (statusMessage == successMsg) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    // Bottom button row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Export to XLS
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    val file = withContext(Dispatchers.IO) {
                                        val chooser = JFileChooser().apply {
                                            dialogTitle = saveTitle
                                            fileSelectionMode = JFileChooser.FILES_ONLY
                                            fileFilter = FileNameExtensionFilter(filterDesc, "xls")
                                            selectedFile = File("statistics.xls")
                                        }
                                        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                                            var f = chooser.selectedFile
                                            if (!f.name.endsWith(".xls", ignoreCase = true)) {
                                                f = File(f.absolutePath + ".xls")
                                            }
                                            f
                                        } else null
                                    }
                                    if (file != null) {
                                        val ok = withContext(Dispatchers.IO) {
                                            statisticsManager.exportStatisticsToXls(file)
                                        }
                                        statusMessage = if (ok) successMsg else errorMsg
                                    }
                                }
                            }
                        ) {
                            Text(stringResource(Res.string.export_to_xls))
                        }

                        // Clear Statistics
                        Button(
                            onClick = {
                                statisticsManager.clearStatistics()
                                topSongsBySongbook = statisticsManager.getTopSongsBySongbook()
                                topVersesByBible = statisticsManager.getTopVersesByBible()
                                statusMessage = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text(stringResource(Res.string.clear_statistics))
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Close
                        Button(onClick = onDismiss) {
                            Text(stringResource(Res.string.close))
                        }
                    }
                }
            }
        }
    }
}

