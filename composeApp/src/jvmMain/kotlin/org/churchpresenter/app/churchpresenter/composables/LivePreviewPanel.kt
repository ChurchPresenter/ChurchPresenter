package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.canvas.DeckLinkManager
import org.churchpresenter.canvas.liveMerges
import org.churchpresenter.presenter.sizedAs
import org.churchpresenter.strings.generated.resources.preview_merged_label
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.testTag
import org.churchpresenter.strings.generated.resources.preview_layout_done
import org.churchpresenter.strings.generated.resources.preview_layout_edit
import org.churchpresenter.sharedui.utils.rememberScreenDevices
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import org.churchpresenter.settings.activeLayout
import org.churchpresenter.settings.updateLayout
import org.churchpresenter.theme.AppShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import org.churchpresenter.theme.components.KeyIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.output_profile_blank
import org.churchpresenter.strings.generated.resources.output_profile_swap_menu_tooltip
import org.churchpresenter.icons.generated.resources.ic_pause
import org.churchpresenter.icons.generated.resources.ic_play
import org.churchpresenter.strings.generated.resources.browser_source_output_label
import org.churchpresenter.strings.generated.resources.ndi_output_numbered
import org.churchpresenter.strings.generated.resources.omt_output_numbered
import org.churchpresenter.strings.generated.resources.collapse_preview
import org.churchpresenter.strings.generated.resources.expand_preview
import org.churchpresenter.strings.generated.resources.live_preview_title
import org.churchpresenter.strings.generated.resources.screen_number
import org.churchpresenter.strings.generated.resources.pause
import org.churchpresenter.strings.generated.resources.play
import org.churchpresenter.app.churchpresenter.PresenterScreen
import org.churchpresenter.app.churchpresenter.showsOutputBackground
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BLANK_OUTPUT_PROFILE_ID
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.profileFor
import org.churchpresenter.settings.resolvedFor
import org.churchpresenter.presenter.LowerThirdLayout
import org.churchpresenter.presenter.LocalBandOutgoing
import org.churchpresenter.presenter.LocalBandSongLineIndex
import org.churchpresenter.presenter.LocalLottieBandClock
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.app.churchpresenter.BuildConfig
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.utils.DevFlags
import org.churchpresenter.presenter.showsContentFor
import org.churchpresenter.sharedui.utils.OutputKind
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.sharedui.utils.OutputSize
import org.churchpresenter.sharedui.utils.outputSizeOf
import org.churchpresenter.media.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.stt.STTManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.sharedui.composables.SlimSlider
import org.churchpresenter.sharedui.composables.mode
import org.churchpresenter.media.viewmodel.formatMediaTime

private const val AUDIO_LEVEL_COLOR = 0xFF4CAF50

/** Half a pulse of the LIVE badge, in milliseconds; it reverses, so a full cycle is twice this. */
private const val LIVE_PULSE_MS = 550

/**
 * A scaled-down preview of whatever is currently live on the presenter windows.
 * Shows one preview per configured display, each respecting its screen assignment.
 */
