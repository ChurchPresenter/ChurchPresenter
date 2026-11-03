package org.churchpresenter.app.churchpresenter

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.strings.generated.resources.background_default
import org.churchpresenter.strings.generated.resources.bible
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.set_as_background
import org.churchpresenter.strings.generated.resources.set_as_background_all_outputs
import org.churchpresenter.strings.generated.resources.set_as_background_confirm
import org.churchpresenter.strings.generated.resources.set_as_background_set
import org.churchpresenter.strings.generated.resources.songs
import androidx.compose.runtime.Composable
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BLANK_OUTPUT_PROFILE_ID
import org.churchpresenter.settings.BackgroundContent
import org.churchpresenter.settings.backgroundShows
import org.churchpresenter.settings.drivesNothing
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.withBackgroundSet
import org.churchpresenter.sharedui.composables.BackgroundTarget
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.browser_source_output_label
import org.churchpresenter.strings.generated.resources.ndi_output_numbered
import org.churchpresenter.strings.generated.resources.omt_output_numbered
import org.churchpresenter.strings.generated.resources.screen_number
import org.jetbrains.compose.resources.stringResource
import java.io.File

/**
 * Where Set as Background can put a picture or clip, besides every output: each profile an output
 * uses, named by the outputs using it ("Screen 1 + NDI Output 1"), since setting it on one of them
 * sets it on all of them. Outputs that draw nothing — a monitor set to Don't use, an output turned
 * off, the Blank profile — are left out, the way the preview panel leaves them out.
 */
@Composable
internal fun backgroundTargets(appSettings: AppSettings): List<BackgroundTarget> {
    val proj = appSettings.projectionSettings
    val outputs = buildList {
        proj.screenAssignments.forEachIndexed { i, output ->
            if (!proj.drivesNothing(output)) {
                val label = proj.screenLabelOr(output, stringResource(Res.string.screen_number, i + 1))
                add(label to output.activeProfileId)
            }
        }
        proj.browserSourceOutputs.forEachIndexed { i, output ->
            add(output.browserSourceLabelOr(stringResource(Res.string.browser_source_output_label, i + 1)) to
                output.activeProfileId)
        }
        proj.ndiOutputs.forEachIndexed { i, output ->
            if (output.ndiEnabled) {
                add(output.ndiLabelOr(stringResource(Res.string.ndi_output_numbered, i + 1)) to output.activeProfileId)
            }
        }
        proj.omtOutputs.forEachIndexed { i, output ->
            if (output.omtEnabled) {
                add(output.omtLabelOr(stringResource(Res.string.omt_output_numbered, i + 1)) to output.activeProfileId)
            }
        }
    }
    return groupedBackgroundTargets(proj.outputProfiles.map { it.id }, outputs)
}

/**
 * One target per profile in [profileOrder] that an output in [outputs] (its label and profile) uses,
 * named by those outputs in their order.
 */
internal fun groupedBackgroundTargets(
    profileOrder: List<String>,
    outputs: List<Pair<String, String?>>,
): List<BackgroundTarget> {
    val labelsByProfile = outputs
        .mapNotNull { (label, profileId) -> profileId?.takeIf { it != BLANK_OUTPUT_PROFILE_ID }?.let { it to label } }
        .groupBy({ it.first }, { it.second })
    return profileOrder.mapNotNull { id ->
        labelsByProfile[id]?.let { BackgroundTarget(id, it.joinToString(TARGET_LABEL_SEPARATOR)) }
    }
}

/** [this] with [file] saved as the [content] background of [profileId]'s outputs, or every output for null. */
internal fun AppSettings.withFileAsBackground(file: File, profileId: String?, content: BackgroundContent): AppSettings =
    withBackgroundSet(
        path = file.absolutePath,
        isVideo = file.extension.lowercase() in Constants.VIDEO_EXTENSIONS,
        content = content,
        profileId = profileId,
    )

/** A Set as Background waiting on the operator's yes: [file] as the [content] background of [target]. */
@Immutable
internal data class PendingBackground(val file: File, val content: BackgroundContent, val target: BackgroundTarget?)

/** [PendingBackground] for [profileId], named the way the menu named it; null [profileId] is every output. */
internal fun List<BackgroundTarget>.pendingBackground(file: File, profileId: String?, content: BackgroundContent) =
    PendingBackground(file, content, profileId?.let { id -> firstOrNull { it.profileId == id } })

/**
 * What a tab's Set as Background is wired to: [request] asks first, through [MainDesktopState.pendingBackground],
 * naming the target the way [targets] does; [isBackground] says which background a file already is, so the
 * menu can mark it.
 */
internal class SetAsBackgroundWiring(
    private val targets: List<BackgroundTarget>,
    private val settings: AppSettings,
    private val state: MainDesktopState,
) {
    fun request(file: File, profileId: String?, content: BackgroundContent) {
        state.pendingBackground = targets.pendingBackground(file, profileId, content)
    }

    fun isBackground(file: File, profileId: String?, content: BackgroundContent): Boolean =
        settings.backgroundShows(file.absolutePath, content, profileId)
}

/** The answer to Set as Background's question: [confirm] saves it, through [onSettingsChange]; either closes it. */
internal class SetAsBackgroundConfirmation(
    private val state: MainDesktopState,
    private val onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    fun confirm(pending: PendingBackground) {
        onSettingsChange { it.withFileAsBackground(pending.file, pending.target?.profileId, pending.content) }
        state.pendingBackground = null
    }

    fun dismiss() {
        state.pendingBackground = null
    }
}

/**
 * Asks before a Set as Background is saved: which background, on which outputs, and which file, since
 * the screen can look the same afterwards (a song that follows Default shows a new Default too).
 */
@Composable
internal fun SetBackgroundConfirmDialog(
    pending: PendingBackground?,
    onConfirm: (PendingBackground) -> Unit,
    onDismiss: () -> Unit,
) {
    if (pending == null) return
    val content = stringResource(
        when (pending.content) {
            BackgroundContent.DEFAULT -> Res.string.background_default
            BackgroundContent.SONG -> Res.string.songs
            BackgroundContent.BIBLE -> Res.string.bible
        },
    )
    val outputs = pending.target?.label ?: stringResource(Res.string.set_as_background_all_outputs)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.set_as_background)) },
        text = { Text(stringResource(Res.string.set_as_background_confirm, content, outputs, pending.file.name)) },
        confirmButton = {
            RaisedButton(shape = AppShape(6.dp), onClick = { onConfirm(pending) }) {
                Text(stringResource(Res.string.set_as_background_set))
            }
        },
        dismissButton = {
            GhostButton(shape = AppShape(6.dp), onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

private const val TARGET_LABEL_SEPARATOR = " + "
