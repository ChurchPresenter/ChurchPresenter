@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.slides.screenshot

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.dp
import org.churchpresenter.presentationengine.DeckRasterizer
import org.churchpresenter.presentationengine.LoadResult
import org.churchpresenter.presentationengine.PresentationLoader
import org.churchpresenter.presentationengine.fonts.SlideFontRegistry
import org.churchpresenter.presentationengine.textEffectsPptx
import org.churchpresenter.sharedui.screenshot.captureComponent
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test

class PptxTextEffectsScreenshotTest {

    private val temp: File = Files.createTempDirectory("cp-text-effects-shot").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun renderedSlide(): ImageBitmap {
        // The engine's own bundled face, so the words are the same on every machine.
        SlideFontRegistry::class.java.getResourceAsStream("/slide-fonts/Carlito-Bold.ttf")
            ?.let { SlideFontRegistry.registerFontStream(it) }
        val file = textEffectsPptx(File(temp, "effects.pptx"))
        val deck = (PresentationLoader.load(file) as LoadResult.Success).deck
        return DeckRasterizer(deck, targetWidthPx = SLIDE_WIDTH).use { it.renderFinalFrame(0) }.toComposeImageBitmap()
    }

    @Test
    fun `a slide's text outline, glow and shadow are drawn as PowerPoint draws them`() {
        val slide = renderedSlide()
        captureComponent(SECTION, "text_effects") {
            Image(slide, contentDescription = null, modifier = Modifier.size(SLIDE_WIDTH.dp, SLIDE_HEIGHT.dp))
        }
    }

    private companion object {
        const val SECTION = "pptxTextEffects"
        const val SLIDE_WIDTH = 720
        const val SLIDE_HEIGHT = 405
    }
}
