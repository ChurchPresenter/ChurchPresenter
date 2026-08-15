package org.churchpresenter.app.churchpresenter.dialogs

import kotlin.test.Test
import kotlin.test.assertEquals

class ContactInitialTypeTest {

    private val types = listOf(
        "Feature Request" to "featureRequest",
        "Feedback" to "feedback",
        "Testimonial" to CONTACT_TYPE_TESTIMONIAL,
        "Bug Report" to "bugReport",
    )

    @Test
    fun `the Help menu opens on the first type`() {
        assertEquals(types.first(), initialContactType(types, null))
    }

    @Test
    fun `the story prompt opens on Testimonial`() {
        assertEquals("Testimonial", initialContactType(types, CONTACT_TYPE_TESTIMONIAL).first)
    }

    @Test
    fun `an unrecognized key falls back to the first type`() {
        assertEquals(types.first(), initialContactType(types, "somethingElse"))
    }

    @Test
    fun `a blank key falls back to the first type`() {
        assertEquals(types.first(), initialContactType(types, ""))
    }

    @Test
    fun `every key the dialog offers selects its own type`() {
        types.forEach { type ->
            assertEquals(type, initialContactType(types, type.second))
        }
    }
}
