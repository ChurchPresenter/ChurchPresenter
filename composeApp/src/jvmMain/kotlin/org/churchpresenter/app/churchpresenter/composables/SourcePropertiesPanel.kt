package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.symbol_dropdown
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import org.jetbrains.compose.resources.stringResource
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.canvas_bg_color
import churchpresenter.composeapp.generated.resources.canvas_color_1
import churchpresenter.composeapp.generated.resources.canvas_color_2
import churchpresenter.composeapp.generated.resources.canvas_font_color
import churchpresenter.composeapp.generated.resources.canvas_gradient
import churchpresenter.composeapp.generated.resources.canvas_source_shape
import churchpresenter.composeapp.generated.resources.canvas_shape_stroke_color
import churchpresenter.composeapp.generated.resources.canvas_shape_fill_color
import churchpresenter.composeapp.generated.resources.canvas_shape_stroke_width
import churchpresenter.composeapp.generated.resources.canvas_angle
import churchpresenter.composeapp.generated.resources.canvas_opacity
import churchpresenter.composeapp.generated.resources.canvas_source_browser
import churchpresenter.composeapp.generated.resources.canvas_source_clock
import churchpresenter.composeapp.generated.resources.canvas_source_qrcode
import churchpresenter.composeapp.generated.resources.canvas_source_camera
import churchpresenter.composeapp.generated.resources.canvas_source_screen_capture
import churchpresenter.composeapp.generated.resources.canvas_clock_mode
import churchpresenter.composeapp.generated.resources.canvas_clock_format
import churchpresenter.composeapp.generated.resources.canvas_clock_show_hours
import churchpresenter.composeapp.generated.resources.canvas_clock_show_seconds
import churchpresenter.composeapp.generated.resources.canvas_clock_font_size
import churchpresenter.composeapp.generated.resources.canvas_clock_target_hour
import churchpresenter.composeapp.generated.resources.canvas_clock_target_minute
import churchpresenter.composeapp.generated.resources.canvas_clock_target_second
import churchpresenter.composeapp.generated.resources.canvas_text_color
import churchpresenter.composeapp.generated.resources.canvas_text_bg_color
import churchpresenter.composeapp.generated.resources.canvas_text_bold
import churchpresenter.composeapp.generated.resources.canvas_qr_type
import churchpresenter.composeapp.generated.resources.canvas_qr_content
import churchpresenter.composeapp.generated.resources.canvas_qr_foreground
import churchpresenter.composeapp.generated.resources.canvas_qr_background
import churchpresenter.composeapp.generated.resources.canvas_qr_wifi_ssid
import churchpresenter.composeapp.generated.resources.canvas_qr_wifi_password
import churchpresenter.composeapp.generated.resources.canvas_qr_wifi_encryption
import churchpresenter.composeapp.generated.resources.canvas_qr_wifi_hidden
import churchpresenter.composeapp.generated.resources.canvas_qr_error_correction
import churchpresenter.composeapp.generated.resources.canvas_camera_device
import churchpresenter.composeapp.generated.resources.canvas_camera_name
import churchpresenter.composeapp.generated.resources.canvas_camera_none_found
import churchpresenter.composeapp.generated.resources.canvas_camera_refresh
import churchpresenter.composeapp.generated.resources.canvas_capture_x
import churchpresenter.composeapp.generated.resources.canvas_capture_y
import churchpresenter.composeapp.generated.resources.canvas_capture_width
import churchpresenter.composeapp.generated.resources.canvas_capture_height
import churchpresenter.composeapp.generated.resources.canvas_capture_mode
import churchpresenter.composeapp.generated.resources.canvas_capture_mode_region
import churchpresenter.composeapp.generated.resources.canvas_capture_mode_window
import churchpresenter.composeapp.generated.resources.canvas_capture_window
import churchpresenter.composeapp.generated.resources.canvas_capture_refresh_windows
import churchpresenter.composeapp.generated.resources.canvas_capture_interval
import churchpresenter.composeapp.generated.resources.position
import churchpresenter.composeapp.generated.resources.canvas_image_not_found
import churchpresenter.composeapp.generated.resources.canvas_properties
import churchpresenter.composeapp.generated.resources.canvas_source_color
import churchpresenter.composeapp.generated.resources.canvas_source_image
import churchpresenter.composeapp.generated.resources.canvas_source_text
import churchpresenter.composeapp.generated.resources.canvas_source_video
import churchpresenter.composeapp.generated.resources.canvas_transform
import churchpresenter.composeapp.generated.resources.canvas_transparent_bg
import churchpresenter.composeapp.generated.resources.ic_folder
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.models.SceneSource
import org.churchpresenter.app.churchpresenter.models.SourceTransform
import org.churchpresenter.app.churchpresenter.utils.Utils
import org.jetbrains.compose.resources.painterResource
import java.io.File
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

