package org.churchpresenter.canvas

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import org.churchpresenter.core.models.scene.SourceTransform
import org.churchpresenter.sharedui.composables.SegmentedButton
import org.churchpresenter.sharedui.composables.SegmentedButtonItem
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.strings.generated.resources.canvas_layout_landscape_short
import org.churchpresenter.strings.generated.resources.canvas_layout_portrait_short
import org.churchpresenter.strings.generated.resources.canvas_layout_reset
import org.churchpresenter.strings.generated.resources.canvas_fill_canvas
import org.churchpresenter.strings.generated.resources.canvas_crop_position
import org.churchpresenter.strings.generated.resources.canvas_crop_horizontal
import org.churchpresenter.strings.generated.resources.canvas_crop_vertical
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxWithConstraints
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
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.canvas_opacity
import org.churchpresenter.strings.generated.resources.canvas_source_name
import org.churchpresenter.strings.generated.resources.canvas_rotation
import org.churchpresenter.strings.generated.resources.canvas_properties
import org.churchpresenter.strings.generated.resources.canvas_transform
import org.churchpresenter.strings.generated.resources.canvas_transform_x
import org.churchpresenter.strings.generated.resources.canvas_transform_y
import org.churchpresenter.strings.generated.resources.canvas_transform_w
import org.churchpresenter.strings.generated.resources.canvas_transform_h
import org.churchpresenter.sharedui.filechooser.FileChooser
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.sharedui.composables.PropertyFloatField
import org.churchpresenter.sharedui.composables.PropertySlider
import org.churchpresenter.sharedui.composables.PropertySliderWithInput
import org.churchpresenter.sharedui.composables.PropertyTextField
import org.churchpresenter.sharedui.composables.updateName
import org.churchpresenter.sharedui.composables.updateTransform

private const val MIN_SOURCE_SIZE = 0.01f
private const val MAX_ROTATION_DEGREES = 180f

@Composable
fun SourcePropertiesPanel(
    source: SceneSource,
    modifier: Modifier = Modifier,
    appSettings: AppSettings? = null,
    fileChooser: FileChooser = FileChooser.platformInstance,
    /** The machine the camera panel describes, or null to ask this one — see [CameraProperties]. */
    cameraHost: CameraHost? = null,
    /** The scene's two layouts, when it has two: the transform shown is the one being edited. */
    layouts: SourceLayouts? = null,
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

        TransformSection(source, layouts, onSourceUpdate)

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
            is SceneSource.CameraSource -> CameraProperties(source, onSourceUpdate, cameraHost)
            is SceneSource.ScreenCaptureSource -> ScreenCaptureProperties(source, onSourceUpdate)
            is SceneSource.NdiSource -> NdiProperties(source, onSourceUpdate)
            is SceneSource.OmtSource -> OmtProperties(source, onSourceUpdate)
            is SceneSource.BibleSource -> BibleProperties(source, onSourceUpdate, appSettings)
        }
    }
}

/**
 * A layer's place in a scene with two layouts: which layout the panel is editing, where the layer
 * sits in the second one, and the calls that change them.
 */
class SourceLayouts(
    /** Whether the scene's main layout is the landscape one; the second is the other. */
    val mainIsLandscape: Boolean,
    /** Whether the panel edits the second layout. */
    val editingAlternate: Boolean,
    /** Where the layer sits in the second layout, moved there or not. */
    val alternateTransform: SourceTransform,
    /** Whether the layer has been moved in the second layout, so Reset has something to undo. */
    val movedInAlternate: Boolean,
    val actions: LayoutActions,
)

/** What the layout switch and the second layout's edits call. */
class LayoutActions(
    /** Edits the second layout when true, the main one when false. */
    val onEditLayout: (alternate: Boolean) -> Unit,
    /** Moves or resizes the layer in the second layout. */
    val onAlternateTransform: (SourceTransform) -> Unit,
    /** Puts the layer back where it starts in the second layout. */
    val onResetAlternate: () -> Unit,
)

/**
 * Position, size, rotation and -- for a picture set to Fill -- where it is cropped, for the layout
 * being edited; opacity, which both layouts share. With two layouts, Landscape and Portrait choose
 * the layout, and the second one can be put back where it starts.
 */
