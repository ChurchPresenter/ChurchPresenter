@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.sharedui.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

/** The spinner-and-label row a list shows while it is still being read off disk. */
class ScanningRowTest {

    @Test
    fun `it says what is being scanned, under its own tag`() = runComposeUiTest {
        setContent { MaterialTheme { ScanningRow("Scanning for Bibles…") } }
        onNodeWithTag(SCANNING_ROW_TAG).assertExists()
        onNodeWithText("Scanning for Bibles…").assertExists()
    }
}