@Composable
fun SourcePropertiesPanel(
    source: SceneSource,
    modifier: Modifier = Modifier,
    onSourceUpdate: (SceneSource) -> Unit
) {
    Column(
        modifier = modifier
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(Res.string.canvas_properties),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Name
        PropertyTextField("Name", source.name) { newName ->
            onSourceUpdate(updateName(source, newName))
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Transform
        Text(stringResource(Res.string.canvas_transform), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        val t = source.transform
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PropertyFloatField("X", t.x, Modifier.weight(1f)) { v ->
                onSourceUpdate(updateTransform(source, t.copy(x = v)))
            }
            PropertyFloatField("Y", t.y, Modifier.weight(1f)) { v ->
                onSourceUpdate(updateTransform(source, t.copy(y = v)))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PropertyFloatField("W", t.width, Modifier.weight(1f)) { v ->
                onSourceUpdate(updateTransform(source, t.copy(width = v.coerceAtLeast(0.01f))))
            }
            PropertyFloatField("H", t.height, Modifier.weight(1f)) { v ->
                onSourceUpdate(updateTransform(source, t.copy(height = v.coerceAtLeast(0.01f))))
            }
        }

        PropertySliderWithInput("Rotation", t.rotation, -180f, 180f, "°") { v ->
            onSourceUpdate(updateTransform(source, t.copy(rotation = v)))
        }
        PropertySlider("Opacity", t.opacity, 0f, 1f) { v ->
            onSourceUpdate(updateTransform(source, t.copy(opacity = v)))
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Type-specific properties
        when (source) {
            is SceneSource.ImageSource -> ImageProperties(source, onSourceUpdate)
            is SceneSource.TextSource -> TextProperties(source, onSourceUpdate)
            is SceneSource.ColorSource -> ColorProperties(source, onSourceUpdate)
            is SceneSource.VideoSource -> VideoProperties(source, onSourceUpdate)
            is SceneSource.BrowserSource -> BrowserProperties(source, onSourceUpdate)
            is SceneSource.ShapeSource -> ShapeProperties(source, onSourceUpdate)
            is SceneSource.ClockSource -> ClockProperties(source, onSourceUpdate)
            is SceneSource.QRCodeSource -> QRCodeProperties(source, onSourceUpdate)
            is SceneSource.CameraSource -> CameraProperties(source, onSourceUpdate)
            is SceneSource.ScreenCaptureSource -> ScreenCaptureProperties(source, onSourceUpdate)
        }
    }
}

@Composable
private fun ImageProperties(source: SceneSource.ImageSource, onUpdate: (SceneSource) -> Unit) {
    val scope = rememberCoroutineScope()
    Text(stringResource(Res.string.canvas_source_image), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PropertyTextField("File Path", source.filePath, Modifier.weight(1f)) { v ->
            onUpdate(source.copy(filePath = v))
        }
        Button(
            onClick = {
                scope.launch {
                    val imageFilter = FileNameExtensionFilter(
                        "Image Files",
                        "png", "jpg", "jpeg", "gif", "bmp", "webp", "heic", "heif", "svg"
                    )
                    val startPath = if (source.filePath.isNotEmpty()) {
                        try { Path(source.filePath).parent } catch (_: Exception) { null }
                    } else null
                    val file = FileChooser.platformInstance.chooseSingle(
                        path = startPath,
                        filters = listOf(imageFilter),
                        title = "Select Image",
                        selectDirectory = false
                    )
                    if (file != null) {
                        onUpdate(source.copy(filePath = file.absolutePathString()))
                    }
                }
            },
            modifier = Modifier.height(40.dp)
        ) {
            Icon(
                painterResource(Res.drawable.ic_folder),
                contentDescription = "Browse",
                modifier = Modifier.size(16.dp)
            )
        }
    }
    val scaleOptions = listOf("Fit", "Fill", "Stretch", "None")
    val scaleMap = mapOf("FIT" to "Fit", "FILL" to "Fill", "STRETCH" to "Stretch", "NONE" to "None")
    val reverseMap = mapOf("Fit" to "FIT", "Fill" to "FILL", "Stretch" to "STRETCH", "None" to "NONE")
    DropdownSelector(
        label = "Scale",
        items = scaleOptions,
        selected = scaleMap[source.contentScale] ?: "Fit",
        onSelectedChange = { onUpdate(source.copy(contentScale = reverseMap[it] ?: "FIT")) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun TextProperties(source: SceneSource.TextSource, onUpdate: (SceneSource) -> Unit) {
    val isTransparentBg = source.backgroundColor.equals("#00000000", ignoreCase = true)

    val availableFonts = remember { Utils.getAvailableSystemFonts() }

    Text(stringResource(Res.string.canvas_source_text), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    PropertyTextField("Text", source.text) { v ->
        onUpdate(source.copy(text = v))
    }
    FontDropdown(
        label = "Font",
        selected = source.fontFamily,
        fonts = availableFonts,
        onSelectedChange = { onUpdate(source.copy(fontFamily = it)) },
        modifier = Modifier.fillMaxWidth()
    )
    PropertyTextField("Font Size", source.fontSize.toString()) { v ->
        v.toIntOrNull()?.let { onUpdate(source.copy(fontSize = it)) }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(Res.string.canvas_font_color), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ColorPickerField(
            color = source.fontColor,
            onColorChange = { onUpdate(source.copy(fontColor = it)) }
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Checkbox(
            checked = isTransparentBg,
            onCheckedChange = { checked ->
                onUpdate(source.copy(
                    backgroundColor = if (checked) "#00000000" else "#000000"
                ))
            }
        )
        Text(stringResource(Res.string.canvas_transparent_bg), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (!isTransparentBg) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(Res.string.canvas_bg_color), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ColorPickerField(
                color = source.backgroundColor,
                onColorChange = { onUpdate(source.copy(backgroundColor = it)) }
            )
        }
    }
}

@Composable
private fun ColorProperties(source: SceneSource.ColorSource, onUpdate: (SceneSource) -> Unit) {
    Text(stringResource(Res.string.canvas_source_color), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(Res.string.canvas_color_1), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ColorPickerField(
            color = source.color,
            onColorChange = { onUpdate(source.copy(color = it)) }
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Checkbox(
            checked = source.isGradient,
            onCheckedChange = { onUpdate(source.copy(isGradient = it)) }
        )
        Text(stringResource(Res.string.canvas_gradient), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    PropertySlider("${stringResource(Res.string.canvas_color_1)} ${stringResource(Res.string.canvas_opacity)}", source.sourceOpacity, 0f, 1f) { v ->
        onUpdate(source.copy(sourceOpacity = v))
    }
    if (source.isGradient) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(Res.string.canvas_color_2), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ColorPickerField(
                color = source.gradientColor2,
                onColorChange = { onUpdate(source.copy(gradientColor2 = it)) }
            )
        }
        PropertySlider("${stringResource(Res.string.canvas_color_2)} ${stringResource(Res.string.canvas_opacity)}", source.gradientColor2Opacity, 0f, 1f) { v ->
            onUpdate(source.copy(gradientColor2Opacity = v))
        }
        PropertySliderWithInput(stringResource(Res.string.canvas_angle), source.gradientAngle, 0f, 360f, "°") { v ->
            onUpdate(source.copy(gradientAngle = v))
        }
        PropertySliderWithInput(stringResource(Res.string.position), source.gradientPosition * 100f, 0f, 100f, "%") { v ->
            onUpdate(source.copy(gradientPosition = (v / 100f).coerceIn(0f, 1f)))
        }
    }
}

@Composable
private fun VideoProperties(source: SceneSource.VideoSource, onUpdate: (SceneSource) -> Unit) {
    val scope = rememberCoroutineScope()
    Text(stringResource(Res.string.canvas_source_video), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PropertyTextField("File Path", source.filePath, Modifier.weight(1f)) { v ->
            onUpdate(source.copy(filePath = v))
        }
        Button(
            onClick = {
                scope.launch {
                    val videoFilter = FileNameExtensionFilter(
                        "Video Files",
                        "mp4", "mov", "avi", "mkv", "wmv", "flv", "webm", "m4v"
                    )
                    val startPath = if (source.filePath.isNotEmpty()) {
                        try { Path(source.filePath).parent } catch (_: Exception) { null }
                    } else null
                    val file = FileChooser.platformInstance.chooseSingle(
                        path = startPath,
                        filters = listOf(videoFilter),
                        title = "Select Video",
                        selectDirectory = false
                    )
                    if (file != null) {
                        onUpdate(source.copy(filePath = file.absolutePathString()))
                    }
                }
            },
            modifier = Modifier.height(40.dp)
        ) {
            Icon(
                painterResource(Res.drawable.ic_folder),
                contentDescription = "Browse",
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun BrowserProperties(source: SceneSource.BrowserSource, onUpdate: (SceneSource) -> Unit) {
    Text(stringResource(Res.string.canvas_source_browser), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    PropertyTextField("URL", source.url) { v ->
        onUpdate(source.copy(url = v))
    }
}

@Composable
private fun ShapeProperties(source: SceneSource.ShapeSource, onUpdate: (SceneSource) -> Unit) {
    Text(
        stringResource(Res.string.canvas_source_shape),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(Res.string.canvas_shape_stroke_color),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ColorPickerField(
            color = source.strokeColor,
            onColorChange = { onUpdate(source.copy(strokeColor = it)) }
        )
    }
    PropertySlider("${stringResource(Res.string.canvas_shape_stroke_color)} ${stringResource(Res.string.canvas_opacity)}", source.strokeOpacity, 0f, 1f) { v ->
        onUpdate(source.copy(strokeOpacity = v))
    }

    val isStrokeOnly = source.shapeType in listOf("line", "arrow", "freehand")

    if (!isStrokeOnly) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(Res.string.canvas_shape_fill_color),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ColorPickerField(
                color = source.fillColor,
                onColorChange = { onUpdate(source.copy(fillColor = it)) }
            )
        }
        PropertySlider("${stringResource(Res.string.canvas_shape_fill_color)} ${stringResource(Res.string.canvas_opacity)}", source.fillOpacity, 0f, 1f) { v ->
            onUpdate(source.copy(fillOpacity = v))
        }
    }

    PropertySliderWithInput(
        stringResource(Res.string.canvas_shape_stroke_width),
        source.strokeWidth, 1f, 20f, "px"
    ) { v ->
        onUpdate(source.copy(strokeWidth = v))
    }

    if (!isStrokeOnly) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Checkbox(
                checked = source.isGradient,
                onCheckedChange = { onUpdate(source.copy(isGradient = it)) }
            )
            Text(
                stringResource(Res.string.canvas_gradient),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (source.isGradient) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(Res.string.canvas_color_2),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ColorPickerField(
                    color = source.gradientColor2,
                    onColorChange = { onUpdate(source.copy(gradientColor2 = it)) }
                )
            }
            PropertySlider("${stringResource(Res.string.canvas_color_2)} ${stringResource(Res.string.canvas_opacity)}", source.gradientColor2Opacity, 0f, 1f) { v ->
                onUpdate(source.copy(gradientColor2Opacity = v))
            }
            PropertySliderWithInput(stringResource(Res.string.canvas_angle), source.gradientAngle, 0f, 360f, "\u00B0") { v ->
                onUpdate(source.copy(gradientAngle = v))
            }
            PropertySliderWithInput(stringResource(Res.string.position), source.gradientPosition * 100f, 0f, 100f, "%") { v ->
                onUpdate(source.copy(gradientPosition = (v / 100f).coerceIn(0f, 1f)))
            }
        }
    }
}

@Composable
private fun ClockProperties(source: SceneSource.ClockSource, onUpdate: (SceneSource) -> Unit) {
    Text(
        stringResource(Res.string.canvas_source_clock),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    DropdownSelector(
        label = stringResource(Res.string.canvas_clock_mode),
        items = listOf("Clock", "Countdown"),
        selected = if (source.mode == "countdown") "Countdown" else "Clock",
        onSelectedChange = { onUpdate(source.copy(mode = if (it == "Countdown") "countdown" else "clock")) },
        modifier = Modifier.fillMaxWidth()
    )
    DropdownSelector(
        label = stringResource(Res.string.canvas_clock_format),
        items = listOf("24h", "12h"),
        selected = source.timeFormat,
        onSelectedChange = { onUpdate(source.copy(timeFormat = it)) },
        modifier = Modifier.fillMaxWidth()
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Checkbox(checked = source.showHours, onCheckedChange = { onUpdate(source.copy(showHours = it)) })
        Text(stringResource(Res.string.canvas_clock_show_hours), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Checkbox(checked = source.showSeconds, onCheckedChange = { onUpdate(source.copy(showSeconds = it)) })
        Text(stringResource(Res.string.canvas_clock_show_seconds), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Checkbox(checked = source.bold, onCheckedChange = { onUpdate(source.copy(bold = it)) })
        Text(stringResource(Res.string.canvas_text_bold), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    PropertyTextField(stringResource(Res.string.canvas_clock_font_size), source.fontSize.toString()) { v ->
        v.toIntOrNull()?.let { onUpdate(source.copy(fontSize = it.coerceIn(8, 500))) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.canvas_text_color), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ColorPickerField(color = source.fontColor, onColorChange = { onUpdate(source.copy(fontColor = it)) })
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.canvas_text_bg_color), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ColorPickerField(color = source.backgroundColor, onColorChange = { onUpdate(source.copy(backgroundColor = it)) })
    }
    if (source.mode == "countdown") {
        PropertyTextField(stringResource(Res.string.canvas_clock_target_hour), source.targetHour.toString()) { v ->
            v.toIntOrNull()?.let { onUpdate(source.copy(targetHour = it.coerceIn(0, 23))) }
        }
        PropertyTextField(stringResource(Res.string.canvas_clock_target_minute), source.targetMinute.toString()) { v ->
            v.toIntOrNull()?.let { onUpdate(source.copy(targetMinute = it.coerceIn(0, 59))) }
        }
        PropertyTextField(stringResource(Res.string.canvas_clock_target_second), source.targetSecond.toString()) { v ->
            v.toIntOrNull()?.let { onUpdate(source.copy(targetSecond = it.coerceIn(0, 59))) }
        }
    }
}

@Composable
private fun QRCodeProperties(source: SceneSource.QRCodeSource, onUpdate: (SceneSource) -> Unit) {
    Text(
        stringResource(Res.string.canvas_source_qrcode),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val typeOptions = listOf("URL", "Text", "Email", "Phone", "SMS", "WiFi", "vCard")
    val typeMap = mapOf(
        "url" to "URL", "text" to "Text", "email" to "Email", "phone" to "Phone",
        "sms" to "SMS", "wifi" to "WiFi", "vcard" to "vCard"
    )
    val reverseTypeMap = mapOf(
        "URL" to "url", "Text" to "text", "Email" to "email", "Phone" to "phone",
        "SMS" to "sms", "WiFi" to "wifi", "vCard" to "vcard"
    )
    DropdownSelector(
        label = stringResource(Res.string.canvas_qr_type),
        items = typeOptions,
        selected = typeMap[source.contentType] ?: "URL",
        onSelectedChange = { newType ->
            val type = reverseTypeMap[newType] ?: "url"
            val prefill = when (type) {
                "url" -> "https://example.com"
                "text" -> "Your text here"
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Checkbox(checked = source.wifiHidden, onCheckedChange = { onUpdate(source.copy(wifiHidden = it)) })
            Text(stringResource(Res.string.canvas_qr_wifi_hidden), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        PropertyTextField(stringResource(Res.string.canvas_qr_content), source.content) { v ->
            onUpdate(source.copy(content = v))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.canvas_qr_foreground), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ColorPickerField(color = source.foregroundColor, onColorChange = { onUpdate(source.copy(foregroundColor = it)) })
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Checkbox(
            checked = source.transparentBackground,
            onCheckedChange = { onUpdate(source.copy(transparentBackground = it)) }
        )
        Text(stringResource(Res.string.canvas_transparent_bg), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (!source.transparentBackground) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.canvas_qr_background), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ColorPickerField(color = source.backgroundColor, onColorChange = { onUpdate(source.copy(backgroundColor = it)) })
        }
    }
    DropdownSelector(
        label = stringResource(Res.string.canvas_qr_error_correction),
        items = listOf("L", "M", "Q", "H"),
        selected = source.errorCorrection,
        onSelectedChange = { onUpdate(source.copy(errorCorrection = it)) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CameraProperties(source: SceneSource.CameraSource, onUpdate: (SceneSource) -> Unit) {
    Text(
        stringResource(Res.string.canvas_source_camera),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    var devices by remember { mutableStateOf(listCameraDevices()) }
    val noCamerasLabel = stringResource(Res.string.canvas_camera_none_found)

    Button(
        onClick = { devices = listCameraDevices() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(Res.string.canvas_camera_refresh), style = MaterialTheme.typography.labelSmall)
    }

    if (devices.isNotEmpty()) {
        val items = devices.map { it.displayName }
        val selectedDisplay = devices.find { it.path == source.devicePath }?.displayName
            ?: if (source.devicePath.isNotEmpty()) source.devicePath else items.first()
        DropdownSelector(
            label = stringResource(Res.string.canvas_camera_device),
            items = items,
            selected = selectedDisplay,
            onSelectedChange = { selected ->
                val device = devices.find { it.displayName == selected }
                if (device != null) {
                    onUpdate(source.copy(devicePath = device.path, deviceName = device.name))
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        Text(
            noCamerasLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class CameraDevice(val name: String, val path: String, val displayName: String)

private fun listCameraDevices(): List<CameraDevice> {
    val osName = System.getProperty("os.name", "").lowercase()
    return when {
        osName.contains("linux") -> listLinuxCameras()
        osName.contains("win") -> listWindowsCameras()
        osName.contains("mac") -> listMacCameras()
        else -> emptyList()
    }
}

private fun listLinuxCameras(): List<CameraDevice> {
    return try {
        val videoDir = File("/dev")
        videoDir.listFiles { f -> f.name.startsWith("video") }
            ?.sorted()
            ?.map { file ->
                val name = try {
                    val nameFile = File("/sys/class/video4linux/${file.name}/name")
                    if (nameFile.exists()) nameFile.readText().trim() else file.name
                } catch (_: Exception) { file.name }
                CameraDevice(
                    name = name,
                    path = "v4l2://${file.absolutePath}",
                    displayName = "$name (${file.name})"
                )
            } ?: emptyList()
    } catch (_: Exception) { emptyList() }
}

private fun listWindowsCameras(): List<CameraDevice> {
    return try {
        val process = ProcessBuilder("wmic", "path", "Win32_PnPEntity", "where",
            "PNPClass='Camera' OR PNPClass='Image'", "get", "Name", "/format:list")
            .redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        output.lines()
            .filter { it.startsWith("Name=") }
            .mapIndexed { index, line ->
                val name = line.removePrefix("Name=").trim()
                CameraDevice(
                    name = name,
                    path = "dshow://",
                    displayName = name
                )
            }
    } catch (_: Exception) { emptyList() }
}

private fun listMacCameras(): List<CameraDevice> {
    return try {
        val process = ProcessBuilder("system_profiler", "SPCameraDataType", "-detailLevel", "mini")
            .redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        val names = output.lines()
            .filter { it.contains(":") && !it.trim().startsWith("Camera") && it.trim().endsWith(":") }
            .map { it.trim().removeSuffix(":") }
        names.mapIndexed { index, name ->
            CameraDevice(
                name = name,
                path = "avfoundation://$index",
                displayName = name
            )
        }
    } catch (_: Exception) { emptyList() }
}

@Composable
private fun ScreenCaptureProperties(source: SceneSource.ScreenCaptureSource, onUpdate: (SceneSource) -> Unit) {
    Text(
        stringResource(Res.string.canvas_source_screen_capture),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val regionLabel = stringResource(Res.string.canvas_capture_mode_region)
    val windowLabel = stringResource(Res.string.canvas_capture_mode_window)
    DropdownSelector(
        label = stringResource(Res.string.canvas_capture_mode),
        items = listOf(regionLabel, windowLabel),
        selected = if (source.captureMode == "window") windowLabel else regionLabel,
        onSelectedChange = {
            val mode = if (it == windowLabel) "window" else "region"
            onUpdate(source.copy(captureMode = mode))
        },
        modifier = Modifier.fillMaxWidth()
    )
    if (source.captureMode == "window") {
        var windows by remember { mutableStateOf(listOpenWindows()) }
        val windowTitles = windows.map { it.title }

        Button(
            onClick = { windows = listOpenWindows() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(Res.string.canvas_capture_refresh_windows), style = MaterialTheme.typography.labelSmall)
        }

        if (windowTitles.isNotEmpty()) {
            DropdownSelector(
                label = stringResource(Res.string.canvas_capture_window),
                items = windowTitles,
                selected = if (source.windowTitle in windowTitles) source.windowTitle else windowTitles.first(),
                onSelectedChange = { onUpdate(source.copy(windowTitle = it)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        PropertyTextField(stringResource(Res.string.canvas_capture_x), source.captureX.toString()) { v ->
            v.toIntOrNull()?.let { onUpdate(source.copy(captureX = it.coerceAtLeast(0))) }
        }
        PropertyTextField(stringResource(Res.string.canvas_capture_y), source.captureY.toString()) { v ->
            v.toIntOrNull()?.let { onUpdate(source.copy(captureY = it.coerceAtLeast(0))) }
        }
        PropertyTextField(stringResource(Res.string.canvas_capture_width), source.captureWidth.toString()) { v ->
            v.toIntOrNull()?.let { onUpdate(source.copy(captureWidth = it.coerceAtLeast(1))) }
        }
        PropertyTextField(stringResource(Res.string.canvas_capture_height), source.captureHeight.toString()) { v ->
            v.toIntOrNull()?.let { onUpdate(source.copy(captureHeight = it.coerceAtLeast(1))) }
        }
    }
    PropertySliderWithInput(stringResource(Res.string.canvas_capture_interval), source.captureInterval.toFloat(), 33f, 1000f, "ms") { v ->
        onUpdate(source.copy(captureInterval = v.toInt()))
    }
}

private data class WindowInfo(val title: String, val id: Long)

private fun listOpenWindows(): List<WindowInfo> {
    val osName = System.getProperty("os.name", "").lowercase()
    return when {
        osName.contains("linux") -> listLinuxWindows()
        osName.contains("win") -> listWindowsWindows()
        osName.contains("mac") -> listMacWindows()
        else -> emptyList()
    }
}

private fun listLinuxWindows(): List<WindowInfo> {
    // Try wmctrl first (most reliable)
    try {
        val process = ProcessBuilder("wmctrl", "-l")
            .redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        if (process.exitValue() == 0) {
            val windows = output.lines()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val parts = line.split(Regex("\\s+"), limit = 5)
                    if (parts.size >= 5) {
                        val id = parts[0].removePrefix("0x").toLongOrNull(16) ?: 0L
                        val title = parts[4]
                        if (title.isNotBlank()) WindowInfo(title, id) else null
                    } else null
                }
            if (windows.isNotEmpty()) return windows
        }
    } catch (_: Exception) {}

    // Fallback: xdotool search for visible windows, get each name
    try {
        val idProcess = ProcessBuilder("xdotool", "search", "--onlyvisible", "--name", "")
            .redirectErrorStream(true).start()
        val ids = idProcess.inputStream.bufferedReader().readText()
        idProcess.waitFor()
        val windows = ids.lines().filter { it.isNotBlank() }.mapNotNull { id ->
            try {
                val nameProcess = ProcessBuilder("xdotool", "getwindowname", id)
                    .redirectErrorStream(true).start()
                val name = nameProcess.inputStream.bufferedReader().readText().trim()
                nameProcess.waitFor()
                if (name.isNotBlank()) WindowInfo(name, id.toLongOrNull() ?: 0L) else null
            } catch (_: Exception) { null }
        }.distinctBy { it.title }
        if (windows.isNotEmpty()) return windows
    } catch (_: Exception) {}

    // Last fallback: xprop on root window
    try {
        val process = ProcessBuilder("bash", "-c",
            "xprop -root _NET_CLIENT_LIST_STACKING | grep -oP '0x[0-9a-fA-F]+' | while read wid; do xdotool getwindowname \"\$wid\" 2>/dev/null; done")
            .redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return output.lines()
            .filter { it.isNotBlank() }
            .distinct()
            .map { WindowInfo(it, 0L) }
    } catch (_: Exception) { return emptyList() }
}

private fun listWindowsWindows(): List<WindowInfo> {
    return try {
        val process = ProcessBuilder("powershell", "-Command",
            "Get-Process | Where-Object {\$_.MainWindowTitle -ne ''} | Select-Object -Property MainWindowTitle | Format-List")
            .redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        output.lines()
            .filter { it.startsWith("MainWindowTitle") }
            .mapNotNull { line ->
                val title = line.substringAfter(":").trim()
                if (title.isNotBlank()) WindowInfo(title, 0L) else null
            }
    } catch (_: Exception) { emptyList() }
}

private fun listMacWindows(): List<WindowInfo> {
    return try {
        val script = """
            tell application "System Events"
                set windowList to {}
                repeat with proc in (every process whose visible is true)
                    repeat with win in (every window of proc)
                        set end of windowList to (name of win)
                    end repeat
                end repeat
                return windowList
            end tell
        """.trimIndent()
        val process = ProcessBuilder("osascript", "-e", script)
            .redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        output.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { WindowInfo(it, 0L) }
    } catch (_: Exception) { emptyList() }
}

// --- Helper composables ---

@Composable
private fun PropertyTextField(label: String, value: String, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onValueChange(it)
        },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun PropertyFloatField(label: String, value: Float, modifier: Modifier = Modifier, onValueChange: (Float) -> Unit) {
    var text by remember(value) { mutableStateOf("%.3f".format(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.toFloatOrNull()?.let(onValueChange)
        },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun PropertySlider(label: String, value: Float, min: Float, max: Float, onValueChange: (Float) -> Unit) {
    Column {
        Text(
            "$label: ${"%.2f".format(value)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PropertySliderWithInput(label: String, value: Float, min: Float, max: Float, suffix: String = "", onValueChange: (Float) -> Unit) {
    var textValue by remember(value) { mutableStateOf(value.toInt().toString()) }
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Slider(
                value = value.coerceIn(min, max),
                onValueChange = {
                    textValue = it.toInt().toString()
                    onValueChange(it)
                },
                valueRange = min..max,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = textValue,
                onValueChange = { input ->
                    textValue = input
                    input.toFloatOrNull()?.let { onValueChange(it.coerceIn(min, max)) }
                },
                singleLine = true,
                modifier = Modifier.width(60.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                suffix = if (suffix.isNotEmpty()) { { Text(suffix, style = MaterialTheme.typography.bodySmall) } } else null
            )
        }
    }
}

// --- Transform update helpers ---

private fun updateName(source: SceneSource, name: String): SceneSource = when (source) {
    is SceneSource.ImageSource -> source.copy(name = name)
    is SceneSource.TextSource -> source.copy(name = name)
    is SceneSource.ColorSource -> source.copy(name = name)
    is SceneSource.VideoSource -> source.copy(name = name)
    is SceneSource.BrowserSource -> source.copy(name = name)
    is SceneSource.ShapeSource -> source.copy(name = name)
    is SceneSource.ClockSource -> source.copy(name = name)
    is SceneSource.QRCodeSource -> source.copy(name = name)
    is SceneSource.CameraSource -> source.copy(name = name)
    is SceneSource.ScreenCaptureSource -> source.copy(name = name)
}

private fun updateTransform(source: SceneSource, transform: SourceTransform): SceneSource = when (source) {
    is SceneSource.ImageSource -> source.copy(transform = transform)
    is SceneSource.TextSource -> source.copy(transform = transform)
    is SceneSource.ColorSource -> source.copy(transform = transform)
    is SceneSource.VideoSource -> source.copy(transform = transform)
    is SceneSource.BrowserSource -> source.copy(transform = transform)
    is SceneSource.ShapeSource -> source.copy(transform = transform)
    is SceneSource.ClockSource -> source.copy(transform = transform)
    is SceneSource.QRCodeSource -> source.copy(transform = transform)
    is SceneSource.CameraSource -> source.copy(transform = transform)
    is SceneSource.ScreenCaptureSource -> source.copy(transform = transform)
}

@Composable
private fun FontDropdown(
    label: String,
    selected: String,
    fonts: List<String>,
    onSelectedChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded = rememberSaveable { mutableStateOf(false) }
    val selectedFontFamily = remember(selected) { systemFontFamilyOrDefault(selected) }

    Box {
        OutlinedTextField(
            modifier = modifier,
            interactionSource = remember { MutableInteractionSource() }
                .also { interactionSource ->
                    LaunchedEffect(interactionSource) {
                        interactionSource.interactions.collect {
                            if (it is PressInteraction.Release) {
                                expanded.value = true
                            }
                        }
                    }
                },
            value = selected,
            onValueChange = {},
            readOnly = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = selectedFontFamily),
            label = { Text(text = label, style = MaterialTheme.typography.bodyMedium) },
            trailingIcon = { Text(stringResource(Res.string.symbol_dropdown)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors().copy(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
            )
        )
        DropdownMenu(
            containerColor = MaterialTheme.colorScheme.surface,
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false }
        ) {
            val scrollState = rememberScrollState()
            Box(modifier = Modifier.height(300.dp).width(220.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .verticalScroll(scrollState)
                        .padding(end = 10.dp)
                ) {
                    fonts.forEach { font ->
                        val fontFamily = remember(font) { systemFontFamilyOrDefault(font) }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    font,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = fontFamily)
                                )
                            },
                            onClick = {
                                onSelectedChange(font)
                                expanded.value = false
                            }
                        )
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState),
                    modifier = Modifier.align(Alignment.CenterEnd).height(300.dp),
                    style = LocalScrollbarStyle.current.copy(
                        thickness = 8.dp,
                        minimalHeight = 24.dp,
                        unhoverColor = Color.Gray.copy(alpha = 0.5f),
                        hoverColor = Color.Gray.copy(alpha = 0.9f)
                    )
                )
            }
        }
    }
}
