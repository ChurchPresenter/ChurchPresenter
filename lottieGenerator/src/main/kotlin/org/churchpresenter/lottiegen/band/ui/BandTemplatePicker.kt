package org.churchpresenter.lottiegen.band.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.lottiegen.band.BandStyle
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.lottiegen.ui.Strings
import org.churchpresenter.lottiegen.ui.Tokens

private val CHIP_SIZE = 44.dp to 26.dp
private val TEMPLATE_ROW_HEIGHT = 38.dp

/** The style, chosen from a list that shows each one as a small swatch of its shapes. */
@Composable
internal fun TemplatePicker(cfg: BibleLottieGenConfig, onPick: (BandStyle) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var anchorWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val count = cfg.roles().size
    Box(Modifier.onSizeChanged { anchorWidth = with(density) { it.width.toDp() } }) {
        CaptionedButton(
            caption = Strings.bandTemplate,
            value = Strings.bandEnumLabel("style", cfg.bandStyle.name),
            open = open,
            onClick = { open = !open },
            leading = { StyleChip(cfg.bandStyle, cfg) },
            trailing = { CountBadge(Strings.bandTemplateColors(count)) },
        )
        if (open) {
            PopupMenu(
                onDismiss = { open = false },
                width = anchorWidth,
                keys = MenuKeys(BandStyle.entries.indexOf(cfg.bandStyle), BandStyle.entries.size, TEMPLATE_ROW_HEIGHT) {
                    onPick(BandStyle.entries[it])
                },
            ) {
                BandStyle.entries.forEach { style ->
                    TemplateRow(style, cfg, selected = style == cfg.bandStyle) {
                        onPick(style)
                        open = false
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateRow(style: BandStyle, cfg: BibleLottieGenConfig, selected: Boolean, onClick: () -> Unit) {
    val count = 2 + (if (style.usesSecond) 1 else 0) + (if (style.usesTertiary) 1 else 0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TEMPLATE_ROW_HEIGHT)
            .clip(FIELD_SHAPE)
            .background(menuRowBackground(selected))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        StyleChip(style, cfg)
        Text(
            Strings.bandEnumLabel("style", style.name), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = if (selected) Tokens.Accent else Tokens.PrimaryText, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Text(
            Strings.bandTemplateColors(count), fontSize = 9.5.sp, fontWeight = FontWeight.Bold,
            color = Tokens.LabelText,
        )
        SelectedMark(selected)
    }
}

@Composable
private fun CountBadge(text: String) {
    Text(
        text, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Tokens.Accent, maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(Tokens.LogoChipBg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * A 44×26 swatch of a style: the background colour with the second colour cut into the shape
 * the style is known by. A likeness, not the band — enough to tell the styles apart in a list.
 */
@Composable
private fun StyleChip(style: BandStyle, cfg: BibleLottieGenConfig) {
    val base = parseBandHex(cfg.bgColor) ?: Tokens.FieldBg
    val second = parseBandHex(cfg.gradientColor) ?: Tokens.Accent
    val accent = parseBandHex(cfg.accentColor) ?: Tokens.Accent
    Canvas(
        Modifier
            .size(CHIP_SIZE.first, CHIP_SIZE.second)
            .clip(RoundedCornerShape(5.dp))
            .border(1.dp, Tokens.FieldBorder, RoundedCornerShape(5.dp)),
    ) {
        drawRect(base)
        chipShape(style, size)?.let { (path, color) -> drawPath(path, if (color == ChipInk.SECOND) second else accent) }
    }
}

private enum class ChipInk { SECOND, ACCENT }

/** A shape on a chip: the colour it takes and its corners, as fractions of the chip. */
private class ChipMark(val ink: ChipInk, val points: List<Pair<Float, Float>>)

private fun mark(ink: ChipInk, vararg points: Pair<Float, Float>) = ChipMark(ink, points.toList())
private fun box(ink: ChipInk, x: Float, y: Float, w: Float, h: Float) =
    mark(ink, x to y, x + w to y, x + w to y + h, x to y + h)

/** The one shape that stands for each style on its chip; a plain bar has none. */
private val CHIP_MARKS: Map<BandStyle, ChipMark> = mapOf(
    BandStyle.GRADIENT_BAR to box(ChipInk.SECOND, 0.5f, 0f, 0.5f, 1f),
    BandStyle.GRADIENT_HORIZONTAL to box(ChipInk.SECOND, 0.5f, 0f, 0.5f, 1f),
    BandStyle.GRADIENT_ANGLED to mark(ChipInk.SECOND, 1f to 0f, 1f to 1f, 0f to 1f),
    BandStyle.GRADIENT_TRIO to box(ChipInk.SECOND, 0.33f, 0f, 0.34f, 1f),
    BandStyle.ACCENT_EDGE_BAR to box(ChipInk.ACCENT, 0f, 0f, 0.09f, 1f),
    BandStyle.GLASS_PANEL to box(ChipInk.ACCENT, 0f, 0.85f, 1f, 0.15f),
    BandStyle.RIBBON to box(ChipInk.ACCENT, 0f, 0f, 1f, 0.16f),
    BandStyle.SPLIT_SHUTTER to box(ChipInk.SECOND, 0f, 0.5f, 1f, 0.5f),
    BandStyle.HORIZONTAL_BANDS to box(ChipInk.SECOND, 0f, 0.78f, 1f, 0.22f),
    BandStyle.TRICOLOR_DIAGONAL to mark(ChipInk.SECOND, 0.4f to 0f, 0.72f to 0f, 0.64f to 1f, 0.32f to 1f),
    BandStyle.ANGLED_BLADE to mark(ChipInk.SECOND, 0f to 0f, 0.28f to 0f, 0.2f to 1f, 0f to 1f),
    BandStyle.SLANTED_THIRDS to mark(ChipInk.SECOND, 0.82f to 0f, 1f to 0f, 1f to 1f, 0.74f to 1f),
    BandStyle.CROSSED_BANDS to mark(ChipInk.SECOND, 0.62f to 0f, 0.71f to 0f, 0.79f to 1f, 0.7f to 1f),
    BandStyle.CHEVRON_TAG to mark(ChipInk.SECOND, 0f to 0f, 0.22f to 0f, 0.27f to 0.5f, 0.22f to 1f, 0f to 1f),
    BandStyle.CORNER_WEDGES to mark(ChipInk.SECOND, 0f to 0f, 0.2f to 0f, 0f to 1f),
    BandStyle.ARCH_DECK to mark(ChipInk.SECOND, 0.15f to 1f, 0.3f to 0.55f, 0.7f to 0.55f, 0.85f to 1f),
    BandStyle.SPOTLIGHT_BAND to box(ChipInk.SECOND, 0f, 0f, 0.1f, 1f),
    BandStyle.DIAGONAL_SPLIT to mark(ChipInk.SECOND, 0.62f to 0f, 1f to 0f, 1f to 1f, 0.55f to 1f),
    BandStyle.RIBBON_FOLD to mark(ChipInk.SECOND, 0f to 0f, 0.17f to 0f, 0.23f to 0.46f, 0.13f to 1f, 0f to 1f),
    BandStyle.WAVE_DECK to mark(
        ChipInk.SECOND, 0f to 0.7f, 0.25f to 0.55f, 0.5f to 0.7f, 0.75f to 0.85f, 1f to 0.7f, 1f to 1f, 0f to 1f,
    ),
    BandStyle.UNDERLINE_BAR to box(ChipInk.ACCENT, 0f, 0.88f, 1f, 0.12f),
    BandStyle.DOUBLE_RULE to box(ChipInk.ACCENT, 0f, 0f, 1f, 0.08f),
    BandStyle.SIDE_TABS to box(ChipInk.SECOND, 0f, 0f, 0.06f, 1f),
    BandStyle.BOOKMARK to mark(ChipInk.SECOND, 0.05f to 0f, 0.14f to 0f, 0.14f to 1f, 0.095f to 0.85f, 0.05f to 1f),
    BandStyle.STEPPED_LEFT to mark(
        ChipInk.SECOND, 0f to 0f, 0.1f to 0f, 0.1f to 0.33f, 0.14f to 0.33f, 0.14f to 1f, 0f to 1f,
    ),
    BandStyle.DIAGONAL_STRIPES to mark(ChipInk.SECOND, 0.86f to 0f, 0.9f to 0f, 0.82f to 1f, 0.78f to 1f),
    BandStyle.CORNER_BRACKETS to mark(
        ChipInk.ACCENT, 0f to 0f, 0.12f to 0f, 0.12f to 0.15f, 0.04f to 0.15f, 0.04f to 0.5f, 0f to 0.5f,
    ),
    BandStyle.LEFT_BLOCK to box(ChipInk.SECOND, 0f, 0f, 0.2f, 1f),
    BandStyle.TOP_TAB to box(ChipInk.SECOND, 0f, 0f, 0.28f, 0.25f),
    BandStyle.CHECKER_EDGE to box(ChipInk.SECOND, 0f, 0f, 0.08f, 0.5f),
    BandStyle.SPLIT_VERTICAL to box(ChipInk.SECOND, 0.5f, 0f, 0.5f, 1f),
    BandStyle.QUARTER_ARCH to mark(ChipInk.SECOND, 0f to 0f, 0.1f to 0.15f, 0.17f to 0.5f, 0.18f to 1f, 0f to 1f),
    BandStyle.TWIN_RULES to box(ChipInk.SECOND, 0.03f, 0f, 0.02f, 1f),
    BandStyle.INNER_PANEL to box(ChipInk.SECOND, 0.05f, 0.15f, 0.9f, 0.7f),
    BandStyle.ZIGZAG_EDGE to mark(
        ChipInk.SECOND, 0f to 1f, 0f to 0.75f, 0.125f to 0.9f, 0.25f to 0.75f, 0.375f to 0.9f, 0.5f to 0.75f,
        0.625f to 0.9f, 0.75f to 0.75f, 0.875f to 0.9f, 1f to 0.75f, 1f to 1f,
    ),
    BandStyle.PENNANT to mark(ChipInk.SECOND, 0f to 0f, 0.2f to 0.5f, 0f to 1f),
    BandStyle.SLASHES to mark(ChipInk.ACCENT, 0.1f to 0f, 0.13f to 0f, 0.05f to 1f, 0.02f to 1f),
    BandStyle.STACKED_TABS to box(ChipInk.SECOND, 0.03f, 0.2f, 0.12f, 0.15f),
    BandStyle.BOTTOM_BAND to box(ChipInk.SECOND, 0f, 0.7f, 1f, 0.3f),
    BandStyle.CHAMFER_BLOCK to mark(ChipInk.SECOND, 0f to 0f, 0.14f to 0f, 0.2f to 0.35f, 0.2f to 1f, 0f to 1f),
)

private fun chipShape(style: BandStyle, s: Size): Pair<Path, ChipInk>? {
    val m = CHIP_MARKS[style] ?: return null
    val path = Path().apply {
        m.points.forEachIndexed { i, (x, y) ->
            if (i == 0) moveTo(x * s.width, y * s.height) else lineTo(x * s.width, y * s.height)
        }
        close()
    }
    return path to m.ink
}

