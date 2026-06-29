package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.composables.isVlcArchMismatch
import org.churchpresenter.app.churchpresenter.composables.isVlcAvailable
import org.churchpresenter.app.churchpresenter.composables.recheckVlcAvailability
import java.awt.Desktop
import java.net.URI
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_app_icon
import churchpresenter.composeapp.generated.resources.ic_settings
import churchpresenter.composeapp.generated.resources.dark_theme
import churchpresenter.composeapp.generated.resources.forest_theme
import churchpresenter.composeapp.generated.resources.light_theme
import churchpresenter.composeapp.generated.resources.midnight_theme
import churchpresenter.composeapp.generated.resources.mocha_theme
import churchpresenter.composeapp.generated.resources.studio_theme
import churchpresenter.composeapp.generated.resources.ocean_theme
import churchpresenter.composeapp.generated.resources.rose_theme
import churchpresenter.composeapp.generated.resources.setup_step0_subtitle
import churchpresenter.composeapp.generated.resources.setup_step0_title
import churchpresenter.composeapp.generated.resources.setup_step1_body
import churchpresenter.composeapp.generated.resources.setup_step1_theme_subtitle
import churchpresenter.composeapp.generated.resources.setup_step1_theme_title
import churchpresenter.composeapp.generated.resources.setup_step1_title
import churchpresenter.composeapp.generated.resources.system_theme
import churchpresenter.composeapp.generated.resources.warm_theme
import churchpresenter.composeapp.generated.resources.setup_step2_step1
import churchpresenter.composeapp.generated.resources.setup_step2_step2
import churchpresenter.composeapp.generated.resources.setup_step2_step3
import churchpresenter.composeapp.generated.resources.setup_step2_step4
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
import churchpresenter.composeapp.generated.resources.setup_step3_tip
import churchpresenter.composeapp.generated.resources.setup_step3_title
import churchpresenter.composeapp.generated.resources.setup_step4_body
import churchpresenter.composeapp.generated.resources.setup_step4_hint
import churchpresenter.composeapp.generated.resources.setup_step4_title
import churchpresenter.composeapp.generated.resources.content_bible
import churchpresenter.composeapp.generated.resources.content_songs
import churchpresenter.composeapp.generated.resources.detected_screens
import churchpresenter.composeapp.generated.resources.display_fullscreen
import churchpresenter.composeapp.generated.resources.display_lower_third
import churchpresenter.composeapp.generated.resources.display_mode
import churchpresenter.composeapp.generated.resources.display_stage_monitor
import churchpresenter.composeapp.generated.resources.identify_screen
import churchpresenter.composeapp.generated.resources.key_output
import churchpresenter.composeapp.generated.resources.presenter_windows_count
import churchpresenter.composeapp.generated.resources.projection_auto_display
import churchpresenter.composeapp.generated.resources.projection_target_display
import churchpresenter.composeapp.generated.resources.screen_assignment
import churchpresenter.composeapp.generated.resources.screen_col_label
import churchpresenter.composeapp.generated.resources.setup_proj_lang_note
import churchpresenter.composeapp.generated.resources.song_language_both
import churchpresenter.composeapp.generated.resources.setup_proj_step1
import churchpresenter.composeapp.generated.resources.setup_proj_step2
import churchpresenter.composeapp.generated.resources.setup_proj_step3
import churchpresenter.composeapp.generated.resources.setup_proj_step4
import churchpresenter.composeapp.generated.resources.setup_proj_step5
import churchpresenter.composeapp.generated.resources.setup_proj_subtitle
import churchpresenter.composeapp.generated.resources.setup_proj_tip
import churchpresenter.composeapp.generated.resources.setup_proj_tip2
import churchpresenter.composeapp.generated.resources.setup_proj_title
import churchpresenter.composeapp.generated.resources.setup_step5_download
import churchpresenter.composeapp.generated.resources.setup_step5_download_intel
import churchpresenter.composeapp.generated.resources.setup_step5_download_silicon
import churchpresenter.composeapp.generated.resources.setup_step5_linux_tip
import churchpresenter.composeapp.generated.resources.setup_step5_recheck
import churchpresenter.composeapp.generated.resources.setup_step5_subtitle
import churchpresenter.composeapp.generated.resources.setup_step5_title
import churchpresenter.composeapp.generated.resources.setup_step5_vlc_missing
import churchpresenter.composeapp.generated.resources.setup_step5_vlc_ok
import churchpresenter.composeapp.generated.resources.setup_step5_vlc_wrong_arch
import churchpresenter.composeapp.generated.resources.setup_step5_vlc_wrong_arch_detail
import churchpresenter.composeapp.generated.resources.shortcut_description_settings
import churchpresenter.composeapp.generated.resources.appearance
import churchpresenter.composeapp.generated.resources.bible
import churchpresenter.composeapp.generated.resources.song
import churchpresenter.composeapp.generated.resources.background
import churchpresenter.composeapp.generated.resources.projection
import churchpresenter.composeapp.generated.resources.setup_wizard_back
import churchpresenter.composeapp.generated.resources.setup_wizard_done
import churchpresenter.composeapp.generated.resources.setup_wizard_next
import churchpresenter.composeapp.generated.resources.setup_wizard_skip
import churchpresenter.composeapp.generated.resources.setup_wizard_step
import churchpresenter.composeapp.generated.resources.setup_wizard_title
import org.churchpresenter.app.churchpresenter.data.Language
import org.churchpresenter.app.churchpresenter.ui.theme.AppThemeWrapper
import org.churchpresenter.app.churchpresenter.ui.theme.LanguageProvider
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val TOTAL_STEPS = 8

