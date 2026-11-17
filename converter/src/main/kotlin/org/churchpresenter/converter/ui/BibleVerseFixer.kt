package org.churchpresenter.converter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.bibleformats.SpbChanges
import org.churchpresenter.bibleformats.SpbRowEdit
import org.churchpresenter.bibleformats.SpbRowEditor
import org.churchpresenter.bibleformats.SpbRowView
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.RaisedButton
import java.io.File

private val LineNumberWidth = 52.dp
private val IdFieldWidth = 150.dp
private val ReferenceWidth = 84.dp

/** How many verses either side of a damaged one stay in view when only the damaged ones are shown. */
private const val CONTEXT_ROWS = 3

private val ReferencePattern = Regex("""\d+:\d+""")

/** One verse in the editor: a row of the file as it was, or one the person added. */
private class WorkRow(val key: Long, val original: SpbRowView?, val book: String, reference: String) {
    var id by mutableStateOf(original?.id.orEmpty())
    var text by mutableStateOf(original?.text.orEmpty())

    /** `chapter:verse` as displayed. Editable on an added row only. */
    var reference by mutableStateOf(reference)

    val isNew: Boolean get() = original == null
    val isChanged: Boolean get() = original == null || id != original.id || text != original.text
    val isDamaged: Boolean get() = !SpbRowEditor.isValidId(id)

    /** Whether saving this row would write something the app can read. */
    val isSavable: Boolean
        get() = when {
            original == null -> SpbRowEditor.isValidId(id) && ReferencePattern.matches(reference)
            id == original.id -> true
            else -> SpbRowEditor.isValidId(id)
        }
}

/** The file as the editor holds it: the rows in order, and which of the file's rows were removed. */
private class FixerDocument(rows: List<SpbRowView>, val bookNames: Map<Int, String>) {
    val work = mutableStateListOf<WorkRow>()
    val deletedLines = mutableStateListOf<Int>()
    private var nextKey = 0L

    init {
        rows.forEach { work += WorkRow(nextKey++, it, it.book, "${it.chapter}:${it.verse}") }
    }

    val hasChanges: Boolean
        get() = deletedLines.isNotEmpty() || work.any { it.isChanged }

    val isSavable: Boolean get() = work.all { it.isSavable }

    fun remove(row: WorkRow) {
        work.remove(row)
        row.original?.let { deletedLines += it.lineNumber }
    }

    /** Adds an empty verse below [row], numbered as the verse after it where that can be told. */
    fun addBelow(row: WorkRow) {
        val chapter = row.reference.substringBefore(':')
        val next = row.reference.substringAfter(':').toIntOrNull()?.plus(1)
        val added = WorkRow(nextKey++, null, row.book, "$chapter:${next ?: ""}")
        added.id = nextId(row.id)
        work.add(work.indexOf(row) + 1, added)
    }

    /** The id one verse on from [id], or blank when [id] is not one to count from. */
    private fun nextId(id: String): String {
        val verse = id.takeIf { SpbRowEditor.isValidId(it) }?.takeLast(VERSE_DIGITS)?.toIntOrNull() ?: return ""
        return id.dropLast(VERSE_DIGITS) + (verse + 1).toString().padStart(VERSE_DIGITS, '0')
    }

    fun toChanges(): SpbChanges {
        val edits = mutableMapOf<Int, SpbRowEdit>()
        val inserted = mutableMapOf<Int, MutableList<SpbRowView>>()
        var anchor = 0
        work.forEach { row ->
            val original = row.original
            if (original == null) {
                val (chapter, verse) = row.reference.split(':', limit = 2)
                inserted.getOrPut(anchor) { mutableListOf() } += SpbRowView(0, row.id, row.book, chapter, verse, row.text)
            } else {
                anchor = original.lineNumber
                val idChanged = row.id != original.id
                val textChanged = row.text != original.text
                if (idChanged || textChanged) {
                    edits[anchor] = SpbRowEdit(id = row.id.takeIf { idChanged }, text = row.text.takeIf { textChanged })
                }
            }
        }
        return SpbChanges(edits, deletedLines.toSet(), inserted)
    }

