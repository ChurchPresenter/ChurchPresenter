package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.setup_wizard_title
import org.churchpresenter.strings.generated.resources.background
import org.churchpresenter.strings.generated.resources.bible
import org.churchpresenter.icons.generated.resources.ic_app_icon
import org.churchpresenter.strings.generated.resources.media
import org.churchpresenter.strings.generated.resources.menu_language
import org.churchpresenter.strings.generated.resources.projection
import org.churchpresenter.strings.generated.resources.setup_language_count
import org.churchpresenter.strings.generated.resources.setup_media_ready
import org.churchpresenter.strings.generated.resources.setup_rail_appearance
import org.churchpresenter.strings.generated.resources.setup_rail_ready
import org.churchpresenter.strings.generated.resources.setup_rail_songs
import org.churchpresenter.strings.generated.resources.setup_rail_welcome
import org.churchpresenter.strings.generated.resources.setup_theme_count
import org.churchpresenter.strings.generated.resources.setup_wizard_done
import org.churchpresenter.media.composables.isVlcAvailable
import org.churchpresenter.app.churchpresenter.data.Language
import org.churchpresenter.app.churchpresenter.ui.theme.LanguageProvider
import org.churchpresenter.app.churchpresenter.ui.theme.themeDisplayName
import org.churchpresenter.sharedui.utils.AppWindowRoot
import org.churchpresenter.theme.ThemeMode
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
internal const val STEP_BIBLE = 3
internal const val STEP_SONGS = 4
internal const val STEP_PROJECTION = 5
private const val STEP_MEDIA = 6
private const val STEP_READY = 7
private const val TOTAL_STEPS = 8

private val PANEL_PADDING = 28.dp
private val PANEL_GAP = 20.dp
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
    onDismiss: () -> Unit,
    onFinish: () -> Unit = onDismiss,
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
        title = stringResource(Res.string.setup_wizard_title),
        icon = painterResource(IconRes.drawable.ic_app_icon),
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
            onDismiss = onDismiss,
            onFinish = onFinish,
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
    onFinish: () -> Unit = onDismiss,
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
                                    onFinish()
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
    STEP_APPEARANCE -> stringResource(Res.string.setup_theme_count, ThemeMode.entries.count { it != ThemeMode.CUSTOM })
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

/** A VLC availability probe's three flags together, so [VlcStep] takes only one injection point. */
internal data class VlcCheckResult(val available: Boolean, val archMismatch: Boolean, val loadFailed: Boolean)
