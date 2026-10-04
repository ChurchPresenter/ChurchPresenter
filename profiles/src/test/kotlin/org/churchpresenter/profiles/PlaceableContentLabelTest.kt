package org.churchpresenter.profiles

import org.churchpresenter.settings.PlaceableContent
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceableContentLabelTest {

    @Test
    fun `every kind of placeable content is named apart`() {
        assertEquals(PlaceableContent.entries.size, PlaceableContent.entries.map { it.label() }.toSet().size)
    }
}
