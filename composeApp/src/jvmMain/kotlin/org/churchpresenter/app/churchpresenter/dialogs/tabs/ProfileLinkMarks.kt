package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.profile_inherited_tooltip
import churchpresenter.composeapp.generated.resources.profile_master_value
import churchpresenter.composeapp.generated.resources.profile_revert
import churchpresenter.composeapp.generated.resources.profile_revert_to
import churchpresenter.composeapp.generated.resources.profile_value_off
import churchpresenter.composeapp.generated.resources.profile_value_on
import org.churchpresenter.app.churchpresenter.composables.ConditionalTooltipArea
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.ControlTooltip
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource

private const val INHERITED_ALPHA = 0.75f
private val MASTER_VALUE_MAX = 140.dp

/*
 * The marks a row of a linked profile carries: an amber dot and the master's value beside a value
 * the profile has made its own, with Revert after it; a dashed frame round one it takes from its
 * master.
 */

/** The 7 dp amber dot before the label of a value this profile has changed. */
@Composable
internal fun OverrideDot() {
    Box(
        Modifier
            .size(7.dp)
            .background(MaterialTheme.semantic.override, CircleShape)
            .testTag(OVERRIDE_DOT_TAG),
    )
}

/** "Sanctuary: 70" -- what the master has, before the control. */
@Composable
internal fun MasterValueText(masterName: String, value: String) {
    Text(
        text = stringResource(Res.string.profile_master_value, masterName, value),
        fontSize = 11.sp,
        color = profilesPalette().faintText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = MASTER_VALUE_MAX),
    )
}

/** ↶ Revert: give this value back to the master. */
@Composable
internal fun RevertLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(AppShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag(REVERT_LINK_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Undo,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.semantic.override,
        )
        Text(stringResource(Res.string.profile_revert), fontSize = 11.sp, color = MaterialTheme.semantic.override)
    }
}

/**
 * The control of a value taken from the master: framed in a dashed line with a small link icon,
 * and a tooltip saying where it comes from. Changing it makes it this profile's own.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun InheritedFrame(link: ProfileLink, control: @Composable RowScope.() -> Unit) {
    val master = link.master?.displayName().orEmpty()
    ConditionalTooltipArea(
        tooltip = {
            ControlTooltip(stringResource(Res.string.profile_inherited_tooltip, master, link.profile.displayName()))
        },
    ) {
        Row(
            modifier = Modifier
                .dashedBorder(MaterialTheme.semantic.inherited.copy(alpha = INHERITED_ALPHA), 8.dp)
                .padding(start = 6.dp, end = 3.dp, top = 3.dp, bottom = 3.dp)
                .testTag(INHERITED_FRAME_TAG),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.Link,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.semantic.inherited,
            )
            control()
        }
    }
}

/**
 * [control] as a row of [link]'s profile draws it for [paths]: with the master's value and Revert
 * when the profile owns one of them, dashed when it takes them all from its master, and plain when
 * the row names none.
 */
@Composable
internal fun RowScope.LinkedControl(
    link: ProfileLink?,
    paths: List<String>,
    control: @Composable RowScope.() -> Unit,
) {
    when {
        link == null || !link.isLinked || paths.isEmpty() -> control()
        link.owns(paths) -> {
            val on = stringResource(Res.string.profile_value_on)
            val value = link.masterValue(paths, on, stringResource(Res.string.profile_value_off))
            if (value != null) MasterValueText(link.master?.displayName().orEmpty(), value)
            control()
            RevertLink({ link.onRevert(paths) })
        }
        else -> InheritedFrame(link, control)
    }
}

/**
 * The caption action of a group editing [paths] on [link]'s profile: "Revert to {master}" while the
 * profile has made any of them its own, nothing while it has not -- a linked profile's values are
 * its master's until changed, so "Reset to defaults" is not the way back. [action] everywhere else.
 */
@Composable
internal fun linkedGroupAction(
    link: ProfileLink?,
    paths: List<String>,
    action: (@Composable RowScope.() -> Unit)?,
): (@Composable RowScope.() -> Unit)? {
    if (link == null || !link.isLinked || paths.isEmpty()) return action
    if (!link.owns(paths)) return null
    val label = stringResource(Res.string.profile_revert_to, link.master?.displayName().orEmpty())
    return { GroupCaptionAction(label, { link.onRevert(paths) }) }
}

/** Test handles for a linked profile's row marks. */
internal const val OVERRIDE_DOT_TAG = "profile_override_dot"
internal const val REVERT_LINK_TAG = "profile_revert"
internal const val INHERITED_FRAME_TAG = "profile_inherited"
