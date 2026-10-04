package org.churchpresenter.profiles

import org.churchpresenter.settings.OutputProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileListDropTest {

    private val profiles = listOf("a", "b", "c").map { OutputProfile(id = it, name = it) }
    private val tops = mapOf("a" to 0f, "b" to 40f, "c" to 80f)
    private val heights = mapOf("a" to 40f, "b" to 40f, "c" to 40f)

    @Test
    fun `a row dragged past the next one lands below it`() {
        assertEquals(2, dropIndexFor(profiles, "a", offset = 45f, tops, heights))
    }

    @Test
    fun `a row dragged above the first lands at the top`() {
        assertEquals(0, dropIndexFor(profiles, "c", offset = -85f, tops, heights))
    }

    @Test
    fun `a row nudged within its own slot has nowhere new to land`() {
        assertNull(dropIndexFor(profiles, "b", offset = 5f, tops, heights))
        assertNull(dropIndexFor(profiles, "b", offset = -15f, tops, heights))
    }

    @Test
    fun `a row not yet measured cannot be dropped`() {
        assertNull(dropIndexFor(profiles, "b", offset = 100f, mapOf("a" to 0f), heights))
    }

    @Test
    fun `rows not yet measured count as sitting at the top`() {
        assertEquals(2, dropIndexFor(profiles, "a", offset = 0f, mapOf("a" to 100f), emptyMap()))
    }

    @Test
    fun `the landing line sits on the row below the gap, or under the last row`() {
        assertEquals(40f, dropLineTop(profiles, 1, tops, heights))
        assertEquals(120f, dropLineTop(profiles, 3, tops, heights))
    }

    @Test
    fun `an unmeasured row puts the landing line at zero`() {
        assertEquals(0f, dropLineTop(profiles, 1, emptyMap(), emptyMap()))
        assertEquals(0f, dropLineTop(profiles, 3, emptyMap(), emptyMap()))
    }
}
