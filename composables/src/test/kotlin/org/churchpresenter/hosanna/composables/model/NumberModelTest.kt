package org.churchpresenter.hosanna.composables.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NumberModelTest {

    private val count = NumberRange(0.0, 99.0)
    private val seconds = NumberRange(0.0, 30.0, step = 0.5, decimals = 1)
    private val rotation = NumberRange(-180.0, 180.0)

    @Test
    fun `numbers format to their decimals`() {
        assertEquals("5", formatNumber(5.0, 0))
        assertEquals("1920", formatNumber(1920.0, 0))
        assertEquals("2.5", formatNumber(2.5, 1))
        assertEquals("3.0", formatNumber(3.0, 1))
        assertEquals("-45", formatNumber(-45.0, 0))
        assertEquals("3", formatNumber(2.6, 0))
    }

    @Test
    fun `parsing and the range`() {
        assertEquals(12.5, parseNumber(" 12.5 "))
        assertNull(parseNumber(""))
        assertNull(parseNumber("-"))
        assertTrue(inRange(0.0, count))
        assertTrue(inRange(99.0, count))
        assertFalse(inRange(100.0, count))
        assertEquals(99.0, clampNumber(500.0, count))
        assertEquals(-180.0, clampNumber(-500.0, rotation))
    }

    @Test
    fun `steps stop at the limits, shift steps ten at once`() {
        assertEquals(1.0, stepNumber(0.0, count, up = true))
        assertEquals(0.0, stepNumber(0.0, count, up = false))
        assertEquals(15.0, stepNumber(5.0, count, up = true, big = true))
        assertEquals(99.0, stepNumber(95.0, count, up = true, big = true))
        assertEquals(3.0, stepNumber(2.5, seconds, up = true))
        assertEquals(2.0, stepNumber(2.5, seconds, up = false))
        assertFalse(canStepNumber(0.0, count, up = false))
        assertTrue(canStepNumber(0.0, count, up = true))
        assertFalse(canStepNumber(99.0, count, up = true))
    }

    @Test
    fun `only digits, an allowed minus and one decimal point can be typed`() {
        assertEquals("120", filterNumberInput("12a0", count))
        assertEquals("12", filterNumberInput("-12", count))
        assertEquals("-12", filterNumberInput("-12", rotation))
        assertEquals("12", filterNumberInput("1-2", rotation))
        assertEquals("125", filterNumberInput("12.5", count))
        assertEquals("12.55", filterNumberInput("12.5.5", seconds))
        assertEquals("", filterNumberInput("abc", count))
    }
}
