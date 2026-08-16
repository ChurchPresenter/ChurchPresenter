@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.TestSingletons
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class TabDefaultArgumentsTest {

    private lateinit var tempHome: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        TestSingletons.latchToTestHome()
        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-tab-defaults").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the media tab with no player provided offers no transport controls`() = runComposeUiTest {
        setContent { MaterialTheme { MediaTab() } }

        waitForIdle()
        assertTrue(
            renderedText().none { it.contains("Add to Schedule") },
            renderedText().toString(),
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the pictures tab stands up on its own defaults`() = runComposeUiTest {
        setContent { MaterialTheme { PicturesTab() } }

        waitForIdle()
        assertTrue(renderedText().isNotEmpty())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the pictures tab on its own defaults starts with no folder open`() = runComposeUiTest {
        setContent { MaterialTheme { PicturesTab() } }

        waitForIdle()
        assertTrue(
            renderedText().none { it.contains("Add to Schedule") },
            renderedText().toString(),
        )
    }
}
