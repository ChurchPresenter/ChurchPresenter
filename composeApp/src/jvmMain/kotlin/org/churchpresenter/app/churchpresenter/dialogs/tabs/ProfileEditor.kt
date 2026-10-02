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
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.ok
import org.churchpresenter.strings.generated.resources.output_profile_delete
import org.churchpresenter.strings.generated.resources.output_profile_delete_blocked
import org.churchpresenter.strings.generated.resources.output_profile_delete_confirm
import org.churchpresenter.strings.generated.resources.profile_delete_blocked_master
import org.churchpresenter.strings.generated.resources.profile_mode_locked_sub
import org.churchpresenter.strings.generated.resources.profile_page_not_shown
import org.churchpresenter.sharedui.composables.SettingsScrollbar
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.OutputStyleScope
import org.churchpresenter.settings.overrideCount
import org.churchpresenter.settings.resolvedFor
import org.churchpresenter.settings.withValueAt
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
    /** Gives the values under these paths back to the profile's master. */
    onRevert: (Collection<String>) -> Unit,
    linkActions: ProfileLinkActions,
    modifier: Modifier = Modifier,
) {
    // Forgotten when the display mode changes: a stage monitor's pages are not a full screen's.
    val shownMode = shownDisplayMode(profile.displayMode)
    var pickedElement by remember(profile.id, shownMode) { mutableStateOf<CustomizeElement?>(null) }
    // All by default: one look for the whole stack is the usual case, and a single translation is
    // picked out when it needs a look of its own.
    var translationIndex by remember(profile.id) { mutableStateOf(ALL_TRANSLATIONS) }
    var query by remember { mutableStateOf("") }
    var onlyChanges by remember(profile.id) { mutableStateOf(false) }

    // A page the profile's mode no longer offers -- the stage layout after switching to a full
    // screen -- falls back to General rather than drawing nothing.
    val sections = profileNavSections(profile)
    val shownPage = page.takeIf { p -> sections.any { p in it.pages } } ?: ProfilePage.General
    val pane = (shownPage as? ProfilePage.Appearance)?.pane
    val previewPane = pane ?: stylePanesFor(profile).firstOrNull { it != CustomizePane.STAGE_MONITOR }
    val elements = previewPane?.let { styleElementsFor(it, profile) }.orEmpty()
    val element = pickedElement?.takeIf { it in elements } ?: elements.firstOrNull()
    val songTargets = rememberSongTargets(profile.id, element) { pickedElement = it }

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
    val prefixes = shownPage.pathPrefixes()
    val link = settings.projectionSettings.linkOf(profile, onlyChanges && prefixes.isNotEmpty(), onRevert)

    CompositionLocalProvider(LocalProfileLink provides link) {
        Row(modifier = modifier) {
            ProfileSectionNav(
                profile = profile,
                selected = shownPage,
                onSelect = onPageChange,
                query = query,
                onQueryChange = { query = it },
                pageMatches = { p -> searchIndex[p].orEmpty().contains(query.trim(), ignoreCase = true) },
                badge = { p -> LinkNavBadge(link, p) },
            )
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ProfileSettingsColumn(
                header = { EditorHeader(link, shownPage, usedBy, detail, onSettingsChange, onPageChange) },
                detail = if (shownPage.hasDetailSwitch) detail else SettingsDetail.ADVANCED,
                query = query,
                scope = scope,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                // Keyed on the page and the profile: one page's fields must never hand their typing to
                // the same slot of the next.
                key(profile.id, shownPage) {
                    LinkBanner(link, prefixes, linkActions, onOnlyChanges = { onlyChanges = it })
                    PageBody(
                        page = shownPage,
                        settings = settings,
                        draft = resolved,
                        profile = profile,
                        element = element,
                        onElementChange = { pickedElement = it },
                        translationIndex = translationIndex,
                        onTranslationChange = { translationIndex = it },
                        songTargets = songTargets,
                        onDraftSettingsChange = onDraftSettingsChange,
                        onSettingsChange = onSettingsChange,
                        onProfileChange = onProfileChange,
                        onRename = onRename,
                        onDuplicate = onDuplicate,
                        onRequestDelete = onRequestDelete,
                        onIdentify = onIdentify,
                        onOpenPage = onPageChange,
                        linkActions = linkActions,
                    )
                }
            }
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CompositionLocalProvider(LocalOutputStyleScope provides scope) {
                EditorPreview(
                    pane = previewPane,
                    pageLabel = previewPane?.navLabel() ?: shownPage.label(),
                    element = element,
                    draft = resolved,
                    profile = profile,
                    usedBy = usedBy,
                    onProfileChange = onProfileChange,
                    onOpenOutputs = { onPageChange(ProfilePage.Outputs) },
                    // The handles act on the page being edited only, never on the picture another shows.
                    adjustModel = adjustModelFor(
                        pane, resolved, profile, Adjustable(element) { pickedElement = it }, onDraftSettingsChange,
                        Adjustable(translationIndex) { translationIndex = it },
                        songTargets,
                    ),
                ) {
                    LinkContextCard(
                        link = link,
                        actions = linkActions,
                        onOpenPage = onPageChange,
                        onValueChange = { path, value -> onProfileChange(profile.withValueAt(path, value)) },
                        onProfileChange = onProfileChange,
                    )
                }
            }
        }
    }
}

