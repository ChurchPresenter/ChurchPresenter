package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_add
import churchpresenter.composeapp.generated.resources.ic_arrow_down
import churchpresenter.composeapp.generated.resources.ic_arrow_up
import churchpresenter.composeapp.generated.resources.ic_cast
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.ic_delete
import churchpresenter.composeapp.generated.resources.ic_edit
import churchpresenter.composeapp.generated.resources.ic_playlist_add
import churchpresenter.composeapp.generated.resources.add_to_schedule
import churchpresenter.composeapp.generated.resources.canvas_create_scene
import churchpresenter.composeapp.generated.resources.canvas_new_scene
import churchpresenter.composeapp.generated.resources.canvas_no_scene_selected
import churchpresenter.composeapp.generated.resources.canvas_scenes
import churchpresenter.composeapp.generated.resources.canvas_select_source
import churchpresenter.composeapp.generated.resources.canvas_source_browser
import churchpresenter.composeapp.generated.resources.canvas_source_color
import churchpresenter.composeapp.generated.resources.canvas_source_image
import churchpresenter.composeapp.generated.resources.canvas_source_text
import churchpresenter.composeapp.generated.resources.canvas_source_video
import churchpresenter.composeapp.generated.resources.canvas_sources
import churchpresenter.composeapp.generated.resources.go_live
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.SceneCanvas
import org.churchpresenter.app.churchpresenter.composables.SourcePropertiesPanel
import org.churchpresenter.app.churchpresenter.data.AppSettings
import androidx.compose.ui.unit.sp
import org.churchpresenter.app.churchpresenter.utils.presenterScreenBounds
import org.churchpresenter.app.churchpresenter.utils.formatAspectRatio
import org.churchpresenter.app.churchpresenter.models.SceneSource
import org.churchpresenter.app.churchpresenter.models.SourceTransform
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.SceneViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.util.UUID
import churchpresenter.composeapp.generated.resources.canvas_source_shape
import churchpresenter.composeapp.generated.resources.canvas_source_clock
import churchpresenter.composeapp.generated.resources.canvas_source_qrcode
import churchpresenter.composeapp.generated.resources.canvas_source_camera
import churchpresenter.composeapp.generated.resources.canvas_source_screen_capture
import churchpresenter.composeapp.generated.resources.canvas_tool_select
import churchpresenter.composeapp.generated.resources.canvas_tool_rectangle
import churchpresenter.composeapp.generated.resources.canvas_tool_ellipse
import churchpresenter.composeapp.generated.resources.canvas_tool_line
import churchpresenter.composeapp.generated.resources.canvas_tool_arrow
import churchpresenter.composeapp.generated.resources.canvas_tool_freehand
import churchpresenter.composeapp.generated.resources.canvas_rename_scene
import churchpresenter.composeapp.generated.resources.canvas_remove_scene
import churchpresenter.composeapp.generated.resources.canvas_add_source
import churchpresenter.composeapp.generated.resources.canvas_delete_source
import churchpresenter.composeapp.generated.resources.canvas_source_move_forward
import churchpresenter.composeapp.generated.resources.canvas_source_move_backward
import churchpresenter.composeapp.generated.resources.canvas_toggle_visibility
import churchpresenter.composeapp.generated.resources.canvas_toggle_lock
import churchpresenter.composeapp.generated.resources.canvas_aspect_ratio_warning
import churchpresenter.composeapp.generated.resources.canvas_fix_aspect_ratio

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CanvasTab(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    presenterManager: PresenterManager,
    sceneViewModel: SceneViewModel,
    onAddToSchedule: (sceneId: String, sceneName: String) -> Unit
) {
    var renamingSceneId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    val currentScene = sceneViewModel.currentScene
    val selectedSourceId by sceneViewModel.selectedSourceId
    val selectedSource = sceneViewModel.selectedSource

    // Localised default source names (resolved in composable scope so they
    // can be captured by non-composable onClick lambdas below)
    val strImage         = stringResource(Res.string.canvas_source_image)
    val strText          = stringResource(Res.string.canvas_source_text)
    val strColor         = stringResource(Res.string.canvas_source_color)
    val strVideo         = stringResource(Res.string.canvas_source_video)
    val strClock         = stringResource(Res.string.canvas_source_clock)
    val strQrCode        = stringResource(Res.string.canvas_source_qrcode)
    val strCamera        = stringResource(Res.string.canvas_source_camera)
    val strScreenCapture = stringResource(Res.string.canvas_source_screen_capture)
    val strBrowser       = stringResource(Res.string.canvas_source_browser)

    // Drawing tool state
    var activeTool by remember { mutableStateOf("select") }
    var drawingStrokeColor by remember { mutableStateOf("#FFFFFF") }
    var drawingFillColor by remember { mutableStateOf("#00000000") }
    var drawingStrokeWidth by remember { mutableStateOf(3f) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    // Re-grab focus whenever a source is selected so Delete key works right after clicking canvas items
    LaunchedEffect(selectedSourceId) {
        if (selectedSourceId != null) {
            focusRequester.requestFocus()
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Delete || event.key == Key.Backspace) &&
                    renamingSceneId == null
                ) {
                    val sourceId = selectedSourceId
                    if (sourceId != null) {
                        sceneViewModel.removeSource(sourceId)
                        true
                    } else false
                } else false
            }
    ) {
        // Left panel: Scene selector + Source list
        Column(
            modifier = Modifier
                .widthIn(min = 160.dp, max = 220.dp)
                .weight(0.2f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp)
        ) {
            // Scene selector section
            Text(
                stringResource(Res.string.canvas_scenes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))

            // Resolve live presentation display for aspect ratio checks
            val presentationAssignment0 = appSettings.projectionSettings.getAssignment(0)
            val presentationBounds0 = remember(presentationAssignment0.targetDisplay, presentationAssignment0.targetBoundsX, presentationAssignment0.targetBoundsY) {
                val ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                val screens = ge.screenDevices
                val matched = if (presentationAssignment0.targetBoundsX != Int.MIN_VALUE) {
                    screens.firstOrNull { sd ->
                        val b = sd.defaultConfiguration.bounds
                        b.x == presentationAssignment0.targetBoundsX && b.y == presentationAssignment0.targetBoundsY
                    }
                } else null
                val device = matched
                    ?: screens.getOrNull(presentationAssignment0.targetDisplay)
                    ?: screens.firstOrNull { it != ge.defaultScreenDevice }
                    ?: ge.defaultScreenDevice
                device.defaultConfiguration.bounds
            }
            val displayAr0 = if (presentationBounds0.height > 0) presentationBounds0.width.toFloat() / presentationBounds0.height else 0f

            @OptIn(ExperimentalFoundationApi::class)
            LazyColumn(modifier = Modifier.weight(0.4f).fillMaxWidth()) {
                items(sceneViewModel.scenes) { scene ->
                    val isSelected = scene.id == sceneViewModel.currentSceneId.value
                    val isRenaming = renamingSceneId == scene.id
                    val sceneAr0 = if (scene.canvasHeight > 0) scene.canvasWidth.toFloat() / scene.canvasHeight else 0f
                    val isMismatched = displayAr0 > 0f && kotlin.math.abs(displayAr0 - sceneAr0) > 0.01f
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { sceneViewModel.selectScene(scene.id) },
                                onDoubleClick = {
                                    renamingSceneId = scene.id
                                    renameText = scene.name
                                }
                            )
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isRenaming) {
                            BasicTextField(
                                value = renameText,
                                onValueChange = { renameText = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                            IconButton(
                                onClick = {
                                    sceneViewModel.renameScene(scene.id, renameText)
                                    renamingSceneId = null
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Text("✓", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            Text(
                                scene.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (isMismatched) {
                                Text("⚠", fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                            }
                            TooltipArea(
                                tooltip = {
                                    Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                        Text(stringResource(Res.string.canvas_rename_scene), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                tooltipPlacement = TooltipPlacement.CursorPoint()
                            ) {
                                IconButton(
                                    onClick = {
                                        renamingSceneId = scene.id
                                        renameText = scene.name
                                    },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        painterResource(Res.drawable.ic_edit),
                                        contentDescription = stringResource(Res.string.canvas_rename_scene),
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            TooltipArea(
                                tooltip = {
                                    Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                        Text(stringResource(Res.string.canvas_remove_scene), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                tooltipPlacement = TooltipPlacement.CursorPoint()
                            ) {
                                IconButton(
                                    onClick = { sceneViewModel.removeScene(scene.id) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        painterResource(Res.drawable.ic_close),
                                        contentDescription = stringResource(Res.string.canvas_remove_scene),
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = { sceneViewModel.addScene() },
                    modifier = Modifier.weight(1f),
                    contentPadding = ButtonDefaults.ContentPadding
                ) {
                    Icon(painterResource(Res.drawable.ic_add), null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(Res.string.canvas_new_scene), style = MaterialTheme.typography.labelSmall)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Source list section
            Text(
                stringResource(Res.string.canvas_sources),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))

            if (currentScene != null) {
                LazyColumn(modifier = Modifier.weight(0.6f).fillMaxWidth()) {
                    // Render in reverse order so top item = front
                    items(currentScene.sources.reversed()) { source ->
                        val isSelected = source.id == selectedSourceId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { sceneViewModel.selectSource(source.id) }
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                    else Color.Transparent,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Visibility toggle
                            TooltipArea(
                                tooltip = {
                                    Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                        Text(stringResource(Res.string.canvas_toggle_visibility), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                tooltipPlacement = TooltipPlacement.CursorPoint()
                            ) {
                                IconButton(
                                    onClick = { sceneViewModel.toggleSourceVisibility(source.id) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Text(
                                        if (source.visible) "\uD83D\uDC41" else "\u2014",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Lock toggle
                            TooltipArea(
                                tooltip = {
                                    Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                        Text(stringResource(Res.string.canvas_toggle_lock), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                tooltipPlacement = TooltipPlacement.CursorPoint()
                            ) {
                                IconButton(
                                    onClick = { sceneViewModel.toggleSourceLock(source.id) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Text(
                                        if (source.locked) "\uD83D\uDD12" else "\uD83D\uDD13",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text(
                                source.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f).alpha(if (source.visible) 1f else 0.5f)
                            )
                        }
                    }
                }

                // Source toolbar
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    var showAddMenu by remember { mutableStateOf(false) }

                    Box {
                        TooltipArea(
                            tooltip = {
                                Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                    Text(stringResource(Res.string.canvas_add_source), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            tooltipPlacement = TooltipPlacement.CursorPoint()
                        ) {
                            IconButton(
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
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.canvas_source_image)) },
                                onClick = {
                                    showAddMenu = false
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
                                    showAddMenu = false
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
                                    showAddMenu = false
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
                                    showAddMenu = false
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
                                text = { Text(stringResource(Res.string.canvas_source_clock)) },
                                onClick = {
                                    showAddMenu = false
                                    sceneViewModel.addSource(
                                        SceneSource.ClockSource(
                                            id = UUID.randomUUID().toString(),
                                            name = strClock,
                                            transform = SourceTransform(width = 0.4f, height = 0.15f)
                                        )
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.canvas_source_qrcode)) },
                                onClick = {
                                    showAddMenu = false
                                    sceneViewModel.addSource(
                                        SceneSource.QRCodeSource(
                                            id = UUID.randomUUID().toString(),
                                            name = strQrCode,
                                            transform = SourceTransform(width = 0.2f, height = 0.2f)
                                        )
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.canvas_source_camera)) },
                                onClick = {
                                    showAddMenu = false
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
                                    showAddMenu = false
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
                                text = { Text(stringResource(Res.string.canvas_source_browser)) },
                                onClick = {
                                    showAddMenu = false
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
                        }
                    }

                    val currentSelectedId = selectedSourceId
                    if (currentSelectedId != null) {
                        TooltipArea(
                            tooltip = {
                                Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                    Text(stringResource(Res.string.canvas_delete_source), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            tooltipPlacement = TooltipPlacement.CursorPoint()
                        ) {
                            IconButton(
                                onClick = { sceneViewModel.removeSource(currentSelectedId) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    painterResource(Res.drawable.ic_delete),
                                    contentDescription = stringResource(Res.string.canvas_delete_source),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        TooltipArea(
                            tooltip = {
                                Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                    Text(stringResource(Res.string.canvas_source_move_forward), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            tooltipPlacement = TooltipPlacement.CursorPoint()
                        ) {
                            IconButton(
                                onClick = { sceneViewModel.moveSourceDown(currentSelectedId) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    painterResource(Res.drawable.ic_arrow_up),
                                    contentDescription = stringResource(Res.string.canvas_source_move_forward),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        TooltipArea(
                            tooltip = {
                                Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                    Text(stringResource(Res.string.canvas_source_move_backward), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            tooltipPlacement = TooltipPlacement.CursorPoint()
                        ) {
                            IconButton(
                                onClick = { sceneViewModel.moveSourceUp(currentSelectedId) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    painterResource(Res.drawable.ic_arrow_down),
                                    contentDescription = stringResource(Res.string.canvas_source_move_backward),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

        // Center panel: Toolbar + Canvas
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (currentScene != null) {
                // Top toolbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Drawing tools (left)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        data class ToolDef(val id: String, val label: String)
                        val tools = listOf(
                            ToolDef("select", stringResource(Res.string.canvas_tool_select)),
                            ToolDef("rectangle", stringResource(Res.string.canvas_tool_rectangle)),
                            ToolDef("ellipse", stringResource(Res.string.canvas_tool_ellipse)),
                            ToolDef("line", stringResource(Res.string.canvas_tool_line)),
                            ToolDef("arrow", stringResource(Res.string.canvas_tool_arrow)),
                            ToolDef("freehand", stringResource(Res.string.canvas_tool_freehand))
                        )

                        tools.forEach { tool ->
                            val isActive = activeTool == tool.id
                            TooltipArea(
                                tooltip = {
                                    Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                        Text(tool.label, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                tooltipPlacement = TooltipPlacement.CursorPoint()
                            ) {
                                IconButton(
                                    onClick = { activeTool = tool.id },
                                    modifier = Modifier.size(32.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent,
                                        contentColor = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Text(
                                        when (tool.id) {
                                            "select" -> "\u25C6"
                                            "rectangle" -> "\u25A1"
                                            "ellipse" -> "\u25CB"
                                            "line" -> "\u2215"
                                            "arrow" -> "\u2192"
                                            "freehand" -> "\u270E"
                                            else -> "?"
                                        },
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                            }
                        }

                        // Drawing color/stroke controls when a drawing tool is active
                        if (activeTool != "select") {
                            VerticalDivider(
                                modifier = Modifier.height(24.dp).padding(horizontal = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            ColorPickerField(
                                color = drawingStrokeColor,
                                onColorChange = { drawingStrokeColor = it }
                            )
                            Spacer(Modifier.width(4.dp))
                            ColorPickerField(
                                color = drawingFillColor,
                                onColorChange = { drawingFillColor = it }
                            )
                        }
                    }

                    // Action buttons (right)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Add to Schedule
                        TooltipArea(
                            tooltip = {
                                Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                    Text(stringResource(Res.string.add_to_schedule), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            tooltipPlacement = TooltipPlacement.CursorPoint()
                        ) {
                            IconButton(
                                onClick = { onAddToSchedule(currentScene.id, currentScene.name) },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                )
                            ) {
                                Icon(painter = painterResource(Res.drawable.ic_playlist_add), contentDescription = stringResource(Res.string.add_to_schedule), modifier = Modifier.size(20.dp))
                            }
                        }

                        // Go Live
                        TooltipArea(
                            tooltip = {
                                Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                    Text(stringResource(Res.string.go_live), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            tooltipPlacement = TooltipPlacement.CursorPoint()
                        ) {
                            IconButton(
                                onClick = {
                                    presenterManager.setActiveScene(currentScene)
                                    presenterManager.setPresentingMode(Presenting.CANVAS)
                                    presenterManager.setShowPresenterWindow(true)
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(painter = painterResource(Res.drawable.ic_cast), contentDescription = stringResource(Res.string.go_live), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                // Aspect ratio mismatch warning
                val presentationAssignment = appSettings.projectionSettings.getAssignment(0)
                val presentationBounds = remember(presentationAssignment.targetDisplay, presentationAssignment.targetBoundsX, presentationAssignment.targetBoundsY) {
                    val ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                    val screens = ge.screenDevices
                    val matched = if (presentationAssignment.targetBoundsX != Int.MIN_VALUE) {
                        screens.firstOrNull { sd ->
                            val b = sd.defaultConfiguration.bounds
                            b.x == presentationAssignment.targetBoundsX && b.y == presentationAssignment.targetBoundsY
                        }
                    } else null
                    val device = matched
                        ?: screens.getOrNull(presentationAssignment.targetDisplay)
                        ?: screens.firstOrNull { it != ge.defaultScreenDevice }
                        ?: ge.defaultScreenDevice
                    device.defaultConfiguration.bounds
                }
                val displayW = presentationBounds.width
                val displayH = presentationBounds.height
                val displayAr = if (displayH > 0) displayW.toFloat() / displayH else 0f
                val sceneAr = if (currentScene.canvasHeight > 0) currentScene.canvasWidth.toFloat() / currentScene.canvasHeight else 0f
                if (displayAr > 0f && kotlin.math.abs(displayAr - sceneAr) > 0.01f) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            stringResource(
                                Res.string.canvas_aspect_ratio_warning,
                                formatAspectRatio(currentScene.canvasWidth, currentScene.canvasHeight),
                                formatAspectRatio(displayW, displayH)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(
                            onClick = {
                                sceneViewModel.updateCanvasSize(displayW, displayH)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier.height(28.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text(stringResource(Res.string.canvas_fix_aspect_ratio), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Canvas preview
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SceneCanvas(
                        modifier = Modifier.fillMaxSize(),
                        scene = currentScene,
                        selectedSourceId = selectedSourceId,
                        onSourceSelected = { sceneViewModel.selectSource(it) },
                        onTransformChanged = { sourceId, transform ->
                            sceneViewModel.updateTransform(sourceId, transform)
                        },
                        isInteractive = true,
                        activeTool = activeTool,
                        drawingStrokeColor = drawingStrokeColor,
                        drawingFillColor = drawingFillColor,
                        drawingStrokeWidth = drawingStrokeWidth,
                        onShapeDrawn = { shape ->
                            sceneViewModel.addSource(shape)
                        }
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(Res.string.canvas_no_scene_selected),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { sceneViewModel.addScene() }) {
                            Text(stringResource(Res.string.canvas_create_scene))
                        }
                    }
                }
            }
        }

        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

        // Right panel: Properties
        Column(
            modifier = Modifier
                .widthIn(min = 160.dp, max = 220.dp)
                .weight(0.2f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (selectedSource != null) {
                SourcePropertiesPanel(
                    source = selectedSource,
                    modifier = Modifier.fillMaxSize(),
                    onSourceUpdate = { updatedSource ->
                        sceneViewModel.updateSource(updatedSource.id) { updatedSource }
                    }
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(Res.string.canvas_select_source),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