    private companion object {
        const val VERSE_DIGITS = 3
    }
}

/**
 * The Bible verse fixer's window body: every verse of a `.spb` on its own line, like a text editor.
 * Each id and text can be changed, a verse can be added below any line or removed, and the book
 * and chapter at the top of the list follow the scroll. A verse whose id the app cannot read is
 * marked.
 *
 * Opened from the Developer menu. [onSaved] fires after a save that changed the file, so the app
 * can say the Bible has to be reloaded.
 */
@Composable
fun BibleVerseFixerApp(
    /** The Bibles already set in settings, offered first; a file that is gone is left out. */
    bibleFiles: List<File> = emptyList(),
    /** Asks the person for a file of their own — the app's own chooser; null when they cancel. */
    chooseFile: suspend () -> File? = { null },
    initialFile: File? = null,
    onSaved: (changed: Int) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var file by remember { mutableStateOf(initialFile) }
    var document by remember { mutableStateOf<FixerDocument?>(null) }
    var reloads by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(file, reloads) {
        document = file?.let { f ->
            withContext(Dispatchers.IO) { FixerDocument(SpbRowEditor.readRows(f), SpbRowEditor.readBookNames(f)) }
        }
    }
    val current = document

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(Strings.verseFixerTitle, Strings.verseFixerDesc) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BiblePicker(file, bibleFiles, onChosen = { file = it; message = null }, chooseFile = chooseFile)
                    RaisedButton(
                        shape = ButtonShape,
                        enabled = current != null && current.hasChanges && current.isSavable,
                        onClick = {
                            saveChanges(scope, file, current, onSaved, onMessage = { message = it }, onSaved = { reloads++ })
                        },
                    ) {
                        Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                        Text(Strings.verseFixerApply, Modifier.padding(start = 6.dp))
                    }
                    KeyButton(shape = ButtonShape, enabled = current?.hasChanges == true, onClick = { reloads++ }) {
                        Text(Strings.verseFixerDiscard)
                    }
                }
                message?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (file != null && current != null) VerseEditor(file?.name.orEmpty(), current)
        }
    }
}

private fun saveChanges(
    scope: CoroutineScope,
    file: File?,
    document: FixerDocument?,
    onChanged: (Int) -> Unit,
    onMessage: (String) -> Unit,
    onSaved: () -> Unit,
) {
    val target = file ?: return
    val changes = document?.toChanges() ?: return
    scope.launch {
        val changed = withContext(Dispatchers.IO) { SpbRowEditor.applyChanges(target, changes) }
        onMessage(Strings.verseFixerSaved(changed))
        onChanged(changed)
        onSaved()
    }
}

/** The Bible to fix: one already set in settings, or a file of the person's own. */
@Composable
private fun BiblePicker(
    selected: File?,
    bibleFiles: List<File>,
    onChosen: (File) -> Unit,
    chooseFile: suspend () -> File?,
) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    Box {
        KeyButton(shape = ButtonShape, onClick = { open = true }) {
            Icon(Icons.Default.FileOpen, null, Modifier.size(18.dp))
            Text(selected?.name ?: Strings.verseFixerSelectFile, Modifier.padding(horizontal = 6.dp))
            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            bibleFiles.forEach { bible ->
                DropdownMenuItem(text = { Text(bible.name) }, onClick = { open = false; onChosen(bible) })
            }
            if (bibleFiles.isNotEmpty()) HorizontalDivider()
            DropdownMenuItem(
                text = { Text(Strings.verseFixerChooseOwn) },
                onClick = {
                    open = false
                    scope.launch { chooseFile()?.let(onChosen) }
                },
            )
        }
    }
}

