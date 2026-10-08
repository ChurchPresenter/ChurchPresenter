@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.dialogs.AlreadyRunningContent
import org.churchpresenter.sharedui.screenshot.captureComponent
import kotlin.test.Test

/**
 * The dialog a second launch shows while ChurchPresenter is already running.
 *
 * Shot on its own because the only way to reach it in the app is to start a second copy. The
 * dialog is drawn at the size its window opens at.
 */
class AlreadyRunningDialogScreenshotTest {

    @Test
    fun `the already-running dialog`() {
        captureComponent(
            SECTION,
            "default",
            drive = { waitUntilAtLeastOneExists(hasText("OK"), timeoutMillis = RESOURCE_TIMEOUT_MS) },
        ) {
            Box(Modifier.size(440.dp, 190.dp)) { AlreadyRunningContent(onDismiss = {}) }
        }
    }

    private companion object {
        const val SECTION = "alreadyRunningDialog"
        const val RESOURCE_TIMEOUT_MS = 5_000L
    }
}
