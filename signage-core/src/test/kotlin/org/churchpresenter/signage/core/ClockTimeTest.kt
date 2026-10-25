package org.churchpresenter.signage.core

import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClockTimeTest {
    @Test
    fun parsesAndPrintsHoursAndMinutes() {
        val time = ClockTime.parse("07:05")
        assertEquals(7, time.hour)
        assertEquals(5, time.minute)
        assertEquals("07:05", time.toString())
        assertEquals(ClockTime.of(23, 59), ClockTime.parse("23:59"))
    }

    @Test
    fun refusesWhatIsNotATimeOfDay() {
        listOf("24:00", "12:60", "7", "aa:bb", "12:30:00", "-1:00").forEach { text ->
            assertFailsWith<IllegalArgumentException>(text) { ClockTime.parse(text) }
        }
        assertFailsWith<IllegalArgumentException> { ClockTime(ClockTime.MINUTES_PER_DAY) }
    }

    @Test
    fun travelsAsAString() {
        val encoded = SignageJson.encodeToString(ClockTime.serializer(), ClockTime.of(9, 30))
        assertEquals("\"09:30\"", encoded)
        assertEquals(ClockTime.of(9, 30), SignageJson.decodeFromString(ClockTime.serializer(), encoded))
        assertEquals("\"09:30\"", SignageJson.encodeToString(String.serializer(), "09:30"))
    }
}
