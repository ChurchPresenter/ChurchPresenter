package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.appearance
import churchpresenter.composeapp.generated.resources.background
import churchpresenter.composeapp.generated.resources.bible
import churchpresenter.composeapp.generated.resources.content_outputs
import churchpresenter.composeapp.generated.resources.content_outputs_enabled_short
import churchpresenter.composeapp.generated.resources.detected_screens
import churchpresenter.composeapp.generated.resources.display_fullscreen
import churchpresenter.composeapp.generated.resources.display_mode
import churchpresenter.composeapp.generated.resources.ic_app_icon
import churchpresenter.composeapp.generated.resources.ic_settings
import churchpresenter.composeapp.generated.resources.identify_screen
import churchpresenter.composeapp.generated.resources.key_output
import churchpresenter.composeapp.generated.resources.key_output_none
import churchpresenter.composeapp.generated.resources.loading
import churchpresenter.composeapp.generated.resources.media
import churchpresenter.composeapp.generated.resources.menu_language
import churchpresenter.composeapp.generated.resources.presenter_windows_count
import churchpresenter.composeapp.generated.resources.projection
import churchpresenter.composeapp.generated.resources.projection_auto_display
import churchpresenter.composeapp.generated.resources.projection_target_display
import churchpresenter.composeapp.generated.resources.screen_assignment
import churchpresenter.composeapp.generated.resources.setup_bible_choose_folder
import churchpresenter.composeapp.generated.resources.setup_bible_pick_translations
import churchpresenter.composeapp.generated.resources.setup_language_count
import churchpresenter.composeapp.generated.resources.setup_language_none
import churchpresenter.composeapp.generated.resources.setup_language_search
import churchpresenter.composeapp.generated.resources.setup_media_ready
import churchpresenter.composeapp.generated.resources.setup_media_why_body
import churchpresenter.composeapp.generated.resources.setup_media_why_title
import churchpresenter.composeapp.generated.resources.setup_proj_assign_note
import churchpresenter.composeapp.generated.resources.setup_proj_lang_note
import churchpresenter.composeapp.generated.resources.setup_proj_rows_note
import churchpresenter.composeapp.generated.resources.setup_proj_step1
import churchpresenter.composeapp.generated.resources.setup_proj_step2
import churchpresenter.composeapp.generated.resources.setup_proj_step5
import churchpresenter.composeapp.generated.resources.setup_proj_subtitle
import churchpresenter.composeapp.generated.resources.setup_proj_tip
import churchpresenter.composeapp.generated.resources.setup_proj_tip2
import churchpresenter.composeapp.generated.resources.setup_proj_title
import churchpresenter.composeapp.generated.resources.setup_rail_appearance
import churchpresenter.composeapp.generated.resources.setup_rail_ready
import churchpresenter.composeapp.generated.resources.setup_rail_songs
import churchpresenter.composeapp.generated.resources.setup_rail_welcome
import churchpresenter.composeapp.generated.resources.setup_songs_converter_body
import churchpresenter.composeapp.generated.resources.setup_songs_converter_button
import churchpresenter.composeapp.generated.resources.setup_songs_format_note
import churchpresenter.composeapp.generated.resources.setup_songs_samples_note
import churchpresenter.composeapp.generated.resources.setup_step0_subtitle
import churchpresenter.composeapp.generated.resources.setup_step0_title
import churchpresenter.composeapp.generated.resources.setup_step1_body
import churchpresenter.composeapp.generated.resources.setup_step1_theme_subtitle
import churchpresenter.composeapp.generated.resources.setup_step1_theme_title
import churchpresenter.composeapp.generated.resources.setup_step1_title
import churchpresenter.composeapp.generated.resources.setup_step2_download_hint
import churchpresenter.composeapp.generated.resources.setup_step2_step1
import churchpresenter.composeapp.generated.resources.setup_step2_step2
import churchpresenter.composeapp.generated.resources.setup_step2_step5
import churchpresenter.composeapp.generated.resources.setup_step2_subtitle
import churchpresenter.composeapp.generated.resources.setup_step2_tip
import churchpresenter.composeapp.generated.resources.setup_step2_tip2
import churchpresenter.composeapp.generated.resources.setup_step2_title
import churchpresenter.composeapp.generated.resources.setup_step3_step1
import churchpresenter.composeapp.generated.resources.setup_step3_step2
import churchpresenter.composeapp.generated.resources.setup_step3_step3
import churchpresenter.composeapp.generated.resources.setup_step3_step4
import churchpresenter.composeapp.generated.resources.setup_step3_subtitle
import churchpresenter.composeapp.generated.resources.setup_step3_title
import churchpresenter.composeapp.generated.resources.setup_step4_body
import churchpresenter.composeapp.generated.resources.setup_step4_hint
import churchpresenter.composeapp.generated.resources.setup_step4_title
import churchpresenter.composeapp.generated.resources.setup_step5_download
import churchpresenter.composeapp.generated.resources.setup_step5_download_intel
import churchpresenter.composeapp.generated.resources.setup_step5_download_silicon
import churchpresenter.composeapp.generated.resources.setup_step5_linux_tip
import churchpresenter.composeapp.generated.resources.setup_step5_recheck
import churchpresenter.composeapp.generated.resources.setup_step5_subtitle
import churchpresenter.composeapp.generated.resources.setup_step5_title
import churchpresenter.composeapp.generated.resources.setup_step5_vlc_load_failed
import churchpresenter.composeapp.generated.resources.setup_step5_vlc_load_failed_detail
import churchpresenter.composeapp.generated.resources.setup_step5_vlc_missing
import churchpresenter.composeapp.generated.resources.setup_step5_vlc_ok
import churchpresenter.composeapp.generated.resources.setup_step5_vlc_wrong_arch
import churchpresenter.composeapp.generated.resources.setup_step5_vlc_wrong_arch_detail
import churchpresenter.composeapp.generated.resources.setup_summary_bible
import churchpresenter.composeapp.generated.resources.setup_summary_bible_value
import churchpresenter.composeapp.generated.resources.setup_summary_songs_value
import churchpresenter.composeapp.generated.resources.setup_theme_section_dark
import churchpresenter.composeapp.generated.resources.setup_theme_section_light
import churchpresenter.composeapp.generated.resources.setup_theme_section_system
import churchpresenter.composeapp.generated.resources.setup_theme_count
import churchpresenter.composeapp.generated.resources.setup_welcome_bible_body
import churchpresenter.composeapp.generated.resources.setup_welcome_bible_title
import churchpresenter.composeapp.generated.resources.setup_welcome_card_step
import churchpresenter.composeapp.generated.resources.setup_welcome_projection_body
import churchpresenter.composeapp.generated.resources.setup_welcome_projection_title
import churchpresenter.composeapp.generated.resources.setup_welcome_songs_body
import churchpresenter.composeapp.generated.resources.setup_welcome_songs_title
import churchpresenter.composeapp.generated.resources.setup_wizard_done
import churchpresenter.composeapp.generated.resources.shortcut_description_settings
import churchpresenter.composeapp.generated.resources.song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.composables.CopyLinkIconButton
import org.churchpresenter.app.churchpresenter.composables.isVlcArchMismatch
import org.churchpresenter.app.churchpresenter.composables.isVlcAvailable
import org.churchpresenter.app.churchpresenter.composables.isVlcLoadFailed
import org.churchpresenter.app.churchpresenter.composables.recheckVlcAvailability
import org.churchpresenter.app.churchpresenter.data.Language
import org.churchpresenter.app.churchpresenter.ui.theme.LanguageProvider
import org.churchpresenter.app.churchpresenter.ui.theme.themeDisplayName
import org.churchpresenter.app.churchpresenter.utils.AppWindowRoot
import org.churchpresenter.app.churchpresenter.utils.SystemClipboard
import org.churchpresenter.app.churchpresenter.utils.UrlOpener
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.theme.colorSchemeFor
import org.churchpresenter.theme.isLightTheme
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/*
 * Getting Started — eight steps in a two-column window.
 *
 * The rail on the left is the whole sequence at once, each row carrying the choice made on it; the
 * panel on the right is one step. That replaced a single centred column under a row of dots, which
 * could only be walked with Back and Next and, at a fixed 700x620, cut the language chips off mid-row
 * with nothing on screen to say the area scrolled.
 *
 * The chrome lives in SetupWizardShell.kt and the last step's figures in SetupSummary.kt; this
 * file is the eight steps themselves.
 */

