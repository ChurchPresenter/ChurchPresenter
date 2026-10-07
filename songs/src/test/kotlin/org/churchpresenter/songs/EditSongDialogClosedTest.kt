@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTuning
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertFalse

class EditSongDialogClosedTest {

    @Test
    fun `a hidden song editor draws nothing`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                EditSongDialog(
                    isVisible = false,
                    song = SongItem(number = "1", title = "Amazing Grace"),
                    backgroundButton = testBackgroundButton,
                    theme = ThemeMode.LIGHT,
                    onDismiss = {},
                    onSave = { _: SongItem, _: SongTuning -> },
                )
            }
        }

        assertFalse(onRoot().printToString().contains("Amazing Grace"))
    }

    @Test
    fun `a song editor opened with no song draws nothing`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                EditSongDialog(
                    isVisible = true,
                    song = null,
                    backgroundButton = testBackgroundButton,
                    theme = ThemeMode.LIGHT,
                    onDismiss = {},
                    onSave = { _: SongItem, _: SongTuning -> },
                )
            }
        }

        assertFalse(onRoot().printToString().contains("Save"))
    }
}
