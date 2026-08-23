package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.bible_catalog_download
import org.churchpresenter.resources.generated.resources.bible_catalog_installed
import org.churchpresenter.resources.generated.resources.bible_catalog_redownload
import org.churchpresenter.resources.generated.resources.bible_catalog_size_mb
import org.churchpresenter.resources.generated.resources.bible_catalog_testament_full
import org.churchpresenter.resources.generated.resources.bible_catalog_testament_new
import org.churchpresenter.resources.generated.resources.bible_catalog_testament_old
import org.churchpresenter.bibleformats.catalog.BibleModule
import org.churchpresenter.bibleformats.catalog.InstallPhase
import org.churchpresenter.bibleformats.catalog.Testament
import org.jetbrains.compose.resources.stringResource

private const val BADGE_CHARS = 3

@Composable
internal fun ModuleRow(
    module: BibleModule,
    showDate: Boolean,
    isInstalled: Boolean,
    isInstalling: Boolean,
    phase: InstallPhase?,
    progress: Float,
    anyInstallRunning: Boolean,
    onInstall: () -> Unit
) {

    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val rowBackground by animateColorAsState(
        targetValue = if (hovered) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        } else {
            Color.Transparent
        },
        label = "bibleCatalogRowHover"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .background(rowBackground, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModuleAvatar(module)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = module.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isInstalled) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = stringResource(Res.string.bible_catalog_installed),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
            Text(
                text = moduleSubtitle(module, showDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (module.copyright.isNotBlank()) {
                Text(
                    text = module.copyright,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(modifier = Modifier.width(ACTION_COLUMN_WIDTH), contentAlignment = Alignment.CenterEnd) {
            when {
                isInstalling -> Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(phaseStringRes(phase)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                isInstalled -> OutlinedButton(
                    onClick = onInstall,
                    enabled = !anyInstallRunning,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(Res.string.bible_catalog_redownload),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                else -> Button(
                    onClick = onInstall,
                    enabled = !anyInstallRunning,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(Res.string.bible_catalog_download),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
internal fun ModuleAvatar(module: BibleModule) {
    val (container, content) = when (module.testament) {
        Testament.NEW -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
        Testament.OLD -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary
        Testament.FULL -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    }
    val testamentLabel = stringResource(
        when (module.testament) {
            Testament.NEW -> Res.string.bible_catalog_testament_new
            Testament.OLD -> Res.string.bible_catalog_testament_old
            Testament.FULL -> Res.string.bible_catalog_testament_full
        }
    )
    Surface(
        modifier = Modifier.size(44.dp),
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = module.identifier.take(BADGE_CHARS).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = testamentLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun moduleSubtitle(module: BibleModule, showDate: Boolean): String {
    val parts = mutableListOf<String>()
    if (module.identifier.isNotBlank()) parts.add(module.identifier)
    if (module.language.isNotBlank()) parts.add(module.language)
    if (module.sizeBytes > 0) {
        val megabytes = "%.1f".format(module.sizeBytes / (1024.0 * 1024.0))
        parts.add(stringResource(Res.string.bible_catalog_size_mb, megabytes))
    }

    if (showDate && module.releaseDate.isNotBlank()) parts.add(module.releaseDate)
    return parts.joinToString(" · ")
}

internal val ACTION_COLUMN_WIDTH = 160.dp
