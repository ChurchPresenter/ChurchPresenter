package org.churchpresenter.liveoutput.preview

import androidx.compose.runtime.getValue
import org.churchpresenter.canvas.liveMerges
import org.churchpresenter.presenter.sizedAs
import org.churchpresenter.strings.generated.resources.preview_merged_label
import org.churchpresenter.strings.generated.resources.preview_bus_label
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import org.churchpresenter.settings.drivesNothing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.browser_source_output_label
import org.churchpresenter.strings.generated.resources.ndi_output_numbered
import org.churchpresenter.strings.generated.resources.omt_output_numbered
import org.churchpresenter.strings.generated.resources.screen_number
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.utils.OutputKind
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.sharedui.utils.outputSizeOf
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.stt.STTManager
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.sharedui.composables.mode

// Which previews the panel draws, one per output, and what each is drawn with.

/**
 * Every output the panel can show, in screen, Browser Source, NDI, OMT order, each drawn by its own
 * preview.
 */
@Composable
internal fun previewEntries(
    proj: ProjectionSettings,
    displayCount: Int,
    realWindowCount: Int,
    devWindowedFallback: Boolean,
    presenterManager: PresenterManager,
    appSettings: AppSettings,
    serverUrl: String,
    qaDisplayUrl: String,
    sttManager: STTManager?,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
): List<PreviewEntry> {
    val context = PreviewContext(presenterManager, appSettings, serverUrl, qaDisplayUrl, sttManager, onSettingsChange)
    // A merged picture is previewed once, at its own shape, under its first output; the rest of
    // its outputs are that same picture and are left out.
    val merges = remember(proj) { proj.liveMerges() }
    val mergedLabel = stringResource(Res.string.preview_merged_label)
    fun PreviewEntry.merged(kind: String, index: Int): PreviewEntry? {
        val key = Constants.previewOutputKey(kind, index)
        val merge = merges[key] ?: return this
        return if (merge.host != key) null else this
    }
    fun ScreenAssignment.forPreview(kind: String, index: Int): ScreenAssignment =
        merges[Constants.previewOutputKey(kind, index)]?.let { sizedAs(it) } ?: this
    fun String.forPreview(kind: String, index: Int): String =
        if (merges.containsKey(Constants.previewOutputKey(kind, index))) "$this $mergedLabel" else this
    return buildList {
        if (presenterManager.previewBus.enabled.value) add(context.previewBusEntry(proj.getAssignment(0)))
        for (i in 0 until displayCount) {
            val screenAssignment = proj.getAssignment(i)

            // Skip displays the user set to "None" — but never skip dev-fallback slots (i >=
            // realWindowCount): those are auto-resolved to None only because no hardware exists,
            // yet main.kt still opens a window for them, so they must appear in the preview too.
            val isDevFallbackSlot = devWindowedFallback && i >= realWindowCount
            // A row on a monitor marked "Don't use" opens no window either, so it is skipped the same way.
            if (!isDevFallbackSlot && proj.drivesNothing(screenAssignment)) continue

            // The operator's name for the monitor, falling back to the numbered default. This
            // panel is the one place they watch all service long, so a booth driving "Foyer TV"
            // and "Balcony" should not have to remember which of those is Screen 2.
            val label = proj.screenLabelOr(screenAssignment, stringResource(Res.string.screen_number, i + 1))
            val screen = Constants.PREVIEW_OUTPUT_SCREEN
            context.entry(OutputKind.SCREEN, i, screenAssignment.forPreview(screen, i), label.forPreview(screen, i))
                .merged(screen, i)?.let(::add)
        }

        // Browser Source outputs — virtual, no physical hardware, so they get their own
        // loop over ProjectionSettings.browserSourceOutputs and their own lock index space.
        proj.browserSourceOutputs.forEachIndexed { i, output ->
            val label = output.browserSourceLabelOr(stringResource(Res.string.browser_source_output_label, i + 1))
            val bs = Constants.PREVIEW_OUTPUT_BROWSER_SOURCE
            context.entry(OutputKind.BROWSER_SOURCE, i, output.forPreview(bs, i), label.forPreview(bs, i))
                .merged(bs, i)?.let(::add)
        }

        // NDI outputs — virtual in exactly the same way as the Browser Source ones above, so they
        // get their own loop over ProjectionSettings.ndiOutputs and their own lock index space.
        // A disabled output is skipped: main.kt renders nothing for it, so a preview would show a
        // picture the network is not actually receiving.
        proj.ndiOutputs.forEachIndexed { i, output ->
            if (!output.ndiEnabled) return@forEachIndexed
            val label = output.ndiLabelOr(stringResource(Res.string.ndi_output_numbered, i + 1))
            val ndi = Constants.PREVIEW_OUTPUT_NDI
            context.entry(OutputKind.NDI, i, output.forPreview(ndi, i), label.forPreview(ndi, i))
                .merged(ndi, i)?.let(::add)
        }

        // OMT outputs, in their own loop and lock index space for the reasons NDI's are, and
        // skipped when disabled for the same reason.
        proj.omtOutputs.forEachIndexed { i, output ->
            if (!output.omtEnabled) return@forEachIndexed
            val label = output.omtLabelOr(stringResource(Res.string.omt_output_numbered, i + 1))
            val omt = Constants.PREVIEW_OUTPUT_OMT
            context.entry(OutputKind.OMT, i, output.forPreview(omt, i), label.forPreview(omt, i))
                .merged(omt, i)?.let(::add)
        }
    }
}

