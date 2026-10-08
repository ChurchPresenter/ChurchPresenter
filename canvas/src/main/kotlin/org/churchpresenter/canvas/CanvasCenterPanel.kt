package org.churchpresenter.canvas

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background

import org.churchpresenter.sharedui.composables.AddToScheduleButton
import org.churchpresenter.sharedui.composables.SavePresetButton
import org.churchpresenter.sharedui.composables.GoLiveButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.ButtonDefaults
import org.churchpresenter.theme.components.KeyIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.add_to_schedule
import org.churchpresenter.strings.generated.resources.save_preset
import org.churchpresenter.strings.generated.resources.canvas_create_scene
import org.churchpresenter.strings.generated.resources.canvas_no_scene_selected
import org.churchpresenter.strings.generated.resources.canvas_select_source
import org.churchpresenter.strings.generated.resources.go_live
import org.churchpresenter.sharedui.composables.ColorPickerField

import org.churchpresenter.sharedui.utils.assignedDisplayBounds
import org.churchpresenter.sharedui.utils.formatAspectRatio
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.forArea
import org.churchpresenter.core.models.scene.isLandscape
import org.jetbrains.compose.resources.stringResource
import java.util.UUID
import org.churchpresenter.strings.generated.resources.canvas_tool_select
import org.churchpresenter.strings.generated.resources.canvas_tool_rectangle
import org.churchpresenter.strings.generated.resources.canvas_tool_ellipse
import org.churchpresenter.strings.generated.resources.canvas_tool_line
import org.churchpresenter.strings.generated.resources.canvas_tool_arrow
import org.churchpresenter.strings.generated.resources.canvas_tool_freehand
import org.churchpresenter.strings.generated.resources.canvas_bring_into_view
import org.churchpresenter.strings.generated.resources.canvas_layout_landscape
import org.churchpresenter.strings.generated.resources.canvas_layout_portrait
import org.churchpresenter.strings.generated.resources.canvas_layers_outside
import org.churchpresenter.strings.generated.resources.canvas_layers_outside_one
import org.churchpresenter.strings.generated.resources.canvas_aspect_ratio_warning
import org.churchpresenter.strings.generated.resources.canvas_fix_aspect_ratio
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.sharedui.composables.bibleListCard

/* The Canvas tab's centre: the toolbar, the warnings, and the scene's canvas (or both layouts). */

@Composable
internal fun CanvasTabScope.CanvasCenterPanel(sceneViewModel: SceneViewModel, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = 4.dp)
            .bibleListCard()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val currentScene = sceneViewModel.currentScene
        if (currentScene != null) {
            CanvasToolbar(currentScene)
            CanvasAspectWarning(sceneViewModel, currentScene)
            // Layers the canvas is clipping away, with one click to put them back inside.
            val canvasLayouts = remember(currentScene) { currentScene.editorLayouts() }
            val outsideLayers = currentScene.sources.filter { canvasLayouts.misplacements(it.id).isNotEmpty() }
            CanvasOutsideLayers(sceneViewModel, outsideLayers, canvasLayouts)
            CanvasEditorArea(sceneViewModel, canvasLayouts, Modifier.weight(1f).fillMaxWidth().padding(8.dp))
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
                    RaisedButton(onClick = { sceneViewModel.addScene() }, shape = AppShape(8.dp)) {
                        Text(stringResource(Res.string.canvas_create_scene))
                    }
                }
            }
        }
    }
}

@Composable
private fun CanvasTabScope.CanvasToolbar(currentScene: Scene) {
    // Top toolbar
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CanvasDrawingTools()
        CanvasSceneActions(currentScene)
    }
}

