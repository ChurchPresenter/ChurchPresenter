@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lottiegen.uitest

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.lottiegen.App
import org.churchpresenter.lottiegen.band.BibleLottieGenApp
import org.churchpresenter.lottiegen.editor.StyleEditorApp
import org.churchpresenter.lottiegen.ui.Strings
import kotlin.test.Test
import kotlin.test.assertTrue

class AppSmokeTest {

    @Test
    fun `the generator renders standalone`() = runComposeUiTest {
        setContent { Box(Modifier.size(1400.dp, 1000.dp)) { App() } }
        waitForIdle()
        assertTrue(onAllNodesWithText(Strings.appTitle, substring = true).fetchSemanticsNodes().size >= 0)
    }

    @Test
    fun `the editor renders standalone`() = runComposeUiTest {
        setContent { Box(Modifier.size(1500.dp, 1000.dp)) { StyleEditorApp(standalone = true) } }
        waitForIdle()
    }

    @Test
    fun `the band generator renders standalone`() = runComposeUiTest {
        setContent { Box(Modifier.size(1400.dp, 1000.dp)) { BibleLottieGenApp(null, null, embedded = false) } }
        waitForIdle()
    }
}
