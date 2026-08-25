package org.churchpresenter.canvas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.churchpresenter.ui.ColorPickerField
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.canvas_color_2
import org.churchpresenter.resources.generated.resources.canvas_gradient
import org.churchpresenter.resources.generated.resources.canvas_source_shape
import org.churchpresenter.resources.generated.resources.canvas_shape_show_stroke
import org.churchpresenter.resources.generated.resources.canvas_shape_stroke_color
import org.churchpresenter.resources.generated.resources.canvas_shape_fill_color
import org.churchpresenter.resources.generated.resources.canvas_shape_stroke_width
import org.churchpresenter.resources.generated.resources.canvas_angle
import org.churchpresenter.resources.generated.resources.canvas_opacity
import org.churchpresenter.resources.generated.resources.canvas_source_clock
import org.churchpresenter.resources.generated.resources.canvas_source_qrcode
import org.churchpresenter.resources.generated.resources.canvas_clock_mode
import org.churchpresenter.resources.generated.resources.canvas_clock_format
import org.churchpresenter.resources.generated.resources.canvas_clock_show_hours
import org.churchpresenter.resources.generated.resources.canvas_clock_show_seconds
import org.churchpresenter.resources.generated.resources.canvas_clock_font_size
import org.churchpresenter.resources.generated.resources.canvas_clock_target_hour
import org.churchpresenter.resources.generated.resources.canvas_clock_target_minute
import org.churchpresenter.resources.generated.resources.canvas_clock_target_second
import org.churchpresenter.resources.generated.resources.canvas_text_color
import org.churchpresenter.resources.generated.resources.canvas_text_bg_color
import org.churchpresenter.resources.generated.resources.canvas_text_bold
import org.churchpresenter.resources.generated.resources.canvas_qr_type
import org.churchpresenter.resources.generated.resources.canvas_qr_content
import org.churchpresenter.resources.generated.resources.canvas_qr_foreground
import org.churchpresenter.resources.generated.resources.canvas_qr_background
import org.churchpresenter.resources.generated.resources.canvas_qr_wifi_ssid
import org.churchpresenter.resources.generated.resources.canvas_qr_wifi_password
import org.churchpresenter.resources.generated.resources.canvas_qr_wifi_encryption
import org.churchpresenter.resources.generated.resources.canvas_qr_wifi_hidden
import org.churchpresenter.resources.generated.resources.canvas_qr_error_correction
import org.churchpresenter.resources.generated.resources.position
import org.churchpresenter.resources.generated.resources.canvas_qr_type_url
import org.churchpresenter.resources.generated.resources.canvas_qr_type_text
import org.churchpresenter.resources.generated.resources.canvas_qr_type_email
import org.churchpresenter.resources.generated.resources.canvas_qr_type_phone
import org.churchpresenter.resources.generated.resources.canvas_qr_type_sms
import org.churchpresenter.resources.generated.resources.canvas_qr_type_wifi
import org.churchpresenter.resources.generated.resources.canvas_qr_type_vcard
import org.churchpresenter.resources.generated.resources.canvas_clock_mode_clock
import org.churchpresenter.resources.generated.resources.canvas_clock_mode_countdown
import org.churchpresenter.resources.generated.resources.canvas_clock_format_24h
import org.churchpresenter.resources.generated.resources.canvas_clock_format_12h
import org.churchpresenter.resources.generated.resources.canvas_transparent_bg
import org.churchpresenter.resources.generated.resources.canvas_qr_default_text
import org.churchpresenter.resources.generated.resources.timer_start
import org.churchpresenter.resources.generated.resources.timer_reset
import org.churchpresenter.resources.generated.resources.pause
import org.churchpresenter.core.models.scene.SceneSource
import androidx.compose.foundation.layout.PaddingValues
import org.churchpresenter.ui.DropdownSelector
import org.churchpresenter.ui.LabeledCheckbox