@Composable
fun LivePreviewPanel(
    presenterManager: PresenterManager,
    appSettings: AppSettings,
    modifier: Modifier = Modifier,
    serverUrl: String = "",
    qaDisplayUrl: String = "",
    sttManager: STTManager? = null,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    /** Whether the active layout is being edited -- every area carries its controls. */
    editingLayout: Boolean = false,
    /** Ends editing the layout; the Done bar above it calls this. */
    onDoneEditing: () -> Unit = {},
) {
    val proj = appSettings.projectionSettings
    val deckLinkCount = remember { if (DeckLinkManager.isAvailable()) DeckLinkManager.listDevices().size else 0 }
    val realWindowCount = ((rememberScreenDevices().size - 1) + deckLinkCount).coerceAtLeast(0)
    // Mirror main.kt's dev-fallback: with no real output, preview the dev fallback window(s) so the
    // right-pane preview matches what's actually opened. devWindowCount lets several be previewed.
    val devWindowedFallback = (!BuildConfig.IS_RELEASE || DevFlags.forceDevWindow) && realWindowCount == 0
    val displayCount = realWindowCount + if (devWindowedFallback) proj.devWindowCount.coerceAtLeast(1) else 0
    val mediaViewModel = LocalMediaViewModel.current

    val layout = proj.activeLayout()
    // A layout that fills the panel takes its height and does not scroll; otherwise the panel is
    // scrollable: with several outputs (displays, dev-fallback windows, browser sources, NDI) the
    // previews are taller than the sidebar and would otherwise be clipped at the bottom.
    val fills = layout != null && proj.previewLayoutFillsPanel
    Column(
        modifier = modifier.fillMaxWidth()
            .then(if (fills) Modifier else Modifier.verticalScroll(rememberScrollState())),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val entries = previewEntries(
            proj, displayCount, realWindowCount, devWindowedFallback,
            presenterManager, appSettings, serverUrl, qaDisplayUrl, sttManager, onSettingsChange,
        )

        // With no layout the panel lists every output, one per row. With one, the layout draws the
        // outputs its areas name, and the rest are listed under it or left out as the panel says.
        val byKey = entries.associateBy { it.key }
        if (layout == null) {
            for (entry in entries) entry.content(Modifier.fillMaxWidth(), false)
        } else {
            if (editingLayout) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(Res.string.preview_layout_edit),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDoneEditing, modifier = Modifier.testTag(PREVIEW_LAYOUT_DONE_TAG)) {
                        Text(stringResource(Res.string.preview_layout_done))
                    }
                }
            }
            val edits = if (editingLayout) {
                PreviewLayoutEdits(layout.root) { root ->
                    onSettingsChange { s ->
                        val updated = s.projectionSettings.updateLayout(layout.id) { it.copy(root = root) }
                        s.copy(projectionSettings = updated)
                    }
                }
            } else {
                null
            }
            PreviewLayoutView(
                root = layout.root,
                entries = byKey,
                fills = fills,
                headerAllowance = if (proj.showOutputLabels || proj.showOutputModes) PREVIEW_HEADER_ALLOWANCE else 0.dp,
                edits = edits,
                modifier = if (fills) Modifier.weight(1f) else Modifier,
            )
            if (proj.listUnplacedOutputs) {
                val shown = layout.root.outputs().toSet()
                for (entry in entries) if (entry.key !in shown) entry.content(Modifier.fillMaxWidth(), false)
            }
        }

        // Media controls — for the clip this panel can still do something with; see
        // [mediaTransportUseful], which is where the rule and its reasoning live.
        val presentingMode by presenterManager.presentingMode
        val transportUseful = mediaViewModel != null && mediaTransportUseful(
            isLoaded = mediaViewModel.isLoaded,
            isPlaying = mediaViewModel.isPlaying,
            presentingMode = presentingMode,
        )
        if (transportUseful && mediaViewModel != null) {
            MediaPreviewControls(
                    isPlaying = mediaViewModel.isPlaying,
                    duration = mediaViewModel.duration,
                    currentPosition = mediaViewModel.currentPosition,
                    formatTime = { formatMediaTime(it) },
                    onTogglePlayPause = { mediaViewModel.togglePlayPause() },
                    onSeekTo = { mediaViewModel.position.seekTo(it) }
                )
        }
    }
}

/** Test handle for the Done button that ends editing the layout. */
internal const val PREVIEW_LAYOUT_DONE_TAG = "preview_layout_done"

/** The height a preview's name line takes above its picture, for fitting a preview to an area's height. */
private val PREVIEW_HEADER_ALLOWANCE = 26.dp

/**
 * Whether the panel's transport row can still do anything for the loaded clip.
 *
 * Two cases, and the second is why this is not simply "media is live": audio keeps playing while the
 * operator shows a song or a verse, and its transport has to stay reachable.
 *
 *  * the media is what is on screen, playing or paused; or
 *  * it is playing behind whatever is.
 *
 * A clip that has **finished** is neither. `MediaViewModel.markFinished` leaves it loaded, rewound to
 * 0 and not playing, so the old rule -- anything live at all, plus a loaded clip -- brought the seek
 * bar back the moment the next song went live, showing 0:00 of a video that was over and seekable to
 * nowhere.
 */
internal fun mediaTransportUseful(isLoaded: Boolean, isPlaying: Boolean, presentingMode: Presenting): Boolean =
    isLoaded && (presentingMode == Presenting.MEDIA || isPlaying)

/**
 * Every output the panel can show, in screen, Browser Source, NDI, OMT order, each drawn by its own
 * preview.
 */
