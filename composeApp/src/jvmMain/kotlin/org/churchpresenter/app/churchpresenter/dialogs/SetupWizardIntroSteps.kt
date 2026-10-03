package org.churchpresenter.app.churchpresenter.dialogs

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import org.churchpresenter.theme.AppShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.SunkenOutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.background
import org.churchpresenter.icons.generated.resources.ic_app_icon
import org.churchpresenter.strings.generated.resources.setup_language_none
import org.churchpresenter.strings.generated.resources.setup_language_search
import org.churchpresenter.strings.generated.resources.setup_step0_subtitle
import org.churchpresenter.strings.generated.resources.setup_step0_title
import org.churchpresenter.strings.generated.resources.setup_step1_body
import org.churchpresenter.strings.generated.resources.setup_step1_theme_subtitle
import org.churchpresenter.strings.generated.resources.setup_step1_theme_title
import org.churchpresenter.strings.generated.resources.setup_step1_title
import org.churchpresenter.strings.generated.resources.setup_theme_section_dark
import org.churchpresenter.strings.generated.resources.setup_theme_section_light
import org.churchpresenter.strings.generated.resources.setup_theme_section_system
import org.churchpresenter.strings.generated.resources.setup_welcome_bible_body
import org.churchpresenter.strings.generated.resources.setup_welcome_bible_title
import org.churchpresenter.strings.generated.resources.setup_welcome_card_step
import org.churchpresenter.strings.generated.resources.setup_welcome_projection_body
import org.churchpresenter.strings.generated.resources.setup_welcome_projection_title
import org.churchpresenter.strings.generated.resources.setup_welcome_songs_body
import org.churchpresenter.strings.generated.resources.setup_welcome_songs_title
import org.churchpresenter.app.churchpresenter.data.Language
import org.churchpresenter.app.churchpresenter.ui.theme.themeDisplayName
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.theme.colorSchemeFor
import org.churchpresenter.theme.isLightTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Every interface language as a chip, with a search field above them.
 *
 * The search exists because 35 chips is more than a first-time user reads: typing two letters of
 * their own language beats scanning six rows of scripts they cannot read. The count in the footer
 * moves with the filter, so an empty result is legible rather than looking like a broken list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LanguageStep(selectedLanguage: Language, onLanguageSelected: (Language) -> Unit) {
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
    SunkenOutlinedTextField(
        value = query,
        onValueChange = { query = it },
        singleLine = true,
        shape = AppShape(9.dp),
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
    val shape = AppShape(9.dp)
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
internal fun AppearanceStep(selectedTheme: ThemeMode, onThemeSelected: (ThemeMode) -> Unit) {
    WizardPanelHeader(
        icon = Icons.Filled.Palette,
        title = stringResource(Res.string.setup_step1_theme_title),
        subtitle = stringResource(Res.string.setup_step1_theme_subtitle),
    )
    // System first and on its own: it is not a look, it is a deferral to the machine, and grouping
    // it under either heading would claim it is one of them.
    ThemeSection(
        heading = stringResource(Res.string.setup_theme_section_system),
        themes = listOf(ThemeMode.SYSTEM),
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
    val shape = AppShape(10.dp)
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
internal fun WelcomeStep(onGoToStep: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(AppShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(IconRes.drawable.ic_app_icon),
                contentDescription = null,
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
    val shape = AppShape(10.dp)
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
                .clip(AppShape(9.dp))
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

private const val SWATCH_COLUMNS = 3
