package org.churchpresenter.liveoutput.settings

import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.guideTarget
import org.churchpresenter.settings.mergingProfileOf
import org.churchpresenter.settings.withScreenUnused
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.dev_window_label
import org.churchpresenter.strings.generated.resources.screen_assignment
import org.churchpresenter.strings.generated.resources.screen_col_label
import org.churchpresenter.sharedui.composables.SettingsSection
import org.churchpresenter.theme.components.SettingsTextField
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/**
 * The "Screen assignment" card of the Projection settings tab: which physical display or DeckLink
 * device each output drives, and the per-output content toggles.
 *
 * Split out of ProjectionSettingsTab.kt's single 1,390-line composable. The `remember`-backed
 * values ([screenDevicesAll], [displayOptions]) are passed in rather than recomputed here, so the
 * parent keeps ownership of them and recomposition behaves exactly as it did inline.
 */
@Composable
internal fun ScreenAssignmentCard(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onIdentifyScreen: () -> Unit,
    scenes: List<Scene>,
    screenDevicesAll: List<DetectedScreen>,
    detectedScreens: Int,
    devWindowCount: Int,
    devWindowedFallback: Boolean,
    presenterWindowCount: Int,
    numScreens: Int,
    screenAssignments: List<ScreenAssignment>,
    displayOptions: List<DisplayOption>,
) {
    val proj = settings.projectionSettings
    val updateProjection: ((ProjectionSettings) -> ProjectionSettings) -> Unit = { change ->
        onSettingsChange { s -> s.copy(projectionSettings = change(s.projectionSettings)) }
    }
    val langDropdownWidth = 95.dp

SettingsSection(title = stringResource(Res.string.screen_assignment)) {
    // Detected screens info + simulate stepper + Identify button
    ScreenAssignmentInfoRow(
        detectedScreens, presenterWindowCount, devWindowedFallback, devWindowCount, onIdentifyScreen,
    ) { count -> updateProjection { it.copy(devWindowCount = count) } }
    Spacer(modifier = Modifier.height(4.dp))

    // Grid table — screens are rows (left), content types are columns (top)
    // Wide enough for the longest label ("Dev Window") to stay on a single line, and for a typed
    // monitor name — the left column is a name box for any row that drives a real display.
    val screenLabelWidth = 116.dp
    val displayDropdownWidth = 100.dp
    val resolutionCellWidth = 108.dp

    ScreenAssignmentColumnHeaders(
        devWindowedFallback, screenLabelWidth, displayDropdownWidth, resolutionCellWidth,
    )

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

    // One row per screen
    for (i in 0 until numScreens) {
        val assignment = screenAssignments[i]
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Screen label — the dev-window fallback always occupies slot 0
            val defaultScreenLabel = if (devWindowedFallback && i == 0) {
                stringResource(Res.string.dev_window_label)
            } else {
                stringResource(Res.string.screen_col_label, i + 1)
            }
            val monitorKey = assignment.targetScreenKey
            ScreenNameCell(
                name = if (monitorKey.isEmpty()) assignment.screenName
                else proj.screenNames[monitorKey].orEmpty(),
                default = defaultScreenLabel,
                width = screenLabelWidth,
                onRename = { typed ->
                    updateProjection { projection ->
                        if (monitorKey.isEmpty()) {
                            projection.withAssignment(i, assignment.copy(screenName = typed))
                        } else {
                            projection.withScreenName(monitorKey, typed)
                        }
                    }
                },
            )

            // Display target dropdown
            Box(modifier = Modifier.width(displayDropdownWidth), contentAlignment = Alignment.Center) {
                val current = currentPrimaryOption(displayOptions, assignment)
                OutputTargetDropdown(displayOptions, current, hasDeckLinkInputConflict(current, scenes)) { option ->
                    updateProjection { withPrimaryTarget(it, i, assignment, option, numScreens) }
                }
            }

            // Key output target dropdown (None + display options)
            Box(modifier = Modifier.width(displayDropdownWidth), contentAlignment = Alignment.Center) {
                val keyOutputOptions = rememberKeyOutputOptions(screenDevicesAll, proj)
                val current = currentKeyOption(keyOutputOptions, assignment)
                OutputTargetDropdown(keyOutputOptions, current, hasDeckLinkInputConflict(current, scenes)) { option ->
                    updateProjection { withKeyTarget(it, i, assignment, option, numScreens) }
                }
            }

            // Profile dropdown (fixed column) — the only thing left to choose per output: which
            // profile it follows. Everything about how it looks and what it shows lives there now.
            OutputProfilePicker(
                profiles = proj.outputProfiles,
                activeProfileId = assignment.activeProfileId,
                mergedBy = proj.outputProfiles.mergingProfileOf(
                    Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_SCREEN, i),
                    assignment.activeProfileId,
                ),
                // The first screen's is the one the helper's display tours point at; each has its own too.
                modifier = Modifier.width(langDropdownWidth)
                    .then(if (i == 0) Modifier.guideTarget(GuideTargets.SCREEN_PROFILE_PICKER) else Modifier)
                    .guideTarget(GuideTargets.outputProfilePicker("screen", i)),
                onPick = { pickedId ->
                    updateProjection { it.withAssignment(i, assignment.copy(activeProfileId = pickedId)) }
                },
                // Only a row driving a monitor has one to mark unused.
                onDontUse = assignment.targetScreenKey.takeIf { it.isNotEmpty() }?.let { key ->
                    { updateProjection { it.withScreenUnused(key) } }
                },
            )

            // Dev fallback only — see the header comment. Written to the slot's own
            // devWindowWidth/Height, which is what outputSizeOf falls back to when a slot has no
            // display bounds, so the window, its live preview and every settings preview all take
            // the size from the one place.
            if (devWindowedFallback) {
                ResolutionPicker(
                    label = "",
                    width = assignment.devWindowWidth,
                    height = assignment.devWindowHeight,
                    cellWidth = resolutionCellWidth,
                    labelHeight = 0.dp,
                    onChange = { w, h ->
                        updateProjection {
                            it.withAssignment(i, assignment.copy(devWindowWidth = w, devWindowHeight = h))
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
        } // end data Row

        if (i < numScreens - 1) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 1.dp,
            )
        }
    }

    // The band height used to sit here, below the table. It never governed this card -- only the
    // Bible and song presenters read it -- and being here it reached only screen outputs, so an
    // operator sending an NDI or Browser Source lower third could see the band and find nothing that
    // moved it. It now lives on the Bible and Song tabs, one value each, beside their own margins.
}
}

