/*
 * The right column of the Background panel — 212dp fixed, as drawn: the stage preview, what the
 * chosen background is called, the custom-colour field, the Look presets, Dim and Blur, and the
 * Apply to song book footer under a rule.
 */
package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.song_background_apply_to_songbook
import churchpresenter.composeapp.generated.resources.song_background_blur
import churchpresenter.composeapp.generated.resources.song_background_dim
import churchpresenter.composeapp.generated.resources.song_background_look
import churchpresenter.composeapp.generated.resources.song_background_sample_line
import churchpresenter.composeapp.generated.resources.song_background_your_color
import churchpresenter.composeapp.generated.resources.unit_px
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.core.models.songs.SONG_BACKGROUND_MAX_BLUR
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SongBackgroundLookColumn(
    background: SongBackground,
    sampleLine: String,
    onChange: (SongBackground) -> Unit,
    onApplyToSongbook: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            SongBackgroundStage(background, sampleLine)
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = songBackgroundName(background),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = songBackgroundMeta(background),
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // The design shows the hex field for the Custom color tile only — a named solid
                // has nothing to type.
                if (background.type == SongBackgroundType.COLOR &&
                    background.color.lowercase() !in SONG_BACKGROUND_NAMED_COLORS
                ) {
                    YourColor(background, onChange)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PanelCaption(stringResource(Res.string.song_background_look))
                    LookPresetGrid(background, onChange)
                }
                PanelSlider(
                    label = stringResource(Res.string.song_background_dim),
                    readout = "${background.dim}%",
                    value = background.dim,
                    max = PERCENT_MAX,
                ) { onChange(background.copy(dim = it)) }
                PanelSlider(
                    label = stringResource(Res.string.song_background_blur),
                    readout = "${background.blur}${stringResource(Res.string.unit_px)}",
                    value = background.blur,
                    max = SONG_BACKGROUND_MAX_BLUR,
                ) { onChange(background.copy(blur = it)) }
            }
        }
        if (onApplyToSongbook != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(27.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(7.dp))
                        .clickable(onClick = onApplyToSongbook),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.song_background_apply_to_songbook),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The preview: the background blurred and overscanned exactly as the presenter draws it. */
@Composable
private fun SongBackgroundStage(background: SongBackground, sampleLine: String) {
    Box(
        // 16:9 capped in height, which is what the design's `aspect-ratio` plus `max-height` does:
        // the tile keeps the column's width and stops growing. 92 rather than the design's 110 is
        // what keeps the Blur track above the footer at the panel's own 424 height.
        modifier = Modifier.fillMaxWidth().heightIn(max = STAGE_MAX_HEIGHT)
            .aspectRatio(STAGE_ASPECT, matchHeightConstraintsFirst = true)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
    ) {
        SongBackgroundFill(background, Modifier.fillMaxSize())
        if (background.dim > 0) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = background.dim / 100f)))
        }
        Text(
            text = sampleLine.ifBlank { stringResource(Res.string.song_background_sample_line) },
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).padding(9.dp),
        )
        Text(
            text = songBackgroundBadge(background).uppercase(),
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            maxLines = 1,
            color = Color.White.copy(alpha = BADGE_INK_ALPHA),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 4.dp),
        )
    }
}

/** The hex field and the six one-click colours under it. */
@Composable
private fun YourColor(background: SongBackground, onChange: (SongBackground) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PanelCaption(stringResource(Res.string.song_background_your_color))
        Row(
            modifier = Modifier.fillMaxWidth().height(27.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(7.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(7.dp))
                .padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier.size(14.dp).clip(RoundedCornerShape(4.dp))
                    .background(parseHexColor(background.color))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            )
            BasicTextField(
                value = background.color,
                onValueChange = { raw ->
                    val v = raw.trim().let { if (it.startsWith("#")) it else "#$it" }.take(HEX_LENGTH)
                    onChange(background.copy(type = SongBackgroundType.COLOR, color = v))
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SONG_BACKGROUND_SUGGESTIONS.forEach { hex ->
                val selected = background.color.equals(hex, ignoreCase = true)
                Box(
                    Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(4.dp))
                        .background(parseHexColor(hex))
                        .border(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(4.dp),
                        )
                        .clickable {
                            onChange(background.copy(type = SongBackgroundType.COLOR, color = hex))
                        }
                )
            }
        }
    }
}

@Composable
private fun LookPresetGrid(background: SongBackground, onChange: (SongBackground) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SONG_BACKGROUND_LOOKS.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                pair.forEach { preset ->
                    PresetButton(
                        label = stringResource(preset.label),
                        selected = background.dim == preset.dim && background.blur == preset.blur,
                        modifier = Modifier.weight(1f),
                    ) { onChange(background.copy(dim = preset.dim, blur = preset.blur)) }
                }
            }
        }
    }
}

private const val STAGE_ASPECT = 16f / 9f
private val STAGE_MAX_HEIGHT = 92.dp
private const val BADGE_INK_ALPHA = 0.5f
private const val HEX_LENGTH = 7
internal const val PERCENT_MAX = 100
