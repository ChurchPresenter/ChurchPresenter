package org.churchpresenter.songlibrary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.songlibrary.SectionStatus
import org.churchpresenter.songlibrary.generated.resources.Res
import org.churchpresenter.songlibrary.generated.resources.cancel
import org.churchpresenter.songlibrary.generated.resources.compare_footer_hint
import org.churchpresenter.songlibrary.generated.resources.compare_legend_mismatch
import org.churchpresenter.songlibrary.generated.resources.compare_legend_missing
import org.churchpresenter.songlibrary.generated.resources.compare_legend_ok
import org.churchpresenter.songlibrary.generated.resources.compare_only_problems
import org.churchpresenter.songlibrary.generated.resources.compare_reference
import org.churchpresenter.songlibrary.generated.resources.compare_sections
import org.churchpresenter.songlibrary.generated.resources.compare_show
import org.churchpresenter.songlibrary.generated.resources.compare_subhead
import org.churchpresenter.songlibrary.generated.resources.compare_summary_ok
import org.churchpresenter.songlibrary.generated.resources.compare_summary_problems
import org.churchpresenter.songlibrary.generated.resources.done
import org.churchpresenter.songlibrary.generated.resources.no_song_book
import org.churchpresenter.songlibrary.generated.resources.save_changes
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource

// The chrome around the comparison's cards: the header, the section rail, the footer, and the
// small controls they are built from.

@Composable
internal fun CompareHeader(
    song: SongItem,
    languages: List<CompareLanguage>,
    shown: List<Int>,
    onlyProblems: Boolean,
    onToggleLanguage: (Int) -> Unit,
    onToggleOnlyProblems: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = LibraryType.bodyStrong.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(
                    Res.string.compare_subhead,
                    song.songbook.ifBlank { stringResource(Res.string.no_song_book) },
                    languages.size,
                ),
                style = LibraryType.small,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(stringResource(Res.string.compare_show), style = LibraryType.small, color = scheme.onSurfaceVariant)
        languages.forEach { language ->
            LanguageChip(language, language.index in shown) { onToggleLanguage(language.index) }
        }
        Spacer(Modifier.width(4.dp))
        SmallSwitch(onlyProblems, stringResource(Res.string.compare_only_problems), onToggleOnlyProblems)
    }
}

@Composable
internal fun CompareFooter(changed: Boolean, onDismiss: () -> Unit, onSave: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(58.dp).background(scheme.surfaceContainer).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(Res.string.compare_footer_hint),
            style = LibraryType.small,
            color = scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        QuietButton(stringResource(Res.string.cancel), onClick = onDismiss)
        if (changed) {
            PrimaryButton(stringResource(Res.string.save_changes), onClick = onSave)
        } else {
            PrimaryButton(stringResource(Res.string.done), onClick = onDismiss)
        }
    }
}

@Composable
internal fun SectionRail(rows: List<CompareRow>, onJump: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val problems = rows.count { it.status != SectionStatus.OK }
    Column(Modifier.width(188.dp).fillMaxHeight().background(scheme.surfaceContainerLow)) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp)) {
            Text(
                stringResource(Res.string.compare_sections).uppercase(),
                style = LibraryType.columnHead,
                color = scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA),
            )
            Text(
                if (problems == 0) stringResource(Res.string.compare_summary_ok)
                else stringResource(Res.string.compare_summary_problems, problems, rows.size),
                style = LibraryType.small,
                color = if (problems == 0) MaterialTheme.semantic.success else MaterialTheme.semantic.warning,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            rows.forEach { row ->
                Row(
                    Modifier.fillMaxWidth()
                        .height(34.dp)
                        .clip(AppShape(8.dp))
                        .clickable { onJump(row.index) }
                        .padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    StatusDot(statusColor(row.status), 8.dp)
                    Text(
                        row.label,
                        style = LibraryType.body,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Hairline()
        Column(
            Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LegendRow(SectionStatus.OK, stringResource(Res.string.compare_legend_ok))
            LegendRow(SectionStatus.MISMATCH, stringResource(Res.string.compare_legend_mismatch))
            LegendRow(SectionStatus.MISSING, stringResource(Res.string.compare_legend_missing))
        }
    }
}

@Composable
internal fun ColumnHead(language: CompareLanguage, isReference: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LanguageTag(language)
        Text(
            language.name,
            style = LibraryType.bodyStrong,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (isReference) {
            Text(
                stringResource(Res.string.compare_reference),
                style = LibraryType.small.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun StatusDot(color: Color, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
private fun LegendRow(status: SectionStatus, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusDot(statusColor(status), 7.dp)
        Text(label, style = LibraryType.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LanguageTag(language: CompareLanguage, on: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.height(20.dp)
            .clip(AppShape(5.dp))
            .background(if (on) language.accent.copy(alpha = TAG_ALPHA) else scheme.surfaceContainerHigh)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            language.code,
            style = LibraryType.columnHead.copy(fontSize = 10.5.sp, letterSpacing = 0.sp),
            color = if (on) language.accent else scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA),
        )
    }
}

@Composable
private fun LanguageChip(language: CompareLanguage, on: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = AppShape(LibraryMetrics.radius)
    Row(
        Modifier.height(LibraryMetrics.control)
            .clip(shape)
            .background(if (on) scheme.surfaceContainerHigh else Color.Transparent)
            .border(1.dp, if (on) scheme.outlineVariant else scheme.onSurface.copy(alpha = HAIRLINE_ALPHA), shape)
            .clickable(onClick = onToggle)
            .padding(start = 7.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        LanguageTag(language, on)
        Text(
            language.name,
            style = LibraryType.body,
            color = if (on) scheme.onSurface else scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA),
            maxLines = 1,
        )
    }
}

@Composable
private fun SmallSwitch(on: Boolean, label: String, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.clip(AppShape(7.dp)).clickable(onClick = onToggle).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.width(30.dp)
                .height(18.dp)
                .clip(CircleShape)
                .background(if (on) scheme.primary else scheme.surfaceContainerHighest),
        ) {
            Box(
                Modifier.offset(x = if (on) 14.dp else 2.dp, y = 2.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (on) scheme.onPrimary else scheme.outline),
            )
        }
        Text(label, style = LibraryType.body, color = scheme.onSurface, maxLines = 1)
    }
}
