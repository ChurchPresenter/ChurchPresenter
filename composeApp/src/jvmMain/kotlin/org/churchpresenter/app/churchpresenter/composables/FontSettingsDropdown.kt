package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.font_group_all
import org.churchpresenter.resources.generated.resources.font_group_matches
import org.churchpresenter.resources.generated.resources.font_group_recent
import org.churchpresenter.resources.generated.resources.font_group_recommended
import org.churchpresenter.resources.generated.resources.font_hidden_note
import org.churchpresenter.resources.generated.resources.font_picker_clear_search
import org.churchpresenter.resources.generated.resources.font_shown_note
import org.churchpresenter.resources.generated.resources.ic_arrow_down
import org.churchpresenter.resources.generated.resources.no_results_found
import org.churchpresenter.app.churchpresenter.utils.FontCatalogSnapshot
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import org.churchpresenter.app.churchpresenter.utils.rememberFontCatalog
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Room left around the panel so it never runs off the top or the bottom of the window. */
private val PANEL_WINDOW_MARGIN = 32.dp

/**
 * How much of the window the panel may take.
 *
 * Filling the window edge to edge is what a list of several hundred families will do given the
 * room, and a menu that tall reads as a screen rather than as a menu — three quarters leaves the
 * settings behind it visible and still shows a dozen families at a time.
 */
private const val PANEL_HEIGHT_FRACTION = 0.75f

/**
 * Font-family picker: the field opens a panel listing every installed family, each drawn in itself.
 *
 * The list is searched rather than scrolled — a machine has hundreds of families — and it is grouped
 * so that the first screenful is worth reading: what this session has already used, then the ones
 * that hold up at 70pt across a hall, then the rest. Each row says which scripts the family covers,
 * because a verse set in a family with no Cyrillic comes out of the fallback font instead, and the
 * preview at the bottom shows that in black and white, as the room will see it.
 */
@Composable
fun FontSettingsDropdown(
    modifier: Modifier = Modifier,
    label: String = "",
    value: String,
    fonts: List<String>,
    /**
     * Stretches the field to its parent, putting the chevron against the right edge instead of
     * trailing the name. Only for a field whose parent imposes a width — with none, filling resolves
     * against the parent's constraints and blows the field out to whatever contains it.
     */
    fillWidth: Boolean = false,
    /**
     * What the preview quotes, for a caller holding a translation of its own. Empty leaves it on the
     * app's loaded translations, which is right everywhere but the canvas Bible source.
     */
    previewLines: List<String> = emptyList(),
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val catalog = rememberFontCatalog(fonts, keep = value)

    // The popup sits inside the field so that it anchors to it, and so the caller's modifier — a
    // width, a weight — lands on the field itself rather than on a wrapper around it.
    FontPickerTrigger(
        label = label,
        value = value,
        expanded = expanded,
        fillWidth = fillWidth,
        modifier = modifier,
        onClick = { expanded = !expanded },
    ) {
        if (expanded) {
            FontPickerPopup(
                value = value,
                catalog = catalog,
                previewLines = previewLines,
                onDismiss = { expanded = false },
                onPick = { picked ->
                    RecentFonts.record(picked)
                    onValueChange(picked)
                    expanded = false
                },
            )
        }
    }
}

/** The field itself: the current family, drawn in itself, and a chevron that turns when it opens. */
@Composable
private fun FontPickerTrigger(
    label: String,
    value: String,
    expanded: Boolean,
    fillWidth: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    popup: @Composable () -> Unit,
) {
    val caret by animateFloatAsState(if (expanded) 180f else 0f, label = "fontPickerCaret")
    Box(
        modifier = modifier
            .heightIn(min = 42.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .border(
                1.dp,
                if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(8.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                // It reads as a plain box otherwise: the field opens a list, and both a screen
                // reader and a test have to be able to tell that from the label and value alone.
                role = Role.DropdownList,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = if (fillWidth) Modifier.weight(1f) else Modifier,
                verticalArrangement = Arrangement.Center,
            ) {
                if (label.isNotEmpty()) {
                    Text(
                        text = label.uppercase(),
                        fontSize = 10.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(1.dp))
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = remember(value) { systemFontFamilyOrDefault(value) },
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_down),
                contentDescription = null,
                modifier = Modifier.size(14.dp).rotate(caret),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        popup()
    }
}

/**
 * Top-aligned with the field it belongs to, clamped into the window.
 *
 * Hanging the panel *below* the field is what will not fit: it is as tall as a dialog and the
 * pickers it serves sit low in settings panels. Covering the field costs nothing — the family it
 * shows is the one already ticked in the list.
 */
private class FontPanelPosition(private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val lowest = (windowSize.height - popupContentSize.height - margin).coerceAtLeast(0)
        return IntOffset(x, anchorBounds.top.coerceIn(0, lowest))
    }
}

@Composable
private fun FontPickerPopup(
    value: String,
    catalog: FontCatalogSnapshot,
    previewLines: List<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val margin = with(LocalDensity.current) { PANEL_WINDOW_MARGIN.roundToPx() }
    Popup(
        popupPositionProvider = remember(margin) { FontPanelPosition(margin) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        FontPickerPanel(
            value = value,
            catalog = catalog,
            previewLines = previewLines,
            onDismiss = onDismiss,
            onPick = onPick,
        )
    }
}

/**
 * The panel itself, taking the catalog rather than measuring one.
 *
 * `internal` so tests and screenshots can drive it with a catalog they wrote: measured through the
 * machine's own installed fonts, every assertion about a badge, a group or a warning would say
 * something different on a machine with different fonts, and CI has almost none.
 */
@Composable
internal fun FontPickerPanel(
    value: String,
    catalog: FontCatalogSnapshot,
    previewLines: List<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var highlighted by remember { mutableStateOf(value) }
    var keyMoves by remember { mutableIntStateOf(0) }

    val groups = remember(catalog.faces, query, RecentFonts.names) {
        groupFonts(catalog.faces, query, RecentFonts.names)
    }
    val visible = remember(groups) { visibleFonts(groups) }
    LaunchedEffect(visible) { highlighted = highlightAfterFilter(visible, highlighted) }

    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { searchFocus.requestFocus() }

    fun move(step: Int): Boolean {
        if (visible.isEmpty()) return false
        val at = visible.indexOfFirst { it.name == highlighted }
        highlighted = visible[(if (at < 0) 0 else at + step).coerceIn(visible.indices)].name
        keyMoves++
        return true
    }

    val maxHeight = with(LocalDensity.current) {
        val room = LocalWindowInfo.current.containerSize.height.toDp() - PANEL_WINDOW_MARGIN * 2
        (room * PANEL_HEIGHT_FRACTION).coerceAtLeast(240.dp)
    }

    Surface(
        modifier = Modifier
            .width(FONT_PANEL_WIDTH)
            .heightIn(max = maxHeight)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key) {
                        Key.DirectionDown -> move(1)
                        Key.DirectionUp -> move(-1)
                        Key.Enter, Key.NumPadEnter -> highlighted.isNotEmpty().also { if (it) onPick(highlighted) }
                        Key.Escape -> true.also { onDismiss() }
                        else -> false
                    }
                }
            },
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 12.dp,
    ) {
        Column {
            Box(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 8.dp)) {
                FontSearchRow(
                    query = query,
                    onQueryChange = { query = it },
                    onClear = { query = "" },
                    focusRequester = searchFocus,
                )
            }
            FontPickerList(
                groups = groups,
                note = catalogNote(catalog, visible.size),
                query = query,
                selected = value,
                highlighted = highlighted,
                keyMoves = keyMoves,
                onPick = onPick,
                onHighlight = { highlighted = it },
                onClearSearch = { query = "" },
                modifier = Modifier.weight(1f, fill = false),
            )
            val quoted = previewLines.ifEmpty { fontPreviewLines() }
            visible.firstOrNull { it.name == highlighted }
                ?.let { FontPreviewPane(it, catalog.measured, quoted) }
            FontPickerFooter()
        }
    }
}

