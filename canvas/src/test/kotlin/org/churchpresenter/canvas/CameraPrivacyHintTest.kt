@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CameraPrivacyHintTest {

    private val buttonText = "Open Camera Privacy Settings"

    @Test
    fun `on macOS the button opens the camera privacy pane`() = runComposeUiTest {
        val opened = mutableListOf<String>()
        setContent { MaterialTheme { CameraPrivacyHint(osName = "Mac OS X", openUri = { opened += it }) } }

        onNodeWithText(buttonText).performClick()

        assertEquals(listOf(MAC_CAMERA_PRIVACY_URI), opened)
    }

    @Test
    fun `on Windows the button opens the webcam privacy page`() = runComposeUiTest {
        val opened = mutableListOf<String>()
        setContent { MaterialTheme { CameraPrivacyHint(osName = "Windows 11", openUri = { opened += it }) } }

        onNodeWithText(buttonText).performClick()

        assertEquals(listOf(WINDOWS_CAMERA_PRIVACY_URI), opened)
    }

    @Test
    fun `on Linux there is no privacy page to offer`() = runComposeUiTest {
        setContent { MaterialTheme { CameraPrivacyHint(osName = "Linux", openUri = {}) } }

        onNodeWithText(buttonText).assertDoesNotExist()
    }
}
