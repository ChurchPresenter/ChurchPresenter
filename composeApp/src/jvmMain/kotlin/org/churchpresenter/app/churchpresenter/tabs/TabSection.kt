package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.announcements
import org.churchpresenter.strings.generated.resources.bible
import org.churchpresenter.strings.generated.resources.display_lower_third
import org.churchpresenter.strings.generated.resources.media
import org.churchpresenter.strings.generated.resources.pictures
import org.churchpresenter.strings.generated.resources.presentation
import org.churchpresenter.strings.generated.resources.songs
import org.churchpresenter.strings.generated.resources.tab_web
import org.churchpresenter.strings.generated.resources.tab_canvas
import org.churchpresenter.strings.generated.resources.tab_qa
import org.churchpresenter.strings.generated.resources.tab_stt
import org.churchpresenter.strings.generated.resources.crossword_tab
import org.churchpresenter.strings.generated.resources.tab_dictionary
import org.churchpresenter.strings.generated.resources.tab_companion_surface
import org.churchpresenter.app.churchpresenter.composables.LabeledTab
import org.churchpresenter.app.churchpresenter.composables.LabeledTabIndicator
import org.churchpresenter.app.churchpresenter.composables.labeledTabMinWidth
import org.churchpresenter.app.churchpresenter.composables.TabStripBackArrow
import org.churchpresenter.app.churchpresenter.composables.TabStripForwardArrow
import org.churchpresenter.settings.TabLabelMargin
import org.churchpresenter.settings.TabLabelStyle
import org.jetbrains.compose.resources.stringResource

@Composable
fun TabSection(
    modifier: Modifier = Modifier,
    visibleTabs: List<Tabs> = Tabs.entries,
    selectedTabIndex: Int = 0,
    labelStyle: TabLabelStyle = TabLabelStyle.TEXT,
    labelMargin: TabLabelMargin = TabLabelMargin.NORMAL,
    onTabSelected: (Int) -> Unit,
) {
    val scrollState = remember { ScrollState(0) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabStripBackArrow(scrollState)

        PrimaryScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier.weight(1f),
            scrollState = scrollState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            edgePadding = 0.dp,
            minTabWidth = labeledTabMinWidth(labelStyle, labelMargin),
            indicator = { LabeledTabIndicator(selectedTabIndex) },
            divider = {},
        ) {
            visibleTabs.forEachIndexed { index, tab ->
                val selected = selectedTabIndex == index
                LabeledTab(
                    name = getStringName(tab),
                    icon = tabIcon(tab),
                    selected = selected,
                    labelStyle = labelStyle,
                    labelMargin = labelMargin,
                    onClick = { onTabSelected.invoke(index) },
                    textStyle = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (selected)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                )
            }
        }

        TabStripForwardArrow(scrollState)
    }
}

@Composable
internal fun getStringName(tabs: Tabs): String {
    return when (tabs) {
        Tabs.BIBLE -> stringResource(Res.string.bible)
        Tabs.SONGS -> stringResource(Res.string.songs)
        Tabs.PICTURES -> stringResource(Res.string.pictures)
        Tabs.PRESENTATION -> stringResource(Res.string.presentation)
        Tabs.MEDIA -> stringResource(Res.string.media)
        Tabs.LOWER_THIRD -> stringResource(Res.string.display_lower_third)
        Tabs.ANNOUNCEMENTS -> stringResource(Res.string.announcements)
        Tabs.WEB -> stringResource(Res.string.tab_web)
        Tabs.CANVAS -> stringResource(Res.string.tab_canvas)
        Tabs.QA -> stringResource(Res.string.tab_qa)
        Tabs.STT -> stringResource(Res.string.tab_stt)
        Tabs.CROSSWORD -> stringResource(Res.string.crossword_tab)
        Tabs.DICTIONARY -> stringResource(Res.string.tab_dictionary)
        Tabs.COMPANION_SURFACE -> stringResource(Res.string.tab_companion_surface)
    }
}

internal fun tabIcon(tab: Tabs): ImageVector = when (tab) {
    Tabs.BIBLE -> Icons.Filled.MenuBook
    Tabs.SONGS -> Icons.Filled.MusicNote
    Tabs.PICTURES -> Icons.Filled.Image
    Tabs.PRESENTATION -> Icons.Filled.Slideshow
    Tabs.MEDIA -> Icons.Filled.Movie
    Tabs.LOWER_THIRD -> Icons.Filled.Subtitles
    Tabs.ANNOUNCEMENTS -> Icons.Filled.Campaign
    Tabs.WEB -> Icons.Filled.Language
    Tabs.CANVAS -> Icons.Filled.Dashboard
    Tabs.QA -> Icons.Filled.QuestionAnswer
    Tabs.STT -> Icons.Filled.Mic
    Tabs.CROSSWORD -> Icons.Filled.GridOn
    Tabs.DICTIONARY -> Icons.Filled.Book
    Tabs.COMPANION_SURFACE -> Icons.Filled.Apps
}
