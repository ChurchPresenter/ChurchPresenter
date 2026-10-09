package org.churchpresenter.profiles

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.theme.components.RaisedSwitch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A [SettingsRow] labelled by the string [label], which also names it for Wick: the row is the guide
 * target `GuideTargets.settingsRow(label.key)`, and while Wick points at it the row shows whatever the
 * detail level or the search would otherwise hide.
 */
@Composable
internal fun SettingsRow(
    label: StringResource,
    modifier: Modifier = Modifier,
    sub: String? = null,
    advanced: Boolean = false,
    searchTerms: String? = null,
    leading: (@Composable () -> Unit)? = null,
    paths: List<String> = emptyList(),
    control: @Composable RowScope.() -> Unit,
) = SettingsRowBody(
    stringResource(label),
    GuideTargets.settingsRow(label.key),
    modifier,
    sub,
    advanced,
    searchTerms,
    leading,
    paths,
    control,
)

/**
 * A [SettingsSwitchRow] labelled by the string [label], which also names it for Wick, as the keyed
 * [SettingsRow] does.
 */
@Composable
internal fun SettingsSwitchRow(
    label: StringResource,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
    advanced: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    paths: List<String> = emptyList(),
    /** Controls before the switch that belong to it while it is on -- auto-fit's scope. */
    extra: @Composable RowScope.() -> Unit = {},
) {
    SettingsRow(
        label = label,
        sub = sub,
        advanced = advanced,
        leading = leading,
        paths = paths,
        modifier = modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        extra()
        RaisedSwitch(checked = checked, onCheckedChange = null)
    }
}