@Composable
private fun previewEntries(
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
        for (i in 0 until displayCount) {
            val screenAssignment = proj.getAssignment(i)

            // Skip displays the user set to "None" — but never skip dev-fallback slots (i >=
            // realWindowCount): those are auto-resolved to None only because no hardware exists,
            // yet main.kt still opens a window for them, so they must appear in the preview too.
            val isDevFallbackSlot = devWindowedFallback && i >= realWindowCount
            if (!isDevFallbackSlot && screenAssignment.targetDisplay == Constants.KEY_TARGET_NONE) continue

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
    val presenterManager: PresenterManager,
    val appSettings: AppSettings,
    val serverUrl: String,
    val qaDisplayUrl: String,
    val sttManager: STTManager?,
    val onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
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

@Composable
private fun SingleDisplayPreview(
    screenIndex: Int,
    screenAssignment: ScreenAssignment,
    outputKind: OutputKind,
    presenterManager: PresenterManager,
    appSettings: AppSettings,
    modifier: Modifier = Modifier,
    serverUrl: String = "",
    qaDisplayUrl: String = "",
    sttManager: STTManager? = null,
    locks: Map<Int, Presenting> = emptyMap(),
    onToggleLock: (Presenting?) -> Unit = {},
    transposeSteps: Int = 0,
    onTranspose: ((Int?) -> Unit)? = null,
    label: String,
    showLabel: Boolean = true,
    showMode: Boolean = true,
    collapsible: Boolean = true,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
) {
    // The raw assignment, kept for physical fields (key output, size) and for the profile swap
    // menu below (it reads/writes `activeProfileId` itself). Everything content-shaped resolves
    // through the assigned profile instead -- this tile must show what the output actually
    // draws, not fields a bare assignment no longer carries.
    val rawAssignment = screenAssignment
    val profile = remember(appSettings.projectionSettings, rawAssignment) {
        appSettings.projectionSettings.profileFor(rawAssignment) ?: OutputProfile()
    }
    // This preview must show what the real output shows, so it resolves the same per-profile
    // styling the presenter window does. Remembered: resolution merges a sparse tree into the
    // document and decodes it, which is real work to repeat on every recomposition. Keyed on
    // both sides, so it is redone exactly when one of them changes and not otherwise.
    val outputSettings = remember(appSettings, profile) {
        appSettings.resolvedFor(profile)
    }
    val presentingMode by presenterManager.presentingMode
    val effectiveMode = locks[screenIndex] ?: presentingMode
    // The clock stays wrapped: unwrapping it here would recompose this panel on every band frame.
    val bandSongLineIndex by presenterManager.bandSongLineIndex
    val bandOutgoing by presenterManager.bandOutgoing
    val mediaViewModel = LocalMediaViewModel.current

    val isLowerThird = profile.isLowerThird

    // The same background switches every real output obeys — this layout's own
    // (Projection settings' Fullscreen/Lower Third Background columns) and the per-content-type
    // one beside it. Without them the preview draws a background the output is suppressing, and
    // the two disagree on screen for the rest of the service.
    val showsBackground = showsOutputBackground(profile)

    // Determine if this screen shows the current content
    val showsContent = showsContentFor(effectiveMode, profile)

    // This output's own size. Every preview used to take the first non-primary monitor's shape, so
    // a booth running a 4:3 foyer TV beside a 16:9 projector -- or any Browser Source or NDI output
    // configured to something else -- saw N previews that were all the wrong one of them.
    val outputSize = outputSizeOf(rawAssignment, outputKind)

    val isLive = effectiveMode != Presenting.NONE && showsContent
    val borderColor = previewBorderColor(isLive)

    val displayModeChipLabel = displayModeLabel(profile.displayMode)

    // Session-only, and deliberately not persisted: collapsing is something an operator does to get
    // a long sidebar out of the way for a moment, not a property of the output. Nothing here is
    // needed by the real presenter window, which main.kt drives on its own -- a collapsed preview
    // stops drawing and nothing else changes.
    var expanded by remember { mutableStateOf(true) }

    Column(modifier = modifier) {
        PreviewHeader(
            modeLabel = if (showMode) displayModeChipLabel else "",
            outputLabel = label,
            expanded = expanded || !collapsible,
            collapsible = collapsible,
            onToggle = { expanded = !expanded },
            profiles = appSettings.projectionSettings.outputProfiles,
            activeProfileId = rawAssignment.activeProfileId,
            onPickProfile = { pickedId -> onSettingsChange(withPreviewProfile(outputKind, screenIndex, pickedId)) },
        )

        if (expanded || !collapsible) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(outputSize.aspectRatio)
                .clip(AppShape(6.dp))
                .border(1.dp, borderColor, AppShape(6.dp))
        ) {
        val primaryRole = rawAssignment.primaryOutputRole

        // ── Stage Monitor: dedicated presenter-confidence layout, not the normal presenter ──
        if (profile.displayMode == Constants.DISPLAY_MODE_STAGE_MONITOR) {
            PreviewStageMonitor(presenterManager, profile, outputSettings, outputSize, effectiveMode, transposeSteps)
        } else
        // ── Scaled presenter content (all modes except WEBSITE) ───────────────
        // JavaFX/Swing heavyweight components cannot be scaled by Compose layout,
        // so WEBSITE is handled separately below at native size.
        if (effectiveMode != Presenting.WEBSITE) {
            ScaledPresenterContent(output = outputSize) {
                PresenterScreen(
                    appSettings = outputSettings,
                    outputRole = primaryRole,
                    isLowerThird = isLowerThird,
                    showBackground = showsBackground,
                ) {
                    val qaUrl = "${qaDisplayUrl.ifEmpty { serverUrl }}/qa"
                    if (effectiveMode != Presenting.NONE && showsContent) {
                        Crossfade(
                            targetState = effectiveMode,
                            animationSpec = tween(previewCrossfadeMs(outputSettings))
                        ) { mode ->
                        CompositionLocalProvider(
                            LocalLottieBandClock provides presenterManager.lottieBandClock,
                            LocalBandSongLineIndex provides bandSongLineIndex,
                            LocalBandOutgoing provides bandOutgoing,
                        ) {
                        LowerThirdLayout(mode, profile, outputSettings, showsBackground) {
                        PreviewMode(
                            mode, presenterManager, profile, outputSettings, showsBackground, primaryRole, qaUrl,
                            sttManager,
                        )
                        }
                        }
                        }
                    }
                }
            }
        }

        // ── WEBSITE: display live screenshot captured from WebTab's WebView ─
        // A second JFXPanel instance can't be scaled/clipped by Compose.
        // Instead, WebTab pushes a snapshot bitmap every 200ms via PresenterManager
        // so this panel shows a pixel-accurate mirror including scroll position.
        if (profile.displayMode != Constants.DISPLAY_MODE_STAGE_MONITOR && effectiveMode == Presenting.WEBSITE) {
            PreviewWebsiteMirror(presenterManager)
        }

        // "LIVE" badge — only when this screen is showing content
        if (isLive) {
            LiveBadge(Modifier.align(Alignment.TopStart))
        }

        PreviewBadges(
            screenIndex = screenIndex,
            rawAssignment = rawAssignment,
            profile = profile,
            effectiveMode = effectiveMode,
            locks = locks,
            onToggleLock = onToggleLock,
            transposeSteps = transposeSteps,
            onTranspose = onTranspose,
            label = if (showLabel) label else null,
            mediaAudible = mediaViewModel != null && mediaViewModel.isLoaded && mediaViewModel.isPlaying,
        )
        }
        }
    }
}

