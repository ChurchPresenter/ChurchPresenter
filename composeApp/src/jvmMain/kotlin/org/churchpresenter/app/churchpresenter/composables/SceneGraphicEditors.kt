package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.utils.TimerStateManager
import org.churchpresenter.core.models.scene.ClockModes
import org.jetbrains.compose.resources.stringResource
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.canvas_color_2
import churchpresenter.composeapp.generated.resources.canvas_gradient
import churchpresenter.composeapp.generated.resources.canvas_source_shape
import churchpresenter.composeapp.generated.resources.canvas_shape_show_stroke
import churchpresenter.composeapp.generated.resources.canvas_shape_stroke_color
import churchpresenter.composeapp.generated.resources.canvas_shape_fill_color
import churchpresenter.composeapp.generated.resources.canvas_shape_stroke_width
import churchpresenter.composeapp.generated.resources.canvas_angle
import churchpresenter.composeapp.generated.resources.canvas_opacity
import churchpresenter.composeapp.generated.resources.canvas_source_timer
import churchpresenter.composeapp.generated.resources.canvas_source_qrcode
import churchpresenter.composeapp.generated.resources.canvas_clock_mode
import churchpresenter.composeapp.generated.resources.canvas_clock_format
import churchpresenter.composeapp.generated.resources.canvas_clock_show_hours
import churchpresenter.composeapp.generated.resources.canvas_clock_show_seconds
import churchpresenter.composeapp.generated.resources.canvas_clock_font_size
import churchpresenter.composeapp.generated.resources.canvas_clock_mode_count_up
import churchpresenter.composeapp.generated.resources.timer_mode_clock
import churchpresenter.composeapp.generated.resources.timer_hours
import churchpresenter.composeapp.generated.resources.timer_minutes
import churchpresenter.composeapp.generated.resources.timer_seconds
import churchpresenter.composeapp.generated.resources.timer_target_time
import churchpresenter.composeapp.generated.resources.timer_expired_text_label
import churchpresenter.composeapp.generated.resources.canvas_text_color
import churchpresenter.composeapp.generated.resources.canvas_text_bg_color
import churchpresenter.composeapp.generated.resources.canvas_letter_spacing
import churchpresenter.composeapp.generated.resources.canvas_text_curve
import churchpresenter.composeapp.generated.resources.canvas_qr_type
import churchpresenter.composeapp.generated.resources.canvas_qr_content
import churchpresenter.composeapp.generated.resources.canvas_qr_foreground
import churchpresenter.composeapp.generated.resources.canvas_qr_background
import churchpresenter.composeapp.generated.resources.canvas_qr_wifi_ssid
import churchpresenter.composeapp.generated.resources.canvas_qr_wifi_password
import churchpresenter.composeapp.generated.resources.canvas_qr_wifi_encryption
import churchpresenter.composeapp.generated.resources.canvas_qr_wifi_hidden
import churchpresenter.composeapp.generated.resources.canvas_qr_error_correction
import churchpresenter.composeapp.generated.resources.position
import churchpresenter.composeapp.generated.resources.canvas_qr_type_url
import churchpresenter.composeapp.generated.resources.canvas_qr_type_text
import churchpresenter.composeapp.generated.resources.canvas_qr_type_email
import churchpresenter.composeapp.generated.resources.canvas_qr_type_phone
import churchpresenter.composeapp.generated.resources.canvas_qr_type_sms
import churchpresenter.composeapp.generated.resources.canvas_qr_type_wifi
import churchpresenter.composeapp.generated.resources.canvas_qr_type_vcard
import churchpresenter.composeapp.generated.resources.canvas_clock_mode_clock
import churchpresenter.composeapp.generated.resources.canvas_clock_mode_countdown
import churchpresenter.composeapp.generated.resources.canvas_clock_format_24h
import churchpresenter.composeapp.generated.resources.canvas_clock_format_12h
import churchpresenter.composeapp.generated.resources.canvas_transparent_bg
import churchpresenter.composeapp.generated.resources.canvas_qr_default_text
import churchpresenter.composeapp.generated.resources.timer_start
import churchpresenter.composeapp.generated.resources.timer_reset
import churchpresenter.composeapp.generated.resources.pause
import org.churchpresenter.core.models.scene.SceneSource
import androidx.compose.foundation.layout.PaddingValues

