/*
 * The small controls the Background panel is assembled from — its captions, segmented toggles,
 * category pills, one swatch tile and one slider. Nothing here knows what a song background is.
 */
package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The design's section caption: 9px, bold, wide-tracked, upper case. */
@Composable
internal fun PanelCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.9.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier,
    )
}

/** The pill-shaped group the two segmented toggles sit in. */
@Composable
internal fun SegmentedRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
internal fun Segment(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(24.dp)
            .alpha(if (enabled) 1f else DISABLED_SEGMENT_ALPHA)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A segment the machine cannot offer — a video background with no VLC installed, say. */
private const val DISABLED_SEGMENT_ALPHA = 0.45f

/** A category pill: outlined, tinted when it is the one showing. */
@Composable
internal fun CategoryPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLowest
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(7.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One of the four Look presets. */
@Composable
internal fun PresetButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(23.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLowest
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One tile of the library grid: a 16:9 preview drawn by [fill], the design's badges over it, and
 * the name beneath.
 */
@Composable
internal fun SwatchTile(
    label: String,
    selected: Boolean,
    badge: SwatchBadge,
    onClick: () -> Unit,
    fill: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(SWATCH_ASPECT)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    2.dp,
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    RoundedCornerShape(8.dp),
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            fill()
            when (badge) {
                SwatchBadge.PLAY -> GlyphBadge(Icons.Default.PlayArrow)
                SwatchBadge.PLUS -> GlyphBadge(Icons.Default.Add)
                SwatchBadge.NONE -> Unit
            }
            if (selected) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                        .size(15.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", fontSize = 9.sp, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Which of the design's two centre badges a tile carries, if either. */
internal enum class SwatchBadge { NONE, PLAY, PLUS }

/**
 * The badge over a tile: a real icon rather than a text glyph, which a font's line box centers by
 * its baseline and so draws visibly high in the circle.
 */
@Composable
private fun GlyphBadge(glyph: ImageVector) {
    Box(
        modifier = Modifier.size(19.dp).clip(CircleShape).background(Color.Black.copy(alpha = BADGE_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = glyph,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = Color.White,
        )
    }
}

/**
 * The design's slider: a 4px track with a round knob, click anywhere to set. Not a Material Slider,
 * whose touch target is three times the height this panel has room for.
 */
@Composable
internal fun PanelSlider(label: String, readout: String, value: Int, max: Int, onChange: (Int) -> Unit) {
    // `pointerInput` keeps the block it was given until one of its keys changes, and `max` is a
    // constant per slider — so without this the block would hold the lambda from the composition
    // that created it, and that lambda closes over the whole background. Every drag would then
    // write back the background as it stood when the panel opened: set the dim, then the blur, and
    // the blur's write puts the old dim back. SlimSlider carries the same fix for the same reason.
    val currentOnChange by rememberUpdatedState(onChange)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PanelCaption(label, Modifier.weight(1f))
            Text(
                text = readout,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(13.dp)
                .pointerInput(max) {
                    detectTapGestures { offset ->
                        currentOnChange((offset.x / size.width * max).toInt().coerceIn(0, max))
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
            val fraction = if (max == 0) 0f else value.toFloat() / max
            Box(
                Modifier.fillMaxWidth(fraction).height(4.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Box(
                Modifier.fillMaxWidth(fraction).fillMaxHeight(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onPrimary))
            }
        }
    }
}

/** Every tile is drawn at the design's 16:9. */
private const val SWATCH_ASPECT = 16f / 9f
private const val BADGE_ALPHA = 0.55f
