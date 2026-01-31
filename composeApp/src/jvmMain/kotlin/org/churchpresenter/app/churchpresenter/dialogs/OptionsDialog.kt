package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.ok
import churchpresenter.composeapp.generated.resources.options
import churchpresenter.composeapp.generated.resources.song
import org.churchpresenter.app.churchpresenter.composables.ErrorButton
import org.churchpresenter.app.churchpresenter.composables.SuccessButton
import org.churchpresenter.app.churchpresenter.ui.theme.AppThemeWrapper
import org.jetbrains.compose.resources.stringResource

@Composable
fun OptionsDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AppThemeWrapper {
        if (isVisible) {
            val windowState = rememberWindowState(
                position = WindowPosition(300.dp, 100.dp),
                width = 750.dp,
                height = 550.dp
            )

            Window(
                onCloseRequest = onDismiss,
                title = "Options",
                state = windowState,
                resizable = false
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    OptionsDialogContent(
                        onDismiss = onDismiss,
                        onSave = onSave
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionsDialogContent(
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        stringResource(Res.string.song),
        stringResource(Res.string.bible),
        "Text Settings and Colors",
        "Background",
        "Background Images",
        "Folders",
        "Projection",
        "Other"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Title
        Text(
            text = stringResource(Res.string.options),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurface
        )

        HorizontalDivider()

        // Tabs
        PrimaryTabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                    }
                )
            }
        }

        // Content area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
        ) {
            when (selectedTabIndex) {
                0 -> Text("Song tab content will be implemented")
                1 -> Text("Bible tab content will be implemented")
                2 -> Text("Text Settings and Colors tab content will be implemented")
                3 -> Text("Background tab content will be implemented")
                4 -> Text("Background Images tab content will be implemented")
                5 -> Text("Folders tab content will be implemented")
                6 -> Text("Projection tab content will be implemented")
                7 -> Text("Other tab content will be implemented")
            }
        }

        HorizontalDivider()

        // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SuccessButton(
                onClick = onSave,
                modifier = Modifier.padding(end = 8.dp),
                text = stringResource(Res.string.ok),
            )

            ErrorButton(
                onClick = onDismiss,
                text = stringResource(Res.string.cancel)
            )
        }
    }
}


@Preview
@Composable
private fun OptionsDialogPreview() {
    Card(
        modifier = Modifier
            .width(750.dp)
            .height(550.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        OptionsDialogContent(
            onDismiss = {},
            onSave = {}
        )
    }
}

