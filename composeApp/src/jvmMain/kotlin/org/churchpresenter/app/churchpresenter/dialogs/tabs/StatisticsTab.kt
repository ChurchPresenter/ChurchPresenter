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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.clear_statistics
import churchpresenter.composeapp.generated.resources.top_songs
import churchpresenter.composeapp.generated.resources.top_verses
import org.churchpresenter.app.churchpresenter.data.SongDisplayEntry
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import org.churchpresenter.app.churchpresenter.data.VerseDisplayEntry
import org.jetbrains.compose.resources.stringResource

@Composable
fun StatisticsTab(
    statisticsManager: StatisticsManager
) {
    var topSongsBySongbook by remember { mutableStateOf(statisticsManager.getTopSongsBySongbook()) }
    var topVersesByBible by remember { mutableStateOf(statisticsManager.getTopVersesByBible()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(start = 15.dp, end = 15.dp, top = 8.dp, bottom = 15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Songs — one section per songbook
            if (topSongsBySongbook.isEmpty()) {
                Text(
                    text = stringResource(Res.string.top_songs),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                for ((songbook, songs) in topSongsBySongbook) {
                    val header = if (songbook.isNotEmpty()) {
                        "${stringResource(Res.string.top_songs)} ($songbook)"
                    } else {
                        stringResource(Res.string.top_songs)
                    }
                    Text(
                        text = header,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
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

            // Top Verses — one section per Bible
            if (topVersesByBible.isEmpty()) {
                Text(
                    text = stringResource(Res.string.top_verses),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                for ((bibleName, verses) in topVersesByBible) {
                    val header = if (bibleName.isNotEmpty()) {
                        "${stringResource(Res.string.top_verses)} ($bibleName)"
                    } else {
                        stringResource(Res.string.top_verses)
                    }
                    Text(
                        text = header,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
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

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    statisticsManager.clearStatistics()
                    topSongsBySongbook = statisticsManager.getTopSongsBySongbook()
                    topVersesByBible = statisticsManager.getTopVersesByBible()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(stringResource(Res.string.clear_statistics))
            }
        }
    }
}