/**
 * The row above one preview: the output's display mode, and a caret that folds the picture away.
 *
 * The mode chip used to be drawn for Stage Monitor and Lower Third only, so the ordinary full-screen
 * outputs -- the majority of them -- were the ones with nothing written above them. Every mode names
 * itself now, which is also what gives every preview a row to click.
 *
 * [outputLabel] is drawn **only while collapsed**, and against the far edge. Open, the output
 * already names itself in the corner of its own picture and repeating it here would say the same
 * thing twice in one glance; collapsed, that corner is gone and a stack of rows reading "Full
 * Screen" three times over could not be told apart. Pushing it to the trailing edge keeps it in the
 * column the in-picture label sits in, so the name does not jump across the row as a preview folds.
 */
@Composable
private fun PreviewHeader(
    modeLabel: String,
    outputLabel: String,
    expanded: Boolean,
    collapsible: Boolean,
    onToggle: () -> Unit,
    profiles: List<OutputProfile> = emptyList(),
    activeProfileId: String? = null,
    onPickProfile: (String) -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShape(3.dp))
            .then(if (collapsible) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (collapsible) Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) stringResource(Res.string.collapse_preview)
                                 else stringResource(Res.string.expand_preview),
            modifier = Modifier.size(14.dp).rotate(if (expanded) 0f else CARET_CLOSED_DEGREES),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = modeLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp,
        )
        // Quick-swap: which Output Profile this output follows, right where the operator is
        // already watching it live. Only shown once a profile exists to swap to -- an empty menu
        // would just be clutter on every tile -- and only while the tile is open, where there is
        // room for it; the collapsed row is one line reserved for the label below.
        if (expanded && profiles.isNotEmpty()) {
            OutputProfileSwapMenu(profiles = profiles, activeProfileId = activeProfileId, onPick = onPickProfile)
        }
        if (!expanded) {
            Spacer(Modifier.weight(1f))
            Text(
                text = outputLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The header's profile picker: an icon button that opens a menu of every saved [OutputProfile]. */
@Composable
private fun OutputProfileSwapMenu(
    profiles: List<OutputProfile>,
    activeProfileId: String?,
    onPick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        KeyIconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(18.dp),
        ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = stringResource(Res.string.output_profile_swap_menu_tooltip),
                modifier = Modifier.size(14.dp),
                tint = if (activeProfileId != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(Res.string.output_profile_blank),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (activeProfileId == BLANK_OUTPUT_PROFILE_ID) FontWeight.Bold
                        else FontWeight.Normal,
                    )
                },
                onClick = { expanded = false; onPick(BLANK_OUTPUT_PROFILE_ID) },
            )
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = {
                        Text(
                            profile.name.ifBlank { profile.id },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (profile.id == activeProfileId) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = { expanded = false; onPick(profile.id) },
                )
            }
        }
    }
}

