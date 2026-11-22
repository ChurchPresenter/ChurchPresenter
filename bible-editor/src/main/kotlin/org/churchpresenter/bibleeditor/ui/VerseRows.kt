package org.churchpresenter.bibleeditor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.bibleeditor.BibleEditorState
import org.churchpresenter.bibleeditor.ChapterRow
import org.churchpresenter.bibleeditor.HistoryScope
import org.churchpresenter.bibleeditor.saveDraft
import org.churchpresenter.bibleeditor.toggleRemoved
import org.churchpresenter.bibleeditor.wordDiff
import org.churchpresenter.icons.generated.resources.ic_delete
import org.churchpresenter.icons.generated.resources.ic_edit
import org.churchpresenter.sharedui.composables.tabMovesFocus
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.bible_editor_gap_end_caution
import org.churchpresenter.strings.generated.resources.bible_editor_add_verse
import org.churchpresenter.strings.generated.resources.bible_editor_cancel
import org.churchpresenter.strings.generated.resources.bible_editor_edit_hint
import org.churchpresenter.strings.generated.resources.bible_editor_edit_title
import org.churchpresenter.strings.generated.resources.bible_editor_gap
import org.churchpresenter.strings.generated.resources.bible_editor_history_of
import org.churchpresenter.strings.generated.resources.bible_editor_keys_hint
import org.churchpresenter.strings.generated.resources.bible_editor_kind_added
import org.churchpresenter.strings.generated.resources.bible_editor_kind_edited
import org.churchpresenter.strings.generated.resources.bible_editor_kind_removed
import org.churchpresenter.strings.generated.resources.bible_editor_new_hint
import org.churchpresenter.strings.generated.resources.bible_editor_placeholder
import org.churchpresenter.strings.generated.resources.bible_editor_remove_verse
import org.churchpresenter.strings.generated.resources.bible_editor_restore
import org.churchpresenter.strings.generated.resources.bible_editor_save_edit
import org.churchpresenter.strings.generated.resources.bible_editor_see_history
import org.churchpresenter.strings.generated.resources.bible_editor_take_out_added
import org.churchpresenter.strings.generated.resources.bible_editor_type_missing_title
import org.churchpresenter.strings.generated.resources.bible_editor_type_verse
import org.churchpresenter.strings.generated.resources.bible_editor_your_change
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.churchpresenter.icons.generated.resources.Res as IconRes

private val GapShape = RoundedCornerShape(12.dp)
private val CardShape = RoundedCornerShape(14.dp)

/** The place a missing verse goes, with the button that types it in. */
@Composable
internal fun GapRow(verse: Int, joinedTo: Int?, onAdd: () -> Unit) {
    val colors = editorColors()
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        VerseNumber(verse, colors.warn, top = 0)
        Row(
            Modifier.weight(1f).heightIn(min = 46.dp).clip(GapShape).background(colors.warnBg)
                .border(1.5.dp, colors.warnLine, GapShape)
                .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.bible_editor_gap, verse), fontSize = 13.sp, color = colors.warn)
                // Past the chapter's last verse it is more often joined to that verse than lost.
                if (joinedTo != null) {
                    Text(
                        stringResource(Res.string.bible_editor_gap_end_caution, joinedTo),
                        Modifier.padding(top = 2.dp), fontSize = 11.5.sp, lineHeight = 16.sp, color = colors.dim,
                    )
                }
            }
            EditorButton(
                stringResource(Res.string.bible_editor_type_verse, verse), onAdd, height = 30.dp, fontSize = 12.5.sp,
                leading = { Icon(painterResource(IconRes.drawable.ic_edit), null, Modifier.size(11.dp),
                    tint = colors.ink) },
            )
        }
    }
}

/** The card a verse is typed in: the field, the diff against the file, Cancel and Save. */
@Composable
internal fun VerseEditorCard(state: BibleEditorState, row: ChapterRow) {
    val colors = editorColors()
    val verse = row as? ChapterRow.Verse
    val isNew = verse == null
    val typed = state.draft.trim()
    val current = verse?.text?.trim().orEmpty()
    val canSave = typed.isNotEmpty() && typed != current
    val focus = remember { FocusRequester() }
    LaunchedEffect(row.verse) { focus.requestFocus() }
    Row(Modifier.padding(vertical = 6.dp)) {
        VerseNumber(row.verse, colors.accent, top = 14)
        Column(
            Modifier.weight(1f).clip(CardShape).background(colors.raise).border(1.dp, colors.accentLine, CardShape)
                .padding(12.dp),
        ) {
            Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        if (isNew) Res.string.bible_editor_type_missing_title else Res.string.bible_editor_edit_title,
                        row.verse,
                    ),
                    fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = colors.ink,
                )
                Spacer(Modifier.weight(1f))
                Text(stringResource(Res.string.bible_editor_keys_hint), fontSize = 11.5.sp, color = colors.faint)
            }
            DraftField(state, focus, onSave = { if (canSave) state.saveDraft(row) })
            if (!isNew && canSave) {
                Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(9.dp))
                    .background(colors.well)
                    .padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Caption(stringResource(Res.string.bible_editor_your_change), Modifier.padding(bottom = 3.dp),
                        fontSize = 9.5.sp)
                    DiffText(wordDiff(verse.original.orEmpty(), typed))
                }
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isNew) stringResource(Res.string.bible_editor_new_hint, row.verse)
                    else stringResource(Res.string.bible_editor_edit_hint),
                    Modifier.weight(1f), fontSize = 11.5.sp, color = colors.faint,
                )
                EditorButton(stringResource(Res.string.bible_editor_cancel), state::clearEditing,
                    tone = ButtonTone.QUIET, height = 30.dp)
                Spacer(Modifier.size(8.dp))
                EditorButton(
                    if (isNew) stringResource(Res.string.bible_editor_add_verse, row.verse)
                    else stringResource(Res.string.bible_editor_save_edit),
                    onClick = { state.saveDraft(row) }, tone = ButtonTone.PRIMARY, enabled = canSave, height = 30.dp,
                )
            }
        }
    }
}

