package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import org.churchpresenter.theme.components.GhostButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.content_announcements
import churchpresenter.composeapp.generated.resources.content_bible_background
import churchpresenter.composeapp.generated.resources.content_bible_translations_all_selected
import churchpresenter.composeapp.generated.resources.content_bible_translations_enabled
import churchpresenter.composeapp.generated.resources.content_bible_translations_footer
import churchpresenter.composeapp.generated.resources.content_bible_translations_header
import churchpresenter.composeapp.generated.resources.content_media
import churchpresenter.composeapp.generated.resources.content_song_languages_all_selected
import churchpresenter.composeapp.generated.resources.content_song_languages_enabled
import churchpresenter.composeapp.generated.resources.content_song_languages_footer
import churchpresenter.composeapp.generated.resources.content_song_languages_header
import churchpresenter.composeapp.generated.resources.content_pictures
import churchpresenter.composeapp.generated.resources.content_songs_background
import churchpresenter.composeapp.generated.resources.content_streaming
import churchpresenter.composeapp.generated.resources.customize_bible
import churchpresenter.composeapp.generated.resources.customize_songs
import churchpresenter.composeapp.generated.resources.display_fullscreen
import churchpresenter.composeapp.generated.resources.display_lower_third
import churchpresenter.composeapp.generated.resources.display_stage_monitor
import churchpresenter.composeapp.generated.resources.media_subtitles
import churchpresenter.composeapp.generated.resources.ok
import churchpresenter.composeapp.generated.resources.output_profile_content
import churchpresenter.composeapp.generated.resources.output_profile_delete
import churchpresenter.composeapp.generated.resources.output_profile_delete_blocked
import churchpresenter.composeapp.generated.resources.output_profile_delete_confirm
import churchpresenter.composeapp.generated.resources.output_profile_display_mode
import churchpresenter.composeapp.generated.resources.output_profile_name_hint
import churchpresenter.composeapp.generated.resources.projection_content_background
import churchpresenter.composeapp.generated.resources.projection_content_lt_background
import churchpresenter.composeapp.generated.resources.projection_content_song_la
import churchpresenter.composeapp.generated.resources.projection_content_web
import churchpresenter.composeapp.generated.resources.song_language_fourth
import churchpresenter.composeapp.generated.resources.song_language_primary
import churchpresenter.composeapp.generated.resources.song_language_secondary
import churchpresenter.composeapp.generated.resources.song_language_third
import churchpresenter.composeapp.generated.resources.stage_monitor_show_chords
import churchpresenter.composeapp.generated.resources.tab_canvas
import churchpresenter.composeapp.generated.resources.tab_dictionary
import churchpresenter.composeapp.generated.resources.tab_qa
import churchpresenter.composeapp.generated.resources.tab_stt
import org.churchpresenter.bible.defaultTranslationAbbreviation
import org.churchpresenter.app.churchpresenter.composables.LabeledSwitch
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.OutputStyleScope
import org.churchpresenter.settings.resolvedFor
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.theme.components.SettingsTextField
import org.jetbrains.compose.resources.stringResource

/**
 * The header (name, display mode, content selection) plus the reused Customize editing surface,
 * for the profile selected on [ProfilesSettingsTab]'s rail.
 *
 * Split out of that file to stay under detekt's per-file function count -- this is one cohesive
 * unit (the right-hand side of the tab), the rail is the other.
 */
