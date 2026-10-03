package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.liveshow.Cue
import org.churchpresenter.liveshow.Layer
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting

/**
 * The layer map today's single live mode amounts to: at most one cue, on the layer the mode's
 * content belongs to, built from what the outputs draw rather than what the operator chose.
 *
 * Step 1 of the layer-model migration (`docs/LAYER_MODEL.md`): program is derived, nothing reads it
 * yet. It goes once the content setters write cues themselves.
 */
internal fun legacyProgram(mode: Presenting, live: PresenterManager): Map<Layer, Cue> =
    legacyCue(mode, live)?.let { mapOf(it.layer to it) } ?: emptyMap()

private fun legacyCue(mode: Presenting, live: PresenterManager): Cue? = when (mode) {
    Presenting.NONE -> null
    Presenting.BIBLE -> Cue.Verses(live.displayedVerses.value)
    Presenting.LYRICS -> live.displayedSongPosition.value.let {
        Cue.Song(live.displayedLyricSection.value, it.sectionIndex, it.lineIndex)
    }
    Presenting.PICTURES -> live.displayedImagePath.value?.let(Cue::Picture)
    Presenting.PRESENTATION -> live.liveSlide.value?.let { Cue.PresentationSlide(it.fileName, it.index) }
    Presenting.MEDIA -> mediaCue(live.currentMediaUrl.value, live.currentMediaType.value)
    Presenting.LOWER_THIRD -> Cue.LowerThird(live.currentLowerThirdName.value)
    Presenting.ANNOUNCEMENTS -> Cue.Announcement(live.displayedAnnouncementText.value)
    Presenting.WEBSITE -> Cue.Web(live.websiteUrl.value)
    Presenting.CANVAS -> live.activeScene.value?.let(Cue::SceneCue)
    Presenting.QA -> Cue.QuestionCue(live.displayedQuestion.value)
    Presenting.STT -> Cue.Captions
    Presenting.DICTIONARY -> live.displayedDictionaryEntry.value?.let { Cue.Dictionary(it.number) }
}

private fun mediaCue(url: String, type: String): Cue? = when {
    url.isBlank() -> null
    type == Constants.MEDIA_TYPE_AUDIO -> Cue.Audio(url)
    else -> Cue.Video(url)
}