private const val MAX_STROKE_WIDTH = 20f
private const val MAX_ANGLE_DEGREES = 360f
private const val PERCENT_SCALE = 100f
/** Two full turns of curve either way; past that the line runs into itself. */
private const val MAX_TEXT_CURVE = 200f
/** Tracking, as a percentage of the font size. */
private const val MIN_LETTER_SPACING = -20f
private const val MAX_LETTER_SPACING = 100f
private const val MIN_FONT_SIZE = 8
private const val MAX_FONT_SIZE = 500
private const val MAX_TARGET_HOUR = 99
private const val MAX_MINUTE_OR_SECOND = 59
private const val MAX_HOUR_OF_DAY = 23
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600

/**
 * The scene sources the app draws itself: shapes, a clock, and a QR code.
 */

@Composable
internal fun ShapeProperties(source: SceneSource.ShapeSource, onUpdate: (SceneSource) -> Unit) {
    Text(
        stringResource(Res.string.canvas_source_shape),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val isStrokeOnly = source.shapeType in listOf("line", "arrow", "freehand")

    if (!isStrokeOnly) {
        LabeledCheckbox(
            checked = source.showStroke,
            onCheckedChange = { onUpdate(source.copy(showStroke = it)) },
            label = stringResource(Res.string.canvas_shape_show_stroke),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            spacing = 4.dp,
        )
    }

    if (isStrokeOnly || source.showStroke) {
        ColorPickerField(
            color = source.strokeColor,
            onColorChange = { onUpdate(source.copy(strokeColor = it)) },
            label = stringResource(Res.string.canvas_shape_stroke_color)
        )
        PropertySlider("${stringResource(Res.string.canvas_shape_stroke_color)} ${stringResource(Res.string.canvas_opacity)}", source.strokeOpacity, 0f, 1f) { v ->
            onUpdate(source.copy(strokeOpacity = v))
        }
    }

    if (!isStrokeOnly) {
        ColorPickerField(
            color = source.fillColor,
            onColorChange = { onUpdate(source.copy(fillColor = it)) },
            label = stringResource(Res.string.canvas_shape_fill_color)
        )
        PropertySlider("${stringResource(Res.string.canvas_shape_fill_color)} ${stringResource(Res.string.canvas_opacity)}", source.fillOpacity, 0f, 1f) { v ->
            onUpdate(source.copy(fillOpacity = v))
        }
    }

    if (isStrokeOnly || source.showStroke) {
        PropertySliderWithInput(
            stringResource(Res.string.canvas_shape_stroke_width),
            source.strokeWidth, 1f, MAX_STROKE_WIDTH, "px"
        ) { v ->
            onUpdate(source.copy(strokeWidth = v))
        }
    }

    if (!isStrokeOnly) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        LabeledCheckbox(
            checked = source.isGradient,
            onCheckedChange = { onUpdate(source.copy(isGradient = it)) },
            label = stringResource(Res.string.canvas_gradient),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            spacing = 4.dp,
        )
        if (source.isGradient) {
            ColorPickerField(
                color = source.gradientColor2,
                onColorChange = { onUpdate(source.copy(gradientColor2 = it)) },
                label = stringResource(Res.string.canvas_color_2)
            )
            PropertySlider("${stringResource(Res.string.canvas_color_2)} ${stringResource(Res.string.canvas_opacity)}", source.gradientColor2Opacity, 0f, 1f) { v ->
                onUpdate(source.copy(gradientColor2Opacity = v))
            }
            PropertySliderWithInput(stringResource(Res.string.canvas_angle), source.gradientAngle, 0f, MAX_ANGLE_DEGREES, "\u00B0") { v ->
                onUpdate(source.copy(gradientAngle = v))
            }
            PropertySliderWithInput(stringResource(Res.string.position), source.gradientPosition * PERCENT_SCALE, 0f, PERCENT_SCALE, "%") { v ->
                onUpdate(source.copy(gradientPosition = (v / 100f).coerceIn(0f, 1f)))
            }
        }
    }
}

