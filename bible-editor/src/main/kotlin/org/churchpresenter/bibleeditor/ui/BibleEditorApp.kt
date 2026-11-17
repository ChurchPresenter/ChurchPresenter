package org.churchpresenter.bibleeditor.ui

import org.churchpresenter.bibleedits.BibleEditsStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import org.churchpresenter.bibleeditor.BibleEditorState
import org.churchpresenter.bibleeditor.chapterRows
import org.churchpresenter.bibleeditor.openMissing
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.bible_editor_all_saved
import org.churchpresenter.strings.generated.resources.bible_editor_close
import org.churchpresenter.strings.generated.resources.bible_editor_discard
import org.churchpresenter.strings.generated.resources.bible_editor_loading
import org.churchpresenter.strings.generated.resources.bible_editor_no_bibles
import org.churchpresenter.strings.generated.resources.bible_editor_no_changes
import org.churchpresenter.strings.generated.resources.bible_editor_save_changes_one
import org.churchpresenter.strings.generated.resources.bible_editor_save_changes_other
import org.churchpresenter.strings.generated.resources.bible_editor_saved
import org.churchpresenter.strings.generated.resources.bible_editor_unsaved_one
import org.churchpresenter.strings.generated.resources.bible_editor_unsaved_other
import org.jetbrains.compose.resources.stringResource
import java.io.File

private const val CLOCK_TICK_MS = 30_000L

/**
 * The Bible editor: restore the verses a translation is missing and fix its typing mistakes.
 *
 * Opens every `.spb` in [bibleFolder], on the first of [preferred] it finds there. The files are only
 * read: every change goes to a history kept in [dataFolder], which is where the edits live.
 */
@Composable
fun BibleEditorApp(
    bibleFolder: File,
    dataFolder: File,
    onClose: () -> Unit,
    preferred: List<File> = emptyList(),
    /** After each save has reached the disk, for the app to load the corrections into the Bible tab. */
    onSaved: () -> Unit = {},
    io: CoroutineDispatcher = Dispatchers.IO,
    /** What time it is, for the history's "12 min ago"; a test pins it. */
    clock: () -> Long = System::currentTimeMillis,
    /** Whether "12 min ago" moves on by itself while the window is open. */
    tickClock: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val state = remember(bibleFolder, dataFolder) {
        BibleEditorState(bibleFolder, preferred, BibleEditsStore(dataFolder), scope, io, onSaved, clock)
    }
    LaunchedEffect(state) { state.start() }
    var now by remember { mutableLongStateOf(clock()) }
    LaunchedEffect(tickClock) {
        while (tickClock) {
            delay(CLOCK_TICK_MS)
            now = clock()
        }
    }
    LaunchedEffect(state.history.entries.size) { now = clock() }
    val colors = editorColors()
    Box(Modifier.fillMaxSize().background(colors.shell)) {
        when {
            state.loadingShelf -> Notice(stringResource(Res.string.bible_editor_loading))
            state.shelf.isEmpty() -> Notice(stringResource(Res.string.bible_editor_no_bibles, bibleFolder.path))
            else -> EditorBody(state, now, onClose)
        }
        if (state.missingOpen) MissingVersesDialog(state)
    }
}

@Composable
private fun EditorBody(state: BibleEditorState, now: Long, onClose: () -> Unit) {
    val colors = editorColors()
    val rows = state.chapterRows()
    val openMissing = state.openMissing.size
    Column(Modifier.fillMaxSize()) {
        EditorHeader(state, openMissing)
        Divider()
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                ChapterView(state, rows, now, Modifier.weight(1f))
                Divider()
                EditorFooter(state, now, onClose)
            }
            if (state.historyOpen) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(colors.line))
                HistoryPanel(state, now)
            }
        }
    }
}

@Composable
private fun EditorFooter(state: BibleEditorState, now: Long, onClose: () -> Unit) {
    val colors = editorColors()
    val unsaved = state.history.unsavedCount
    val lastSaved = state.history.entries.getOrNull(state.history.savedCount - 1)
    Row(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(if (unsaved > 0) colors.warn else colors.good, size = 8.dp)
        Spacer(Modifier.size(10.dp))
        Text(
            when {
                unsaved > 0 -> countString(unsaved, Res.string.bible_editor_unsaved_one,
                    Res.string.bible_editor_unsaved_other)
                lastSaved != null -> stringResource(Res.string.bible_editor_all_saved, stamp(lastSaved.time, now))
                else -> stringResource(Res.string.bible_editor_no_changes)
            },
            Modifier.weight(1f), fontSize = 12.5.sp, color = colors.dim,
        )
        EditorButton(
            stringResource(Res.string.bible_editor_discard), onClick = {
                state.clearEditing()
                state.history.discardUnsaved()
            },
            tone = ButtonTone.QUIET, enabled = unsaved > 0,
        )
        Spacer(Modifier.size(10.dp))
        EditorButton(stringResource(Res.string.bible_editor_close), onClose)
        Spacer(Modifier.size(10.dp))
        EditorButton(
            if (unsaved > 0) countString(unsaved, Res.string.bible_editor_save_changes_one,
                Res.string.bible_editor_save_changes_other)
            else stringResource(Res.string.bible_editor_saved),
            onClick = state::save, tone = ButtonTone.PRIMARY, enabled = unsaved > 0,
        )
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(editorColors().line))
}

@Composable
private fun Notice(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = editorColors().dim, fontSize = 13.sp)
    }
}