@Composable
internal fun ProfileEditor(
    settings: AppSettings,
    profile: OutputProfile,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onProfileChange: (OutputProfile) -> Unit,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pane by remember(profile.id) { mutableStateOf(customizePanes(profile.displayMode).first()) }
    var element by remember(profile.id, pane) {
        mutableStateOf(customizeElements(pane).firstOrNull())
    }
    var translationIndex by remember(profile.id) { mutableStateOf(0) }
    // Which of the three sample texts the picture stands in for. Not on the profile: it is a
    // question about this session's checking, not about the profile's own look.
    var sampleSlot by remember { mutableStateOf(PreviewSampleSlot.MEDIUM) }

    val panes = customizePanes(profile.displayMode)
    if (pane !in panes) pane = panes.first()
    val elements = customizeElements(pane)

    val resolved = remember(settings, profile) { settings.resolvedFor(profile) }
    val onDraftSettingsChange: ((AppSettings) -> AppSettings) -> Unit = { transform ->
        val updated = transform(resolved)
        onProfileChange(
            profile.copy(
                stageMonitorSettings = updated.stageMonitorSettings,
                // Written whole even when only some surfaces are overridden: resolution reads the
                // profile's copy of a followed surface not at all, so carrying it costs nothing and
                // is what lets "take this one over" start from the picture already on screen.
                backgroundSettings = updated.backgroundSettings,
                songSettings = updated.songSettings,
                bibleSettings = updated.bibleSettings,
            ),
        )
        // Anything the pane touched outside the profile-owned fields is genuinely global rather than
        // this profile's, and goes to the real document -- as a *delta*, never as a snapshot.
        //
        // `resolved` is stale the instant `onProfileChange` above has run, so handing any of it back
        // wholesale reverts that write. This used to read `updated.copy(<the four profile-owned
        // fields> = real.…)`, which carried `updated`'s pre-edit `projectionSettings` -- the profile
        // list -- straight over the edit just made, leaving the document byte-identical to where it
        // started. Every control in this editor was dead, and silently: the value simply snapped
        // back, with nothing to see in a screenshot and nothing for a test to catch.
        //
        // So: name what is genuinely global and leave the rest of the live document alone. The cost
        // is that a future pane editing some new global field must be named here too -- a miss that
        // shows up the first time anyone tries that control, rather than one that quietly corrupts
        // an unrelated field.
        if (updated.stockPhotoSettings != resolved.stockPhotoSettings) {
            onSettingsChange { real -> real.copy(stockPhotoSettings = updated.stockPhotoSettings) }
        }
    }

    val scope = if (profile.isLowerThird) OutputStyleScope.LOWER_THIRD else OutputStyleScope.FULL_SCREEN

    // A Row rather than a Column at the top level: the preview column is a sibling of the header
    // and category rail rather than nested under them, so its top edge lines up with the Profiles
    // tab's own rail instead of starting further down, under the name/display-mode header. The
    // stage monitor draws its own preview inside CustomizeControls and gets no column here.
    Row(modifier = modifier) {
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            ProfileHeader(
                settings = resolved,
                profile = profile,
                onProfileChange = onProfileChange,
                onRename = onRename,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                CustomizeRail(
                    panes = panes,
                    selected = pane,
                    onSelect = { pane = it },
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                CompositionLocalProvider(LocalOutputStyleScope provides scope) {
                    CustomizeControls(
                        pane = pane,
                        element = element,
                        elements = elements,
                        draft = resolved,
                        profile = profile,
                        translationIndex = translationIndex,
                        onTranslationChange = { translationIndex = it },
                        onElementChange = { element = it },
                        onSettingsChange = onDraftSettingsChange,
                        onProfileFieldChange = onProfileChange,
                    )
                }
            }
        }
        if (pane != CustomizePane.STAGE_MONITOR) {
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CompositionLocalProvider(LocalOutputStyleScope provides scope) {
                CustomizePreviewColumn(
                    pane = pane,
                    element = element,
                    draft = resolved,
                    profile = profile,
                    onSettingsChange = onDraftSettingsChange,
                    onProfileFieldChange = onProfileChange,
                    onNavigate = { target, chip ->
                        pane = target
                        element = chip
                    },
                    slot = sampleSlot,
                    onSlotChange = { sampleSlot = it },
                )
            }
        }
    }
}

