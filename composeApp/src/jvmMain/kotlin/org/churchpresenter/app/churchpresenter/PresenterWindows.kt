package org.churchpresenter.app.churchpresenter

import org.churchpresenter.liveoutput.IdentifyScreenOverlay
import org.churchpresenter.liveoutput.PresenterModeContent
import org.churchpresenter.liveoutput.PresenterOutputContent
import org.churchpresenter.liveoutput.PresenterScreen
import org.churchpresenter.liveoutput.PresenterTransitionEffects
import org.churchpresenter.liveoutput.deckLinkOutputCount
import org.churchpresenter.liveoutput.effectiveOutputMode
import org.churchpresenter.liveoutput.hasDeckLinkKeyOutput
import org.churchpresenter.liveoutput.hasScreenKeyOutput
import org.churchpresenter.liveoutput.isDeckLinkKeyOutput
import org.churchpresenter.liveoutput.isDeckLinkPrimaryOutput
import org.churchpresenter.liveoutput.isScreenCrossfadeActive
import org.churchpresenter.liveoutput.isScreenIndexValid
import org.churchpresenter.liveoutput.keyOutputScreenIndex
import org.churchpresenter.liveoutput.modeCrossfadeDuration
import org.churchpresenter.liveoutput.showsOutputBackground
import org.churchpresenter.settings.ResolvedMerge
import io.github.alexzhirkevich.compottie.LottieComposition
import org.churchpresenter.presenter.MergedTile
import org.churchpresenter.canvas.liveMerges
import org.churchpresenter.presenter.mergeHostIndex
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_app_icon
import org.churchpresenter.strings.generated.resources.key_output_title
import org.churchpresenter.strings.generated.resources.presenter_view_title
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import kotlinx.coroutines.CancellationException
import org.churchpresenter.canvas.DeckLinkManager
import org.churchpresenter.sharedui.composables.HideOutputWindowCursor
import org.churchpresenter.sharedui.composables.LocalOutputCursorHidden
import org.churchpresenter.sharedui.composables.hiddenOutputCursor
import org.churchpresenter.liveoutput.DeckLinkComposeOutput
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.utils.OutputKind
import org.churchpresenter.sharedui.utils.outputSizeOf
import org.churchpresenter.sharedui.utils.DevFlags
import org.churchpresenter.sharedui.utils.findScreenIndexByBounds
import org.churchpresenter.media.viewmodel.LocalMediaViewModel
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.liveoutput.OverlayModes
import org.churchpresenter.liveoutput.MessageExpiry
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.liveoutput.shownModeFor
import org.churchpresenter.stt.STTManager
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.profileFor
import org.churchpresenter.settings.resolvedFor
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val NANOS_PER_MILLI = 1_000_000

