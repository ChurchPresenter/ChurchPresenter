package org.churchpresenter.lottiegen.band.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.lottiegen.band.BandColorRole
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.lottiegen.band.BibleLottieGenViewModel
import org.churchpresenter.lottiegen.ui.Strings
import org.churchpresenter.lottiegen.ui.Tokens
import java.io.File

private const val MAX_ALPHA_PCT = 100f
private const val MAX_BORDER_PX = 20f
private const val MAX_CORNER_PX = 60f
private const val MAX_INSET_PX = 80f
private const val MAX_PADDING_PX = 120f

/** The Band pane: the template, its colours, and the band's own four measures. */
@Composable
internal fun BandSection(viewModel: BibleLottieGenViewModel, pickImage: (suspend () -> File?)?) {
    val cfg = viewModel.config
    TemplatePicker(viewModel) { style -> viewModel.updateConfig { it.copy(bandStyle = style) } }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Caption(Strings.bandColors, Modifier.weight(1f))
            Text(
                cfg.roles().joinToString(" · ") { roleLabel(it) },
                fontSize = 9.5.sp, color = Tokens.HintText, maxLines = 1,
            )
        }
        cfg.roles().forEach { role -> ColorRoleRow(viewModel, role, pickImage) }
    }
    // The border's colour and opacity, shown only once there is a border to paint — they were in
    // the config and reached the file, but had no control at all.
    if (cfg.borderThickness > 0) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HexField(
                Strings.bandBorderColor, cfg.borderColor,
                onColorChange = { c -> viewModel.updateConfig { it.copy(borderColor = c) } },
            )
            Box(Modifier.weight(1f)) {
                InlineSlider(
                    label = Strings.bandLookAlpha,
                    value = cfg.borderAlpha.toFloat(),
                    onValueChange = { a -> viewModel.updateConfig { it.copy(borderAlpha = a.toInt()) } },
                    valueRange = 0f..MAX_ALPHA_PCT,
                    format = { it.toInt().toString() },
                )
            }
        }
    }
    Hairline()
    SliderGrid(
        buildList {
            // Where the blend finishes; past it the last colour is flat. Only the styles that
            // actually blend have anything to move.
            if (cfg.bandStyle.usesGradient) {
                add(
                    GridSlider(
                        Strings.bandGradientPosition, cfg.gradientPosition, MAX_ALPHA_PCT,
                        unit = Strings.bandUnitPercent,
                    ) { v -> viewModel.updateConfig { it.copy(gradientPosition = v) } },
                )
            }
            add(
                GridSlider(Strings.bandBorderThickness, cfg.borderThickness, MAX_BORDER_PX) { v ->
                    viewModel.updateConfig { it.copy(borderThickness = v) }
                },
            )
            add(
                GridSlider(Strings.bandCornerRadius, cfg.cornerRadiusPx, MAX_CORNER_PX) { v ->
                    viewModel.updateConfig { it.copy(cornerRadiusPx = v) }
                },
            )
            add(
                GridSlider(Strings.bandInset, cfg.insetPx, MAX_INSET_PX) { v ->
                    viewModel.updateConfig { it.copy(insetPx = v) }
                },
            )
            add(
                GridSlider(Strings.bandPadding, cfg.paddingPx, MAX_PADDING_PX) { v ->
                    viewModel.updateConfig { it.copy(paddingPx = v) }
                },
            )
        },
    )
}

/** The roles the current style paints, in the order the pane lists them. */
internal fun BibleLottieGenConfig.roles(): List<BandColorRole> = buildList {
    add(BandColorRole.BACKGROUND)
    if (bandStyle.usesSecond) add(BandColorRole.SECOND)
    add(BandColorRole.ACCENT)
    if (bandStyle.usesTertiary) add(BandColorRole.TERTIARY)
}

internal fun roleLabel(role: BandColorRole): String = when (role) {
    BandColorRole.BACKGROUND -> Strings.bandColorBackground
    BandColorRole.SECOND -> Strings.bandColorGradient
    BandColorRole.ACCENT -> Strings.bandColorAccent
    BandColorRole.TERTIARY -> Strings.bandColorThird
}

/** One px slider of a two-column grid: its name, its value, its ceiling and where it goes. */
internal class GridSlider(
    val label: String,
    val value: Int,
    val max: Float,
    val min: Float = 0f,
    /** Most of these measure the band in pixels; the opacities and the gradient's position do not. */
    val unit: String = Strings.bandUnitPx,
    val onChange: (Int) -> Unit,
)

/** Sliders two to a row, as the reference lays out the band's and the text area's measures. */
@Composable
internal fun SliderGrid(sliders: List<GridSlider>) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        sliders.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { s ->
                    ThinSlider(
                        label = s.label,
                        value = s.value.toFloat(),
                        onValueChange = { s.onChange(it.toInt()) },
                        valueRange = s.min..s.max,
                        format = { it.toInt().toString() },
                        unit = s.unit,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

internal fun BibleLottieGenConfig.colorOf(role: BandColorRole): String = when (role) {
    BandColorRole.BACKGROUND -> bgColor
    BandColorRole.SECOND -> gradientColor
    BandColorRole.ACCENT -> accentColor
    BandColorRole.TERTIARY -> tertiaryColor
}

internal fun BibleLottieGenConfig.alphaOf(role: BandColorRole): Int = when (role) {
    BandColorRole.BACKGROUND -> bgAlpha
    BandColorRole.SECOND -> secondAlpha
    BandColorRole.ACCENT -> accentAlpha
    BandColorRole.TERTIARY -> tertiaryAlpha
}

internal fun BibleLottieGenConfig.withColor(role: BandColorRole, hex: String): BibleLottieGenConfig = when (role) {
    BandColorRole.BACKGROUND -> copy(bgColor = hex)
    BandColorRole.SECOND -> copy(gradientColor = hex)
    BandColorRole.ACCENT -> copy(accentColor = hex)
    BandColorRole.TERTIARY -> copy(tertiaryColor = hex)
}

internal fun BibleLottieGenConfig.withAlpha(role: BandColorRole, alpha: Int): BibleLottieGenConfig = when (role) {
    BandColorRole.BACKGROUND -> copy(bgAlpha = alpha)
    BandColorRole.SECOND -> copy(secondAlpha = alpha)
    BandColorRole.ACCENT -> copy(accentAlpha = alpha)
    BandColorRole.TERTIARY -> copy(tertiaryAlpha = alpha)
}
