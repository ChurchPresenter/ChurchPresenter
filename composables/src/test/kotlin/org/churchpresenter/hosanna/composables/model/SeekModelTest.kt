package org.churchpresenter.hosanna.composables.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SeekModelTest {

    @Test
    fun `clock text shows minutes, or hours past an hour`() {
        assertEquals("0:00", clockText(0.0))
        assertEquals("0:00", clockText(-3.0))
        assertEquals("1:23", clockText(83.9))
        assertEquals("59:59", clockText(3599.0))
        assertEquals("1:01:40", clockText(3700.0))
    }

    @Test
    fun `position and fraction convert both ways`() {
        assertEquals(0.5f, seekFraction(107.0, 214.0))
        assertEquals(0f, seekFraction(10.0, 0.0))
        assertEquals(1f, seekFraction(300.0, 214.0))
        assertEquals(107.0, seekTimeAt(0.5f, 214.0))
        assertEquals(214.0, seekTimeAt(2f, 214.0))
        assertEquals(0.0, seekTimeAt(0.5f, -1.0))
    }

    @Test
    fun `keys seek by five or thirty seconds and stay in bounds`() {
        assertEquals(88.0, applySeekKey(SliderKey.Up, big = false, position = 83.0, duration = 214.0))
        assertEquals(53.0, applySeekKey(SliderKey.Down, big = true, position = 83.0, duration = 214.0))
        assertEquals(0.0, applySeekKey(SliderKey.Down, big = false, position = 2.0, duration = 214.0))
        assertEquals(214.0, applySeekKey(SliderKey.Up, big = true, position = 200.0, duration = 214.0))
        assertEquals(0.0, applySeekKey(SliderKey.Home, big = false, position = 83.0, duration = 214.0))
        assertEquals(0.0, applySeekKey(SliderKey.Reset, big = false, position = 83.0, duration = 214.0))
        assertEquals(214.0, applySeekKey(SliderKey.End, big = false, position = 83.0, duration = 214.0))
    }
}