@Composable
internal fun PresenterWindows(
    screens: Array<GraphicsDevice>,
    presenterManager: PresenterManager,
    mediaViewModel: MediaViewModel,
    appSettings: AppSettings,
    identifyingScreen: Boolean,
    serverUrl: String = "",
    qaDisplayUrl: String = "",
    sttManager: STTManager,
    defaultScreenDevice: () -> GraphicsDevice? = {
        GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
    },
    /** Opens each output's window; the app's is [awtOutputWindow], a test draws the content in place. */
    window: OutputWindowHost = awtOutputWindow,
) {
    val showPresenterWindow by presenterManager.showPresenterWindow
    val slideContent by presenterManager.slideContent
    val screenLocks by presenterManager.screenLocks
    val selectedVerses by presenterManager.selectedVerses
    val displayedVerses by presenterManager.displayedVerses
    val lyricSection by presenterManager.lyricSection
    val lyricSectionVersion by presenterManager.lyricSectionVersion
    val selectedImagePath by presenterManager.selectedImagePath
    val selectedSlide by presenterManager.selectedSlide
    val animationType by presenterManager.animationType
    val transitionDuration by presenterManager.transitionDuration
    val announcementText by presenterManager.announcementText
    val clearAnnouncementOnFinish = {
        presenterManager.setAnnouncementText("")
        presenterManager.setDisplayedAnnouncementText("")
        presenterManager.overlayFinished(
            Presenting.ANNOUNCEMENTS,
            appSettings.projectionSettings.overlayEndClearsDisplay,
        )
    }

    val proj = appSettings.projectionSettings

    // The full-screen outputs below hide the mouse pointer while this is on (#663). The windowed dev
    // fallback does not: it is a window on the operator's own screen, not a projector.
    val hideCursor = appSettings.projectionSettings.hideCursorOnOutputs

    val lottieComposition = rememberPresenterDrivers(presenterManager, appSettings)
    // Preview's own drivers, while preview mode is on: what is cued fades and plays there as it
    // would on air.
    val previewBus = presenterManager.previewBus
    SideEffect { previewBus.setEnabled(proj.previewModeEnabled) }
    if (previewBus.enabled.value) rememberPresenterDrivers(previewBus.manager, appSettings)

    val env = OutputEnvironment(
        presenterManager, mediaViewModel, sttManager, serverUrl, qaDisplayUrl, lottieComposition,
        clearAnnouncementOnFinish,
    )

    val presenterOutputContent: @Composable (
        screenAssignment: ScreenAssignment,
        effectiveMode: Presenting,
        screenNumber: Int?
    ) -> Unit = { screenAssignment, effectiveMode, screenNumber ->
        PresenterOutputContent(
            screenAssignment, effectiveMode, screenNumber, presenterManager, appSettings,
            mediaViewModel, sttManager, serverUrl, qaDisplayUrl, identifyingScreen,
            lottieComposition, clearAnnouncementOnFinish,
        )
    }

    val availableScreens = nonPrimaryIndices(screens.toList(), defaultScreenDevice())
    // A monitor marked "Don't use" still counts as a slot, but is never handed to one by position.
    val screenKeys = screens.map { it.defaultConfiguration.bounds.asDisplayRect().key }
    val positionalScreens = usableScreenIndices(availableScreens, screenKeys, proj.unusedScreens)
    // Profiles that merge their outputs into one picture -- see OutputMerge.kt.
    val merges = remember(proj) { proj.liveMerges() }

    val deckLinkDeviceCount = deckLinkOutputCount(DeckLinkManager.isAvailable()) { DeckLinkManager.listDevices().size }
    val windowCount = presenterWindowCount(availableScreens.size, deckLinkDeviceCount)
    val devWindowedFallback = isDevWindowedFallback(
        BuildConfig.IS_RELEASE, DevFlags.forceDevWindow, windowCount,
    )
    val devFallbackCount = devFallbackWindowCount(devWindowedFallback, proj.devWindowCount)
    for (i in 0 until (windowCount + devFallbackCount)) {
        val isFallback = isFallbackWindowSlot(devWindowedFallback, i, windowCount)
        val slotIndex = if (isFallback) fallbackSlotIndex(i, windowCount) else i
        val screenAssignment = proj.getAssignment(slotIndex)
        // The profile this output is assigned to -- everything about how it looks and what it
        // shows, used below wherever this file itself (not `PresenterOutputContent`, which
        // resolves its own) needs to know the output's display mode or background switches.
        val profile = proj.profileFor(screenAssignment) ?: OutputProfile()
        // What this output draws with, as PresenterOutputContent resolves it for the plain window: the
        // key and DeckLink outputs below render PresenterModeContent themselves, so they resolve here.
        val outputSettings = remember(appSettings, profile) { appSettings.resolvedFor(profile) }
        val outputKey = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_SCREEN, slotIndex)
        val slot = OutputSlot(
            index = i,
            assignment = screenAssignment,
            profile = profile,
            outputSettings = outputSettings,
            crossfadeMs = modeCrossfadeDuration(outputSettings.bibleSettings, outputSettings.songSettings),
            outputKey = outputKey,
            merge = merges[outputKey],
            // Every tile of one picture shows what its first output shows, lock and all.
            effectiveMode = presenterManager.screenSlotMode(profile, screenLocks, merges, slotIndex, slideContent),
            unusedScreens = proj.unusedScreens,
        )

        when {
            isFallback -> DevFallbackWindow(
                slot, slotIndex, showPresenterWindow, identifyingScreen, presenterManager, presenterOutputContent,
                window,
            )
            isDeckLinkPrimaryOutput(screenAssignment) ->
                DeckLinkOutputs(slot, screens, showPresenterWindow, hideCursor, env, window)
            else -> ScreenOutputs(
                slot, screens, positionalScreens.getOrNull(i), showPresenterWindow, hideCursor, env,
                presenterOutputContent, window,
            )
        }
    }
}