@Composable
internal fun DeleteProfileDialog(
    profileName: String,
    userLabels: List<String>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.output_profile_delete)) },
        text = {
            Text(
                if (userLabels.isEmpty()) {
                    stringResource(Res.string.output_profile_delete_confirm, profileName)
                } else {
                    stringResource(
                        Res.string.output_profile_delete_blocked,
                        profileName,
                        userLabels.joinToString(", "),
                    )
                },
            )
        },
        confirmButton = {
            if (userLabels.isEmpty()) {
                GhostButton(onClick = onConfirm) { Text(stringResource(Res.string.ok)) }
            }
        },
        dismissButton = { GhostButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

/** Name field (beside the display mode selector, on one line) and the content the profile shows. */
@Composable
private fun ProfileHeader(
    settings: AppSettings,
    profile: OutputProfile,
    onProfileChange: (OutputProfile) -> Unit,
    onRename: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Flexible, not a fixed minimum: the Display Mode selector beside it is three fixed-size
            // buttons that need their own room, and a Row does not shrink an unweighted sibling to
            // make space -- it was pushed past the edge and clipped by the preview column instead.
            // Weighting the name field is what lets it give ground, both to the selector and to
            // whatever width this column actually has once the preview sidebar takes its fixed share.
            SettingsTextField(
                value = profile.name,
                onValueChange = onRename,
                label = stringResource(Res.string.output_profile_name_hint),
                placeholder = { Text(profile.id) },
                modifier = Modifier.weight(1f),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.output_profile_display_mode),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DisplayModeSelector(profile, onProfileChange)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(Res.string.output_profile_content),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ContentSelectionRow(settings, profile, onProfileChange)
        }
    }
}

@Composable
private fun DisplayModeSelector(profile: OutputProfile, onProfileChange: (OutputProfile) -> Unit) {
    val items = listOf(
        SegmentedButtonItem(Constants.DISPLAY_MODE_FULLSCREEN, stringResource(Res.string.display_fullscreen)),
        SegmentedButtonItem(
            Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
            stringResource(Res.string.display_lower_third),
        ),
        SegmentedButtonItem(Constants.DISPLAY_MODE_STAGE_MONITOR, stringResource(Res.string.display_stage_monitor)),
    )
    SegmentedButton(
        items = items,
        selectedValue = shownDisplayMode(profile.displayMode),
        onValueChange = { picked ->
            onProfileChange(profile.copy(displayMode = pickedDisplayMode(picked, profile.displayMode)))
        },
        buttonWidth = 130.dp,
        buttonHeight = 32.dp,
    )
}

