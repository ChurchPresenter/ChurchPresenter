package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.canvas_bg_color
import churchpresenter.composeapp.generated.resources.canvas_color_1
import churchpresenter.composeapp.generated.resources.canvas_color_2
import churchpresenter.composeapp.generated.resources.canvas_font_color
import churchpresenter.composeapp.generated.resources.canvas_gradient
import churchpresenter.composeapp.generated.resources.canvas_angle
import churchpresenter.composeapp.generated.resources.canvas_opacity
import churchpresenter.composeapp.generated.resources.canvas_source_browser
import churchpresenter.composeapp.generated.resources.canvas_clock_font_size
import churchpresenter.composeapp.generated.resources.position
import churchpresenter.composeapp.generated.resources.canvas_file_path
import churchpresenter.composeapp.generated.resources.canvas_browse
import churchpresenter.composeapp.generated.resources.canvas_scale
import churchpresenter.composeapp.generated.resources.canvas_scale_fit
import churchpresenter.composeapp.generated.resources.canvas_scale_fill
import churchpresenter.composeapp.generated.resources.canvas_scale_stretch
import churchpresenter.composeapp.generated.resources.canvas_scale_none
import churchpresenter.composeapp.generated.resources.canvas_expand_text_field
import churchpresenter.composeapp.generated.resources.canvas_text_content
import churchpresenter.composeapp.generated.resources.close
import churchpresenter.composeapp.generated.resources.canvas_line_spacing
import churchpresenter.composeapp.generated.resources.canvas_font
import churchpresenter.composeapp.generated.resources.canvas_align_horizontal
import churchpresenter.composeapp.generated.resources.canvas_align_vertical
import churchpresenter.composeapp.generated.resources.canvas_render_width
import churchpresenter.composeapp.generated.resources.canvas_render_height
import churchpresenter.composeapp.generated.resources.canvas_fps
import churchpresenter.composeapp.generated.resources.canvas_custom_css
import churchpresenter.composeapp.generated.resources.canvas_browser_url
import churchpresenter.composeapp.generated.resources.canvas_select_image_title
import churchpresenter.composeapp.generated.resources.canvas_select_video_title
import churchpresenter.composeapp.generated.resources.canvas_image_files
import churchpresenter.composeapp.generated.resources.canvas_video_files
import churchpresenter.composeapp.generated.resources.canvas_source_color
import churchpresenter.composeapp.generated.resources.canvas_source_image
import churchpresenter.composeapp.generated.resources.canvas_source_text
import churchpresenter.composeapp.generated.resources.canvas_source_video
import churchpresenter.composeapp.generated.resources.canvas_video_loop
import churchpresenter.composeapp.generated.resources.canvas_video_volume
import churchpresenter.composeapp.generated.resources.canvas_transparent_bg
import churchpresenter.composeapp.generated.resources.ic_folder
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.models.scene.SceneSource
import org.churchpresenter.app.churchpresenter.utils.rememberSystemFonts
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.jetbrains.compose.resources.painterResource
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

private const val MAX_ANGLE_DEGREES = 360f
private const val PERCENT_SCALE = 100f
private const val MIN_RENDER_WIDTH = 320
private const val MAX_RENDER_WIDTH = 3840
private const val MIN_RENDER_HEIGHT = 240
private const val MAX_RENDER_HEIGHT = 2160
private const val MAX_FPS = 60

/**
 * The scene sources that come from a file, a URL or plain typing: image, text, colour, video
 * and browser.
 */