/**
 * The left-hand column of an assignment row: what this output is called.
 *
 * Every row gets one, whatever it drives. Where it is stored depends on that: a row driving a real
 * display names the *monitor* (keyed by its geometry, so the name follows the hardware and shows in
 * the target menus), and a row set to None, pointed at a DeckLink device or standing in as the dev
 * fallback names the slot instead. The first version of this offered the box only in the first case
 * — which on a single-monitor machine, where the only row is the dev-fallback one, meant the
 * feature was not there at all.
 *
 * Blank means "use the numbered default", which is what makes clearing the box the way to undo a
 * rename, so the default is shown as the placeholder rather than typed into the field.
 */
@Composable
private fun ScreenNameCell(
    name: String,
    default: String,
    width: Dp,
    onRename: (String) -> Unit,
) {
    SettingsTextField(
        value = name,
        onValueChange = onRename,
        placeholder = {
            Text(
                text = default,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = SCREEN_NAME_PLACEHOLDER_ALPHA),
                maxLines = 1,
            )
        },
        modifier = Modifier.width(width).padding(end = 8.dp),
        fillWidth = true,
        // Done rather than the default: a name is finished when Enter is pressed, and it keeps this
        // box out of the "numeric stepper" family. The steppers on this tab are addressed by
        // ordinal among the fields carrying ImeAction.Default, so a name box answering to that
        // description would silently renumber every one of them.
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    )
}

/** Dim enough to read as "not typed yet", solid enough to read at all. */
private const val SCREEN_NAME_PLACEHOLDER_ALPHA = 0.6f
