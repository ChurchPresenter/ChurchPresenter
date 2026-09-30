package org.churchpresenter.lottiegen.band.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.lottiegen.band.BibleLottieGenViewModel
import org.churchpresenter.lottiegen.ui.LottiePreviewProbe
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class BandPreviewPanelTest {

    private lateinit var temp: File
    private lateinit var savedHome: String
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @BeforeTest
    fun isolateHome() {
        temp = Files.createTempDirectory("band-preview-test").toFile()
        savedHome = System.getProperty("user.home")
        System.setProperty("user.home", temp.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        scope.cancel()
        System.setProperty("user.home", savedHome)
        temp.deleteRecursively()
    }

    @Test
    fun `the band preview draws the playing band`() {
        val vm = BibleLottieGenViewModel(scope, null, null, BibleLottieGenConfig(bgColor = "#FF0000", bgAlpha = 100))
        val deadline = System.currentTimeMillis() + 5_000
        while (vm.generatedJson == null) {
            check(System.currentTimeMillis() < deadline) { "timed out waiting for the band's JSON: ${vm.statusText}" }
            Thread.onSpinWait()
        }
        LottiePreviewProbe.run({ BandPreviewPanel(vm) }) {
            assertTrue(redPixels() < LottiePreviewProbe.DRAWN, "red before the composition loaded")
            pumpUntil("the band drawn") { it >= LottiePreviewProbe.DRAWN }
        }
    }
}
