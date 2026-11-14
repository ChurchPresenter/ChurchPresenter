package org.churchpresenter.converter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.converter.library.DuplicateFinder
import org.churchpresenter.converter.library.SongAliases
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.RaisedButton
import java.io.File

/**
 * Sets every song's alias in a folder from one pattern, `{n}` standing for each song's number.
 *
 * The folder is read once when it is picked; typing the pattern only recomputes the new aliases,
 * so the preview keeps up with every keystroke on a large library.
 */
@Composable
fun SongAliasTab() {
    val pickers = LocalConverterPickers.current
    val scope = rememberCoroutineScope()
    var directory by remember { mutableStateOf<File?>(null) }
    var pattern by remember { mutableStateOf("") }
    var scanned by remember { mutableStateOf<List<SongAliases.Entry>>(emptyList()) }
    var working by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    fun rescan(dir: File) {
        working = true
        scope.launch {
            scanned = withContext(Dispatchers.IO) {
                SongAliases.preview(dir, "", DuplicateFinder::readFileWithFallback)
            }
            working = false
        }
    }

    val entries = scanned.map { entry ->
        entry.copy(newAlias = entry.number.takeIf { it.isNotEmpty() }?.let { SongAliases.aliasFor(pattern, it) })
    }
    val changing = entries.filter { it.changes }

    Column(
        modifier = Modifier.fillMaxHeight().widthIn(max = CONTENT_MAX_WIDTH).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(Strings.aliasTitle, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Text(
            Strings.aliasDesc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RaisedButton(shape = ButtonShape, enabled = !working, onClick = {
                pickers.directory()?.let { dir ->
                    directory = dir
                    result = null
                    rescan(dir)
                }
            }) {
                Icon(Icons.Default.Folder, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(Strings.selectFolder)
            }
            directory?.let {
                Text(
                    it.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                Strings.aliasPattern,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ConverterTextField(
                value = pattern,
                onValueChange = { pattern = it; result = null },
                placeholder = Strings.aliasPatternHint,
                modifier = Modifier.width(240.dp),
            )
            Text(
                Strings.aliasPatternHelp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyButton(
                shape = ButtonShape,
                enabled = !working && changing.isNotEmpty(),
                onClick = {
                    val dir = directory ?: return@KeyButton
                    working = true
                    scope.launch {
                        val failed = withContext(Dispatchers.IO) { writeAliases(changing) }
                        result = Strings.aliasDone(changing.size - failed, failed)
                        working = false
                        rescan(dir)
                    }
                },
            ) {
                Text(if (pattern.isBlank()) Strings.aliasClearN(changing.size) else Strings.aliasApplyN(changing.size))
            }
            if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            result?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }

        if (directory != null && !working) {
            AliasPreview(entries, changing, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun AliasPreview(
    entries: List<SongAliases.Entry>,
    changing: List<SongAliases.Entry>,
    modifier: Modifier,
) {
    val noNumber = entries.count { it.newAlias == null }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            Strings.aliasSummary(changing.size, entries.size - changing.size - noNumber, noNumber),
            style = MaterialTheme.typography.titleSmall,
        )
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            LazyColumn(modifier = Modifier.padding(8.dp)) {
                items(changing) { entry -> AliasRow(entry) }
            }
        }
    }
}

@Composable
private fun AliasRow(entry: SongAliases.Entry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(entry.file.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(
            entry.oldAlias.ifEmpty { Strings.aliasNone },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            null,
            Modifier.size(14.dp).padding(horizontal = 4.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            entry.newAlias.orEmpty().ifEmpty { Strings.aliasNone },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(120.dp),
        )
    }
}

/** Writes each entry's new alias into its file; returns how many could not be written. */
private fun writeAliases(entries: List<SongAliases.Entry>): Int = entries.count { entry ->
    runCatching {
        val content = DuplicateFinder.readFileWithFallback(entry.file)
        entry.file.writeText(SongAliases.withAlias(content, entry.newAlias.orEmpty()))
    }.isFailure
}
