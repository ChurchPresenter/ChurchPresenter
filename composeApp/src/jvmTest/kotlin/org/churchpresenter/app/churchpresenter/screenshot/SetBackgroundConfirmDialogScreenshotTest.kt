@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import org.churchpresenter.app.churchpresenter.PendingBackground
import org.churchpresenter.app.churchpresenter.SetBackgroundConfirmDialog
import org.churchpresenter.settings.BackgroundContent
import org.churchpresenter.sharedui.composables.BackgroundTarget
import org.churchpresenter.sharedui.screenshot.captureComponent
import java.io.File
import kotlin.test.Test

/**
 * The question Set as Background asks before it saves: which background, on which outputs, and
 * which file. Asked because the screen can look the same either way -- a song that follows Default
 * shows a new Default too -- so the words are what tell Default from Songs.
 */
class SetBackgroundConfirmDialogScreenshotTest {

    private fun shoot(name: String, pending: PendingBackground) =
        captureComponent(SECTION, name, rootIndex = 1) {
            SetBackgroundConfirmDialog(pending = pending, onConfirm = {}, onDismiss = {})
        }

    @Test
    fun `one profile's outputs, the Songs background`() = shoot(
        "songs_on_one_profile",
        PendingBackground(
            File("Sanctuary sunrise.jpg"),
            BackgroundContent.SONG,
            BackgroundTarget("main", "Screen 1 + NDI Output 1"),
        ),
    )

    @Test
    fun `every output, the Default background`() = shoot(
        "default_on_all_outputs",
        PendingBackground(File("Waves loop.mp4"), BackgroundContent.DEFAULT, target = null),
    )

    private companion object {
        const val SECTION = "setBackgroundConfirm"
    }
}