@Composable
internal fun ImageProperties(source: SceneSource.ImageSource, onUpdate: (SceneSource) -> Unit, fileChooser: FileChooser) {
    val scope = rememberCoroutineScope()
    val strFilePath = stringResource(Res.string.canvas_file_path)
    val strSelectImage = stringResource(Res.string.canvas_select_image_title)
    val strImageFiles = stringResource(Res.string.canvas_image_files)
    val strBrowse = stringResource(Res.string.canvas_browse)
    val fitLabel = stringResource(Res.string.canvas_scale_fit)
    val fillLabel = stringResource(Res.string.canvas_scale_fill)
    val stretchLabel = stringResource(Res.string.canvas_scale_stretch)
    val noneLabel = stringResource(Res.string.canvas_scale_none)

    Text(stringResource(Res.string.canvas_source_image), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PropertyTextField(strFilePath, source.filePath, Modifier.weight(1f)) { v ->
            onUpdate(source.copy(filePath = v))
        }
        Button(
            onClick = {
                scope.launch {
                    val imageFilter = FileNameExtensionFilter(
                        strImageFiles,
                        "png", "jpg", "jpeg", "gif", "bmp", "webp", "heic", "heif", "svg"
                    )
                    val startPath = if (source.filePath.isNotEmpty()) {
                        try { Path(source.filePath).parent } catch (_: Exception) { null }
                    } else null
                    val file = fileChooser.chooseSingle(
                        path = startPath,
                        filters = listOf(imageFilter),
                        title = strSelectImage,
                        selectDirectory = false
                    )
                    if (file != null) {
                        onUpdate(source.copy(filePath = file.absolutePathString()))
                    }
                }
            },
            modifier = Modifier.height(40.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                painterResource(Res.drawable.ic_folder),
                contentDescription = strBrowse,
                modifier = Modifier.size(16.dp)
            )
        }
    }
    val scaleOptions = listOf(fitLabel, fillLabel, stretchLabel, noneLabel)
    val scaleMap = mapOf("FIT" to fitLabel, "FILL" to fillLabel, "STRETCH" to stretchLabel, "NONE" to noneLabel)
    val reverseMap = mapOf(fitLabel to "FIT", fillLabel to "FILL", stretchLabel to "STRETCH", noneLabel to "NONE")
    DropdownSelector(
        label = stringResource(Res.string.canvas_scale),
        items = scaleOptions,
        selected = scaleMap[source.contentScale] ?: fitLabel,
        onSelectedChange = { onUpdate(source.copy(contentScale = reverseMap[it] ?: "FIT")) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun TextProperties(source: SceneSource.TextSource, onUpdate: (SceneSource) -> Unit) {
    val isTransparentBg = source.backgroundColor.equals("#00000000", ignoreCase = true)

    val availableFonts = rememberSystemFonts()

    Text(stringResource(Res.string.canvas_source_text), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

    var textValue by remember(source.text) { mutableStateOf(source.text) }
    var showTextDialog by remember { mutableStateOf(false) }
    StyledTextField(
        value = textValue,
        onValueChange = {
            textValue = it
            onUpdate(source.copy(text = it))
        },
        label = stringResource(Res.string.canvas_text_content),
        singleLine = false,
        minLines = 2,
        maxLines = 5,
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        text = stringResource(Res.string.canvas_expand_text_field),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { showTextDialog = true }.padding(vertical = 2.dp)
    )
    if (showTextDialog) {
        DialogWindow(
            onCloseRequest = { showTextDialog = false },
            state = rememberDialogState(
                width = 600.dp, height = 450.dp
            ),
            title = stringResource(Res.string.canvas_text_content),
            resizable = true
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    StyledTextField(
                        value = textValue,
                        onValueChange = {
                            textValue = it
                            onUpdate(source.copy(text = it))
                        },
                        label = stringResource(Res.string.canvas_text_content),
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(onClick = { showTextDialog = false }, shape = RoundedCornerShape(8.dp)) {
                            Text(stringResource(Res.string.close))
                        }
                    }
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.canvas_line_spacing), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SlimSlider(
            value = source.lineSpacing / 100f,
            onValueChange = { onUpdate(source.copy(lineSpacing = (it * 100).toInt())) },
            valueRange = 0.5f..3f,
            trailingLabel = "${source.lineSpacing}%",
            modifier = Modifier.weight(1f)
        )
    }
    FontDropdown(
        label = stringResource(Res.string.canvas_font),
        selected = source.fontFamily,
        fonts = availableFonts,
        onSelectedChange = { onUpdate(source.copy(fontFamily = it)) },
        modifier = Modifier.fillMaxWidth()
    )
    PropertyTextField(stringResource(Res.string.canvas_clock_font_size), source.fontSize.toString()) { v ->
        v.toIntOrNull()?.let { onUpdate(source.copy(fontSize = it)) }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(stringResource(Res.string.canvas_align_horizontal), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalAlignmentButtons(
                selectedAlignment = source.horizontalAlignment,
                onAlignmentChange = { onUpdate(source.copy(horizontalAlignment = it)) },
                leftValue = "left",
                centerValue = "center",
                rightValue = "right"
            )
        }
        Column {
            Text(stringResource(Res.string.canvas_align_vertical), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            VerticalAlignmentButtons(
                selectedAlignment = source.verticalAlignment,
                onAlignmentChange = { onUpdate(source.copy(verticalAlignment = it)) },
                topValue = "top",
                middleValue = "center",
                bottomValue = "bottom"
            )
        }
    }
    ColorPickerField(
        color = source.fontColor,
        onColorChange = { onUpdate(source.copy(fontColor = it)) },
        label = stringResource(Res.string.canvas_font_color)
    )
    LabeledCheckbox(
        checked = isTransparentBg,
        onCheckedChange = { checked ->
                onUpdate(source.copy(
                    backgroundColor = if (checked) "#00000000" else "#000000"
                ))
            },
        label = stringResource(Res.string.canvas_transparent_bg),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        spacing = 4.dp,
    )
    if (!isTransparentBg) {
        ColorPickerField(
            color = source.backgroundColor,
            onColorChange = { onUpdate(source.copy(backgroundColor = it)) },
            label = stringResource(Res.string.canvas_bg_color)
        )
    }
}

@Composable
internal fun ColorProperties(source: SceneSource.ColorSource, onUpdate: (SceneSource) -> Unit) {
    Text(stringResource(Res.string.canvas_source_color), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ColorPickerField(
        color = source.color,
        onColorChange = { onUpdate(source.copy(color = it)) },
        label = stringResource(Res.string.canvas_color_1)
    )
    LabeledCheckbox(
        checked = source.isGradient,
        onCheckedChange = { onUpdate(source.copy(isGradient = it)) },
        label = stringResource(Res.string.canvas_gradient),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        spacing = 4.dp,
    )
    PropertySlider("${stringResource(Res.string.canvas_color_1)} ${stringResource(Res.string.canvas_opacity)}", source.sourceOpacity, 0f, 1f) { v ->
        onUpdate(source.copy(sourceOpacity = v))
    }
    if (source.isGradient) {
        ColorPickerField(
            color = source.gradientColor2,
            onColorChange = { onUpdate(source.copy(gradientColor2 = it)) },
            label = stringResource(Res.string.canvas_color_2)
        )
        PropertySlider("${stringResource(Res.string.canvas_color_2)} ${stringResource(Res.string.canvas_opacity)}", source.gradientColor2Opacity, 0f, 1f) { v ->
            onUpdate(source.copy(gradientColor2Opacity = v))
        }
        PropertySliderWithInput(stringResource(Res.string.canvas_angle), source.gradientAngle, 0f, MAX_ANGLE_DEGREES, "°") { v ->
            onUpdate(source.copy(gradientAngle = v))
        }
        PropertySliderWithInput(stringResource(Res.string.position), source.gradientPosition * PERCENT_SCALE, 0f, PERCENT_SCALE, "%") { v ->
            onUpdate(source.copy(gradientPosition = (v / 100f).coerceIn(0f, 1f)))
        }
    }
}

@Composable
internal fun VideoProperties(source: SceneSource.VideoSource, onUpdate: (SceneSource) -> Unit, fileChooser: FileChooser) {
    val scope = rememberCoroutineScope()
    val strFilePath = stringResource(Res.string.canvas_file_path)
    val strSelectVideo = stringResource(Res.string.canvas_select_video_title)
    val strVideoFiles = stringResource(Res.string.canvas_video_files)
    val strBrowse = stringResource(Res.string.canvas_browse)

    Text(stringResource(Res.string.canvas_source_video), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PropertyTextField(strFilePath, source.filePath, Modifier.weight(1f)) { v ->
            onUpdate(source.copy(filePath = v))
        }
        Button(
            onClick = {
                scope.launch {
                    val videoFilter = FileNameExtensionFilter(
                        strVideoFiles,
                        "mp4", "mov", "avi", "mkv", "wmv", "flv", "webm", "m4v"
                    )
                    val startPath = if (source.filePath.isNotEmpty()) {
                        try { Path(source.filePath).parent } catch (_: Exception) { null }
                    } else null
                    val file = fileChooser.chooseSingle(
                        path = startPath,
                        filters = listOf(videoFilter),
                        title = strSelectVideo,
                        selectDirectory = false
                    )
                    if (file != null) {
                        onUpdate(source.copy(filePath = file.absolutePathString()))
                    }
                }
            },
            modifier = Modifier.height(40.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                painterResource(Res.drawable.ic_folder),
                contentDescription = strBrowse,
                modifier = Modifier.size(16.dp)
            )
        }
    }

    LabeledCheckbox(
        checked = source.loop,
        onCheckedChange = { onUpdate(source.copy(loop = it)) },
        label = stringResource(Res.string.canvas_video_loop),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        spacing = 4.dp,
    )

    PropertySlider(stringResource(Res.string.canvas_video_volume), source.volume, 0f, 1f) { v ->
        onUpdate(source.copy(volume = v))
    }
}

@Composable
internal fun BrowserProperties(source: SceneSource.BrowserSource, onUpdate: (SceneSource) -> Unit) {
    Text(stringResource(Res.string.canvas_source_browser), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    PropertyTextField(stringResource(Res.string.canvas_browser_url), source.url) { v ->
        onUpdate(source.copy(url = v))
    }

    val currentUrlFlow = remember(source.id) { SharedBrowserFrameCache.getCurrentUrl(source.id) }
    if (currentUrlFlow != null) {
        val currentUrl by currentUrlFlow.collectAsState()
        if (currentUrl.isNotBlank() && currentUrl != source.url) {
            Text(
                text = currentUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PropertyTextField(stringResource(Res.string.canvas_render_width), source.renderWidth.toString(), Modifier.weight(1f)) { v ->
            v.toIntOrNull()?.let { onUpdate(source.copy(renderWidth = it.coerceIn(MIN_RENDER_WIDTH, MAX_RENDER_WIDTH))) }
        }
        PropertyTextField(stringResource(Res.string.canvas_render_height), source.renderHeight.toString(), Modifier.weight(1f)) { v ->
            v.toIntOrNull()?.let { onUpdate(source.copy(renderHeight = it.coerceIn(MIN_RENDER_HEIGHT, MAX_RENDER_HEIGHT))) }
        }
    }
    PropertyTextField(stringResource(Res.string.canvas_fps), source.fps.toString()) { v ->
        v.toIntOrNull()?.let { onUpdate(source.copy(fps = it.coerceIn(1, MAX_FPS))) }
    }
    LabeledCheckbox(
        checked = source.forceTransparent,
        onCheckedChange = { onUpdate(source.copy(forceTransparent = it)) },
        label = stringResource(Res.string.canvas_transparent_bg),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        spacing = 4.dp,
    )
    PropertyTextField(stringResource(Res.string.canvas_custom_css), source.customCss) { v ->
        onUpdate(source.copy(customCss = v))
    }
}