/** How far the caret turns when the preview is folded away; the same quarter turn the tray uses. */
private const val CARET_CLOSED_DEGREES = -90f

/**
 * The pulsing LIVE badge.
 *
 * Its own composable, and its own animation, on purpose. The pulse used to be read at the top of
 * [SingleDisplayPreview]'s scope, which invalidated that whole scope — the nested `PresenterScreen`
 * and every source under it — on every animation frame, for every output, whether or not the output
 * was live. With one preview per display, per Browser Source and per NDI output, that was the app
 * re-rendering all of its outputs twice over at 60fps while sitting idle.
 *
 * The alpha is read inside `drawBehind`, so a pulse now costs a redraw of this one badge and no
 * recomposition at all.
 */
@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "live")
    val pulse = transition.animateFloat(
        initialValue = 0.70f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(LIVE_PULSE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_pulse"
    )
    Text(
        text = stringResource(Res.string.live_preview_title),
        color = Color.White,
        fontSize = 10.sp,
        modifier = modifier
            .padding(4.dp)
            .drawBehind {
                drawRoundRect(
                    color = Color.Red.copy(alpha = pulse.value),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
internal fun AnimatedEqualizer() {
    val transition = rememberInfiniteTransition()
    val barHeights = (0..3).map { index ->
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 300 + index * 100,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(14.dp)
    ) {
        barHeights.forEach { heightFraction ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(heightFraction.value)
                    .background(Color(AUDIO_LEVEL_COLOR), AppShape(1.dp))
            )
        }
    }
}

@Composable
private fun MediaPreviewControls(
    isPlaying: Boolean,
    duration: Long,
    currentPosition: Long,
    formatTime: (Long) -> String,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        KeyIconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier.size(32.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (isPlaying) MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        ) {
            Icon(
                painter = painterResource(
                    if (isPlaying) IconRes.drawable.ic_pause else IconRes.drawable.ic_play
                ),
                contentDescription = stringResource(
                    if (isPlaying) Res.string.pause else Res.string.play
                ),
                modifier = Modifier.size(18.dp),
                tint = Color.White
            )
        }

        if (duration > 0) {
            SlimSlider(
                value = currentPosition.toFloat(),
                onValueChange = { onSeekTo(it.toLong()) },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier.weight(1f),
                trailingLabel = formatTime(currentPosition)
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Renders [content] at [output]'s own logical size and scales it down to fill whatever space its
 * parent allocates — keeping all proportions intact.
 *
 * The size must be the same one the surrounding frame is shaped by, or the content is measured
 * against one screen and framed against another.
 */
@Composable
internal fun ScaledPresenterContent(
    output: OutputSize,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val presenterWidth = output.width
                val presenterHeight = output.height

                val scaleX = constraints.maxWidth.toFloat() / presenterWidth
                val scaleY = constraints.maxHeight.toFloat() / presenterHeight
                val scale = minOf(scaleX, scaleY)

                val placeable = measurable.measure(
                    constraints.copy(
                        minWidth = presenterWidth,
                        maxWidth = presenterWidth,
                        minHeight = presenterHeight,
                        maxHeight = presenterHeight
                    )
                )

                val scaledWidth = (presenterWidth * scale).toInt()
                val scaledHeight = (presenterHeight * scale).toInt()

                layout(scaledWidth, scaledHeight) {
                    placeable.placeWithLayer(0, 0) {
                        this.scaleX = scale
                        this.scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                }
            }
    ) {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            content()
        }
    }
}
