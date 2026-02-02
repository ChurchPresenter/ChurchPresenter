package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.service_schedule
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.tabs.AnnouncementsTab
import org.churchpresenter.app.churchpresenter.tabs.BibleTab
import org.churchpresenter.app.churchpresenter.tabs.MediaTab
import org.churchpresenter.app.churchpresenter.tabs.PicturesTab
import org.churchpresenter.app.churchpresenter.tabs.SongsTab
import org.churchpresenter.app.churchpresenter.tabs.TabSection
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.app.churchpresenter.data.Bible
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.Presenting

@Composable
fun MainDesktop(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    presenting: (Presenting) -> Unit,
    onVerseSelected: (SelectedVerse) -> Unit,
    onSongItemSelected: (LyricSection) -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        // Create Bible instance and load the bundled ru_RST77.spb resource (placed under resources/)
        val bible = remember {
            Bible().apply {
                // loadFromSpb accepts either a classpath resource name or an absolute path
                loadFromSpb("ru_RST77.spb")
            }
        }

        Row {
            Column(modifier = Modifier.fillMaxWidth(0.20f)) {
                Text(
                    text = stringResource(Res.string.service_schedule),
                    modifier = Modifier.padding(8.dp),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(8.dp)
                ) {
                    Text(
                        "Service schedule (empty)",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth(0.8f).padding(8.dp)) {
                var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
                TabSection { tabIndex ->
                    selectedTabIndex = tabIndex
                }

                when (Tabs.entries[selectedTabIndex]) {
                    Tabs.BIBLE -> BibleTab(
                        bible = bible,
                        onVerseSelected = onVerseSelected,
                        presenting = presenting
                    )

                    Tabs.SONGS -> SongsTab(
                        appSettings = appSettings,
                        onSongItemSelected = onSongItemSelected,
                        presenting = presenting
                    )

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
    MainDesktop(
        modifier = Modifier.fillMaxSize(),
        onVerseSelected = {},
        presenting = {},
        onSongItemSelected = {},
        appSettings = AppSettings()
    )
}