/**
 * Every content type this profile can show.
 *
 * The chords switch is the stage monitor's alone -- it is what the platform needs a confidence
 * screen for, and no other display mode draws one. Everything else is offered whatever the display
 * mode is, because `showsContentFor` is display-mode-agnostic: a stage monitor obeys `showPictures`,
 * `showQA` and the rest exactly as a full screen does, so hiding those switches on it left an
 * operator no way to stop a confidence screen showing the dictionary card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContentSelectionRow(
    settings: AppSettings,
    profile: OutputProfile,
    onProfileChange: (OutputProfile) -> Unit,
) {
    val stageMonitor = profile.displayMode == Constants.DISPLAY_MODE_STAGE_MONITOR
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (stageMonitor) {
            ContentToggle(profile.showChords, stringResource(Res.string.stage_monitor_show_chords)) {
                onProfileChange(profile.copy(showChords = it))
            }
        }
        contentToggles(profile).forEach { (checked, label, edit) ->
            ContentToggle(checked, label) { onProfileChange(edit(profile, it)) }
        }
        if (profile.showSongs) {
            ContentToggle(profile.songLookAhead, stringResource(Res.string.projection_content_song_la)) {
                onProfileChange(profile.copy(songLookAhead = it))
            }
        }
    }
    // Which of the stack's translations, and which of a song's languages, this profile actually
    // draws -- not merely whether it draws any. The on/off switches above cannot say "the KJV only,
    // of the three configured", and for songs `songMode`'s five values cannot say "languages 1
    // and 3" at all; both are stored as positions and read by every presenter.
    TranslationSubsetRow(settings, profile, onProfileChange)
}

/** The Bible-translation and song-language checklists, side by side under the content switches. */
@Composable
private fun TranslationSubsetRow(
    settings: AppSettings,
    profile: OutputProfile,
    onProfileChange: (OutputProfile) -> Unit,
) {
    val bibleChoices = bibleTranslationChoices(settings)
    val songChoices = songLanguageChoices(settings)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ContentTranslationCell(
            modifier = Modifier.weight(1f),
            label = stringResource(Res.string.customize_bible),
            tags = TranslationPickerTags.BIBLE,
            headerText = stringResource(Res.string.content_bible_translations_header),
            enabledFormat = stringResource(Res.string.content_bible_translations_enabled),
            footerText = stringResource(Res.string.content_bible_translations_footer),
            allSelectedText = stringResource(Res.string.content_bible_translations_all_selected),
            translations = bibleChoices,
            showing = profile.showBible,
            selected = profile.bibleTranslations,
            onShowingChange = { on ->
                onProfileChange(
                    profile.copy(bibleMode = if (on) Constants.SONG_LANG_BOTH else Constants.SONG_LANG_OFF),
                )
            },
            onSelectedChange = { next -> onProfileChange(profile.copy(bibleTranslations = next)) },
            onShowAndSelect = { next ->
                onProfileChange(profile.copy(bibleMode = Constants.SONG_LANG_BOTH, bibleTranslations = next))
            },
        )
        ContentTranslationCell(
            modifier = Modifier.weight(1f),
            label = stringResource(Res.string.customize_songs),
            tags = TranslationPickerTags.SONG,
            headerText = stringResource(Res.string.content_song_languages_header),
            enabledFormat = stringResource(Res.string.content_song_languages_enabled),
            footerText = stringResource(Res.string.content_song_languages_footer),
            allSelectedText = stringResource(Res.string.content_song_languages_all_selected),
            // Four single digits fit where the Bible's "+N more" count goes, so the trigger names
            // the languages themselves rather than making the operator open the menu to find out.
            listSelectedCodes = true,
            translations = songChoices,
            showing = profile.showSongs,
            selected = profile.songTranslations,
            onShowingChange = { on ->
                onProfileChange(
                    if (on) profile.copy(songMode = Constants.SONG_LANG_BOTH)
                    else profile.copy(songMode = Constants.SONG_LANG_OFF, songLookAhead = false),
                )
            },
            onSelectedChange = { next -> onProfileChange(profile.copy(songTranslations = next)) },
            onShowAndSelect = { next ->
                onProfileChange(profile.copy(songMode = Constants.SONG_LANG_BOTH, songTranslations = next))
            },
        )
    }
}

/**
 * The configured Bible stack as pickable choices, in the order the selections index.
 *
 * Named from the stack itself rather than by opening each `.spb` -- the Content Outputs dialog read
 * a header per module off the IO dispatcher to show "OT+NT" beside each one, which is a nicety this
 * row does without; the widget omits the portion line when it is blank.
 */
@Composable
private fun bibleTranslationChoices(settings: AppSettings): List<TranslationChoiceDisplay> =
    settings.bibleSettings.translationList().map { translation ->
        val code = translation.customAbbreviation.ifBlank {
            defaultTranslationAbbreviation(title = "", fileName = translation.fileName)
        }
        TranslationChoiceDisplay(
            code = code,
            title = translation.customName.ifBlank { translation.fileName.substringBeforeLast('.') },
            portion = "",
        )
    }

