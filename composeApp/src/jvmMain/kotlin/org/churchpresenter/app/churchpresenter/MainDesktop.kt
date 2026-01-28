package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.book
import churchpresenter.composeapp.generated.resources.chapter
import churchpresenter.composeapp.generated.resources.service_schedule
import churchpresenter.composeapp.generated.resources.verse
import org.churchpresenter.app.churchpresenter.tabs.TabSection
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import org.jetbrains.compose.resources.stringResource

@Composable
fun MainDesktop(
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()
    ) {
        Row {
            Column(modifier =Modifier.fillMaxWidth(0.20f)) {
                Text(text = stringResource(Res.string.service_schedule))
                Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
                    // TODO add schedule code here
                }
            }
            Column(modifier = Modifier.fillMaxWidth(0.6f)) {
                var selectedTab by rememberSaveable { mutableStateOf("") }
                TabSection { index ->
                    selectedTab = when(Tabs.entries[index]) {
                        Tabs.BIBLE -> "Bible"
                        Tabs.SONGS -> "Songs"
                        Tabs.ANNOUNCEMENTS -> "Announcements"
                        Tabs.MEDIA -> "Media"
                        Tabs.PICTURES -> "Pictures"
                    }
                }
                Text(
                    text = selectedTab,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(0.70f),
                        state = rememberTextFieldState(),
                        label = { Text(text = stringResource(Res.string.book)) }
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(0.15f),
                        state = rememberTextFieldState(),
                        label = { Text(text = stringResource(Res.string.chapter)) }
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(0.15f),
                        state = rememberTextFieldState(),
                        label = { Text(text = stringResource(Res.string.verse)) }
                    )
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