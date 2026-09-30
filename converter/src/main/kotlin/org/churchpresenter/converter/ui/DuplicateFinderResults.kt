package org.churchpresenter.converter.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import org.churchpresenter.theme.components.RaisedCheckbox
import androidx.compose.material3.Icon
import org.churchpresenter.theme.components.KeyIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import org.churchpresenter.theme.components.GhostButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.churchpresenter.converter.library.DuplicateGroup

/** The groups the scan found, each expandable to its songs. */
@Composable
internal fun DuplicateFinderState.DuplicateResultsPanel(modifier: Modifier) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        if (scanState != ScanState.DONE) {
            EmptyStatePanel(Icons.Default.ContentCopy, Strings.dupesEmptyState)
        } else {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        Strings.showingGroups(filteredGroups.size, duplicateGroups.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    // Select duplicates in same folder
                    GhostButton(shape = ButtonShape, onClick = {
                        val toMark = mutableSetOf<String>()
                        for (group in filteredGroups) {
                            // Group songs by folder, mark all but one per folder
                            val byFolder = group.songs.groupBy { it.file.parentFile.canonicalPath }
                            for ((_, songsInFolder) in byFolder) {
                                if (songsInFolder.size > 1) {
                                    songsInFolder.drop(1).forEach { toMark.add(it.file.canonicalPath) }
                                }
                            }
                        }
                        markedForDelete = markedForDelete + toMark
                    }, modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text(Strings.selectSameFolder, style = MaterialTheme.typography.labelSmall)
                    }
                    // Expand/Collapse all
                    GhostButton(shape = ButtonShape, onClick = {
                        expandedGroups = if (expandedGroups.size >= filteredGroups.size)
                            emptySet() else filteredGroups.indices.toSet()
                    }, modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text(
                            if (expandedGroups.size >= filteredGroups.size) Strings.collapseAll else Strings.expandAll,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                val listState = rememberLazyListState()
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(start = 8.dp, top = 0.dp, bottom = 8.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (deleteLog.isNotEmpty()) {
                            items(deleteLog.size, key = { "log_$it" }) { idx -> LogLine(deleteLog[idx]) }
                        }

                        filteredGroups.forEachIndexed { groupIdx, group ->
                            item(key = "header_$groupIdx") {
                            DuplicateGroupHeader(groupIdx, group)
                            }

                            if (groupIdx in expandedGroups) {
                                items(group.songs.size, key = { "song_${groupIdx}_$it" }) { songIdx ->
                                DuplicateSongCard(group, songIdx)
                                }
                            }
                        }
                    }
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(listState)
                    )
                }
            }
        }
    }
}

@Composable
private fun DuplicateFinderState.DuplicateGroupHeader(groupIdx: Int, group: DuplicateGroup) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        onClick = {
            expandedGroups = if (groupIdx in expandedGroups)
                expandedGroups - groupIdx else expandedGroups + groupIdx
        }
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (groupIdx in expandedGroups) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null, Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    Strings.groupHeader(groupIdx + 1, group.songs.first().title),
                    style = MaterialTheme.typography.bodyMedium
                )
                val avgSim = if (group.similarities.size > 1)
                    group.similarities.drop(1).average() else 1.0
                Text(
                    Strings.groupDetail(group.songs.size, group.reason, (avgSim * 100).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            KeyIconButton(shape = ButtonShape, onClick = { compareLeft = 0; compareRight = minOf(1, group.songs.size - 1); compareGroup = group }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.AutoMirrored.Filled.CompareArrows, "Compare", Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun DuplicateFinderState.DuplicateSongCard(group: DuplicateGroup, songIdx: Int) {
    val song = group.songs[songIdx]
    val canonPath = song.file.canonicalPath
    val isMarked = canonPath in markedForDelete
    val isKept = keepFolder != null &&
            canonPath.startsWith(keepFolder!!.canonicalPath)
    Card(
        modifier = Modifier.fillMaxWidth().padding(start = 28.dp).clickable {
            markedForDelete = if (isMarked) markedForDelete - canonPath
                else markedForDelete + canonPath
        },
        colors = CardDefaults.cardColors(
            containerColor = when {
                isMarked -> MaterialTheme.colorScheme.errorContainer
                isKept -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RaisedCheckbox(
                    checked = isMarked,
                    onCheckedChange = {
                        markedForDelete = if (isMarked) markedForDelete - canonPath
                            else markedForDelete + canonPath
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(song.file.name, style = MaterialTheme.typography.bodyMedium)
                if (isKept && !isMarked) {
                    Spacer(Modifier.width(6.dp))
                    Text(Strings.labelKeep, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                if (isMarked) {
                    Spacer(Modifier.width(6.dp))
                    Text(Strings.labelDelete, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            val simPercent = if (group.similarities.size > songIdx)
                "${(group.similarities[songIdx] * 100).toInt()}%" else ""
            Text(
                Strings.titlePrefix(song.title) + if (simPercent.isNotEmpty()) " \u2022 $simPercent" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 26.dp, top = 2.dp)
            )
            // Show sections and missing verses
            if (song.sections.isNotEmpty()) {
                val allSections = group.songs.flatMap { it.sections }.distinct()
                val missing = allSections - song.sections.toSet()
                Text(
                    Strings.sectionsPrefix(song.sections.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 26.dp, top = 2.dp)
                )
                if (missing.isNotEmpty()) {
                    Text(
                        Strings.missingPrefix(missing.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 26.dp, top = 1.dp)
                    )
                }
            }
            Text(
                song.file.absolutePath,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 26.dp, top = 2.dp)
            )
        }
    }
}
