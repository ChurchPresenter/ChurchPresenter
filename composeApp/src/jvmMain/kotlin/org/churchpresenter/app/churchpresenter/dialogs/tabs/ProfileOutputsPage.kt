package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.browser_source_output_label
import churchpresenter.composeapp.generated.resources.identify_screen
import churchpresenter.composeapp.generated.resources.ndi_output_numbered
import churchpresenter.composeapp.generated.resources.omt_output_numbered
import churchpresenter.composeapp.generated.resources.profile_outputs_empty
import churchpresenter.composeapp.generated.resources.profile_outputs_group
import churchpresenter.composeapp.generated.resources.profile_outputs_hint
import churchpresenter.composeapp.generated.resources.profile_outputs_uses_other
import churchpresenter.composeapp.generated.resources.profile_outputs_uses_this
import churchpresenter.composeapp.generated.resources.profile_not_in_use
import churchpresenter.composeapp.generated.resources.screen_number
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.KeyButton
import org.jetbrains.compose.resources.stringResource

/** Which of the three output lists a tile stands for. */
internal enum class OutputKind { SCREEN, NDI, OMT, BROWSER_SOURCE }

/** One output as the Outputs page shows it: where it lives, what it is called, what it follows. */
internal data class OutputTile(
    val kind: OutputKind,
    val index: Int,
    val label: String,
    val size: String?,
    val profileId: String?,
)

/**
 * Every output, labelled the way its own card on the Projection tab labels it: the screens, then
 * the NDI outputs, the OMT outputs, then the Browser Sources.
 */
@Composable
internal fun outputTiles(proj: ProjectionSettings): List<OutputTile> = buildList {
    proj.screenAssignments.forEachIndexed { index, a ->
        add(
            OutputTile(
                OutputKind.SCREEN,
                index,
                proj.screenLabelOr(a, stringResource(Res.string.screen_number, index + 1)),
                sizeLabel(a.targetBoundsW, a.targetBoundsH),
                a.activeProfileId,
            ),
        )
    }
    proj.ndiOutputs.forEachIndexed { index, a ->
        add(
            OutputTile(
                OutputKind.NDI,
                index,
                a.ndiLabelOr(stringResource(Res.string.ndi_output_numbered, index + 1)),
                sizeLabel(a.ndiWidth, a.ndiHeight),
                a.activeProfileId,
            ),
        )
    }
    proj.omtOutputs.forEachIndexed { index, a ->
        add(
            OutputTile(
                OutputKind.OMT,
                index,
                a.omtLabelOr(stringResource(Res.string.omt_output_numbered, index + 1)),
                sizeLabel(a.omtWidth, a.omtHeight),
                a.activeProfileId,
            ),
        )
    }
    proj.browserSourceOutputs.forEachIndexed { index, a ->
        add(
            OutputTile(
                OutputKind.BROWSER_SOURCE,
                index,
                a.browserSourceLabelOr(stringResource(Res.string.browser_source_output_label, index + 1)),
                sizeLabel(a.browserSourceWidth, a.browserSourceHeight),
                a.activeProfileId,
            ),
        )
    }
}

private fun sizeLabel(width: Int, height: Int): String? = if (width > 0 && height > 0) "$width×$height" else null

/**
 * [this] with the output behind [tile] following [profileId].
 *
 * The one write the Outputs page makes; the output's own wiring -- its monitor, its network name,
 * its key output -- is untouched.
 */
internal fun ProjectionSettings.withTileProfile(tile: OutputTile, profileId: String): ProjectionSettings {
    fun List<ScreenAssignment>.set() =
        mapIndexed { i, a -> if (i == tile.index) a.copy(activeProfileId = profileId) else a }
    return when (tile.kind) {
        OutputKind.SCREEN -> copy(screenAssignments = screenAssignments.set())
        OutputKind.NDI -> copy(ndiOutputs = ndiOutputs.set())
        OutputKind.OMT -> copy(omtOutputs = omtOutputs.set())
        OutputKind.BROWSER_SOURCE -> copy(browserSourceOutputs = browserSourceOutputs.set())
    }
}

