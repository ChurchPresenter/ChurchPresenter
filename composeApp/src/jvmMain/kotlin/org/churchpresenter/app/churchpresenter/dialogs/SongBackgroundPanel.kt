/*
 * The Background panel itself: the header the design draws — the caption, the Inherit/Custom
 * switch, the close button — over the library column and the look column beside it.
 */
package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.song_background
import churchpresenter.composeapp.generated.resources.song_background_applies_to
import churchpresenter.composeapp.generated.resources.song_background_full_screen
import churchpresenter.composeapp.generated.resources.song_background_inherit
import churchpresenter.composeapp.generated.resources.song_background_lower_third
import churchpresenter.composeapp.generated.resources.song_background_own
import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import churchpresenter.composeapp.generated.resources.song_background_preset_cinema
import churchpresenter.composeapp.generated.resources.song_background_preset_legible
import churchpresenter.composeapp.generated.resources.song_background_preset_none
import churchpresenter.composeapp.generated.resources.song_background_preset_soft

internal const val SONG_BACKGROUND_PANEL_TAG = "song_background_panel"

/** The scope selector inside the panel — which of the song's backgrounds is being edited. */
internal const val SONG_BACKGROUND_SCOPE_TAG = "song_background_scope"

/** The design's panel size. */
internal val SONG_BACKGROUND_PANEL_WIDTH = 660.dp
internal val SONG_BACKGROUND_PANEL_HEIGHT = 424.dp

/**
 * What the scope row adds when it is drawn.
 *
 * Added to the panel's height rather than taken out of it: the library and the look column are
 * sized to the design, and squeezing a row in above them clipped the blur slider off the bottom.
 */
internal val SONG_BACKGROUND_SCOPE_ROW_HEIGHT = 48.dp

/** Which of a song's two backgrounds the panel is editing. */
internal enum class SongBackgroundTarget { FULL_SCREEN, LOWER_THIRD }

/** One dim/blur combination the Look row offers as a single click. */
internal data class LookPreset(val label: StringResource, val dim: Int, val blur: Int)

internal val SONG_BACKGROUND_LOOKS = listOf(
    LookPreset(Res.string.song_background_preset_none, 0, 0),
    LookPreset(Res.string.song_background_preset_soft, 25, 3),
    LookPreset(Res.string.song_background_preset_legible, 45, 6),
    LookPreset(Res.string.song_background_preset_cinema, 65, 12),
)

/**
 * The panel the Background button opens.
 *
 * The Full screen / Lower third switch beside the mode one is the single addition to the design:
 * a song carries a background for each, and the panel edits one at a time.
 */
@Composable
internal fun SongBackgroundPanel(
    background: SongBackground,
    lowerThirdBackground: SongBackground,
    onBackgroundChange: (SongBackground) -> Unit,
    onLowerThirdBackgroundChange: (SongBackground) -> Unit,
    sampleLine: String,
    onApplyToSongbook: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Whether "Inherit" is on offer. False for a quick background, which exists only to override —
     * an inheriting one would be a tray tile that does nothing.
     */
    allowInherit: Boolean = true,
    /**
     * An optional row along the bottom of the panel. Null for a song, whose background is edited
     * in place and saved with the song; the quick tray passes its OK/Cancel through here so a
     * tray tile can be edited and then abandoned.
     */
    footer: (@Composable () -> Unit)? = null,
    /**
     * What the panel can be pointed at, as display names — the whole song first, then each of its
     * sections. Empty for a background that has only one scope, such as a quick-tray tile, and then
     * no selector is drawn at all.
     */
    scopes: List<String> = emptyList(),
    scopeIndex: Int = 0,
    onScopeChange: (Int) -> Unit = {},
) {
    var target by remember { mutableStateOf(SongBackgroundTarget.FULL_SCREEN) }
    val current = if (target == SongBackgroundTarget.FULL_SCREEN) background else lowerThirdBackground
    fun update(next: SongBackground) {
        if (target == SongBackgroundTarget.FULL_SCREEN) onBackgroundChange(next)
        else onLowerThirdBackgroundChange(next)
    }

    Surface(
        modifier = modifier
            .testTag(SONG_BACKGROUND_PANEL_TAG)
            .width(SONG_BACKGROUND_PANEL_WIDTH)
            .fillMaxHeight()
            // A click inside the panel must not reach the dismiss handler outside it.
            .pointerInput(Unit) { detectTapGestures { } },
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 16.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            PanelHeader(
                target = target,
                onTarget = { target = it },
                allowInherit = allowInherit,
                custom = current.isCustom,
                onMode = { custom ->
                    update(
                        if (custom) current.copy(type = current.type.ifBlank { SongBackgroundType.COLOR })
                        else current.copy(type = SongBackgroundType.INHERIT)
                    )
                },
                onDismiss = onDismiss,
            )
            if (scopes.size > 1) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ScopeRow(scopes = scopes, scopeIndex = scopeIndex, onScopeChange = onScopeChange)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.weight(1f).fillMaxWidth()) {
                SongBackgroundLibrary(
                    background = current,
                    onChange = ::update,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .alpha(if (current.isCustom || !allowInherit) 1f else INHERIT_ALPHA),
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SongBackgroundLookColumn(
                    background = current,
                    sampleLine = sampleLine,
                    onChange = ::update,
                    onApplyToSongbook = onApplyToSongbook,
                    modifier = Modifier.width(LOOK_COLUMN_WIDTH).fillMaxHeight(),
                )
            }
            if (footer != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                footer()
            }
        }
    }
}

/**
 * Which of the song's backgrounds is being edited: the song's own, or one section's.
 *
 * A row of its own rather than another control in the header, which already carries three. The
 * section names are the operator's own headings, so the row reads as the song does.
 */
@Composable
private fun ScopeRow(scopes: List<String>, scopeIndex: Int, onScopeChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            text = stringResource(Res.string.song_background_applies_to).uppercase(),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.05.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DropdownSelector(
            label = "",
            value = scopeIndex.toString(),
            options = scopes.mapIndexed { index, name -> index.toString() to name },
            onValueChange = { onScopeChange(it.toIntOrNull() ?: 0) },
            compact = true,
            modifier = Modifier.width(SCOPE_SELECTOR_WIDTH).testTag(SONG_BACKGROUND_SCOPE_TAG),
        )
    }
}

@Composable
private fun PanelHeader(
    target: SongBackgroundTarget,
    onTarget: (SongBackgroundTarget) -> Unit,
    allowInherit: Boolean,
    custom: Boolean,
    onMode: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 12.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            text = stringResource(Res.string.song_background).uppercase(),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.05.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (allowInherit) {
            SegmentedRow {
                Segment(stringResource(Res.string.song_background_inherit), !custom) { onMode(false) }
                Segment(stringResource(Res.string.song_background_own), custom) { onMode(true) }
            }
        }
        SegmentedRow {
            Segment(
                stringResource(Res.string.song_background_full_screen),
                target == SongBackgroundTarget.FULL_SCREEN,
            ) { onTarget(SongBackgroundTarget.FULL_SCREEN) }
            Segment(
                stringResource(Res.string.song_background_lower_third),
                target == SongBackgroundTarget.LOWER_THIRD,
            ) { onTarget(SongBackgroundTarget.LOWER_THIRD) }
        }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The look column's fixed width, as drawn. */
private val LOOK_COLUMN_WIDTH = 212.dp

/** How far the library fades while the song is inheriting. */
private const val INHERIT_ALPHA = 0.4f

/** The scope selector's width — wide enough for a section name without crowding the row. */
private val SCOPE_SELECTOR_WIDTH = 240.dp
