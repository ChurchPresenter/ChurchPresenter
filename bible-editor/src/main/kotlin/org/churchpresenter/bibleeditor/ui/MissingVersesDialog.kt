package org.churchpresenter.bibleeditor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.bibleeditor.BibleEditorState
import org.churchpresenter.bibleeditor.MissingTab
import org.churchpresenter.bibleeditor.MissingVerse
import org.churchpresenter.bibleeditor.bookNameOf
import org.churchpresenter.bibleeditor.isRestored
import org.churchpresenter.bibleeditor.openMissing
import org.churchpresenter.icons.generated.resources.ic_check
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.sharedui.utils.SystemClipboard
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.bible_editor_checking
import org.churchpresenter.strings.generated.resources.bible_editor_close
import org.churchpresenter.strings.generated.resources.bible_editor_matched
import org.churchpresenter.strings.generated.resources.bible_editor_copied
import org.churchpresenter.strings.generated.resources.bible_editor_copy_list
import org.churchpresenter.strings.generated.resources.bible_editor_empty_all_done
import org.churchpresenter.strings.generated.resources.bible_editor_empty_all_done_body
import org.churchpresenter.strings.generated.resources.bible_editor_empty_none
import org.churchpresenter.strings.generated.resources.bible_editor_empty_none_body
import org.churchpresenter.strings.generated.resources.bible_editor_empty_restored
import org.churchpresenter.strings.generated.resources.bible_editor_empty_restored_body
import org.churchpresenter.strings.generated.resources.bible_editor_missing_title
import org.churchpresenter.strings.generated.resources.bible_editor_report_header
import org.churchpresenter.strings.generated.resources.bible_editor_stat_chapters
import org.churchpresenter.strings.generated.resources.bible_editor_stat_missing
import org.churchpresenter.strings.generated.resources.bible_editor_stat_restored
import org.churchpresenter.strings.generated.resources.bible_editor_state_missing
import org.churchpresenter.strings.generated.resources.bible_editor_state_restored
import org.churchpresenter.strings.generated.resources.bible_editor_tab_all
import org.churchpresenter.strings.generated.resources.bible_editor_tab_missing
import org.churchpresenter.strings.generated.resources.bible_editor_tab_restored
import org.churchpresenter.strings.generated.resources.bible_editor_type_it
import org.churchpresenter.strings.generated.resources.bible_editor_type_next
import org.churchpresenter.strings.generated.resources.bible_editor_view
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.bibleeditor.Numbering

private val DialogShape = RoundedCornerShape(18.dp)
private const val SCRIM_ALPHA = 0.55f

/** The Validate dialog: every verse the reference has and this Bible lacks, and which are typed back in. */
@Composable
internal fun MissingVersesDialog(state: BibleEditorState) {
    val colors = editorColors()
    val bible = state.bible
    val all = state.missingHere
    val open = state.openMissing
    val restored = all.orEmpty().filter { state.isRestored(it) }
    val shown = when (state.missingTab) {
        MissingTab.MISSING -> open
        MissingTab.RESTORED -> restored
        MissingTab.ALL -> all.orEmpty()
    }
    val close = { state.missingOpen = false }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM_ALPHA))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close)
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 560.dp).heightIn(max = 620.dp).fillMaxWidth().clip(DialogShape)
                .background(colors.shell)
                .border(1.dp, colors.line, DialogShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Row(Modifier.padding(start = 20.dp, end = 18.dp, top = 18.dp, bottom = 12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.bible_editor_missing_title), fontSize = 17.sp,
                        fontWeight = FontWeight.Bold, color = colors.ink)
                    Text(
                        when {
                            all == null -> stringResource(Res.string.bible_editor_checking)
                            else -> stringResource(
                                Res.string.bible_editor_matched, state.selected?.title.orEmpty(),
                                numberingName(state.numberingHere ?: Numbering.KJV),
                                bible?.getBookCount() ?: 0,
                            )
                        },
                        Modifier.padding(top = 3.dp), fontSize = 12.5.sp, color = colors.dim,
                    )
                }
                EditorIconButton(painterResource(IconRes.drawable.ic_close),
                    stringResource(Res.string.bible_editor_close), close, iconSize = 11.dp)
            }
            MissingStats(open.size, restored.size, all.orEmpty().map { it.book to it.chapter }.toSet().size)
            SegmentTrack(
                listOf(
                    stringResource(Res.string.bible_editor_tab_missing, open.size),
                    stringResource(Res.string.bible_editor_tab_restored, restored.size),
                    stringResource(Res.string.bible_editor_tab_all, all.orEmpty().size),
                ),
                selected = state.missingTab.ordinal,
                onSelect = { state.missingTab = MissingTab.entries[it] },
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp).fillMaxWidth(),
                fontSize = 12.5.sp,
            )
            Box(Modifier.weight(1f, fill = false)) {
                if (shown.isEmpty()) MissingEmpty(all.isNullOrEmpty(), state.missingTab) else MissingList(state, shown)
            }
            MissingFooter(state, open)
        }
    }
}

