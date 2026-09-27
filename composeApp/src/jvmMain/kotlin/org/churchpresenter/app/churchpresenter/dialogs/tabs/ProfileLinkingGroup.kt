package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.profile_create_linked_sub
import churchpresenter.composeapp.generated.resources.profile_group_linking
import churchpresenter.composeapp.generated.resources.profile_link_keep
import churchpresenter.composeapp.generated.resources.profile_link_keep_sub
import churchpresenter.composeapp.generated.resources.profile_link_linked
import churchpresenter.composeapp.generated.resources.profile_link_match
import churchpresenter.composeapp.generated.resources.profile_link_match_sub
import churchpresenter.composeapp.generated.resources.profile_link_no_master
import churchpresenter.composeapp.generated.resources.profile_linking_differ
import churchpresenter.composeapp.generated.resources.profile_linking_link_to
import churchpresenter.composeapp.generated.resources.profile_linking_master
import churchpresenter.composeapp.generated.resources.profile_linking_unlink_note
import churchpresenter.composeapp.generated.resources.profile_menu_create_linked
import churchpresenter.composeapp.generated.resources.profile_unlink
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.theme.components.DropdownSelector
import org.jetbrains.compose.resources.stringResource

private val MASTER_PICKER_WIDTH = 220.dp

/**
 * LINKING, on General: the master a linked profile follows and how far it differs, with Unlink; the
 * profiles following a master; or, on a standalone profile, a master to follow -- keeping its own
 * values where they differ, or matching the master's entirely.
 *
 * [candidates] are the profiles this one could follow: those that follow nothing, itself aside.
 */
@Composable
internal fun LinkingGroup(link: ProfileLink, candidates: List<OutputProfile>, actions: ProfileLinkActions) {
    SettingsGroup(stringResource(Res.string.profile_group_linking)) {
        val master = link.master
        when {
            master != null -> {
                SettingsRow(
                    stringResource(Res.string.profile_link_linked, master.displayName()),
                    sub = stringResource(Res.string.profile_linking_differ, link.profile.overrides.size),
                ) {
                    ActionKey(Icons.Filled.LinkOff, stringResource(Res.string.profile_unlink), actions.onUnlink)
                }
                SettingsWideRow { LinkingNote(stringResource(Res.string.profile_linking_unlink_note)) }
            }
            link.followers.isNotEmpty() -> SettingsWideRow {
                Text(
                    stringResource(Res.string.profile_linking_master, joinNames(link.followers)),
                    fontSize = 13.sp,
                )
            }
            candidates.isEmpty() -> SettingsWideRow { LinkingNote(stringResource(Res.string.profile_link_no_master)) }
            else -> LinkToMasterRows(candidates, actions)
        }
    }
}

/** A standalone profile's way in: pick the master, then keep this profile's values or match its. */
@Composable
private fun LinkToMasterRows(candidates: List<OutputProfile>, actions: ProfileLinkActions) {
    var picked by remember(candidates.map { it.id }) { mutableStateOf(candidates.first().id) }
    val master = candidates.find { it.id == picked } ?: candidates.first()
    SettingsRow(stringResource(Res.string.profile_linking_link_to)) {
        DropdownSelector(
            label = "",
            value = master.id,
            options = candidates.map { it.id to it.displayName() },
            onValueChange = { picked = it },
            modifier = Modifier.width(MASTER_PICKER_WIDTH).testTag(MASTER_PICKER_TAG),
            compact = true,
        )
    }
    SettingsRow(
        stringResource(Res.string.profile_link_keep),
        sub = stringResource(Res.string.profile_link_keep_sub),
    ) {
        ActionKey(
            Icons.Filled.Link,
            stringResource(Res.string.profile_link_keep),
            onClick = { actions.onLink(master.id, true) },
        )
    }
    SettingsRow(
        stringResource(Res.string.profile_link_match, master.displayName()),
        sub = stringResource(Res.string.profile_link_match_sub),
    ) {
        ActionKey(Icons.Filled.Link, stringResource(Res.string.profile_link_match, master.displayName()), onClick = {
            actions.onLink(master.id, false)
        })
    }
}

/** Create linked profile, among General's actions -- for a profile that follows nothing. */
@Composable
internal fun CreateLinkedAction(onCreate: () -> Unit) {
    SettingsRow(
        stringResource(Res.string.profile_menu_create_linked),
        sub = stringResource(Res.string.profile_create_linked_sub),
    ) {
        ActionKey(Icons.Filled.AddLink, stringResource(Res.string.profile_menu_create_linked), onCreate)
    }
}

@Composable
private fun LinkingNote(text: String) {
    Text(text, fontSize = 12.sp, color = profilesPalette().faintText)
}

/** Test handle for the master picker. */
internal const val MASTER_PICKER_TAG = "profile_master_picker"
