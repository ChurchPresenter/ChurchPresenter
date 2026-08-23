package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.announcements
import org.churchpresenter.resources.generated.resources.bible
import org.churchpresenter.resources.generated.resources.display_lower_third
import org.churchpresenter.resources.generated.resources.media
import org.churchpresenter.resources.generated.resources.pictures
import org.churchpresenter.resources.generated.resources.presentation
import org.churchpresenter.resources.generated.resources.songs
import org.churchpresenter.resources.generated.resources.tab_web
import org.churchpresenter.resources.generated.resources.tab_canvas
import org.churchpresenter.resources.generated.resources.tab_qa
import org.churchpresenter.resources.generated.resources.tab_stt
import org.churchpresenter.resources.generated.resources.crossword_tab
import org.churchpresenter.resources.generated.resources.tab_dictionary
import org.churchpresenter.resources.generated.resources.tab_companion_surface
import org.churchpresenter.ui.TabStripBackArrow
import org.churchpresenter.ui.TabStripForwardArrow
import org.jetbrains.compose.resources.stringResource

@Composable
fun TabSection(
    modifier: Modifier = Modifier,
    visibleTabs: List<Tabs> = Tabs.entries,
    selectedTabIndex: Int = 0,
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
            divider = {},
        ) {
            visibleTabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { onTabSelected.invoke(index) },
                    text = {
                        Text(
                            style = if (selectedTabIndex == index)
                                MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            else
                                MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
                            color = if (selectedTabIndex == index)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                            text = getStringName(tab),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
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
