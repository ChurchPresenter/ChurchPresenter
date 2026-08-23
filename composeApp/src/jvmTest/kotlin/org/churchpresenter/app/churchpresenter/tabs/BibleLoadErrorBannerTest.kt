@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.bible.BibleLoadError
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText

class BibleLoadErrorBannerTest {

    private val unreadableTitle = "This translation could not be read"
    private val partialTitle = "This translation was only partly readable"
    private val partialHint = "Only the part that could be read is shown"
    private val reportHint = "The details are in the crash report folder"

    private fun error(path: String, reason: String, partial: Boolean) =
        BibleLoadError(resourcePath = path, reason = reason, partial = partial)

    @OptIn(ExperimentalTestApi::class)
    private fun banner(vararg errors: BibleLoadError, block: (List<String>) -> Unit) = runComposeUiTest {
        setContent { MaterialTheme { BibleLoadErrorBanner(errors.toList()) } }
        waitForIdle()
        block(renderedText())
    }

    @Test
    fun `a translation that could not be read at all is titled as unreadable`() {
        banner(error("/bibles/kjv.spb", "corrupt header", partial = false)) { shown ->
            assertTrue(shown.any { it.contains(unreadableTitle) }, shown.toString())
            assertFalse(shown.any { it.contains(partialTitle) }, shown.toString())
        }
    }

    @Test
    fun `a translation read only part way is titled as partly readable`() {
        banner(error("/bibles/kjv.spb", "truncated at book 12", partial = true)) { shown ->
            assertTrue(shown.any { it.contains(partialTitle) }, shown.toString())
            assertFalse(shown.any { it.contains(unreadableTitle) }, shown.toString())
        }
    }

    @Test
    fun `a partly readable translation explains what is missing`() {
        banner(error("/bibles/kjv.spb", "truncated", partial = true)) { shown ->
            assertTrue(shown.any { it.contains(partialHint) }, shown.toString())
        }
    }

    @Test
    fun `a wholly unreadable translation carries no partial hint`() {
        banner(error("/bibles/kjv.spb", "corrupt header", partial = false)) { shown ->
            assertFalse(shown.any { it.contains(partialHint) }, shown.toString())
        }
    }

    @Test
    fun `the banner names the file and the reason it failed`() {
        banner(error("/bibles/russian/rst.spb", "unexpected end of file", partial = false)) { shown ->
            assertTrue(shown.any { it.contains("rst.spb") }, shown.toString())
            assertTrue(shown.any { it.contains("unexpected end of file") }, shown.toString())
            assertFalse(shown.any { it.contains("/bibles/russian/") }, shown.toString())
        }
    }

    @Test
    fun `several failed translations are each named`() {
        banner(
            error("/bibles/kjv.spb", "corrupt header", partial = false),
            error("/bibles/rst.spb", "truncated", partial = false),
        ) { shown ->
            assertTrue(shown.any { it.contains("kjv.spb") }, shown.toString())
            assertTrue(shown.any { it.contains("rst.spb") }, shown.toString())
        }
    }

    @Test
    fun `one partly readable translation among several sets the title for the banner`() {
        banner(
            error("/bibles/kjv.spb", "corrupt header", partial = false),
            error("/bibles/rst.spb", "truncated", partial = true),
        ) { shown ->
            assertTrue(shown.any { it.contains(partialTitle) }, shown.toString())
            assertTrue(shown.any { it.contains(partialHint) }, shown.toString())
        }
    }

    @Test
    fun `the banner always points at the crash report folder`() {
        banner(error("/bibles/kjv.spb", "corrupt header", partial = false)) { shown ->
            assertTrue(shown.any { it.contains(reportHint) }, shown.toString())
        }
    }

    @Test
    fun `a windows path is reduced to the file name`() {
        banner(error("""C:\Users\op\Bibles\kjv.spb""", "corrupt header", partial = false)) { shown ->
            assertTrue(shown.any { it.contains("kjv.spb") }, shown.toString())
            assertFalse(shown.any { it.contains("""C:\Users""") }, shown.toString())
        }
    }
}
