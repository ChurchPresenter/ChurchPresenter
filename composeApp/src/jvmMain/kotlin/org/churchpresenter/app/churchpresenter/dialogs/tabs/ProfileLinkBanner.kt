package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.profile_banner_linked_lead
import churchpresenter.composeapp.generated.resources.profile_banner_linked_rest
import churchpresenter.composeapp.generated.resources.profile_banner_master_lead
import churchpresenter.composeapp.generated.resources.profile_banner_master_rest
import churchpresenter.composeapp.generated.resources.profile_banner_unlinked_lead
import churchpresenter.composeapp.generated.resources.profile_banner_unlinked_rest
import churchpresenter.composeapp.generated.resources.profile_changes_chip
import churchpresenter.composeapp.generated.resources.profile_link_linked
import churchpresenter.composeapp.generated.resources.profile_link_master
import churchpresenter.composeapp.generated.resources.profile_names_and
import churchpresenter.composeapp.generated.resources.profile_only_changes
import churchpresenter.composeapp.generated.resources.tooltip_undo_unbound
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.overrideCount
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.RaisedSwitch
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource

/** A corner of half the height: the chip is a pill. */
private const val CHIP_PILL_PERCENT = 50

/**
 * What the Profiles tab can do to the selected profile's link, and what it has just done: the
 * profile it was unlinked from a moment ago, with the way back.
 */
internal class ProfileLinkActions(
    val onUnlink: () -> Unit,
    val onLink: (masterId: String, keepOwnValues: Boolean) -> Unit,
    val onCreateLinked: () -> Unit,
    val onSelectProfile: (String) -> Unit,
    /** The master the profile was unlinked from just now, or null. */
    val unlinkedFrom: String? = null,
    val onUndoUnlink: () -> Unit = {},
)

/** The start of the header's second line: linked to whom, master of how many, or standalone. */
@Composable
internal fun RowScope.LinkHeaderState(link: ProfileLink) {
    val master = link.master
    when {
        master != null ->
            HeaderLinkLine(Icons.Filled.Link, stringResource(Res.string.profile_link_linked, master.displayName()))
        link.followers.isNotEmpty() -> HeaderLinkLine(
            Icons.Filled.AccountTree,
            stringResource(Res.string.profile_link_master, link.followers.size),
        )
        else -> StandaloneLinkState()
    }
}

/** "N changes": an amber pill, on a linked profile's row and beside a page's name. */
@Composable
internal fun ChangesChip(count: Int, modifier: Modifier = Modifier, short: Boolean = false) {
    Row(
        modifier = modifier
            .height(18.dp)
            .clip(AppShape(CHIP_PILL_PERCENT))
            .background(MaterialTheme.semantic.overrideContainer)
            .padding(horizontal = 7.dp)
            .testTag(CHANGES_CHIP_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (short) count.toString() else stringResource(Res.string.profile_changes_chip, count),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.semantic.onOverrideContainer,
            maxLines = 1,
        )
    }
}

/**
 * The band at the top of a page saying how the profile is linked: which master it follows and how
 * many of the page's settings are its own, with Only changes; which profiles follow it; or that it
 * was unlinked just now, with Undo. Nothing on a standalone profile.
 */
@Composable
internal fun LinkBanner(
    link: ProfileLink,
    prefixes: List<String>,
    actions: ProfileLinkActions,
    onOnlyChanges: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val master = link.master
    val unlinkedFrom = actions.unlinkedFrom
    when {
        master != null -> if (prefixes.isNotEmpty()) {
            val count = overrideCount(link.profile, prefixes)
            BannerBox(modifier) {
                BannerText(
                    lead = stringResource(Res.string.profile_banner_linked_lead, master.displayName(), count),
                    rest = stringResource(
                        Res.string.profile_banner_linked_rest,
                        master.displayName(),
                        link.profile.displayName(),
                    ),
                )
                OnlyChangesSwitch(link.onlyChanges, onOnlyChanges)
            }
        }
        unlinkedFrom != null -> BannerBox(modifier) {
            BannerText(
                lead = stringResource(Res.string.profile_banner_unlinked_lead, unlinkedFrom),
                rest = stringResource(Res.string.profile_banner_unlinked_rest, unlinkedFrom),
            )
            LinkText(stringResource(Res.string.tooltip_undo_unbound), actions.onUndoUnlink)
        }
        link.followers.isNotEmpty() -> BannerBox(modifier) {
            BannerText(
                lead = stringResource(Res.string.profile_banner_master_lead, joinNames(link.followers)),
                rest = stringResource(Res.string.profile_banner_master_rest),
            )
        }
    }
}

/** "A", "A and B", "A, B and C". */
@Composable
internal fun joinNames(profiles: List<OutputProfile>): String {
    val names = profiles.map { it.displayName() }
    if (names.size < 2) return names.firstOrNull().orEmpty()
    return stringResource(Res.string.profile_names_and, names.dropLast(1).joinToString(", "), names.last())
}

@Composable
private fun BannerBox(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    val palette = profilesPalette()
    val shape = AppShape(10.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .clip(shape)
            .background(palette.banner)
            .border(1.dp, palette.bannerBorder, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag(LINK_BANNER_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun RowScope.BannerText(lead: String, rest: String) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(lead) }
            append(" ")
            append(rest)
        },
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun OnlyChangesSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .clip(AppShape(8.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag(ONLY_CHANGES_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(Res.string.profile_only_changes),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        RaisedSwitch(checked = checked, onCheckedChange = null)
    }
}

/** Test handles for the link banner and its switch, and the change chip. */
internal const val LINK_BANNER_TAG = "profile_link_banner"
internal const val ONLY_CHANGES_TAG = "profile_only_changes"
internal const val CHANGES_CHIP_TAG = "profile_changes_chip"