private const val STEP_LANGUAGE = 0
private const val STEP_APPEARANCE = 1
private const val STEP_WELCOME = 2
private const val STEP_BIBLE = 3
private const val STEP_SONGS = 4
private const val STEP_PROJECTION = 5
private const val STEP_MEDIA = 6
private const val STEP_READY = 7
private const val TOTAL_STEPS = 8

private val PANEL_PADDING = 28.dp
private val PANEL_GAP = 20.dp
private const val SWATCH_COLUMNS = 3
private const val DIM_ALPHA = 0.75f

@Composable
fun SetupWizardDialog(
    theme: ThemeMode,
    selectedLanguage: Language,
    alwaysOnTop: Boolean = true,
    bibleDirectory: String = "",
    songsDirectory: String = "",
    onLanguageSelected: (Language) -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenConverter: () -> Unit = {},
    onDismiss: () -> Unit
) {
    // Roomier than the 700x620 it replaces, and resizable now: the rail plus a panel wide enough
    // for 35 language chips does not fit the old box, and a window the user cannot resize is what
    // turned "too many chips" into "four languages you cannot reach".
    val windowState = rememberWindowState(
        width = 1120.dp,
        height = 760.dp,
        position = WindowPosition(Alignment.Center)
    )

    Window(
        onCloseRequest = onDismiss,
        title = "Getting Started",
        icon = painterResource(Res.drawable.ic_app_icon),
        state = windowState,
        resizable = true,
        alwaysOnTop = alwaysOnTop
    ) {
        SetupWizardContent(
            theme = theme,
            selectedLanguage = selectedLanguage,
            bibleDirectory = bibleDirectory,
            songsDirectory = songsDirectory,
            onLanguageSelected = onLanguageSelected,
            onThemeSelected = onThemeSelected,
            onOpenSettings = onOpenSettings,
            onOpenConverter = onOpenConverter,
            onDismiss = onDismiss
        )
    }
}