/** What the panel says about itself: how much of the machine's set it is showing, and what it left out. */
@Composable
private fun catalogNote(catalog: FontCatalogSnapshot, shown: Int): String =
    if (catalog.hiddenCount > 0) {
        stringResource(Res.string.font_hidden_note, catalog.hiddenCount, shown, catalog.faces.size)
    } else {
        stringResource(Res.string.font_shown_note, shown, catalog.faces.size)
    }

@Composable
private fun FontPickerList(
    groups: List<FontGroup>,
    note: String,
    query: String,
    selected: String,
    highlighted: String,
    keyMoves: Int,
    onPick: (String) -> Unit,
    onHighlight: (String) -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember(groups) { flattenGroups(groups) }
    val listState = rememberLazyListState()
    // Only the keys scroll the list: hovering moves the highlight too, and a list that scrolls under
    // the pointer takes the row out from under it.
    LaunchedEffect(keyMoves) {
        if (keyMoves > 0) {
            rows.indexOfFirst { it is FontRow.Family && it.face.name == highlighted }
                .takeIf { it >= 0 }
                ?.let { listState.scrollToItem(it) }
        }
    }
    Box(modifier = modifier.heightIn(min = 60.dp)) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            items(rows) { row ->
                when (row) {
                    is FontRow.Header -> FontGroupHeader(row)
                    is FontRow.Family -> FontFamilyRow(
                        face = row.face,
                        selected = row.face.name == selected,
                        highlighted = row.face.name == highlighted,
                        onPick = { onPick(row.face.name) },
                        onHover = { onHighlight(row.face.name) },
                    )
                }
            }
            if (rows.isEmpty()) {
                item { FontPickerNoResults(query = query, onClearSearch = onClearSearch) }
            }
            // Under the last row rather than in the footer: it is a remark about the list, and it is
            // far too long to sit beside the key hints without being cut off.
            item { FontCatalogNote(note) }
        }
    }
}

@Composable
private fun FontPickerNoResults(query: String, onClearSearch: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.no_results_found, query),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .height(24.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClearSearch,
                )
                .padding(horizontal = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.font_picker_clear_search),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** Headings and families in one list, which is what the lazy column scrolls over. */
private fun flattenGroups(groups: List<FontGroup>): List<FontRow> = groups.flatMap { group ->
    listOf(FontRow.Header(group.kind, group.items.size)) + group.items.map { FontRow.Family(it) }
}

/** The heading a group is drawn with. */
@Composable
internal fun groupLabel(kind: FontGroupKind): String = when (kind) {
    FontGroupKind.RECENT -> stringResource(Res.string.font_group_recent)
    FontGroupKind.RECOMMENDED -> stringResource(Res.string.font_group_recommended)
    FontGroupKind.ALL -> stringResource(Res.string.font_group_all)
    FontGroupKind.MATCHES -> stringResource(Res.string.font_group_matches)
}

/** Its colour, which is how strong a recommendation the heading is. */
@Composable
internal fun groupColor(kind: FontGroupKind) = when (kind) {
    FontGroupKind.RECENT, FontGroupKind.MATCHES -> MaterialTheme.colorScheme.primary
    FontGroupKind.RECOMMENDED -> MaterialTheme.semantic.success
    FontGroupKind.ALL -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
}
