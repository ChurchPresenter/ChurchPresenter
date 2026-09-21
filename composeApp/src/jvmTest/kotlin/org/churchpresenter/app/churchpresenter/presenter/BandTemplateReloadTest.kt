package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A template edited and saved over its own path must reach the output without a restart.
 *
 * The band generator always writes to the file it loaded from, so the path cannot tell a new
 * template from the old one. The loader was keyed on the path alone, which meant every control in
 * the generator appeared to do nothing on a running app — the crossfade length was the one that gave
 * it away, but it applied to every setting the template carries.
 */
@OptIn(ExperimentalTestApi::class)
class BandTemplateReloadTest {

    private val dir = Files.createTempDirectory("band-reload").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun writeBand(swapSeconds: Float) = LottieBandTestSupport.writeTemplate(
        dir,
        cfg = BibleLottieGenConfig(canvasW = 960, canvasH = 180, swapSeconds = swapSeconds),
    )

    @Test
    fun `saving over a template reloads it without a restart`() = runComposeUiTest {
        val template = writeBand(swapSeconds = 0.2f)
        var swapMs: Long? = null
        var sawNullAfterLoading = false
        var loadedOnce = false

        setContent {
            val loaded by rememberBibleLottieTemplate(template.absolutePath)
            val current = loaded
            if (current == null) {
                if (loadedOnce) sawNullAfterLoading = true
            } else {
                loadedOnce = true
                swapMs = current.swapMs()
            }
        }

        waitUntil("the template loaded") { swapMs != null }
        assertEquals(SHORT_SWAP_MS, swapMs, "the first template's crossfade length")

        // What the generator does: same path, new contents.
        writeBand(swapSeconds = 0.8f)
        invalidateBibleLottieTemplates()

        waitUntil("the new template reached the output") { swapMs == LONG_SWAP_MS }
        assertEquals(LONG_SWAP_MS, swapMs, "the saved template's crossfade length")
        assertTrue(
            !sawNullAfterLoading,
            "the band was dropped while the template reloaded, which flashes the whole lower third",
        )
    }

    private companion object {
        const val SHORT_SWAP_MS = 200L
        const val LONG_SWAP_MS = 800L
    }
}
