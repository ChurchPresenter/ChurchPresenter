package org.churchpresenter.bibleeditor.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.bibleeditor.BibleEditorState
import org.churchpresenter.bibleeditor.ChapterStatus
import org.churchpresenter.bibleeditor.bookHasMissing
import org.churchpresenter.bibleeditor.chapterStatus
import org.churchpresenter.bibleeditor.goToNextMissing
import org.churchpresenter.bibleeditor.isRestored
import org.churchpresenter.bibleeditor.nextMissing
import org.churchpresenter.bibleeditor.bookNameOf
import org.churchpresenter.icons.generated.resources.ic_arrow_down
import org.churchpresenter.icons.generated.resources.ic_arrow_left
import org.churchpresenter.icons.generated.resources.ic_arrow_right
import org.churchpresenter.sharedui.composables.HintTooltip
import org.churchpresenter.theme.keyboardFocusRing
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.bible_editor_book_flag
import org.churchpresenter.strings.generated.resources.bible_editor_chapter
import org.churchpresenter.strings.generated.resources.bible_editor_chapter_count
import org.churchpresenter.strings.generated.resources.bible_editor_chapter_number
import org.churchpresenter.strings.generated.resources.bible_editor_chapter_of
import org.churchpresenter.strings.generated.resources.bible_editor_chapter_tip
import org.churchpresenter.strings.generated.resources.bible_editor_chapter_tip_missing
import org.churchpresenter.strings.generated.resources.bible_editor_chapter_tip_restored
import org.churchpresenter.strings.generated.resources.bible_editor_go_to
import org.churchpresenter.strings.generated.resources.bible_editor_next_chapter
import org.churchpresenter.strings.generated.resources.bible_editor_next_missing
import org.churchpresenter.strings.generated.resources.bible_editor_next_missing_at
import org.churchpresenter.strings.generated.resources.bible_editor_no_next_missing
import org.churchpresenter.strings.generated.resources.bible_editor_previous_chapter
import org.churchpresenter.strings.generated.resources.bible_editor_some_chapters_missing
import org.churchpresenter.strings.generated.resources.bible_editor_state_restored
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.theme.components.AppDropdownMenu

private const val MAX_CHAPTER_DIGITS = 3
private const val GRID_COLUMNS = 7
private val CellShape = RoundedCornerShape(8.dp)
private val CellHeight = 34.dp
private val CellGap = 4.dp
private val GridMaxHeight = 236.dp

/** The chapter control: back, the chapter button that opens the picker, and forward. */
@Composable
internal fun ChapterStepper(state: BibleEditorState) {
    val colors = editorColors()
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.height(34.dp).clip(ButtonShape).background(colors.well).padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            EditorIconButton(
                painterResource(IconRes.drawable.ic_arrow_left),
                stringResource(Res.string.bible_editor_previous_chapter),
                onClick = { state.goToChapter(state.chapter - 1) }, size = 28.dp, iconSize = 12.dp,
            )
            ChapterButton(state, open) { open = !open }
            EditorIconButton(
                painterResource(IconRes.drawable.ic_arrow_right), stringResource(Res.string.bible_editor_next_chapter),
                onClick = { state.goToChapter(state.chapter + 1) }, size = 28.dp, iconSize = 12.dp,
            )
        }
        AppDropdownMenu(open, onDismissRequest = { open = false }, containerColor = colors.raise) {
            ChapterPicker(state, onDone = { open = false })
        }
    }
}