/**
 * What moves [presenterManager]'s content once it is set: the transitions from selected to
 * displayed, and the lower third's playback. Returns the lower third's parsed composition, which
 * the outputs draw.
 */
@Composable
internal fun rememberPresenterDrivers(
    presenterManager: PresenterManager,
    appSettings: AppSettings,
): LottieComposition? {
    val lottieJsonContent by presenterManager.lottieJsonContent
    PresenterTransitionEffects(presenterManager, appSettings)
    MessageExpiry(presenterManager)
    val lottieComposition by rememberLottieComposition(lottieJsonContent) {
        LottieCompositionSpec.JsonString(lottieJsonContent)
    }
    LottiePlaybackEffect(
        presenterManager = presenterManager,
        durationFrames = lottieComposition?.durationFrames,
        frameRate = lottieComposition?.frameRate,
        pauseAtFrame = presenterManager.lottiePauseAtFrame.value,
        pauseFrame = presenterManager.lottiePauseFrame.value,
        pauseDurationMs = presenterManager.lottiePauseDurationMs.value,
        trigger = presenterManager.lottieTrigger.value,
        overlayEndClearsDisplay = appSettings.projectionSettings.overlayEndClearsDisplay,
    )
    return lottieComposition
}

@Composable
internal fun LottiePlaybackEffect(
    presenterManager: PresenterManager,
    durationFrames: Float?,
    frameRate: Float?,
    pauseAtFrame: Boolean,
    pauseFrame: Float,
    pauseDurationMs: Long,
    trigger: Int,
    /** What the lower third finishing does -- see [PresenterManager.overlayFinished]. */
    overlayEndClearsDisplay: Boolean = true,
) {
    LaunchedEffect(durationFrames, frameRate, pauseAtFrame, pauseFrame, pauseDurationMs, trigger) {
        try {
            val initialFrameCount = presenterManager.lottieFrameCount.value
            val totalDurMs = when {
                durationFrames != null && frameRate != null ->
                    lottieCompositionDurationMs(durationFrames, frameRate)
                initialFrameCount != null ->
                    lottiePrerenderDurationMs(initialFrameCount, presenterManager.lottiePrerenderFps.value)
                else -> return@LaunchedEffect
            }
            val hasPause = lottieHasPause(pauseAtFrame, pauseFrame)
            val pauseAtMs = lottiePauseAtMs(totalDurMs, pauseFrame, hasPause)
            val grandTotalMs = lottieGrandTotalMs(totalDurMs, hasPause, pauseDurationMs)

            val hold = if (hasPause) LottieHold(pauseFrame, pauseAtMs, pauseDurationMs) else null

            fun progressAt(elapsedMs: Long): Float = lottieProgressAt(elapsedMs, totalDurMs, hold)

            val startNanos = withFrameNanos { it }
            var elapsedMs = 0L
            while (true) {
                val frameCount = presenterManager.lottieFrameCount.value
                val progress = progressAt(elapsedMs)
                if (frameCount != null) {
                    presenterManager.setLottieCurrentFrameIndex(lottieFrameIndexFor(progress, frameCount))
                } else {
                    presenterManager.setLottieProgress(progress)
                }
                if (elapsedMs >= grandTotalMs) break
                val nowNanos = withFrameNanos { it }
                elapsedMs = ((nowNanos - startNanos) / NANOS_PER_MILLI).coerceAtMost(grandTotalMs)
            }
            val finalFrameCount = presenterManager.lottieFrameCount.value
            if (finalFrameCount != null) {
                presenterManager.setLottieCurrentFrameIndex(finalFrameCount - 1)
            } else {
                presenterManager.setLottieProgress(1f)
            }
            presenterManager.overlayFinished(Presenting.LOWER_THIRD, overlayEndClearsDisplay)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Reports whatever escapes the playback loop and throws it on: nothing is swallowed,
            // so narrowing would only lose the report.
            CrashReporter.reportException(e, "Lottie playback LaunchedEffect")
            throw e
        }
    }
}

