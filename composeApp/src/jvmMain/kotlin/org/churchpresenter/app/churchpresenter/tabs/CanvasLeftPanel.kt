package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.app.churchpresenter.viewmodel.SceneViewModel
import org.churchpresenter.app.churchpresenter.presenter.liveMerges
import churchpresenter.composeapp.generated.resources.preview_merged_label
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import org.churchpresenter.app.churchpresenter.composables.initialPassClickable
import org.churchpresenter.app.churchpresenter.composables.initialPassCombinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.lazy.items
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import org.churchpresenter.theme.components.KeyIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import churchpresenter.composeapp.generated.resources.ic_add
import churchpresenter.composeapp.generated.resources.ic_arrow_down
import churchpresenter.composeapp.generated.resources.ic_arrow_up
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.ic_copy
import churchpresenter.composeapp.generated.resources.ic_delete
import churchpresenter.composeapp.generated.resources.ic_edit
import churchpresenter.composeapp.generated.resources.canvas_new_scene
import churchpresenter.composeapp.generated.resources.canvas_scenes
import churchpresenter.composeapp.generated.resources.canvas_sources
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.assignedDisplayBounds
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.forArea
import org.churchpresenter.core.models.scene.isLandscape
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import churchpresenter.composeapp.generated.resources.canvas_rename_confirm
import churchpresenter.composeapp.generated.resources.canvas_rename_scene
import churchpresenter.composeapp.generated.resources.canvas_remove_scene
import churchpresenter.composeapp.generated.resources.canvas_size_screen
import churchpresenter.composeapp.generated.resources.canvas_duplicate_scene
import churchpresenter.composeapp.generated.resources.canvas_scene_copy_name
import churchpresenter.composeapp.generated.resources.canvas_delete_source
import churchpresenter.composeapp.generated.resources.canvas_source_move_forward
import churchpresenter.composeapp.generated.resources.canvas_source_move_backward
import churchpresenter.composeapp.generated.resources.canvas_toggle_visibility
import churchpresenter.composeapp.generated.resources.canvas_toggle_lock
import java.awt.Rectangle
import org.churchpresenter.core.models.scene.Scene

/* The Canvas tab's left panel: the scenes, and the selected scene's sources. */