/** The drawing tools, and the stroke and fill colours while one other than Select is picked. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CanvasTabScope.CanvasDrawingTools() {
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
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface,
                        shape = MaterialTheme.shapes.extraSmall,
                        tonalElevation = 4.dp,
                    ) {
                        Text(
                            tool.label,
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
                    onClick = { activeTool = tool.id },
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent,
                        contentColor = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    // Geometric glyphs, not `painterResource` icons, and kept that
                    // way deliberately: AGENT.md's "no text as icons" rule is about
                    // letters and emoji standing in for artwork, and these are the
                    // shapes the tools draw. Their names live in the TooltipArea
                    // above — see CanvasTabToolTooltipTest, which addresses the
                    // buttons by these glyphs.
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
                        // Trimmed to the glyph, so the shape itself is centred
                        // in the key rather than the font's line box around it.
                        style = MaterialTheme.typography.titleSmall.let {
                            it.copy(lineHeight = it.fontSize, lineHeightStyle = CenteredGlyphLine)
                        }
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
}

@Composable
private fun CanvasTabScope.CanvasSceneActions(currentScene: Scene) {
    // Action buttons (right)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onSavePreset != null) {
            SavePresetButton(
                onClick = { onSavePreset(currentScene.id, currentScene.name) },
                tooltipText = stringResource(Res.string.save_preset)
            )
        }
        // Add to Schedule
        AddToScheduleButton(
            onClick = { onAddToSchedule(currentScene.id, currentScene.name) },
            tooltipText = stringResource(Res.string.add_to_schedule)
        )

        // Go Live
        GoLiveButton(
            onClick = { goLive(currentScene) },
            tooltipText = stringResource(Res.string.go_live),
            showsShortcut = true,
        )
    }
}

/** A warning when the scene's shape is not the output's, with a button to match them. */
@Composable
private fun CanvasTabScope.CanvasAspectWarning(sceneViewModel: SceneViewModel, currentScene: Scene) {
    // Aspect ratio mismatch warning
    val presentationAssignment = appSettings.projectionSettings.getAssignment(0)
    val presentationBounds = remember(
        presentationAssignment.targetDisplay,
        presentationAssignment.targetBoundsX,
        presentationAssignment.targetBoundsY,
    ) {
        assignedDisplayBounds(presentationAssignment)
    }
    val displayW = presentationBounds.width
    val displayH = presentationBounds.height
    val displayAr = if (displayH > 0) displayW.toFloat() / displayH else 0f
    // A scene with two layouts is compared by the one this output would draw.
    val usedLayout = currentScene.forArea(displayW.toFloat(), displayH.toFloat())
    val usesAlternate = usedLayout !== currentScene
    val sceneAr =
        if (usedLayout.canvasHeight > 0) usedLayout.canvasWidth.toFloat() / usedLayout.canvasHeight else 0f
    if (displayAr > 0f && kotlin.math.abs(displayAr - sceneAr) > CANVAS_ASPECT_EPSILON) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer, AppShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(
                    Res.string.canvas_aspect_ratio_warning,
                    formatAspectRatio(usedLayout.canvasWidth, usedLayout.canvasHeight),
                    formatAspectRatio(displayW, displayH)
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            RaisedButton(
                onClick = {
                    // The second layout is the main canvas turned sideways, so matching
                    // it means setting the main canvas to the output turned sideways.
                    if (usesAlternate) {
                        sceneViewModel.updateCanvasSize(displayH, displayW)
                    } else {
                        sceneViewModel.updateCanvasSize(displayW, displayH)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.height(28.dp),
                shape = AppShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Text(stringResource(Res.string.canvas_fix_aspect_ratio), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Layers the canvas is clipping away, with one click to put them back inside. */
@Composable
private fun CanvasTabScope.CanvasOutsideLayers(
    sceneViewModel: SceneViewModel,
    outsideLayers: List<SceneSource>,
    canvasLayouts: List<EditorLayout>,
) {
    if (outsideLayers.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .background(MaterialTheme.colorScheme.errorContainer, AppShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (outsideLayers.size == 1) {
                    stringResource(Res.string.canvas_layers_outside_one)
                } else {
                    stringResource(Res.string.canvas_layers_outside, outsideLayers.size)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            RaisedButton(
                onClick = {
                    outsideLayers.forEach { layer ->
                        canvasLayouts.misplacements(layer.id).forEach { (layout, _) ->
                            val transform = layout.scene.sources.first { it.id == layer.id }.transform
                            sceneViewModel.updateTransform(
                                layer.id,
                                transform.broughtIntoView(),
                                alternate = layout.isAlternate,
                            )
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.height(28.dp).testTag(CANVAS_BRING_INTO_VIEW_TAG),
                shape = AppShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Text(
                    stringResource(Res.string.canvas_bring_into_view),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun CanvasTabScope.CanvasEditorArea(
    sceneViewModel: SceneViewModel,
    canvasLayouts: List<EditorLayout>,
    modifier: Modifier,
) {
    val selectedSourceId = sceneViewModel.selectedSourceId.value
    // Canvas preview -- padded all round, so a canvas bound by the height (portrait) or
    // the width (ultra-wide) does not run into the edges of the area it sits in.
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (canvasLayouts.size == 1) {
            EditorCanvas(
                modifier = Modifier.fillMaxSize(),
                layout = canvasLayouts.single(),
                selectedSourceId = selectedSourceId,
                sceneViewModel = sceneViewModel,
                activeTool = activeTool,
                drawingStrokeColor = drawingStrokeColor,
                drawingFillColor = drawingFillColor,
                drawingStrokeWidth = drawingStrokeWidth,
            )
        } else {
            // Weighted by shape, so the two stand at the same height and both fit.
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                canvasLayouts.forEach { layout ->
                    val shape = layout.scene.canvasWidth.toFloat() / layout.scene.canvasHeight
                    Column(
                        modifier = Modifier.weight(shape).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val size = "${layout.scene.canvasWidth}×${layout.scene.canvasHeight}"
                        Text(
                            stringResource(
                                if (layout.scene.isLandscape) Res.string.canvas_layout_landscape
                                else Res.string.canvas_layout_portrait,
                                size,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        val tag = CANVAS_LAYOUT_TAG_PREFIX + if (layout.isAlternate) "alternate" else "main"
                        EditorCanvas(
                            modifier = Modifier.weight(1f).fillMaxWidth().testTag(tag),
                            layout = layout,
                            selectedSourceId = selectedSourceId,
                            sceneViewModel = sceneViewModel,
                            activeTool = activeTool,
                            drawingStrokeColor = drawingStrokeColor,
                            drawingFillColor = drawingFillColor,
                            drawingStrokeWidth = drawingStrokeWidth,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CanvasTabScope.CanvasPropertiesPanel(sceneViewModel: SceneViewModel) {
    // Right panel: Properties
    Column(
        modifier = Modifier
            .width(with(density) { rightPanelPx.toDp() })
            .fillMaxHeight()
            .padding(end = 4.dp, top = 4.dp, bottom = 4.dp)
            .bibleListCard()
    ) {
        val selectedSource = sceneViewModel.selectedSource
        if (selectedSource != null) {
            SourcePropertiesPanel(
                source = selectedSource,
                modifier = Modifier.fillMaxSize(),
                appSettings = appSettings,
                cameraHost = cameraHost,
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

/** Puts [scene] on screen. */
internal fun CanvasTabScope.goLive(scene: Scene) {
    onPresentScene(scene)
    wentLive(ScheduleItem.SceneItem(id = UUID.randomUUID().toString(), sceneId = scene.id, sceneName = scene.name))
}
