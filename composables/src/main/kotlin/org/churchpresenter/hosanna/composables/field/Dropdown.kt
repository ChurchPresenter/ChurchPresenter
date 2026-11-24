package org.churchpresenter.hosanna.composables.field

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.HosannaRadius
import org.churchpresenter.theme.HosannaTheme
import org.churchpresenter.theme.asOverline
import org.churchpresenter.hosanna.composables.button.icon
import org.churchpresenter.hosanna.composables.defaults.FieldDefaults
import org.churchpresenter.hosanna.composables.foundation.FieldState
import org.churchpresenter.hosanna.composables.foundation.dashedBorder
import org.churchpresenter.hosanna.composables.foundation.fieldColors
import org.churchpresenter.hosanna.composables.foundation.fieldState
import org.churchpresenter.hosanna.composables.foundation.focusRing
import org.churchpresenter.hosanna.composables.model.MenuEntry
import org.churchpresenter.hosanna.composables.model.MenuSearch
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_arrow_down
import org.jetbrains.compose.resources.painterResource
import org.churchpresenter.hosanna.composables.foundation.ThemeColors

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

internal const val CHEVRON_MILLIS: Int = 150
internal const val REOPEN_GUARD_MILLIS: Long = 250

internal fun opensMenu(event: KeyEvent): Boolean = event.type == KeyEventType.KeyDown &&
    (event.key == Key.Enter || event.key == Key.Spacebar || event.key == Key.DirectionDown)

internal fun labelOf(entries: List<MenuEntry>, selected: String?): String? =
    entries.firstNotNullOfOrNull { (it as? MenuEntry.Item)?.takeIf { item -> item.key == selected }?.label }

/**
 * A field that opens a menu to pick one entry. ↑ ↓ and Enter work in the menu, and Esc closes it.
 *
 * @param entries the menu's lines.
 * @param selectedKey the key of the picked item, or null.
 * @param onSelect called with the key of the item picked.
 * @param modifier applied to the outermost layout.
 * @param interactionSource drives the hover, pressed and focus looks; pass one to watch or freeze them.
 * @param label an optional label above the field.
 * @param placeholder shown while nothing is picked.
 * @param error an error under the field, which also turns its edge red.
 * @param search turns on a search field at the top of the menu.
 * @param enabled whether the field can be used.
 * @param readOnly shows the pick but does not open the menu.
 */
@Composable
fun Dropdown(
    entries: List<MenuEntry>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    label: String? = null,
    placeholder: String? = null,
    error: String? = null,
    search: MenuSearch? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    var dismissedAt by remember { mutableLongStateOf(0L) }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val state = if (expanded) FieldState.Focused else fieldState(enabled, readOnly, hovered, focused)
    val colors = ThemeColors.current.fieldColors(state, error != null)
    val spec = tween<Color>(FieldDefaults.TRANSITION_MILLIS, easing = EaseOut)
    val container by animateColorAsState(colors.container, spec)
    val border by animateColorAsState(colors.border, spec)
    val turn by animateFloatAsState(if (expanded) 180f else 0f, tween(CHEVRON_MILLIS))
    val density = LocalDensity.current
    var fieldWidth by remember { mutableStateOf(0.dp) }
    val requester = remember { FocusRequester() }
    val shape = HosannaTheme.shapes.m
    val typography = HosannaTheme.typography
    val value = labelOf(entries, selectedKey)
    val interactive = enabled && !readOnly
    fun open() {
        if (System.currentTimeMillis() - dismissedAt > REOPEN_GUARD_MILLIS) expanded = true
    }
    Column(modifier) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { fieldWidth = with(density) { it.width.toDp() } }
                    .then(colors.ring?.let { Modifier.focusRing(it, HosannaRadius.M) } ?: Modifier)
                    .background(container, shape)
                    .then(
                        if (readOnly) {
                            Modifier.dashedBorder(border, FieldDefaults.Border, HosannaRadius.M)
                        } else {
                            Modifier.border(FieldDefaults.Border, border, shape)
                        },
                    )
                    .height(if (label != null) FieldDefaults.LabeledHeight else FieldDefaults.CompactHeight)
                    .focusRequester(requester)
                    .onPreviewKeyEvent { event -> (interactive && opensMenu(event)).also { if (it) open() } }
                    .then(
                        if (interactive) {
                            Modifier.clickable(interactionSource, null, role = Role.DropdownList) { open() }
                        } else {
                            Modifier.focusable(enabled, interactionSource)
                        },
                    )
                    .padding(horizontal = FieldDefaults.HorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(FieldDefaults.IconGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    if (label != null) {
                        BasicText(label.asOverline(), style = typography.overline.copy(color = colors.label))
                        Spacer(Modifier.height(FieldDefaults.LabelGap))
                    }
                    BasicText(
                        value ?: placeholder.orEmpty(),
                        style = typography.body.copy(color = if (value != null) colors.value else colors.icon),
                        maxLines = 1,
                    )
                }
                if (!readOnly) {
                    FieldGlyph(
                        painterResource(Res.drawable.ic_arrow_down),
                        colors.icon,
                        FieldDefaults.Icon,
                        Modifier.rotate(turn),
                    )
                }
            }
            if (expanded) {
                DropdownMenu(
                    entries = entries,
                    selected = selectedKey,
                    onPick = {
                        onSelect(it)
                        expanded = false
                        requester.requestFocus()
                    },
                    onDismiss = {
                        expanded = false
                        dismissedAt = System.currentTimeMillis()
                    },
                    width = fieldWidth,
                    search = search,
                )
            }
        }
        Supporting("", null, error, null, enabled)
    }
}
