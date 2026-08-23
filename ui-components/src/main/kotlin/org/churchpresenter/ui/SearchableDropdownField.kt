package org.churchpresenter.ui

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
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
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.clear
import org.churchpresenter.resources.generated.resources.ic_arrow_down
import org.churchpresenter.resources.generated.resources.ic_close
import org.churchpresenter.resources.generated.resources.no_results_found
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * A dropdown whose value is also a search box: typing narrows the menu to the options that contain
 * what was typed.
 *
 * Worth reaching for wherever the list is too long to scan by scrolling — font families, or the
 * download browser's thousand-odd languages. For a handful of options prefer [DropdownSettingsField],
 * which is the same chrome without the text field.
 *
 * Typed text is local: [onValueChange] fires only on a real pick, and abandoning the field restores
 * whatever [value] was. Options are matched as plain substrings, so a label carrying several pieces
 * (a name, a code, a count) is searchable by any of them without extra plumbing.
 *
 * @param leadingIcon drawn before the text, inside the field's border.
 * @param menuWidth the popup's width, which need not match the field's — useful where the field is
 *   sized to a layout but the options are longer than it.
 * @param clearOnFocus empties the field on focus, showing [value] as a placeholder instead, so the
 *   first keystroke starts a fresh search rather than editing the current pick — and the menu opens
 *   on the whole list rather than on the one row the pick itself matches. Wanted wherever [value] is
 *   a long label nobody would want to clear by hand. Selecting the text instead of clearing it does
 *   not work here: a mouse click places the caret *after* focus arrives, undoing the selection.
 * @param fillWidth stretches the field to its parent, putting the chevron against the right edge
 *   instead of trailing the text. Only for a field given a width of its own — with none, filling
 *   resolves against the parent's constraints and blows the field out to whatever contains it.
 * @param onClear when given, shows a clear button that discards both the typed text and, through
 *   this callback, the selection behind it. Pass null wherever there is nothing to clear, which is
 *   what keeps the button from offering to undo a choice nobody has made.
 * @param valueTextStyle overrides the style of the value in the field, for a caller that renders the
 *   selection in something other than the default (a font picker previewing the font itself).
 * @param itemContent renders one option in the menu.
 */
@Composable
fun SearchableDropdownField(
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    leadingIcon: @Composable (() -> Unit)? = null,
    horizontalPadding: Dp = 8.dp,
    menuWidth: Dp = 200.dp,
    menuHeight: Dp = 300.dp,
    clearOnFocus: Boolean = false,
    fillWidth: Boolean = false,
    onClear: (() -> Unit)? = null,
    valueTextStyle: TextStyle? = null,
    itemContent: @Composable (String) -> Unit = { option ->
        Text(
            text = option,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    },
) {
    var expanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Local editable text. Resyncs to `value` whenever the prop changes (including
    // right after a pick round-trips back through the caller), so `value` never
    // receives free-form uncommitted text — onValueChange only fires on a real pick.
    var query by remember(value) { mutableStateOf(if (clearOnFocus) "" else value) }

    val filteredOptions = remember(options, query) {
        if (query.isBlank()) options else options.filter { it.contains(query, ignoreCase = true) }
    }

    val defaultValueStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = 13.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Box(
        modifier = modifier
            .heightIn(min = 42.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                focusRequester.requestFocus()
                expanded = true
            }
            .padding(start = horizontalPadding, end = horizontalPadding, top = 4.dp, bottom = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            // Only stretch when the caller imposed a width: with none, filling would resolve
            // against the parent's constraints and blow the field out to the width of its container.
            modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Box(modifier = Modifier.padding(end = 6.dp)) { leadingIcon() }
            }
            Column(
                modifier = if (fillWidth) Modifier.weight(1f) else Modifier,
                verticalArrangement = Arrangement.Center
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
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(1.dp))
                }
                BasicTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        expanded = true
                    },
                    singleLine = true,
                    maxLines = 1,
                    textStyle = valueTextStyle ?: defaultValueStyle,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurfaceVariant),
                    interactionSource = remember { MutableInteractionSource() },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        filteredOptions.singleOrNull()?.let { sole ->
                            query = if (clearOnFocus) "" else sole
                            onValueChange(sole)
                            expanded = false
                        }
                    }),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            // With clearOnFocus the field itself is empty, so the current pick is
                            // what stands in for it — the field never looks blank.
                            if (query.isEmpty() && value.isNotEmpty()) {
                                Text(
                                    text = value,
                                    style = valueTextStyle ?: defaultValueStyle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                expanded = true
                            } else {
                                expanded = false
                                query = if (clearOnFocus) "" else value
                            }
                        }
                )
            }
            Spacer(Modifier.width(4.dp))
            if (onClear != null) {
                Icon(
                    painter = painterResource(Res.drawable.ic_close),
                    contentDescription = stringResource(Res.string.clear),
                    modifier = Modifier
                        .size(14.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            // The typed text goes too: leaving a stale query behind would filter the
                            // menu by a selection that no longer exists.
                            query = if (clearOnFocus) "" else value
                            expanded = false
                            onClear()
                        },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
            }
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_down),
                contentDescription = null,
                modifier = Modifier
                    .size(14.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (expanded) {
                            expanded = false
                        } else {
                            focusRequester.requestFocus()
                            expanded = true
                        }
                    },
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                query = if (clearOnFocus) "" else value
            },
            containerColor = MaterialTheme.colorScheme.surface,
            // Without this the popup steals focus and the field stops accepting keystrokes.
            properties = PopupProperties(focusable = false),
            modifier = Modifier.width(menuWidth)
        ) {
            val scrollState = rememberScrollState()
            Box(modifier = Modifier.height(menuHeight).width(menuWidth)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(menuHeight)
                        .verticalScroll(scrollState)
                        .padding(end = 10.dp)
                ) {
                    if (filteredOptions.isEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(Res.string.no_results_found, query),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            },
                            onClick = {},
                            enabled = false
                        )
                    } else {
                        filteredOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { itemContent(option) },
                                onClick = {
                                    query = if (clearOnFocus) "" else option
                                    onValueChange(option)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState),
                    modifier = Modifier.align(Alignment.CenterEnd).height(menuHeight),
                    style = LocalScrollbarStyle.current.copy(
                        thickness = 8.dp,
                        minimalHeight = 24.dp,
                        unhoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        hoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                )
            }
        }
    }
}
