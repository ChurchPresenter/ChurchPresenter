package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.dialog_choose_color
import churchpresenter.composeapp.generated.resources.label_hex
import churchpresenter.composeapp.generated.resources.ok
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.mutableStateListOf
import kotlin.math.abs

/** Stores recently used colors across all color picker instances, persisted to disk. */
private object RecentColors {
    private const val MAX = 12
    private val file = java.io.File(System.getProperty("user.home"), ".churchpresenter/recent_colors.json")
    val colors = mutableStateListOf<String>()

    init { load() }

    fun add(hex: String) {
        val upper = hex.uppercase()
        colors.remove(upper)
        colors.add(0, upper)
        while (colors.size > MAX) colors.removeLast()
        save()
    }

    private fun load() {
        try {
            if (file.exists()) {
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val list = json.decodeFromString<List<String>>(file.readText())
                colors.clear()
                colors.addAll(list.take(MAX))
            }
        } catch (_: Exception) {}
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            val json = kotlinx.serialization.json.Json { encodeDefaults = true }
            file.writeText(json.encodeToString(colors.toList()))
        } catch (_: Exception) {}
    }
}

/**
 * A beautiful Compose-native color picker dialog.
 *
 * Features:
 *  - 2-D saturation/brightness gradient panel
 *  - Hue rainbow slider
 *  - Live color preview swatch
 *  - Hex text field (two-way bound)
 *  - OK / Cancel buttons
 *
 * @param initialHex  Starting color as "#RRGGBB"
 * @param onDismiss   Called when the dialog is dismissed (cancel or OK)
 * @param onColorSelected  Called with the new "#RRGGBB" hex when OK is pressed
 */
@Composable
fun ColorPickerDialog(
    initialHex: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit,
) {
    val initialColor = cpTryParseHex(initialHex) ?: Color.White
    val (initH, initS, initV) = cpColorToHsv(initialColor)

    var hue by remember { mutableStateOf(initH) }
    var saturation by remember { mutableStateOf(initS) }
    var brightness by remember { mutableStateOf(initV) }
    var hexText by remember { mutableStateOf(cpColorToHex(initialColor)) }
    var hexError by remember { mutableStateOf(false) }

    /** Recompute hex text from current HSV sliders */
    fun syncHex() {
        hexText = cpColorToHex(cpHsvToColor(hue, saturation, brightness))
        hexError = false
    }

    val currentColor = cpHsvToColor(hue, saturation, brightness)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.width(300.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(Res.string.dialog_choose_color),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // ── Saturation / Brightness square ──────────────────────────
                SvPanel(
                    hue = hue,
                    saturation = saturation,
                    brightness = brightness,
                    onChanged = { s, v ->
                        saturation = s
                        brightness = v
                        syncHex()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp)),
                )

                // ── Hue rainbow bar ─────────────────────────────────────────
                HueBar(
                    hue = hue,
                    onHueChange = { h ->
                        hue = h
                        syncHex()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )

                // ── Preview swatch + hex text field ─────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(currentColor)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp),
                            ),
                    )
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { text ->
                            hexText = text
                            val parsed = cpTryParseHex(text)
                            if (parsed != null) {
                                val (h, s, v) = cpColorToHsv(parsed)
                                hue = h; saturation = s; brightness = v
                                hexError = false
                            } else {
                                hexError = text.isNotEmpty() && text != "#"
                            }
                        },
                        label = { Text(stringResource(Res.string.label_hex)) },
                        isError = hexError,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }

                // ── Recent colors ─────────────────────────────────────────
                if (RecentColors.colors.isNotEmpty()) {
                    Text(
                        "Recent",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RecentColors.colors.forEach { recentHex ->
                            val recentColor = cpTryParseHex(recentHex) ?: Color.White
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(recentColor, RoundedCornerShape(4.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .clickable {
                                        val (h, s, v) = cpColorToHsv(recentColor)
                                        hue = h; saturation = s; brightness = v
                                        syncHex()
                                    }
                            )
                        }
                    }
                }

                // ── Buttons ─────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val hex = cpColorToHex(currentColor)
                            RecentColors.add(hex)
                            onColorSelected(hex)
                            onDismiss()
                        },
                        enabled = !hexError && cpTryParseHex(hexText) != null,
                    ) { Text(stringResource(Res.string.ok)) }
                }
            }
        }
    }
}

// ── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun SvPanel(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onChanged: (saturation: Float, brightness: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val hueColor = cpHsvToColor(hue, 1f, 1f)

    Box(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(canvasSize) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val (w, h) = canvasSize
                    fun update(pos: Offset) {
                        onChanged(
                            (pos.x / w).coerceIn(0f, 1f),
                            (1f - pos.y / h).coerceIn(0f, 1f),
                        )
                    }
                    update(down.position)
                    drag(down.id) { change ->
                        change.consume()
                        update(change.position)
                    }
                }
            },
    ) {
        // Background gradient layers
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(brush = Brush.horizontalGradient(listOf(Color.White, hueColor)))
            drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        }
        // Circular selector indicator
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = saturation * size.width
            val cy = (1f - brightness) * size.height
            drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(cx, cy), style = Stroke(2.dp.toPx()))
            drawCircle(Color.Black.copy(alpha = 0.4f), radius = 9.dp.toPx(), center = Offset(cx, cy), style = Stroke(1.dp.toPx()))
        }
    }
}

@Composable
private fun HueBar(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var barWidth by remember { mutableStateOf(0) }

    // Rainbow stops: R → Y → G → C → B → M → R
    val rainbowBrush = remember {
        Brush.horizontalGradient(
            listOf(
                Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
                Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000),
            )
        )
    }

    Box(
        modifier = modifier
            .onSizeChanged { barWidth = it.width }
            .pointerInput(barWidth) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun update(x: Float) = onHueChange((x / barWidth * 360f).coerceIn(0f, 360f))
                    update(down.position.x)
                    drag(down.id) { change ->
                        change.consume()
                        update(change.position.x)
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(brush = rainbowBrush)
            // Thumb line
            val x = hue / 360f * size.width
            drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3.dp.toPx())
            drawLine(Color.Black.copy(alpha = 0.35f), Offset(x - 1.dp.toPx(), 0f), Offset(x - 1.dp.toPx(), size.height), strokeWidth = 1.dp.toPx())
        }
    }
}

// ── Internal helpers (prefixed with "cp" to avoid clashing with other files) ──

internal fun cpHsvToColor(h: Float, s: Float, v: Float): Color {
    val c = v * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = v - c
    val (r, g, b) = when ((h / 60f).toInt().coerceIn(0, 5)) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r + m).coerceIn(0f, 1f),
        green = (g + m).coerceIn(0f, 1f),
        blue = (b + m).coerceIn(0f, 1f),
    )
}

internal fun cpColorToHsv(color: Color): Triple<Float, Float, Float> {
    val r = color.red; val g = color.green; val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val v = max
    val s = if (max > 0.001f) delta / max else 0f
    val h = when {
        delta < 0.001f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return Triple(h, s, v)
}

internal fun cpColorToHex(color: Color): String {
    val r = (color.red * 255f).toInt().coerceIn(0, 255)
    val g = (color.green * 255f).toInt().coerceIn(0, 255)
    val b = (color.blue * 255f).toInt().coerceIn(0, 255)
    return "#%02X%02X%02X".format(r, g, b)
}

internal fun cpTryParseHex(hex: String): Color? = try {
    val clean = hex.trim().removePrefix("#")
    when (clean.length) {
        8 -> {
            val a = clean.substring(0, 2).toInt(16)
            val r = clean.substring(2, 4).toInt(16)
            val g = clean.substring(4, 6).toInt(16)
            val b = clean.substring(6, 8).toInt(16)
            Color(r, g, b, a)
        }
        6 -> Color(clean.substring(0, 2).toInt(16), clean.substring(2, 4).toInt(16), clean.substring(4, 6).toInt(16))
        else -> null
    }
} catch (_: Exception) { null }

