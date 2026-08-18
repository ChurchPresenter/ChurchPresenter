package songlibrary.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import songlibrary.menuMaxHeight

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
    val c = colors
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            Text(placeholder, style = style, color = c.textFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = style.copy(color = c.text),
            cursorBrush = SolidColor(c.accent),
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
    val c = colors
    var open by remember { mutableStateOf(false) }
    MenuAnchorBox { menuMaxHeight ->
        Row(
            Modifier.height(LibraryMetrics.control)
                .widthIn(max = 210.dp)
                .clip(RoundedCornerShape(LibraryMetrics.radius))
                .background(c.inputSurface)
                .border(
                    1.dp,
                    if (highlighted) c.accentBorder else c.border,
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
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowDropDown, null, tint = c.textFaint, modifier = Modifier.size(14.dp))
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
    val c = colors
    val scroll = rememberScrollState()
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(Modifier.padding(top = 5.dp).width(width).heightIn(max = maxHeight)) {
            Column(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(c.popupSurface)
                    .border(1.dp, c.border, RoundedCornerShape(10.dp))
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
    val c = colors
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
                selected -> Icon(Icons.Default.Check, null, tint = c.accent, modifier = Modifier.size(11.dp))
            }
        }
        // `weight(1f)`, and the trailing count measured before it rather than given a weight of its
        // own: two weighted children split the row in half, which capped every label at half the
        // panel and ellipsised it there however much room was free. Two different song books both
        // came out as "Песнь Возрож...".
        Text(
            label,
            style = LibraryType.body,
            color = if (accent) c.accentText else if (onClick == null) c.textMuted else c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        when {
            trailing != null -> trailing()
            count != null -> Text(count.toString(), style = LibraryType.small, color = c.textFaint)
        }
    }
}

@Composable
fun MenuDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp).height(1.dp).background(colors.border))
}
