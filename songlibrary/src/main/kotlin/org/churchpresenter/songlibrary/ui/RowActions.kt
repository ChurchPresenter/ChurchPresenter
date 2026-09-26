package org.churchpresenter.songlibrary.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.ControlTooltip
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource

// The small controls on a song row: its buttons, and the mark that flags a translation problem.

/**
 * Opens the translation comparison, amber when some section's languages do not line up.
 *
 * Shown but disabled on a one-language song, which has nothing to compare, so the button is in the
 * same place on every row.
 */
@Composable
internal fun CompareAction(languages: Int, mismatches: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val mismatched = mismatches > 0
    RowAction(
        Icons.AutoMirrored.Filled.CompareArrows,
        when {
            languages < 2 -> stringResource(Res.string.compare_single_language)
            mismatched -> stringResource(Res.string.compare_translations_mismatch, mismatches)
            else -> stringResource(Res.string.compare_translations)
        },
        when {
            languages < 2 -> scheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA)
            mismatched -> MaterialTheme.semantic.warning
            else -> scheme.onSurfaceVariant
        },
        onClick.takeIf { languages >= 2 },
    )
}

private const val DISABLED_ALPHA = 0.3f

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
            Icon(
                Icons.Default.PriorityHigh,
                contentDescription = text,
                tint = MaterialTheme.semantic.warning,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** A row's icon button, with [description] as both its tooltip and what a screen reader says. */
@Composable
internal fun RowAction(icon: ImageVector, description: String, tint: Color, onClick: (() -> Unit)?) {
    WithTooltip(description) {
        Box(
            Modifier.size(26.dp)
                .clip(AppShape(7.dp))
                .clickable(enabled = onClick != null, onClick = { onClick?.invoke() }),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(14.dp))
        }
    }
}

/** [content] with the app's control tooltip under it on hover. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WithTooltip(text: String, content: @Composable () -> Unit) {
    TooltipArea(
        tooltip = { ControlTooltip(text) },
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.BottomCenter,
            offset = DpOffset(0.dp, 4.dp),
        ),
        content = content,
    )
}
