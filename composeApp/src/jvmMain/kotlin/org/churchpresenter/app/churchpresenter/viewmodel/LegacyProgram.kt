package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.liveshow.BackgroundSource
import org.churchpresenter.liveshow.Cue
import org.churchpresenter.liveshow.Layer
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting

/**
 * The layer map today's single live mode amounts to: one cue for any mode but [Presenting.NONE], on
 * the layer the mode's content belongs to, built from what the outputs draw rather than what the
 * operator chose -- and for Bible and songs, their background on the background layer.
 *
 * A mode always has its cue, even before its content arrives (no picture yet, no scene chosen):
 * the outputs drew that mode's presenter regardless, and a picture fading out still needs it.
 *
 * Steps 1 to 3 of the layer-model migration (`docs/LAYER_MODEL.md`): program is derived, and the
 * outputs draw from it. It goes once the content setters write cues themselves.
 */
internal fun legacyProgram(mode: Presenting, live: PresenterManager): Map<Layer, Cue> =
    listOfNotNull(legacyBackground(mode, live), legacyCue(mode, live)).associateBy { it.layer }

/** The content types with a background setting of their own put it up with them. */
private fun legacyBackground(mode: Presenting, live: PresenterManager): Cue? = when (mode) {
    Presenting.BIBLE -> Cue.Background(BackgroundSource.BIBLE)
    Presenting.LYRICS -> Cue.Background(BackgroundSource.SONGS, live.displayedLyricSection.value.background)
    else -> null
}

private fun legacyCue(mode: Presenting, live: PresenterManager): Cue? = when (mode) {
    Presenting.NONE -> null
    Presenting.BIBLE -> Cue.Verses(live.displayedVerses.value)
    Presenting.LYRICS -> live.displayedSongPosition.value.let {
        Cue.Song(live.displayedLyricSection.value, it.sectionIndex, it.lineIndex)
    }
    Presenting.PICTURES -> Cue.Picture(live.displayedImagePath.value)
    Presenting.PRESENTATION -> live.liveSlide.value.let { Cue.PresentationSlide(it?.fileName, it?.index ?: -1) }
    Presenting.MEDIA -> mediaCue(live.currentMediaUrl.value, live.currentMediaType.value)
    Presenting.LOWER_THIRD -> Cue.LowerThird(live.currentLowerThirdName.value)
    Presenting.ANNOUNCEMENTS -> Cue.Announcement(live.displayedAnnouncementText.value)
    Presenting.WEBSITE -> Cue.Web(live.websiteUrl.value)
    Presenting.CANVAS -> Cue.SceneCue(live.activeScene.value)
    Presenting.QA -> Cue.QuestionCue(live.displayedQuestion.value)
    Presenting.STT -> Cue.Captions
    Presenting.DICTIONARY -> Cue.Dictionary(live.displayedDictionaryEntry.value?.number)
    // A message is held whole, on its own layer.
    Presenting.MESSAGE -> live.liveShow.program.value[Layer.MESSAGES]
    Presenting.PROPS -> live.liveShow.program.value[Layer.PROPS]
}

private fun mediaCue(url: String, type: String): Cue =
    if (type == Constants.MEDIA_TYPE_AUDIO) Cue.Audio(url) else Cue.Video(url)

/** The content type this cue puts on air, or [Presenting.NONE] for a background, which is none. */
internal val Cue.content: Presenting get() = when (this) {
    is Cue.Background -> Presenting.NONE
    is Cue.Video, is Cue.Audio -> Presenting.MEDIA
    is Cue.Picture -> Presenting.PICTURES
    is Cue.Verses -> Presenting.BIBLE
    is Cue.Song -> Presenting.LYRICS
    is Cue.PresentationSlide -> Presenting.PRESENTATION
    is Cue.SceneCue -> Presenting.CANVAS
    is Cue.Web -> Presenting.WEBSITE
    is Cue.QuestionCue -> Presenting.QA
    is Cue.Dictionary -> Presenting.DICTIONARY
    is Cue.Captions -> Presenting.STT
    is Cue.LowerThird -> Presenting.LOWER_THIRD
    is Cue.Announcement -> Presenting.ANNOUNCEMENTS
    is Cue.Message -> Presenting.MESSAGE
    is Cue.Props -> Presenting.PROPS
}

/** The content types [program] puts on air, in the order they went up: the slide first, then each overlay. */
internal fun contentOnAir(program: Map<Layer, Cue>): Set<Presenting> =
    program.values.mapNotNullTo(LinkedHashSet()) { cue -> cue.content.takeIf { it != Presenting.NONE } }
