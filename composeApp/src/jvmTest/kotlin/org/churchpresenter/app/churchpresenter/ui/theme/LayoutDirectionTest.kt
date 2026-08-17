package org.churchpresenter.app.churchpresenter.ui.theme

import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import org.churchpresenter.app.churchpresenter.data.Language

class LayoutDirectionTest {

    @Test
    fun `arabic and persian compose right to left`() {
        assertEquals(LayoutDirection.Rtl, layoutDirectionFor(Language.ARABIC))
        assertEquals(LayoutDirection.Rtl, layoutDirectionFor(Language.PERSIAN))
    }

    @Test
    fun `every other language composes left to right`() {
        val ltr = Language.entries.filter { it != Language.ARABIC && it != Language.PERSIAN }

        ltr.forEach {
            assertEquals(LayoutDirection.Ltr, layoutDirectionFor(it), "${it.name} should not mirror")
        }
    }
}
