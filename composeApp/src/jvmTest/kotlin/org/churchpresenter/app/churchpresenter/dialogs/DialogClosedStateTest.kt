@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.runComposeUiTest
import core.models.songs.SongItem
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import org.churchpresenter.app.churchpresenter.models.SongTuning
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertFalse

class DialogClosedStateTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a hidden song editor draws nothing`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                EditSongDialog(
                    isVisible = false,
                    song = SongItem(number = "1", title = "Amazing Grace"),
                    theme = ThemeMode.LIGHT,
                    onDismiss = {},
                    onSave = { _: SongItem, _: SongTuning -> },
                )
            }
        }

        assertFalse(onRoot().printToString().contains("Amazing Grace"))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a song editor opened with no song draws nothing`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                EditSongDialog(
                    isVisible = true,
                    song = null,
                    theme = ThemeMode.LIGHT,
                    onDismiss = {},
                    onSave = { _: SongItem, _: SongTuning -> },
                )
            }
        }

        assertFalse(onRoot().printToString().contains("Save"))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a hidden statistics window draws nothing`() = withStatsHome { _ ->
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    CCLIReportDialog(
                        isVisible = false,
                        theme = ThemeMode.LIGHT,
                        statisticsManager = StatisticsManager(),
                        onDismiss = {},
                    )
                }
            }

            assertFalse(onRoot().printToString().contains("CCLI"))
        }
    }
}
