package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.announcements
import churchpresenter.composeapp.generated.resources.bible
import churchpresenter.composeapp.generated.resources.display_lower_third
import churchpresenter.composeapp.generated.resources.ic_arrow_left
import churchpresenter.composeapp.generated.resources.ic_arrow_right
import churchpresenter.composeapp.generated.resources.media
import churchpresenter.composeapp.generated.resources.pictures
import churchpresenter.composeapp.generated.resources.presentation
import churchpresenter.composeapp.generated.resources.songs
import churchpresenter.composeapp.generated.resources.tab_web
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun TabSection(
    modifier: Modifier = Modifier,
    selectedTabIndex: Int = Tabs.BIBLE.ordinal,
    onTabSelected: (Int) -> Unit,
) {
    val scrollState = remember { ScrollState(0) }
    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (scrollState.maxValue > 0 && scrollState.value > 0) {
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        scrollState.animateScrollTo((scrollState.value - 200).coerceAtLeast(0))
                    }
                },
                modifier = Modifier.size(28.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_arrow_left),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        PrimaryScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier.weight(1f),
            scrollState = scrollState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            edgePadding = 0.dp,
            divider = {},
        ) {
            Tabs.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { onTabSelected.invoke(index) },
                    text = {
                        Text(
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            text = getStringName(tab),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                )
            }
        }

        if (scrollState.maxValue > 0 && scrollState.value < scrollState.maxValue) {
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        scrollState.animateScrollTo(
                            (scrollState.value + 200).coerceAtMost(scrollState.maxValue)
                        )
                    }
                },
                modifier = Modifier.size(28.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_arrow_right),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun getStringName(tabs: Tabs): String {
    return when (tabs) {
        Tabs.BIBLE -> stringResource(Res.string.bible)
        Tabs.SONGS -> stringResource(Res.string.songs)
        Tabs.PICTURES -> stringResource(Res.string.pictures)
        Tabs.PRESENTATION -> stringResource(Res.string.presentation)
        Tabs.MEDIA -> stringResource(Res.string.media)
        Tabs.LOWER_THIRD -> stringResource(Res.string.display_lower_third)
        Tabs.ANNOUNCEMENTS -> stringResource(Res.string.announcements)
        Tabs.WEB -> stringResource(Res.string.tab_web)
    }
}
