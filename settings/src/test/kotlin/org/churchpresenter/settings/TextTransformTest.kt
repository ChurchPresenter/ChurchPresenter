package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextTransformTest {

    @Test
    fun `the picker offers each re-casing once, starting from none`() {
        // The stored value is one of these strings, so an entry that is not a TEXT_TRANSFORM_
        // constant is a setting the presenter cannot apply, and "none" has to be first because it
        // is what every profile starts on.
        val options = textTransformOptions()
        assertEquals(Constants.TEXT_TRANSFORM_NONE, options.first())
        assertEquals(options.size, options.toSet().size)
        assertTrue(
            options.containsAll(
                listOf(
                    Constants.TEXT_TRANSFORM_UPPERCASE,
                    Constants.TEXT_TRANSFORM_LOWERCASE,
                    Constants.TEXT_TRANSFORM_CAPITALIZE,
                ),
            ),
        )
    }
}
