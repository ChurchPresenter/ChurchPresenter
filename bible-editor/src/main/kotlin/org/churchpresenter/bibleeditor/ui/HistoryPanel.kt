package org.churchpresenter.bibleeditor.ui

import org.churchpresenter.bibleedits.EditEntry
import org.churchpresenter.bibleedits.kind
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.bibleeditor.BibleEditorState
import org.churchpresenter.bibleeditor.HistoryScope
import org.churchpresenter.bibleeditor.bookNameIn
import org.churchpresenter.bibleeditor.textOf
import org.churchpresenter.bibleeditor.visibleHistory
import org.churchpresenter.bibleeditor.wordDiff
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.icons.generated.resources.ic_undo
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.bible_editor_already_reverted
import org.churchpresenter.strings.generated.resources.bible_editor_close_history
import org.churchpresenter.strings.generated.resources.bible_editor_go_to_verse
import org.churchpresenter.strings.generated.resources.bible_editor_history
import org.churchpresenter.strings.generated.resources.bible_editor_history_empty_body
import org.churchpresenter.strings.generated.resources.bible_editor_history_empty_title
import org.churchpresenter.strings.generated.resources.bible_editor_not_saved
import org.churchpresenter.strings.generated.resources.bible_editor_only_verse
import org.churchpresenter.strings.generated.resources.bible_editor_revert
import org.churchpresenter.strings.generated.resources.bible_editor_scope_all
import org.churchpresenter.strings.generated.resources.bible_editor_scope_bible
import org.churchpresenter.strings.generated.resources.bible_editor_scope_chapter
import org.churchpresenter.strings.generated.resources.bible_editor_show_all
import org.churchpresenter.strings.generated.resources.bible_editor_undid
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.icons.generated.resources.Res as IconRes

private val ItemShape = RoundedCornerShape(12.dp)

/** The side panel: every change in this chapter, this Bible or all of them, newest first. */
@Composable
internal fun HistoryPanel(state: BibleEditorState, now: Long, modifier: Modifier = Modifier) {
    val colors = editorColors()
    val shown = state.visibleHistory()
    Column(modifier.width(370.dp).fillMaxHeight().background(colors.side)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.bible_editor_history), Modifier.weight(1f), fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold, color = colors.ink,
                )
                EditorIconButton(
                    painterResource(IconRes.drawable.ic_close), stringResource(Res.string.bible_editor_close_history),
                    onClick = { state.historyOpen = false }, iconSize = 11.dp,
                )
            }
            SegmentTrack(
                listOf(
                    stringResource(Res.string.bible_editor_scope_chapter),
                    stringResource(Res.string.bible_editor_scope_bible),
                    stringResource(Res.string.bible_editor_scope_all),
                ),
                selected = state.historyScope.ordinal,
                onSelect = { index ->
                    state.historyScope = HistoryScope.entries[index]
                    if (state.historyScope != HistoryScope.CHAPTER) state.historyVerse = null
                },
                modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
            )
            // Switching scope clears it, so a verse is only ever set while the chapter is listed.
            state.historyVerse?.let { VerseFilter(state, it) }
        }
        if (shown.isEmpty()) {
            HistoryEmpty()
        } else {
            HistoryList(state, shown, now)
        }
    }
}

@Composable
private fun VerseFilter(state: BibleEditorState, verse: Int) {
    val colors = editorColors()
    val book = state.bookName
    Row(
        Modifier.padding(top = 10.dp).height(26.dp).clip(RoundedCornerShape(7.dp)).background(colors.accentBg)
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(Res.string.bible_editor_only_verse, "$book ${state.chapter}:$verse"),
            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.accent,
        )
        EditorIconButton(
            painterResource(IconRes.drawable.ic_close), stringResource(Res.string.bible_editor_show_all),
            onClick = { state.historyVerse = null }, tint = colors.accent, size = 20.dp, iconSize = 9.dp,
        )
    }
}

@Composable
private fun HistoryList(state: BibleEditorState, shown: List<EditEntry>, now: Long) {
    val groups = shown.groupBy { dayLabel(it.time, now) }
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp)) {
        groups.forEach { (day, entries) ->
            item(key = "day-$day") { Caption(day, Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp,
                bottom = 7.dp)) }
            items(entries, key = { it.id }) { entry ->
                Box(Modifier.padding(bottom = 7.dp)) { HistoryItem(state, entry, now) }
            }
        }
    }
}

@Composable
private fun HistoryItem(state: BibleEditorState, entry: EditEntry, now: Long) {
    val colors = editorColors()
    val unsaved = state.history.isUnsaved(entry)
    val undone = state.history.isUndone(entry)
    val (label, bg, ink) = kindLook(entry.kind(), colors)
    val book = state.bookNameIn(entry.bible, entry.book)
    val bibleTag = if (state.historyScope == HistoryScope.ALL) {
        " · " + (state.shelf.firstOrNull { it.key == entry.bible }?.abbreviation ?: entry.bible)
    } else {
        ""
    }
    val before = if (entry.before == null && entry.after?.added == true) "" else textOf(entry.before, entry.original)
    Column(
        Modifier.fillMaxWidth().clip(ItemShape).background(colors.raise)
            .border(1.dp, if (unsaved) colors.warnLine else colors.line, ItemShape)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Pill(label, bg, ink)
            Text(
                "$book ${entry.chapter}:${entry.verse}$bibleTag",
                Modifier.clip(RoundedCornerShape(4.dp)).clickable(
                    role = Role.Button, onClickLabel = stringResource(Res.string.bible_editor_go_to_verse),
                ) { state.goTo(entry.bible, entry.book, entry.chapter, entry.verse) },
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.ink,
            )
            Spacer(Modifier.weight(1f))
            Text(relativeTime(entry.time, now), fontSize = 11.5.sp, color = colors.faint, maxLines = 1)
        }
        DiffText(wordDiff(before, textOf(entry.after, entry.original)))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HistoryNote(state, entry, unsaved, now)
            Spacer(Modifier.weight(1f))
            EditorButton(
                stringResource(
                    if (undone) Res.string.bible_editor_already_reverted else Res.string.bible_editor_revert,
                ),
                onClick = { state.history.revert(entry) }, enabled = !undone, height = 26.dp, fontSize = 11.5.sp,
                contentPadding = PaddingValues(horizontal = 9.dp),
                leading = {
                    Icon(painterResource(IconRes.drawable.ic_undo), null, Modifier.size(11.dp), tint = colors.ink)
                },
            )
        }
    }
}

@Composable
private fun HistoryNote(state: BibleEditorState, entry: EditEntry, unsaved: Boolean, now: Long) {
    val colors = editorColors()
    val reverted = entry.revertOf?.let { id -> state.history.entries.firstOrNull { it.id == id } }
    when {
        unsaved -> Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Dot(colors.warn)
            Text(stringResource(Res.string.bible_editor_not_saved), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = colors.warn)
        }
        reverted != null -> Text(
            stringResource(Res.string.bible_editor_undid, stamp(reverted.time,
                now)), fontSize = 11.sp, color = colors.faint,
        )
    }
}

@Composable
private fun HistoryEmpty() {
    val colors = editorColors()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.raise),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.History, null, Modifier.size(18.dp), tint = colors.faint) }
        Text(
            stringResource(Res.string.bible_editor_history_empty_title), Modifier.padding(top = 10.dp),
            fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.ink,
        )
        Text(
            stringResource(Res.string.bible_editor_history_empty_body), Modifier.padding(top = 4.dp),
            fontSize = 12.sp, lineHeight = 18.sp, color = colors.dim, textAlign = TextAlign.Center,
        )
    }
}
