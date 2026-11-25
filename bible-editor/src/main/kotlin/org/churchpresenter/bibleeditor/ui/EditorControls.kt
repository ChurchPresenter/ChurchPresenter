package org.churchpresenter.bibleeditor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.theme.keyboardFocusRing
import org.churchpresenter.theme.semantic

/** The window's colors, every one a role of the app's theme. */
@Immutable
internal data class EditorColors(
    val ink: Color,
    val dim: Color,
    val faint: Color,
    val line: Color,
    val shell: Color,
    val side: Color,
    val raise: Color,
    val well: Color,
    val accent: Color,
    val onAccent: Color,
    val good: Color,
    val warn: Color,
    val bad: Color,
) {
    val accentBg: Color get() = accent.copy(alpha = TINT)
    val accentLine: Color get() = accent.copy(alpha = LINE_TINT)
    val goodBg: Color get() = good.copy(alpha = TINT)
    val warnBg: Color get() = warn.copy(alpha = TINT)
    val warnLine: Color get() = warn.copy(alpha = LINE_TINT)
    val badBg: Color get() = bad.copy(alpha = TINT)
    val hover: Color get() = ink.copy(alpha = HOVER)

    private companion object {
        const val TINT = 0.13f
        const val LINE_TINT = 0.45f
        const val HOVER = 0.06f
    }
}

@Composable
internal fun editorColors(): EditorColors {
    val scheme = MaterialTheme.colorScheme
    val semantic = MaterialTheme.semantic
    return EditorColors(
        ink = scheme.onSurface,
        dim = scheme.onSurfaceVariant,
        faint = scheme.onSurfaceVariant.copy(alpha = 0.8f),
        line = scheme.outlineVariant,
        shell = scheme.surface,
        side = scheme.surfaceContainerLow,
        raise = scheme.surfaceContainerHigh,
        well = scheme.surfaceContainerLowest,
        accent = scheme.primary,
        onAccent = scheme.onPrimary,
        good = semantic.success,
        warn = semantic.warning,
        bad = scheme.error,
    )
}

internal enum class ButtonTone { KEY, PRIMARY, QUIET }

internal val ButtonShape = RoundedCornerShape(9.dp)
private const val DISABLED_ALPHA = 0.45f

/** A text button in one of the window's three tones; [leading] is drawn before the label. */
@Composable
internal fun EditorButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.KEY,
    enabled: Boolean = true,
    height: Dp = 34.dp,
    fontSize: TextUnit = 13.sp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    colors: EditorColors = editorColors(),
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val (fill, ink) = when (tone) {
        ButtonTone.PRIMARY -> (if (enabled) colors.accent else colors.raise) to
            (if (enabled) colors.onAccent else colors.dim)
        ButtonTone.KEY -> colors.raise to colors.ink
        ButtonTone.QUIET -> (if (hovered && enabled) colors.hover else Color.Transparent) to
            (if (hovered && enabled) colors.ink else colors.dim)
    }
    Row(
        modifier = modifier
            .keyboardFocusRing(ButtonShape)
            .height(height)
            .alpha(if (enabled || tone == ButtonTone.PRIMARY) 1f else DISABLED_ALPHA)
            .clip(ButtonShape)
            .background(fill)
            .background(if (hovered && enabled && tone != ButtonTone.QUIET) colors.hover else Color.Transparent)
            .hoverable(interaction)
            .clickable(enabled = enabled, role = Role.Button, interactionSource = interaction, indication = null) {
                onClick()
            }
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        leading?.invoke(this)
        Text(
            text, color = ink, fontSize = fontSize, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (tone == ButtonTone.PRIMARY) FontWeight.Bold else FontWeight.SemiBold,
        )
        trailing?.invoke(this)
    }
}

/** An icon-only button; [description] is what a screen reader says and the hover tip. */
@Composable
internal fun EditorIconButton(
    painter: Painter,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = editorColors().dim,
    size: Dp = 30.dp,
    iconSize: Dp = 14.dp,
) {
    val colors = editorColors()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .keyboardFocusRing(shape)
            .size(size)
            .clip(shape)
            .background(if (hovered) colors.hover else Color.Transparent)
            .hoverable(interaction)
            .clickable(role = Role.Button, interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter, contentDescription = null, tint = if (hovered) colors.ink else tint,
            modifier = Modifier.size(iconSize))
    }
}

/** A small capitalized label on a tinted ground. */
@Composable
internal fun Pill(text: String, background: Color, ink: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(19.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(background)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), color = ink, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.7.sp)
    }
}

/** A row of segments, one selected, on a sunken track. */
@Composable
internal fun SegmentTrack(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.sp,
) {
    val colors = editorColors()
    Row(
        modifier
            .clip(ButtonShape)
            .background(colors.well)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val on = index == selected
            val shape = RoundedCornerShape(7.dp)
            Box(
                Modifier
                    .weight(1f)
                    .keyboardFocusRing(shape)
                    .height(28.dp)
                    .clip(shape)
                    .background(if (on) colors.raise else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, fontSize = fontSize, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    color = if (on) colors.ink else colors.dim, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The small all-caps heading above a group. */
@Composable
internal fun Caption(text: String, modifier: Modifier = Modifier, fontSize: TextUnit = 10.5.sp) {
    Text(
        text.uppercase(), modifier = modifier, color = editorColors().faint, fontSize = fontSize,
        fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp,
    )
}

/** A dot of [color], as the status strips and lists use. */
@Composable
internal fun Dot(color: Color, size: Dp = 6.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}
