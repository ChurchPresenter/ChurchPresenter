package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondary
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_to_schedule
import churchpresenter.composeapp.generated.resources.copy_verse
import churchpresenter.composeapp.generated.resources.go_live
import churchpresenter.composeapp.generated.resources.ic_copy
import churchpresenter.composeapp.generated.resources.ic_playlist_add
import kotlinx.coroutines.flow.first
import org.churchpresenter.app.churchpresenter.viewmodel.verseNumberOf
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The three-column browser: books, chapters, verses, and whatever is docked beside them.
 *
 * The verse pane is a Row of optional neighbours — the cross-reference column and the live-chapter
 * panel — each of which costs width only when it is on, and each with a drag handle of its own. The
 * width arithmetic is the reason they share a parent: the verse list keeps a 100dp floor, so what
 * the live panel may claim has to come out of what the cross-reference column already took.
 *
 * Takes values and typed callbacks throughout; the cross-reference state arrives as one object.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun ColumnScope.BibleBrowserPane(
    books: List<String>,
    filteredBooks: List<String>,
    filteredChapters: List<String>,
    filteredVerses: List<String>,
    selectedBookIndex: Int,
    selectedChapter: Int,
    selectedVerseIndices: Set<Int>?,
    selectedVerseInFiltered: Int,
    bookWidthPx: Float,
    chapterWidthPx: Float,
    crossRefWidthPx: Float,
    splitWidthPx: Float,
    onBookWidthChange: (Float) -> Unit,
    onChapterWidthChange: (Float) -> Unit,
    onCrossRefWidthChange: (Float) -> Unit,
    onSplitWidthChange: (Float) -> Unit,
    onSaveColumnWidths: () -> Unit,
    onSaveCrossRefWidth: () -> Unit,
    onSaveSplitWidth: () -> Unit,
    crossRefs: BibleCrossReferenceState,
    crossRefsDocked: Boolean,
    crossRefCountLabel: (Int) -> String,
    crossRefPopoverTitle: (String, Int) -> String,
    onOpenCrossRef: (CrossRefRow) -> Unit,
    onGoLiveCrossRef: (CrossRefRow) -> Unit,
    onScheduleCrossRef: (CrossRefRow) -> Unit,
    onDockCrossRefs: () -> Unit,
    onUndockCrossRefs: () -> Unit,
    onDismissPopover: () -> Unit,
    onRefsChipClicked: (Int) -> Unit,
    onBookSelected: (Int) -> Unit,
    onChapterSelected: (Int) -> Unit,
    onVerseSelected: (Int) -> Unit,
    onVerseCtrlClicked: (Int) -> Unit,
    onVerseShiftClicked: (Int) -> Unit,
    onVerseRightClicked: (Int) -> Unit,
    onVerseDoubleClicked: () -> Unit,
    onCopyVerse: () -> Unit,
    onAddToSchedule: () -> Unit,
    isSplitActive: Boolean,
    liveChapterVerses: List<String>,
    liveVerseNumbers: Set<Int>,
    onLiveVerseClicked: (Int) -> Unit,
    /** Drawn under the verse pane, inside the same column — the history panel. */
    footer: @Composable () -> Unit,
) {
    val density = LocalDensity.current
        Row(modifier = Modifier.fillMaxWidth().weight(1f).padding(start = 4.dp)) {

            Column(modifier = Modifier.width(with(density) { bookWidthPx.toDp() }).fillMaxHeight()) {
                BibleBrowserColumn(
                    items = filteredBooks,
                    selectedIndex = filteredBooks.indexOf(books.getOrNull(selectedBookIndex) ?: "").coerceAtLeast(0),
                    singleLine = true,
                    onItemSelected = onBookSelected
                )
            }

            DragHandle(onDragEnd = onSaveColumnWidths) { amount ->
                onBookWidthChange(
                    (bookWidthPx + amount).coerceIn(
                        with(density) { 80.dp.toPx() }, with(density) { 400.dp.toPx() },
                    )
                )
            }

            Column(modifier = Modifier.width(with(density) { chapterWidthPx.toDp() }).fillMaxHeight()) {
                BibleBrowserColumn(
                    items = filteredChapters,
                    selectedIndex = filteredChapters.indexOf(selectedChapter.toString()).coerceAtLeast(0),
                    centerText = true,
                    rowHeight = 31.dp,
                    onItemSelected = onChapterSelected
                )
            }

            DragHandle(onDragEnd = onSaveColumnWidths) { amount ->
                onChapterWidthChange(
                    (chapterWidthPx + amount).coerceIn(
                        with(density) { 60.dp.toPx() }, with(density) { 300.dp.toPx() },
                    )
                )
            }

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {

                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {

                val crossRefReserve =
                    if (crossRefsDocked) crossRefWidthPx + with(density) { 5.dp.toPx() } else 0f
                val effectiveSplitWidth = if (isSplitActive)
                    splitWidthPx.coerceAtMost(
                        (constraints.maxWidth - crossRefReserve - with(
                            density
                        ) { (100.dp + 6.dp).toPx() }).coerceAtLeast(0f)
                    )
                else 0f
                Row(modifier = Modifier.fillMaxSize()) {

                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        var showVerseContextMenu by remember { mutableStateOf(false) }
                        var verseContextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }

                        Box(modifier = Modifier.fillMaxSize()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Main)
                                        if (event.type == PointerEventType.Press && event.button?.isSecondary == true) {
                                            val pos = event.changes.first().position
                                            verseContextMenuOffset =
                                                with(density) { DpOffset(pos.x.toDp(), pos.y.toDp()) }
                                        }
                                    }
                                }
                            }
                        ) {

                            BibleVerseColumn(
                                verses = filteredVerses,
                                selectedIndex = selectedVerseInFiltered,
                                selectedIndices = selectedVerseIndices,
                                onItemSelected = onVerseSelected,
                                refCountFor = { index ->
                                    filteredVerses.getOrNull(index)
                                        ?.let(::verseNumberOf)
                                        ?.let { crossRefs.counts[it] } ?: 0
                                },
                                refCountTooltip = crossRefCountLabel,
                                openRefIndex = if (crossRefsDocked) -1 else crossRefs.popoverIndex,
                                onRefsClicked = onRefsChipClicked,
                                refPopover = {
                                    CrossReferencePopover(
                                        title = crossRefPopoverTitle(
                                            crossRefs.popoverLabel,
                                            crossRefs.popoverRows.size
                                        ),
                                        rows = crossRefs.popoverRows,
                                        onDismiss = onDismissPopover,
                                        onDock = onDockCrossRefs,
                                        onOpen = onOpenCrossRef,
                                        onGoLive = onGoLiveCrossRef,
                                        onAddToSchedule = onScheduleCrossRef,
                                    )
                                },
                                onItemDoubleClicked = { _ -> onVerseDoubleClicked() },
                                onItemCtrlClicked = onVerseCtrlClicked,
                                onItemShiftClicked = onVerseShiftClicked,
                                onRightClicked = { index ->
                                    onVerseRightClicked(index)
                                    showVerseContextMenu = true
                                }
                            )

                            DropdownMenu(
                                expanded = showVerseContextMenu,
                                onDismissRequest = { showVerseContextMenu = false },
                                offset = verseContextMenuOffset
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.copy_verse)) },
                                    leadingIcon = { Icon(
                                        painter = painterResource(Res.drawable.ic_copy),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    ) },
                                    onClick = { onCopyVerse(); showVerseContextMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.add_to_schedule)) },
                                    leadingIcon = { Icon(
                                        painter = painterResource(Res.drawable.ic_playlist_add),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.secondary
                                    ) },
                                    onClick = { onAddToSchedule(); showVerseContextMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.go_live)) },
                                    leadingIcon = { Icon(
                                        imageVector = Icons.Default.Tv,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    ) },
                                    onClick = { onVerseDoubleClicked(); showVerseContextMenu = false }
                                )
                            }
                        }
                    }

                    if (crossRefsDocked) {
                        DragHandle(onDragEnd = onSaveCrossRefWidth) { amount ->
                            onCrossRefWidthChange(
                                (crossRefWidthPx - amount).coerceIn(
                                    with(density) { CROSS_REF_MIN_WIDTH.toPx() },
                                    with(density) { CROSS_REF_MAX_WIDTH.toPx() },
                                )
                            )
                        }
                        CrossReferencePanel(
                            rows = crossRefs.rows,
                            selectedIndex = crossRefs.selectedIndex,
                            onClick = { idx ->
                                crossRefs.selectedIndex = idx
                                crossRefs.rows.getOrNull(idx)?.let(onOpenCrossRef)
                            },
                            onDoubleClick = { idx ->
                                crossRefs.selectedIndex = idx
                                crossRefs.rows.getOrNull(idx)?.let(onGoLiveCrossRef)
                            },
                            onAddToSchedule = { idx -> crossRefs.rows.getOrNull(idx)?.let(onScheduleCrossRef) },
                            onClose = onUndockCrossRefs,
                            passageSpan = crossRefs.passageSpan,
                            modifier = Modifier.width(with(density) { crossRefWidthPx.toDp() }).fillMaxHeight(),
                        )
                    }

                    if (isSplitActive) {
                        DragHandle(onDragEnd = onSaveSplitWidth) { amount ->
                            onSplitWidthChange(
                                (splitWidthPx - amount).coerceIn(
                                    with(density) { 150.dp.toPx() }, with(density) { 600.dp.toPx() },
                                )
                            )
                        }
                        Column(modifier = Modifier.width(
                            with(density) { effectiveSplitWidth.toDp() }
                        ).fillMaxHeight()) {
                            LiveChapterPanel(
                                verses = liveChapterVerses,
                                liveVerseNumbers = liveVerseNumbers,
                                onVerseClicked = onLiveVerseClicked,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                }
                }

                footer()
            }

        }
}
