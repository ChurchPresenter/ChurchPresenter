package org.churchpresenter.hosanna.composables.model

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SliderModelTest {

    private val percent = NumberRange(0.0, 100.0)
    private val stars = NumberRange(0.0, 5.0)
    private val transpose = NumberRange(-6.0, 6.0)

    @Test
    fun `fraction and value convert both ways and snap to the step`() {
        assertEquals(0.6f, sliderFraction(60.0, percent))
        assertEquals(0f, sliderFraction(-5.0, percent))
        assertEquals(1f, sliderFraction(500.0, percent))
        assertEquals(0f, sliderFraction(3.0, NumberRange(3.0, 3.0)))
        assertEquals(60.0, sliderValueAt(0.6f, percent))
        assertEquals(3.0, sliderValueAt(0.62f, stars))
        assertEquals(5.0, sliderValueAt(1.5f, stars))
        assertEquals(0.3, sliderValueAt(0.31f, NumberRange(0.0, 1.0, 0.1, 1)))
    }

    @Test
    fun `the origin is zero when the range crosses it, else the middle`() {
        assertEquals(0.0, sliderOrigin(transpose))
        assertEquals(50.0, sliderOrigin(percent))
    }

    @Test
    fun `values near the origin snap to it`() {
        assertEquals(0.0, snapToOrigin(0.2, transpose))
        assertEquals(1.0, snapToOrigin(1.0, transpose))
    }

    @Test
    fun `dots show for ten steps or fewer`() {
        assertEquals(listOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f), sliderStepDots(stars))
        assertTrue(sliderStepDots(percent).isEmpty())
        assertTrue(sliderStepDots(NumberRange(1.0, 1.0)).isEmpty())
    }

    @Test
    fun `keys step, jump and reset`() {
        assertEquals(61.0, applySliderKey(SliderKey.Up, big = false, value = 60.0, range = percent))
        assertEquals(50.0, applySliderKey(SliderKey.Down, big = true, value = 60.0, range = percent))
        assertEquals(0.0, applySliderKey(SliderKey.Home, big = false, value = 60.0, range = percent))
        assertEquals(100.0, applySliderKey(SliderKey.End, big = false, value = 60.0, range = percent))
        assertEquals(0.0, applySliderKey(SliderKey.Reset, big = false, value = 4.0, range = transpose))
    }

    @Test
    fun `key mapping reads arrows and resets only when centered`() {
        assertEquals(SliderKey.Down, sliderKey(Key.DirectionLeft, centered = false))
        assertEquals(SliderKey.Down, sliderKey(Key.DirectionDown, centered = false))
        assertEquals(SliderKey.Up, sliderKey(Key.DirectionRight, centered = false))
        assertEquals(SliderKey.Up, sliderKey(Key.DirectionUp, centered = false))
        assertEquals(SliderKey.Home, sliderKey(Key.MoveHome, centered = false))
        assertEquals(SliderKey.End, sliderKey(Key.MoveEnd, centered = false))
        assertEquals(SliderKey.Reset, sliderKey(Key.Delete, centered = true))
        assertEquals(SliderKey.Reset, sliderKey(Key.Zero, centered = true))
        assertNull(sliderKey(Key.Delete, centered = false))
        assertNull(sliderKey(Key.A, centered = true))
    }

    @Test
    fun `signed text uses a true minus and a plus`() {
        assertEquals("−2", signedText(-2.0, 0))
        assertEquals("+3", signedText(3.0, 0))
        assertEquals("0", signedText(0.0, 0))
        assertEquals("+0.5", signedText(0.5, 1))
    }
}
