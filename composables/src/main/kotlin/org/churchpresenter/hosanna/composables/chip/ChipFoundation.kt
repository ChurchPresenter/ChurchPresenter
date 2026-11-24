package org.churchpresenter.hosanna.composables.chip

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import org.churchpresenter.hosanna.HosannaColors
import org.churchpresenter.hosanna.HosannaStateAlpha
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.composables.button.ButtonState
import org.churchpresenter.hosanna.composables.button.buttonState
import org.churchpresenter.hosanna.composables.button.focusRing

enum class ChipSize { Default, Small }

enum class ChipColor {
    Neutral,
    Verse,
    Chorus,
    Bridge,
    Intro,
    Voice,
    OtherSection,
    Song,
    Bible,
    Presentation,
    Pictures,
    Media,
    LowerThird,
    Announcement,
}

@Immutable
internal data class ChipColors(
    val container: Color,
    val edge: Color,
    val content: Color,
    val sub: Color,
    val ring: Color?,
)

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

internal fun HosannaColors.accent(color: ChipColor): Color? = when (color) {
    ChipColor.Neutral -> null
    ChipColor.Verse -> verse
    ChipColor.Chorus -> chorus
    ChipColor.Bridge -> bridge
    ChipColor.Intro -> intro
    ChipColor.Voice -> voice
    ChipColor.OtherSection -> otherSection
    ChipColor.Song -> song
    ChipColor.Bible -> bible
    ChipColor.Presentation -> presentation
    ChipColor.Pictures -> pictures
    ChipColor.Media -> media
    ChipColor.LowerThird -> lowerThird
    ChipColor.Announcement -> announcement
}

internal fun HosannaColors.restChipColors(color: ChipColor, selected: Boolean): ChipColors {
    val accent = accent(color)
    return when {
        selected -> ChipColors(
            primaryContainer,
            primary.copy(alpha = HosannaStateAlpha.CONTENT_TYPE_EDGE),
            onPrimaryContainer,
            onPrimaryContainer,
            null,
        )
        accent != null -> ChipColors(
            accent.copy(alpha = HosannaStateAlpha.CONTENT_TYPE_CONTAINER),
            accent.copy(alpha = HosannaStateAlpha.CONTENT_TYPE_EDGE),
            accent,
            accent,
            null,
        )
        else -> ChipColors(secondaryContainer, outlineVariant, onSurface, onSurfaceVariant, null)
    }
}

internal fun HosannaColors.chipColors(color: ChipColor, selected: Boolean, state: ButtonState): ChipColors {
    val rest = restChipColors(color, selected)
    fun layer(alpha: Float) = rest.content.copy(alpha = alpha).compositeOver(rest.container)
    return when (state) {
        ButtonState.Rest, ButtonState.Loading -> rest
        ButtonState.Hovered -> rest.copy(container = layer(HosannaStateAlpha.HOVER))
        ButtonState.Pressed -> rest.copy(container = layer(HosannaStateAlpha.PRESSED))
        ButtonState.Focused -> rest.copy(
            container = layer(HosannaStateAlpha.FOCUS),
            ring = primary.copy(alpha = HosannaStateAlpha.FOCUS_RING),
        )
        ButtonState.Disabled -> {
            val ink = onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTENT)
            ChipColors(onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTAINER), Color.Transparent, ink, ink, null)
        }
    }
}

@Composable
internal fun chipTextStyle(size: ChipSize): TextStyle =
    if (size == ChipSize.Small) HosannaTheme.typography.captionStrong else HosannaTheme.typography.label

@Composable
internal fun ChipFrame(
    modifier: Modifier,
    interactionSource: MutableInteractionSource,
    size: ChipSize,
    color: ChipColor,
    selected: Boolean,
    enabled: Boolean,
    click: Modifier,
    padding: PaddingValues,
    content: @Composable RowScope.(ChipColors) -> Unit,
) {
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val colors = HosannaTheme.colors.chipColors(color, selected, buttonState(enabled, false, hovered, pressed, focused))
    val container by animateColorAsState(colors.container, tween(ChipDefaults.TRANSITION_MILLIS, easing = EaseOut))
    val shape = RoundedCornerShape(ChipDefaults.Radius)
    Row(
        modifier = modifier
            .then(colors.ring?.let { Modifier.focusRing(it, ChipDefaults.Radius) } ?: Modifier)
            .height(ChipDefaults.metrics(size).height)
            .background(container, shape)
            .border(ChipDefaults.Edge, colors.edge, shape)
            .then(click)
            .padding(padding),
        horizontalArrangement = Arrangement.spacedBy(ChipDefaults.IconGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content(colors)
    }
}

@Composable
internal fun ChipGlyph(icon: Painter, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    Image(icon, null, modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

@Composable
internal fun ChipCircleButton(
    icon: Painter,
    glyph: Dp,
    side: Dp,
    tint: Color,
    fill: Color,
    contentDescription: String,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
) {
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val active = enabled && (hovered || pressed || focused)
    Box(
        modifier = Modifier
            .size(side)
            .background(if (active) fill else Color.Transparent, HosannaTheme.shapes.full)
            .clickable(interactionSource, null, enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        ChipGlyph(icon, tint, glyph)
    }
}
