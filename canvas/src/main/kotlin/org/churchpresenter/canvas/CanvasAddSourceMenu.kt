package org.churchpresenter.canvas

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
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_add
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.util.UUID
import org.churchpresenter.strings.generated.resources.canvas_add_source

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
                    painterResource(IconRes.drawable.ic_add),
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
            sourceNames.newSourceItems().forEach { (label, make) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        showAddMenu = false
                        sceneViewModel.addSource(make(UUID.randomUUID().toString()))
                    },
                )
            }
        }
    }
}

/**
 * The Add Source menu, in order: each entry's label -- which is also the new source's name -- and
 * the source it adds under a given id, sized and placed where that kind of layer usually goes.
 */
internal fun CanvasSourceNames.newSourceItems(): List<Pair<String, (id: String) -> SceneSource>> = listOf(
    strImage to { id ->
        SceneSource.ImageSource(
            id = id, name = strImage, filePath = "", transform = SourceTransform(width = HALF, height = HALF),
        )
    },
    strText to { id ->
        SceneSource.TextSource(
            id = id, name = strText,
            transform = SourceTransform(x = QUARTER, y = TEXT_TOP, width = HALF, height = TEXT_HEIGHT),
        )
    },
    strColor to { id -> SceneSource.ColorSource(id = id, name = strColor, transform = SourceTransform()) },
    strVideo to { id ->
        SceneSource.VideoSource(id = id, name = strVideo, filePath = "", transform = SourceTransform())
    },
    strTimer to { id ->
        SceneSource.ClockSource(
            id = id, name = strTimer, transform = SourceTransform(width = TIMER_WIDTH, height = TIMER_HEIGHT),
        )
    },
    strQrCode to { id ->
        SceneSource.QRCodeSource(
            id = id, name = strQrCode, transform = SourceTransform(width = QR_SIDE, height = QR_SIDE),
        )
    },
    strCamera to { id -> SceneSource.CameraSource(id = id, name = strCamera, transform = SourceTransform()) },
    strScreenCapture to { id ->
        SceneSource.ScreenCaptureSource(id = id, name = strScreenCapture, transform = SourceTransform())
    },
    strNdi to { id -> SceneSource.NdiSource(id = id, name = strNdi, transform = SourceTransform()) },
    strOmt to { id -> SceneSource.OmtSource(id = id, name = strOmt, transform = SourceTransform()) },
    strBrowser to { id ->
        SceneSource.BrowserSource(
            id = id, name = strBrowser, url = "http://www.",
            transform = SourceTransform(x = INSET, y = INSET, width = WIDE, height = WIDE),
        )
    },
    strBible to { id ->
        SceneSource.BibleSource(
            id = id, name = strBible,
            transform = SourceTransform(x = INSET, y = BIBLE_TOP, width = WIDE, height = BIBLE_HEIGHT),
        )
    },
)

private const val HALF = 0.5f
private const val QUARTER = 0.25f
private const val TEXT_TOP = 0.4f
private const val TEXT_HEIGHT = 0.2f
private const val TIMER_WIDTH = 0.4f
private const val TIMER_HEIGHT = 0.15f
private const val QR_SIDE = 0.2f
private const val INSET = 0.1f
private const val WIDE = 0.8f
private const val BIBLE_TOP = 0.2f
private const val BIBLE_HEIGHT = 0.6f
