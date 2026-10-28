package org.churchpresenter.lottiegen.band.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.churchpresenter.lottiegen.band.BandTextAlign
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.lottiegen.band.BibleLottieGenViewModel
import org.churchpresenter.lottiegen.ui.LottiePreviewProbe
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.math.abs
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

    /**
     * Tamil is drawn as whole lines, which the player would centre by its letters measured one at a
     * time — far left of centre. The preview places each line itself, as the app's band does.
     */
    @Test
    fun `a Tamil sample is centred line by line on the band`() {
        val cfg = BibleLottieGenConfig(
            bgColor = "#FF0000", bgAlpha = 100,
            previewFontFamily = "Noto Sans Tamil", previewText1 = TAMIL_VERSE,
            textAlign = BandTextAlign.CENTER, referenceAlign = BandTextAlign.CENTER,
        )
        val vm = BibleLottieGenViewModel(scope, null, null, cfg)
        val deadline = System.currentTimeMillis() + 5_000
        while (vm.generatedJson == null) {
            check(System.currentTimeMillis() < deadline) { "timed out waiting for the band's JSON: ${vm.statusText}" }
            Thread.onSpinWait()
        }
        LottiePreviewProbe.run({ BandPreviewPanel(vm) }) {
            val frame = pumpUntilFrame("the sample drawn on the band") { whiteRows(it, band(it)).size >= 2 }
            val band = band(frame)
            val bandCentre = (band.left + band.right) / 2f
            val bandWidth = (band.right - band.left).toFloat()
            whiteRows(frame, band).forEach { span ->
                val centre = (span.first + span.last) / 2f
                assertTrue(
                    abs(centre - bandCentre) < bandWidth * 0.01f,
                    "a line spanning $span is centred on $bandCentre",
                )
            }
        }
    }

    /** The red band's bounds in a frame: the rectangle its red pixels span. */
    private class Band(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun band(pixels: IntArray): Band {
        val width = LottiePreviewProbe.WIDTH
        val red = pixels.indices.filter { LottiePreviewProbe.isRed(pixels[it]) }
        if (red.isEmpty()) return Band(0, 0, -1, -1)
        return Band(red.minOf { it % width }, red.first() / width, red.maxOf { it % width }, red.last() / width)
    }

    /** The horizontal extent of each run of rows holding white text inside [band], top to bottom. */
    private fun whiteRows(pixels: IntArray, band: Band): List<IntRange> {
        val width = LottiePreviewProbe.WIDTH
        val spans = mutableListOf<IntRange>()
        var left = Int.MAX_VALUE
        var right = -1
        for (y in band.top..band.bottom) {
            val xs = (band.left..band.right).filter { x -> isWhite(pixels[y * width + x]) }
            if (xs.isNotEmpty()) {
                left = minOf(left, xs.first())
                right = maxOf(right, xs.last())
            } else if (right >= 0) {
                spans += left..right
                left = Int.MAX_VALUE
                right = -1
            }
        }
        return spans
    }

    private fun isWhite(argb: Int): Boolean =
        (argb shr 16 and 0xFF) > 220 && (argb shr 8 and 0xFF) > 220 && (argb and 0xFF) > 220

    private companion object {
        const val TAMIL_VERSE = "கர்த்தராகிய இயேசுவினாலே நாங்கள் உங்களுக்குக் கொடுத்த கட்டளைகளை " +
            "அறிந்திருக்கிறீர்களே. நீங்கள் பரிசுத்தமுள்ளவர்களாகவேண்டுமென்பதே தேவனுடைய சித்தமாயிருக்கிறது."
    }
}
