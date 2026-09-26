package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.display_fullscreen
import churchpresenter.composeapp.generated.resources.display_lower_third
import churchpresenter.composeapp.generated.resources.display_stage_monitor
import churchpresenter.composeapp.generated.resources.ok
import churchpresenter.composeapp.generated.resources.output_profile_delete
import churchpresenter.composeapp.generated.resources.output_profile_delete_blocked
import churchpresenter.composeapp.generated.resources.output_profile_delete_confirm
import churchpresenter.composeapp.generated.resources.output_profile_display_mode
import churchpresenter.composeapp.generated.resources.output_profile_nothing_to_style
import churchpresenter.composeapp.generated.resources.output_profile_placement
import churchpresenter.composeapp.generated.resources.output_profile_scale
import churchpresenter.composeapp.generated.resources.output_profile_sources
import churchpresenter.composeapp.generated.resources.output_profile_style
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.churchpresenter.bible.defaultTranslationAbbreviation
import org.churchpresenter.core.models.songs.MAX_SONG_TRANSLATIONS
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.OutputStyleScope
import org.churchpresenter.settings.resolvedFor
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.theme.components.GhostButton
import org.jetbrains.compose.resources.stringResource

/** What the styling area says when everything this profile could style has been switched off. */
@Composable
private fun NothingToStyle() {
    Text(
        text = stringResource(Res.string.output_profile_nothing_to_style),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
    )
}

/** The width a caption beside a control takes, so the controls in the setup block line up. */
private val SETUP_CAPTION_WIDTH = 96.dp

/**
 * Everything to the right of the profile list: the header, what the profile shows, and the styling
 * surface, with the preview column beside it.
 *
 * Top to bottom it follows the order an operator sets a profile up in -- what kind of screen it is,
 * what goes on it, which Bibles and song languages, and then how each of those looks -- so the
 * things set once sit above the styling that is revisited every week, and fold away when done.
 */
@Composable
internal fun ProfileEditor(
    settings: AppSettings,
    profile: OutputProfile,
    /** Every output drawing with this profile, labelled the way its own card labels it. */
    usedBy: List<String>,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onProfileChange: (OutputProfile) -> Unit,
    onRename: (String) -> Unit,
    contentOpen: Boolean,
    onContentOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // What the operator last picked, not what is shown: a pick is dropped the moment its category
    // or chip is switched off under Content, and comes back if it is switched on again.
    //
    // Forgotten when the display mode changes, too. A stage monitor shares Q&A and Dictionary with
    // the other modes, so a pick carried across the switch landed on one of those and the stage
    // monitor's own editor -- the reason for switching -- never came up.
    val shownMode = shownDisplayMode(profile.displayMode)
    var pickedPane by remember(profile.id, shownMode) { mutableStateOf<CustomizePane?>(null) }
    var pickedElement by remember(profile.id, shownMode) { mutableStateOf<CustomizeElement?>(null) }
    // All by default, as the Songs pane's language row is: one look for the whole stack is the
    // usual case, and a single translation is picked out when it needs a look of its own.
    var translationIndex by remember(profile.id) { mutableStateOf(ALL_TRANSLATIONS) }
    // Which of the three sample texts the picture stands in for. Not on the profile: it is a
    // question about this session's checking, not about the profile's own look.
    var sampleSlot by remember { mutableStateOf(PreviewSampleSlot.MEDIUM) }

    val panes = stylePanesFor(profile)
    val pane = pickedPane?.takeIf { it in panes } ?: panes.firstOrNull()
    val elements = pane?.let { styleElementsFor(it, profile) }.orEmpty()
    val element = pickedElement?.takeIf { it in elements } ?: elements.firstOrNull()

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
                // Carried whole, like the Bible and Song styling: resolution keeps the install-wide
                // keys (the caption server, the Q&A rate limit) from the document regardless.
                sttSettings = updated.sttSettings,
                qaSettings = updated.qaSettings,
                mediaSettings = updated.mediaSettings,
                dictionarySettings = updated.dictionarySettings,
            ),
        )
        // Anything the pane touched outside the profile-owned fields is genuinely global rather than
        // this profile's, and goes to the real document -- as a *delta*, never as a snapshot.
        //
        // `resolved` is stale the instant `onProfileChange` above has run, so handing any of it back
        // wholesale reverts that write: carrying `updated`'s pre-edit `projectionSettings` -- the
        // profile list -- straight over the edit just made left every control in this editor dead,
        // silently. So name what is genuinely global and leave the rest of the live document alone.
        if (updated.stockPhotoSettings != resolved.stockPhotoSettings) {
            onSettingsChange { real -> real.copy(stockPhotoSettings = updated.stockPhotoSettings) }
        }
    }

    val scope = if (profile.isLowerThird) OutputStyleScope.LOWER_THIRD else OutputStyleScope.FULL_SCREEN

    // A Row rather than a Column at the top level: the preview column is a sibling of the header
    // rather than nested under it, so its top edge lines up with the profile list's own. The stage
    // monitor draws its own preview inside its pane and gets no column here.
    Row(modifier = modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            ProfileHeader(
                profile = profile,
                usedBy = usedBy,
                onRename = onRename,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ProfileSetup(
                settings = resolved,
                profile = profile,
                contentOpen = contentOpen,
                onContentOpenChange = onContentOpenChange,
                onProfileChange = onProfileChange,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            StyleTabs(panes = panes, selected = pane, onSelect = { pickedPane = it })
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (pane == null) {
                    NothingToStyle()
                } else {
                    CompositionLocalProvider(LocalOutputStyleScope provides scope) {
                        CustomizeControls(
                            pane = pane,
                            element = element,
                            elements = elements,
                            draft = resolved,
                            profile = profile,
                            translationIndex = translationIndex,
                            onTranslationChange = { translationIndex = it },
                            onElementChange = { pickedElement = it },
                            onSettingsChange = onDraftSettingsChange,
                            onProfileFieldChange = onProfileChange,
                        )
                    }
                }
            }
        }
        // Every category draws its picture beside the controls, bar the stage monitor, whose own
        // tab already draws its zone layout at full size.
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
                        pickedPane = target
                        pickedElement = chip
                    },
                    slot = sampleSlot,
                    onSlotChange = { sampleSlot = it },
                )
            }
        }
    }
}

