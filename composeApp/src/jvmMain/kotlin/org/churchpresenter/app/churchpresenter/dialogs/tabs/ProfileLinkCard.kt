package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.profile_different_from
import churchpresenter.composeapp.generated.resources.profile_linked_profiles
import churchpresenter.composeapp.generated.resources.profile_no_changes
import churchpresenter.composeapp.generated.resources.profile_show_more
import churchpresenter.composeapp.generated.resources.profile_unlink
import churchpresenter.composeapp.generated.resources.profile_value_off
import churchpresenter.composeapp.generated.resources.profile_value_on
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.pathWithin
import org.churchpresenter.settings.plainText
import org.churchpresenter.settings.valueAt
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.RaisedSwitch
import org.jetbrains.compose.resources.stringResource

/** How many of a linked profile's changes the card lists before "Show N more". */
private const val SHOWN_CHANGES = 6
private val INLINE_NUMBER_RANGE = -10_000..10_000

/**
 * The preview column's card about the profile's link: every setting a linked profile has changed
 * from its master, each with its value to edit in place and Revert; the profiles following a master;
 * or, standalone, the way to General to link it.
 */
@Composable
internal fun LinkContextCard(
    link: ProfileLink,
    actions: ProfileLinkActions,
    onOpenPage: (ProfilePage) -> Unit,
    onValueChange: (path: String, value: JsonElement) -> Unit,
) {
    val master = link.master
    when {
        master != null -> DifferencesCard(link, master, actions, onOpenPage, onValueChange)
        link.followers.isNotEmpty() -> PreviewSideCard(Modifier.testTag(CONTEXT_CARD_TAG)) {
            CardTitle(stringResource(Res.string.profile_linked_profiles), link.followers.size)
            link.followers.forEach { follower ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppShape(6.dp))
                        .clickable { actions.onSelectProfile(follower.id) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(follower.displayName(), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    if (follower.overrides.isNotEmpty()) ChangesChip(follower.overrides.size)
                }
            }
        }
        else -> StandaloneContextCard(onOpenGeneral = { onOpenPage(ProfilePage.General) })
    }
}

@Composable
private fun DifferencesCard(
    link: ProfileLink,
    master: OutputProfile,
    actions: ProfileLinkActions,
    onOpenPage: (ProfilePage) -> Unit,
    onValueChange: (String, JsonElement) -> Unit,
) {
    var expanded by remember(link.profile.id) { mutableStateOf(false) }
    val changes = link.profile.overrides.sorted()
    PreviewSideCard(Modifier.testTag(CONTEXT_CARD_TAG)) {
        CardTitle(stringResource(Res.string.profile_different_from, master.displayName()), changes.size)
        if (changes.isEmpty()) {
            Text(
                stringResource(Res.string.profile_no_changes, master.displayName()),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val shown = if (expanded) changes else changes.take(SHOWN_CHANGES)
        shown.forEach { path -> ChangeRow(path, link, master, onOpenPage, onValueChange) }
        if (!expanded && changes.size > SHOWN_CHANGES) {
            LinkText(stringResource(Res.string.profile_show_more, changes.size - SHOWN_CHANGES), { expanded = true })
        }
        ActionKey(Icons.Filled.LinkOff, stringResource(Res.string.profile_unlink), actions.onUnlink)
    }
}

@Composable
private fun CardTitle(text: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
        if (count > 0) ChangesChip(count, short = true)
    }
}

/** One changed setting: its name, which opens its page, the master's value, then its own to edit and Revert. */
@Composable
private fun ChangeRow(
    path: String,
    link: ProfileLink,
    master: OutputProfile,
    onOpenPage: (ProfilePage) -> Unit,
    onValueChange: (String, JsonElement) -> Unit,
) {
    val on = stringResource(Res.string.profile_value_on)
    val off = stringResource(Res.string.profile_value_off)
    Column(Modifier.fillMaxWidth().testTag(changeRowTag(path)), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val page = pageForPath(path)
            Text(
                text = settingPathLabel(path),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .then(if (page != null) Modifier.clickable { onOpenPage(page) } else Modifier),
            )
            master.valueAt(path)?.plainText(on, off)?.takeIf { it.isNotBlank() }?.let {
                MasterValueText(master.displayName(), it)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InlineValue(link.profile, path, on, off, onValueChange, Modifier.weight(1f))
            RevertLink({ link.onRevert(listOf(path)) })
        }
    }
}

/** The value at [path], editable where it is a switch or a whole number, and read out otherwise. */
@Composable
private fun InlineValue(
    profile: OutputProfile,
    path: String,
    on: String,
    off: String,
    onValueChange: (String, JsonElement) -> Unit,
    modifier: Modifier,
) {
    val value = profile.valueAt(path)
    val primitive = value as? JsonPrimitive
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        when {
            primitive?.booleanOrNull != null -> RaisedSwitch(
                checked = primitive.booleanOrNull == true,
                onCheckedChange = { onValueChange(path, JsonPrimitive(it)) },
            )
            primitive != null && !primitive.isString && primitive.intOrNull != null -> RowStepper(
                value = primitive.intOrNull ?: 0,
                onValueChange = { onValueChange(path, JsonPrimitive(it)) },
                range = INLINE_NUMBER_RANGE,
            )
            else -> Text(
                value?.plainText(on, off).orEmpty(),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The page a setting is edited on, or null for one no page shows. */
internal fun pageForPath(path: String): ProfilePage? =
    (listOf(ProfilePage.Content) + CustomizePane.entries.map { ProfilePage.Appearance(it) })
        .firstOrNull { page -> page.pathPrefixes().any { pathWithin(path, it) } }

/**
 * A setting's name read off its path: `bibleSettings.translations[kjv.spb].textFontSize` is
 * "Text font size · KJV". Named after the stored field, so it reads the way the settings file does
 * rather than the way a row labels it -- the card lists changes on every page, not only this one's.
 */
internal fun settingPathLabel(path: String): String {
    val entry = ENTRY.find(path)?.groupValues?.get(1)?.substringBeforeLast('.')?.uppercase()
    val leaf = path.replace(ENTRY, "").substringAfterLast('.')
    val words = leaf.replace(CAMEL_HUMP, " $1").lowercase().replaceFirstChar { it.uppercase() }
    return if (entry != null) "$words · $entry" else words
}

private val ENTRY = Regex("\\[([^]]*)]")
private val CAMEL_HUMP = Regex("(?<=[a-z0-9])([A-Z])")

/** Test handle for one change in the card. */
internal fun changeRowTag(path: String): String = "profile_change_$path"