@Composable
private fun TransformSection(
    source: SceneSource,
    layouts: SourceLayouts?,
    onSourceUpdate: (SceneSource) -> Unit,
) {
    Text(
        stringResource(Res.string.canvas_transform),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    // The second layout, when that is the one being edited.
    val second = layouts?.takeIf { it.editingAlternate }
    val alternate = second != null
    val t = second?.alternateTransform ?: source.transform
    val set: (SourceTransform) -> Unit = { next ->
        if (second != null) second.actions.onAlternateTransform(next) else onSourceUpdate(updateTransform(source, next))
    }
    if (layouts != null) LayoutSwitch(layouts)

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PropertyFloatField(stringResource(Res.string.canvas_transform_x), t.x, Modifier.weight(1f)) { v ->
            set(t.copy(x = v))
        }
        PropertyFloatField(stringResource(Res.string.canvas_transform_y), t.y, Modifier.weight(1f)) { v ->
            set(t.copy(y = v))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PropertyFloatField(stringResource(Res.string.canvas_transform_w), t.width, Modifier.weight(1f)) { v ->
            set(t.copy(width = v.coerceAtLeast(MIN_SOURCE_SIZE)))
        }
        PropertyFloatField(stringResource(Res.string.canvas_transform_h), t.height, Modifier.weight(1f)) { v ->
            set(t.copy(height = v.coerceAtLeast(MIN_SOURCE_SIZE)))
        }
    }
    // With two layouts each canvas is set up on its own, so the box can be fitted to the one being
    // edited -- or, in the second, put back where it starts.
    if (layouts != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        GhostButton(
            onClick = { set(t.copy(x = 0f, y = 0f, width = 1f, height = 1f)) },
            modifier = Modifier.testTag(FILL_CANVAS_TAG),
        ) { Text(stringResource(Res.string.canvas_fill_canvas), style = MaterialTheme.typography.labelSmall) }
        if (alternate && layouts.movedInAlternate) {
            GhostButton(
                onClick = layouts.actions.onResetAlternate,
                modifier = Modifier.testTag(RESET_LAYOUT_TAG),
            ) { Text(stringResource(Res.string.canvas_layout_reset), style = MaterialTheme.typography.labelSmall) }
        }
    }

    PropertySliderWithInput(
        stringResource(Res.string.canvas_rotation),
        t.rotation,
        -MAX_ROTATION_DEGREES,
        MAX_ROTATION_DEGREES,
        "°"
    ) { v ->
        set(t.copy(rotation = v))
    }
    if (source is SceneSource.ImageSource && source.contentScale == "FILL") CropPosition(t, set)
    // Opacity is the layer's own, one setting for both layouts.
    PropertySlider(stringResource(Res.string.canvas_opacity), source.transform.opacity, 0f, 1f) { v ->
        onSourceUpdate(updateTransform(source, source.transform.copy(opacity = v)))
    }
}

/**
 * Landscape and Portrait: which of the scene's two layouts the panel edits, in the order the canvases
 * stand. The main layout is the orientation the scene was made in, so either can be the second one.
 */
@Composable
private fun LayoutSwitch(layouts: SourceLayouts) = BoxWithConstraints(Modifier.fillMaxWidth()) {
    // Half the panel each, so neither name is cut short however narrow the panel is.
    SegmentedButton(
        items = listOf(
            SegmentedButtonItem(
                value = !layouts.mainIsLandscape,
                label = stringResource(Res.string.canvas_layout_landscape_short),
                testTag = LAYOUT_LANDSCAPE_TAG,
            ),
            SegmentedButtonItem(
                value = layouts.mainIsLandscape,
                label = stringResource(Res.string.canvas_layout_portrait_short),
                testTag = LAYOUT_PORTRAIT_TAG,
            ),
        ),
        selectedValue = layouts.editingAlternate,
        onValueChange = layouts.actions.onEditLayout,
        buttonWidth = maxWidth / 2,
        buttonHeight = 28.dp,
        fontSize = 12.sp,
    )
}

/** Where a picture set to Fill is cropped, across and down, in the layout being edited. */
@Composable
private fun CropPosition(t: SourceTransform, set: (SourceTransform) -> Unit) {
    Text(
        stringResource(Res.string.canvas_crop_position),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    PropertySlider(stringResource(Res.string.canvas_crop_horizontal), t.cropX, 0f, 1f) { v -> set(t.copy(cropX = v)) }
    PropertySlider(stringResource(Res.string.canvas_crop_vertical), t.cropY, 0f, 1f) { v -> set(t.copy(cropY = v)) }
}

/** Test handles for the layout switch and its buttons. */
internal const val LAYOUT_LANDSCAPE_TAG = "canvas_layout_landscape"
internal const val LAYOUT_PORTRAIT_TAG = "canvas_layout_portrait"
internal const val FILL_CANVAS_TAG = "canvas_fill_canvas"
internal const val RESET_LAYOUT_TAG = "canvas_layout_reset"