/** A song's four language slots as pickable choices, named the way the song tabs name them. */
@Composable
private fun songLanguageChoices(settings: AppSettings): List<TranslationChoiceDisplay> {
    val names = listOf(
        stringResource(Res.string.song_language_primary),
        stringResource(Res.string.song_language_secondary),
        stringResource(Res.string.song_language_third),
        stringResource(Res.string.song_language_fourth),
    )
    return names.mapIndexed { index, name ->
        val configured = settings.songSettings.translations.getOrNull(index - 1)?.label.orEmpty()
        TranslationChoiceDisplay(
            code = (index + 1).toString(),
            title = configured.ifBlank { name },
            portion = "",
        )
    }
}

/** One toggle's current value, label and how to write a new value onto [profile] -- see [ContentSelectionRow]. */
private data class ContentToggleSpec(
    val checked: Boolean,
    val label: String,
    val edit: (OutputProfile, Boolean) -> OutputProfile,
)

@Composable
private fun contentToggles(profile: OutputProfile): List<ContentToggleSpec> = listOf(
    ContentToggleSpec(profile.showBible, stringResource(Res.string.customize_bible)) { p, v ->
        p.copy(bibleMode = if (v) Constants.SONG_LANG_BOTH else Constants.SONG_LANG_OFF)
    },
    ContentToggleSpec(profile.showSongs, stringResource(Res.string.customize_songs)) { p, v ->
        p.copy(songMode = if (v) Constants.SONG_LANG_BOTH else Constants.SONG_LANG_OFF)
    },
    ContentToggleSpec(profile.showPictures, stringResource(Res.string.content_pictures)) { p, v ->
        p.copy(showPictures = v)
    },
    ContentToggleSpec(profile.showMedia, stringResource(Res.string.content_media)) { p, v -> p.copy(showMedia = v) },
    // Whether video/media carries its subtitle overlay on this output. Read by MediaPresenter
    // through PresenterModeContent and OffscreenOutputContent; it has never had a switch anywhere.
    ContentToggleSpec(profile.showSubtitles, stringResource(Res.string.media_subtitles)) { p, v ->
        p.copy(showSubtitles = v)
    },
    ContentToggleSpec(profile.showStreaming, stringResource(Res.string.content_streaming)) { p, v ->
        p.copy(showStreaming = v)
    },
    ContentToggleSpec(profile.showAnnouncements, stringResource(Res.string.content_announcements)) { p, v ->
        p.copy(showAnnouncements = v)
    },
    ContentToggleSpec(profile.showWebsite, stringResource(Res.string.projection_content_web)) { p, v ->
        p.copy(showWebsite = v)
    },
    ContentToggleSpec(profile.showCanvas, stringResource(Res.string.tab_canvas)) { p, v -> p.copy(showCanvas = v) },
    ContentToggleSpec(profile.showQA, stringResource(Res.string.tab_qa)) { p, v -> p.copy(showQA = v) },
    ContentToggleSpec(profile.showSTT, stringResource(Res.string.tab_stt)) { p, v -> p.copy(showSTT = v) },
    ContentToggleSpec(profile.showDictionary, stringResource(Res.string.tab_dictionary)) { p, v ->
        p.copy(showDictionary = v)
    },
    ContentToggleSpec(
        profile.showFullscreenBackground,
        stringResource(Res.string.projection_content_background),
    ) { p, v -> p.copy(showFullscreenBackground = v) },
    ContentToggleSpec(
        profile.showLowerThirdBackground,
        stringResource(Res.string.projection_content_lt_background),
    ) { p, v -> p.copy(showLowerThirdBackground = v) },
    ContentToggleSpec(profile.showBibleBackground, stringResource(Res.string.content_bible_background)) { p, v ->
        p.copy(showBibleBackground = v)
    },
    ContentToggleSpec(profile.showSongsBackground, stringResource(Res.string.content_songs_background)) { p, v ->
        p.copy(showSongsBackground = v)
    },
)

@Composable
private fun ContentToggle(checked: Boolean, label: String, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(8.dp),
    ) {
        LabeledSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            label = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            spacing = 6.dp,
        )
    }
}