/** What every output's mode content draws with, beyond its own profile and settings. */
internal data class OutputEnvironment(
    val presenterManager: PresenterManager,
    val mediaViewModel: MediaViewModel,
    val sttManager: STTManager,
    val serverUrl: String,
    val qaDisplayUrl: String,
    val lottieComposition: LottieComposition?,
    val clearAnnouncementOnFinish: () -> Unit,
)

/**
 * Crossfades [content] between modes as the real outputs do: only when the Bible or song settings
 * crossfade and neither side is NONE, over [crossfadeMs]; otherwise it cuts. The overlays are drawn
 * by [content] too, over it.
 */
@Composable
private fun CrossfadedOutput(
    presenterManager: PresenterManager,
    profile: OutputProfile,
    effectiveMode: Presenting,
    crossfadeMs: Int,
    outputSettings: AppSettings,
    content: @Composable (Presenting) -> Unit,
) {
    var prevEffectiveMode by remember { mutableStateOf(effectiveMode) }
    val screenCrossfadeActive = isScreenCrossfadeActive(
        outputSettings.bibleSettings, outputSettings.songSettings, effectiveMode, prevEffectiveMode,
    )
    if (effectiveMode != prevEffectiveMode) prevEffectiveMode = effectiveMode
    Crossfade(
        targetState = effectiveMode,
        animationSpec = if (screenCrossfadeActive) tween(crossfadeMs) else snap()
    ) { mode -> content(mode) }
    OverlayModes(presenterManager, profile, effectiveMode, content)
}

/** One output's [mode], drawn with its background forced on as the key and DeckLink paths always have. */
@Composable
private fun OutputModeContent(
    mode: Presenting,
    profile: OutputProfile,
    outputSettings: AppSettings,
    outputRole: String,
    showBg: Boolean,
    env: OutputEnvironment,
) {
    PresenterModeContent(
        mode = mode,
        profile = profile,
        presenterManager = env.presenterManager,
        appSettings = outputSettings,
        mediaViewModel = env.mediaViewModel,
        sttManager = env.sttManager,
        serverUrl = env.serverUrl,
        qaDisplayUrl = env.qaDisplayUrl,
        lottieComposition = env.lottieComposition,
        clearAnnouncementOnFinish = env.clearAnnouncementOnFinish,
        outputRole = outputRole,
        showBg = showBg,
        showBackgroundOverride = true,
    )
}

/** What screen slot [slotIndex] shows: its picture's first output's lock, else [profile]'s live mode. */
private fun PresenterManager.screenSlotMode(
    profile: OutputProfile,
    screenLocks: Map<Int, Presenting>,
    merges: Map<String, ResolvedMerge>,
    slotIndex: Int,
    slideContent: Presenting,
): Presenting = shownModeFor(
    profile,
    effectiveOutputMode(
        screenLocks, mergeHostIndex(merges, Constants.PREVIEW_OUTPUT_SCREEN, slotIndex), slideContent,
    ),
)

/** One screen slot's output: its assignment, what it draws with, and the mode it shows. */
private data class OutputSlot(
    val index: Int,
    val assignment: ScreenAssignment,
    val profile: OutputProfile,
    val outputSettings: AppSettings,
    val crossfadeMs: Int,
    val outputKey: String,
    val merge: ResolvedMerge?,
    val effectiveMode: Presenting,
    /** The monitors marked "Don't use" -- no window of this slot's ever opens on one. */
    val unusedScreens: List<String> = emptyList(),
)

