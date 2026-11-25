package org.churchpresenter.bibleeditor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.key
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.bibleeditor.BibleEditorState
import org.churchpresenter.bibleeditor.openMissing
import org.churchpresenter.icons.generated.resources.ic_arrow_down
import org.churchpresenter.icons.generated.resources.ic_check
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.bible_editor_book_flag
import org.churchpresenter.strings.generated.resources.bible_editor_changes_one
import org.churchpresenter.strings.generated.resources.bible_editor_changes_other
import org.churchpresenter.strings.generated.resources.bible_editor_history
import org.churchpresenter.strings.generated.resources.bible_editor_title
import org.churchpresenter.strings.generated.resources.bible_editor_validate
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.icons.generated.resources.Res as IconRes

/** The top bar: the title, the Bible, book and chapter pickers, Validate and History. */
@Composable
internal fun EditorHeader(state: BibleEditorState, openMissing: Int) {
    val colors = editorColors()
    Row(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(Res.string.bible_editor_title), color = colors.ink, fontSize = 15.sp,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        BiblePicker(state)
        BookPicker(state)
        ChapterStepper(state)
        Box(Modifier.width(1.dp).height(22.dp).background(colors.line))
        EditorButton(
            stringResource(Res.string.bible_editor_validate),
            onClick = { state.missingOpen = true },
            leading = {
                Icon(painterResource(IconRes.drawable.ic_check), null, tint = colors.good,
                    modifier = Modifier.size(14.dp))
            },
            trailing = if (openMissing > 0) {
                { CountBadge(openMissing) }
            } else {
                null
            },
        )
        HistoryToggle(state)
    }
}

@Composable
private fun CountBadge(count: Int) {
    val colors = editorColors()
    Box(
        Modifier.heightIn(min = 18.dp).widthIn(min = 19.dp).clip(RoundedCornerShape(9.dp)).background(colors.warn)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(count.toString(), color = colors.shell, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun HistoryToggle(state: BibleEditorState) {
    val colors = editorColors()
    val on = state.historyOpen
    EditorButton(
        stringResource(Res.string.bible_editor_history),
        onClick = { state.historyOpen = !on },
        tone = if (on) ButtonTone.QUIET else ButtonTone.KEY,
        modifier = if (on) Modifier.border(1.dp, colors.accentLine, ButtonShape).background(colors.accentBg,
            ButtonShape)
        else Modifier,
        leading = {
            Icon(Icons.Filled.History, null, tint = if (on) colors.accent else colors.ink,
                modifier = Modifier.size(15.dp))
        },
    )
}

@Composable
private fun Chevron() {
    Icon(painterResource(IconRes.drawable.ic_arrow_down), null, tint = editorColors().faint,
        modifier = Modifier.size(12.dp))
}

@Composable
private fun BiblePicker(state: BibleEditorState) {
    val colors = editorColors()
    var open by remember { mutableStateOf(false) }
    val current = state.selected
    val changes = state.history.changes
    Box {
        EditorButton(
            current?.title.orEmpty(),
            onClick = { open = !open },
            modifier = Modifier.widthIn(min = 250.dp, max = 320.dp),
            leading = { current?.let { Pill(it.abbreviation, colors.accentBg, colors.accent) } },
            trailing = { Spacer(Modifier.weight(1f)); Chevron() },
        )
        val shelf = state.shelf
        EditorMenu(
            open, onDismiss = { open = false }, width = 320.dp, maxHeight = 300.dp, itemHeight = 36.dp,
            count = shelf.size, selected = shelf.indexOf(current),
        ) { index ->
            val entry = shelf[index]
            val count = changes.keys.count { it.bible == entry.key }
            val picked = entry == current
            EditorMenuRow(36.dp, picked, onClick = { open = false; state.selectBible(entry) }) {
                Text(
                    entry.abbreviation, Modifier.width(52.dp), fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (picked) colors.accent else colors.dim, maxLines = 1,
                )
                Text(
                    entry.title, Modifier.weight(1f), fontSize = 13.sp, color = colors.ink, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (count > 0) {
                    Text(
                        countString(count, Res.string.bible_editor_changes_one, Res.string.bible_editor_changes_other),
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.faint,
                    )
                }
            }
        }
    }
}

@Composable
private fun BookPicker(state: BibleEditorState) {
    val colors = editorColors()
    var open by remember { mutableStateOf(false) }
    val books = state.bible?.getBooks().orEmpty()
    val openMissingBooks = state.openMissing.map { it.book }.toSet()
    Box {
        EditorButton(
            books.getOrNull(state.bookIndex).orEmpty(),
            onClick = { open = !open },
            modifier = Modifier.width(150.dp),
            trailing = { Spacer(Modifier.weight(1f)); Chevron() },
        )
        val flag = stringResource(Res.string.bible_editor_book_flag)
        EditorMenu(
            open, onDismiss = { open = false }, width = 220.dp, maxHeight = 340.dp, itemHeight = 32.dp,
            count = books.size, selected = state.bookIndex,
        ) { index ->
            val bookId = state.bible?.getBookId(index)
            EditorMenuRow(32.dp, index == state.bookIndex, onClick = { open = false; state.selectBook(index) }) {
                Text(books[index], Modifier.weight(1f), fontSize = 13.sp, color = colors.ink)
                if (bookId in openMissingBooks) {
                    Box(Modifier.semantics { contentDescription = flag }) { Dot(colors.warn) }
                }
            }
        }
    }
}
