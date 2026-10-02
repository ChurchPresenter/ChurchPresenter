@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.sharedui.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PresentedBlockTest {

    private val block = PresentedBlock(PresentedBlock.Kind.BOX, "subtitle")

    @Test
    fun `a block is reported where it was laid out when someone is listening`() = runComposeUiTest {
        val blocks = mutableMapOf<PresentedBlock, Rect>()
        setContent {
            CompositionLocalProvider(LocalPresentedBlocks provides blocks) {
                Box(Modifier.size(40.dp).reportsBlock(block))
            }
        }
        waitForIdle()
        assertEquals(setOf(block), blocks.keys)
        assertTrue(blocks.getValue(block).width > 0f)
        assertEquals("subtitle", block.key)
        assertEquals(PresentedBlock.Kind.BOX, block.kind)
    }

    @Test
    fun `with nobody listening nothing is reported`() = runComposeUiTest {
        setContent { Box(Modifier.size(40.dp).reportsBlock(block)) }
        waitForIdle()
    }
}
