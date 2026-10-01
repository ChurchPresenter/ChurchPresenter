package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.app.churchpresenter.viewmodel.SceneViewModel
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import org.churchpresenter.theme.components.KeyIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_add
import churchpresenter.composeapp.generated.resources.canvas_source_browser
import churchpresenter.composeapp.generated.resources.canvas_source_color
import churchpresenter.composeapp.generated.resources.canvas_source_image
import churchpresenter.composeapp.generated.resources.canvas_source_text
import churchpresenter.composeapp.generated.resources.canvas_source_video
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.util.UUID
import churchpresenter.composeapp.generated.resources.canvas_source_timer
import churchpresenter.composeapp.generated.resources.canvas_source_qrcode
import churchpresenter.composeapp.generated.resources.canvas_source_camera
import churchpresenter.composeapp.generated.resources.canvas_source_screen_capture
import churchpresenter.composeapp.generated.resources.canvas_source_ndi
import churchpresenter.composeapp.generated.resources.canvas_source_omt
import churchpresenter.composeapp.generated.resources.canvas_source_bible
import churchpresenter.composeapp.generated.resources.canvas_add_source

/* The Canvas tab's Add source button and its menu of source types. */

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CanvasTabScope.AddSourceButton(sceneViewModel: SceneViewModel) {
    var showAddMenu by remember { mutableStateOf(false) }

    Box {
        TooltipArea(
            tooltip = {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = MaterialTheme.shapes.extraSmall,
                    tonalElevation = 4.dp,
                ) {
                    Text(
                        stringResource(Res.string.canvas_add_source),
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            tooltipPlacement = TooltipPlacement.ComponentRect(
                anchor = Alignment.BottomCenter,
                offset = DpOffset(0.dp, 4.dp),
            )
        ) {
            KeyIconButton(
                onClick = { showAddMenu = true; activeTool = "select" },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    painterResource(Res.drawable.ic_add),
                    contentDescription = stringResource(Res.string.canvas_add_source),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        DropdownMenu(
            expanded = showAddMenu,
            onDismissRequest = { showAddMenu = false }
        ) {
            AddSourceItemsFirst(sceneViewModel) { showAddMenu = false }
            AddSourceItemsSecond(sceneViewModel) { showAddMenu = false }
        }
    }
}

@Composable
private fun CanvasTabScope.AddSourceItemsFirst(sceneViewModel: SceneViewModel, onClose: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.canvas_source_image)) },
        onClick = {
            onClose()
            sceneViewModel.addSource(
                SceneSource.ImageSource(
                    id = UUID.randomUUID().toString(),
                    name = strImage,
                    filePath = "",
                    transform = SourceTransform(width = 0.5f, height = 0.5f)
                )
            )
        }
    )
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.canvas_source_text)) },
        onClick = {
            onClose()
            sceneViewModel.addSource(
                SceneSource.TextSource(
                    id = UUID.randomUUID().toString(),
                    name = strText,
                    transform = SourceTransform(
                        x = 0.25f, y = 0.4f,
                        width = 0.5f, height = 0.2f
                    )
                )
            )
        }
    )
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.canvas_source_color)) },
        onClick = {
            onClose()
            sceneViewModel.addSource(
                SceneSource.ColorSource(
                    id = UUID.randomUUID().toString(),
                    name = strColor,
                    transform = SourceTransform()
                )
            )
        }
    )
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.canvas_source_video)) },
        onClick = {
            onClose()
            sceneViewModel.addSource(
                SceneSource.VideoSource(
                    id = UUID.randomUUID().toString(),
                    name = strVideo,
                    filePath = "",
                    transform = SourceTransform()
                )
            )
        }
    )
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.canvas_source_timer)) },
        onClick = {
            onClose()
            sceneViewModel.addSource(
                SceneSource.ClockSource(
                    id = UUID.randomUUID().toString(),
                    name = strTimer,
                    transform = SourceTransform(width = 0.4f, height = 0.15f)
                )
            )
        }
    )
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.canvas_source_qrcode)) },
        onClick = {
            onClose()
            sceneViewModel.addSource(
                SceneSource.QRCodeSource(
                    id = UUID.randomUUID().toString(),
                    name = strQrCode,
                    transform = SourceTransform(width = 0.2f, height = 0.2f)
                )
            )
        }
    )
}

@Composable
private fun CanvasTabScope.AddSourceItemsSecond(sceneViewModel: SceneViewModel, onClose: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.canvas_source_camera)) },
        onClick = {
            onClose()
            sceneViewModel.addSource(
                SceneSource.CameraSource(
                    id = UUID.randomUUID().toString(),
                    name = strCamera,
                    transform = SourceTransform()
                )
            )
        }
    )
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.canvas_source_screen_capture)) },
        onClick = {
            onClose()
            sceneViewModel.addSource(
                SceneSource.ScreenCaptureSource(
                    id = UUID.randomUUID().toString(),
                    name = strScreenCapture,
                    transform = SourceTransform()
                )
            )
        }
    )
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.canvas_source_ndi)) },
        onClick = {
            onClose()
            sceneViewModel.addSource(
                SceneSource.NdiSource(
                    id = UUID.randomUUID().toString(),
                    name = strNdi,
                    transform = SourceTransform()
                )
            )
        }
    )
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.canvas_source_omt)) },
        onClick = {
            onClose()
            sceneViewModel.addSource(
                SceneSource.OmtSource(
                    id = UUID.randomUUID().toString(),
                    name = strOmt,
                    transform = SourceTransform()
                )
            )
        }
    )
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.canvas_source_browser)) },
        onClick = {
            onClose()
            sceneViewModel.addSource(
                SceneSource.BrowserSource(
                    id = UUID.randomUUID().toString(),
                    name = strBrowser,
                    url = "http://www.",
                    transform = SourceTransform(
                        x = 0.1f, y = 0.1f,
                        width = 0.8f, height = 0.8f
                    )
                )
            )
        }
    )
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.canvas_source_bible)) },
        onClick = {
            onClose()
            sceneViewModel.addSource(
                SceneSource.BibleSource(
                    id = UUID.randomUUID().toString(),
                    name = strBible,
                    transform = SourceTransform(
                        x = 0.1f, y = 0.2f,
                        width = 0.8f, height = 0.6f
                    )
                )
            )
        }
    )
}
