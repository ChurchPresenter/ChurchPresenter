package org.churchpresenter.hosanna.composables.field

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.churchpresenter.hosanna.HosannaRadius
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.tabular

private const val HUE_DEGREES = 360f
private const val PERCENT_MAX = 100

@Composable
internal fun ColorPickerPopover(
    color: Color,
    onColorChange: (Color) -> Unit,
    allowOpacity: Boolean,
    themeColors: List<Color>,
    recentColors: List<Color>,
    labels: ColorFieldLabels,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val gap = with(LocalDensity.current) { MenuDefaults.Gap.roundToPx() }
    var hsv by remember { mutableStateOf(color.toHsv()) }
    LaunchedEffect(color) { if (!sameRgb(hsv.toColor(), color)) hsv = color.toHsv() }
    fun pick(next: Hsv) {
        hsv = next
        onColorChange(next.toColor(color.alpha))
    }
    Popup(
        popupPositionProvider = remember(gap) { MenuPositionProvider(gap) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
        onPreviewKeyEvent = { event ->
            (event.type == KeyEventType.KeyDown && event.key == Key.Escape).also { if (it) onRestore() }
        },
    ) {
        MenuSurface(width = ColorFieldDefaults.PickerWidth, padding = ColorFieldDefaults.PickerPadding) {
            Column(verticalArrangement = Arrangement.spacedBy(ColorFieldDefaults.Gap)) {
                SaturationArea(hsv, ::pick)
                ColorSlider(
                    fraction = hsv.hue / HUE_DEGREES,
                    thumb = hueColor(hsv.hue),
                    track = { drawRect(Brush.horizontalGradient(HueStops)) },
                ) { pick(hsv.copy(hue = it * HUE_DEGREES)) }
                if (allowOpacity) {
                    val solid = color.copy(alpha = 1f)
                    val light = HosannaTheme.colors.surfaceContainerLowest
                    val dark = HosannaTheme.colors.outlineVariant
                    ColorSlider(
                        fraction = color.alpha,
                        thumb = color,
                        track = {
                            drawCheckerboard(light, dark)
                            drawRect(Brush.horizontalGradient(listOf(solid.copy(alpha = 0f), solid)))
                        },
                    ) { onColorChange(color.copy(alpha = it)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(ColorFieldDefaults.SwatchGap)) {
                    PickerInput(
                        text = hexText(color),
                        modifier = Modifier.weight(1f),
                        accept = { parseColor(it)?.let { parsed -> onColorChange(parsed.copy(alpha = color.alpha)) } },
                    )
                    if (allowOpacity) {
                        PickerInput(
                            text = "${opacityPercent(color)}",
                            suffix = "%",
                            modifier = Modifier.width(ColorFieldDefaults.OpacityInputWidth),
                            accept = { typed ->
                                typed.toIntOrNull()?.takeIf { it in 0..PERCENT_MAX }?.let {
                                    onColorChange(withOpacityPercent(color, it))
                                }
                            },
                        )
                    }
                }
                SwatchRow(labels.theme, themeColors, color) { onColorChange(it.copy(alpha = color.alpha)) }
                SwatchRow(labels.recent, recentColors, color) { onColorChange(it) }
            }
        }
    }
}

@Composable
private fun SaturationArea(hsv: Hsv, onPick: (Hsv) -> Unit) {
    val current = hsv.toColor()
    var latest by remember { mutableStateOf(hsv) }
    latest = hsv
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ColorFieldDefaults.AreaHeight)
            .clip(HosannaTheme.shapes.m)
            .pointerInput(Unit) {
                detectTapGestures { onPick(areaHsv(latest, it, size.width.toFloat(), size.height.toFloat())) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    onPick(areaHsv(latest, change.position, size.width.toFloat(), size.height.toFloat()))
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                nudgeHsv(latest, event.key, event.isShiftPressed)?.let(onPick) != null
            }
            .focusable()
            .drawBehind {
                drawRect(hueColor(hsv.hue))
                drawRect(Brush.horizontalGradient(listOf(Color.White, Color.White.copy(alpha = 0f))))
                drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0f), Color.Black)))
                val at = Offset(hsv.saturation * size.width, (1 - hsv.value) * size.height)
                drawThumb(at, ColorFieldDefaults.AreaThumb, current)
            },
    )
}

private fun DrawScope.drawThumb(center: Offset, diameter: Dp, fill: Color) {
    val radius = diameter.toPx() / 2
    val ring = ColorFieldDefaults.Ring.toPx()
    drawCircle(fill, radius, center)
    drawCircle(Color.White, radius - ring / 2, center, style = Stroke(ring))
}

@Composable
private fun ColorSlider(fraction: Float, thumb: Color, track: DrawScope.() -> Unit, onChange: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ColorFieldDefaults.SliderThumb)
            .pointerInput(Unit) { detectTapGestures { onChange((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> onChange((change.position.x / size.width).coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(ColorFieldDefaults.SliderHeight)
                .clip(HosannaTheme.shapes.full)
                .drawBehind(track),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(ColorFieldDefaults.SliderThumb)
                .drawBehind {
                    val radius = ColorFieldDefaults.SliderThumb.toPx() / 2
                    val x = radius + fraction.coerceIn(0f, 1f) * (size.width - radius * 2)
                    drawThumb(Offset(x, size.height / 2), ColorFieldDefaults.SliderThumb, thumb)
                },
        )
    }
}

@Composable
private fun PickerInput(text: String, modifier: Modifier, accept: (String) -> Unit, suffix: String? = null) {
    val colors = HosannaTheme.colors
    val shape = HosannaTheme.shapes.m
    var value by remember(text) { mutableStateOf(text) }
    Row(
        modifier = modifier
            .height(ColorFieldDefaults.InputHeight)
            .background(colors.surfaceVariant, shape)
            .border(FieldDefaults.Border, colors.outline, shape)
            .padding(horizontal = ColorFieldDefaults.Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = {
                value = it
                accept(it)
            },
            modifier = Modifier.weight(1f),
            textStyle = HosannaTheme.typography.body.tabular().copy(color = colors.onSurface),
            singleLine = true,
            cursorBrush = SolidColor(colors.primary),
        )
        suffix?.let { BasicText(it, style = HosannaTheme.typography.caption.copy(color = colors.onSurfaceVariant)) }
    }
}

@Composable
private fun SwatchRow(title: String, swatches: List<Color>, current: Color, onPick: (Color) -> Unit) {
    if (swatches.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(ColorFieldDefaults.SwatchGap)) {
        BasicText(title, style = HosannaTheme.typography.caption.copy(color = HosannaTheme.colors.onSurfaceFaint))
        Row(horizontalArrangement = Arrangement.spacedBy(ColorFieldDefaults.SwatchGap)) {
            swatches.take(ColorFieldDefaults.SWATCHES_PER_ROW).forEach { swatch ->
                PaletteSwatch(swatch, selected = sameRgb(swatch, current)) { onPick(swatch) }
            }
        }
    }
}

@Composable
private fun PaletteSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    val colors = HosannaTheme.colors
    val ring = ColorFieldDefaults.Ring
    val outer = RoundedCornerShape(HosannaRadius.nested(HosannaRadius.S, ring * 2))
    Box(
        modifier = Modifier
            .size(ColorFieldDefaults.PaletteSwatch)
            .then(if (selected) Modifier.border(ring, colors.primary, outer) else Modifier)
            .padding(if (selected) ring * 2 else ring * 0)
            .clip(HosannaTheme.shapes.s)
            .background(color)
            .border(FieldDefaults.Border, colors.outlineVariant, HosannaTheme.shapes.s)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = hexText(color) },
    )
}