/**
 * Everything the wizard window contains: the rail, the step being shown and its footer.
 *
 * Held apart from [SetupWizardDialog] because that function's only other statement is the `Window`
 * it opens, which cannot be composed on a headless machine. Keeping the window down to that one call
 * leaves the wizard's actual behaviour — which step follows which, and which buttons a step offers —
 * reachable from a test.
 */
@Composable
internal fun SetupWizardContent(
    theme: ThemeMode,
    selectedLanguage: Language,
    bibleDirectory: String = "",
    songsDirectory: String = "",
    onLanguageSelected: (Language) -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenConverter: () -> Unit = {},
    onDismiss: () -> Unit,
    loadSummary: suspend () -> SetupSummary = { loadSetupSummary(bibleDirectory, songsDirectory) },
) {
    var step by remember { mutableStateOf(0) }
    var goingForward by remember { mutableStateOf(true) }
    var summary by remember { mutableStateOf<SetupSummary?>(null) }

    // Counted when the last step is first reached rather than at construction: the two folders it
    // reports on are chosen on steps 4 and 5, so a scan run any earlier reports the state the user
    // arrived with instead of the one they just set up.
    LaunchedEffect(step) {
        if (step == STEP_READY && summary == null) summary = loadSummary()
    }

    val themeName = themeDisplayName(theme)
    val railSteps = listOf(
        WizardRailStep(stringResource(Res.string.menu_language), selectedLanguage.nativeName),
        WizardRailStep(stringResource(Res.string.setup_rail_appearance), themeName),
        WizardRailStep(stringResource(Res.string.setup_rail_welcome)),
        WizardRailStep(stringResource(Res.string.bible)),
        WizardRailStep(stringResource(Res.string.setup_rail_songs)),
        WizardRailStep(stringResource(Res.string.projection)),
        WizardRailStep(stringResource(Res.string.media)),
        WizardRailStep(stringResource(Res.string.setup_rail_ready)),
    )

    LanguageProvider(language = selectedLanguage) {
        AppWindowRoot(theme = theme) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Row(modifier = Modifier.fillMaxSize()) {
                    WizardRail(
                        steps = railSteps,
                        currentStep = step,
                        onSelectStep = { target ->
                            goingForward = target > step
                            step = target
                        },
                        onSkip = onDismiss,
                    )
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        AnimatedContent(
                            targetState = step,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            transitionSpec = {
                                val shift = if (goingForward) 1 else -1
                                (slideInHorizontally { it * shift / SLIDE_DIVISOR } + fadeIn()) togetherWith
                                    (slideOutHorizontally { -it * shift / SLIDE_DIVISOR } + fadeOut())
                            },
                            label = "wizard_step"
                        ) { currentStep ->
                            WizardStep(
                                step = currentStep,
                                theme = theme,
                                selectedLanguage = selectedLanguage,
                                summary = summary,
                                onLanguageSelected = onLanguageSelected,
                                onThemeSelected = onThemeSelected,
                                onOpenSettings = onOpenSettings,
                                onOpenConverter = onOpenConverter,
                                onGoToStep = { target ->
                                    goingForward = target > step
                                    step = target
                                },
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        WizardPanelFooter(
                            canGoBack = step > 0,
                            isLastStep = step == TOTAL_STEPS - 1,
                            status = footerStatus(step),
                            continueLabel = stringResource(Res.string.setup_wizard_done),
                            onBack = {
                                goingForward = false
                                step--
                            },
                            onContinue = {
                                if (step == TOTAL_STEPS - 1) {
                                    onDismiss()
                                } else {
                                    goingForward = true
                                    step++
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** How far a step slides in and out. A full width reads as a page turn; a third reads as a step. */
private const val SLIDE_DIVISOR = 3

/** The line the footer shows for the step on screen — a count, a state, or nothing. */
@Composable
private fun footerStatus(step: Int): String = when (step) {
    STEP_LANGUAGE -> stringResource(
        Res.string.setup_language_count,
        Language.entries.size,
        Language.entries.size,
    )
    STEP_APPEARANCE -> stringResource(Res.string.setup_theme_count, ThemeMode.entries.size)
    STEP_MEDIA -> if (isVlcAvailable) stringResource(Res.string.setup_media_ready) else ""
    else -> ""
}

/** The panel body for one step, scrolled and padded the same way for all eight. */
@Composable
private fun WizardStep(
    step: Int,
    theme: ThemeMode,
    selectedLanguage: Language,
    summary: SetupSummary?,
    onLanguageSelected: (Language) -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenConverter: () -> Unit,
    onGoToStep: (Int) -> Unit,
) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(PANEL_PADDING),
            verticalArrangement = Arrangement.spacedBy(PANEL_GAP),
        ) {
            when (step) {
                STEP_LANGUAGE -> LanguageStep(selectedLanguage, onLanguageSelected)
                STEP_APPEARANCE -> AppearanceStep(theme, onThemeSelected)
                STEP_WELCOME -> WelcomeStep(onGoToStep)
                STEP_BIBLE -> BibleStep(onOpenSettings)
                STEP_SONGS -> SongsStep(onOpenSettings, onOpenConverter)
                STEP_PROJECTION -> ProjectionStep(onOpenSettings)
                STEP_MEDIA -> VlcStep()
                STEP_READY -> ReadyStep(selectedLanguage, theme, summary)
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

// ── Step 1: language ─────────────────────────────────────────────────────────────────────────

/**
 * Every interface language as a chip, with a search field above them.
 *
 * The search exists because 35 chips is more than a first-time user reads: typing two letters of
 * their own language beats scanning six rows of scripts they cannot read. The count in the footer
 * moves with the filter, so an empty result is legible rather than looking like a broken list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageStep(selectedLanguage: Language, onLanguageSelected: (Language) -> Unit) {
    var query by remember { mutableStateOf("") }
    val matches = remember(query) {
        if (query.isBlank()) {
            Language.entries.toList()
        } else {
            Language.entries.filter { language ->
                language.nativeName.contains(query, ignoreCase = true) ||
                    language.name.contains(query, ignoreCase = true) ||
                    language.code.equals(query, ignoreCase = true)
            }
        }
    }

    WizardPanelHeader(
        icon = Icons.Filled.Language,
        title = stringResource(Res.string.setup_step0_title),
        subtitle = stringResource(Res.string.setup_step0_subtitle),
    )
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        singleLine = true,
        shape = RoundedCornerShape(9.dp),
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        placeholder = {
            Text(
                text = stringResource(Res.string.setup_language_search, Language.entries.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
    if (matches.isEmpty()) {
        Text(
            text = stringResource(Res.string.setup_language_none, query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            matches.forEach { language ->
                SelectPill(
                    label = language.nativeName,
                    selected = language == selectedLanguage,
                    onClick = { onLanguageSelected(language) },
                )
            }
        }
    }
}

/** A rounded selectable pill: filled with the accent when selected, subtle outline otherwise. */
@Composable
private fun SelectPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ── Step 2: appearance ───────────────────────────────────────────────────────────────────────

/** Every theme as a swatch card that actually shows the theme, rather than a pill naming it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceStep(selectedTheme: ThemeMode, onThemeSelected: (ThemeMode) -> Unit) {
    WizardPanelHeader(
        icon = Icons.Filled.Palette,
        title = stringResource(Res.string.setup_step1_theme_title),
        subtitle = stringResource(Res.string.setup_step1_theme_subtitle),
    )
    // System first and on its own: it is not a look, it is a deferral to the machine, and grouping
    // it under either heading would claim it is one of them.
    ThemeSection(
        heading = stringResource(Res.string.setup_theme_section_system),
        themes = ThemeMode.entries.filter { it.isLightTheme() == null },
        selectedTheme = selectedTheme,
        onThemeSelected = onThemeSelected,
    )
    ThemeSection(
        heading = stringResource(Res.string.setup_theme_section_light),
        themes = ThemeMode.entries.filter { it.isLightTheme() == true },
        selectedTheme = selectedTheme,
        onThemeSelected = onThemeSelected,
    )
    ThemeSection(
        heading = stringResource(Res.string.setup_theme_section_dark),
        themes = ThemeMode.entries.filter { it.isLightTheme() == false },
        selectedTheme = selectedTheme,
        onThemeSelected = onThemeSelected,
    )
}

/** One labelled block of swatches. Empty sections draw nothing rather than a bare heading. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeSection(
    heading: String,
    themes: List<ThemeMode>,
    selectedTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
) {
    if (themes.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            text = heading,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = SWATCH_COLUMNS,
        ) {
            themes.forEach { mode ->
                ThemeSwatchCard(
                    mode = mode,
                    label = themeDisplayName(mode),
                    selected = mode == selectedTheme,
                    onClick = { onThemeSelected(mode) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * One theme, painted in its own colours.
 *
 * Drawn from [colorSchemeFor] rather than from a hand-kept table of swatch colours, so a theme whose
 * palette is edited — or a tenth theme added — updates here without anyone remembering to.
 */
@Composable
private fun ThemeSwatchCard(
    mode: ThemeMode,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = remember(mode) { colorSchemeFor(mode) }
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .background(Brush.verticalGradient(listOf(scheme.surface, scheme.background))),
            contentAlignment = Alignment.BottomStart,
        ) {
            Row(
                modifier = Modifier.padding(start = 10.dp, bottom = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(width = 20.dp, height = 5.dp).clip(CircleShape).background(scheme.primary))
                Box(Modifier.size(width = 12.dp, height = 5.dp).clip(CircleShape).background(scheme.surfaceVariant))
                Box(Modifier.size(5.dp).clip(CircleShape).background(scheme.tertiary))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Box(
                    modifier = Modifier.size(16.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}


// ── Step 3: welcome ──────────────────────────────────────────────────────────────────────────

/** What the next three steps are for, each card jumping straight to the step it describes. */
@Composable
private fun WelcomeStep(onGoToStep: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_app_icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(30.dp),
            )
        }
        Column {
            Text(
                text = stringResource(Res.string.setup_step1_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.setup_step1_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    WelcomeCard(
        icon = Icons.Filled.Book,
        title = stringResource(Res.string.setup_welcome_bible_title),
        body = stringResource(Res.string.setup_welcome_bible_body),
        step = STEP_BIBLE,
        onClick = { onGoToStep(STEP_BIBLE) },
    )
    WelcomeCard(
        icon = Icons.Filled.MusicNote,
        title = stringResource(Res.string.setup_welcome_songs_title),
        body = stringResource(Res.string.setup_welcome_songs_body),
        step = STEP_SONGS,
        onClick = { onGoToStep(STEP_SONGS) },
    )
    WelcomeCard(
        icon = Icons.Filled.Tv,
        title = stringResource(Res.string.setup_welcome_projection_title),
        body = stringResource(Res.string.setup_welcome_projection_body),
        step = STEP_PROJECTION,
        onClick = { onGoToStep(STEP_PROJECTION) },
    )
}

@Composable
private fun WelcomeCard(icon: ImageVector, title: String, body: String, step: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(Res.string.setup_welcome_card_step, step + 1),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Step 4: Bible ────────────────────────────────────────────────────────────────────────────

@Composable
private fun BibleStep(onOpenSettings: () -> Unit) {
    WizardPanelHeader(
        icon = Icons.Filled.Book,
        title = stringResource(Res.string.setup_step2_title),
        subtitle = stringResource(Res.string.setup_step2_subtitle),
        instructionCount = BIBLE_INSTRUCTIONS,
    )
    InstructionStep(number = 1, text = stringResource(Res.string.setup_step2_step1)) {
        OpenSettingsButton(onOpenSettings)
    }
    InstructionStep(number = 2, text = stringResource(Res.string.setup_step2_step2)) {
        SettingsTabHint(highlightedTab = stringResource(Res.string.appearance))
    }
    InstructionStep(number = 3, text = stringResource(Res.string.setup_bible_choose_folder))
    InstructionStep(number = 4, text = stringResource(Res.string.setup_step2_step5)) {
        TipBox(text = stringResource(Res.string.setup_step2_download_hint))
    }
    // Names translations in the plural: the app carries an ordered stack of them, and saying only
    // "primary" left multi-translation mode undiscoverable from the wizard.
    InstructionStep(number = 5, text = stringResource(Res.string.setup_bible_pick_translations)) {
        SettingsTabHint(highlightedTab = stringResource(Res.string.bible))
    }
    TipBox(text = stringResource(Res.string.setup_step2_tip))
    TipBox(text = stringResource(Res.string.setup_step2_tip2))
}

private const val BIBLE_INSTRUCTIONS = 5

// ── Step 5: song books ───────────────────────────────────────────────────────────────────────

@Composable
private fun SongsStep(onOpenSettings: () -> Unit, onOpenConverter: () -> Unit) {
    WizardPanelHeader(
        icon = Icons.Filled.MusicNote,
        title = stringResource(Res.string.setup_step3_title),
        subtitle = stringResource(Res.string.setup_step3_subtitle),
        instructionCount = SONG_INSTRUCTIONS,
    )
    InstructionStep(number = 1, text = stringResource(Res.string.setup_step3_step1)) {
        OpenSettingsButton(onOpenSettings)
    }
    InstructionStep(number = 2, text = stringResource(Res.string.setup_step3_step2)) {
        SettingsTabHint(highlightedTab = stringResource(Res.string.appearance))
    }
    InstructionStep(number = 3, text = stringResource(Res.string.setup_step3_step3))
    InstructionStep(number = 4, text = stringResource(Res.string.setup_step3_step4)) {
        TipBox(text = stringResource(Res.string.setup_songs_format_note))
    }
    ConverterCallout(onOpenConverter)
    TipBox(text = stringResource(Res.string.setup_songs_samples_note))
}

private const val SONG_INSTRUCTIONS = 4

/**
 * The way in for a user whose songs are in another app's format.
 *
 * The step above tells them the folder reads `.song` and nothing else, which is true and, on its
 * own, a dead end — so this names the formats the bundled Converter does read and opens it.
 */
@Composable
private fun ConverterCallout(onOpenConverter: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.setup_songs_converter_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Button(shape = RoundedCornerShape(8.dp), onClick = onOpenConverter) {
            Text(stringResource(Res.string.setup_songs_converter_button))
        }
    }
}

// ── Step 6: projection ───────────────────────────────────────────────────────────────────────

@Composable
private fun ProjectionStep(onOpenSettings: () -> Unit) {
    WizardPanelHeader(
        icon = Icons.Filled.Tv,
        title = stringResource(Res.string.setup_proj_title),
        subtitle = stringResource(Res.string.setup_proj_subtitle),
        instructionCount = PROJECTION_INSTRUCTIONS,
    )
    InstructionStep(number = 1, text = stringResource(Res.string.setup_proj_step1))
    InstructionStep(number = 2, text = stringResource(Res.string.setup_proj_step2)) {
        OpenSettingsButton(onOpenSettings)
        SettingsTabHint(highlightedTab = stringResource(Res.string.projection))
    }
    // Replaces "set the number of projection windows": there is no such control, and never was one
    // the user could reach — "Presenter windows" is a read-only line that follows the assignments.
    InstructionStep(number = 3, text = stringResource(Res.string.setup_proj_rows_note))
    InstructionStep(number = 4, text = stringResource(Res.string.setup_proj_assign_note)) {
        ScreenAssignmentHint()
    }
    InstructionStep(number = 5, text = stringResource(Res.string.setup_proj_step5))
    TipBox(text = stringResource(Res.string.setup_proj_tip))
    TipBox(text = stringResource(Res.string.setup_proj_tip2))
    TipBox(text = stringResource(Res.string.setup_proj_lang_note))
}

private const val PROJECTION_INSTRUCTIONS = 5

/** The sample row's figures — one screen, one window, and all but one content type switched on. */
private const val SAMPLE_SCREENS = 1
private const val SAMPLE_OUTPUTS_ON = 15
private const val SAMPLE_OUTPUTS_TOTAL = 16

/**
 * A sketch of the Screen Assignment card, so instruction 4 points at something recognisable.
 *
 * Deliberately one row of a real-looking table rather than a screenshot: it is built from the same
 * strings the card itself uses, so a renamed column follows it here instead of going stale.
 */
@Composable
private fun ScreenAssignmentHint() {
    WizardMockPanel(
        title = stringResource(Res.string.screen_assignment),
        trailing = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Text(
                    text = stringResource(Res.string.identify_screen),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(Res.string.detected_screens, SAMPLE_SCREENS),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.presenter_windows_count, SAMPLE_SCREENS),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MockColumn(
                    stringResource(Res.string.projection_target_display),
                    stringResource(Res.string.projection_auto_display),
                )
                MockColumn(stringResource(Res.string.key_output), stringResource(Res.string.key_output_none))
                MockColumn(stringResource(Res.string.display_mode), stringResource(Res.string.display_fullscreen))
                MockColumn(
                    stringResource(Res.string.content_outputs),
                    stringResource(Res.string.content_outputs_enabled_short, SAMPLE_OUTPUTS_ON, SAMPLE_OUTPUTS_TOTAL),
                )
            }
        }
    }
}

/** One labelled cell of the sample row: the column's name over the value it would hold. */
@Composable
private fun MockColumn(label: String, value: String) {
    Column(modifier = Modifier.width(96.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(5.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(5.dp))
                .padding(horizontal = 7.dp, vertical = 4.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Step 7: media ────────────────────────────────────────────────────────────────────────────

/** A VLC availability probe's three flags together, so [VlcStep] takes only one injection point. */
internal data class VlcCheckResult(val available: Boolean, val archMismatch: Boolean, val loadFailed: Boolean)

/** The real probe: re-runs [recheckVlcAvailability] and reads the two detail flags it leaves behind. */
private fun vlcCheckResultFromRecheck(): VlcCheckResult =
    VlcCheckResult(recheckVlcAvailability(), isVlcArchMismatch, isVlcLoadFailed)

@Composable
internal fun VlcStep(
    initial: VlcCheckResult = VlcCheckResult(isVlcAvailable, isVlcArchMismatch, isVlcLoadFailed),
    osName: String = System.getProperty("os.name", "").lowercase(),
    arch: String = System.getProperty("os.arch", "").lowercase(),
    onRecheck: suspend () -> VlcCheckResult = { vlcCheckResultFromRecheck() },
    onOpenDownloadPage: (String) -> Unit = { UrlOpener.open(it) },
    /**
     * How the download address is copied.
     *
     * The wizard is where a machine whose browser cannot be reached is most likely to be met — and
     * where it opens is the operating system's choice, not the app's, so on a two-screen setup the
     * page can land on the projection output. A parameter for the same reason
     * [onOpenDownloadPage] is one: a test observes the copy rather than taking the real clipboard.
     */
    copyText: (String) -> Unit = { SystemClipboard.copy(it) }
) {
    val isMac = remember { "mac" in osName || "darwin" in osName }
    val isWin = remember { "win" in osName }
    val isArm = remember { "aarch64" in arch || "arm" in arch }
    val isLinux = remember { !isMac && !isWin }

    var vlcOk by remember { mutableStateOf(initial.available) }
    var archMismatch by remember { mutableStateOf(initial.archMismatch) }
    var loadFailed by remember { mutableStateOf(initial.loadFailed) }
    var rechecking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val downloadUrl = remember {
        when {
            isWin -> "https://www.videolan.org/vlc/download-windows.html"
            isMac -> "https://www.videolan.org/vlc/download-macosx.html"
            else -> "https://www.videolan.org/vlc/download-linux.html"
        }
    }

    WizardPanelHeader(
        icon = Icons.Filled.OndemandVideo,
        title = stringResource(Res.string.setup_step5_title),
        subtitle = stringResource(Res.string.setup_step5_subtitle),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (vlcOk) MaterialTheme.semantic.successContainer
                else MaterialTheme.colorScheme.errorContainer
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                imageVector = if (vlcOk) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (vlcOk) MaterialTheme.semantic.onSuccessContainer else MaterialTheme.colorScheme.error,
            )
            Text(
                text = stringResource(
                    when {
                        vlcOk -> Res.string.setup_step5_vlc_ok
                        archMismatch -> Res.string.setup_step5_vlc_wrong_arch
                        loadFailed -> Res.string.setup_step5_vlc_load_failed
                        else -> Res.string.setup_step5_vlc_missing
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (vlcOk) MaterialTheme.semantic.onSuccessContainer else MaterialTheme.colorScheme.error,
            )
        }
        if (!vlcOk) {
            if (archMismatch) {
                Text(
                    text = stringResource(Res.string.setup_step5_vlc_wrong_arch_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            if (loadFailed) {
                Text(
                    text = stringResource(Res.string.setup_step5_vlc_load_failed_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    shape = RoundedCornerShape(8.dp),
                    onClick = { onOpenDownloadPage(downloadUrl) }
                ) {
                    Text(
                        stringResource(
                            when {
                                isMac && isArm -> Res.string.setup_step5_download_silicon
                                isMac -> Res.string.setup_step5_download_intel
                                else -> Res.string.setup_step5_download
                            }
                        )
                    )
                }
                OutlinedButton(
                    shape = RoundedCornerShape(8.dp),
                    onClick = {
                        scope.launch {
                            rechecking = true
                            val result = withContext(Dispatchers.IO) { onRecheck() }
                            vlcOk = result.available
                            archMismatch = result.archMismatch
                            loadFailed = result.loadFailed
                            rechecking = false
                        }
                    },
                    enabled = !rechecking
                ) {
                    Text(stringResource(Res.string.setup_step5_recheck))
                }
                CopyLinkIconButton(url = downloadUrl, onCopy = copyText)
            }
        }
    }
    InfoCard(
        title = stringResource(Res.string.setup_media_why_title),
        body = stringResource(Res.string.setup_media_why_body),
    )
    if (isLinux) {
        TipBox(text = stringResource(Res.string.setup_step5_linux_tip))
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Step 8: all set ──────────────────────────────────────────────────────────────────────────

/** What the setup actually produced, read back off disk rather than asserted. */
@Composable
private fun ReadyStep(selectedLanguage: Language, theme: ThemeMode, summary: SetupSummary?) {
    WizardPanelHeader(
        icon = Icons.Filled.CheckCircle,
        title = stringResource(Res.string.setup_step4_title),
        subtitle = stringResource(Res.string.setup_step4_body),
    )
    SummaryRow(
        label = stringResource(Res.string.menu_language),
        value = selectedLanguage.nativeName,
    )
    SummaryRow(
        label = stringResource(Res.string.setup_rail_appearance),
        value = themeDisplayName(theme),
    )
    SummaryRow(
        label = stringResource(Res.string.setup_summary_bible),
        value = summary
            ?.let { stringResource(Res.string.setup_summary_bible_value, it.bibleTranslations) }
            ?: stringResource(Res.string.loading),
        satisfied = summary != null && summary.bibleTranslations > 0,
    )
    SummaryRow(
        label = stringResource(Res.string.setup_rail_songs),
        value = summary
            ?.let { stringResource(Res.string.setup_summary_songs_value, it.songBooks, it.songs) }
            ?: stringResource(Res.string.loading),
        satisfied = summary != null && summary.songs > 0,
    )
    TipBox(text = stringResource(Res.string.setup_step4_hint))
}

/**
 * One line of the summary.
 *
 * [satisfied] drives the tick: a row reporting zero translations is not a success, and colouring it
 * like one is exactly how a mistyped folder path gets past a setup wizard unnoticed.
 */
@Composable
private fun SummaryRow(label: String, value: String, satisfied: Boolean = true) {
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    if (satisfied) MaterialTheme.semantic.success
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (satisfied) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.semantic.onSuccess,
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── Shared step furniture ────────────────────────────────────────────────────────────────────

/**
 * One numbered instruction, with whatever illustrates it underneath.
 *
 * The number sits in its own gutter so the instructions read as an ordered list even when one of
 * them carries a button, a tab strip or a mock panel below it.
 */
@Composable
private fun InstructionStep(number: Int, text: String, content: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$number",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = withoutLeadingNumber(text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = DIM_ALPHA),
            )
            if (content != null) content()
        }
    }
}

/**
 * Drops a "1. " that the instruction string already carries.
 *
 * These strings were written for a plain list and number themselves; the step now draws the number
 * in its own gutter, and every locale would otherwise read "(1) 1. Open Settings". Stripping at
 * render time rather than re-cutting the strings keeps all thirty-odd existing translations working
 * — the prefix is digits and a separator in every one of them, and text that does not match is
 * returned untouched.
 */
private val LEADING_NUMBER = Regex("""^\s*\d+[.)]\s*""")

internal fun withoutLeadingNumber(text: String): String = text.replace(LEADING_NUMBER, "")

@Composable
private fun OpenSettingsButton(onOpenSettings: () -> Unit) {
    OutlinedButton(shape = RoundedCornerShape(8.dp), onClick = onOpenSettings) {
        Image(
            painter = painterResource(Res.drawable.ic_settings),
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(stringResource(Res.string.shortcut_description_settings))
    }
}

/** The settings dialog's first five tabs, with the one the instruction means picked out. */
@Composable
private fun SettingsTabHint(highlightedTab: String) {
    val tabs = listOf(
        stringResource(Res.string.appearance),
        stringResource(Res.string.bible),
        stringResource(Res.string.song),
        stringResource(Res.string.background),
        stringResource(Res.string.projection),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val active = tab == highlightedTab
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = tab,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    // A tab name is a label, not prose: wrapping "Projection" onto two lines makes
                    // the strip read as two tabs.
                    maxLines = 1,
                    softWrap = false,
                    color = if (active) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
        Text(
            text = "…",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun TipBox(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
