package org.churchpresenter.calendar.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** [safeFileName]: the run of show's name as a file Windows, macOS and Linux will all save (#651). */
class SafeFileNameTest {

    @Test
    fun `an ordinary name is left alone`() {
        assertEquals("Sunday School - 2026-09-27", safeFileName("Sunday School - 2026-09-27"))
        assertEquals("Служение - 2026-09-27", safeFileName("Служение - 2026-09-27"))
    }

    @Test
    fun `every character Windows refuses becomes a dash`() {
        assertEquals("a-b-c-d-e-f-g-h-i-j", safeFileName("a\\b/c:d*e?f\"g<h>i|j"))
        assertEquals("tab-here", safeFileName("tab\there"))
    }

    @Test
    fun `trailing dots and spaces go, and nothing at all becomes a name`() {
        assertEquals("Service", safeFileName("Service. . "))
        assertEquals("run-of-show", safeFileName(" . "))
    }
}