/** What kind of screen the profile is for, what it shows, and which Bibles and song languages. */
@Composable
private fun ProfileSetup(
    settings: AppSettings,
    profile: OutputProfile,
    contentOpen: Boolean,
    onContentOpenChange: (Boolean) -> Unit,
    onProfileChange: (OutputProfile) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SetupRow(stringResource(Res.string.output_profile_display_mode)) {
            DisplayModeSelector(profile, onProfileChange)
        }
        ProfileContentSection(
            profile = profile,
            open = contentOpen,
            onOpenChange = onContentOpenChange,
            onProfileChange = onProfileChange,
        )
        SetupRow(stringResource(Res.string.output_profile_sources)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                BibleSourcePicker(
                    profile = profile,
                    stack = bibleTranslationChoices(settings),
                    onProfileChange = onProfileChange,
                    modifier = Modifier.weight(1f),
                )
                SongSourcePicker(
                    profile = profile,
                    languages = songLanguageChoices(settings),
                    onProfileChange = onProfileChange,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // Only for what this profile shows: a screen that never draws a picture has nothing to fit.
        // Nor on a stage monitor, which always fits its slide and video into their zone.
        val stageMonitor = profile.displayMode == Constants.DISPLAY_MODE_STAGE_MONITOR
        if (!stageMonitor && (profile.showPictures || profile.showMedia)) {
            SetupRow(stringResource(Res.string.output_profile_scale)) {
                ProfileScaleRow(profile, onProfileChange)
            }
        }
        // A lower third only: on a full screen there is nowhere else for the content to go.
        if (profile.isLowerThird && profile.placeableShown().isNotEmpty()) {
            SetupRow(stringResource(Res.string.output_profile_placement)) {
                ProfilePlacementRow(profile, onProfileChange)
            }
        }
    }
}

/** A caption and its control on one line, the caption at a fixed width so the controls align. */
@Composable
private fun SetupRow(caption: String, control: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = caption,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp).width(SETUP_CAPTION_WIDTH),
        )
        control()
    }
}

/**
 * Which part of the profile's look the column below edits: Bible, Songs or Background -- or, on a
 * stage monitor, its zone layout, which is the only thing it has to style.
 */
@Composable
private fun StyleTabs(panes: List<CustomizePane>, selected: CustomizePane?, onSelect: (CustomizePane) -> Unit) {
    if (panes.isEmpty() || selected == null) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.output_profile_style),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp).width(SETUP_CAPTION_WIDTH),
        )
        if (panes.size == 1) {
            val only = panes.first()
            Text(
                text = only.label(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag(railTag(only.name)),
            )
        } else {
            // Up to seven categories, so it folds onto a second row of equal segments rather than
            // running past the column.
            CustomizeSelectorRow(
                items = panes.map { SegmentedButtonItem(it, it.label(), testTag = railTag(it.name)) },
                selected = selected,
                onSelect = onSelect,
                modifier = Modifier.weight(1f),
                banded = false,
                segmentHeight = STYLE_TAB_HEIGHT,
                fontSize = MaterialTheme.typography.labelLarge.fontSize,
            )
        }
    }
}

private val STYLE_TAB_HEIGHT = 32.dp

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
        buttonWidth = 132.dp,
        buttonHeight = 32.dp,
        fontSize = MaterialTheme.typography.labelLarge.fontSize,
    )
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

/** A song's four language slots as pickable choices, named as the Song settings name them. */
@Composable
private fun songLanguageChoices(settings: AppSettings): List<TranslationChoiceDisplay> =
    List(MAX_SONG_TRANSLATIONS) { slot ->
        TranslationChoiceDisplay(
            code = (slot + 1).toString(),
            title = songLanguageName(settings.songSettings, slot),
            portion = "",
        )
    }
