package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.tabs.AnnouncementsTab
import org.churchpresenter.app.churchpresenter.tabs.BibleTab
import org.churchpresenter.app.churchpresenter.tabs.MediaTab
import org.churchpresenter.app.churchpresenter.tabs.PicturesTab
import org.churchpresenter.app.churchpresenter.tabs.ScheduleTab
import org.churchpresenter.app.churchpresenter.tabs.SongsTab
import org.churchpresenter.app.churchpresenter.tabs.TabSection
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.ScheduleViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.SongsViewModel

@Composable
fun MainDesktop(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    bibleViewModel: BibleViewModel,
    songsViewModel: SongsViewModel,
    scheduleViewModel: ScheduleViewModel,
    presenting: (Presenting) -> Unit,
    onVerseSelected: (List<SelectedVerse>) -> Unit,
    onSongItemSelected: (LyricSection) -> Unit,
    onTabChange: (Int) -> Unit = {},
    onScheduleItemSelected: (String?) -> Unit = {}
) {
    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
        var selectedScheduleItemId by remember { mutableStateOf<String?>(null) }

        // Notify parent when tab changes
        LaunchedEffect(selectedTabIndex) {
            onTabChange(selectedTabIndex)
        }

        // Notify parent when schedule item selection changes
        LaunchedEffect(selectedScheduleItemId) {
            onScheduleItemSelected(selectedScheduleItemId)
        }

        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth(0.30f).fillMaxHeight()) {
                ScheduleTab(
                    scheduleViewModel = scheduleViewModel,
                    songsViewModel = songsViewModel,
                    bibleViewModel = bibleViewModel,
                    onSongItemSelected = onSongItemSelected,
                    onVerseSelected = onVerseSelected,
                    onPresenting = presenting,
                    onItemClick = { item ->
                        selectedScheduleItemId = if (selectedScheduleItemId == item.id) null else item.id
                        when (item) {
                            is org.churchpresenter.app.churchpresenter.models.ScheduleItem.SongItem -> {
                                // Switch to Songs tab (index 1)
                                selectedTabIndex = 1
                                // Select the song
                                songsViewModel.selectSongByDetails(
                                    songNumber = item.songNumber,
                                    title = item.title,
                                    songbook = item.songbook
                                )
                            }
                            is org.churchpresenter.app.churchpresenter.models.ScheduleItem.BibleVerseItem -> {
                                // Switch to Bible tab (index 0)
                                selectedTabIndex = 0
                                // Select the verse
                                bibleViewModel.selectVerseByDetails(
                                    bookName = item.bookName,
                                    chapter = item.chapter,
                                    verseNumber = item.verseNumber
                                )
                            }
                        }
                    }
                )
            }

            Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                TabSection(
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = { tabIndex ->
                        selectedTabIndex = tabIndex
                    }
                )

                when (Tabs.entries[selectedTabIndex]) {
                    Tabs.BIBLE -> BibleTab(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = bibleViewModel,
                        scheduleViewModel = scheduleViewModel,
                        onVerseSelected = onVerseSelected,
                        onPresenting = presenting
                    )

                    Tabs.SONGS -> SongsTab(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = songsViewModel,
                        scheduleViewModel = scheduleViewModel,
                        onSongItemSelected = onSongItemSelected,
                        onPresenting = presenting
                    )

                    Tabs.PICTURES -> PicturesTab()
                    Tabs.MEDIA -> MediaTab()
                    Tabs.ANNOUNCEMENTS -> AnnouncementsTab()
                }
            }
        }
    }
}