/** The dev build's stand-in for an output: an ordinary window on the operator's own screen. */
@Composable
private fun DevFallbackWindow(
    slot: OutputSlot,
    slotIndex: Int,
    showPresenterWindow: Boolean,
    identifyingScreen: Boolean,
    presenterManager: PresenterManager,
    presenterOutputContent: @Composable (ScreenAssignment, Presenting, Int?) -> Unit,
    window: OutputWindowHost,
) {
    val fallbackIndex = slotIndex
    // Keyed on the size too: without it, changing an output's resolution in settings
    // did nothing until the app was restarted.
    val devSize = outputSizeOf(slot.assignment, OutputKind.SCREEN)
    val fallbackWindowState = remember(fallbackIndex, devSize) {
        val (windowWidth, windowHeight) = devFallbackWindowSizeDp(devSize.width, devSize.height)
        WindowState(
            width = windowWidth.dp,
            height = windowHeight.dp,
            position = WindowPosition(
                x = devFallbackWindowOffsetDp(fallbackIndex).dp,
                y = devFallbackWindowOffsetDp(fallbackIndex).dp,
            ),
        )
    }
    val spec = OutputWindowSpec(
        visible = showPresenterWindow,
        title = stringResource(Res.string.presenter_view_title, fallbackIndex + 1),
        onClose = { presenterManager.setShowPresenterWindow(false) },
        state = fallbackWindowState,
        undecorated = false,
        resizable = true,
        alwaysOnTop = presenterManager.devWindowAlwaysOnTop.value,
        hideCursor = null,
    )
    window(spec) {
        // A merged dev window shows its own tile of the picture; its number is drawn
        // over the tile rather than inside the picture, where it would land on one tile.
        MergedTile(slot.merge, slot.outputKey) {
            val number = (fallbackIndex + 1).takeIf { slot.merge == null }
            presenterOutputContent(slot.assignment, slot.effectiveMode, number)
        }
        if (slot.merge != null && identifyingScreen) IdentifyScreenOverlay(fallbackIndex + 1)
    }
}

/** An output on a DeckLink card: its fill, its key on a second port, and a key on a screen. */
@Composable
private fun DeckLinkOutputs(
    slot: OutputSlot,
    screens: Array<GraphicsDevice>,
    showPresenterWindow: Boolean,
    hideCursor: Boolean,
    env: OutputEnvironment,
    window: OutputWindowHost,
) {
    if (showPresenterWindow && slot.assignment.targetDisplay >= 0) {
        val deckLinkRole = slot.assignment.primaryOutputRole
        DeckLinkComposeOutput(
            deviceIndex = slot.assignment.targetDisplay,
            outputRole = deckLinkRole,
            appSettings = slot.outputSettings,
            mediaViewModel = env.mediaViewModel,
            isLowerThird = slot.profile.isLowerThird,
            merge = slot.merge,
            mergeOutput = slot.outputKey,
        ) {
            CrossfadedOutput(
                env.presenterManager, slot.profile, slot.effectiveMode, slot.crossfadeMs, slot.outputSettings,
            ) { mode ->
                OutputModeContent(
                    mode, slot.profile, slot.outputSettings, deckLinkRole,
                    showsOutputBackground(slot.profile), env,
                )
            }
        }
    }

    if (showPresenterWindow && hasDeckLinkKeyOutput(slot.assignment)) {
        DeckLinkComposeOutput(
            deviceIndex = slot.assignment.keyTargetDisplay,
            outputRole = Constants.OUTPUT_ROLE_KEY,
            appSettings = slot.outputSettings,
            mediaViewModel = env.mediaViewModel,
            isLowerThird = slot.profile.isLowerThird,
        ) {
            CrossfadedOutput(
                env.presenterManager, slot.profile, slot.effectiveMode, slot.crossfadeMs, slot.outputSettings,
            ) { mode ->
                OutputModeContent(
                    mode, slot.profile, slot.outputSettings, Constants.OUTPUT_ROLE_KEY,
                    showsOutputBackground(slot.profile), env,
                )
            }
        }
    }

    if (showPresenterWindow && hasScreenKeyOutput(slot.assignment)) {
        KeyOutputWindow(slot, screens, visible = true, hideCursor, env, clearOnEscape = false, window)
    }
}