/** What every preview in the panel is drawn with, whichever output list it came from. */
private class PreviewContext(
    private val presenterManager: PresenterManager,
    private val appSettings: AppSettings,
    private val serverUrl: String,
    private val qaDisplayUrl: String,
    private val sttManager: STTManager?,
    private val onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    /**
     * The Preview bus's tile: what is cued, drawn as the first screen's [output] would draw it, and
     * framed green. No locks or transposes -- those belong to an output, and this is none.
     */
    @Composable
    fun previewBusEntry(output: ScreenAssignment): PreviewEntry {
        val label = stringResource(Res.string.preview_bus_label)
        return PreviewEntry(
            Constants.PREVIEW_OUTPUT_PREVIEW_BUS,
            label,
            outputSizeOf(output, OutputKind.SCREEN).aspectRatio,
        ) { m, grouped ->
            SingleDisplayPreview(
                screenIndex = 0,
                screenAssignment = output,
                outputKind = OutputKind.SCREEN,
                presenterManager = presenterManager.previewBus.manager,
                appSettings = appSettings,
                modifier = m,
                serverUrl = serverUrl,
                qaDisplayUrl = qaDisplayUrl,
                sttManager = sttManager,
                label = label,
                showLabel = true,
                showMode = appSettings.projectionSettings.showOutputModes,
                collapsible = !grouped,
                onSettingsChange = onSettingsChange,
                busRole = BusRole.PREVIEW,
            )
        }
    }

    /**
     * The preview of [output], the [index]th of its [kind].
     *
     * Each kind is its own 0-based index space with its own lock map — screen 0, Browser Source 0, NDI
     * output 0 and OMT output 0 are four different outputs — so the key, the locks and the lock toggle
     * are all chosen by [kind] here rather than passed in, where they could be crossed.
     */
    fun entry(kind: OutputKind, index: Int, output: ScreenAssignment, label: String): PreviewEntry {
        return PreviewEntry.of(kind, index, label, output) { m, grouped ->
            val locks = when (kind) {
                OutputKind.SCREEN -> presenterManager.screenLocks.value
                OutputKind.BROWSER_SOURCE -> presenterManager.browserSourceLocks.value
                OutputKind.NDI -> presenterManager.ndiLocks.value
                OutputKind.OMT -> presenterManager.omtLocks.value
            }
            // Only a Browser Source carries a per-output transpose. `kind` is fixed for this entry,
            // so the collect below is either always or never part of its composition.
            val transposes = if (kind == OutputKind.BROWSER_SOURCE) {
                presenterManager.browserSourceTranspose.collectAsState().value
            } else {
                emptyMap()
            }
            SingleDisplayPreview(
                screenIndex = index,
                screenAssignment = output,
                outputKind = kind,
                presenterManager = presenterManager,
                appSettings = appSettings,
                modifier = m,
                serverUrl = serverUrl,
                qaDisplayUrl = qaDisplayUrl,
                sttManager = sttManager,
                locks = locks,
                onToggleLock = { mode ->
                    when (kind) {
                        OutputKind.SCREEN -> presenterManager.setScreenLock(index, mode)
                        OutputKind.BROWSER_SOURCE -> presenterManager.setBrowserSourceLock(index, mode)
                        OutputKind.NDI -> presenterManager.setNdiLock(index, mode)
                        OutputKind.OMT -> presenterManager.setOmtLock(index, mode)
                    }
                },
                transposeSteps = transposes[index] ?: 0,
                onTranspose = if (kind == OutputKind.BROWSER_SOURCE) {
                    { delta ->
                        if (delta == null) {
                            presenterManager.setBrowserSourceTranspose(index, 0)
                        } else {
                            presenterManager.stepBrowserSourceTranspose(index, delta)
                        }
                    }
                } else {
                    null
                },
                label = label,
                showLabel = appSettings.projectionSettings.showOutputLabels,
                showMode = appSettings.projectionSettings.showOutputModes,
                collapsible = !grouped,
                onSettingsChange = onSettingsChange,
                busRole = if (presenterManager.previewBus.enabled.value) BusRole.PROGRAM else null,
            )
        }
    }
}

/**
 * One output's preview, identified by its `Constants.previewOutputKey` so a layout's area can claim
 * it, with the name it goes by and the shape of its picture -- width over height -- for fitting it
 * into an area of a given height.
 */
internal class PreviewEntry(
    val key: String,
    val label: String,
    val aspect: Float,
    val content: @Composable (Modifier, Boolean) -> Unit,
) {
    companion object {
        /** Output [index] of [kind], drawn by [content] at [assignment]'s own shape. */
        fun of(
            kind: OutputKind,
            index: Int,
            label: String,
            assignment: ScreenAssignment,
            content: @Composable (Modifier, Boolean) -> Unit,
        ): PreviewEntry {
            val prefix = when (kind) {
                OutputKind.SCREEN -> Constants.PREVIEW_OUTPUT_SCREEN
                OutputKind.BROWSER_SOURCE -> Constants.PREVIEW_OUTPUT_BROWSER_SOURCE
                OutputKind.NDI -> Constants.PREVIEW_OUTPUT_NDI
                OutputKind.OMT -> Constants.PREVIEW_OUTPUT_OMT
            }
            return PreviewEntry(
                Constants.previewOutputKey(prefix, index),
                label,
                outputSizeOf(assignment, kind).aspectRatio,
                content,
            )
        }
    }
}
