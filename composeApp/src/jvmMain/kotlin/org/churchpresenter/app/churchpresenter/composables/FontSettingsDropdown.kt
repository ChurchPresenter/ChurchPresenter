package org.churchpresenter.app.churchpresenter.composables

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_arrow_down
import churchpresenter.composeapp.generated.resources.no_results_found
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault

@Composable
fun FontSettingsDropdown(
    modifier: Modifier = Modifier,
    label: String = "",
    value: String,
    fonts: List<String>,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Local editable text. Resyncs to `value` whenever the prop changes (including
    // right after a pick round-trips back through the caller), so `value` never
    // receives free-form uncommitted text — onValueChange only fires on a real pick.
    var query by remember(value) { mutableStateOf(value) }
    val selectedFontFamily = remember(value) { systemFontFamilyOrDefault(value) }

    val filteredFonts = remember(fonts, query) {
        if (query.isBlank()) fonts else fonts.filter { it.contains(query, ignoreCase = true) }
    }

    Box(
        modifier = modifier
            .heightIn(min = 42.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                focusRequester.requestFocus()
                expanded = true
            }
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.Center) {
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
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = selectedFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurfaceVariant),
                    interactionSource = remember { MutableInteractionSource() },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        filteredFonts.singleOrNull()?.let { sole ->
                            query = sole
                            onValueChange(sole)
                            expanded = false
                        }
                    }),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) { innerTextField() }
                    },
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                expanded = true
                            } else {
                                expanded = false
                                query = value
                            }
                        }
                )
            }
            Spacer(Modifier.width(4.dp))
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
                query = value
            },
            containerColor = MaterialTheme.colorScheme.surface,
            properties = PopupProperties(focusable = false),
            modifier = Modifier.width(200.dp)
        ) {
            val scrollState = rememberScrollState()
            Box(modifier = Modifier.height(300.dp).width(200.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .verticalScroll(scrollState)
                        .padding(end = 10.dp)
                ) {
                    if (filteredFonts.isEmpty()) {
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
                        filteredFonts.forEach { font ->
                            val fontFamily = remember(font) { systemFontFamilyOrDefault(font) }
                            DropdownMenuItem(
                                text = { Text(font, style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontFamily)) },
                                onClick = {
                                    query = font
                                    onValueChange(font)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState),
                    modifier = Modifier.align(Alignment.CenterEnd).height(300.dp),
                    style = LocalScrollbarStyle.current.copy(
                        thickness = 8.dp,
                        minimalHeight = 24.dp,
                        unhoverColor = Color.Gray.copy(alpha = 0.5f),
                        hoverColor = Color.Gray.copy(alpha = 0.9f)
                    )
                )
            }
        }
    }
}