@Composable
internal fun ClockProperties(source: SceneSource.ClockSource, onUpdate: (SceneSource) -> Unit) {
    Text(
        stringResource(Res.string.canvas_source_timer),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val format24hLabel = stringResource(Res.string.canvas_clock_format_24h)
    val format12hLabel = stringResource(Res.string.canvas_clock_format_12h)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ClockModeDropdown(source, onUpdate)
        DropdownSelector(
            label = stringResource(Res.string.canvas_clock_format),
            items = listOf(format24hLabel, format12hLabel),
            selected = if (source.timeFormat == "12h") format12hLabel else format24hLabel,
            onSelectedChange = { onUpdate(source.copy(timeFormat = if (it == format12hLabel) "12h" else "24h")) }
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LabeledCheckbox(
            checked = source.showHours,
            onCheckedChange = { onUpdate(source.copy(showHours = it)) },
            label = stringResource(Res.string.canvas_clock_show_hours),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            spacing = 4.dp,
        )
        LabeledCheckbox(
            checked = source.showSeconds,
            onCheckedChange = { onUpdate(source.copy(showSeconds = it)) },
            label = stringResource(Res.string.canvas_clock_show_seconds),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            spacing = 4.dp,
        )
    }
    TextStyleButtons(
        bold = source.bold,
        italic = source.italic,
        underline = source.underline,
        shadow = false,
        onBoldChange = { onUpdate(source.copy(bold = it)) },
        onItalicChange = { onUpdate(source.copy(italic = it)) },
        onUnderlineChange = { onUpdate(source.copy(underline = it)) },
        onShadowChange = {},
        strikethrough = source.strikethrough,
        onStrikethroughChange = { onUpdate(source.copy(strikethrough = it)) },
        showShadow = false,
        backdrop = source.backdrop,
        onBackdropChange = { onUpdate(source.copy(backdrop = it)) },
    )
    PropertySliderWithInput(
        stringResource(Res.string.canvas_letter_spacing),
        source.letterSpacing, MIN_LETTER_SPACING, MAX_LETTER_SPACING, "%"
    ) { v -> onUpdate(source.copy(letterSpacing = v)) }
    PropertySliderWithInput(
        stringResource(Res.string.canvas_text_curve),
        source.curve, -MAX_TEXT_CURVE, MAX_TEXT_CURVE, "%"
    ) { v -> onUpdate(source.copy(curve = v)) }
    // Half a row: a font size is three digits, and the panel is a narrow column.
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PropertyTextField(
            stringResource(Res.string.canvas_clock_font_size),
            source.fontSize.toString(),
            Modifier.weight(1f)
        ) { v ->
            v.toIntOrNull()?.let { onUpdate(source.copy(fontSize = it.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE))) }
        }
        Spacer(Modifier.weight(1f))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ColorPickerField(
            color = source.fontColor,
            onColorChange = { onUpdate(source.copy(fontColor = it)) },
            modifier = Modifier.weight(1f),
            label = stringResource(Res.string.canvas_text_color)
        )
        ColorPickerField(
            color = source.backgroundColor,
            onColorChange = { onUpdate(source.copy(backgroundColor = it)) },
            modifier = Modifier.weight(1f),
            label = stringResource(Res.string.canvas_text_bg_color)
        )
    }
    when (source.mode) {
        ClockModes.COUNTDOWN -> ClockCountdownControls(source, onUpdate)
        ClockModes.COUNT_UP -> ClockCountUpControls(source)
        ClockModes.TARGET_TIME -> ClockTargetTimeFields(source, onUpdate)
        else -> Unit
    }
}

/**
 * The four things the source can show. Picking one also re-seeds the shared timer, so a stopwatch
 * never opens holding whatever a countdown left behind on the same source.
 */
@Composable
private fun ClockModeDropdown(source: SceneSource.ClockSource, onUpdate: (SceneSource) -> Unit) {
    val modes = listOf(
        ClockModes.CLOCK to stringResource(Res.string.canvas_clock_mode_clock),
        ClockModes.COUNTDOWN to stringResource(Res.string.canvas_clock_mode_countdown),
        ClockModes.COUNT_UP to stringResource(Res.string.canvas_clock_mode_count_up),
        ClockModes.TARGET_TIME to stringResource(Res.string.timer_mode_clock),
    )
    DropdownSelector(
        label = stringResource(Res.string.canvas_clock_mode),
        items = modes.map { it.second },
        selected = modes.firstOrNull { it.first == source.mode }?.second ?: modes.first().second,
        onSelectedChange = { picked ->
            val mode = modes.firstOrNull { it.second == picked }?.first ?: ClockModes.CLOCK
            TimerStateManager.reset(source.id, if (mode == ClockModes.COUNTDOWN) source.durationSeconds() else 0)
            onUpdate(source.copy(mode = mode))
        }
    )
}

