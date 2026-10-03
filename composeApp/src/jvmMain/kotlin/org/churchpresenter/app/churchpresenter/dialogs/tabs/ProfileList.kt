package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.output_profile_delete
import org.churchpresenter.strings.generated.resources.output_profile_duplicate
import org.churchpresenter.strings.generated.resources.output_profiles_tab
import org.churchpresenter.strings.generated.resources.profile_list_hint
import org.churchpresenter.strings.generated.resources.profile_list_new
import org.churchpresenter.strings.generated.resources.profile_menu_create_linked
import org.churchpresenter.strings.generated.resources.profile_list_more_master
import org.churchpresenter.strings.generated.resources.profile_list_more_masters
import org.churchpresenter.strings.generated.resources.output_profile_move_down
import org.churchpresenter.strings.generated.resources.output_profile_move_up
import org.churchpresenter.strings.generated.resources.profile_menu_rename
import kotlin.math.roundToInt
import org.churchpresenter.sharedui.composables.SettingsScrollbar
import org.churchpresenter.settings.OWN_SECTION
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.KeyIconButton
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource

/** The profile list is this wide: a name, its badge, and what uses it under them. */
private val PROFILE_LIST_WIDTH = 250.dp

private const val HOVER_WASH_ALPHA = 0.06f
private const val DRAGGED_ALPHA = 0.85f
private val DROP_LINE = 2.dp
private val ROW_GAP = 3.dp

/** How far a linked profile sits in from its master. */
private val LINKED_INDENT = 18.dp

/** What the profile list can do to a profile, from its row, its menu or the keyboard. */
internal class ProfileListActions(
    val onSelect: (String) -> Unit,
    val onNew: () -> Unit,
    val onMove: (id: String, move: ProfileMove) -> Unit,
    /** Makes a new profile following this one. */
    val onCreateLinked: (String) -> Unit = {},
    val onRename: (id: String, name: String) -> Unit,
    val onDuplicate: (String) -> Unit,
    val onDelete: (String) -> Unit,
)

/** How a profile is moved in the list. */
internal sealed interface ProfileMove {
    /** This many places among its peers -- negative is up. */
    data class By(val delta: Int) : ProfileMove

    /** Into the gap before this row of the list, as a drag lands. */
    data class Drop(val gap: Int) : ProfileMove
}

/**
 * The left column: every profile, in the order every profile list in the app shows them.
 *
 * The order is the operator's. A profile is dragged by its handle, with a line showing where it will
 * land; moved a place with Alt+↑ / Alt+↓ on the selected row; or moved from its right-click menu,
 * which also renames it in place, duplicates it, makes a profile linked to it and deletes it.
 *
 * A linked profile sits indented under its master with how many settings it has changed, and moves
 * only among the master's other linked profiles; a master moves with all of them.
 */
