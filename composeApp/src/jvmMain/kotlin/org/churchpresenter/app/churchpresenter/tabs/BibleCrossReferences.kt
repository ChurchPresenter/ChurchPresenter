package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.add_to_schedule
import churchpresenter.composeapp.generated.resources.bible_cross_references_close
import churchpresenter.composeapp.generated.resources.bible_cross_references_dismiss_hint
import churchpresenter.composeapp.generated.resources.bible_cross_references_keep_open
import churchpresenter.composeapp.generated.resources.bible_cross_references_none
import churchpresenter.composeapp.generated.resources.bible_cross_references_often_next
import churchpresenter.composeapp.generated.resources.bible_cross_references_passage
import churchpresenter.composeapp.generated.resources.bible_cross_references_source_count
import churchpresenter.composeapp.generated.resources.bible_cross_references_title
import churchpresenter.composeapp.generated.resources.chapter
import churchpresenter.composeapp.generated.resources.close
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.icons.generated.resources.ic_link
import org.churchpresenter.icons.generated.resources.ic_playlist_add
import churchpresenter.composeapp.generated.resources.verse
import org.churchpresenter.app.churchpresenter.composables.initialPassClickable
import org.churchpresenter.app.churchpresenter.composables.initialPassCombinedClickable
import org.churchpresenter.app.churchpresenter.data.formatCrossRefLabel
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.icons.generated.resources.Res as IconRes

private const val SELECTION_BAR_WIDTH = 4f

private val CROSS_REF_POPOVER_WIDTH = 380.dp

private val CROSS_REF_POPOVER_MAX_HEIGHT = 420.dp

internal data class CrossRefRow(
    val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val endVerse: Int?,

    val learned: Boolean,

    val label: String,

    val preview: String,

    val available: Boolean,

    val count: Int = 0,
)

