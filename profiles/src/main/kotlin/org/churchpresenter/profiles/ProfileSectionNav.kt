package org.churchpresenter.profiles

import androidx.compose.foundation.background
import org.churchpresenter.sharedui.guide.guideTarget
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.profile_nav_no_matches
import org.churchpresenter.strings.generated.resources.profile_search_settings
import org.churchpresenter.sharedui.composables.SettingsScrollbar
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.sunken
import org.jetbrains.compose.resources.stringResource

/** The section list is this wide: the longest page name and a count beside it. */
private val SECTION_NAV_WIDTH = 200.dp

private const val HIDDEN_PAGE_ALPHA = 0.5f

/**
 * The second column: a search over every setting's name, then the profile's pages in two groups.
 *
 * While the search holds text, only the pages with a matching setting are listed -- [pageMatches]
 * says which -- and each page then shows only its matching rows ([LocalSettingsQuery]).
 *
 * [badge] draws after a page's name (the linked-profile change count, from Phase 4 on).
 */
@Composable
internal fun ProfileSectionNav(
    profile: OutputProfile,
    selected: ProfilePage,
    onSelect: (ProfilePage) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    pageMatches: (ProfilePage) -> Boolean,
    modifier: Modifier = Modifier,
    badge: @Composable (ProfilePage) -> Unit = {},
) {
    val palette = profilesPalette()
    Column(
        modifier = modifier
            .width(SECTION_NAV_WIDTH)
            .fillMaxHeight()
            .background(palette.sections),
    ) {
        SearchField(query, onQueryChange, Modifier.padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 6.dp))
        val scroll = rememberScrollState()
        Box(Modifier.weight(1f)) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                var any = false
                profileNavSections(profile).forEach { section ->
                    val pages = section.pages.filter { query.isBlank() || pageMatches(it) }
                    if (pages.isEmpty()) return@forEach
                    any = true
                    GroupCaption(section.title, Modifier.padding(start = 10.dp, top = 14.dp, bottom = 6.dp))
                    pages.forEach { page ->
                        NavItem(
                            page = page,
                            selected = page == selected,
                            dimmed = !page.isShownBy(profile),
                            onClick = { onSelect(page) },
                            badge = { badge(page) },
                        )
                    }
                }
                if (!any) {
                    Text(
                        text = stringResource(Res.string.profile_nav_no_matches),
                        fontSize = 12.sp,
                        color = palette.faintText,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            SettingsScrollbar(scroll)
        }
    }
}

@Composable
private fun NavItem(
    page: ProfilePage,
    selected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
    badge: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(AppShape(8.dp))
            .background(if (selected) scheme.primaryContainer else scheme.primaryContainer.copy(alpha = 0f))
            .clickable(onClick = onClick)
            .testTag(page.navTag())
            .then(page.guideTarget()?.let { Modifier.guideTarget(it) } ?: Modifier)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = page.icon,
            contentDescription = null,
            tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp).alpha(if (dimmed) HIDDEN_PAGE_ALPHA else 1f),
        )
        Text(
            text = page.label(),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).alpha(if (dimmed) HIDDEN_PAGE_ALPHA else 1f),
        )
        badge()
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val faint = profilesPalette().faintText
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .sunken(AppShape(8.dp), elevationPalette())
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = faint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(stringResource(Res.string.profile_search_settings), fontSize = 12.sp, color = faint, maxLines = 1)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                interactionSource = remember { MutableInteractionSource() },
                textStyle = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().testTag(PROFILE_SEARCH_TAG),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Filled.Close,
                contentDescription = null,
                tint = faint,
                modifier = Modifier.size(14.dp).clickable { onQueryChange("") },
            )
        }
    }
}

/** Test handle for the settings search. */
internal const val PROFILE_SEARCH_TAG = "profile_settings_search"
