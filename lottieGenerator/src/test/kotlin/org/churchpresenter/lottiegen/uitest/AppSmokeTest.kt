@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lottiegen.uitest

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.lottiegen.App
import org.churchpresenter.lottiegen.band.BibleLottieGenApp
import org.churchpresenter.lottiegen.editor.EditorViewModel
import org.churchpresenter.lottiegen.editor.StyleEditorApp
import org.churchpresenter.lottiegen.editor.ui.EditorLabels
import org.churchpresenter.lottiegen.ui.Strings
import org.churchpresenter.lottiegen.ui.click
import org.churchpresenter.lottiegen.ui.hasNode
import org.churchpresenter.lottiegen.ui.node
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class AppSmokeTest {

    private lateinit var temp: File
    private lateinit var savedHome: String

    @BeforeTest
    fun isolateHome() {
        temp = Files.createTempDirectory("app-smoke-test").toFile()
        savedHome = System.getProperty("user.home")
        System.setProperty("user.home", temp.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        System.setProperty("user.home", savedHome)
        temp.deleteRecursively()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.dragDivider(fromDp: Int, byDp: Int) {
        onRoot().performTouchInput {
            val touch = this
            val y = 300.dp.toPx()
            touch.swipe(Offset(fromDp.dp.toPx(), y), Offset((fromDp + byDp).dp.toPx(), y))
        }
        waitForIdle()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.widthOf(text: String): Float =
        node(text).fetchSemanticsNode().size.width.toFloat()

    @Test
    fun `the standalone generator widens its panel when the divider is dragged`() =
        runDesktopComposeUiTest(1400, 1000) {
        setContent { Box(Modifier.size(1400.dp, 1000.dp)) { App() } }
        waitForIdle()
        assertTrue(hasNode(Strings.appTitle) && hasNode(Strings.downloadJson))
        val before = widthOf(Strings.appTitle)
        dragDivider(438, 150)
        assertTrue(widthOf(Strings.appTitle) > before)
    }

    @Test
    fun `embedded with an output folder, the generator saves rather than downloads`() =
        runDesktopComposeUiTest(1400, 1000) {
        setContent {
            ChurchPresenterTheme(ThemeMode.LIGHT) {
                Box(Modifier.size(1400.dp, 1000.dp)) {
                    App(outputDir = temp, onFileSaved = {}, canvasWidth = 1280, canvasHeight = 720)
                }
            }
        }
        waitForIdle()
        assertTrue(hasNode(Strings.saveLowerThird))
        assertTrue(hasNode("1280 × 720"))
    }

    @Test
    fun `the editor switches to its test matrix and back`() = runDesktopComposeUiTest(1500, 1000) {
        setContent { Box(Modifier.size(1500.dp, 1000.dp)) { StyleEditorApp(standalone = true) } }
        waitForIdle()
        assertTrue(hasNode(EditorViewModel.templateSpec().name, substring = true))
        click(Strings.editorModeMatrix)
        waitUntil(timeoutMillis = 5_000) { hasNode(EditorLabels.align("left"), substring = true) }
        click(Strings.editorModePreview)
        assertTrue(hasNode(Strings.editorTimeline))
    }

    @Test
    fun `the editor drops into a host theme`() = runDesktopComposeUiTest(1500, 1000) {
        setContent { MaterialTheme { Box(Modifier.size(1500.dp, 1000.dp)) { StyleEditorApp() } } }
        waitForIdle()
        assertTrue(hasNode(Strings.editorElements))
    }

    @Test
    fun `the band generator renders standalone and embedded with a lent colour field`() =
        runDesktopComposeUiTest(1400, 1000) {
            var embedded by mutableStateOf(false)
            setContent {
                Box(Modifier.size(1400.dp, 1000.dp)) {
                    if (embedded) {
                        MaterialTheme {
                            BibleLottieGenApp(
                                temp, {}, colorField = { label, color, _, _ -> Text("$label=$color") },
                            )
                        }
                    } else {
                        BibleLottieGenApp(null, null, embedded = false)
                    }
                }
            }
            waitForIdle()
            assertTrue(hasNode(Strings.bandTemplate.uppercase()))
            dragDivider(378, 100)
            embedded = true
            waitForIdle()
            assertTrue(hasNode("${Strings.bandColorBackground}=", substring = true))
        }
}
