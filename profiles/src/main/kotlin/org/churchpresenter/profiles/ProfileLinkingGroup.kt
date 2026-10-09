package org.churchpresenter.profiles

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
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.profile_create_linked_sub
import org.churchpresenter.strings.generated.resources.profile_group_linking
import org.churchpresenter.strings.generated.resources.profile_link_keep
import org.churchpresenter.strings.generated.resources.profile_link_keep_sub
import org.churchpresenter.strings.generated.resources.profile_link_linked
import org.churchpresenter.strings.generated.resources.profile_link_match
import org.churchpresenter.strings.generated.resources.profile_link_match_sub
import org.churchpresenter.strings.generated.resources.profile_link_no_master
import org.churchpresenter.strings.generated.resources.profile_linking_differ
import org.churchpresenter.strings.generated.resources.profile_linking_link_to
import org.churchpresenter.strings.generated.resources.profile_linking_master
import org.churchpresenter.strings.generated.resources.profile_link_sections
import org.churchpresenter.strings.generated.resources.profile_link_sections_sub
import org.churchpresenter.strings.generated.resources.profile_section_main
import org.churchpresenter.strings.generated.resources.profile_section_own
import org.churchpresenter.strings.generated.resources.profile_linking_unlink_note
import org.churchpresenter.strings.generated.resources.profile_menu_create_linked
import org.churchpresenter.strings.generated.resources.profile_unlink
import org.churchpresenter.settings.OWN_SECTION
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProfileSection
import org.churchpresenter.theme.components.DropdownSelector
import org.jetbrains.compose.resources.stringResource

private val MASTER_PICKER_WIDTH = 220.dp

/**
 * LINKING, on General: the master a linked profile follows and how far it differs, with Unlink, and
 * the master each of its sections follows; the profiles following a master; or, on a standalone
 * profile, a master to follow -- keeping its own values where they differ, or matching the master's
 * entirely.
 *
 * [candidates] are the profiles this one could follow: those that follow nothing, itself aside.
 */
@Composable
internal fun LinkingGroup(link: ProfileLink, candidates: List<OutputProfile>, actions: ProfileLinkActions) {
    SettingsGroup(stringResource(Res.string.profile_group_linking), key = "linking") {
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
                SectionMasterRows(link.profile, master, candidates, actions)
            }
            link.followers.isNotEmpty() -> SettingsWideRow {
                Text(
                    stringResource(Res.string.profile_linking_master, joinFollowers(link)),
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
    SettingsRow(Res.string.profile_linking_link_to) {
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
        Res.string.profile_link_keep,
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

/**
 * Sections, under a linked profile's link: one row per section the profile shows, each with the master
 * it follows -- [main] by default, another master, or Own.
 */
@Composable
private fun SectionMasterRows(
    profile: OutputProfile,
    main: OutputProfile,
    candidates: List<OutputProfile>,
    actions: ProfileLinkActions,
) {
    SettingsWideRow {
        Text(stringResource(Res.string.profile_link_sections), fontSize = 13.sp)
        LinkingNote(stringResource(Res.string.profile_link_sections_sub, main.displayName()))
    }
    val options = listOf(main.id to stringResource(Res.string.profile_section_main, main.displayName())) +
        candidates.filter { it.id != main.id }.map { it.id to it.displayName() } +
        (OWN_SECTION to stringResource(Res.string.profile_section_own))
    shownSections(profile).forEach { section ->
        SettingsRow(section.label()) {
            DropdownSelector(
                label = "",
                value = profile.sectionMasters[section.id] ?: main.id,
                options = options,
                onValueChange = { id -> actions.onSectionMaster(section, id.takeIf { it != main.id }) },
                modifier = Modifier.width(MASTER_PICKER_WIDTH).testTag(sectionMasterTag(section)),
                compact = true,
            )
        }
    }
}

/** The sections [profile] has a page for: Content, then its appearance pages -- the stage's only on a stage. */
private fun shownSections(profile: OutputProfile): List<ProfileSection> =
    listOf(ProfileSection.CONTENT) + customizePanes(profile.displayMode).map { it.section() }

/** Create linked profile, among General's actions -- for a profile that follows nothing. */
@Composable
internal fun CreateLinkedAction(onCreate: () -> Unit) {
    SettingsRow(
        Res.string.profile_menu_create_linked,
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

/** Test handle for the picker of the master [section] follows. */
internal fun sectionMasterTag(section: ProfileSection): String = "profile_section_master_${section.id}"