/** The duration counted down from, the message left on screen at zero, and the transport. */
@Composable
private fun ClockCountdownControls(source: SceneSource.ClockSource, onUpdate: (SceneSource) -> Unit) {
    TimeFieldRow(
        listOf(
            TimeField(stringResource(Res.string.timer_hours), source.targetHour, MAX_TARGET_HOUR) {
                onUpdate(source.copy(targetHour = it))
            },
            TimeField(stringResource(Res.string.timer_minutes), source.targetMinute, MAX_MINUTE_OR_SECOND) {
                onUpdate(source.copy(targetMinute = it))
            },
            TimeField(stringResource(Res.string.timer_seconds), source.targetSecond, MAX_MINUTE_OR_SECOND) {
                onUpdate(source.copy(targetSecond = it))
            },
        )
    )
    PropertyTextField(stringResource(Res.string.timer_expired_text_label), source.expiredText) { v ->
        onUpdate(source.copy(expiredText = v))
    }

    val totalSeconds = source.durationSeconds()
    ClockTimerTransport(
        sourceId = source.id,
        seedSeconds = totalSeconds,
        canStart = totalSeconds > 0
    )
}

/** A stopwatch has nothing to configure, so it is the transport and its read-out alone. */
@Composable
private fun ClockCountUpControls(source: SceneSource.ClockSource) {
    ClockTimerTransport(sourceId = source.id, seedSeconds = 0, canStart = true, countUp = true)
}

