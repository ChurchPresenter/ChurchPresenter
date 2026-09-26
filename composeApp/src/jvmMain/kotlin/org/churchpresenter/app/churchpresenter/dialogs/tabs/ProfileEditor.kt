package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.ok
import churchpresenter.composeapp.generated.resources.output_profile_delete
import churchpresenter.composeapp.generated.resources.output_profile_delete_blocked
import churchpresenter.composeapp.generated.resources.output_profile_delete_confirm
import churchpresenter.composeapp.generated.resources.profile_page_not_shown
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbar
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.OutputStyleScope
import org.churchpresenter.settings.resolvedFor
import org.churchpresenter.theme.components.GhostButton
import org.jetbrains.compose.resources.stringResource

/**
 * Everything to the right of the profile list: the section list, the page of settings, and the
 * preview beside them.
 *
 * Every setting of the profile lives on exactly one page, and the preview column holds none -- it
 * only draws the page being edited, at the shape the operator picks.
 */
@Composable
internal fun ProfileEditor(
    settings: AppSettings,
    profile: OutputProfile,
    /** Every output drawing with this profile, labelled the way its own card labels it. */
    usedBy: List<String>,
    page: ProfilePage,
    onPageChange: (ProfilePage) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onProfileChange: (OutputProfile) -> Unit,
    onRename: (String) -> Unit,
    onDuplicate: () -> Unit,
    onRequestDelete: () -> Unit,
    onIdentify: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Forgotten when the display mode changes: a stage monitor's pages are not a full screen's.
    val shownMode = shownDisplayMode(profile.displayMode)
    var pickedElement by remember(profile.id, shownMode) { mutableStateOf<CustomizeElement?>(null) }
    // All by default: one look for the whole stack is the usual case, and a single translation is
    // picked out when it needs a look of its own.
    var translationIndex by remember(profile.id) { mutableStateOf(ALL_TRANSLATIONS) }
    // The preview's sample and background are this session's checking, never the profile.
    var sampleSlot by remember { mutableStateOf(PreviewSampleSlot.MEDIUM) }
    var backgroundMode by remember { mutableStateOf(PreviewBackgroundMode.ACTUAL) }
    var query by remember { mutableStateOf("") }

    // A page the profile's mode no longer offers -- the stage layout after switching to a full
    // screen -- falls back to General rather than drawing nothing.
    val sections = profileNavSections(profile)
    val shownPage = page.takeIf { p -> sections.any { p in it.pages } } ?: ProfilePage.General
    val pane = (shownPage as? ProfilePage.Appearance)?.pane
    val previewPane = pane ?: stylePanesFor(profile).firstOrNull { it != CustomizePane.STAGE_MONITOR }
    val elements = previewPane?.let { styleElementsFor(it, profile) }.orEmpty()
    val element = pickedElement?.takeIf { it in elements } ?: elements.firstOrNull()

    val resolved = remember(settings, profile) { settings.resolvedFor(profile) }
    val onDraftSettingsChange: ((AppSettings) -> AppSettings) -> Unit = { transform ->
        val updated = transform(resolved)
        onProfileChange(profile.withStylingFrom(updated))
        // Anything the page touched outside the profile-owned fields is genuinely global and goes to
        // the real document -- as a delta, never as a snapshot: `resolved` is stale the instant
        // `onProfileChange` above has run, so handing any of it back wholesale reverts that write.
        if (updated.stockPhotoSettings != resolved.stockPhotoSettings) {
            onSettingsChange { real -> real.copy(stockPhotoSettings = updated.stockPhotoSettings) }
        }
    }
    val searchIndex = profileSearchIndex(profile)
    val detail = if (settings.profilesAdvanced) SettingsDetail.ADVANCED else SettingsDetail.BASIC
    val scope = if (profile.isLowerThird) OutputStyleScope.LOWER_THIRD else OutputStyleScope.FULL_SCREEN

    Row(modifier = modifier) {
        ProfileSectionNav(
            profile = profile,
            selected = shownPage,
            onSelect = onPageChange,
            query = query,
            onQueryChange = { query = it },
            pageMatches = { p -> searchIndex[p].orEmpty().contains(query.trim(), ignoreCase = true) },
        )
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ProfileSettingsColumn(
            header = {
                ProfilePageHeader(
                    profile = profile,
                    page = shownPage,
                    usedBy = usedBy,
                    detail = detail,
                    onDetailChange = { d ->
                        onSettingsChange { it.copy(profilesAdvanced = d == SettingsDetail.ADVANCED) }
                    },
                    onAssignOutput = { onPageChange(ProfilePage.Outputs) },
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 4.dp),
                )
            },
            detail = if (shownPage.hasDetailSwitch) detail else SettingsDetail.ADVANCED,
            query = query,
            scope = scope,
            // The dictionary form is still the tab it was, and scrolls itself.
            selfScrolling = pane == CustomizePane.DICTIONARY,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            // Keyed on the page and the profile: one page's fields must never hand their typing to
            // the same slot of the next.
            key(profile.id, shownPage) {
                PageBody(
                    page = shownPage,
                    settings = settings,
                    draft = resolved,
                    profile = profile,
                    element = element,
                    onElementChange = { pickedElement = it },
                    translationIndex = translationIndex,
                    onTranslationChange = { translationIndex = it },
                    onDraftSettingsChange = onDraftSettingsChange,
                    onSettingsChange = onSettingsChange,
                    onProfileChange = onProfileChange,
                    onRename = onRename,
                    onDuplicate = onDuplicate,
                    onRequestDelete = onRequestDelete,
                    onIdentify = onIdentify,
                    onOpenPage = onPageChange,
                )
            }
        }
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        CompositionLocalProvider(LocalOutputStyleScope provides scope) {
            ProfilePreviewColumn(
                pane = previewPane,
                pageLabel = previewPane?.navLabel() ?: shownPage.label(),
                element = element,
                draft = resolved,
                profile = profile,
                usedBy = usedBy,
                onProfileFieldChange = onProfileChange,
                slot = sampleSlot,
                onSlotChange = { sampleSlot = it },
                backgroundMode = backgroundMode,
                onBackgroundModeChange = { backgroundMode = it },
                onOpenOutputs = { onPageChange(ProfilePage.Outputs) },
                contextCard = { StandaloneContextCard(onOpenGeneral = { onPageChange(ProfilePage.General) }) },
            )
        }
    }
}

