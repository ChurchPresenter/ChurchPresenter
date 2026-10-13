@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.sharedui.guide

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

/** How a window below the helper wraps itself in the spotlight it cannot depend on. */
class LocalSpotlightHostTest {

    @Test
    fun `with no helper the window's content is drawn as it is`() = runComposeUiTest {
        setContent {
            LocalSpotlightHost.current(Modifier.testTag("window")) { Text("Bibles") }
        }
        onNodeWithTag("window").assertExists()
        onNodeWithText("Bibles").assertExists()
    }

    @Test
    fun `a provided host wraps the window's content`() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(
                LocalSpotlightHost provides { modifier, content ->
                    Box(modifier.testTag("spotlight")) { content() }
                },
            ) {
                LocalSpotlightHost.current(Modifier) { Text("Bibles") }
            }
        }
        onNodeWithTag("spotlight").assertExists()
        onNodeWithText("Bibles").assertExists()
    }
}