@Composable
private fun DraftField(state: BibleEditorState, focus: FocusRequester, onSave: () -> Unit) {
    val colors = editorColors()
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = state.draft,
        onValueChange = { state.draft = it },
        textStyle = TextStyle(color = colors.ink, fontFamily = ScriptureFamily, fontSize = 17.sp, lineHeight = 26.sp),
        cursorBrush = SolidColor(colors.accent),
        minLines = 3,
        modifier = Modifier.fillMaxWidth().focusRequester(focus).tabMovesFocus()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    event.key == Key.Escape -> { state.clearEditing(); true }
                    event.key == Key.Enter && (event.isMetaPressed || event.isCtrlPressed) -> { onSave(); true }
                    else -> false
                }
            },
        decorationBox = { inner ->
            Box(
                Modifier.clip(shape).background(colors.well).border(1.dp, colors.line, shape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (state.draft.isEmpty()) {
                    Text(
                        stringResource(Res.string.bible_editor_placeholder), color = colors.faint,
                        fontFamily = ScriptureFamily,
                        fontSize = 17.sp,
                    )
                }
                inner()
            }
        },
    )
}

/** One verse as the file and the changes make it read, with Edit, History and Remove on hover. */
@Composable
internal fun VerseLine(state: BibleEditorState, row: ChapterRow.Verse, now: Long) {
    val colors = editorColors()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var focusInside by remember { mutableStateOf(false) }
    val startEdit = { if (!row.removed) { state.clearEditing(); state.editing = row.verse; state.draft = row.text } }
    val openHistory = {
        state.historyOpen = true
        state.historyScope = HistoryScope.CHAPTER
        state.historyVerse = row.verse
    }
    val numberInk = when {
        row.added -> colors.good
        row.edited -> colors.accent
        row.removed -> colors.bad
        else -> colors.faint
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(GapShape)
            .background(
                when {
                    state.flash == row.verse -> colors.accentBg
                    hovered -> colors.hover
                    else -> Color.Transparent
                },
            )
            .hoverable(interaction)
            .onFocusChanged { focusInside = it.hasFocus }
            .pointerInput(row.verse, row.removed) { detectTapGestures(onDoubleTap = { startEdit() }) }
            .padding(top = 9.dp, bottom = 9.dp, end = 8.dp),
    ) {
        VerseNumber(row.verse, numberInk)
        Column(Modifier.weight(1f)) {
            Text(
                row.text, color = if (row.removed) colors.faint else colors.ink, fontFamily = ScriptureFamily,
                fontSize = 17.sp, lineHeight = 27.sp,
                style = TextStyle(
                    textDecoration = if (row.removed) TextDecoration.LineThrough else TextDecoration.None,
                ),
            )
            if (row.change != null) VerseTag(row, now, openHistory)
        }
        Row(Modifier.alpha(if (hovered || focusInside) 1f else 0f),
            horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            VerseActions(row, startEdit, openHistory, onRemove = { state.toggleRemoved(row) })
        }
    }
}

@Composable
private fun VerseTag(row: ChapterRow.Verse, now: Long, onClick: () -> Unit) {
    val colors = editorColors()
    val (label, bg, ink) = when {
        row.removed -> Triple(stringResource(Res.string.bible_editor_kind_removed), colors.badBg, colors.bad)
        row.added -> Triple(stringResource(Res.string.bible_editor_kind_added), colors.goodBg, colors.good)
        else -> Triple(stringResource(Res.string.bible_editor_kind_edited), colors.accentBg, colors.accent)
    }
    val tip = stringResource(Res.string.bible_editor_see_history)
    Row(
        Modifier.padding(top = 6.dp).clip(RoundedCornerShape(5.dp))
            .clickable(role = Role.Button, onClickLabel = tip, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Pill(label, bg, ink)
        row.last?.let { Text(stamp(it.time, now), fontSize = 11.5.sp, color = colors.faint) }
    }
}

@Composable
private fun VerseActions(row: ChapterRow.Verse, onEdit: () -> Unit, onHistory: () -> Unit, onRemove: () -> Unit) {
    val colors = editorColors()
    if (row.removed) {
        EditorButton(stringResource(Res.string.bible_editor_restore), onRemove, height = 28.dp, fontSize = 12.sp)
        return
    }
    EditorIconButton(painterResource(IconRes.drawable.ic_edit), stringResource(Res.string.bible_editor_edit_title,
        row.verse), onEdit)
    if (row.last != null) {
        EditorIconButton(
            rememberVectorPainter(Icons.Filled.History), stringResource(Res.string.bible_editor_history_of,
                row.verse), onHistory,
        )
    }
    EditorIconButton(
        painterResource(IconRes.drawable.ic_delete),
        stringResource(if (row.added) Res.string.bible_editor_take_out_added else Res.string.bible_editor_remove_verse),
        onRemove, tint = colors.bad,
    )
}
