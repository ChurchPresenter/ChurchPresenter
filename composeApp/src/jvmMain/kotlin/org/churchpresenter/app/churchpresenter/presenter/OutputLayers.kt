package org.churchpresenter.app.churchpresenter.presenter

import org.churchpresenter.canvas.ScenePresenter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import io.github.alexzhirkevich.compottie.LottieComposition
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.content
import org.churchpresenter.app.churchpresenter.viewmodel.drawsOverContent
import org.churchpresenter.app.churchpresenter.viewmodel.legacyProgram
import org.churchpresenter.app.churchpresenter.viewmodel.unlockedModeFor
import org.churchpresenter.liveshow.Cue
import org.churchpresenter.liveshow.Layer
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.presenter.showsContentFor
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
    // The output's look decides whether it draws this kind of content at all; a background goes by
    // the background switches, which the background layer reads itself.
    if (cue !is Cue.Background && !showsContentFor(cue.content, surface.profile)) return
    when (cue) {
        is Cue.Verses -> BibleCue(surface)
        is Cue.Song -> SongCue(surface)
        is Cue.Picture -> PictureCue(surface)
        is Cue.PresentationSlide -> PresentationCue(surface)
        // Audio draws nothing; which of the two the media is follows the media view model, as it
        // always has, so a video and an audio cue go through the same check.
        is Cue.Video, is Cue.Audio -> MediaCue(surface)
        is Cue.LowerThird -> LowerThirdCue(surface)
        is Cue.Announcement -> AnnouncementCue(surface)
        is Cue.Web -> WebCue(surface)
        is Cue.SceneCue -> ScenePresenter(scene = surface.presenterManager.activeScene.value)
        is Cue.QuestionCue -> QuestionCue(surface)
        is Cue.Captions -> CaptionsCue(surface)
        is Cue.Dictionary -> DictionaryCue(surface)
        is Cue.Background -> BackgroundCue(cue, surface)
        is Cue.Message -> MessageCue(cue, surface)
        is Cue.Props -> PropsCue(cue, surface)
    }
}

/** The overlays in the order they stack, bottom to top: captions, lower third, props, announcements. */
private val OVERLAY_DRAW_ORDER =
    listOf(Presenting.STT, Presenting.LOWER_THIRD, Presenting.PROPS, Presenting.ANNOUNCEMENTS)

/**
 * The overlays this output draws over its content, each drawn by [content] exactly as it is drawn on
 * its own, stacked above whatever the caller drew before this: those up that [profile] puts over the
 * content. [shownMode] is what the output shows beneath them ([shownModeFor]); an output locked to
 * a mode of its own shows that and nothing over it. A lock to the mode already live cannot be told
 * from no lock here, so such an output takes the overlays as an unlocked one would.
 */
@Composable
internal fun OverlayModes(
    presenterManager: PresenterManager,
    profile: OutputProfile,
    shownMode: Presenting,
    content: @Composable (Presenting) -> Unit,
) {
    if (shownMode != presenterManager.unlockedModeFor(profile)) return
    val overlays = presenterManager.overlays.value
    OVERLAY_DRAW_ORDER.forEach { mode ->
        // Props sit over the lower third and under announcements, and are up whatever is shown.
        if (mode == Presenting.PROPS) {
            if (presenterManager.isLive(Presenting.PROPS)) key(mode) { content(mode) }
        } else if (mode in overlays && mode != shownMode && profile.drawsOverContent(mode)) {
            key(mode) { content(mode) }
        }
    }
    // A message is up alone, over everything an output following the live content shows.
    if (presenterManager.isLive(Presenting.MESSAGE)) key(Presenting.MESSAGE) { content(Presenting.MESSAGE) }
}
