package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import java.awt.Cursor
import org.churchpresenter.theme.isDarkScheme

internal val BibleListCardShape = RoundedCornerShape(14.dp)
internal val BibleListRowShape = RoundedCornerShape(9.dp)
internal val BibleVerseRowShape = RoundedCornerShape(10.dp)

private const val SELECTED_ALPHA_DARK = 0.18f
private const val SELECTED_ALPHA_LIGHT = 0.10f
private const val SELECTED_HOVER_EXTRA = 0.06f
private const val SELECTED_INK_TOWARD_TEXT = 0.35f
private const val ROW_HOVER_ALPHA = 0.05f
private const val CARD_EDGE_ALPHA_DARK = 0.05f
private const val CARD_EDGE_ALPHA_LIGHT = 0.07f
private const val GRIP_ALPHA = 0.16f
private const val LIVE_TINT_ALPHA = 0.12f

internal data class BibleRowColors(val background: Color, val ink: Color)

@Composable
@ReadOnlyComposable
private fun selectedTint(hovered: Boolean): Color {
    val scheme = MaterialTheme.colorScheme
    val base = if (isDarkScheme(scheme)) SELECTED_ALPHA_DARK else SELECTED_ALPHA_LIGHT
    return scheme.primary.copy(alpha = base + if (hovered) SELECTED_HOVER_EXTRA else 0f)
}

@Composable
@ReadOnlyComposable
internal fun bibleSelectedInk(): Color =
    lerp(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurface, SELECTED_INK_TOWARD_TEXT)

@Composable
@ReadOnlyComposable
internal fun bibleLiveTint(): Color = MaterialTheme.colorScheme.error.copy(alpha = LIVE_TINT_ALPHA)

@Composable
@ReadOnlyComposable
internal fun bibleRowColors(selected: Boolean, hovered: Boolean): BibleRowColors {
    val scheme = MaterialTheme.colorScheme
    return when {
        selected -> BibleRowColors(selectedTint(hovered), bibleSelectedInk())
        hovered -> BibleRowColors(scheme.onSurface.copy(alpha = ROW_HOVER_ALPHA), scheme.onSurface)
        else -> BibleRowColors(Color.Transparent, scheme.onSurfaceVariant)
    }
}

/** What a list card is filled with -- for anything drawn over one that has to be opaque. */
@Composable
@ReadOnlyComposable
internal fun bibleListCardFill(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (isDarkScheme(scheme)) scheme.surfaceContainer else scheme.surface
}

private const val INSET_SHADE_DARK = 0.18f
private const val INSET_SHADE_LIGHT = 0.04f

/** A section grouped inside a card: a shade darker than the card around it. */
@Composable
@ReadOnlyComposable
internal fun bibleInsetFill(): Color =
    lerp(
        bibleListCardFill(),
        Color.Black,
        if (isDarkScheme(MaterialTheme.colorScheme)) INSET_SHADE_DARK else INSET_SHADE_LIGHT,
    )

/** The rounded panel a list sits in. */
internal fun Modifier.bibleListCard(): Modifier = composed {
    val scheme = MaterialTheme.colorScheme
    val dark = isDarkScheme(scheme)
    val fill = bibleListCardFill()
    val edge = scheme.onSurface.copy(alpha = if (dark) CARD_EDGE_ALPHA_DARK else CARD_EDGE_ALPHA_LIGHT)
    this
        .shadow(1.dp, BibleListCardShape, clip = false)
        .background(fill, BibleListCardShape)
        .border(1.dp, edge, BibleListCardShape)
        .clip(BibleListCardShape)
}

/** The pointer-over state of one row, and the source to attach with `hoverable`. */
@Composable
internal fun rememberRowHover(): Pair<MutableInteractionSource, Boolean> {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    return interaction to hovered
}

/** An 8dp gap between two lists that resizes them: a short grip, accented while it is dragged. */
@Composable
internal fun DragHandle(onDragEnd: () -> Unit, onDrag: (Float) -> Unit) {
    val (interaction, hovered) = rememberRowHover()
    var dragging by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .width(8.dp)
            .fillMaxHeight()
            .background(
                if (hovered && !dragging) scheme.onSurface.copy(alpha = ROW_HOVER_ALPHA) else Color.Transparent,
                RoundedCornerShape(6.dp),
            )
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> onDrag(delta) },
                onDragStarted = { dragging = true },
                onDragStopped = { dragging = false; onDragEnd() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(40.dp)
                .background(
                    if (dragging) scheme.primary else scheme.onSurface.copy(alpha = GRIP_ALPHA),
                    RoundedCornerShape(3.dp),
                )
        )
    }
}
