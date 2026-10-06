package org.churchpresenter.liveoutput

import org.churchpresenter.presenter.LocalBandOutgoing
import org.churchpresenter.presenter.LocalBandSongLineIndex
import org.churchpresenter.presenter.LocalLottieBandClock
import org.churchpresenter.presenter.LowerThirdLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import io.github.alexzhirkevich.compottie.LottieComposition
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.stt.STTManager

/**
 * Draws whatever [mode] means for one window output: the layers it puts on air, through the
 * renderer every output shares ([OutputLayers]), with [profile] deciding visibility, layout and
 * language for that output.
 *
 * Shared by every output — the per-screen windows, the DeckLink fill and key surfaces and the
 * browser-source overlays — which each supply their own Window/Crossfade wrapper and differ only
 * in [outputRole] and whether backgrounds are drawn. [showBackgroundOverride] exists because the
 * key and DeckLink paths historically omitted `showBackground` and so took the presenters' `true`
 * default; passing `true` there keeps that exact behaviour rather than quietly changing what those
 * outputs render.
 */
@Composable
fun PresenterModeContent(
    mode: Presenting,
    profile: OutputProfile,
    presenterManager: PresenterManager,
    appSettings: AppSettings,
    mediaViewModel: MediaViewModel,
    sttManager: STTManager,
    serverUrl: String,
    qaDisplayUrl: String,
    lottieComposition: LottieComposition?,
    clearAnnouncementOnFinish: () -> Unit,
    outputRole: String,
    showBg: Boolean,
    showBackgroundOverride: Boolean? = null,
) {
    val bandSongLineIndex by presenterManager.bandSongLineIndex
    val bandOutgoing by presenterManager.bandOutgoing

    // The Lottie band's clock, line and outgoing text travel as locals: the two presenters that
    // read them are long past their parameter budget, and every output has exactly one of each.
    //
    // The clock is provided as its state holder and deliberately NOT unwrapped here. Reading its
    // value in this body would subscribe the whole dispatch below to a state that moves every
    // animation frame.
    CompositionLocalProvider(
        LocalLottieBandClock provides presenterManager.lottieBandClock,
        LocalBandSongLineIndex provides bandSongLineIndex,
        LocalBandOutgoing provides bandOutgoing,
    ) {
    LowerThirdLayout(mode, profile, appSettings, showBackgroundOverride ?: showBg) {
        OutputLayers(
            mode = mode,
            surface = OutputSurface(
                kind = OutputSurfaceKind.WINDOW,
                profile = profile,
                appSettings = appSettings,
                presenterManager = presenterManager,
                outputRole = outputRole,
                showBg = showBg,
                mediaViewModel = mediaViewModel,
                sttManager = sttManager,
                qrCodeUrl = qaQrCodeUrl(qaDisplayUrl, serverUrl),
                showBackgroundOverride = showBackgroundOverride,
                lottieComposition = lottieComposition,
                onAnnouncementFinished = clearAnnouncementOnFinish,
            ),
        )
    }
    }
}