@Composable
internal fun ProfilesList(
    profiles: List<OutputProfile>,
    selectedId: String?,
    usageOf: (String) -> List<String>,
    actions: ProfileListActions,
) {
    val palette = profilesPalette()
    // Where each row sits in the list, measured as it lays out: dragging works out its landing
    // place from these rather than from a fixed row height, since a long name or usage line is not
    // one.
    val rowTops = remember { mutableStateMapOf<String, Float>() }
    val rowHeights = remember { mutableStateMapOf<String, Float>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var renamingId by remember { mutableStateOf<String?>(null) }
    val dropIndex = draggingId?.let { id -> dropIndexFor(profiles, id, dragOffset, rowTops, rowHeights) }

    Column(
        modifier = Modifier
            .width(PROFILE_LIST_WIDTH)
            .fillMaxHeight()
            .background(palette.rail),
    ) {
        ProfileListHeader(actions.onNew)
        val scrollState = rememberScrollState()
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(ROW_GAP),
            ) {
                profiles.forEach { profile ->
                    // Keyed by the profile: after a move the row, and the focus in it, go with the
                    // profile rather than staying in the slot it left.
                    key(profile.id) {
                        val dragged = profile.id == draggingId
                        val peers = profiles.filter { it.parentId == profile.parentId }
                        val index = peers.indexOfFirst { it.id == profile.id }
                        ProfileListRow(
                            profile = profile,
                            selected = profile.id == selectedId,
                            usedBy = usageOf(profile.id),
                            renaming = profile.id == renamingId,
                            onRenameDone = { renamingId = null },
                            onRename = { actions.onRename(profile.id, it) },
                            menu = {
                                profileMenu(
                                    index = index,
                                    count = peers.size,
                                    onRename = { renamingId = profile.id },
                                    onDuplicate = { actions.onDuplicate(profile.id) },
                                    onCreateLinked = if (profile.parentId == null) {
                                        { actions.onCreateLinked(profile.id) }
                                    } else {
                                        null
                                    },
                                    onMoveUp = { actions.onMove(profile.id, ProfileMove.By(-1)) },
                                    onMoveDown = { actions.onMove(profile.id, ProfileMove.By(1)) },
                                    onDelete = { actions.onDelete(profile.id) },
                                )
                            },
                            onSelect = { actions.onSelect(profile.id) },
                            onMoveBy = { delta -> actions.onMove(profile.id, ProfileMove.By(delta)) },
                            handle = Modifier.pointerInput(profile.id) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingId = profile.id
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                    },
                                    onDragEnd = {
                                        val to = dropIndexFor(profiles, profile.id, dragOffset, rowTops, rowHeights)
                                        draggingId = null
                                        dragOffset = 0f
                                        if (to != null) actions.onMove(profile.id, ProfileMove.Drop(to))
                                    },
                                    onDragCancel = {
                                        draggingId = null
                                        dragOffset = 0f
                                    },
                                )
                            },
                            modifier = Modifier
                                .onGloballyPositioned {
                                    rowTops[profile.id] = it.positionInParent().y
                                    rowHeights[profile.id] = it.size.height.toFloat()
                                }
                                .then(
                                    if (dragged) {
                                        Modifier
                                            .offset { IntOffset(0, dragOffset.roundToInt()) }
                                            .graphicsLayer { alpha = DRAGGED_ALPHA }
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }
                }
            }
            // The landing line, drawn over the list at the gap the dragged row would drop into.
            if (dropIndex != null) {
                DropLine(dropLineTop(profiles, dropIndex, rowTops, rowHeights), scrollState.value)
            }
            SettingsScrollbar(scrollState)
        }
        ProfileListHint()
    }
}

/** PROFILES, and the "+" that makes a new one. */
@Composable
private fun ProfileListHeader(onNew: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GroupCaption(stringResource(Res.string.output_profiles_tab), Modifier.weight(1f))
        KeyIconButton(onClick = onNew, modifier = Modifier.size(30.dp).testTag(NEW_PROFILE_TAG)) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(Res.string.profile_list_new),
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

/** How the list is used, under it. */
@Composable
private fun ProfileListHint() {
    val palette = profilesPalette()
    Text(
        text = stringResource(Res.string.profile_list_hint),
        fontSize = 11.sp,
        lineHeight = 15.sp,
        color = palette.faintText,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
    )
}

/** The right-click menu of one row. */
@Composable
private fun profileMenu(
    index: Int,
    count: Int,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onCreateLinked: (() -> Unit)?,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
): List<ContextMenuItem> {
    val rename = stringResource(Res.string.profile_menu_rename)
    val duplicate = stringResource(Res.string.output_profile_duplicate)
    val createLinked = stringResource(Res.string.profile_menu_create_linked)
    val up = stringResource(Res.string.output_profile_move_up)
    val down = stringResource(Res.string.output_profile_move_down)
    val delete = stringResource(Res.string.output_profile_delete)
    return buildList {
        add(ContextMenuItem(rename, onRename))
        add(ContextMenuItem(duplicate, onDuplicate))
        if (onCreateLinked != null) add(ContextMenuItem(createLinked, onCreateLinked))
        if (index > 0) add(ContextMenuItem(up, onMoveUp))
        if (index < count - 1) add(ContextMenuItem(down, onMoveDown))
        add(ContextMenuItem(delete, onDelete))
    }
}

@Composable
private fun DropLine(topPx: Float, scroll: Int) {
    val density = LocalDensity.current
    val y = with(density) { (topPx - scroll).toDp() } - DROP_LINE
    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .offset(y = y)
            .height(DROP_LINE)
            .background(MaterialTheme.semantic.dropIndicator)
            .testTag(PROFILE_DROP_LINE_TAG),
    )
}

