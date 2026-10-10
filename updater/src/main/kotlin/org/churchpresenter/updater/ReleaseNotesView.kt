package org.churchpresenter.updater

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.sharedui.composables.SettingsScrollbar
import org.churchpresenter.sharedui.composables.SettingsScrollbarGutter
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.isDarkScheme
import org.churchpresenter.theme.semantic

private val PanelShape = AppShape(12.dp)
private val GroupChipShape = AppShape(7.dp)

private const val CHIP_ALPHA_DARK = 0.22f
private const val CHIP_ALPHA_LIGHT = 0.13f
private const val PULL_CHIP_ALPHA = 0.14f
private const val BULLET_ALPHA = 0.8f

/**
 * The parsed notes in their scrolling panel: each group under its own tinted icon, each change as a
 * bullet, and every pull request it names as a small link chip that [onOpenUrl] browses to.
 */
@Composable
internal fun ReleaseNotesPanel(groups: List<NotesGroup>, onOpenUrl: (String) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(scheme.surfaceContainerLowest)
            .border(BorderStroke(1.dp, scheme.outlineVariant), PanelShape),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(start = 14.dp, end = 14.dp + SettingsScrollbarGutter, top = 4.dp, bottom = 10.dp),
        ) {
            groups.forEachIndexed { index, group ->
                val (icon, accent) = groupStyle(group.title, index)
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    if (group.title != null) GroupTitle(group.title, icon, accent)
                    group.lines.forEach { line -> NotesLineRow(line, accent, onOpenUrl) }
                }
            }
        }
        // Inset from the panel's rounded border so the bar's ends stay inside the corners.
        Box(modifier = Modifier.matchParentSize().padding(vertical = 6.dp, horizontal = 3.dp)) {
            SettingsScrollbar(scrollState)
        }
    }
}

@Composable
private fun GroupTitle(title: String, icon: ImageVector, accent: Color) {
    Row(
        modifier = Modifier.padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(22.dp).clip(GroupChipShape).background(chipColor(accent)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = accent)
        }
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun NotesLineRow(line: NotesLine, accent: Color, onOpenUrl: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 3.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (line.bullet) {
            Box(
                modifier = Modifier
                    .padding(top = 7.dp)
                    .size(5.dp)
                    .background(accent.copy(alpha = BULLET_ALPHA), CircleShape),
            )
        }
        Text(
            text = notesLineText(line, onOpenUrl),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The line's words, then one chip per pull request it names. */
@Composable
private fun notesLineText(line: NotesLine, onOpenUrl: (String) -> Unit): AnnotatedString {
    val chip = TextLinkStyles(
        SpanStyle(
            background = MaterialTheme.colorScheme.primary.copy(alpha = PULL_CHIP_ALPHA),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
        ),
    )
    return buildAnnotatedString {
        append(line.text)
        line.pulls.forEach { number ->
            append(" ")
            withLink(LinkAnnotation.Clickable("pull-$number", chip) { onOpenUrl(pullRequestUrl(number)) }) {
                // Thin spaces either side give the chip's tint a little room around the number.
                append(" #$number ")
            }
        }
    }
}

/**
 * The icon and hue a group of notes is drawn with, from the words in its title — the releases group
 * their changes by area — or, for a title it does not recognise, the [index]th of a few neutral hues.
 */
@Composable
@ReadOnlyComposable
private fun groupStyle(title: String?, index: Int): Pair<ImageVector, Color> {
    val semantic = MaterialTheme.semantic
    val scheme = MaterialTheme.colorScheme
    val words = title.orEmpty().lowercase()
    fun has(vararg keys: String) = keys.any { it in words }
    return when {
        has("song", "lyric") -> Icons.Default.MusicNote to semantic.contentSongs
        has("bible", "scripture") -> Icons.AutoMirrored.Filled.MenuBook to semantic.contentBible
        has("media", "video", "audio") -> Icons.Default.Movie to semantic.contentMedia
        has("presentation", "slide") -> Icons.Default.Slideshow to semantic.contentPresentation
        has("picture", "image") -> Icons.Default.Image to semantic.contentPictures
        has("schedule") -> Icons.AutoMirrored.Filled.List to scheme.primary
        has("calendar") -> Icons.Default.Event to semantic.contentCalendar
        has("display", "output", "screen", "canvas") -> Icons.Default.DesktopWindows to semantic.contentLowerThird
        has("q&a", "qr", "caption") -> Icons.Default.QuestionAnswer to scheme.tertiary
        has("fix", "bug", "reliab") -> Icons.Default.Build to semantic.success
        else -> {
            val neutral = listOf(scheme.primary, scheme.tertiary, semantic.info)
            Icons.Default.AutoAwesome to neutral[index % neutral.size]
        }
    }
}

@Composable
@ReadOnlyComposable
internal fun chipColor(accent: Color): Color =
    accent.copy(alpha = if (isDarkScheme(MaterialTheme.colorScheme)) CHIP_ALPHA_DARK else CHIP_ALPHA_LIGHT)
