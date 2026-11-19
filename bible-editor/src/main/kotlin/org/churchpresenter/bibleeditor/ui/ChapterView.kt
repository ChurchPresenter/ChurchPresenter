package org.churchpresenter.bibleeditor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.bibleeditor.BibleEditorState
import org.churchpresenter.bibleeditor.ChapterRow
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.bible_editor_care_body
import org.churchpresenter.strings.generated.resources.bible_editor_care_title
import org.churchpresenter.strings.generated.resources.bible_editor_chapter_changes_one
import org.churchpresenter.strings.generated.resources.bible_editor_chapter_changes_other
import org.churchpresenter.strings.generated.resources.bible_editor_complete_matches
import org.churchpresenter.strings.generated.resources.bible_editor_double_click_hint
import org.churchpresenter.strings.generated.resources.bible_editor_hide_note
import org.churchpresenter.strings.generated.resources.bible_editor_missing_one
import org.churchpresenter.strings.generated.resources.bible_editor_missing_other
import org.churchpresenter.strings.generated.resources.bible_editor_verses_present
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.icons.generated.resources.Res as IconRes

/** Rows before the first verse in the list: the title and the care note. */
private const val LEADING_ITEMS = 2

/** The open chapter: its title and status, the care note, and every verse or gap in order. */
@Composable
internal fun ChapterView(state: BibleEditorState, rows: List<ChapterRow>, now: Long, modifier: Modifier = Modifier) {
    val list = rememberLazyListState()
    LaunchedEffect(state.flash, state.addingAt, rows.size) {
        val target = state.flash ?: state.addingAt ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it.verse == target }
        if (index >= 0) list.animateScrollToItem(index + LEADING_ITEMS)
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = list,
            modifier = Modifier.widthIn(max = 780.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 28.dp, top = 26.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            item(key = "title") { ChapterTitle(state, rows) }
            item(key = "note") { if (!state.noteHidden) CareNote { state.noteHidden = true } }
            items(rows, key = { it.verse }) { row -> ChapterRowView(state, row, now) }
            item(key = "hint") {
                Text(
                    stringResource(Res.string.bible_editor_double_click_hint),
                    Modifier.padding(start = 50.dp, top = 22.dp), fontSize = 12.sp, color = editorColors().faint,
                )
            }
        }
    }
}

@Composable
private fun ChapterTitle(state: BibleEditorState, rows: List<ChapterRow>) {
    val colors = editorColors()
    val book = state.bookName
    val gaps = rows.count { it is ChapterRow.Gap }
    val present = rows.count { it is ChapterRow.Verse && !it.removed }
    val here = rows.count { it is ChapterRow.Verse && it.change != null }
    Column(Modifier.padding(start = 50.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "$book ${state.chapter}", color = colors.ink, fontFamily = ScriptureFamily, fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${state.selected?.abbreviation.orEmpty()} · " +
                    stringResource(Res.string.bible_editor_verses_present, present, rows.size),
                Modifier.padding(bottom = 6.dp), fontSize = 12.5.sp, color = colors.dim,
            )
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val strip = when {
                gaps > 0 -> countString(gaps, Res.string.bible_editor_missing_one,
                    Res.string.bible_editor_missing_other)
                else -> stringResource(
                    Res.string.bible_editor_complete_matches, BibleEditorState.REFERENCE_ABBREVIATION,
                )
            }
            StatusChip(strip, if (gaps > 0) colors.warnBg else colors.goodBg,
                if (gaps > 0) colors.warn else colors.good, dot = true)
            if (here > 0) {
                StatusChip(
                    countString(here, Res.string.bible_editor_chapter_changes_one,
                        Res.string.bible_editor_chapter_changes_other),
                    colors.accentBg, colors.accent, dot = false,
                )
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, background: Color, ink: Color, dot: Boolean) {
    Row(
        Modifier.height(24.dp).clip(RoundedCornerShape(7.dp)).background(background).padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot) Dot(ink)
        Text(text, color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CareNote(onHide: () -> Unit) {
    val colors = editorColors()
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.padding(start = 50.dp, bottom = 18.dp).fillMaxWidth().clip(shape).background(colors.warnBg)
            .border(1.dp, colors.warnLine, shape).padding(start = 14.dp, end = 10.dp, top = 11.dp, bottom = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = colors.warn, fontWeight = FontWeight.Bold)) {
                    append(stringResource(Res.string.bible_editor_care_title))
                }
                append(" ")
                append(stringResource(Res.string.bible_editor_care_body))
            },
            Modifier.weight(1f), fontSize = 12.5.sp, lineHeight = 19.sp, color = colors.dim,
        )
        EditorIconButton(
            painterResource(IconRes.drawable.ic_close), stringResource(Res.string.bible_editor_hide_note), onHide,
            tint = colors.faint, size = 24.dp, iconSize = 10.dp,
        )
    }
}

@Composable
private fun ChapterRowView(state: BibleEditorState, row: ChapterRow, now: Long) {
    val typing = state.editing == row.verse || state.addingAt == row.verse
    when {
        typing -> VerseEditorCard(state, row)
        row is ChapterRow.Gap -> GapRow(row.verse, row.joinedTo) { state.clearEditing(); state.addingAt = row.verse }
        row is ChapterRow.Verse -> VerseLine(state, row, now)
    }
}

/** The fixed column the verse numbers sit in, so text lines up down the page. */
@Composable
internal fun VerseNumber(number: Int, color: Color, top: Int = 4) {
    Box(Modifier.width(44.dp).padding(top = top.dp, end = 12.dp), contentAlignment = Alignment.TopEnd) {
        Text(number.toString(), color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

