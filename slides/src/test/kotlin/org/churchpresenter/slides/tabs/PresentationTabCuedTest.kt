@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.slides.tabs

import org.churchpresenter.slides.FakeSlidesOutput
import org.churchpresenter.slides.SlidesOutput
import org.churchpresenter.slides.awaitDeck
import org.churchpresenter.slides.pdfDeck
import org.churchpresenter.slides.tempDir
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PresentationTabCuedTest {

    private val dir = tempDir("cp-presentation-cued")

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private class CuedOutput(val fake: FakeSlidesOutput = FakeSlidesOutput()) : SlidesOutput by fake {
        override val presentationCued: Boolean get() = true
    }

    @Test
    fun `a cued output is sent the selected slide and the one after it`() {
        val out = CuedOutput()
        presentationTab(presenterManager = out) { vm, _ ->
            vm.addPresentation(pdfDeck(dir, 3))
            awaitDeck(vm)
            vm.selectSlide(1)
            waitUntil("the slide sent to the cued output", 5_000) { out.fake.liveSlide.value?.second == 1 }
            assertNotNull(out.fake.selectedSlide.value)
            waitUntil("the next slide sent", 5_000) { out.fake.nextSlide.value != null }
        }
    }

    @Test
    fun `hiding a slide refreshes what a cued output shows next`() {
        val out = CuedOutput()
        presentationTab(presenterManager = out) { vm, _ ->
            vm.addPresentation(pdfDeck(dir, 2))
            awaitDeck(vm)
            vm.selectSlide(1)
            waitUntil("the last slide sent", 5_000) { out.fake.liveSlide.value?.second == 1 }
            waitForIdle()
            vm.toggleSlideHidden(0)
            waitForIdle()
            assertEquals(setOf(0), vm.hiddenSlides)
            waitUntil("nothing left to show next", 5_000) { out.fake.nextSlide.value == null }
        }
    }

    @Test
    fun `auto-play with no output moves on to the next slide`() = presentationTab { vm, _ ->
        vm.addPresentation(pdfDeck(dir, 3))
        awaitDeck(vm)
        vm.autoScrollInterval = 1f
        vm.togglePlayPause()
        mainClock.advanceTimeBy(1_500)
        waitUntil("the next slide", 5_000) { vm.selectedSlideIndex == 1 }
    }
}
