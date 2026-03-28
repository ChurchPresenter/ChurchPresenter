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
    PropertySlider("${stringResource(Res.string.canvas_color_1)} Opacity", source.sourceOpacity, 0f, 1f) { v ->
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
        PropertySlider("${stringResource(Res.string.canvas_color_2)} Opacity", source.gradientColor2Opacity, 0f, 1f) { v ->
            onUpdate(source.copy(gradientColor2Opacity = v))
        }
        PropertySliderWithInput("Angle", source.gradientAngle, 0f, 360f, "°") { v ->
            onUpdate(source.copy(gradientAngle = v))
        }
        PropertySliderWithInput("Position", source.gradientPosition * 100f, 0f, 100f, "%") { v ->
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
    Text("Browser", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    PropertySlider("${stringResource(Res.string.canvas_shape_stroke_color)} Opacity", source.strokeOpacity, 0f, 1f) { v ->
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
        PropertySlider("${stringResource(Res.string.canvas_shape_fill_color)} Opacity", source.fillOpacity, 0f, 1f) { v ->
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
            PropertySlider("${stringResource(Res.string.canvas_color_2)} Opacity", source.gradientColor2Opacity, 0f, 1f) { v ->
                onUpdate(source.copy(gradientColor2Opacity = v))
            }
            PropertySliderWithInput("Angle", source.gradientAngle, 0f, 360f, "\u00B0") { v ->
                onUpdate(source.copy(gradientAngle = v))
            }
            PropertySliderWithInput("Position", source.gradientPosition * 100f, 0f, 100f, "%") { v ->
                onUpdate(source.copy(gradientPosition = (v / 100f).coerceIn(0f, 1f)))
            }
        }
    }
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
}

private fun updateTransform(source: SceneSource, transform: SourceTransform): SceneSource = when (source) {
    is SceneSource.ImageSource -> source.copy(transform = transform)
    is SceneSource.TextSource -> source.copy(transform = transform)
    is SceneSource.ColorSource -> source.copy(transform = transform)
    is SceneSource.VideoSource -> source.copy(transform = transform)
    is SceneSource.BrowserSource -> source.copy(transform = transform)
    is SceneSource.ShapeSource -> source.copy(transform = transform)
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