@Composable
private fun ChapterButton(state: BibleEditorState, open: Boolean, onClick: () -> Unit) {
    val colors = editorColors()
    val shape = RoundedCornerShape(7.dp)
    val flag = stringResource(Res.string.bible_editor_some_chapters_missing)
    Row(
        Modifier.keyboardFocusRing(shape).height(28.dp).clip(shape)
            .background(if (open) colors.hover else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(stringResource(Res.string.bible_editor_chapter), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = colors.dim)
        Text(state.chapter.toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.ink)
        Text(stringResource(Res.string.bible_editor_chapter_of, state.chapterCount), fontSize = 12.sp,
            color = colors.faint)
        if (state.bookHasMissing) Box(Modifier.semantics { contentDescription = flag }) { Dot(colors.warn) }
        Icon(painterResource(IconRes.drawable.ic_arrow_down), null, Modifier.size(10.dp), tint = colors.faint)
    }
}

/** The picker: the book's chapters as a grid, flagged where verses are missing, and Next missing. */
@Composable
private fun ChapterPicker(state: BibleEditorState, onDone: () -> Unit) {
    val colors = editorColors()
    Column(Modifier.width(330.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(state.bookName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.ink, maxLines = 1)
            Text(
                "· " + stringResource(Res.string.bible_editor_chapter_count, state.chapterCount), Modifier.weight(1f),
                fontSize = 13.sp, color = colors.faint, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(stringResource(Res.string.bible_editor_go_to), fontSize = 12.sp, color = colors.dim)
            GoToField(state, onDone)
        }
        if (state.bookHasMissing) Legend()
        ChapterGrid(state, onDone)
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        NextMissingRow(state, onDone)
    }
}

@Composable
private fun Legend() {
    val colors = editorColors()
    Row(
        Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf(
            colors.warn to stringResource(Res.string.bible_editor_book_flag),
            colors.good to stringResource(Res.string.bible_editor_state_restored),
        ).forEach { (dot, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Dot(dot)
                Text(label, fontSize = 11.5.sp, color = colors.dim)
            }
        }
    }
}

@Composable
private fun ChapterGrid(state: BibleEditorState, onDone: () -> Unit) {
    val scroll = rememberScrollState()
    val rows = (1..state.chapterCount).chunked(GRID_COLUMNS)
    val height = minOf((CellHeight + CellGap) * rows.size, GridMaxHeight)
    Box(Modifier.fillMaxWidth().height(height).padding(start = 12.dp, end = 4.dp)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(scroll).padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(CellGap),
        ) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(CellGap)) {
                    for (i in 0 until GRID_COLUMNS) {
                        val chapter = row.getOrNull(i)
                        Box(Modifier.weight(1f)) {
                            if (chapter != null) {
                                ChapterCell(state, chapter) {
                                    state.goToChapter(chapter)
                                    onDone()
                                }
                            }
                        }
                    }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
private fun ChapterCell(state: BibleEditorState, chapter: Int, onPick: () -> Unit) {
    val colors = editorColors()
    val status = state.chapterStatus(chapter)
    val current = chapter == state.chapter
    val tip = when (status) {
        ChapterStatus.MISSING -> stringResource(
            Res.string.bible_editor_chapter_tip_missing,
            missingRefs(state, chapter),
        )
        ChapterStatus.RESTORED -> stringResource(Res.string.bible_editor_chapter_tip_restored)
        ChapterStatus.COMPLETE -> stringResource(Res.string.bible_editor_chapter_tip, chapter)
    }
    val missing = status == ChapterStatus.MISSING
    val (fill, ink) = when {
        current -> colors.accent to colors.onAccent
        missing -> colors.warnBg to colors.warn
        else -> colors.hover to colors.ink
    }
    val dot = when {
        missing && !current -> colors.warn
        status == ChapterStatus.RESTORED -> colors.good
        else -> Color.Transparent
    }
    HintTooltip(tip) {
        Box(
            Modifier.fillMaxWidth().keyboardFocusRing(CellShape).height(CellHeight).clip(CellShape).background(fill)
                .border(1.dp, if (missing && !current) colors.warnLine else Color.Transparent, CellShape)
                .clickable(role = Role.Button, onClick = onPick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                chapter.toString(), color = ink, fontSize = 13.sp,
                fontWeight = if (current || status != ChapterStatus.COMPLETE) FontWeight.Bold else FontWeight.Medium,
            )
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) { Dot(dot) }
        }
    }
}

/** "Matthew 17:11, Matthew 17:21" — what a flagged chapter's tooltip names. */
private fun missingRefs(state: BibleEditorState, chapter: Int): String {
    val name = state.bookNameOf(state.bookId)
    return state.missingHere.orEmpty()
        .filter { it.book == state.bookId && it.chapter == chapter && !state.isRestored(it) }
        .joinToString(", ") { "$name $chapter:${it.verse}" }
}

@Composable
private fun NextMissingRow(state: BibleEditorState, onDone: () -> Unit) {
    val colors = editorColors()
    val next = state.nextMissing()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (next == null) {
                stringResource(Res.string.bible_editor_no_next_missing)
            } else {
                stringResource(
                    Res.string.bible_editor_next_missing_at,
                    "${state.bookNameOf(next.book)} ${next.chapter}:${next.verse}",
                )
            },
            Modifier.weight(1f), fontSize = 12.sp, color = colors.dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        EditorButton(
            stringResource(Res.string.bible_editor_next_missing),
            onClick = {
                state.goToNextMissing()
                onDone()
            },
            enabled = next != null, height = 28.dp, fontSize = 12.sp,
            trailing = {
                Icon(painterResource(IconRes.drawable.ic_arrow_right), null, Modifier.size(11.dp), tint = colors.ink)
            },
        )
    }
}

/** The "Go to" box: a number typed and Enter goes there; arrows step; Escape lets go. */
@Composable
private fun GoToField(state: BibleEditorState, onDone: () -> Unit) {
    val colors = editorColors()
    var draft by remember { mutableStateOf<String?>(null) }
    var focused by remember { mutableStateOf(false) }
    val label = stringResource(Res.string.bible_editor_chapter_number)
    fun commit() {
        draft?.toIntOrNull()?.takeIf { it in 1..state.chapterCount }?.let {
            state.goToChapter(it)
            onDone()
        }
        draft = null
    }
    BasicTextField(
        value = draft ?: state.chapter.toString(),
        onValueChange = { typed -> draft = typed.filter(Char::isDigit).take(MAX_CHAPTER_DIGITS) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = TextStyle(
            color = colors.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(colors.accent),
        modifier = Modifier
            .width(48.dp).heightIn(min = 28.dp)
            .onFocusChanged {
                if (focused && !it.isFocused) commit()
                focused = it.isFocused
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter -> { commit(); true }
                    Key.Escape -> { draft = null; onDone(); true }
                    Key.DirectionUp -> { draft = null; state.goToChapter(state.chapter + 1); true }
                    Key.DirectionDown -> { draft = null; state.goToChapter(state.chapter - 1); true }
                    else -> false
                }
            }
            .semantics { contentDescription = label },
        decorationBox = { inner ->
            Box(
                Modifier.height(28.dp).clip(RoundedCornerShape(7.dp)).background(colors.well)
                    .border(1.dp, if (focused) colors.accent else colors.line, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) { inner() }
        },
    )
}
