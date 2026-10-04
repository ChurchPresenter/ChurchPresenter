package org.churchpresenter.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import org.churchpresenter.theme.AppShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.ButtonDefaults
import org.churchpresenter.theme.components.GhostButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.ok
import org.churchpresenter.strings.generated.resources.quick_background_add
import org.churchpresenter.strings.generated.resources.quick_backgrounds
import org.churchpresenter.strings.generated.resources.quick_backgrounds_help
import org.churchpresenter.strings.generated.resources.remove
import org.churchpresenter.strings.generated.resources.song_background_sample_line
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.QuickBackground
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * The backgrounds the preview panel's quick tray keeps one click away, under the preview because
 * that is where they are used from.
 *
 * The tray is a live control that writes nothing, so this is the only place a quick background is
 * added, edited, reordered or removed. A tile opens the very panel a song's own background is
 * edited in — the same swatch library, the same Look presets, the same dim and blur — because a
 * quick background *is* one of those, just kept on a shelf instead of inside a song.
 */
@Composable
internal fun QuickBackgroundsRail(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
) {
    val entries = settings.quickBackgrounds
    // A quick background is a full-screen background, so its tile is a picture of the output. It
    // was drawn 16:10 here and 16:9 in the song background picker -- one object, two shapes,
    // neither of them the screen's.
    val tileAspect = previewOutputSize(settings).aspectRatio
    var openId by remember { mutableStateOf<String?>(null) }
    // A removal, or a settings import, can take the open entry away underneath the panel.
    LaunchedEffect(entries.map { it.id }) {
        if (entries.none { it.id == openId }) openId = null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PanelCaption(stringResource(Res.string.quick_backgrounds))
            Text(
                text = "${entries.size} / $QUICK_BACKGROUND_SLOTS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(AppShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
        Text(
            text = stringResource(Res.string.quick_backgrounds_help),
            fontSize = 10.5.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        QuickBackgroundStrip(
            entries = entries,
            tileAspect = tileAspect,
            openId = openId,
            onOpenChange = { openId = it },
            onAdd = {
                val added = newQuickBackground()
                openId = added.id
                onSettingsChange { s -> s.copy(quickBackgrounds = s.quickBackgrounds + added) }
            },
            onChange = { updated ->
                onSettingsChange { s ->
                    s.copy(quickBackgrounds = s.quickBackgrounds.map { if (it.id == updated.id) updated else it })
                }
            },
            onRemove = { id ->
                if (openId == id) openId = null
                onSettingsChange { s -> s.copy(quickBackgrounds = s.quickBackgrounds.filterNot { it.id == id }) }
            },
            onReorder = { from, to ->
                onSettingsChange { s -> s.copy(quickBackgrounds = s.quickBackgrounds.moved(from, to)) }
            }
        )
    }
}

/**
 * The tray as the operator will see it, in the order it will show it — and where that order is set.
 *
 * The strip *is* the tray, so the slot numbers are on the tiles here too: the first tile is the one
 * Ctrl+1 reaches, and dragging a tile along the strip is how that changes.
 */
@Composable
private fun QuickBackgroundStrip(
    entries: List<QuickBackground>,
    tileAspect: Float,
    openId: String?,
    onOpenChange: (String?) -> Unit,
    onAdd: () -> Unit,
    onChange: (QuickBackground) -> Unit,
    onRemove: (String) -> Unit,
    onReorder: (Int, Int) -> Unit
) {
    val stridePx = with(LocalDensity.current) { (QUICK_STRIP_TILE_WIDTH + QUICK_STRIP_GAP).toPx() }
    var draggedIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }
    val dropIndex = dropIndexFor(draggedIndex, dragOffset, stridePx, entries.lastIndex)

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(QUICK_STRIP_GAP),
        verticalAlignment = Alignment.Top
    ) {
        entries.forEachIndexed { index, entry ->
            QuickBackgroundStripTile(
                entry = entry,
                slot = index + 1,
                tileAspect = tileAspect,
                open = entry.id == openId,
                dragging = index == draggedIndex,
                onOpenChange = { open -> onOpenChange(if (open) entry.id else null) },
                onChange = onChange,
                onRemove = { onRemove(entry.id) },
                modifier = Modifier
                    .zIndex(if (index == draggedIndex) 1f else 0f)
                    .graphicsLayer {
                        translationX = tileShift(index, draggedIndex, dropIndex, dragOffset, stridePx)
                    }
                    .pointerInput(entries.size, index) {
                        detectDragGestures(
                            onDragStart = {
                                draggedIndex = index
                                dragOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.x
                            },
                            onDragEnd = {
                                val to = dropIndexFor(index, dragOffset, stridePx, entries.lastIndex)
                                if (to != index) onReorder(index, to)
                                draggedIndex = -1
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                draggedIndex = -1
                                dragOffset = 0f
                            }
                        )
                    }
            )
        }
        if (entries.size < QUICK_BACKGROUND_SLOTS) {
            QuickBackgroundAddTile(tileAspect = tileAspect, onClick = onAdd)
        }
    }
}

