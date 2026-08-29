package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import org.churchpresenter.app.churchpresenter.utils.isLiveOutput
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants

/**
 * Runs the sample on the **real** outputs for as long as [active], and puts the screen back after.
 *
 * The in-dialog preview answers "what will this look like"; only the output itself answers "does it
 * look right in this room, on that projector, at that distance". So the button pushes the same
 * sample the panel is drawing at the live outputs, styled with the settings being edited rather than
 * the saved ones -- see [PresenterManager.setPreviewSettingsOverride], without which the screen
 * would show the sample in the styling the operator is in the middle of replacing.
 *
 * **Revert is structural, not remembered.** The restore hangs off `onDispose`, so it runs when the
 * button is switched off, when the operator moves to another settings tab, and when the dialog is
 * closed -- by OK, by Cancel or by the window's own X, all of which drop the dialog's subtree. There
 * is no close path that has to remember to call anything.
 *
 * @param outputs what the previewed picture needs the outputs to be set to for the duration -- see
 *   [withPreviewOutputs]. The switches above the preview describe a *picture*, but a real output
 *   takes each of those decisions from its own assignment, so without this the button drew a
 *   full-screen slide with no look-ahead and no chart however the tab was set.
 * @param push puts the sample on the manager: the content setters and the mode, in that order.
 * @param contentKey what the pushed sample is made of, so a change of sample or of styling
 *   re-pushes. Anything with a sound `equals` -- the sample slot and the verse list, typically.
 */
@Composable
internal fun OnScreenPreviewEffect(
    active: Boolean,
    settings: AppSettings,
    presenterManager: PresenterManager?,
    outputs: PreviewOutputState,
    contentKey: Any?,
    push: (PresenterManager) -> Unit,
) {
    val previewSettings = settings.withPreviewOutputs(outputs)
    // A box rather than a plain local: onDispose has to read what the enter branch wrote, and a
    // `remember` of the snapshot itself would be keyed wrong -- it must survive the recompositions
    // that a settings edit causes while the preview is running.
    val snapshot = remember { arrayOfNulls<PresenterManager.LiveStateSnapshot>(1) }

    DisposableEffect(active, presenterManager) {
        if (active && presenterManager != null) {
            snapshot[0] = presenterManager.snapshotLiveState()
            presenterManager.setShowPresenterWindow(true)
        }
        onDispose {
            val taken = snapshot[0] ?: return@onDispose
            snapshot[0] = null
            presenterManager?.setPreviewSettingsOverride(null)
            presenterManager?.restoreLiveState(taken)
        }
    }

    LaunchedEffect(active, previewSettings, contentKey, presenterManager) {
        if (!active || presenterManager == null) return@LaunchedEffect
        presenterManager.setPreviewSettingsOverride(previewSettings)
        push(presenterManager)
    }
}

/**
 * What the preview switches describe, in the terms an output is actually configured in.
 *
 * Every one of these is a property of the *output* rather than of the styling: a screen draws the
 * band because its own assignment says lower third, draws a look-ahead line because its own
 * assignment says so, and draws a chart because its own `showChords` does. The tab's switches say
 * what the picture should contain, so they have to be translated before a real screen can show it.
 *
 * `null` means "leave the outputs as they are" -- the Bible tab has no look-ahead and no chords, and
 * must not silently turn either off on a screen configured for songs.
 */
internal data class PreviewOutputState(
    val lowerThird: Boolean,
    val songLookAhead: Boolean? = null,
    val showChords: Boolean? = null,
)

/**
 * The same settings with every live output set up to draw [state]'s picture.
 *
 * Only the outputs that are switched on: a preview must not light up a projector the operator has
 * disabled. A band already set to vertical stays vertical, because that is a choice about the
 * screen's shape rather than about the styling. Whether songs or scripture reach the screen at all
 * is left alone for the same reason `withSongLanguage` leaves it alone -- "off" means that screen
 * is deliberately kept clear, which is a different question from what it would look like.
 *
 * This override lives only as long as the preview: it is folded over the saved settings in main.kt
 * and dropped when the button goes off or the dialog closes, so nothing here is ever written to
 * disk.
 */
internal fun AppSettings.withPreviewOutputs(state: PreviewOutputState): AppSettings = copy(
    projectionSettings = projectionSettings.copy(
        screenAssignments = projectionSettings.screenAssignments.map { assignment ->
            if (!assignment.isLiveOutput()) {
                assignment
            } else {
                assignment.copy(
                    displayMode = when {
                        !state.lowerThird -> Constants.DISPLAY_MODE_FULLSCREEN
                        assignment.isLowerThird -> assignment.displayMode
                        else -> Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL
                    },
                    songLookAhead = state.songLookAhead ?: assignment.songLookAhead,
                    showChords = state.showChords ?: assignment.showChords,
                )
            }
        },
    ),
)
