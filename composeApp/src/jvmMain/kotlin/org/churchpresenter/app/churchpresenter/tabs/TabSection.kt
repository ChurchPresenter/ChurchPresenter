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
import androidx.compose.material.icons.automirrored.filled.MenuBook
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
import org.churchpresenter.app.churchpresenter.composables.LabeledTab
import org.churchpresenter.app.churchpresenter.composables.LabeledTabIndicator
import org.churchpresenter.app.churchpresenter.composables.labeledTabMinWidth
import org.churchpresenter.app.churchpresenter.composables.TabStripBackArrow
import org.churchpresenter.app.churchpresenter.composables.TabStripForwardArrow
import org.churchpresenter.settings.TabLabelMargin
import org.churchpresenter.settings.TabLabelStyle
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.guideTarget
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.sharedui.models.labelRes

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
                    modifier = Modifier.guideTarget(GuideTargets.mainTab(tab)),
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
internal fun getStringName(tabs: Tabs): String = stringResource(tabs.labelRes)

internal fun tabIcon(tab: Tabs): ImageVector = when (tab) {
    Tabs.BIBLE -> Icons.AutoMirrored.Filled.MenuBook
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
