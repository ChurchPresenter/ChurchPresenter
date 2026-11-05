package org.churchpresenter.signage.tab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.sharedui.composables.ZoneLabel
import org.churchpresenter.theme.isDarkScheme

/**
 * The colour a kind of playlist is told apart by: a soft tile and the icon on it. The hue says which
 * kind a row is, never whether it is healthy — that is the error colour beside it.
 */
internal data class KindTint(val tile: Color, val icon: Color)

/** What a tile stands for: a kind of playlist, or a screen itself. */
internal enum class TileKind(val hue: Float) {
    FOLDER(38f), PICTURE(140f), VIDEO(165f), WEB(192f), STREAM(2f), ROTATION(228f), SMUGMUG(318f), SCREEN(214f),
}

internal fun tileKindOf(kind: SourceKind): TileKind = when (kind) {
    SourceKind.FOLDER -> TileKind.FOLDER
    SourceKind.PICTURE -> TileKind.PICTURE
    SourceKind.VIDEO -> TileKind.VIDEO
    SourceKind.WEB -> TileKind.WEB
    SourceKind.STREAM -> TileKind.STREAM
    SourceKind.ROTATION -> TileKind.ROTATION
    SourceKind.SMUGMUG -> TileKind.SMUGMUG
}

internal fun iconOf(kind: TileKind): ImageVector = when (kind) {
    TileKind.FOLDER -> Icons.Filled.Folder
    TileKind.PICTURE -> Icons.Filled.Image
    TileKind.VIDEO -> Icons.Filled.Movie
    TileKind.WEB -> Icons.Filled.Language
    TileKind.STREAM -> Icons.Filled.LiveTv
    TileKind.ROTATION -> Icons.Filled.Repeat
    TileKind.SMUGMUG -> Icons.Filled.PhotoLibrary
    TileKind.SCREEN -> Icons.Filled.Tv
}

@Composable
internal fun tintOf(kind: TileKind): KindTint =
    if (isDarkScheme(MaterialTheme.colorScheme)) {
        KindTint(Color.hsl(kind.hue, DARK_TILE_S, DARK_TILE_L), Color.hsl(kind.hue, DARK_ICON_S, DARK_ICON_L))
    } else {
        KindTint(Color.hsl(kind.hue, LIGHT_TILE_S, LIGHT_TILE_L), Color.hsl(kind.hue, LIGHT_ICON_S, LIGHT_ICON_L))
    }

/** A kind's icon on its coloured tile; [tint] overrides the kind's colours (the error red, say). */
@Composable
internal fun KindTile(kind: TileKind, size: Dp = 34.dp, tint: KindTint = tintOf(kind)) {
    Box(
        modifier = Modifier.size(size).background(tint.tile, RoundedCornerShape(size * TILE_RADIUS)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(iconOf(kind), contentDescription = null, tint = tint.icon, modifier = Modifier.size(size * ICON_SHARE))
    }
}

/** The error colours a tile takes for a playlist whose file or folder is gone. */
@Composable
internal fun errorTint(): KindTint = with(MaterialTheme.colorScheme) { KindTint(error.copy(alpha = SOFT_ALPHA), error) }

/** A column's caption with how many it holds beside it. */
@Composable
internal fun CountedHeading(text: String, count: Int?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ZoneLabel(text)
        if (count != null) {
            Text(
                count.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = TAG_ALPHA), CircleShape)
                    .padding(horizontal = 7.dp, vertical = 1.dp),
            )
        }
    }
}

/** A small rounded label with a dot: a screen's state, in [color] on a soft wash of it. */
@Composable
internal fun StatusPill(text: String, color: Color) {
    Row(
        modifier = Modifier.background(color.copy(alpha = SOFT_ALPHA), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.size(5.dp))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
    }
}

/** One health reading that needs a look: its name, and its value bold, in [warn] on a wash of it. */
@Composable
internal fun HealthChip(name: String, value: String, warn: Color?) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .background(
                warn?.copy(alpha = SOFT_ALPHA) ?: scheme.onSurface.copy(alpha = TAG_ALPHA),
                RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, fontSize = 11.5.sp, color = warn ?: scheme.onSurfaceVariant)
        Spacer(Modifier.size(5.dp))
        Text(value, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = warn ?: scheme.onSurface)
    }
}

/**
 * A banner over the columns in [color]'s wash: its icon on a tile, a bold [title] with [hint] under it,
 * and its actions at the end.
 */
@Composable
internal fun TintedBanner(
    icon: ImageVector,
    color: Color,
    title: @Composable () -> Unit,
    hint: String?,
    actions: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(color.copy(alpha = BANNER_ALPHA), shape)
            .border(1.dp, color.copy(alpha = BANNER_EDGE_ALPHA), shape)
            .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(color.copy(alpha = BANNER_ALPHA), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f)) {
            title()
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        actions()
    }
}

private const val TILE_RADIUS = 0.27f
private const val ICON_SHARE = 0.5f
private const val SOFT_ALPHA = 0.14f
private const val TAG_ALPHA = 0.07f
private const val BANNER_ALPHA = 0.12f
private const val BANNER_EDGE_ALPHA = 0.34f
private const val DARK_TILE_S = 0.32f
private const val DARK_TILE_L = 0.24f
private const val DARK_ICON_S = 0.75f
private const val DARK_ICON_L = 0.76f
private const val LIGHT_TILE_S = 0.62f
private const val LIGHT_TILE_L = 0.92f
private const val LIGHT_ICON_S = 0.6f
private const val LIGHT_ICON_L = 0.38f
