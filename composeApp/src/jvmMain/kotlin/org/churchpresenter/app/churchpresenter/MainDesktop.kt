package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.service_schedule
import org.churchpresenter.app.churchpresenter.tabs.AnnouncementsTab
import org.churchpresenter.app.churchpresenter.tabs.BibleTab
import org.churchpresenter.app.churchpresenter.tabs.MediaTab
import org.churchpresenter.app.churchpresenter.tabs.PicturesTab
import org.churchpresenter.app.churchpresenter.tabs.SongsTab
import org.churchpresenter.app.churchpresenter.tabs.TabSection
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import org.jetbrains.compose.resources.stringResource

@Composable
fun MainDesktop(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Row {
            Column(modifier = Modifier.fillMaxWidth(0.20f)) {
                Text(
                    text = stringResource(Res.string.service_schedule),
                    modifier = Modifier.padding(8.dp)
                )
                Box(modifier = Modifier.fillMaxWidth().background(Color.White).padding(8.dp)) {
                    Text("Service schedule (empty)")
                }
            }

            Column(modifier = Modifier.fillMaxWidth(0.8f).padding(8.dp)) {
                var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
                TabSection { tabIndex ->
                    selectedTabIndex = tabIndex
                }

                when (Tabs.entries[selectedTabIndex]) {
                    Tabs.BIBLE -> BibleTab()
                    Tabs.SONGS -> SongsTab()
                    Tabs.PICTURES -> PicturesTab()
                    Tabs.MEDIA -> MediaTab()
                    Tabs.ANNOUNCEMENTS -> AnnouncementsTab()
                }
            }
        }
    }
}

@Preview
@Composable
fun MainDesktopPreview() {
    MainDesktop()
}