package org.churchpresenter.songlibrary.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.churchpresenter.songlibrary.generated.resources.Res
import org.churchpresenter.songlibrary.generated.resources.problem_no_lyrics
import org.churchpresenter.songlibrary.generated.resources.problem_sections
import org.churchpresenter.songlibrary.generated.resources.problem_untitled
import org.churchpresenter.songlibrary.generated.resources.compare_single_language
import org.churchpresenter.songlibrary.generated.resources.compare_translations
import org.churchpresenter.songlibrary.generated.resources.compare_translations_mismatch
import org.churchpresenter.songlibrary.TranslationProblems
import org.churchpresenter.theme.components.ControlTooltip
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.churchpresenter.hosanna.composables.button.IconButton
import org.churchpresenter.hosanna.composables.feedback.Tooltip
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ButtonIntent
import org.churchpresenter.hosanna.composables.model.IconButtonSize

// The small controls on a song row: its buttons, and the mark that flags a translation problem.

/**
 * Opens the translation comparison, amber when some section's languages do not line up.
 *
 * Shown but disabled on a one-language song, which has nothing to compare, so the button is in the
 * same place on every row.
 */
@Composable
internal fun CompareAction(languages: Int, mismatches: Int, onClick: () -> Unit) {
    val mismatched = mismatches > 0
    RowAction(
        Icons.AutoMirrored.Filled.CompareArrows,
        when {
            languages < 2 -> stringResource(Res.string.compare_single_language)
            mismatched -> stringResource(Res.string.compare_translations_mismatch, mismatches)
            else -> stringResource(Res.string.compare_translations)
        },
        if (mismatched) ButtonIntent.Warning else ButtonIntent.Neutral,
        onClick.takeIf { languages >= 2 },
    )
}

/**
 * The `!` beside the tick of a song whose languages need attention — sections out of step, or a
 * language missing its title or its lyrics — so those songs stand out down the left edge of the
 * grid. The tooltip lists what is wrong, one problem per line.
 */
@Composable
internal fun ProblemMark(problems: TranslationProblems?) {
    Box(Modifier.width(PROBLEM_WIDTH), contentAlignment = Alignment.Center) {
        if (problems == null || problems.isEmpty) return@Box
        val lines = buildList {
            if (problems.mismatchedSections > 0) {
                add(stringResource(Res.string.problem_sections, problems.mismatchedSections))
            }
            problems.untitledLanguages.forEach { add(stringResource(Res.string.problem_untitled, it)) }
            problems.lyriclessLanguages.forEach { add(stringResource(Res.string.problem_no_lyrics, it)) }
        }
        val text = lines.joinToString("\n")
        WithTooltip(text) {
            SirenIcon(text)
        }
    }
}

/**
 * The `!` itself, flashing like a police light — red, then blue — with a glow behind it, so a
 * problem song catches the eye while scrolling. One red and one blue flash take [SIREN_CYCLE_MS]:
 * under two flashes a second, clear of the three a second that flashing content must stay below.
 */
@Composable
private fun SirenIcon(description: String) {
    // Held at the peak of the red flash when animation is off -- see [LocalAnimateProblems].
    val cycle = if (LocalAnimateProblems.current) sirenCycle() else HALF / 2
    val red = cycle < HALF
    // How far into its own flash each colour is: up to full brightness and back down.
    val flash = sin((cycle % HALF) / HALF * PI).toFloat()
    val color = if (red) MaterialTheme.colorScheme.error else MaterialTheme.semantic.info
    Box(Modifier.size(PROBLEM_WIDTH), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().clip(CircleShape).background(color.copy(alpha = GLOW_ALPHA * flash)))
        Icon(
            Icons.Default.PriorityHigh,
            contentDescription = description,
            tint = color,
            modifier = Modifier.size(14.dp).scale(1f + SIREN_GROWTH * flash),
        )
    }
}

@Composable
private fun sirenCycle(): Float {
    val cycle by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SIREN_CYCLE_MS, easing = LinearEasing)),
    )
    return cycle
}

/** Whether a problem song's `!` flashes; see `SongLibraryApp`'s `animateProblems`. */
internal val LocalAnimateProblems = staticCompositionLocalOf { true }

private const val SIREN_CYCLE_MS = 1200
private const val HALF = 0.5f
private const val SIREN_GROWTH = 0.2f
private const val GLOW_ALPHA = 0.35f

/** A row's icon button, with [description] as both its tooltip and what a screen reader says. */
@Composable
internal fun RowAction(icon: ImageVector, description: String, intent: ButtonIntent, onClick: (() -> Unit)?) {
    Tooltip(text = description) {
        IconButton(
            icon = rememberVectorPainter(icon),
            contentDescription = description,
            onClick = { onClick?.invoke() },
            size = IconButtonSize.Small,
            hierarchy = ButtonHierarchy.Tertiary,
            intent = intent,
            enabled = onClick != null,
        )
    }
}

/** [content] with the app's control tooltip under it on hover. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WithTooltip(text: String, content: @Composable () -> Unit) {
    TooltipArea(
        tooltip = { ControlTooltip(text) },
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.BottomCenter,
            offset = DpOffset(0.dp, 4.dp),
        ),
        content = content,
    )
}
