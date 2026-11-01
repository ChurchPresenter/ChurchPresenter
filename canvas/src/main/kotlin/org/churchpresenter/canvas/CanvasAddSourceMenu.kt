package org.churchpresenter.canvas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import org.churchpresenter.icons.generated.resources.ic_add
import org.churchpresenter.icons.generated.resources.ic_layer_bible
import org.churchpresenter.icons.generated.resources.ic_layer_browser
import org.churchpresenter.icons.generated.resources.ic_layer_camera
import org.churchpresenter.icons.generated.resources.ic_layer_clock
import org.churchpresenter.icons.generated.resources.ic_layer_color
import org.churchpresenter.icons.generated.resources.ic_layer_image
import org.churchpresenter.icons.generated.resources.ic_layer_ndi
import org.churchpresenter.icons.generated.resources.ic_layer_omt
import org.churchpresenter.icons.generated.resources.ic_layer_qr
import org.churchpresenter.icons.generated.resources.ic_layer_screen
import org.churchpresenter.icons.generated.resources.ic_layer_text
import org.churchpresenter.icons.generated.resources.ic_layer_video
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.canvas_add_source
import org.churchpresenter.strings.generated.resources.canvas_layer_group_content
import org.churchpresenter.strings.generated.resources.canvas_layer_group_live
import org.churchpresenter.strings.generated.resources.canvas_layer_group_sources
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.KeyIconButton
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.util.UUID
import org.churchpresenter.icons.generated.resources.Res as IconRes

/* The Canvas tab's Add source button and its menu of layer kinds, in three groups. */

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CanvasTabScope.AddSourceButton(sceneViewModel: SceneViewModel) {
    var showAddMenu by remember { mutableStateOf(false) }

    Box {
        val scheme = MaterialTheme.colorScheme
        TooltipArea(
            tooltip = {
                Surface(
                    color = scheme.inverseSurface,
                    shape = MaterialTheme.shapes.extraSmall,
                    tonalElevation = 4.dp,
                ) {
                    Text(
                        stringResource(Res.string.canvas_add_source),
                        color = scheme.inverseOnSurface,
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
                    tint = scheme.onSurface
                )
            }
        }

        DropdownMenu(
            expanded = showAddMenu,
            onDismissRequest = { showAddMenu = false },
            shape = AppShape(10.dp),
            containerColor = scheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, scheme.outlineVariant),
        ) {
            Column(Modifier.width(236.dp).padding(horizontal = 4.dp)) {
                val groups = sourceNames.newSourceGroups()
                groups.forEachIndexed { index, group ->
                    LayerGroupTitle(stringResource(group.title))
                    group.items.forEach { item ->
                        LayerMenuItem(item.label, painterResource(item.icon)) {
                            showAddMenu = false
                            sceneViewModel.addSource(item.make(UUID.randomUUID().toString()))
                        }
                    }
                    if (index < groups.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = scheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LayerGroupTitle(title: String) {
    Text(
        title.uppercase(),
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.1.em,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LayerMenuItem(label: String, icon: Painter, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(AppShape(6.dp))
            .background(if (hovered) scheme.onSurface.copy(alpha = HOVER_ALPHA) else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(22.dp).clip(AppShape(5.dp)).background(scheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = scheme.primary)
        }
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One kind of layer the menu adds: its label -- also the new layer's name -- its icon, and the layer. */
internal class NewSourceItem(val label: String, val icon: DrawableResource, val make: (id: String) -> SceneSource)

/** A titled group of the menu's layer kinds. */
internal class NewSourceGroup(val title: StringResource, val items: List<NewSourceItem>)

/** The Add layer menu's groups, in order: what you place, what shows live information, and live feeds. */
internal fun CanvasSourceNames.newSourceGroups(): List<NewSourceGroup> = listOf(
    NewSourceGroup(
        Res.string.canvas_layer_group_content,
        listOf(
            NewSourceItem(strImage, IconRes.drawable.ic_layer_image) { id ->
                SceneSource.ImageSource(
                    id = id, name = strImage, filePath = "", transform = SourceTransform(width = HALF, height = HALF),
                )
            },
            NewSourceItem(strText, IconRes.drawable.ic_layer_text) { id ->
                SceneSource.TextSource(
                    id = id, name = strText,
                    transform = SourceTransform(x = QUARTER, y = TEXT_TOP, width = HALF, height = TEXT_HEIGHT),
                )
            },
            NewSourceItem(strColor, IconRes.drawable.ic_layer_color) { id ->
                SceneSource.ColorSource(id = id, name = strColor, transform = SourceTransform())
            },
            NewSourceItem(strVideo, IconRes.drawable.ic_layer_video) { id ->
                SceneSource.VideoSource(id = id, name = strVideo, filePath = "", transform = SourceTransform())
            },
        ),
    ),
    NewSourceGroup(
        Res.string.canvas_layer_group_live,
        listOf(
            NewSourceItem(strTimer, IconRes.drawable.ic_layer_clock) { id ->
                SceneSource.ClockSource(
                    id = id, name = strTimer, transform = SourceTransform(width = TIMER_WIDTH, height = TIMER_HEIGHT),
                )
            },
            NewSourceItem(strQrCode, IconRes.drawable.ic_layer_qr) { id ->
                SceneSource.QRCodeSource(
                    id = id, name = strQrCode, transform = SourceTransform(width = QR_SIDE, height = QR_SIDE),
                )
            },
            NewSourceItem(strBible, IconRes.drawable.ic_layer_bible) { id ->
                SceneSource.BibleSource(
                    id = id, name = strBible,
                    transform = SourceTransform(x = INSET, y = BIBLE_TOP, width = WIDE, height = BIBLE_HEIGHT),
                )
            },
            NewSourceItem(strBrowser, IconRes.drawable.ic_layer_browser) { id ->
                SceneSource.BrowserSource(
                    id = id, name = strBrowser, url = "http://www.",
                    transform = SourceTransform(x = INSET, y = INSET, width = WIDE, height = WIDE),
                )
            },
        ),
    ),
    NewSourceGroup(
        Res.string.canvas_layer_group_sources,
        listOf(
            NewSourceItem(strCamera, IconRes.drawable.ic_layer_camera) { id ->
                SceneSource.CameraSource(id = id, name = strCamera, transform = SourceTransform())
            },
            NewSourceItem(strScreenCapture, IconRes.drawable.ic_layer_screen) { id ->
                SceneSource.ScreenCaptureSource(id = id, name = strScreenCapture, transform = SourceTransform())
            },
            NewSourceItem(strNdi, IconRes.drawable.ic_layer_ndi) { id ->
                SceneSource.NdiSource(id = id, name = strNdi, transform = SourceTransform())
            },
            NewSourceItem(strOmt, IconRes.drawable.ic_layer_omt) { id ->
                SceneSource.OmtSource(id = id, name = strOmt, transform = SourceTransform())
            },
        ),
    ),
)

/** Every entry of the menu, in order, as its label and the layer it adds under a given id. */
internal fun CanvasSourceNames.newSourceItems(): List<Pair<String, (id: String) -> SceneSource>> =
    newSourceGroups().flatMap { group -> group.items.map { it.label to it.make } }

private const val HOVER_ALPHA = 0.07f
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
