package org.churchpresenter.lowerthird

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class LowerThirdOffscreenRendererTest {

    private val lottieJson =
        """{"v":"5.5.2","fr":30,"ip":0,"op":30,"w":10,"h":10,"nm":"test","ddd":0,"assets":[],"layers":[]}"""

    @Test
    fun `renderStill returns a pixel buffer sized to the requested canvas`() = runBlocking {
        val pixels = LowerThirdOffscreenRenderer(10, 10).renderStill(lottieJson, progress = 0.5f)

        assertEquals(100, pixels.size)
    }

    @Test
    fun `renderAllFrames returns the requested number of correctly-sized frames`() = runBlocking {
        val frames = LowerThirdOffscreenRenderer(10, 10).renderAllFrames(lottieJson, frameCount = 3)

        assertEquals(3, frames.size)
        frames.forEach { assertEquals(100, it.size) }
    }

    @Test
    fun `renderAllFrames with a single frame does not divide by zero`() = runBlocking {
        val frames = LowerThirdOffscreenRenderer(10, 10).renderAllFrames(lottieJson, frameCount = 1)

        assertEquals(1, frames.size)
        assertEquals(100, frames[0].size)
    }

    @Test
    fun `each returned frame is an independent copy, not a shared buffer`() = runBlocking {
        val frames = LowerThirdOffscreenRenderer(10, 10).renderAllFrames(lottieJson, frameCount = 2)

        assertNotSame(frames[0], frames[1])
    }
}