private const val MAX_STROKE_WIDTH = 20f
private const val MAX_ANGLE_DEGREES = 360f
private const val PERCENT_SCALE = 100f
private const val MIN_FONT_SIZE = 8
private const val MAX_FONT_SIZE = 500
private const val MAX_TARGET_HOUR = 99
private const val MAX_MINUTE_OR_SECOND = 59

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
        stringResource(Res.string.canvas_source_clock),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val clockLabel = stringResource(Res.string.canvas_clock_mode_clock)
    val countdownLabel = stringResource(Res.string.canvas_clock_mode_countdown)
    val format24hLabel = stringResource(Res.string.canvas_clock_format_24h)
    val format12hLabel = stringResource(Res.string.canvas_clock_format_12h)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DropdownSelector(
            label = stringResource(Res.string.canvas_clock_mode),
            items = listOf(clockLabel, countdownLabel),
            selected = if (source.mode == "countdown") countdownLabel else clockLabel,
            onSelectedChange = { onUpdate(source.copy(mode = if (it == countdownLabel) "countdown" else "clock")) }
        )
        DropdownSelector(
            label = stringResource(Res.string.canvas_clock_format),
            items = listOf(format24hLabel, format12hLabel),
            selected = if (source.timeFormat == "12h") format12hLabel else format24hLabel,
            onSelectedChange = { onUpdate(source.copy(timeFormat = if (it == format12hLabel) "12h" else "24h")) }
        )
    }
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
    LabeledCheckbox(
        checked = source.bold,
        onCheckedChange = { onUpdate(source.copy(bold = it)) },
        label = stringResource(Res.string.canvas_text_bold),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        spacing = 4.dp,
    )
    PropertyTextField(stringResource(Res.string.canvas_clock_font_size), source.fontSize.toString()) { v ->
        v.toIntOrNull()?.let { onUpdate(source.copy(fontSize = it.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE))) }
    }
    ColorPickerField(
        color = source.fontColor,
        onColorChange = { onUpdate(source.copy(fontColor = it)) },
        label = stringResource(Res.string.canvas_text_color)
    )
    ColorPickerField(
        color = source.backgroundColor,
        onColorChange = { onUpdate(source.copy(backgroundColor = it)) },
        label = stringResource(Res.string.canvas_text_bg_color)
    )
    if (source.mode == "countdown") {
        PropertyTextField(stringResource(Res.string.canvas_clock_target_hour), source.targetHour.toString()) { v ->
            v.toIntOrNull()?.let { onUpdate(source.copy(targetHour = it.coerceIn(0, MAX_TARGET_HOUR))) }
        }
        PropertyTextField(stringResource(Res.string.canvas_clock_target_minute), source.targetMinute.toString()) { v ->
            v.toIntOrNull()?.let { onUpdate(source.copy(targetMinute = it.coerceIn(0, MAX_MINUTE_OR_SECOND))) }
        }
        PropertyTextField(stringResource(Res.string.canvas_clock_target_second), source.targetSecond.toString()) { v ->
            v.toIntOrNull()?.let { onUpdate(source.copy(targetSecond = it.coerceIn(0, MAX_MINUTE_OR_SECOND))) }
        }

        val totalSeconds = source.targetHour * 3600 + source.targetMinute * 60 + source.targetSecond
        val timerState = TimerStateManager.getState(source.id, totalSeconds)
        val isRunning = timerState.isRunning
        val remaining = timerState.remainingSeconds

        val hh = remaining / 3600
        val mm = (remaining % 3600) / 60
        val ss = remaining % 60

        Spacer(Modifier.height(8.dp))
        Text(
            "%02d:%02d:%02d".format(hh, mm, ss),
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
                onClick = { TimerStateManager.setRunning(source.id, totalSeconds, !isRunning) },
                enabled = remaining > 0 || isRunning,
                modifier = Modifier.weight(1f).height(32.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(if (isRunning) stringResource(Res.string.pause) else stringResource(Res.string.timer_start), style = MaterialTheme.typography.labelSmall)
            }
            Button(
                onClick = { TimerStateManager.reset(source.id, totalSeconds) },
                modifier = Modifier.weight(1f).height(32.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(stringResource(Res.string.timer_reset), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

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
