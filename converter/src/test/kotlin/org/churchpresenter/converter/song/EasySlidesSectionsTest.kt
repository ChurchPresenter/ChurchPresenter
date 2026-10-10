package org.churchpresenter.converter.song

import kotlin.test.Test
import kotlin.test.assertEquals

class EasySlidesSectionsTest {

    @Test
    fun `leading and repeated blank lines do not open empty sections`() {
        val sections = EasySlidesConverter.sectionsOf("\n\nfirst\n\n\n\nsecond\n")
        assertEquals(listOf("Verse 1" to listOf("first"), "Verse 2" to listOf("second")), sections.map { it.label to it.lines })
    }

    @Test
    fun `an empty marker keeps adding to the section before it`() {
        val sections = EasySlidesConverter.sectionsOf("[1]\nfirst\n[]\nstill first\n\n[c]\nchorus")
        assertEquals(listOf(listOf("first", "still first"), listOf("chorus")), sections.map { it.lines })
    }
}