/** The verses in a scrolling list under a header that names the book and chapter at the top of it. */
@Composable
private fun VerseEditor(fileName: String, document: FixerDocument) {
    var focus by remember(document) { mutableStateOf<Set<Long>?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val damaged by remember(document) { derivedStateOf { document.work.count { it.isDamaged } } }
    val focused = focus
    val shown = if (focused == null) document.work else document.work.filter { it.isNew || it.key in focused }
    val top by remember(shown) { derivedStateOf { shown.getOrNull(listState.firstVisibleItemIndex) } }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (damaged == 0) Strings.verseFixerNone else Strings.verseFixerFound(damaged, fileName),
            style = MaterialTheme.typography.titleSmall,
        )
        KeyButton(shape = ButtonShape, enabled = damaged > 0, onClick = {
            jumpToNextDamaged(scope, listState, shown)
        }) { Text(Strings.verseFixerNextDamaged) }
        KeyButton(shape = ButtonShape, onClick = { focus = if (focused == null) aroundDamaged(document.work) else null }) {
            Text(if (focused == null) Strings.verseFixerShowDamaged else Strings.verseFixerShowAll)
        }
    }
    top?.let { BookHeader(document, it) }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(shown, key = { it.key }) { row ->
            VerseEditRow(row, onAdd = { document.addBelow(row) }, onRemove = { document.remove(row) })
        }
    }
}

/** The book and chapter of the verse at the top of the list, in the file's own words for the book. */
@Composable
private fun BookHeader(document: FixerDocument, row: WorkRow) {
    val name = document.bookNames[row.book.toIntOrNull()] ?: row.book
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = ButtonShape, modifier = Modifier.fillMaxWidth()) {
        Text(
            Strings.verseFixerHeader(name, row.reference.substringBefore(':')),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** Every damaged row's key with the keys of [CONTEXT_ROWS] rows either side of it. */
private fun aroundDamaged(rows: List<WorkRow>): Set<Long> {
    val keys = mutableSetOf<Long>()
    rows.forEachIndexed { i, row ->
        if (row.isDamaged) {
            for (j in (i - CONTEXT_ROWS).coerceAtLeast(0)..(i + CONTEXT_ROWS).coerceAtMost(rows.lastIndex)) keys += rows[j].key
        }
    }
    return keys
}

/** Scrolls to the first damaged row below the one at the top, wrapping to the first of all. */
private fun jumpToNextDamaged(scope: CoroutineScope, state: LazyListState, shown: List<WorkRow>) {
    val top = state.firstVisibleItemIndex
    val target = shown.indices.firstOrNull { it > top && shown[it].isDamaged } ?: shown.indexOfFirst { it.isDamaged }
    if (target >= 0) scope.launch { state.animateScrollToItem(target) }
}

/** One verse on one line: line number, id, reference, text, and the buttons to add below or remove. */
@Composable
private fun VerseEditRow(row: WorkRow, onAdd: () -> Unit, onRemove: () -> Unit) {
    val tint = when {
        row.isDamaged -> MaterialTheme.colorScheme.errorContainer
        row.isChanged -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = Modifier.fillMaxWidth().background(tint).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            row.original?.lineNumber?.toString() ?: Strings.verseFixerNew,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(LineNumberWidth),
        )
        ConverterTextField(
            value = row.id,
            onValueChange = { row.id = it.trim().uppercase() },
            modifier = Modifier.width(IdFieldWidth),
        )
        if (row.isNew) {
            ConverterTextField(
                value = row.reference,
                onValueChange = { row.reference = it.trim() },
                modifier = Modifier.width(ReferenceWidth),
            )
        } else {
            Text(
                row.reference,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(ReferenceWidth),
            )
        }
        ConverterTextField(value = row.text, onValueChange = { row.text = it }, modifier = Modifier.weight(1f))
        if (row.isDamaged) SuggestionButton(row)
        IconButton(onClick = onAdd, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Add, Strings.verseFixerAdd, Modifier.size(18.dp))
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Delete, Strings.verseFixerRemove, Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SuggestionButton(row: WorkRow) {
    val original = row.original ?: return
    val suggestion = SpbRowEditor.suggestedId(original)
    if (suggestion != null && suggestion != row.id) {
        KeyButton(shape = ButtonShape, onClick = { row.id = suggestion }) {
            Text("${Strings.verseFixerUseSuggestion}: $suggestion")
        }
    }
}
