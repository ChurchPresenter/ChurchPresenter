package org.churchpresenter.settings.utils

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ClockFormatTest {

    private val originalLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `24-hour detection follows the default locale`() {
        // Drives the clock/countdown display format, so it must track the OS locale, not a guess.
        Locale.setDefault(Locale.US)
        assertEquals(false, isSystemUsing24HourFormat(), "en-US uses AM/PM")

        Locale.setDefault(Locale.GERMANY)
        assertEquals(true, isSystemUsing24HourFormat(), "de-DE uses a 24-hour clock")

        Locale.setDefault(Locale.FRANCE)
        assertEquals(true, isSystemUsing24HourFormat(), "fr-FR uses a 24-hour clock")
    }
}