/** The time of day counted down to, entered on a 24-hour clock. */
@Composable
private fun ClockTargetTimeFields(source: SceneSource.ClockSource, onUpdate: (SceneSource) -> Unit) {
    TimeFieldRow(
        listOf(
            TimeField(stringResource(Res.string.timer_hours), source.targetTimeHour, MAX_HOUR_OF_DAY) {
                onUpdate(source.copy(targetTimeHour = it))
            },
            TimeField(stringResource(Res.string.timer_minutes), source.targetTimeMinute, MAX_MINUTE_OR_SECOND) {
                onUpdate(source.copy(targetTimeMinute = it))
            },
            TimeField(stringResource(Res.string.timer_seconds), source.targetTimeSecond, MAX_MINUTE_OR_SECOND) {
                onUpdate(source.copy(targetTimeSecond = it))
            },
        )
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "%s %02d:%02d:%02d".format(
            stringResource(Res.string.timer_target_time),
            source.targetTimeHour, source.targetTimeMinute, source.targetTimeSecond
        ),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}

/** One hours/minutes/seconds box of a [TimeFieldRow]: what it is called, holds, and may hold. */
private data class TimeField(val label: String, val value: Int, val max: Int, val onChange: (Int) -> Unit)

/**
 * Hours, minutes and seconds as one colon-separated row rather than three full-width fields —
 * a time reads as a time, and the panel is a narrow column.
 */
@Composable
private fun TimeFieldRow(fields: List<TimeField>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        fields.forEachIndexed { index, field ->
            if (index > 0) {
                Text(
                    ":",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PropertyTextField(field.label, field.value.toString(), Modifier.weight(1f)) { typed ->
                typed.toIntOrNull()?.let { field.onChange(it.coerceIn(0, field.max)) }
            }
        }
    }
}

/**
 * The read-out and the two buttons the countdown and the stopwatch share. The value is
 * [TimerStateManager]'s, not the source's, so the panel and the canvas always agree.
 */
@Composable
private fun ClockTimerTransport(
    sourceId: String,
    seedSeconds: Int,
    canStart: Boolean,
    countUp: Boolean = false
) {
    val timerState = TimerStateManager.getState(sourceId, seedSeconds)
    val isRunning = timerState.isRunning
    val seconds = timerState.remainingSeconds

    Spacer(Modifier.height(8.dp))
    Text(
        "%02d:%02d:%02d".format(
            seconds / SECONDS_PER_HOUR,
            (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE,
            seconds % SECONDS_PER_MINUTE
        ),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Button(
            onClick = { TimerStateManager.setRunning(sourceId, seedSeconds, !isRunning, countUp) },
            enabled = canStart || isRunning,
            modifier = Modifier.weight(1f).height(32.dp),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text(if (isRunning) stringResource(Res.string.pause) else stringResource(Res.string.timer_start), style = MaterialTheme.typography.labelSmall)
        }
        Button(
            onClick = { TimerStateManager.reset(sourceId, seedSeconds) },
            modifier = Modifier.weight(1f).height(32.dp),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text(stringResource(Res.string.timer_reset), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** The length a countdown counts from, in seconds. */
private fun SceneSource.ClockSource.durationSeconds(): Int =
    targetHour * SECONDS_PER_HOUR + targetMinute * SECONDS_PER_MINUTE + targetSecond

@Composable
internal fun QRCodeProperties(source: SceneSource.QRCodeSource, onUpdate: (SceneSource) -> Unit) {
    Text(
        stringResource(Res.string.canvas_source_qrcode),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val urlLabel    = stringResource(Res.string.canvas_qr_type_url)
    val textLabel   = stringResource(Res.string.canvas_qr_type_text)
    val emailLabel  = stringResource(Res.string.canvas_qr_type_email)
    val phoneLabel  = stringResource(Res.string.canvas_qr_type_phone)
    val smsLabel    = stringResource(Res.string.canvas_qr_type_sms)
    val wifiLabel   = stringResource(Res.string.canvas_qr_type_wifi)
    val vcardLabel  = stringResource(Res.string.canvas_qr_type_vcard)
    val defaultText = stringResource(Res.string.canvas_qr_default_text)
    val typeOptions = listOf(urlLabel, textLabel, emailLabel, phoneLabel, smsLabel, wifiLabel, vcardLabel)
    val typeMap = mapOf(
        "url" to urlLabel, "text" to textLabel, "email" to emailLabel, "phone" to phoneLabel,
        "sms" to smsLabel, "wifi" to wifiLabel, "vcard" to vcardLabel
    )
    val reverseTypeMap = mapOf(
        urlLabel to "url", textLabel to "text", emailLabel to "email", phoneLabel to "phone",
        smsLabel to "sms", wifiLabel to "wifi", vcardLabel to "vcard"
    )
    DropdownSelector(
        label = stringResource(Res.string.canvas_qr_type),
        items = typeOptions,
        selected = typeMap[source.contentType] ?: "URL",
        onSelectedChange = { newType ->
            val type = reverseTypeMap[newType] ?: "url"
            val prefill = when (type) {
                "url" -> "https://example.com"
                "text" -> defaultText
                "email" -> "mailto:name@example.com"
                "phone" -> "tel:+1234567890"
                "sms" -> "smsto:+1234567890:Message"
                "vcard" -> "BEGIN:VCARD\nVERSION:3.0\nFN:Name\nTEL:+1234567890\nEMAIL:name@example.com\nEND:VCARD"
                else -> source.content
            }
            onUpdate(source.copy(contentType = type, content = if (type != "wifi") prefill else source.content))
        },
        modifier = Modifier.fillMaxWidth()
    )

    if (source.contentType == "wifi") {
        PropertyTextField(stringResource(Res.string.canvas_qr_wifi_ssid), source.wifiSsid) { v ->
            onUpdate(source.copy(wifiSsid = v))
        }
        PropertyTextField(stringResource(Res.string.canvas_qr_wifi_password), source.wifiPassword) { v ->
            onUpdate(source.copy(wifiPassword = v))
        }
        DropdownSelector(
            label = stringResource(Res.string.canvas_qr_wifi_encryption),
            items = listOf("WPA", "WPA2", "WPA3", "WEP", "None"),
            selected = source.wifiEncryption,
            onSelectedChange = { onUpdate(source.copy(wifiEncryption = it)) },
            modifier = Modifier.fillMaxWidth()
        )
        LabeledCheckbox(
            checked = source.wifiHidden,
            onCheckedChange = { onUpdate(source.copy(wifiHidden = it)) },
            label = stringResource(Res.string.canvas_qr_wifi_hidden),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            spacing = 4.dp,
        )
    } else {
        PropertyTextField(stringResource(Res.string.canvas_qr_content), source.content) { v ->
            onUpdate(source.copy(content = v))
        }
    }
    ColorPickerField(
        color = source.foregroundColor,
        onColorChange = { onUpdate(source.copy(foregroundColor = it)) },
        label = stringResource(Res.string.canvas_qr_foreground)
    )
    LabeledCheckbox(
        checked = source.transparentBackground,
        onCheckedChange = { onUpdate(source.copy(transparentBackground = it)) },
        label = stringResource(Res.string.canvas_transparent_bg),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        spacing = 4.dp,
    )
    if (!source.transparentBackground) {
        ColorPickerField(
            color = source.backgroundColor,
            onColorChange = { onUpdate(source.copy(backgroundColor = it)) },
            label = stringResource(Res.string.canvas_qr_background)
        )
    }
    DropdownSelector(
        label = stringResource(Res.string.canvas_qr_error_correction),
        items = listOf("L", "M", "Q", "H"),
        selected = source.errorCorrection,
        onSelectedChange = { onUpdate(source.copy(errorCorrection = it)) },
        modifier = Modifier.fillMaxWidth()
    )
}
