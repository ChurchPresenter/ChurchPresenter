package org.churchpresenter.songlibrary.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.churchpresenter.songlibrary.menuMaxHeight

/**
 * A text field with nothing around it: the frame is drawn by whatever holds it.
 *
 * Material's `OutlinedTextField` is 56dp tall before any content, which is taller than a whole row
 * of this table. The chrome here is a border on the parent, so a field is exactly the height it is
 * given.
 */
@Composable
fun PlainTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    style: TextStyle = LibraryType.body,
    textModifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            Text(
                placeholder,
                style = style,
                color = scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = style.copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.primary),
            modifier = textModifier.fillMaxWidth(),
        )
    }
}

/**
 * A button that opens a panel under it.
 *
 * Compose's `DropdownMenu` brings Material's own surface and item padding with it, which is a
 * different shape from the rest of this window; this is the design's panel — dark, thin-bordered,
 * tight rows — with the same behaviour.
 */
@Composable
fun LibraryDropdown(
    label: String,
    highlighted: Boolean = false,
    menuWidth: Dp = 240.dp,
    leading: (@Composable () -> Unit)? = null,
    content: @Composable (close: () -> Unit) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    MenuAnchorBox { menuMaxHeight ->
        Row(
            Modifier.height(LibraryMetrics.control)
                .widthIn(max = 210.dp)
                .clip(RoundedCornerShape(LibraryMetrics.radius))
                .background(scheme.surfaceContainerHigh)
                .border(
                    1.dp,
                    if (highlighted) scheme.primary.copy(alpha = ACCENT_BORDER_ALPHA) else scheme.outlineVariant,
                    RoundedCornerShape(LibraryMetrics.radius),
                )
                .clickable { open = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(7.dp))
            }
            Text(
                label,
                style = LibraryType.button,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Default.ArrowDropDown,
                null,
                tint = scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA),
                modifier = Modifier.size(14.dp),
            )
        }
        if (open) {
            LibraryPopup(width = menuWidth, maxHeight = menuMaxHeight, onDismiss = { open = false }) {
                content { open = false }
            }
        }
    }
}

/**
 * A box that works out how tall a menu opened under it may be, and hands that to [content].
 *
 * Both halves of the sum are read **here and not inside the `Popup`**. A popup is its own
 * composition layer, so `LocalWindowInfo` read from within it is not dependably the app's window —
 * and a cap computed from the wrong number does not bind at all, which leaves the menu cut off in
 * exactly the way the cap exists to prevent. Out here the window is the window and the box's own
 * bounds are the button's, so the only thing the popup is told is a height.
 */
@Composable
fun MenuAnchorBox(modifier: Modifier = Modifier, content: @Composable (menuMaxHeight: Dp) -> Unit) {
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    var anchorBottom by remember { mutableStateOf(0.dp) }
    Box(
        modifier.onGloballyPositioned { coordinates ->
            anchorBottom = with(density) { (coordinates.positionInWindow().y + coordinates.size.height).toDp() }
        }
    ) {
        content(menuMaxHeight(windowHeight, anchorBottom))
    }
}

/**
 * The panel every menu in this window drops down: one surface, one border, one radius.
 *
 * [maxHeight] comes from [MenuAnchorBox], which measures it outside this popup; past it the panel
 * scrolls. Without that, a library with more song books than fit on screen dropped a list whose
 * bottom rows were never drawn, and nothing said they were there.
 */
@Composable
fun LibraryPopup(width: Dp, maxHeight: Dp, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val scroll = rememberScrollState()
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(Modifier.padding(top = 5.dp).width(width).heightIn(max = maxHeight)) {
            Column(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(scheme.surfaceContainerHigh)
                    .border(1.dp, scheme.outlineVariant, RoundedCornerShape(10.dp))
                    .padding(4.dp)
                    .verticalScroll(scroll),
            ) {
                content()
            }
            // Only once there is something to scroll to: a bar standing in an eight-row menu reads
            // as a list that has been cut off, which is the thing this is here to disprove.
            if (scroll.maxValue > 0) {
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scroll),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 5.dp),
                )
            }
        }
    }
}

