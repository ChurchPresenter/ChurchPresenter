package org.churchpresenter.updater

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.update_already_latest
import org.churchpresenter.strings.generated.resources.update_dialog_beta_hint
import org.churchpresenter.strings.generated.resources.update_dialog_beta_title
import org.churchpresenter.strings.generated.resources.update_dialog_channel_prerelease
import org.churchpresenter.strings.generated.resources.update_dialog_channel_stable
import org.churchpresenter.strings.generated.resources.update_dialog_full_notes
import org.churchpresenter.strings.generated.resources.update_dialog_ready_title
import org.churchpresenter.strings.generated.resources.update_dialog_released
import org.churchpresenter.strings.generated.resources.update_dialog_size_mb
import org.churchpresenter.strings.generated.resources.update_dialog_up_to_date_title
import org.churchpresenter.strings.generated.resources.update_dialog_whats_new
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.RaisedSwitch
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val HeroTileShape = AppShape(18.dp)
private val PillShape = AppShape(999.dp)

private const val BYTES_PER_MB = 1_000_000L
private const val TILE_GRADIENT_DARKEN = 0.72f
private const val BADGE_ALPHA = 0.16f

/** The square tile at the top left: [icon] in white on a top-lit [accent] gradient. */
@Composable
internal fun HeroTile(icon: ImageVector, accent: Color) {
    val deep = Color(
        red = accent.red * TILE_GRADIENT_DARKEN,
        green = accent.green * TILE_GRADIENT_DARKEN,
        blue = accent.blue * TILE_GRADIENT_DARKEN,
    )
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(HeroTileShape)
            .background(Brush.verticalGradient(listOf(accent, deep))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
    }
}

/** The tile, the headline, the jump from the running version to the new one, and when and how big. */
@Composable
internal fun UpdateHero(info: UpdateInfo) {
    HeroRow(icon = Icons.Default.Download, accent = MaterialTheme.colorScheme.primary) {
        Text(
            stringResource(Res.string.update_dialog_ready_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (info.currentVersion.isNotEmpty()) {
                Text(
                    info.currentVersion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                info.latestVersion,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ChannelBadge(info.isPrerelease)
        }
        releaseFacts(info)?.let { facts ->
            Text(
                facts,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** The tick tile and the words saying this is already the newest version. */
@Composable
internal fun UpToDateHero() {
    HeroRow(icon = Icons.Default.Check, accent = MaterialTheme.semantic.success) {
        Text(
            stringResource(Res.string.update_dialog_up_to_date_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(Res.string.update_already_latest),
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HeroRow(icon: ImageVector, accent: Color, text: @Composable ColumnScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 26.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeroTile(icon, accent)
        Column(modifier = Modifier.weight(1f), content = text)
    }
}

/** "Released October 8, 2026 · 640 MB", or as much of it as the release said, or null for neither. */
@Composable
private fun releaseFacts(info: UpdateInfo): String? {
    val released = info.publishedAt?.let { stringResource(Res.string.update_dialog_released, releaseDate(it)) }
    val size = info.downloadSize?.let { stringResource(Res.string.update_dialog_size_mb, megabytes(it)) }
    return listOfNotNull(released, size).joinToString(" · ").ifEmpty { null }
}

/** [instant] as a long date in [locale], read on the UTC calendar so every machine names the same day. */
internal fun releaseDate(instant: Instant, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(instant.atZone(ZoneOffset.UTC))

/** [bytes] in whole megabytes, as the OS reports a file's size: decimal, rounded. */
internal fun megabytes(bytes: Long): Int = ((bytes + BYTES_PER_MB / 2) / BYTES_PER_MB).toInt()

/** Whether the offered version is a stable release or a prerelease, as a small pill beside it. */
@Composable
internal fun ChannelBadge(isPrerelease: Boolean) {
    val tint = if (isPrerelease) MaterialTheme.semantic.warning else MaterialTheme.semantic.success
    Text(
        text = stringResource(
            if (isPrerelease) Res.string.update_dialog_channel_prerelease else Res.string.update_dialog_channel_stable,
        ),
        modifier = Modifier
            .clip(PillShape)
            .background(tint.copy(alpha = BADGE_ALPHA))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.ExtraBold,
        color = tint,
    )
}

/**
 * "What's new" over the release notes, with the full notes on GitHub a click away. The panel takes
 * the window's spare height and scrolls inside it.
 */
@Composable
internal fun ColumnScope.WhatsNew(info: UpdateInfo, onOpenUrl: (String) -> Unit) {
    val groups = parseReleaseNotes(info.releaseNotes)
    if (groups.isEmpty()) {
        // Nothing to read: the footer drops to the bottom rather than a heading over an empty panel.
        Spacer(modifier = Modifier.weight(1f))
        return
    }
    Column(modifier = Modifier.weight(1f).padding(horizontal = 28.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.update_dialog_whats_new).uppercase(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.11.em,
                color = MaterialTheme.colorScheme.outline,
            )
            Row(
                modifier = Modifier.clip(AppShape(6.dp)).clickable { onOpenUrl(info.releaseUrl) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.update_dialog_full_notes),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        ReleaseNotesPanel(groups, onOpenUrl, modifier = Modifier.weight(1f))
    }
}

/** Beta updates as one row: what it is, what it costs, and a switch. The whole row toggles. */
@Composable
internal fun BetaRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 28.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.update_dialog_beta_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(Res.string.update_dialog_beta_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        RaisedSwitch(checked = checked, onCheckedChange = null)
    }
}