internal fun crossRefRow(
    moduleRefFor: (bookId: Int, chapter: Int, verse: Int) -> BibleViewModel.ModuleRef?,
    fallbackAbbreviations: List<String>,
    bookId: Int,
    chapter: Int,
    verse: Int,
    endVerse: Int?,
    learned: Boolean,
    count: Int = 0,
): CrossRefRow {
    val moduleRef = moduleRefFor(bookId, chapter, verse)
    val abbreviation = moduleRef?.abbreviation
        ?: fallbackAbbreviations.getOrNull(bookId - 1).orEmpty()
    return CrossRefRow(
        bookId = bookId,
        chapter = chapter,
        verse = verse,
        endVerse = endVerse,
        learned = learned,

        label = formatCrossRefLabel(
            abbreviation,
            moduleRef?.chapter ?: chapter,
            moduleRef?.verse ?: verse,
            endVerse,
        ),
        preview = moduleRef?.text.orEmpty(),
        available = moduleRef != null,
        count = count,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CrossReferenceCard(
    row: CrossRefRow,
    selected: Boolean,
    striped: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onAddToSchedule: () -> Unit,

    showLearnedDot: Boolean = row.learned,
) {
    val background = when {
        selected -> MaterialTheme.colorScheme.surfaceVariant
        striped -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        else -> Color.Transparent
    }
    val markerColor = MaterialTheme.semantic.marker
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .background(background, RoundedCornerShape(9.dp))
            .drawBehind {
                if (selected) drawRect(color = markerColor, size = Size(SELECTION_BAR_WIDTH, size.height))
            },
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)

                .then(
                    if (row.available) Modifier.initialPassCombinedClickable(
                        onClick = onClick,
                        onDoubleClick = onDoubleClick,
                    ) else Modifier
                )
                .padding(start = 9.dp, top = 7.dp, bottom = 7.dp, end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (showLearnedDot) {
                    Box(
                        modifier = Modifier.size(4.dp)
                            .background(MaterialTheme.colorScheme.secondary, CircleShape)
                    )
                }
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (row.available) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.count > 0) {
                    Text(
                        text = stringResource(Res.string.bible_cross_references_source_count, row.count),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            if (row.preview.isNotEmpty()) {
                Text(
                    text = row.preview,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 12.sp * 1.55f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (row.available) {

            val addStr = stringResource(Res.string.add_to_schedule)
            Box(modifier = Modifier.padding(top = 5.dp, end = 5.dp)) {
                CrossRefActionButton(
                    painter = painterResource(IconRes.drawable.ic_playlist_add),
                    tooltipText = addStr,
                    contentDescription = "$addStr ${row.label}",
                    tint = MaterialTheme.colorScheme.secondary,
                    onClick = onAddToSchedule,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CrossRefActionButton(
    tooltipText: String,
    tint: Color,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    painter: Painter? = null,

    contentDescription: String = tooltipText,
) {
    TooltipArea(
        tooltip = {
            Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall) {
                Text(
                    tooltipText,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp)),
    ) {
        Box(
            modifier = Modifier.size(22.dp)
                .clip(RoundedCornerShape(6.dp))

                .initialPassClickable(onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(13.dp), tint = tint)
            } else if (painter != null) {
                Icon(painter, contentDescription = contentDescription, modifier = Modifier.size(13.dp), tint = tint)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CrossReferencePanel(
    rows: List<CrossRefRow>,
    selectedIndex: Int,
    onClick: (Int) -> Unit,
    onDoubleClick: (Int) -> Unit,
    onAddToSchedule: (Int) -> Unit,
    onClose: () -> Unit,

    passageSpan: String?,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val firstLearned = rows.indexOfFirst { it.learned }

    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        CrossReferenceHeader(

            title = passageSpan?.let { stringResource(Res.string.bible_cross_references_passage, it) }
                ?: stringResource(Res.string.bible_cross_references_title),
            onClose = onClose,
            closeTooltip = stringResource(Res.string.bible_cross_references_close),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (rows.isEmpty()) {
            CrossReferenceEmptyState(modifier = Modifier.fillMaxSize())
            return@Column
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(top = 4.dp, bottom = 4.dp, end = 8.dp),
            ) {
                itemsIndexed(rows) { idx, row ->
                    if (idx == firstLearned) {
                        Text(
                            text = stringResource(Res.string.bible_cross_references_often_next),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
                        )
                    }
                    CrossReferenceCard(
                        row = row,
                        selected = idx == selectedIndex,
                        striped = idx % 2 == 1,
                        onClick = { onClick(idx) },
                        onDoubleClick = { onDoubleClick(idx) },
                        onAddToSchedule = { onAddToSchedule(idx) },
                    )
                }
            }
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState = listState),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CrossReferenceHeader(
    title: String,
    onClose: () -> Unit,
    closeTooltip: String,
    onDock: (() -> Unit)? = null,
    dockTooltip: String = "",
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            painter = painterResource(IconRes.drawable.ic_link),
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onDock != null) {
            CrossRefActionButton(
                painter = painterResource(IconRes.drawable.ic_link),
                tooltipText = dockTooltip,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onDock,
            )
        }
        CrossRefActionButton(
            painter = painterResource(IconRes.drawable.ic_close),
            tooltipText = closeTooltip,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onClose,
        )
    }
}

@Composable
private fun CrossReferenceEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterVertically),
    ) {
        Icon(
            painter = painterResource(IconRes.drawable.ic_link),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        )
        Text(
            text = stringResource(Res.string.bible_cross_references_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun CrossReferencePopover(
    title: String,
    rows: List<CrossRefRow>,
    onDismiss: () -> Unit,
    onDock: () -> Unit,
    onOpen: (CrossRefRow) -> Unit,
    onGoLive: (CrossRefRow) -> Unit,
    onAddToSchedule: (CrossRefRow) -> Unit,
) {
    Popup(
        popupPositionProvider = remember { CrossRefPopoverPosition },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {

            Column(modifier = Modifier.width(CROSS_REF_POPOVER_WIDTH)) {
                CrossReferenceHeader(
                    title = title,
                    onClose = onDismiss,
                    closeTooltip = stringResource(Res.string.close),
                    onDock = onDock,
                    dockTooltip = stringResource(Res.string.bible_cross_references_keep_open),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (rows.isEmpty()) {
                    CrossReferenceEmptyState(modifier = Modifier.fillMaxWidth().height(110.dp))
                } else {

                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .heightIn(max = CROSS_REF_POPOVER_MAX_HEIGHT)
                            .verticalScroll(scrollState)
                            .padding(vertical = 4.dp),
                    ) {
                        rows.forEachIndexed { idx, row ->
                            CrossReferenceCard(
                                row = row,
                                selected = false,
                                striped = idx % 2 == 1,
                                onClick = { onOpen(row) },
                                onDoubleClick = { onGoLive(row) },
                                onAddToSchedule = { onAddToSchedule(row) },
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = stringResource(Res.string.bible_cross_references_dismiss_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

internal object CrossRefPopoverPosition : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val gap = 6
        val x = (anchorBounds.right - popupContentSize.width)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom + gap
        val y = if (below + popupContentSize.height <= windowSize.height) below
        else (anchorBounds.top - gap - popupContentSize.height)
            .coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CrossRefChip(
    count: Int,
    active: Boolean,
    tooltipText: String,
    onClick: () -> Unit,
) {
    val accent = if (active) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    TooltipArea(
        tooltip = {
            Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall) {
                Text(
                    tooltipText,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp)),
    ) {
        Row(
            modifier = Modifier
                .height(19.dp)
                .background(
                    if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    RoundedCornerShape(10.dp),
                )
                .border(
                    1.dp,
                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(10.dp),
                )

                .initialPassClickable(onClick)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                painter = painterResource(IconRes.drawable.ic_link),
                contentDescription = tooltipText,
                modifier = Modifier.size(9.dp),
                tint = accent,
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                color = accent,
                maxLines = 1,
            )
        }
    }
}
