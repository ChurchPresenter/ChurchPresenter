package org.churchpresenter.appsettings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import org.churchpresenter.theme.AppShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.KeyButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.appearance
import org.churchpresenter.strings.generated.resources.background
import org.churchpresenter.strings.generated.resources.bible
import org.churchpresenter.icons.generated.resources.ic_settings
import org.churchpresenter.strings.generated.resources.loading
import org.churchpresenter.strings.generated.resources.menu_language
import org.churchpresenter.strings.generated.resources.projection
import org.churchpresenter.strings.generated.resources.setup_rail_appearance
import org.churchpresenter.strings.generated.resources.setup_rail_songs
import org.churchpresenter.strings.generated.resources.setup_step4_body
import org.churchpresenter.strings.generated.resources.setup_step4_hint
import org.churchpresenter.strings.generated.resources.setup_step4_title
import org.churchpresenter.strings.generated.resources.setup_summary_bible
import org.churchpresenter.strings.generated.resources.setup_summary_bible_value
import org.churchpresenter.strings.generated.resources.setup_summary_songs_value
import org.churchpresenter.strings.generated.resources.shortcut_description_settings
import org.churchpresenter.strings.generated.resources.song
import org.churchpresenter.sharedui.language.Language
import org.churchpresenter.sharedui.utils.themeDisplayName
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun InfoCard(title: String, body: String) {
    val shape = AppShape(10.dp)
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
internal fun ReadyStep(selectedLanguage: Language, theme: ThemeMode, summary: SetupSummary?) {
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
    val shape = AppShape(9.dp)
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
internal fun InstructionStep(number: Int, text: String, content: (@Composable () -> Unit)? = null) {
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

internal fun withoutLeadingNumber(text: String): String = text.replace(LEADING_NUMBER, "")

@Composable
internal fun OpenSettingsButton(onOpenSettings: () -> Unit) {
    KeyButton(shape = AppShape(8.dp), onClick = onOpenSettings) {
        Image(
            painter = painterResource(IconRes.drawable.ic_settings),
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
internal fun SettingsTabHint(highlightedTab: String) {
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
            .clip(AppShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val active = tab == highlightedTab
            Box(
                modifier = Modifier
                    .clip(AppShape(6.dp))
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
internal fun TipBox(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShape(8.dp))
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

private const val DIM_ALPHA = 0.75f

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