@Composable
fun SetupWizardDialog(
    theme: ThemeMode,
    selectedLanguage: Language,
    alwaysOnTop: Boolean = true,
    onLanguageSelected: (Language) -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onOpenSettings: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(0) }
    var goingForward by remember { mutableStateOf(true) }

    val windowState = rememberWindowState(
        width = 700.dp,
        height = 620.dp,
        position = WindowPosition(Alignment.Center)
    )

    Window(
        onCloseRequest = onDismiss,
        title = "Getting Started",
        icon = painterResource(Res.drawable.ic_app_icon),
        state = windowState,
        resizable = false,
        alwaysOnTop = alwaysOnTop
    ) {
        LanguageProvider(language = selectedLanguage) {
            AppThemeWrapper(theme = theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {

                        // Step indicator header
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(Res.string.setup_wizard_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                StepDots(currentStep = step, totalSteps = TOTAL_STEPS)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(Res.string.setup_wizard_step, step + 1, TOTAL_STEPS),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }

                        HorizontalDivider()

                        // Step content — animated slide
                        AnimatedContent(
                            targetState = step,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            transitionSpec = {
                                if (goingForward) {
                                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                                } else {
                                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                                }
                            },
                            label = "wizard_step"
                        ) { currentStep ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 32.dp, vertical = 24.dp),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                when (currentStep) {
                                    0 -> LanguageStep(
                                        selectedLanguage = selectedLanguage,
                                        onLanguageSelected = onLanguageSelected
                                    )
                                    1 -> ThemeStep(
                                        selectedTheme = theme,
                                        onThemeSelected = onThemeSelected
                                    )
                                    2 -> WelcomeStep()
                                    3 -> BibleStep(onOpenSettings = onOpenSettings)
                                    4 -> SongsStep(onOpenSettings = onOpenSettings)
                                    5 -> ProjectionStep(onOpenSettings = onOpenSettings)
                                    6 -> VlcStep()
                                    7 -> ReadyStep()
                                }
                            }
                        }

                        HorizontalDivider()

                        // Navigation buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (step < TOTAL_STEPS - 1) {
                                TextButton(onClick = onDismiss) {
                                    Text(stringResource(Res.string.setup_wizard_skip))
                                }
                            } else {
                                Spacer(modifier = Modifier.width(80.dp))
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (step > 0) {
                                    OutlinedButton(onClick = {
                                        goingForward = false
                                        step--
                                    }) {
                                        Text(stringResource(Res.string.setup_wizard_back))
                                    }
                                }
                                if (step < TOTAL_STEPS - 1) {
                                    Button(onClick = {
                                        goingForward = true
                                        step++
                                    }) {
                                        Text(stringResource(Res.string.setup_wizard_next))
                                    }
                                } else {
                                    Button(onClick = onDismiss) {
                                        Text(stringResource(Res.string.setup_wizard_done))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepDots(currentStep: Int, totalSteps: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(totalSteps) { index ->
            val active = index == currentStep
            Box(
                modifier = Modifier
                    .size(if (active) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.35f)
                    )
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageStep(
    selectedLanguage: Language,
    onLanguageSelected: (Language) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(Res.string.setup_step0_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(Res.string.setup_step0_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Language.entries.forEach { language ->
                val selected = language == selectedLanguage
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            width = if (selected) 2.dp else 0.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.small
                        )
                        .clickable { onLanguageSelected(language) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = language.nativeName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeStep(
    selectedTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit
) {
    val themes = listOf(
        ThemeMode.SYSTEM to stringResource(Res.string.system_theme),
        ThemeMode.LIGHT  to stringResource(Res.string.light_theme),
        ThemeMode.DARK   to stringResource(Res.string.dark_theme),
        ThemeMode.WARM   to stringResource(Res.string.warm_theme),
        ThemeMode.OCEAN  to stringResource(Res.string.ocean_theme),
        ThemeMode.ROSE   to stringResource(Res.string.rose_theme),
        ThemeMode.MIDNIGHT to stringResource(Res.string.midnight_theme),
        ThemeMode.FOREST to stringResource(Res.string.forest_theme),
        ThemeMode.MOCHA  to stringResource(Res.string.mocha_theme),
        ThemeMode.STUDIO to stringResource(Res.string.studio_theme),
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(Res.string.setup_step1_theme_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(Res.string.setup_step1_theme_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            themes.forEach { (mode, label) ->
                val selected = mode == selectedTheme
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            width = if (selected) 2.dp else 0.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.small
                        )
                        .clickable { onThemeSelected(mode) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_app_icon),
            contentDescription = null,
            modifier = Modifier.size(72.dp)
        )
        Text(
            text = stringResource(Res.string.setup_step1_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(Res.string.setup_step1_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
        )
    }
}

@Composable
private fun BibleStep(onOpenSettings: () -> Unit) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(end = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Book,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(Res.string.setup_step2_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(Res.string.setup_step2_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(Res.string.setup_step2_step1),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                )
                OutlinedButton(onClick = onOpenSettings) {
                    Image(
                        painter = painterResource(Res.drawable.ic_settings),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(Res.string.shortcut_description_settings))
                }
                Text(
                    text = stringResource(Res.string.setup_step2_step2),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                )
                SettingsTabHint(highlightedTab = stringResource(Res.string.appearance))
                Text(
                    text = stringResource(Res.string.setup_step2_step3),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                )
                Text(
                    text = stringResource(Res.string.setup_step2_step4),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                )
                Text(
                    text = stringResource(Res.string.setup_step2_step5),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                )
                SettingsTabHint(highlightedTab = stringResource(Res.string.bible))
            }
            TipBox(text = stringResource(Res.string.setup_step2_tip))
            TipBox(text = stringResource(Res.string.setup_step2_tip2))
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )
    }
}

@Composable
private fun SongsStep(onOpenSettings: () -> Unit) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(end = 12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(Res.string.setup_step3_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(Res.string.setup_step3_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(Res.string.setup_step3_step1),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
            )
            OutlinedButton(onClick = onOpenSettings) {
                Icon(
                    painter = painterResource(Res.drawable.ic_settings),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(Res.string.shortcut_description_settings))
            }
            Text(
                text = stringResource(Res.string.setup_step3_step2),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
            )
            SettingsTabHint(highlightedTab = stringResource(Res.string.appearance))
            Text(
                text = stringResource(Res.string.setup_step3_step3),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
            )
            Text(
                text = stringResource(Res.string.setup_step3_step4),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
            )
        }
        TipBox(text = stringResource(Res.string.setup_step3_tip))
    }
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
    )
    }
}

@Composable
private fun ProjectionStep(onOpenSettings: () -> Unit) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(end = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Tv,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(Res.string.setup_proj_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(Res.string.setup_proj_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(Res.string.setup_proj_step1),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                )
                Text(
                    text = stringResource(Res.string.setup_proj_step2),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                )
                OutlinedButton(onClick = onOpenSettings) {
                    Image(
                        painter = painterResource(Res.drawable.ic_settings),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(Res.string.shortcut_description_settings))
                }
                Text(
                    text = stringResource(Res.string.setup_proj_step3),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                )
                SettingsTabHint(highlightedTab = stringResource(Res.string.projection))
                Text(
                    text = stringResource(Res.string.setup_proj_step4),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                )
                ProjectionModeHint()
                Text(
                    text = stringResource(Res.string.setup_proj_step5),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                )
            }
            TipBox(text = stringResource(Res.string.setup_proj_tip))
            TipBox(text = stringResource(Res.string.setup_proj_tip2))
            TipBox(text = stringResource(Res.string.setup_proj_lang_note))
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )
    }
}

@Composable
private fun VlcStep() {
    val osName = remember { System.getProperty("os.name", "").lowercase() }
    val arch = remember { System.getProperty("os.arch", "").lowercase() }
    val isMac = remember { "mac" in osName || "darwin" in osName }
    val isWin = remember { "win" in osName }
    val isArm = remember { "aarch64" in arch || "arm" in arch }
    val isLinux = remember { !isMac && !isWin }

    var vlcOk by remember { mutableStateOf(isVlcAvailable) }
    var archMismatch by remember { mutableStateOf(isVlcArchMismatch) }
    var rechecking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val downloadUrl = remember {
        when {
            isWin -> "https://www.videolan.org/vlc/download-windows.html"
            isMac && isArm -> "https://www.videolan.org/vlc/download-macosx.html"
            isMac -> "https://www.videolan.org/vlc/download-macosx.html"
            else -> "https://www.videolan.org/vlc/download-linux.html"
        }
    }

    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(end = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.OndemandVideo,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(Res.string.setup_step5_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(Res.string.setup_step5_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Status card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        when {
                            vlcOk -> Color(0xFF1B5E20).copy(alpha = 0.12f)
                            archMismatch -> Color(0xFFE65100).copy(alpha = 0.12f)
                            else -> MaterialTheme.colorScheme.errorContainer
                        }
                    )
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                vlcOk -> Icons.Filled.CheckCircle
                                archMismatch -> Icons.Filled.Warning
                                else -> Icons.Filled.Warning
                            },
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = when {
                                vlcOk -> Color(0xFF2E7D32)
                                archMismatch -> Color(0xFFE65100)
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                        Text(
                            text = stringResource(
                                when {
                                    vlcOk -> Res.string.setup_step5_vlc_ok
                                    archMismatch -> Res.string.setup_step5_vlc_wrong_arch
                                    else -> Res.string.setup_step5_vlc_missing
                                }
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                vlcOk -> Color(0xFF2E7D32)
                                archMismatch -> Color(0xFFE65100)
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                    }
                    if (!vlcOk) {
                        if (archMismatch) {
                            Text(
                                text = stringResource(Res.string.setup_step5_vlc_wrong_arch_detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                runCatching { Desktop.getDesktop().browse(URI(downloadUrl)) }
                            }) {
                                Text(stringResource(when {
                                    isMac && isArm -> Res.string.setup_step5_download_silicon
                                    isMac -> Res.string.setup_step5_download_intel
                                    else -> Res.string.setup_step5_download
                                }))
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        rechecking = true
                                        val result = withContext(Dispatchers.IO) { recheckVlcAvailability() }
                                        vlcOk = result
                                        archMismatch = isVlcArchMismatch
                                        rechecking = false
                                    }
                                },
                                enabled = !rechecking
                            ) {
                                Text(stringResource(Res.string.setup_step5_recheck))
                            }
                        }
                    }
                }
            }

            if (isLinux) {
                TipBox(text = stringResource(Res.string.setup_step5_linux_tip))
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )
    }
}

@Composable
private fun ReadyStep() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(Res.string.setup_step4_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(Res.string.setup_step4_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.setup_step4_hint),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
        )
    }
}

@Composable
private fun StepList(steps: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        steps.forEach { step ->
            Text(
                text = step,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
            )
        }
    }
}

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
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val active = tab == highlightedTab
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = tab,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
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
private fun ProjectionModeHint() {
    val displayDropW = 80.dp
    val langDropW    = 72.dp
    val modeColW     = 64.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Title row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(Res.string.screen_assignment),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = stringResource(Res.string.identify_screen),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Column header row — scrollable middle section mirrors the real UI
        val contentScrollState = rememberScrollState()
        Row(verticalAlignment = Alignment.Bottom) {
            // fixed left: screen label placeholder
            Spacer(modifier = Modifier.width(50.dp))
            // Display column
            Text(
                text = stringResource(Res.string.projection_target_display),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(displayDropW)
            )
            // Key Output column
            Text(
                text = stringResource(Res.string.key_output),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(displayDropW)
            )
            // Scrollable content-type columns
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(contentScrollState),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    stringResource(Res.string.content_bible),
                    stringResource(Res.string.content_songs)
                ).forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(langDropW)
                    )
                }
            }
            // Display Mode header + sub-headers
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(Res.string.display_mode),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(modeColW * 3)
                )
                Row {
                    listOf(
                        stringResource(Res.string.display_fullscreen),
                        stringResource(Res.string.display_lower_third),
                        stringResource(Res.string.display_stage_monitor)
                    ).forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(modeColW)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Example data row — Screen 1
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Screen label
            Text(
                text = stringResource(Res.string.screen_col_label, 1),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(50.dp)
            )
            // Display dropdown
            FakeDropdown(label = stringResource(Res.string.projection_auto_display), width = displayDropW)
            // Key Output dropdown
            FakeDropdown(label = "None", width = displayDropW)
            // Bible / Songs dropdowns
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(contentScrollState),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(2) {
                    FakeDropdown(label = stringResource(Res.string.song_language_both), width = langDropW)
                }
            }
            // Display mode radio buttons
            Row {
                listOf(true, false, false).forEach { selected ->
                    RadioButton(
                        selected = selected,
                        onClick = null,
                        modifier = Modifier.size(modeColW)
                    )
                }
            }
        }
    }
}

@Composable
private fun FakeDropdown(label: String, width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(horizontal = 2.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "▾",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun TipBox(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = "💡 $text",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
