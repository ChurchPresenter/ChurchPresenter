package org.churchpresenter.converter.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Transform
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.KeyButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.bibleformats.SpbVersePatcher
import org.churchpresenter.bibleformats.XmlToSpbConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// =============================================================================
// Bible Tab
// =============================================================================

@Composable
fun BibleConverterTab(onConverted: (sourceId: String) -> Unit = {}) {
    val pickers = LocalConverterPickers.current
    var inputFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var outputDir by remember { mutableStateOf<File?>(null) }
    var logMessages by remember { mutableStateOf<List<String>>(emptyList()) }
    var state by remember { mutableStateOf(ConvertState.SELECT) }
    var previewItems by remember { mutableStateOf<List<PreviewItem>>(emptyList()) }
    val scope = rememberCoroutineScope()
    var spbFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var fixState by remember { mutableStateOf(ConvertState.SELECT) }
    var fixLog by remember { mutableStateOf<List<String>>(emptyList()) }

    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        // fillMaxHeight, not fillMaxSize: the latter pins min width to the parent too, which would
        // override the max below and leave the cards spanning the whole window.
        modifier = Modifier.fillMaxHeight().widthIn(max = CONTENT_MAX_WIDTH)
            .verticalScroll(scrollState).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      SectionCard(Strings.bibleTitle, Strings.bibleDesc) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RaisedButton(shape = ButtonShape, onClick = {
                val files = pickers.files(Strings.xmlBibleFiles, "xml", multiSelection = true)
                if (files.isNotEmpty()) {
                    inputFiles = files; state = ConvertState.SELECT; previewItems = emptyList(); logMessages = emptyList()
                }
            }) {
                Icon(Icons.Default.FileOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(Strings.selectXmlFiles)
            }
            KeyButton(shape = ButtonShape, onClick = {
                val dir = pickers.directory()
                if (dir != null) {
                    val files = findXmlFilesRecursive(dir)
                    inputFiles = files; state = ConvertState.SELECT; previewItems = emptyList(); logMessages = emptyList()
                }
            }) {
                Icon(Icons.Default.Folder, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(Strings.selectFolder)
            }
            if (inputFiles.isNotEmpty()) {
                Text(Strings.fileCount(inputFiles.size), style = MaterialTheme.typography.bodySmall)
            }
        }

        DestinationRow(
            path = outputDir?.absolutePath ?: Strings.sameAsInput,
            chosen = outputDir != null,
            warning = null,
            onBrowse = { pickers.directory()?.let { outputDir = it } }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                ConvertState.SELECT -> {
                    KeyButton(shape = ButtonShape, onClick = {
                        previewItems = buildBiblePreview(inputFiles, outputDir); state = ConvertState.PREVIEW
                    }, enabled = inputFiles.isNotEmpty()) {
                        Icon(Icons.Default.Preview, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(Strings.preview)
                    }
                    RaisedButton(shape = ButtonShape, onClick = {
                        state = ConvertState.CONVERTING
                        scope.launch {
                            logMessages = withContext(Dispatchers.IO) {
                                inputFiles.map { file ->
                                    try {
                                        val outDir = outputDir ?: file.parentFile
                                        val outFile = File(outDir, file.nameWithoutExtension + ".spb")
                                        XmlToSpbConverter.convert(file, outFile)
                                        "OK: ${file.name} -> ${outFile.name}"
                                    } catch (e: Exception) { "ERROR: ${file.name} - ${e.message}" }
                                }
                            }
                            if (logMessages.anyConverted()) onConverted(BIBLE_CONVERSION)
                            state = ConvertState.DONE
                        }
                    }, enabled = inputFiles.isNotEmpty()) {
                        Icon(Icons.Default.Transform, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(Strings.convert)
                    }
                }
                ConvertState.PREVIEW -> {
                    RaisedButton(shape = ButtonShape, onClick = {
                        state = ConvertState.CONVERTING
                        scope.launch {
                            logMessages = withContext(Dispatchers.IO) {
                                inputFiles.map { file ->
                                    try {
                                        val outDir = outputDir ?: file.parentFile
                                        val outFile = File(outDir, file.nameWithoutExtension + ".spb")
                                        XmlToSpbConverter.convert(file, outFile)
                                        "OK: ${file.name} -> ${outFile.name}"
                                    } catch (e: Exception) { "ERROR: ${file.name} - ${e.message}" }
                                }
                            }
                            if (logMessages.anyConverted()) onConverted(BIBLE_CONVERSION)
                            state = ConvertState.DONE
                        }
                    }) {
                        Icon(Icons.Default.Transform, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(Strings.convertNFiles(inputFiles.size))
                    }
                    KeyButton(shape = ButtonShape, onClick = { state = ConvertState.SELECT; previewItems = emptyList() }) { Text(Strings.back) }
                }
                ConvertState.CONVERTING -> {
                    RaisedButton(shape = ButtonShape, enabled = false, onClick = {}) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp)); Text(Strings.converting)
                    }
                }
                ConvertState.DONE -> {
                    KeyButton(shape = ButtonShape, onClick = {
                        state = ConvertState.SELECT; inputFiles = emptyList(); previewItems = emptyList(); logMessages = emptyList()
                    }) { Text(Strings.startOver) }
                }
            }
        }

        when (state) {
            ConvertState.PREVIEW -> {
                Text(Strings.previewLabel, style = MaterialTheme.typography.titleSmall)
                previewItems.forEach { item -> PreviewRow(item) }
            }
            ConvertState.DONE -> {
                val ok = logMessages.count { it.startsWith("OK") }; val err = logMessages.count { it.startsWith("ERROR") }
                Text(Strings.doneConverted(ok, err), style = MaterialTheme.typography.titleSmall,
                    color = if (err > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                logMessages.forEach { msg -> LogLine(msg) }
            }
            else -> {}
        }
      }

      SectionCard(Strings.fixVersesTitle, Strings.fixVersesDesc) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RaisedButton(shape = ButtonShape, onClick = {
                val files = pickers.files(Strings.spbBibleFiles, "spb", multiSelection = true)
                if (files.isNotEmpty()) {
                    spbFiles = files; fixState = ConvertState.SELECT; fixLog = emptyList()
                }
            }) {
                Icon(Icons.Default.FileOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(Strings.selectSpbFiles)
            }

            when (fixState) {
                ConvertState.SELECT -> {
                    RaisedButton(shape = ButtonShape, onClick = {
                        fixState = ConvertState.CONVERTING
                        scope.launch {
                            fixLog = withContext(Dispatchers.IO) {
                                spbFiles.map { file ->
                                    try {
                                        val count = SpbVersePatcher.applyPatches(file)
                                        if (count > 0) "OK: ${file.name} \u2014 $count verse(s) patched"
                                        else "OK: ${file.name} \u2014 no patches needed"
                                    } catch (e: Exception) { "ERROR: ${file.name} \u2014 ${e.message}" }
                                }
                            }
                            fixState = ConvertState.DONE
                        }
                    }, enabled = spbFiles.isNotEmpty()) {
                        Icon(Icons.Default.Build, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(Strings.fixVerses)
                    }
                }
                ConvertState.CONVERTING -> {
                    RaisedButton(shape = ButtonShape, enabled = false, onClick = {}) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp)); Text(Strings.fixingVerses)
                    }
                }
                ConvertState.DONE -> {
                    KeyButton(shape = ButtonShape, onClick = {
                        fixState = ConvertState.SELECT; spbFiles = emptyList(); fixLog = emptyList()
                    }) { Text(Strings.startOver) }
                }
                ConvertState.PREVIEW -> {}
            }

            if (spbFiles.isNotEmpty()) {
                Text(Strings.fileCount(spbFiles.size), style = MaterialTheme.typography.bodySmall)
            }
        }

        if (fixState == ConvertState.DONE && fixLog.isNotEmpty()) {
            val fixedCount = fixLog.count { it.startsWith("OK") && it.contains("patched") }
            val errCount = fixLog.count { it.startsWith("ERROR") }
            Text(
                Strings.doneFixed(fixedCount, errCount),
                style = MaterialTheme.typography.titleSmall,
                color = if (errCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            fixLog.forEach { msg -> LogLine(msg) }
        }
      }
    }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState)
        )
    }
}
