package org.churchpresenter.hosanna.composables.field

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import org.churchpresenter.theme.AppRadius
import org.churchpresenter.theme.StateAlpha
import org.churchpresenter.theme.AppTheme
import org.churchpresenter.hosanna.composables.button.icon
import org.churchpresenter.hosanna.composables.defaults.FieldDefaults
import org.churchpresenter.hosanna.composables.foundation.Spinner
import org.churchpresenter.hosanna.composables.foundation.fieldColors
import org.churchpresenter.hosanna.composables.foundation.fieldState
import org.churchpresenter.hosanna.composables.foundation.focusRing
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_search
import org.jetbrains.compose.resources.painterResource

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

internal enum class SearchKey { Search, MoveDown, Clear, Leave, None }

internal fun searchKey(event: KeyEvent, query: String): SearchKey = when {
    event.type != KeyEventType.KeyDown -> SearchKey.None
    event.key == Key.Enter || event.key == Key.NumPadEnter -> SearchKey.Search
    event.key == Key.DirectionDown -> SearchKey.MoveDown
    event.key == Key.Escape -> if (query.isNotEmpty()) SearchKey.Clear else SearchKey.Leave
    else -> SearchKey.None
}

internal fun showKeyCap(shortcut: String?, query: String, focused: Boolean): Boolean =
    shortcut != null && query.isEmpty() && !focused

/**
 * A search field with a clear button and, while empty, the shortcut that focuses it.
 *
 * @param query the search text.
 * @param onQueryChange called with the new text, as the user types.
 * @param placeholder the hint shown while empty.
 * @param clearContentDescription the clear button's screen-reader name.
 * @param modifier applied to the outermost layout.
 * @param interactionSource drives the hover, pressed and focus looks; pass one to watch or freeze them.
 * @param compact uses the smaller height.
 * @param shortcut the key cap shown while empty and unfocused, such as "⌘F".
 * @param resultCount text at the end while searching, such as "12 results".
 * @param searching shows a spinner while results are on their way.
 * @param detection a chip at the end for something recognized in the query, such as a Bible reference.
 * @param onDetectionClick called when that chip is pressed.
 * @param onSearch called on Enter.
 * @param onMoveDown called on ↓, to move into the results.
 * @param enabled whether the field can be used.
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    clearContentDescription: String,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    compact: Boolean = false,
    shortcut: String? = null,
    resultCount: String? = null,
    searching: Boolean = false,
    detection: String? = null,
    onDetectionClick: () -> Unit = {},
    onSearch: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    enabled: Boolean = true,
) {
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current
    val colors = AppTheme.colors.fieldColors(fieldState(enabled, false, hovered, focused), error = false)
    val spec = tween<Color>(FieldDefaults.TRANSITION_MILLIS, easing = EaseOut)
    val container by animateColorAsState(colors.container, spec)
    val border by animateColorAsState(colors.border, spec)
    val muted = if (enabled) AppTheme.colors.onSurfaceVariant else colors.icon
    val shape = AppTheme.shapes.m
    val typography = AppTheme.typography
    val requester = remember { FocusRequester() }
    val clearable = query.isNotEmpty() && enabled
    Row(
        modifier = modifier
            .then(colors.ring?.let { Modifier.focusRing(it, AppRadius.M) } ?: Modifier)
            .hoverable(interactionSource, enabled)
            .background(container, shape)
            .border(FieldDefaults.Border, border, shape)
            .height(if (compact) FieldDefaults.SearchCompactHeight else FieldDefaults.SearchHeight)
            .pointerInput(enabled) { if (enabled) detectTapGestures { requester.requestFocus() } }
            .padding(
                start = FieldDefaults.HorizontalPadding,
                end = if (clearable) FieldDefaults.SupportingIconGap else FieldDefaults.HorizontalPadding,
            ),
        horizontalArrangement = Arrangement.spacedBy(FieldDefaults.IconGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FieldGlyph(painterResource(Res.drawable.ic_search), muted, FieldDefaults.Icon)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                BasicText(placeholder, style = typography.body.copy(color = muted), maxLines = 1)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(requester)
                    .onPreviewKeyEvent { event ->
                        when (searchKey(event, query)) {
                            SearchKey.Search -> onSearch()
                            SearchKey.MoveDown -> onMoveDown()
                            SearchKey.Clear -> onQueryChange("")
                            SearchKey.Leave -> focusManager.clearFocus()
                            SearchKey.None -> return@onPreviewKeyEvent false
                        }
                        true
                    },
                enabled = enabled,
                textStyle = typography.body.copy(color = colors.value),
                singleLine = true,
                cursorBrush = SolidColor(colors.caret),
                interactionSource = interactionSource,
            )
        }
        if (query.isNotEmpty() && detection != null) {
            DetectionChip(detection, enabled, onDetectionClick)
        }
        if (query.isNotEmpty() && searching) {
            Spinner(muted, FieldDefaults.Spinner)
        } else if (query.isNotEmpty() && resultCount != null) {
            BasicText(resultCount, style = typography.caption.copy(color = muted), maxLines = 1)
        }
        if (showKeyCap(shortcut, query, focused)) KeyCap(shortcut.orEmpty(), muted)
        if (clearable) {
            ClearButton(clearContentDescription, muted, Modifier, FieldDefaults.SearchClear) { onQueryChange("") }
        }
    }
}

@Composable
private fun KeyCap(text: String, color: Color) {
    Box(
        modifier = Modifier
            .height(FieldDefaults.KeyCapHeight)
            .background(AppTheme.colors.secondaryContainer, AppTheme.shapes.s)
            .padding(horizontal = FieldDefaults.KeyCapPadding),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = AppTheme.typography.caption.copy(color = color), maxLines = 1)
    }
}

@Composable
private fun DetectionChip(text: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val fill = if (hovered && enabled) {
        colors.onPrimaryContainer.copy(alpha = StateAlpha.HOVER).compositeOver(colors.primaryContainer)
    } else {
        colors.primaryContainer
    }
    Box(
        modifier = Modifier
            .height(FieldDefaults.DetectionHeight)
            .background(fill, AppTheme.shapes.s)
            .clickable(source, null, enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = FieldDefaults.DetectionPadding),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text,
            style = AppTheme.typography.captionStrong.copy(color = colors.onPrimaryContainer),
            maxLines = 1,
        )
    }
}