@Composable
internal fun CanvasTabScope.CanvasLeftPanel(sceneViewModel: SceneViewModel) {
    Column(
        modifier = Modifier
            .width(with(density) { leftPanelPx.toDp() })
            .fillMaxHeight()
            .padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
            .bibleListCard()
            .padding(8.dp)
    ) {
        // Scene selector section
        Text(
            stringResource(Res.string.canvas_scenes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        CanvasSceneList(sceneViewModel, Modifier.weight(CANVAS_SCENE_LIST_WEIGHT).fillMaxWidth())

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RaisedButton(
                onClick = { sceneViewModel.addScene() },
                modifier = Modifier.weight(1f),
                shape = AppShape(8.dp),
                contentPadding = ButtonDefaults.ContentPadding
            ) {
                Icon(painterResource(Res.drawable.ic_add), null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.canvas_new_scene), style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Source list section
        Text(
            stringResource(Res.string.canvas_sources),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))

        val currentScene = sceneViewModel.currentScene

        if (currentScene != null) {
            CanvasSourcePanel(sceneViewModel, currentScene, Modifier.weight(CANVAS_SOURCE_LIST_WEIGHT).fillMaxWidth())
        }
    }
}

@Composable
private fun CanvasTabScope.CanvasSceneList(sceneViewModel: SceneViewModel, listModifier: Modifier) {
    // Resolve live presentation display for aspect ratio checks
    val presentationAssignment0 = appSettings.projectionSettings.getAssignment(0)
    val presentationBounds0 = remember(
        presentationAssignment0.targetDisplay,
        presentationAssignment0.targetBoundsX,
        presentationAssignment0.targetBoundsY,
    ) {
        assignedDisplayBounds(presentationAssignment0)
    }
    val displayAr0 =
        if (presentationBounds0.height > 0) presentationBounds0.width.toFloat() / presentationBounds0.height else 0f
    // Every projection screen a scene can be sized to match, from its size menu. A screen
    // switched off or not yet resolved has no size and is left out.
    val assignments = appSettings.projectionSettings.screenAssignments
    val assignmentBounds = remember(assignments) { assignments.map { assignedDisplayBounds(it) } }
    // And every merged picture, at its whole size -- the size a scene for a video wall is built at.
    val mergedLabel = stringResource(Res.string.preview_merged_label)
    val mergedOutputs = remember(appSettings.projectionSettings) {
        appSettings.projectionSettings.liveMerges().values.distinct()
    }.map { merge ->
        val name = appSettings.projectionSettings.outputProfiles
            .find { it.id == merge.profileId }?.name.orEmpty()
        CanvasOutputSize(label = "$name $mergedLabel".trim(), width = merge.width, height = merge.height)
    }
    val canvasOutputs =
        mergedOutputs + assignments.zip(assignmentBounds).mapIndexedNotNull { index, (assignment, bounds) ->
        if (assignment.targetDisplay == Constants.KEY_TARGET_NONE || bounds.width <= 0 || bounds.height <= 0) {
            null
        } else {
            CanvasOutputSize(
                label = assignment.screenName.ifBlank {
                    stringResource(Res.string.canvas_size_screen, index + 1)
                },
                width = bounds.width,
                height = bounds.height,
            )
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    LazyColumn(
        modifier = listModifier,
        verticalArrangement = Arrangement.spacedBy(rowPad(2.dp)),
    ) {
        items(sceneViewModel.scenes) { scene ->
            SceneRow(sceneViewModel, scene, presentationBounds0, displayAr0, canvasOutputs)
        }
    }
}

/** One scene: its name (or the field renaming it), a warning when its shape is not the output's, and its actions. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CanvasTabScope.SceneRow(
    sceneViewModel: SceneViewModel,
    scene: Scene,
    presentationBounds0: Rectangle,
    displayAr0: Float,
    canvasOutputs: List<CanvasOutputSize>,
) {
    val isSelected = scene.id == sceneViewModel.currentSceneId.value
    val isRenaming = renamingSceneId == scene.id
    val usedLayout0 =
        scene.forArea(presentationBounds0.width.toFloat(), presentationBounds0.height.toFloat())
    val sceneAr0 = if (usedLayout0.canvasHeight > 0) {
        usedLayout0.canvasWidth.toFloat() / usedLayout0.canvasHeight
    } else {
        0f
    }
    val isMismatched = displayAr0 > 0f && kotlin.math.abs(displayAr0 - sceneAr0) > 0.01f
    val (sceneHover, sceneHovered) = rememberRowHover()
    val sceneColors = bibleRowColors(isSelected, sceneHovered)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BibleListRowShape)
            .background(sceneColors.background)
            .hoverable(sceneHover)
            .padding(start = 10.dp, end = 8.dp, top = rowPad(4.dp), bottom = rowPad(4.dp)),
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
                    .border(1.dp, MaterialTheme.colorScheme.primary, AppShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
            KeyIconButton(
                onClick = {
                    sceneViewModel.renameScene(scene.id, renameText)
                    renamingSceneId = null
                },
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(Res.string.canvas_rename_confirm),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            Text(
                scene.name,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) sceneColors.ink else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
                    .initialPassCombinedClickable(
                        onClick = { sceneViewModel.selectScene(scene.id) },
                        onDoubleClick = {
                            renamingSceneId = scene.id
                            renameText = scene.name
                        }
                    )
            )
            if (isMismatched) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            SceneRowActions(sceneViewModel, scene, canvasOutputs)
        }
    }
}

/** Rename, duplicate, size and remove, for one scene. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CanvasTabScope.SceneRowActions(
    sceneViewModel: SceneViewModel,
    scene: Scene,
    canvasOutputs: List<CanvasOutputSize>,
) {
    TooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = 4.dp,
            ) {
                Text(
                    stringResource(Res.string.canvas_rename_scene),
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
    val copyName = stringResource(Res.string.canvas_scene_copy_name, scene.name)
    TooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = 4.dp,
            ) {
                Text(
                    stringResource(Res.string.canvas_duplicate_scene),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.BottomCenter,
            offset = DpOffset(0.dp, 4.dp),
        )
    ) {
        KeyIconButton(
            onClick = { sceneViewModel.duplicateScene(scene.id, copyName) },
            modifier = Modifier.size(20.dp)
        ) {
            Icon(
                painterResource(Res.drawable.ic_copy),
                contentDescription = stringResource(Res.string.canvas_duplicate_scene),
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    SceneRowSizeAndRemove(sceneViewModel, scene, canvasOutputs)
}

/** The scene's size menu and its remove button. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CanvasTabScope.SceneRowSizeAndRemove(
    sceneViewModel: SceneViewModel,
    scene: Scene,
    canvasOutputs: List<CanvasOutputSize>,
) {
    CanvasSizeMenu(
        width = scene.canvasWidth,
        height = scene.canvasHeight,
        outputs = canvasOutputs,
        onSetSize = { w, h -> sceneViewModel.updateCanvasSize(w, h, scene.id) },
        dualLayout = scene.alternate != null,
        onDualLayoutChange = { on ->
            if (on) {
                sceneViewModel.setDualLayout(scene.id, true)
            } else {
                confirmSingleLayoutSceneId = scene.id
            }
        },
    )
    TooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = 4.dp,
            ) {
                Text(
                    stringResource(Res.string.canvas_remove_scene),
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

@Composable
private fun CanvasTabScope.CanvasSourcePanel(
    sceneViewModel: SceneViewModel,
    currentScene: Scene,
    listModifier: Modifier,
) {
    val sourceLayouts = remember(currentScene) { currentScene.editorLayouts() }
    val placementTexts = placementTexts(dual = sourceLayouts.size > 1)
    LazyColumn(
        modifier = listModifier,
        verticalArrangement = Arrangement.spacedBy(rowPad(2.dp)),
    ) {
        // Render in reverse order so top item = front
        items(currentScene.sources.reversed()) { source ->
            SourceRow(sceneViewModel, source, sourceLayouts, placementTexts)
        }
    }

    // Source toolbar
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        AddSourceButton(sceneViewModel)
        SelectedSourceButtons(sceneViewModel)
    }
}

/** One layer: visibility, lock, name, and a warning when a layout clips it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CanvasTabScope.SourceRow(
    sceneViewModel: SceneViewModel,
    source: SceneSource,
    sourceLayouts: List<EditorLayout>,
    placementTexts: Map<Pair<Boolean, CanvasPlacement>, String>,
) {
    val selectedSourceId = sceneViewModel.selectedSourceId.value
    val isSelected = source.id == selectedSourceId
    val (sourceHover, sourceHovered) = rememberRowHover()
    val sourceColors = bibleRowColors(isSelected, sourceHovered)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BibleListRowShape)
            .background(sourceColors.background)
            .hoverable(sourceHover)
            .padding(start = 4.dp, end = 4.dp, top = rowPad(2.dp), bottom = rowPad(2.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SourceRowToggles(sceneViewModel, source)

        Text(
            source.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).alpha(if (source.visible) 1f else CANVAS_HIDDEN_SOURCE_ALPHA)
                .initialPassClickable { sceneViewModel.selectSource(source.id) }
        )
        // The canvas clips what it draws, so a layer past an edge is cut off or gone
        // with nothing else on screen saying it is still there.
        // With two layouts it is flagged if it is outside either, and the tooltip
        // says which.
        val misplaced = sourceLayouts.misplacements(source.id)
        if (misplaced.isNotEmpty()) {
            val placementText = misplaced.joinToString("\n") { (layout, placement) ->
                placementTexts.getValue(layout.scene.isLandscape to placement)
            }
            TooltipArea(
                tooltip = {
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface,
                        shape = MaterialTheme.shapes.extraSmall,
                        tonalElevation = 4.dp,
                    ) {
                        Text(
                            placementText,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                tooltipPlacement = TooltipPlacement.ComponentRect(
                    anchor = Alignment.BottomCenter,
                    offset = DpOffset(0.dp, 4.dp),
                )
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = placementText,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** Show or hide, and lock or unlock, one layer. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CanvasTabScope.SourceRowToggles(sceneViewModel: SceneViewModel, source: SceneSource) {
    // Visibility toggle
    TooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = 4.dp,
            ) {
                Text(
                    stringResource(Res.string.canvas_toggle_visibility),
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
            onClick = { sceneViewModel.toggleSourceVisibility(source.id) },
            modifier = Modifier.size(20.dp)
        ) {
            Icon(
                if (source.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                contentDescription = stringResource(Res.string.canvas_toggle_visibility),
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Lock toggle
    TooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = 4.dp,
            ) {
                Text(
                    stringResource(Res.string.canvas_toggle_lock),
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
            onClick = { sceneViewModel.toggleSourceLock(source.id) },
            modifier = Modifier.size(20.dp)
        ) {
            Icon(
                if (source.locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = stringResource(Res.string.canvas_toggle_lock),
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Delete, bring forward and send back, for the selected layer. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CanvasTabScope.SelectedSourceButtons(sceneViewModel: SceneViewModel) {
    val selectedSourceId = sceneViewModel.selectedSourceId.value
    val currentSelectedId = selectedSourceId
    if (currentSelectedId != null) {
        TooltipArea(
            tooltip = {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = MaterialTheme.shapes.extraSmall,
                    tonalElevation = 4.dp,
                ) {
                    Text(
                        stringResource(Res.string.canvas_delete_source),
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
        SourceOrderButtons(sceneViewModel, currentSelectedId)
    }
}

/** Bring the selected layer forward, or send it back. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CanvasTabScope.SourceOrderButtons(sceneViewModel: SceneViewModel, currentSelectedId: String) {
    TooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = 4.dp,
            ) {
                Text(
                    stringResource(Res.string.canvas_source_move_forward),
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
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = 4.dp,
            ) {
                Text(
                    stringResource(Res.string.canvas_source_move_backward),
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
