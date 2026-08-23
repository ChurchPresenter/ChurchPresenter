package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isSecondary
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible_load_failed_detail
import churchpresenter.composeapp.generated.resources.bible_load_failed_partial_hint
import churchpresenter.composeapp.generated.resources.bible_load_failed_partial_title
import churchpresenter.composeapp.generated.resources.bible_load_failed_report_hint
import churchpresenter.composeapp.generated.resources.bible_load_failed_title
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.icons.generated.resources.ic_search
import org.churchpresenter.icons.generated.resources.ic_warning
import churchpresenter.composeapp.generated.resources.search_clear
import org.churchpresenter.bible.BibleLoadError
import org.churchpresenter.app.churchpresenter.viewmodel.indexOfFirstLiveVerse
import org.churchpresenter.app.churchpresenter.viewmodel.verseNumberOf
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.foundation.layout.width
import java.awt.Cursor
import org.churchpresenter.icons.generated.resources.Res as IconRes

@Composable
internal fun BibleLoadErrorBanner(errors: List<BibleLoadError>, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(IconRes.drawable.ic_warning),
                contentDescription = null,
                modifier = Modifier.size(18.dp).padding(top = 1.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val anyPartial = errors.any { it.partial }
                Text(
                    text = stringResource(
                        if (anyPartial) Res.string.bible_load_failed_partial_title
                        else Res.string.bible_load_failed_title
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                errors.forEach { error ->
                    Text(
                        text = stringResource(Res.string.bible_load_failed_detail, error.fileName, error.reason),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (anyPartial) {
                    Text(
                        text = stringResource(Res.string.bible_load_failed_partial_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    text = stringResource(Res.string.bible_load_failed_report_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun LiveChapterPanel(
    verses: List<String>,
    liveVerseNumbers: Set<Int>,
    onVerseClicked: ((verseNumber: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(verses) {
        val firstLiveIndex = indexOfFirstLiveVerse(verses, liveVerseNumbers)
        if (firstLiveIndex >= 0) listState.scrollToItem(firstLiveIndex)
    }

    LaunchedEffect(liveVerseNumbers) {
        val firstLiveIndex = indexOfFirstLiveVerse(verses, liveVerseNumbers)
        if (firstLiveIndex < 0 || firstLiveIndex + 1 >= verses.size) return@LaunchedEffect
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        val lastVisible = visibleItems.lastOrNull() ?: return@LaunchedEffect
        if (firstLiveIndex < lastVisible.index - 1) return@LaunchedEffect
        val viewportEnd = layoutInfo.viewportEndOffset
        val itemHeight = lastVisible.size.toFloat()
        val target2 = visibleItems.firstOrNull { it.index == firstLiveIndex + 2 }
        val target1 = visibleItems.firstOrNull { it.index == firstLiveIndex + 1 }
        val scrollAmount = when {
            target2 != null -> ((target2.offset + target2.size) - viewportEnd).toFloat().coerceAtLeast(0f)
            target1 != null -> ((target1.offset + target1.size) - viewportEnd + itemHeight).coerceAtLeast(0f)
            else -> itemHeight * 2
        }
        if (scrollAmount > 0f) listState.scroll { scrollBy(scrollAmount) }
    }

    Box(modifier = modifier.fillMaxWidth().padding(top = 8.dp).fillMaxHeight()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 4.dp, top = 4.dp, bottom = 4.dp, end = 12.dp)
        ) {
            itemsIndexed(verses) { _, verseStr ->
                val verseNum = verseNumberOf(verseStr)
                val isLive = verseNum != null && verseNum in liveVerseNumbers
                Text(
                    text = verseStr,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.5.sp,
                        lineHeight = 13.5.sp * 1.6f
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isLive) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                        .then(
                            if (onVerseClicked != null && verseNum != null)
                                Modifier.clickable { onVerseClicked(verseNum) }
                            else Modifier
                        )
                        .padding(6.dp)
                )
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(listState)
        )
    }
}

@Composable
internal fun BibleSearchField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modeChip: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(42.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(IconRes.drawable.ic_search),
            contentDescription = null,
            modifier = Modifier.padding(start = 11.dp).size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        )
        Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth()
                    .onFocusChanged { onFocusChanged(it.isFocused) }
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.Enter) {
                            onSubmit(); true
                        } else false
                    },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
            )
        }
        if (value.isNotEmpty()) {
            IconButton(
                onClick = onClear,

                modifier = Modifier.size(30.dp).testTag("bible_searchClear")
            ) {
                Icon(painter = painterResource(IconRes.drawable.ic_close), contentDescription = stringResource(Res.string.search_clear), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(modifier = Modifier.padding(end = 6.dp)) {
            modeChip()
        }
    }
}

@Composable
internal fun BibleBrowserColumn(
    items: List<String>,
    selectedIndex: Int,
    singleLine: Boolean = false,
    centerText: Boolean = false,
    rowHeight: Dp = 28.dp,
    onItemSelected: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0 && selectedIndex < items.size) {
            listState.animateScrollToItem(selectedIndex.coerceAtMost(items.size - 1))
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 8.dp)) {
            itemsIndexed(items) { index, item ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                        .clickable { onItemSelected(index) }
                        .padding(start = 12.dp, end = 4.dp),
                    contentAlignment = if (centerText) Alignment.Center else Alignment.CenterStart
                ) {
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = if (centerText) TextAlign.Center else TextAlign.Start
                    )
                }
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(listState)
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun BibleVerseColumn(
    verses: List<String>,
    selectedIndex: Int,
    selectedIndices: Set<Int>? = null,
    onItemSelected: (Int) -> Unit,
    onItemDoubleClicked: (Int) -> Unit = {},
    onItemCtrlClicked: (Int) -> Unit = {},
    onItemShiftClicked: (Int) -> Unit = {},
    onRightClicked: (Int) -> Unit = {},

    refCountFor: (Int) -> Int = { 0 },

    refCountTooltip: (Int) -> String = { "" },

    openRefIndex: Int = -1,
    onRefsClicked: (Int) -> Unit = {},

    refPopover: @Composable () -> Unit = {},
) {
    val listState = rememberLazyListState()
    LaunchedEffect(verses) {
        if (selectedIndex >= 0 && selectedIndex < verses.size) {
            listState.scrollToItem(selectedIndex.coerceAtMost(verses.size - 1))
        }
    }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex < 0 || selectedIndex + 1 >= verses.size) return@LaunchedEffect
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        val lastVisible = visibleItems.lastOrNull() ?: return@LaunchedEffect
        if (selectedIndex < lastVisible.index - 1) return@LaunchedEffect
        val viewportEnd = layoutInfo.viewportEndOffset
        val itemHeight = lastVisible.size.toFloat()
        val target2 = visibleItems.firstOrNull { it.index == selectedIndex + 2 }
        val target1 = visibleItems.firstOrNull { it.index == selectedIndex + 1 }
        val scrollAmount = when {
            target2 != null -> ((target2.offset + target2.size) - viewportEnd).toFloat().coerceAtLeast(0f)
            target1 != null -> ((target1.offset + target1.size) - viewportEnd + itemHeight).coerceAtLeast(0f)
            else -> itemHeight * 2
        }
        if (scrollAmount > 0f) listState.scroll { scrollBy(scrollAmount) }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 4.dp, top = 4.dp, bottom = 4.dp, end = 12.dp)
        ) {
            itemsIndexed(verses) { index, verseStr ->
                val isSelected = index == selectedIndex || (selectedIndices != null && index in selectedIndices)
                val refCount = refCountFor(index)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface
                        ),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = verseStr,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.5.sp,
                            lineHeight = 13.5.sp * 1.6f,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .pointerInput(index) {
                                var lastClickTime = 0L
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Main)
                                        if (event.type == PointerEventType.Press) {
                                            val isRight = event.button?.isSecondary == true
                                            val mods = event.keyboardModifiers
                                            val isCtrl = mods.isCtrlPressed || mods.isMetaPressed
                                            val isShift = mods.isShiftPressed
                                            when {
                                                isRight -> onRightClicked(index)
                                                isCtrl -> onItemCtrlClicked(index)
                                                isShift -> onItemShiftClicked(index)
                                                else -> {
                                                    val now = System.currentTimeMillis()
                                                    val isDouble = now - lastClickTime < 300L
                                                    lastClickTime = now
                                                    if (isDouble) onItemDoubleClicked(index) else onItemSelected(index)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            .padding(6.dp)
                    )
                    if (refCount > 0) {
                        Box(modifier = Modifier.padding(top = 6.dp, end = 2.dp)) {
                            CrossRefChip(
                                count = refCount,
                                active = index == openRefIndex,
                                tooltipText = refCountTooltip(refCount),
                                onClick = { onRefsClicked(index) },
                            )
                            if (index == openRefIndex) refPopover()
                        }
                    }
                }
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(listState)
        )
    }
}

@Composable
internal fun DragHandle(onDragEnd: () -> Unit, onDrag: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .width(4.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outlineVariant)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> onDrag(delta) },
                onDragStopped = { onDragEnd() }
            )
    )
}
