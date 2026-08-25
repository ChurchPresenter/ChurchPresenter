package org.churchpresenter.canvas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.canvas_opacity
import org.churchpresenter.resources.generated.resources.canvas_source_name
import org.churchpresenter.resources.generated.resources.canvas_rotation
import org.churchpresenter.resources.generated.resources.canvas_properties
import org.churchpresenter.resources.generated.resources.canvas_transform
import org.churchpresenter.resources.generated.resources.canvas_transform_x
import org.churchpresenter.resources.generated.resources.canvas_transform_y
import org.churchpresenter.resources.generated.resources.canvas_transform_w
import org.churchpresenter.resources.generated.resources.canvas_transform_h
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.scene.SceneSource

private const val MIN_SOURCE_SIZE = 0.01f
private const val MAX_ROTATION_DEGREES = 180f

@Composable
fun SourcePropertiesPanel(
    source: SceneSource,
    modifier: Modifier = Modifier,
    appSettings: AppSettings? = null,
    fileChooser: CanvasFilePicker = CanvasFilePicker.None,
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

        PropertyTextField(stringResource(Res.string.canvas_source_name), source.name) { newName ->
            onSourceUpdate(updateName(source, newName))
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Text(stringResource(Res.string.canvas_transform), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        val t = source.transform
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PropertyFloatField(stringResource(Res.string.canvas_transform_x), t.x, Modifier.weight(1f)) { v ->
                onSourceUpdate(updateTransform(source, t.copy(x = v)))
            }
            PropertyFloatField(stringResource(Res.string.canvas_transform_y), t.y, Modifier.weight(1f)) { v ->
                onSourceUpdate(updateTransform(source, t.copy(y = v)))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PropertyFloatField(stringResource(Res.string.canvas_transform_w), t.width, Modifier.weight(1f)) { v ->
                onSourceUpdate(updateTransform(source, t.copy(width = v.coerceAtLeast(MIN_SOURCE_SIZE))))
            }
            PropertyFloatField(stringResource(Res.string.canvas_transform_h), t.height, Modifier.weight(1f)) { v ->
                onSourceUpdate(updateTransform(source, t.copy(height = v.coerceAtLeast(MIN_SOURCE_SIZE))))
            }
        }

        PropertySliderWithInput(stringResource(Res.string.canvas_rotation), t.rotation, -MAX_ROTATION_DEGREES, MAX_ROTATION_DEGREES, "°") { v ->
            onSourceUpdate(updateTransform(source, t.copy(rotation = v)))
        }
        PropertySlider(stringResource(Res.string.canvas_opacity), t.opacity, 0f, 1f) { v ->
            onSourceUpdate(updateTransform(source, t.copy(opacity = v)))
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        when (source) {
            is SceneSource.ImageSource -> ImageProperties(source, onSourceUpdate, fileChooser)
            is SceneSource.TextSource -> TextProperties(source, onSourceUpdate)
            is SceneSource.ColorSource -> ColorProperties(source, onSourceUpdate)
            is SceneSource.VideoSource -> VideoProperties(source, onSourceUpdate, fileChooser)
            is SceneSource.BrowserSource -> BrowserProperties(source, onSourceUpdate)
            is SceneSource.ShapeSource -> ShapeProperties(source, onSourceUpdate)
            is SceneSource.ClockSource -> ClockProperties(source, onSourceUpdate)
            is SceneSource.QRCodeSource -> QRCodeProperties(source, onSourceUpdate)
            is SceneSource.CameraSource -> CameraProperties(source, onSourceUpdate)
            is SceneSource.ScreenCaptureSource -> ScreenCaptureProperties(source, onSourceUpdate)
            is SceneSource.BibleSource -> BibleProperties(source, onSourceUpdate, appSettings)
        }
    }
}
