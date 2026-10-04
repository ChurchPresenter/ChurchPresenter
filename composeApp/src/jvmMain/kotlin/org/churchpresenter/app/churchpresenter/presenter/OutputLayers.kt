package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import io.github.alexzhirkevich.compottie.LottieComposition
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.legacyProgram
import org.churchpresenter.liveshow.Cue
import org.churchpresenter.liveshow.Layer
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.stt.STTManager

/**
 * Which kind of output is drawing. Where the three draw a cue differently, the difference is
 * written out against this, in one place, rather than in three copies of the dispatch.
 */
internal enum class OutputSurfaceKind {
    /** A projector window, or a DeckLink fill or key surface. */
    WINDOW,

    /** NDI, OMT or a Browser Source, drawn off screen. */
    OFFSCREEN,

    /** A tile in the operator's live preview panel. */
    PREVIEW,
}

/**
 * Everything about one output that decides how a cue is drawn on it.
 *
 * Holds [presenterManager] for the rendering-bridge exception AGENT.md allows: the presenters still
 * read their state from it, and the cue only says which of them is up.
 */
internal class OutputSurface(
    val kind: OutputSurfaceKind,
    val profile: OutputProfile,
    val appSettings: AppSettings,
    val presenterManager: PresenterManager,
    val outputRole: String,
    val showBg: Boolean,
    val mediaViewModel: MediaViewModel? = null,
    val sttManager: STTManager? = null,
    val qrCodeUrl: String = "",
    /** Forces backgrounds on; the key and DeckLink paths have always drawn them. */
    val showBackgroundOverride: Boolean? = null,
    /** The lower third every window shares, parsed once; the other outputs parse their own. */
    val lottieComposition: LottieComposition? = null,
    val onAnnouncementFinished: () -> Unit = {},
)

/**
 * Draws the layers [mode] puts on air, bottom to top, each through [CueContent].
 *
 * Program is still derived from the single live mode ([legacyProgram]): one content layer, plus the
 * background layer under Bible and songs. The layers are emitted straight into the caller's
 * container -- a box in every output -- so they stack in [Layer] order, and a lone layer lays out
 * exactly as its presenter did on its own.
 */
@Composable
internal fun OutputLayers(mode: Presenting, surface: OutputSurface) {
    val presenterManager = surface.presenterManager
    val program by remember(mode, presenterManager) {
        derivedStateOf { legacyProgram(mode, presenterManager) }
    }
    Layer.entries.forEach { layer ->
        program[layer]?.let { cue -> key(layer) { CueContent(cue, surface) } }
    }
}

/** What one cue draws on [surface], when the output's profile shows that kind of content. */
@Composable
internal fun CueContent(cue: Cue, surface: OutputSurface) {
    val profile = surface.profile
    when (cue) {
        is Cue.Verses -> if (profile.showBible) BibleCue(surface)
        is Cue.Song -> if (profile.showSongs) SongCue(surface)
        is Cue.Picture -> if (profile.showPictures) PictureCue(surface)
        is Cue.PresentationSlide -> if (profile.showPictures) PresentationCue(surface)
        // Audio draws nothing; which of the two the media is follows the media view model, as it
        // always has, so a video and an audio cue go through the same check.
        is Cue.Video, is Cue.Audio -> if (profile.showMedia) MediaCue(surface)
        is Cue.LowerThird -> if (profile.showStreaming) LowerThirdCue(surface)
        is Cue.Announcement -> if (profile.showAnnouncements) AnnouncementCue(surface)
        is Cue.Web -> if (profile.showWebsite) WebCue(surface)
        is Cue.SceneCue -> if (profile.showCanvas) ScenePresenter(scene = surface.presenterManager.activeScene.value)
        is Cue.QuestionCue -> if (profile.showQA) QuestionCue(surface)
        is Cue.Captions -> if (profile.showSTT) CaptionsCue(surface)
        is Cue.Dictionary -> if (profile.showDictionary) DictionaryCue(surface)
        is Cue.Background -> BackgroundCue(cue, surface)
        // Not put on air until its own migration step.
        is Cue.Message -> Unit
    }
}

/** The overlays in the order they stack, bottom to top: captions, then the lower third. */
private val OVERLAY_DRAW_ORDER = listOf(Presenting.STT, Presenting.LOWER_THIRD)

/**
 * The overlays up over the slide, each drawn by [content] exactly as it is drawn on its own, stacked
 * above whatever the caller drew before this. Nothing on an output locked to a mode other than the
 * slide's: a locked screen shows its own mode and nothing over it.
 */
@Composable
internal fun OverlayModes(
    presenterManager: PresenterManager,
    effectiveMode: Presenting,
    content: @Composable (Presenting) -> Unit,
) {
    if (effectiveMode != presenterManager.presentingMode.value) return
    val overlays = presenterManager.overlays.value
    OVERLAY_DRAW_ORDER.forEach { mode -> if (mode in overlays) key(mode) { content(mode) } }
}