/**
 * Outputs: every output as a tile, and which profile each follows.
 *
 * Clicking a tile gives it this profile. Clicking one that already follows it hands it back to the
 * first other profile in the list -- an output always follows exactly one profile, so "none" is
 * not a state it can be left in, and taking the profile off it has to put another one on.
 */
@Composable
internal fun ProfileOutputsPage(
    profile: OutputProfile,
    profiles: List<OutputProfile>,
    proj: ProjectionSettings,
    onProjectionChange: ((ProjectionSettings) -> ProjectionSettings) -> Unit,
    onIdentify: () -> Unit,
) {
    val tiles = outputTiles(proj)
    SettingsGroup(
        caption = stringResource(Res.string.profile_outputs_group),
        action = {
            KeyButton(
                onClick = onIdentify,
                shape = AppShape(7.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(26.dp),
            ) {
                Text(stringResource(Res.string.identify_screen), fontSize = 12.sp)
            }
        },
    ) {
        SettingsWideRow {
            Text(
                stringResource(Res.string.profile_outputs_hint),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (tiles.isEmpty()) {
                Text(
                    stringResource(Res.string.profile_outputs_empty),
                    fontSize = 12.sp,
                    color = profilesPalette().faintText,
                )
            }
            tiles.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { tile ->
                        OutputTileCard(
                            tile = tile,
                            profile = profile,
                            other = profiles.find { it.id == tile.profileId && it.id != profile.id },
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val target = if (tile.profileId == profile.id) {
                                    profiles.firstOrNull { it.id != profile.id }?.id
                                } else {
                                    profile.id
                                }
                                if (target != null) onProjectionChange { it.withTileProfile(tile, target) }
                            },
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun OutputTileCard(
    tile: OutputTile,
    profile: OutputProfile,
    other: OutputProfile?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val palette = profilesPalette()
    val mine = tile.profileId == profile.id
    val shape = AppShape(10.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (mine) scheme.surfaceContainerHigh else palette.card)
            .border(if (mine) 2.dp else 1.dp, if (mine) scheme.primary else palette.cardBorder, shape)
            .clickable(onClick = onClick)
            .testTag(outputTileTag(tile.kind, tile.index))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(tile.kind.icon, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                tile.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // What the output is showing as -- the mode of the profile it follows -- and its size.
            val follows = if (mine) profile else other
            val description = listOfNotNull(follows?.let { displayModeLabel(it.displayMode) }, tile.size)
            if (description.isNotEmpty()) {
                Text(description.joinToString(" · "), fontSize = 11.sp, color = palette.faintText, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                when {
                    mine -> Text(
                        stringResource(Res.string.profile_outputs_uses_this),
                        fontSize = 11.sp,
                        color = scheme.primary,
                    )
                    other != null -> {
                        ProfileModeDot(other.displayMode)
                        Text(
                            stringResource(Res.string.profile_outputs_uses_other, other.displayName()),
                            fontSize = 11.sp,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    else -> Text(
                        stringResource(Res.string.profile_not_in_use),
                        fontSize = 11.sp,
                        color = palette.faintText,
                    )
                }
            }
        }
        if (mine) {
            Box { Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(18.dp),
            ) }
        }
    }
}

private val OutputKind.icon: ImageVector
    get() = when (this) {
        OutputKind.SCREEN -> Icons.Filled.Tv
        OutputKind.NDI -> Icons.Filled.SettingsInputAntenna
        OutputKind.OMT -> Icons.Filled.Lan
        OutputKind.BROWSER_SOURCE -> Icons.Filled.Language
    }

/** Test handle for one output tile. */
internal fun outputTileTag(kind: OutputKind, index: Int): String = "profile_output_${kind.name}_$index"
