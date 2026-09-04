package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.core.models.text.TextBackdrop

/** A stored `#RRGGBB` at a stored 0-100 opacity, as the renderers read the same pair. */
internal fun String.backdropColor(opacity: Int): Color =
    parseHexColor(this).copy(alpha = (opacity / 100f).coerceIn(0f, 1f))

/**
 * A small swatch of what [backdrop] would draw — the fill behind a letter, the box around it.
 *
 * The button in the toolbar and every choice in its dialog are drawn with this, so the control
 * reports the look rather than naming it: which colour, how transparent, how round, and whether
 * there is a box at all are all visible without opening anything.
 *
 * [emptyOutline] and [emptyInk] are what it falls back to when the backdrop draws nothing of its
 * own — the surrounding button's own colours, so an off chip reads as an empty slot rather than as
 * a black one.
 *
 * Passing a null [label] draws a bar in place of the letter, which is what the four mode choices
 * use: at 26x15 a glyph is smaller than the difference between the modes, and the bar reads as
 * "text" at any size.
 */
@Composable
internal fun TextBackdropChip(
    backdrop: TextBackdrop,
    emptyOutline: Color,
    emptyInk: Color,
    modifier: Modifier = Modifier,
    label: String? = "A",
    fontSize: TextUnit = 9.sp,
) {
    val mode = backdrop.mode
    val fill = if (mode.drawsFill) {
        backdrop.lineBackgroundColor.backdropColor(backdrop.lineBackgroundOpacity)
    } else {
        Color.Transparent
    }
    val stroke = if (mode.drawsBorder) {
        backdrop.borderColor.backdropColor(backdrop.borderOpacity)
    } else {
        Color.Transparent
    }
    // The mark has to stay readable on whatever fill was chosen, so it takes its contrast from that
    // fill rather than from the theme -- white lyrics on a white plate is a valid setting and the
    // swatch still has to show the plate.
    val ink = when {
        fill.alpha > CHIP_INK_FILL_ALPHA ->
            if (fill.luminance() > CHIP_INK_LUMINANCE) Color.Black else Color.White
        mode == TextBackdropMode.OFF -> emptyInk
        else -> Color.White
    }
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier = modifier
            // A backing is drawn over a photo or a video, never over the settings panel, so the
            // swatch stands the look on a neutral ground rather than on the theme's own surface.
            // Without it half of what people set is invisible in the preview: a white outline on
            // the light theme, a black bar on the dark one.
            .background(if (mode == TextBackdropMode.OFF) Color.Transparent else CHIP_STAGE, shape)
            .background(fill, shape)
            .border(
                width = if (mode.drawsBorder) 1.5.dp else 1.dp,
                color = if (mode.drawsBorder) stroke else emptyOutline,
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (label == null) {
            Box(Modifier.width(CHIP_BAR_WIDTH).height(CHIP_BAR_HEIGHT).background(ink, RoundedCornerShape(2.dp)))
        } else {
            ChipLabel(label, ink, fontSize)
        }
    }
}

/**
 * The letter inside a chip, centred on the box rather than on its own baseline.
 *
 * A `Text` reserves the font's ascent and descent whatever the glyph is, and a capital A has no
 * descender — so centring the composable leaves the letter visibly high. Trimming the line box to
 * the glyph and centring what is left is what puts it in the middle of a 15dp chip.
 */
@Composable
private fun ChipLabel(label: String, ink: Color, fontSize: TextUnit) {
    Text(
        text = label,
        color = ink,
        maxLines = 1,
        textAlign = TextAlign.Center,
        style = TextStyle(
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.ExtraBold,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        ),
    )
}

/** Below this the fill is see-through enough that the button behind it decides the contrast. */
private const val CHIP_INK_FILL_ALPHA = 0.35f

/**
 * The neutral ground a look is previewed against, standing in for the picture behind the text.
 *
 * A mid grey rather than a dark one: the two colours people actually set are black and white, and
 * against a dark stage a black plate is indistinguishable from an empty one.
 */
private val CHIP_STAGE = Color(0xFF7C848F)
private const val CHIP_INK_LUMINANCE = 0.5f
private val CHIP_BAR_WIDTH = 14.dp
private val CHIP_BAR_HEIGHT = 2.5.dp

/** The tooltip surface the style buttons and the preset swatches share. */
@Composable
internal fun BackdropTooltip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        shape = MaterialTheme.shapes.extraSmall,
        tonalElevation = 4.dp,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** Square where it meets its neighbour, rounded where it does not, so a strip reads as one shape. */
internal fun segmentShape(index: Int, count: Int): RoundedCornerShape {
    val rounded = 8.dp
    val square = 0.dp
    return RoundedCornerShape(
        topStart = if (index == 0) rounded else square,
        bottomStart = if (index == 0) rounded else square,
        topEnd = if (index == count - 1) rounded else square,
        bottomEnd = if (index == count - 1) rounded else square,
    )
}

/** How far the theme outline is faded on an inactive control, shared so the row matches. */
internal const val OUTLINE_ALPHA = 0.5f
