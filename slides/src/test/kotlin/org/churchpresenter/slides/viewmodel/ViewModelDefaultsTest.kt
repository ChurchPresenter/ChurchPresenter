package org.churchpresenter.slides.viewmodel

import org.churchpresenter.presentationengine.DeckRasterizer
import org.churchpresenter.presentationengine.LoadResult
import org.churchpresenter.slides.pdfDeck
import org.churchpresenter.slides.tempDir
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ViewModelDefaultsTest {

    private val dir = tempDir("cp-viewmodel-defaults")
    private val vm = PresentationViewModel()

    @AfterTest
    fun cleanUp() {
        runCatching { vm.dispose() }
        dir.deleteRecursively()
    }

    @Test
    fun `the default loader and renderer read a real deck`() {
        val deck = (vm.loadDeck(pdfDeck(dir, 2)) as LoadResult.Success).deck
        val frame = DeckRasterizer(deck, 64).use { vm.renderSlideFrame(it, 0) }
        assertEquals(2, deck.slideCount)
        assertTrue(frame.width > 0 && frame.height > 0)
    }
}