/**
 * An output on a display: its window -- one across every display of a merge -- and its key, on
 * another display or a DeckLink port. [positionalFallback] is the display it takes when its saved
 * one cannot be found.
 */
@Composable
private fun ScreenOutputs(
    slot: OutputSlot,
    screens: Array<GraphicsDevice>,
    positionalFallback: Int?,
    showPresenterWindow: Boolean,
    hideCursor: Boolean,
    env: OutputEnvironment,
    presenterOutputContent: @Composable (ScreenAssignment, Presenting, Int?) -> Unit,
    window: OutputWindowHost,
) {
    val targetScreenIndex = if (hasNoPrimaryTarget(slot.assignment)) null
        else primaryOutputScreenIndex(
        matchedByBounds = findScreenIndexByBounds(
            screens,
            slot.assignment.targetBoundsX,
            slot.assignment.targetBoundsY,
            slot.assignment.targetBoundsW,
            slot.assignment.targetBoundsH
        ),
        savedDisplay = slot.assignment.targetDisplay,
        screenCount = screens.size,
        positionalFallback = positionalFallback,
        )

    // A merge of real displays opens one window, on its first display, across them all.
    val attached = screens.map { it.defaultConfiguration.bounds.asDisplayRect() }
    // A monitor marked "Don't use" never gets a window -- nor, by the return, this output's key.
    val screenKeys = attached.map { it.key }
    val b = screenWindowRect(slot.merge, slot.outputKey, attached) {
        targetScreenIndex
            ?.takeIf { isScreenIndexValid(it, screens.size) }
            ?.takeUnless { isUnusedScreenIndex(it, screenKeys, slot.unusedScreens) }
            ?.let { attached[it] }
    } ?: return

    val showBg = showsOutputBackground(slot.profile)

    val primaryRole = slot.assignment.primaryOutputRole

    val windowState = remember(slot.index) {
        WindowState(
            placement = WindowPlacement.Floating,
            position = WindowPosition(b.x.dp, b.y.dp),
            width = b.width.dp,
            height = b.height.dp
        )
    }

    // Keyed on the rectangle, not the display's index: a merge, an unmerge or a display
    // moving all change where the window belongs without changing which slot it is.
    LaunchedEffect(b) {
        windowState.position = WindowPosition(b.x.dp, b.y.dp)
        windowState.size = DpSize(b.width.dp, b.height.dp)
    }

    val presenterTitle = stringResource(Res.string.presenter_view_title, slot.index + 1)
    val spec = OutputWindowSpec(
        visible = showPresenterWindow,
        title = presenterTitle,
        onClose = { env.presenterManager.setShowPresenterWindow(false) },
        state = windowState,
        hideCursor = hideCursor,
    )
    window(spec) {
        CompositionLocalProvider(LocalOutputCursorHidden provides hideCursor) {
            Box(modifier = Modifier.fillMaxSize().hiddenOutputCursor(hideCursor)) {
                presenterOutputContent(slot.assignment, slot.effectiveMode, slot.index + 1)
            }
        }
    }

    if (slot.assignment.hasKeyOutput && !isDeckLinkKeyOutput(slot.assignment)) {
        KeyOutputWindow(slot, screens, visible = showPresenterWindow, hideCursor, env, clearOnEscape = true, window)
    }

    if (!isDeckLinkPrimaryOutput(slot.assignment) && hasDeckLinkKeyOutput(slot.assignment)) {
        if (showPresenterWindow) {
            DeckLinkComposeOutput(
                deviceIndex = slot.assignment.keyTargetDisplay,
                outputRole = Constants.OUTPUT_ROLE_KEY,
                appSettings = slot.outputSettings,
                mediaViewModel = env.mediaViewModel,
                isLowerThird = slot.profile.isLowerThird,
            ) {
                CrossfadedOutput(
                    env.presenterManager, slot.profile, slot.effectiveMode, slot.crossfadeMs, slot.outputSettings,
                ) { mode ->
                    OutputModeContent(mode, slot.profile, slot.outputSettings, primaryRole, showBg, env)
                }
            }
        }
    }
}