/** One tile in the strip, and the panel it opens. */
@Composable
private fun QuickBackgroundStripTile(
    entry: QuickBackground,
    slot: Int,
    tileAspect: Float,
    open: Boolean,
    dragging: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onChange: (QuickBackground) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    // What the panel is editing, kept aside until OK. A tile is a saved thing, so abandoning an
    // edit has to put it back exactly — which the tab's own buffer cannot do on its own, since it
    // only knows the whole settings object and not which tile was being played with.
    var draft by remember(open, entry.id) { mutableStateOf(entry) }
    val shown = if (open) draft else entry

    Box(modifier = modifier) {
        Column(
            modifier = Modifier.width(QUICK_STRIP_TILE_WIDTH),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(tileAspect)
                    .clip(AppShape(7.dp))
                    .clickable { onOpenChange(!open) }
                    .border(
                        width = 2.dp,
                        color = when {
                            open -> MaterialTheme.colorScheme.primary
                            dragging -> MaterialTheme.colorScheme.outline
                            else -> MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = AppShape(7.dp)
                    )
            ) {
                SongBackgroundFill(shown.background, Modifier.fillMaxSize())
                SlotChip(slot, Modifier.align(Alignment.TopStart).padding(3.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = QUICK_SCRIM_ALPHA))
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.remove),
                        modifier = Modifier.size(9.dp),
                        tint = Color.White
                    )
                }
            }
            Text(
                text = quickBackgroundLabel(shown),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (open) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (open) {
            Popup(
                popupPositionProvider = remember { SongBackgroundPanelPosition },
                // Dismissing by any route other than OK is a cancel: the draft is dropped.
                onDismissRequest = { onOpenChange(false) },
                properties = PopupProperties(focusable = true)
            ) {
                val panelHeight = songBackgroundPanelHeight(SONG_BACKGROUND_PANEL_HEIGHT)
                Box(Modifier.size(SONG_BACKGROUND_PANEL_WIDTH, panelHeight)) {
                    SongBackgroundPanel(
                        background = draft.background,
                        lowerThirdBackground = draft.lowerThirdBackground,
                        onBackgroundChange = { draft = draft.copy(background = it) },
                        onLowerThirdBackgroundChange = { draft = draft.copy(lowerThirdBackground = it) },
                        sampleLine = stringResource(Res.string.song_background_sample_line),
                        // A song book is a song idea; a tray tile belongs to no book.
                        onApplyToSongbook = null,
                        onDismiss = { onOpenChange(false) },
                        stageAspect = tileAspect,
                        allowInherit = false,
                        allowInheritLowerThird = true,
                        footer = {
                            QuickBackgroundPanelFooter(
                                onCancel = { onOpenChange(false) },
                                onConfirm = {
                                    onChange(draft)
                                    onOpenChange(false)
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

/** OK and Cancel for a tray tile's panel — the tile is a saved setting, so an edit can be dropped. */
@Composable
private fun QuickBackgroundPanelFooter(onCancel: () -> Unit, onConfirm: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GhostButton(
            shape = AppShape(6.dp),
            onClick = onCancel,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
        ) {
            Text(stringResource(Res.string.cancel))
        }
        Spacer(Modifier.width(8.dp))
        RaisedButton(shape = AppShape(6.dp), onClick = onConfirm) {
            Text(stringResource(Res.string.ok))
        }
    }
}

/** The trailing tile: where a background gets into the tray in the first place. */
@Composable
private fun QuickBackgroundAddTile(tileAspect: Float, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(QUICK_STRIP_TILE_WIDTH),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(tileAspect)
                .clip(AppShape(7.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShape(7.dp))
                .clickable(onClick = onClick)
                .testTag(QUICK_BACKGROUND_ADD_TAG),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(Res.string.quick_background_add),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The slot number, on a scrim of its own so it reads over a white background and a black one. */
@Composable
private fun SlotChip(slot: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(AppShape(3.dp))
            .background(Color.Black.copy(alpha = QUICK_SCRIM_ALPHA))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(text = slot.toString(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/**
 * Where the tile being dragged would land if it were dropped now, or [dragged] when nothing moves.
 *
 * Pulled out of the composable so the drag maths is one expression both the live offsets and the
 * drop itself are computed from — the tiles shifting under the cursor and where the entry actually
 * ends up cannot disagree.
 */
internal fun dropIndexFor(dragged: Int, offset: Float, stridePx: Float, lastIndex: Int): Int {
    if (dragged < 0 || lastIndex < 0 || stridePx <= 0f) return dragged
    return (dragged + (offset / stridePx).roundToInt()).coerceIn(0, lastIndex)
}

/** How far the tile at [index] slides while a drag is in flight, to open a gap at the drop point. */
internal fun tileShift(index: Int, dragged: Int, drop: Int, offset: Float, stridePx: Float): Float = when {
    dragged < 0 -> 0f
    index == dragged -> offset
    dragged < drop && index in (dragged + 1)..drop -> -stridePx
    dragged > drop && index in drop until dragged -> stridePx
    else -> 0f
}

/** [this] with the item at [from] moved to [to], or [this] unchanged when that is a no-op. */
internal fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

internal const val QUICK_BACKGROUND_ADD_TAG = "quick_background_add"

private val QUICK_STRIP_TILE_WIDTH = 92.dp

private val QUICK_STRIP_GAP = 8.dp

private const val QUICK_SCRIM_ALPHA = 0.45f
