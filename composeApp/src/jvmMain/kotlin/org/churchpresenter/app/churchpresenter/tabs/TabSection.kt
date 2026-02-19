package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.announcements
import churchpresenter.composeapp.generated.resources.bible
import churchpresenter.composeapp.generated.resources.media
import churchpresenter.composeapp.generated.resources.pictures
import churchpresenter.composeapp.generated.resources.songs
import org.jetbrains.compose.resources.stringResource

@Composable
fun TabSection(
    modifier: Modifier = Modifier,
    selectedTabIndex: Int = Tabs.BIBLE.ordinal,
    onTabSelected: (Int) -> Unit,
) {
    PrimaryTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Tabs.entries.forEachIndexed { index, tab ->
            Tab(
                selected = selectedTabIndex == index,
                onClick = {
                    onTabSelected.invoke(index)
                },
                text = {
                    Text(
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        text = getStringName(tab),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
private fun getStringName(tabs: Tabs): String {
    return when (tabs) {
        Tabs.BIBLE -> stringResource(Res.string.bible)
        Tabs.SONGS -> stringResource(Res.string.songs)
        Tabs.PICTURES -> stringResource(Res.string.pictures)
        Tabs.MEDIA -> stringResource(Res.string.media)
        Tabs.ANNOUNCEMENTS -> stringResource(Res.string.announcements)
    }
}