/**
 * One profile: its drag handle, its mode's dot, its name, what uses it, and its mode's badge -- or,
 * while it is being renamed, a field in place of the name.
 */
@Composable
private fun ProfileListRow(
    profile: OutputProfile,
    selected: Boolean,
    usedBy: List<String>,
    renaming: Boolean,
    onRenameDone: () -> Unit,
    onRename: (String) -> Unit,
    menu: @Composable () -> List<ContextMenuItem>,
    onSelect: () -> Unit,
    onMoveBy: (Int) -> Unit,
    handle: Modifier,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scheme = MaterialTheme.colorScheme
    val background = when {
        selected -> scheme.secondaryContainer
        hovered -> scheme.onSurface.copy(alpha = HOVER_WASH_ALPHA)
        else -> Color.Transparent
    }
    val items = menu()
    ContextMenuArea(items = { items }) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(AppShape(9.dp))
                .background(background)
                .hoverable(interaction)
                .onPreviewKeyEvent { event ->
                    // Alt+↑ / Alt+↓ move the focused profile a place, as the menu's Move does.
                    if (event.type != KeyEventType.KeyDown || !event.isAltPressed) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionUp -> { onMoveBy(-1); true }
                        Key.DirectionDown -> { onMoveBy(1); true }
                        else -> false
                    }
                }
                .clickable(onClick = onSelect)
                .testTag(profileRowTag(profile.id))
                .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val linked = profile.parentId != null
            if (linked) Spacer(Modifier.width(LINKED_INDENT))
            Icon(
                Icons.Filled.DragIndicator,
                contentDescription = null,
                tint = profilesPalette().faintText,
                modifier = Modifier.size(18.dp).then(handle).testTag(profileHandleTag(profile.id)),
            )
            if (linked) {
                Icon(
                    Icons.Filled.SubdirectoryArrowRight,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                )
            } else {
                ProfileModeDot(profile.displayMode)
            }
            Column(modifier = Modifier.weight(1f)) {
                if (renaming) {
                    RenameField(profile, onRename, onRenameDone)
                } else {
                    Text(
                        text = profile.displayName(),
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = listOfNotNull(usageText(usedBy), moreMastersNote(profile)).joinToString(" · "),
                    fontSize = 11.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(2.dp))
            when {
                !linked -> ProfileModeBadge(profile.displayMode)
                profile.overrides.isNotEmpty() -> ChangesChip(profile.overrides.size)
            }
        }
    }
}

/**
 * "+1 master" for a linked profile some of whose sections follow another master than the one it is
 * listed under, or null.
 */
@Composable
private fun moreMastersNote(profile: OutputProfile): String? {
    val more = profile.sectionMasters.values.filter { it != OWN_SECTION && it != profile.parentId }.distinct().size
    return when {
        more == 0 -> null
        more == 1 -> stringResource(Res.string.profile_list_more_master, more)
        else -> stringResource(Res.string.profile_list_more_masters, more)
    }
}

/** The name, editable where it stands. Enter or leaving the field finishes; every keystroke is kept. */
@Composable
private fun RenameField(profile: OutputProfile, onRename: (String) -> Unit, onDone: () -> Unit) {
    val focus = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = profile.name,
        onValueChange = onRename,
        singleLine = true,
        textStyle = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .onFocusChanged { state ->
                if (state.isFocused) hadFocus = true else if (hadFocus) onDone()
            }
            .onPreviewKeyEvent { event ->
                (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.Escape))
                    .also { if (it) onDone() }
            }
            .testTag(PROFILE_RENAME_FIELD_TAG),
    )
}

/** Test handle for one row of the profile list. */
internal fun profileRowTag(id: String): String = "profile_row_$id"

/** Test handle for one row's drag handle. */
internal fun profileHandleTag(id: String): String = "profile_handle_$id"

/** Test handle for the "+" that makes a new profile. */
internal const val NEW_PROFILE_TAG = "profile_new"

/** Test handle for the landing line shown while a profile is dragged. */
internal const val PROFILE_DROP_LINE_TAG = "profile_drop_line"

/** Test handle for the in-place rename field. */
internal const val PROFILE_RENAME_FIELD_TAG = "profile_rename_field"
