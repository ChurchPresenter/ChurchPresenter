package org.churchpresenter.hosanna.composables.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressModelTest {

    @Test
    fun `percent text rounds and clamps`() {
        assertEquals("0%", percentText(0f))
        assertEquals("64%", percentText(0.642f))
        assertEquals("65%", percentText(0.645f))
        assertEquals("100%", percentText(1.4f))
        assertEquals("0%", percentText(-0.2f))
    }
}