/** One line of a menu: a tick or a box on the left, a count or a note on the right. */
@Composable
fun MenuRow(
    label: String,
    selected: Boolean = false,
    accent: Boolean = false,
    count: Int? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(if (leading != null) 20.dp else 14.dp), contentAlignment = Alignment.CenterStart) {
            when {
                leading != null -> leading()
                selected -> Icon(Icons.Default.Check, null, tint = scheme.primary, modifier = Modifier.size(11.dp))
            }
        }
        // `weight(1f)`, and the trailing count measured before it rather than given a weight of its
        // own: two weighted children split the row in half, which capped every label at half the
        // panel and ellipsised it there however much room was free. Two different song books both
        // came out as "Песнь Возрож...".
        Text(
            label,
            style = LibraryType.body,
            color = if (accent) scheme.primary else if (onClick == null) scheme.onSurfaceVariant else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        when {
            trailing != null -> trailing()
            count != null -> Text(
                count.toString(),
                style = LibraryType.small,
                color = scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA),
            )
        }
    }
}

@Composable
fun MenuDivider() {
    Box(
        Modifier.fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/** The alphas the recessive chrome is built from, over whatever the scheme's own text colour is. */
internal const val HAIRLINE_ALPHA = 0.07f
internal const val FAINT_TEXT_ALPHA = 0.55f
internal const val ACCENT_SURFACE_ALPHA = 0.10f
internal const val ACCENT_BORDER_ALPHA = 0.28f
internal const val SKELETON_ALPHA = 0.09f
internal const val SKELETON_HIGHLIGHT_ALPHA = 0.20f

/** The type scale of the design: small, tight, and even down the table. */
object LibraryType {
    val title = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
    val body = TextStyle(fontSize = 12.5.sp)
    val bodyStrong = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    val small = TextStyle(fontSize = 11.5.sp)
    val button = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val columnHead = TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.9.sp)
}

/** Corner radii, control heights and the row height the whole table is built on. */
object LibraryMetrics {
    val control = 32.dp
    val radius = 9.dp
    val panelRadius = 14.dp
    val rowHeight = 38.dp
    val headerHeight = 32.dp
}

/** A filled button in the accent colour: the one action a bar is really offering. */
@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, enabled: Boolean = true, icon: (@Composable () -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.height(LibraryMetrics.control)
            .clip(RoundedCornerShape(LibraryMetrics.radius))
            .background(if (enabled) scheme.primary else scheme.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            Box(Modifier.size(7.dp))
        }
        Text(
            label,
            style = LibraryType.button,
            color = if (enabled) scheme.onPrimary else scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA),
        )
    }
}

/** An outlined button: everything that is not the one action. */
@Composable
fun QuietButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    danger: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val border = when {
        !enabled -> scheme.onSurface.copy(alpha = HAIRLINE_ALPHA)
        danger -> scheme.error
        else -> scheme.outlineVariant
    }
    val text = when {
        !enabled -> scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA)
        // Drawn on `dangerSurface` below, so it is that surface's own foreground, not `danger`.
        danger -> scheme.onErrorContainer
        else -> scheme.onSurface
    }
    Row(
        Modifier.height(LibraryMetrics.control)
            .clip(RoundedCornerShape(LibraryMetrics.radius))
            .background(if (danger && enabled) scheme.errorContainer else scheme.surfaceContainerHigh)
            .border(1.dp, border, RoundedCornerShape(LibraryMetrics.radius))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            Box(Modifier.size(7.dp))
        }
        Text(label, style = LibraryType.button, color = text)
    }
}

/** The tick box the table and the menus share, drawn at the size the design uses. */
@Composable
fun LibraryCheckbox(checked: Boolean, indeterminate: Boolean = false, onToggle: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(15.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (checked || indeterminate) scheme.primary else Color.Transparent)
            .border(
                1.5.dp,
                if (checked || indeterminate) scheme.primary else scheme.outlineVariant,
                RoundedCornerShape(4.dp),
            )
            .then(
                if (onToggle == null) Modifier
                // Toggleable rather than clickable: this *is* a checkbox, and only the toggleable
                // form publishes that role and its on/off value. As a bare `clickable` it had no
                // semantics beyond "something you can press" — nothing that reads the tree, from a
                // screen reader to a test, could tell it apart from a cell or find its state.
                else Modifier.toggleable(
                    value = checked,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() },
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            checked -> Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = scheme.onPrimary,
                modifier = Modifier.size(11.dp),
            )
            indeterminate -> Box(Modifier.width(7.dp).height(2.dp).background(scheme.onPrimary))
        }
    }
}

@Composable
internal fun Hairline() {
    Box(
        Modifier.fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = HAIRLINE_ALPHA))
    )
}