/**
 * An output's key on its own display: a borderless window over the key's screen, white on black.
 * With [clearOnEscape], Escape there pauses media and clears the output as it does on the fill.
 */
@Composable
private fun KeyOutputWindow(
    slot: OutputSlot,
    screens: Array<GraphicsDevice>,
    visible: Boolean,
    hideCursor: Boolean,
    env: OutputEnvironment,
    clearOnEscape: Boolean,
    window: OutputWindowHost,
) {
    val keyScreenIndex = keyOutputScreenIndex(
        findScreenIndexByBounds(
            screens,
            slot.assignment.keyTargetBoundsX,
            slot.assignment.keyTargetBoundsY,
            slot.assignment.keyTargetBoundsW,
            slot.assignment.keyTargetBoundsH
        ),
        slot.assignment.keyTargetDisplay,
    )
    if (!isScreenIndexValid(keyScreenIndex, screens.size)) return
    val screenKeys = screens.map { it.defaultConfiguration.bounds.asDisplayRect().key }
    if (isUnusedScreenIndex(keyScreenIndex, screenKeys, slot.unusedScreens)) return
    val keyWindowState = remember(slot.index, keyScreenIndex) {
        val b = screens[keyScreenIndex].defaultConfiguration.bounds
        WindowState(
            placement = WindowPlacement.Floating,
            position = WindowPosition(b.x.dp, b.y.dp),
            width = b.width.dp,
            height = b.height.dp
        )
    }
    val spec = OutputWindowSpec(
        visible = visible,
        title = stringResource(Res.string.key_output_title, slot.index + 1),
        onClose = { env.presenterManager.setShowPresenterWindow(false) },
        state = keyWindowState,
        hideCursor = hideCursor,
    )
    window(spec) {
        CompositionLocalProvider(
            LocalMediaViewModel provides env.mediaViewModel,
            LocalOutputCursorHidden provides hideCursor,
        ) {
            PresenterScreen(
                modifier = Modifier.fillMaxSize().hiddenOutputCursor(hideCursor),
                appSettings = slot.outputSettings,
                outputRole = Constants.OUTPUT_ROLE_KEY
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onPreviewKeyEvent { keyEvent ->
                            if (clearOnEscape && keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                                env.mediaViewModel.pause()
                                env.presenterManager.requestClearDisplay()
                                true
                            } else false
                        }
                ) {
                    CrossfadedOutput(
                    env.presenterManager, slot.profile, slot.effectiveMode, slot.crossfadeMs, slot.outputSettings,
                ) { mode ->
                        OutputModeContent(
                            mode, slot.profile, slot.outputSettings, Constants.OUTPUT_ROLE_KEY,
                            showsOutputBackground(slot.profile), env,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One output window as [PresenterWindows] asks for it. [hideCursor] is whether the window hides the
 * mouse pointer; null leaves the pointer alone, as the dev build's ordinary window does.
 */
internal data class OutputWindowSpec(
    val visible: Boolean,
    val title: String,
    val onClose: () -> Unit,
    val state: WindowState,
    val hideCursor: Boolean?,
    val undecorated: Boolean = true,
    val resizable: Boolean = false,
    val alwaysOnTop: Boolean = true,
)

/** Opens the window [OutputWindowSpec] describes and draws [content] in it. */
internal typealias OutputWindowHost = @Composable (spec: OutputWindowSpec, content: @Composable () -> Unit) -> Unit

/** The app's output windows: real AWT windows with the app icon. */
internal val awtOutputWindow: OutputWindowHost = { spec, content ->
    Window(
        visible = spec.visible,
        title = spec.title,
        icon = painterResource(IconRes.drawable.ic_app_icon),
        onCloseRequest = spec.onClose,
        state = spec.state,
        undecorated = spec.undecorated,
        resizable = spec.resizable,
        alwaysOnTop = spec.alwaysOnTop,
    ) {
        spec.hideCursor?.let { HideOutputWindowCursor(window, it) }
        content()
    }
}
