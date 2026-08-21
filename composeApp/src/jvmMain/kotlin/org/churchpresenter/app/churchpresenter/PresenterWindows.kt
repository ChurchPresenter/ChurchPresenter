package org.churchpresenter.app.churchpresenter

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_app_icon
import churchpresenter.composeapp.generated.resources.key_output_title
import churchpresenter.composeapp.generated.resources.presenter_view_title
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import kotlinx.coroutines.CancellationException
import org.churchpresenter.app.churchpresenter.composables.DeckLinkManager
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.presenter.DeckLinkComposeOutput
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.app.churchpresenter.utils.DevFlags
import org.churchpresenter.app.churchpresenter.utils.findScreenIndexByBounds
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
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
) {
    val showPresenterWindow by presenterManager.showPresenterWindow
    val presentingMode by presenterManager.presentingMode
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
        presenterManager.requestClearDisplay()
    }
    val lottieJsonContent by presenterManager.lottieJsonContent
    val lottiePauseAtFrame by presenterManager.lottiePauseAtFrame
    val lottiePauseFrame by presenterManager.lottiePauseFrame
    val lottiePauseDurationMs by presenterManager.lottiePauseDurationMs
    val lottieTrigger by presenterManager.lottieTrigger

    val proj = appSettings.projectionSettings

    val modeCrossfadeDuration = modeCrossfadeDuration(appSettings.bibleSettings, appSettings.songSettings)

    PresenterTransitionEffects(presenterManager, appSettings)

    val lottieComposition by rememberLottieComposition(key = lottieJsonContent) {
        LottieCompositionSpec.JsonString(lottieJsonContent)
    }
    LottiePlaybackEffect(
        presenterManager = presenterManager,
        durationFrames = lottieComposition?.durationFrames,
        frameRate = lottieComposition?.frameRate,
        pauseAtFrame = lottiePauseAtFrame,
        pauseFrame = lottiePauseFrame,
        pauseDurationMs = lottiePauseDurationMs,
        trigger = lottieTrigger,
    )

    val presenterOutputContent: @Composable (screenAssignment: ScreenAssignment, effectiveMode: Presenting, screenNumber: Int?) -> Unit = { screenAssignment, effectiveMode, screenNumber ->
        PresenterOutputContent(
            screenAssignment, effectiveMode, screenNumber, presenterManager, appSettings,
            mediaViewModel, sttManager, serverUrl, qaDisplayUrl, identifyingScreen,
            lottieComposition, clearAnnouncementOnFinish,
        )
    }

    val availableScreens = nonPrimaryIndices(screens.toList(), defaultScreenDevice())

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
        val effectiveMode = effectiveOutputMode(screenLocks, slotIndex, presentingMode)

        when {
            isFallback -> {
                val fallbackIndex = slotIndex
                val fallbackWindowState = remember(fallbackIndex) {
                    WindowState(
                        width = 960.dp,
                        height = 540.dp,
                        position = WindowPosition(
                            x = devFallbackWindowOffsetDp(fallbackIndex).dp,
                            y = devFallbackWindowOffsetDp(fallbackIndex).dp,
                        ),
                    )
                }
                Window(
                    visible = showPresenterWindow,
                    title = stringResource(Res.string.presenter_view_title, fallbackIndex + 1),
                    icon = painterResource(Res.drawable.ic_app_icon),
                    onCloseRequest = { presenterManager.setShowPresenterWindow(false) },
                    state = fallbackWindowState,
                    undecorated = false,
                    resizable = true,
                    alwaysOnTop = presenterManager.devWindowAlwaysOnTop.value,
                ) {
                    presenterOutputContent(screenAssignment, effectiveMode, fallbackIndex + 1)
                }
            }

            isDeckLinkPrimaryOutput(screenAssignment) -> {
                if (showPresenterWindow && screenAssignment.targetDisplay >= 0) {
                    val deckLinkRole = screenAssignment.primaryOutputRole
                    DeckLinkComposeOutput(
                        deviceIndex = screenAssignment.targetDisplay,
                        outputRole = deckLinkRole,
                        appSettings = appSettings,
                        mediaViewModel = mediaViewModel,
                        isLowerThird = screenAssignment.isLowerThird,
                    ) {
                        var prevEffectiveMode by remember { mutableStateOf(effectiveMode) }
                        val screenCrossfadeActive = isScreenCrossfadeActive(
                            appSettings.bibleSettings, appSettings.songSettings, effectiveMode, prevEffectiveMode,
                        )
                        if (effectiveMode != prevEffectiveMode) prevEffectiveMode = effectiveMode
                        Crossfade(
                            targetState = effectiveMode,
                            animationSpec = if (screenCrossfadeActive) tween(modeCrossfadeDuration) else snap()
                        ) { mode ->
                        PresenterModeContent(
                            mode = mode,
                            screenAssignment = screenAssignment,
                            presenterManager = presenterManager,
                            appSettings = appSettings,
                            mediaViewModel = mediaViewModel,
                            sttManager = sttManager,
                            serverUrl = serverUrl,
                            qaDisplayUrl = qaDisplayUrl,
                            lottieComposition = lottieComposition,
                            clearAnnouncementOnFinish = clearAnnouncementOnFinish,
                            outputRole = deckLinkRole,
                            showBg = showsOutputBackground(screenAssignment),
                            showBackgroundOverride = true,
                        )
                        }
                    }
                }

                if (showPresenterWindow && hasDeckLinkKeyOutput(screenAssignment)) {
                    DeckLinkComposeOutput(
                        deviceIndex = screenAssignment.keyTargetDisplay,
                        outputRole = Constants.OUTPUT_ROLE_KEY,
                        appSettings = appSettings,
                        mediaViewModel = mediaViewModel,
                        isLowerThird = screenAssignment.isLowerThird,
                    ) {
                        var prevEffectiveMode by remember { mutableStateOf(effectiveMode) }
                        val screenCrossfadeActive = isScreenCrossfadeActive(
                            appSettings.bibleSettings, appSettings.songSettings, effectiveMode, prevEffectiveMode,
                        )
                        if (effectiveMode != prevEffectiveMode) prevEffectiveMode = effectiveMode
                        Crossfade(targetState = effectiveMode, animationSpec = if (screenCrossfadeActive) tween(modeCrossfadeDuration) else snap()) { mode ->
                        PresenterModeContent(
                            mode = mode,
                            screenAssignment = screenAssignment,
                            presenterManager = presenterManager,
                            appSettings = appSettings,
                            mediaViewModel = mediaViewModel,
                            sttManager = sttManager,
                            serverUrl = serverUrl,
                            qaDisplayUrl = qaDisplayUrl,
                            lottieComposition = lottieComposition,
                            clearAnnouncementOnFinish = clearAnnouncementOnFinish,
                            outputRole = Constants.OUTPUT_ROLE_KEY,
                            showBg = showsOutputBackground(screenAssignment),
                            showBackgroundOverride = true,
                        )
                        }
                    }
                }

                if (showPresenterWindow && hasScreenKeyOutput(screenAssignment)) {
                    val keyScreenIndex = keyOutputScreenIndex(
                        findScreenIndexByBounds(
                            screens,
                            screenAssignment.keyTargetBoundsX,
                            screenAssignment.keyTargetBoundsY,
                            screenAssignment.keyTargetBoundsW,
                            screenAssignment.keyTargetBoundsH
                        ),
                        screenAssignment.keyTargetDisplay,
                    )
                    if (isScreenIndexValid(keyScreenIndex, screens.size)) {
                        val keyWindowState = remember(i, keyScreenIndex) {
                            val b = screens[keyScreenIndex].defaultConfiguration.bounds
                            WindowState(
                                placement = WindowPlacement.Floating,
                                position = WindowPosition(b.x.dp, b.y.dp),
                                width = b.width.dp,
                                height = b.height.dp
                            )
                        }

                        Window(
                            visible = true,
                            title = "Key Output ${i + 1}",
                            icon = painterResource(Res.drawable.ic_app_icon),
                            onCloseRequest = { presenterManager.setShowPresenterWindow(false) },
                            state = keyWindowState,
                            undecorated = true,
                            resizable = false,
                            alwaysOnTop = true,
                        ) {
                            CompositionLocalProvider(LocalMediaViewModel provides mediaViewModel) {
                                PresenterScreen(
                                    modifier = Modifier.fillMaxSize(),
                                    appSettings = appSettings,
                                    outputRole = Constants.OUTPUT_ROLE_KEY
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        var prevEffectiveMode by remember { mutableStateOf(effectiveMode) }
                                        val screenCrossfadeActive = isScreenCrossfadeActive(
                                            appSettings.bibleSettings, appSettings.songSettings,
                                            effectiveMode, prevEffectiveMode,
                                        )
                                        if (effectiveMode != prevEffectiveMode) prevEffectiveMode = effectiveMode
                                        Crossfade(targetState = effectiveMode, animationSpec = if (screenCrossfadeActive) tween(modeCrossfadeDuration) else snap()) { mode ->
                        PresenterModeContent(
                            mode = mode,
                            screenAssignment = screenAssignment,
                            presenterManager = presenterManager,
                            appSettings = appSettings,
                            mediaViewModel = mediaViewModel,
                            sttManager = sttManager,
                            serverUrl = serverUrl,
                            qaDisplayUrl = qaDisplayUrl,
                            lottieComposition = lottieComposition,
                            clearAnnouncementOnFinish = clearAnnouncementOnFinish,
                            outputRole = Constants.OUTPUT_ROLE_KEY,
                            showBg = showsOutputBackground(screenAssignment),
                            showBackgroundOverride = true,
                        )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            else -> {

            val targetScreenIndex = if (hasNoPrimaryTarget(screenAssignment)) null
                else primaryOutputScreenIndex(
                matchedByBounds = findScreenIndexByBounds(
                    screens,
                    screenAssignment.targetBoundsX,
                    screenAssignment.targetBoundsY,
                    screenAssignment.targetBoundsW,
                    screenAssignment.targetBoundsH
                ),
                savedDisplay = screenAssignment.targetDisplay,
                screenCount = screens.size,
                positionalFallback = availableScreens.getOrNull(i),
                )

            if (targetScreenIndex == null || !isScreenIndexValid(targetScreenIndex, screens.size)) continue

            val showBg = showsOutputBackground(screenAssignment)

            val primaryRole = screenAssignment.primaryOutputRole

            val windowState = remember(i) {
                val b = screens[targetScreenIndex].defaultConfiguration.bounds
                WindowState(
                    placement = WindowPlacement.Floating,
                    position = WindowPosition(b.x.dp, b.y.dp),
                    width = b.width.dp,
                    height = b.height.dp
                )
            }

            LaunchedEffect(targetScreenIndex) {
                val b = screens[targetScreenIndex].defaultConfiguration.bounds
                windowState.position = WindowPosition(b.x.dp, b.y.dp)
                windowState.size = DpSize(b.width.dp, b.height.dp)
            }

            val presenterTitle = stringResource(Res.string.presenter_view_title, i + 1)
            Window(
                visible = showPresenterWindow,
                title = presenterTitle,
                icon = painterResource(Res.drawable.ic_app_icon),
                onCloseRequest = { presenterManager.setShowPresenterWindow(false) },
                state = windowState,
                undecorated = true,
                resizable = false,
                alwaysOnTop = true,
            ) {
                presenterOutputContent(screenAssignment, effectiveMode, i + 1)
            }

            if (screenAssignment.hasKeyOutput && !isDeckLinkKeyOutput(screenAssignment)) {
                val keyScreenIndex = keyOutputScreenIndex(
                    findScreenIndexByBounds(
                        screens,
                        screenAssignment.keyTargetBoundsX,
                        screenAssignment.keyTargetBoundsY,
                        screenAssignment.keyTargetBoundsW,
                        screenAssignment.keyTargetBoundsH
                    ),
                    screenAssignment.keyTargetDisplay,
                )
                if (isScreenIndexValid(keyScreenIndex, screens.size)) {
                    val keyWindowState = remember(i, keyScreenIndex) {
                        val b = screens[keyScreenIndex].defaultConfiguration.bounds
                        WindowState(
                            placement = WindowPlacement.Floating,
                            position = WindowPosition(b.x.dp, b.y.dp),
                            width = b.width.dp,
                            height = b.height.dp
                        )
                    }

                    val keyOutputTitle = stringResource(Res.string.key_output_title, i + 1)
                    Window(
                        visible = showPresenterWindow,
                        title = keyOutputTitle,
                        icon = painterResource(Res.drawable.ic_app_icon),
                        onCloseRequest = { presenterManager.setShowPresenterWindow(false) },
                        state = keyWindowState,
                        undecorated = true,
                        resizable = false,
                        alwaysOnTop = true,
                    ) {
                        CompositionLocalProvider(LocalMediaViewModel provides mediaViewModel) {
                            PresenterScreen(
                                modifier = Modifier.fillMaxSize(),
                                appSettings = appSettings,
                                outputRole = Constants.OUTPUT_ROLE_KEY
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                                                mediaViewModel.pause()
                                                presenterManager.requestClearDisplay()
                                                true
                                            } else false
                                        }
                                ) {
                                    var prevEffectiveMode by remember { mutableStateOf(effectiveMode) }
                                    val screenCrossfadeActive = isScreenCrossfadeActive(
                                        appSettings.bibleSettings, appSettings.songSettings,
                                        effectiveMode, prevEffectiveMode,
                                    )
                                    if (effectiveMode != prevEffectiveMode) prevEffectiveMode = effectiveMode
                                    Crossfade(targetState = effectiveMode, animationSpec = if (screenCrossfadeActive) tween(modeCrossfadeDuration) else snap()) { mode ->
                        PresenterModeContent(
                            mode = mode,
                            screenAssignment = screenAssignment,
                            presenterManager = presenterManager,
                            appSettings = appSettings,
                            mediaViewModel = mediaViewModel,
                            sttManager = sttManager,
                            serverUrl = serverUrl,
                            qaDisplayUrl = qaDisplayUrl,
                            lottieComposition = lottieComposition,
                            clearAnnouncementOnFinish = clearAnnouncementOnFinish,
                            outputRole = Constants.OUTPUT_ROLE_KEY,
                            showBg = showBg,
                            showBackgroundOverride = true,
                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!isDeckLinkPrimaryOutput(screenAssignment) && hasDeckLinkKeyOutput(screenAssignment)) {
                if (showPresenterWindow) {
                    DeckLinkComposeOutput(
                        deviceIndex = screenAssignment.keyTargetDisplay,
                        outputRole = Constants.OUTPUT_ROLE_KEY,
                        appSettings = appSettings,
                        mediaViewModel = mediaViewModel,
                        isLowerThird = screenAssignment.isLowerThird,
                    ) {
                        var prevEffectiveMode by remember { mutableStateOf(effectiveMode) }
                        val screenCrossfadeActive = isScreenCrossfadeActive(
                            appSettings.bibleSettings, appSettings.songSettings, effectiveMode, prevEffectiveMode,
                        )
                        if (effectiveMode != prevEffectiveMode) prevEffectiveMode = effectiveMode
                        Crossfade(targetState = effectiveMode, animationSpec = if (screenCrossfadeActive) tween(modeCrossfadeDuration) else snap()) { mode ->
                        PresenterModeContent(
                            mode = mode,
                            screenAssignment = screenAssignment,
                            presenterManager = presenterManager,
                            appSettings = appSettings,
                            mediaViewModel = mediaViewModel,
                            sttManager = sttManager,
                            serverUrl = serverUrl,
                            qaDisplayUrl = qaDisplayUrl,
                            lottieComposition = lottieComposition,
                            clearAnnouncementOnFinish = clearAnnouncementOnFinish,
                            outputRole = primaryRole,
                            showBg = showBg,
                            showBackgroundOverride = true,
                        )
                        }
                    }
                }
            }
            }
        }
    }
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

            fun progressAt(elapsedMs: Long): Float = lottieProgressAt(
                elapsedMs, totalDurMs, hasPause, pauseFrame, pauseAtMs, pauseDurationMs,
            )

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
            presenterManager.requestClearDisplay()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CrashReporter.reportException(e, "Lottie playback LaunchedEffect")
            throw e
        }
    }
}
