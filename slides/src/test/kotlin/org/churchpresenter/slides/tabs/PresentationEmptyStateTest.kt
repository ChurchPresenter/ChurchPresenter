@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.slides.tabs

import org.churchpresenter.sharedui.testing.renderedText
import org.churchpresenter.sharedui.testing.showsExactly
import kotlin.test.Test
import kotlin.test.assertTrue

class PresentationEmptyStateTest {

    @Test
    fun `with nothing open the tab says what it opens and how`() = presentationTab { _, _ ->
        assertTrue(showsExactly("No presentation open"), renderedText().toString())
        assertTrue(showsExactly("Open presentation"))
        assertTrue(showsExactly("or drag a file here"))
        listOf(".pptx", ".ppt", ".key", ".pdf").forEach { format ->
            assertTrue(showsExactly(format), "the $format chip")
        }
    }
}
