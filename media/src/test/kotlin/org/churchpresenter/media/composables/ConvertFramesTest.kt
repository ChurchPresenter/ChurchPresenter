@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.media.composables

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.v2.runComposeUiTest
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConvertFramesTest {

    @Test
    fun `a new frame version publishes the frame to the player and to every output`() = runComposeUiTest {
        val version = mutableStateOf(0L)
        val holder = mutableStateOf<BufferedImage?>(null)
        val out = mutableStateOf<ImageBitmap?>(null)
        var shown by mutableStateOf(true)
        SharedVideoOutput.frame.value = null
        setContent { if (shown) ConvertFramesOffRenderThread(version, holder, out) }
        waitForIdle()
        version.value = 1L
        waitForIdle()
        assertNull(out.value, "a version with no frame behind it publishes nothing")
        holder.value = BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB)
        version.value = 2L
        waitUntil(timeoutMillis = 5_000) { out.value != null }
        assertEquals(3, out.value?.width)
        assertEquals(out.value, SharedVideoOutput.frame.value)
        shown = false
        waitForIdle()
        SharedVideoOutput.frame.value = null
    }
}