/**
 * Where the Songs page's Text rows point, held here for the preview to pick as well as the page:
 * the element, the title slide's own one, and the language -- All by default, since one look for
 * every language is the usual case. A song's languages are not a Bible's translations.
 */
@Composable
private fun rememberSongTargets(
    profileId: String,
    element: CustomizeElement?,
    onElementChange: (CustomizeElement) -> Unit,
): SongTargets {
    var language by remember(profileId) { mutableStateOf<SongStyleLanguage?>(null) }
    var slideElement by remember(profileId) { mutableStateOf(SongStyleElement.TITLE) }
    return SongTargets(
        element = Adjustable(element ?: CustomizeElement.SONG_LYRICS, onElementChange),
        slideElement = Adjustable(slideElement) { slideElement = it },
        language = Adjustable(language) { language = it },
    )
}

/** The settings column's header, with Basic / Advanced written straight to the document. */
@Composable
private fun EditorHeader(
    link: ProfileLink,
    page: ProfilePage,
    usedBy: List<String>,
    detail: SettingsDetail,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onPageChange: (ProfilePage) -> Unit,
) {
    ProfilePageHeader(
        profile = link.profile,
        page = page,
        usedBy = usedBy,
        detail = detail,
        onDetailChange = { d -> onSettingsChange { it.copy(profilesAdvanced = d == SettingsDetail.ADVANCED) } },
        onAssignOutput = { onPageChange(ProfilePage.Outputs) },
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 4.dp),
        linkState = { LinkHeaderState(link) },
    )
}

/** How many of [page]'s settings a linked profile has changed, beside the page's name. */
@Composable
private fun LinkNavBadge(link: ProfileLink, page: ProfilePage) {
    val prefixes = page.pathPrefixes()
    val count = if (link.isLinked && prefixes.isNotEmpty()) overrideCount(link.profile, prefixes) else 0
    if (count > 0) ChangesChip(count, short = true)
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
    songTargets: SongTargets,
    onDraftSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onProfileChange: (OutputProfile) -> Unit,
    onRename: (String) -> Unit,
    onDuplicate: () -> Unit,
    onRequestDelete: () -> Unit,
    onIdentify: () -> Unit,
    onOpenPage: (ProfilePage) -> Unit,
    linkActions: ProfileLinkActions,
) {
    if (!page.isShownBy(profile)) NotShownNote(page)
    when (page) {
        ProfilePage.General -> GeneralPageWithLinks(
            settings = settings,
            profile = profile,
            onProfileChange = onProfileChange,
            onRename = onRename,
            onDuplicate = onDuplicate,
            onRequestDelete = onRequestDelete,
            linkActions = linkActions,
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
                targets = songTargets,
            )
            CustomizePane.BACKGROUND ->
                ProfileBackgroundPage(draft, profile, onProfileChange, onDraftSettingsChange, onOpenPage)
            CustomizePane.DICTIONARY -> ProfileDictionaryPage(draft, onDraftSettingsChange)
            CustomizePane.STAGE_MONITOR -> ProfileStagePage(draft, onDraftSettingsChange)
            CustomizePane.CAPTIONS -> ProfileCaptionsPage(draft, onDraftSettingsChange)
            CustomizePane.SUBTITLES -> ProfileSubtitlesPage(draft, onDraftSettingsChange)
            CustomizePane.QA -> ProfileQaPage(draft, onDraftSettingsChange)
        }
    }
}

/** General, with the profile's link: its Linking card, Create linked profile, and what the link locks. */
@Composable
private fun GeneralPageWithLinks(
    settings: AppSettings,
    profile: OutputProfile,
    onProfileChange: (OutputProfile) -> Unit,
    onRename: (String) -> Unit,
    onDuplicate: () -> Unit,
    onRequestDelete: () -> Unit,
    linkActions: ProfileLinkActions,
) {
    val link = LocalProfileLink.current ?: return
    val master = link.master
    val candidates = settings.projectionSettings.outputProfiles.filter { it.parentId == null && it.id != profile.id }
    ProfileGeneralPage(
        profile = profile,
        onProfileChange = onProfileChange,
        onRename = onRename,
        onDuplicate = onDuplicate,
        onRequestDelete = onRequestDelete,
        modeLocked = master != null,
        modeSub = master?.let { stringResource(Res.string.profile_mode_locked_sub, it.displayName()) },
        extraGroups = { LinkingGroup(link, candidates, linkActions) },
        extraActions = { if (master == null) CreateLinkedAction(linkActions.onCreateLinked) },
        deleteBlockedNote = if (link.followers.isNotEmpty()) {
            stringResource(Res.string.profile_delete_blocked_master)
        } else {
            null
        },
    )
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