/**
 * The settings column: [header] over a scrolling page. The page's groups read Basic / Advanced, the
 * search and the output's shape from the composition, so they are provided here once.
 */
@Composable
private fun ProfileSettingsColumn(
    header: @Composable () -> Unit,
    detail: SettingsDetail,
    query: String,
    scope: OutputStyleScope,
    selfScrolling: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.background(profilesPalette().page)) {
        header()
        CompositionLocalProvider(
            LocalSettingsDetail provides detail,
            LocalSettingsQuery provides query,
            LocalOutputStyleScope provides scope,
        ) {
            if (selfScrolling) {
                Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), content = content)
                return@CompositionLocalProvider
            }
            val scroll = rememberScrollState()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scroll)
                        .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    content = content,
                )
                SettingsScrollbar(scroll)
            }
        }
    }
}

/** One page's groups. */
@Composable
private fun ColumnScope.PageBody(
    page: ProfilePage,
    settings: AppSettings,
    draft: AppSettings,
    profile: OutputProfile,
    element: CustomizeElement?,
    onElementChange: (CustomizeElement) -> Unit,
    translationIndex: Int,
    onTranslationChange: (Int) -> Unit,
    onDraftSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onProfileChange: (OutputProfile) -> Unit,
    onRename: (String) -> Unit,
    onDuplicate: () -> Unit,
    onRequestDelete: () -> Unit,
    onIdentify: () -> Unit,
    onOpenPage: (ProfilePage) -> Unit,
) {
    if (!page.isShownBy(profile)) NotShownNote(page)
    when (page) {
        ProfilePage.General -> ProfileGeneralPage(
            profile = profile,
            onProfileChange = onProfileChange,
            onRename = onRename,
            onDuplicate = onDuplicate,
            onRequestDelete = onRequestDelete,
        )
        ProfilePage.Outputs -> ProfileOutputsPage(
            profile = profile,
            profiles = settings.projectionSettings.outputProfiles,
            proj = settings.projectionSettings,
            onProjectionChange = { transform ->
                onSettingsChange { it.copy(projectionSettings = transform(it.projectionSettings)) }
            },
            onIdentify = onIdentify,
        )
        ProfilePage.Content -> ProfileContentPage(draft, profile, onProfileChange)
        is ProfilePage.Appearance -> when (page.pane) {
            CustomizePane.BIBLE -> ProfileBiblePage(
                draft = draft,
                profile = profile,
                translationIndex = translationIndex,
                onTranslationChange = onTranslationChange,
                element = element ?: CustomizeElement.BIBLE_TEXT,
                onElementChange = onElementChange,
                onSettingsChange = onDraftSettingsChange,
                onProfileChange = onProfileChange,
                onOpenPage = onOpenPage,
            )
            CustomizePane.SONGS -> ProfileSongsPage(
                draft = draft,
                profile = profile,
                element = element ?: CustomizeElement.SONG_LYRICS,
                onElementChange = onElementChange,
                onSettingsChange = onDraftSettingsChange,
                onProfileChange = onProfileChange,
                onOpenPage = onOpenPage,
            )
            CustomizePane.BACKGROUND ->
                ProfileBackgroundPage(draft, profile, onProfileChange, onDraftSettingsChange, onOpenPage)
            // The whole tab it always was, given the column's height; it scrolls itself.
            CustomizePane.DICTIONARY -> DictionarySettingsTab(
                settings = draft,
                onSettingsChange = onDraftSettingsChange,
            )
            else -> ProfileFormPage(page.pane, draft, onDraftSettingsChange)
        }
    }
}

/** At the top of a page whose content the profile does not show. */
@Composable
private fun NotShownNote(page: ProfilePage) {
    Text(
        text = stringResource(Res.string.profile_page_not_shown, page.label()),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

/**
 * [this] carrying [updated]'s styling: every profile-owned settings object, taken whole from the
 * resolved document an edit was made against.
 *
 * Written whole even when only some background surfaces are overridden: resolution reads the
 * profile's copy of a followed surface not at all, so carrying it costs nothing and is what lets
 * "take this one over" start from the picture already on screen.
 */
internal fun OutputProfile.withStylingFrom(updated: AppSettings): OutputProfile = copy(
    stageMonitorSettings = updated.stageMonitorSettings,
    backgroundSettings = updated.backgroundSettings,
    songSettings = updated.songSettings,
    bibleSettings = updated.bibleSettings,
    sttSettings = updated.sttSettings,
    qaSettings = updated.qaSettings,
    mediaSettings = updated.mediaSettings,
    dictionarySettings = updated.dictionarySettings,
)

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
