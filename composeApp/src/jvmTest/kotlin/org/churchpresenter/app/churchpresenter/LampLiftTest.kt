package org.churchpresenter.app.churchpresenter

import kotlin.test.Test
import kotlin.test.assertEquals

/** How far the helper lamp rises to keep above a Companion surface in the right sidebar. */
class LampLiftTest {

    @Test
    fun `with no Companion surface in the sidebar the lamp stays in its corner`() {
        assertEquals(0f, lampLift(companionTop = null, cornerBottom = 900f))
    }

    @Test
    fun `before the corner is measured the lamp does not move`() {
        assertEquals(0f, lampLift(companionTop = 700f, cornerBottom = null))
    }

    @Test
    fun `the lamp rises by the height from the corner up to the surface's divider`() {
        assertEquals(200f, lampLift(companionTop = 700f, cornerBottom = 900f))
    }

    @Test
    fun `a surface wholly below the corner never pulls the lamp down`() {
        assertEquals(0f, lampLift(companionTop = 950f, cornerBottom = 900f))
    }
}
