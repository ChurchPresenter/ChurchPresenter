package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.media_subtitles
import churchpresenter.composeapp.generated.resources.output_profile_sample_caption
import churchpresenter.composeapp.generated.resources.output_profile_sample_caption_earlier
import churchpresenter.composeapp.generated.resources.output_profile_sample_caption_translation
import churchpresenter.composeapp.generated.resources.output_profile_sample_dictionary_definition
import churchpresenter.composeapp.generated.resources.output_profile_sample_dictionary_usage
import churchpresenter.composeapp.generated.resources.output_profile_sample_question
import churchpresenter.composeapp.generated.resources.output_profile_sample_subtitle
import churchpresenter.composeapp.generated.resources.tab_dictionary
import churchpresenter.composeapp.generated.resources.tab_qa
import churchpresenter.composeapp.generated.resources.tab_stt
import org.churchpresenter.app.churchpresenter.data.StrongsEntry
import org.churchpresenter.app.churchpresenter.presenter.DictionaryPresenter
import org.churchpresenter.app.churchpresenter.presenter.QAPresenter
import org.churchpresenter.app.churchpresenter.presenter.STTPresenter
import org.churchpresenter.app.churchpresenter.presenter.SubtitleOverlay
import org.churchpresenter.app.churchpresenter.subtitles.SubtitleCue
import org.churchpresenter.app.churchpresenter.viewmodel.STTSegment
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.settings.AppSettings
import org.jetbrains.compose.resources.stringResource

/**
 * The pictures beside the whole-form style tabs: captions, subtitles, a Q&A question and a
 * dictionary card, each drawn by the renderer the output itself uses, over sample content, at the
 * profile's preview shape -- so a change in the form is seen as the screen would show it.
 */
@Composable
internal fun ProfileFormStage(pane: CustomizePane, settings: AppSettings, output: PreviewOutputSize) {
    FormStageFrame(output, badge = pane.stageBadge()) {
        when (pane) {
            CustomizePane.CAPTIONS -> CaptionSample(settings)
            CustomizePane.SUBTITLES -> SubtitleOverlay(
                cue = SubtitleCue(
                    startMs = 0,
                    endMs = 0,
                    text = stringResource(Res.string.output_profile_sample_subtitle),
                ),
                mediaSettings = settings.mediaSettings,
            )
            CustomizePane.QA -> QAPresenter(question = sampleQuestion(), qaSettings = settings.qaSettings)
            CustomizePane.DICTIONARY -> DictionaryPresenter(
                entry = sampleEntry(),
                dictionarySettings = settings.dictionarySettings,
            )
            // Not whole-form stages; [CustomizeStagePanel] draws these.
            CustomizePane.STAGE_MONITOR, CustomizePane.BIBLE, CustomizePane.SONGS, CustomizePane.BACKGROUND -> Unit
        }
    }
}

@Composable
private fun CustomizePane.stageBadge(): String = stringResource(
    when (this) {
        CustomizePane.SUBTITLES -> Res.string.media_subtitles
        CustomizePane.QA -> Res.string.tab_qa
        CustomizePane.DICTIONARY -> Res.string.tab_dictionary
        else -> Res.string.tab_stt
    },
)

/** Two finished lines and their translation, with no drip-feed left to play out. */
@Composable
private fun CaptionSample(settings: AppSettings) {
    val earlier = stringResource(Res.string.output_profile_sample_caption_earlier)
    val latest = stringResource(Res.string.output_profile_sample_caption)
    val translated = stringResource(Res.string.output_profile_sample_caption_translation)
    STTPresenter(
        segments = listOf(sampleSegment(0, earlier), sampleSegment(1, latest)),
        inProgressText = "",
        translationSegments = listOf(sampleSegment(1, translated)),
        inProgressTranslation = "",
        highlightedWords = emptyList(),
        // The drip-feed types the newest line out letter by letter; a still picture shows it whole.
        sttSettings = settings.sttSettings.copy(dripFeedEnabled = false),
    )
}

private fun sampleSegment(id: Int, text: String) =
    STTSegment(id = id, timestamp = "", text = text, start = 0.0, end = 0.0, completed = true)

@Composable
private fun sampleQuestion() = Question(
    id = "sample",
    text = stringResource(Res.string.output_profile_sample_question),
    timestamp = 0L,
)

@Composable
private fun sampleEntry() = StrongsEntry(
    number = "G3056",
    word = "λόγος",
    transliteration = "logos",
    pronunciation = "log'-os",
    definition = stringResource(Res.string.output_profile_sample_dictionary_definition),
    kjvUsage = stringResource(Res.string.output_profile_sample_dictionary_usage),
)

/** The plate the form stages draw on: the Bible and Song stages' frame, and their badge. */
@Composable
private fun FormStageFrame(output: PreviewOutputSize, badge: String, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(output.aspectRatio)
            .clipToBounds()
            .background(Color(PREVIEW_BACKGROUND), RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
    ) {
        ScaledPresenterBox(output) { content() }
        PreviewBadge(label = badge, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
    }
}