@Composable
private fun MissingStats(open: Int, restored: Int, chapters: Int) {
    val colors = editorColors()
    Row(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple(open, stringResource(Res.string.bible_editor_stat_missing),
                if (open > 0) colors.warn else colors.good),
            Triple(restored, stringResource(Res.string.bible_editor_stat_restored), colors.good),
            Triple(chapters, stringResource(Res.string.bible_editor_stat_chapters), colors.ink),
        ).forEachIndexed { index, (n, label, ink) ->
            val bg = when (index) {
                0 -> if (open > 0) colors.warnBg else colors.goodBg
                1 -> colors.goodBg
                else -> colors.raise
            }
            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 12.dp,
                vertical = 10.dp)) {
                Text(n.toString(), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = ink)
                Text(label, Modifier.padding(top = 1.dp), fontSize = 11.5.sp, color = colors.dim)
            }
        }
    }
}

@Composable
private fun MissingList(state: BibleEditorState, shown: List<MissingVerse>) {
    val colors = editorColors()
    val groups = shown.groupBy { it.book }
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 10.dp)) {
        groups.forEach { (book, verses) ->
            val name = state.bookNameOf(book)
            item(key = "book-$book") { Caption(name, Modifier.padding(start = 2.dp, end = 2.dp, top = 12.dp,
                bottom = 6.dp)) }
            items(verses, key = { "${it.book}-${it.chapter}-${it.verse}" }) { verse ->
                val fixed = state.isRestored(verse)
                Row(
                    Modifier.padding(bottom = 4.dp).fillMaxWidth().height(42.dp).clip(RoundedCornerShape(10.dp))
                        .background(colors.raise).border(1.dp, colors.line, RoundedCornerShape(10.dp))
                        .padding(start = 12.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Dot(if (fixed) colors.good else colors.warn, size = 8.dp)
                    Text("$name ${verse.chapter}:${verse.verse}", Modifier.weight(1f), fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold, color = colors.ink)
                    Text(
                        stringResource(
                            if (fixed) {
                                Res.string.bible_editor_state_restored
                            } else {
                                Res.string.bible_editor_state_missing
                            },
                        ),
                        fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                        color = if (fixed) colors.good else colors.warn,
                    )
                    val typeIt = !fixed
                    EditorButton(
                        stringResource(if (typeIt) Res.string.bible_editor_type_it else Res.string.bible_editor_view),
                        onClick = { openVerse(state, verse, typeIt) }, height = 28.dp, fontSize = 12.sp,
                        contentPadding = PaddingValues(horizontal = 11.dp),
                    )
                }
            }
        }
    }
}

private fun openVerse(state: BibleEditorState, verse: MissingVerse, typeIt: Boolean) {
    val key = state.selected?.key ?: return
    state.missingOpen = false
    state.goTo(key, verse.book, verse.chapter, if (typeIt) null else verse.verse)
    if (typeIt) state.addingAt = verse.verse
}

@Composable
private fun MissingEmpty(nothingMissing: Boolean, tab: MissingTab) {
    val colors = editorColors()
    val (title, body) = when {
        nothingMissing -> Res.string.bible_editor_empty_none to Res.string.bible_editor_empty_none_body
        tab == MissingTab.RESTORED ->
            Res.string.bible_editor_empty_restored to Res.string.bible_editor_empty_restored_body
        else -> Res.string.bible_editor_empty_all_done to Res.string.bible_editor_empty_all_done_body
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.goodBg),
            contentAlignment = Alignment.Center) {
            Icon(painterResource(IconRes.drawable.ic_check), null, Modifier.size(18.dp), tint = colors.good)
        }
        Text(stringResource(title), Modifier.padding(top = 10.dp), fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold, color = colors.ink)
        Text(stringResource(body), Modifier.padding(top = 3.dp), fontSize = 12.5.sp, color = colors.dim)
    }
}

@Composable
private fun MissingFooter(state: BibleEditorState, open: List<MissingVerse>) {
    val colors = editorColors()
    var copied by remember(state.selected) { mutableStateOf(false) }
    val header = stringResource(Res.string.bible_editor_report_header, state.selected?.title.orEmpty())
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditorButton(
            stringResource(if (copied) Res.string.bible_editor_copied else Res.string.bible_editor_copy_list),
            onClick = {
                val lines = open.map { "${state.bookNameOf(it.book)} ${it.chapter}:${it.verse}" }
                copied = SystemClipboard.copy((listOf(header) + lines).joinToString("\n"))
            },
            tone = ButtonTone.QUIET,
        )
        Spacer(Modifier.weight(1f))
        open.firstOrNull()?.let { next ->
            EditorButton(
                stringResource(Res.string.bible_editor_type_next), onClick = { openVerse(state, next, typeIt = true) },
                tone = ButtonTone.PRIMARY,
            )
        }
    }
}
