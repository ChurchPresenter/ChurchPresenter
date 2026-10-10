package org.churchpresenter.liveoutput.preview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.churchpresenter.canvas.DeckLinkManager
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.testTag
import org.churchpresenter.strings.generated.resources.preview_layout_done
import org.churchpresenter.strings.generated.resources.preview_layout_edit
import org.churchpresenter.sharedui.utils.rememberScreenDevices
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import org.churchpresenter.settings.activeLayout
import org.churchpresenter.settings.updateLayout
import org.churchpresenter.theme.AppShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.liveoutput.PresenterScreen
import org.churchpresenter.liveoutput.showsOutputBackground
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.profileFor
import org.churchpresenter.settings.resolvedFor
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.utils.DevFlags
import org.churchpresenter.presenter.showsContentFor
import org.churchpresenter.sharedui.utils.OutputKind
import org.churchpresenter.sharedui.utils.OutputSize
import org.churchpresenter.sharedui.utils.outputSizeOf
import org.churchpresenter.media.viewmodel.LocalMediaViewModel
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.liveoutput.shownModeFor
import org.churchpresenter.stt.STTManager
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.media.viewmodel.formatMediaTime

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
    /**
     * Whether this is a release build: a developer build with no real output previews the dev
     * fallback window(s), as the app opens them. The app passes its `BuildConfig.IS_RELEASE`.
     */
    isRelease: Boolean = false,
) {
    val proj = appSettings.projectionSettings
    val deckLinkCount = remember { if (DeckLinkManager.isAvailable()) DeckLinkManager.listDevices().size else 0 }
    val realWindowCount = ((rememberScreenDevices().size - 1) + deckLinkCount).coerceAtLeast(0)
    // Mirror main.kt's dev-fallback: with no real output, preview the dev fallback window(s) so the
    // right-pane preview matches what's actually opened. devWindowCount lets several be previewed.
    val devWindowedFallback = (!isRelease || DevFlags.forceDevWindow) && realWindowCount == 0
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
        val slideContent by presenterManager.slideContent
        val transportUseful = mediaViewModel != null && mediaTransportUseful(
            isLoaded = mediaViewModel.isLoaded,
            isPlaying = mediaViewModel.isPlaying,
            slideContent = slideContent,
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

/** Test handle on a tile whose output is showing something -- the one framed red. */
internal const val LIVE_TILE_TAG = "live_preview_tile"

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
internal fun mediaTransportUseful(isLoaded: Boolean, isPlaying: Boolean, slideContent: Presenting): Boolean =
    isLoaded && (slideContent == Presenting.MEDIA || isPlaying)

@Composable
internal fun SingleDisplayPreview(
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
    /** The bus this tile shows while preview mode is on, which frames it; null while it is off. */
    busRole: BusRole? = null,
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
    val slideContent by presenterManager.slideContent
    val effectiveMode = presenterManager.shownModeFor(profile, locks[screenIndex] ?: slideContent)
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

    val isLive = previewShowsSomething(presenterManager, effectiveMode, profile)
    val borderColor = previewBorderColor(isLive, busRole)

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
                .border(if (busRole != null) BUS_BORDER_WIDTH else 1.dp, borderColor, AppShape(6.dp))
                // Live is the red frame alone; this says so to a test.
                .then(if (isLive) Modifier.testTag(LIVE_TILE_TAG) else Modifier)
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
                    PreviewModeLayers(
                        presenterManager, effectiveMode, showsContent, profile, outputSettings,
                        showsBackground, primaryRole, qaUrl, sttManager,
                    )
                }
            }
        }

        // ── WEBSITE: display live screenshot captured from WebTab's WebView ─
        // A second JFXPanel instance can't be scaled/clipped by Compose.
        // Instead, WebTab pushes a snapshot bitmap every 200ms via PresenterManager
        // so this panel shows a pixel-accurate mirror including scroll position.
        if (profile.displayMode != Constants.DISPLAY_MODE_STAGE_MONITOR && effectiveMode == Presenting.WEBSITE) {
            PreviewWebsiteMirror(presenterManager)
            // What goes up over the page, which its snapshot does not carry: drawn alone, with no
            // screen of its own to hide the page under it.
            ScaledPresenterContent(output = outputSize) {
                PreviewModeLayers(
                    presenterManager, effectiveMode, showsContent, profile, outputSettings,
                    showsBackground, primaryRole, "", sttManager, drawsSlide = false,
                )
            }
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
            lockable = busRole != BusRole.PREVIEW,
        )
        }
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
