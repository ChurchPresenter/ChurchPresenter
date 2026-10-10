@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lottiegen.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.churchpresenter.lottiegen.band.BandColorRole
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.lottiegen.band.BibleLottieGenViewModel
import org.churchpresenter.lottiegen.band.SlotLayout
import org.churchpresenter.lottiegen.band.TextAnimation
import org.churchpresenter.lottiegen.band.ui.BandPhase
import org.churchpresenter.lottiegen.band.ui.BandPreviewPanel
import org.churchpresenter.lottiegen.lottie.LottieGenerator
import org.churchpresenter.lottiegen.lottie.LottieTextShaping
import org.churchpresenter.lottiegen.model.LottieGenConfig
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviewPanelsTest {

    private lateinit var temp: File
    private lateinit var savedHome: String
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @BeforeTest
    fun isolateHome() {
        temp = Files.createTempDirectory("preview-panels-test").toFile()
        savedHome = System.getProperty("user.home")
        System.setProperty("user.home", temp.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        scope.cancel()
        System.setProperty("user.home", savedHome)
        temp.deleteRecursively()
    }

    /** Taps the transport's track at [fraction] of its width, measured from the play key. */
    private fun ComposeUiTest.seekTo(fraction: Float, trackWidth: Float, gap: Float) {
        val keys = listOf("Pause", "Play")
        val key = keys.first { has(it) }
        onAllNodes(androidx.compose.ui.test.hasContentDescription(key), useUnmergedTree = true)[0].performTouchInput {
            val touch = this
            touch.click(Offset(width + gap.dp.toPx() + fraction * trackWidth.dp.toPx(), height / 2f))
        }
        waitForIdle()
    }

    private fun ComposeUiTest.has(description: String) =
        onAllNodes(androidx.compose.ui.test.hasContentDescription(description), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `the generator preview waits, plays, pauses and seeks`() = runDesktopComposeUiTest(900, 700) {
        var json: String? by mutableStateOf(null)
        showDark(900.dp, 700.dp) {
            PreviewPanel(
                json, 16f / 9f, "status line", 1920, 1080, 7f,
                guides = listOf(PreviewGuide(0.1f, 0.1f, 0.5f, 0.2f)),
            )
        }
        assertTrue(hasNode(Strings.generating) && hasNode("status line") && hasNode("1920 × 1080"))
        json = Json.encodeToString(JsonObject.serializer(), LottieGenerator.generate(LottieGenConfig()))
        waitForIdle()
        clickDescription("Pause")
        assertTrue(has("Play"))
        seekTo(0.5f, 900f - 36 - 34 - 28 - 42, 14f)
        assertFalse(hasNode("0%"))
        assertTrue(hasNode("%", substring = true))
    }

    @Test
    fun `guides show only inside their window, and the size only when both sides are known`() =
        runDesktopComposeUiTest(900, 700) {
            val json = Json.encodeToString(JsonObject.serializer(), LottieGenerator.generate(LottieGenConfig()))
            showDark(900.dp, 700.dp) {
                PreviewPanel(
                    json, 16f / 9f, "", canvasW = 1920, canvasH = 0,
                    guides = listOf(PreviewGuide(0f, 0f, 1f, 1f)), guideWindow = 0.9f..1f,
                )
            }
            assertFalse(hasNode("1920 × 0"))
            clickDescription("Pause")
            seekTo(0.5f, 900f - 36 - 34 - 28 - 42, 14f)
            seekTo(0.97f, 900f - 36 - 34 - 28 - 42, 14f)
            assertFalse(hasNode("0%"))
        }

    @Test
    fun `the band preview shows what went wrong under the stage`() = runDesktopComposeUiTest(900, 700) {
        val vm = BibleLottieGenViewModel(scope, null, null, BibleLottieGenConfig())
        waitUntil(timeoutMillis = 5_000) { vm.generatedJson != null }
        vm.loadBandImage(BandColorRole.BACKGROUND, File(temp, "absent.png"))
        showDark(900.dp, 700.dp) { BandPreviewPanel(vm) }
        assertTrue(hasNode(Strings.bandStatusPictureUnreadable("absent.png")))
    }

    @Test
    fun `whole-line text is drawn from the bundled and the installed fonts`() = runDesktopComposeUiTest(900, 700) {
        val configs = listOf(
            LottieGenConfig(textShaping = "lines", fontFamily = "Poppins"),
            LottieGenConfig(textShaping = "lines", fontFamily = "Serif", nameWeight = 400),
        )
        var json by mutableStateOf(Json.encodeToString(JsonObject.serializer(), LottieGenerator.generate(configs[0])))
        showDark(900.dp, 700.dp) { PreviewPanel(json, 16f / 9f, "") }
        assertFalse(hasNode("1920 × 1080"))
        clickDescription("Pause")
        seekTo(0.6f, 900f - 36 - 34 - 28 - 42, 14f)
        json = Json.encodeToString(JsonObject.serializer(), LottieGenerator.generate(configs[1]))
        waitForIdle()
        seekTo(0.7f, 900f - 36 - 34 - 28 - 42, 14f)
        assertTrue(LottieTextShaping.groupsText(json))
    }

    @Test
    fun `the band preview runs each run-time text motion through its phases`() = runDesktopComposeUiTest(900, 700) {
        val motions = listOf(TextAnimation.TYPEWRITER, TextAnimation.TYPEWRITER_WORDS, TextAnimation.TICKER)
        val models = motions.map { motion ->
            val seed = BibleLottieGenConfig(textAnimation = motion, layout = SlotLayout.SIDE_BY_SIDE)
            BibleLottieGenViewModel(scope, null, null, seed)
        }
        var current by mutableStateOf(models.first())
        setContent {
            LottieGenTheme { Box(Modifier.size(900.dp, 700.dp)) { key(current) { BandPreviewPanel(current) } } }
        }
        for (vm in models) {
            val motion = vm.config.textAnimation
            waitUntil(timeoutMillis = 5_000) { vm.generatedJson != null }
            current = vm
            waitForIdle()
            clickDescription("Pause")
            val seen = mutableSetOf<String>()
            for (f in listOf(0.02f, 0.3f, 0.5f, 0.7f, 0.85f, 0.98f)) {
                seekTo(f, 900f - 32 - 38 - 26 - 60, 13f)
                BandPhase.entries.map { it.label }.filter { hasNode(it) }.forEach { seen += it }
            }
            assertTrue(seen.size >= 3, "$motion phases seen: $seen")
        }
    }
}
