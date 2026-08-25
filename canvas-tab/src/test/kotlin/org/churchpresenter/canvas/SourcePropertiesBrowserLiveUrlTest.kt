@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The address a live browser source has actually ended up at.
 *
 * The URL field holds what the operator typed; the page can be somewhere else entirely — a redirect,
 * a login wall, a link the page followed itself. The editor shows the real address underneath, but
 * only when it differs, because repeating the typed URL back verbatim under the field it came from
 * is noise that trains people to stop reading it.
 *
 * It needs a live entry in the cache to have an address at all, which used to mean a running
 * Chromium. The entry store is `internal`, so a test can seed one.
 */
class SourcePropertiesBrowserLiveUrlTest {

    private val seeded = mutableListOf<String>()

    @AfterTest
    fun tearDown() {
        seeded.forEach { SharedBrowserFrameCache.entries.remove(it) }
        seeded.clear()
    }

    private fun live(sourceId: String, at: String) {
        val entry = SharedBrowserFrameCache.CacheEntry(refCount = 1)
        entry.currentUrl.value = at
        SharedBrowserFrameCache.entries[sourceId] = entry
        seeded += sourceId
    }

    private fun browser(id: String, url: String) =
        SceneSource.BrowserSource(id = id, name = "Feed", url = url)

    @Test
    fun `a page that redirected shows where it actually went`() {
        live("web-redirect", at = "https://example.org/login")

        sourcePanel(browser("web-redirect", "https://example.org")) { _ ->
            onNodeWithText("https://example.org/login").assertExists()
        }
    }

    @Test
    fun `a page sitting exactly where it was told to go says nothing`() {
        live("web-same", at = "https://example.org")

        sourcePanel(browser("web-same", "https://example.org")) { _ ->
            assertEquals(
                1, countOf("https://example.org"),
                "the typed URL must not be echoed back beneath itself",
            )
        }
    }

    @Test
    fun `a browser that has not loaded anything yet says nothing`() {
        live("web-blank", at = "")

        sourcePanel(browser("web-blank", "https://example.org")) { _ ->
            assertEquals(1, countOf("https://example.org"))
        }
    }

    @Test
    fun `a source that is not live at all says nothing`() {
        // Nothing seeded: the cache has no entry for this id.
        sourcePanel(browser("web-not-live", "https://example.org")) { _ ->
            assertEquals(1, countOf("https://example.org"))
        }
    }

    @Test
    fun `editing the URL leaves the live address showing until the page follows`() {
        live("web-editing", at = "https://example.org/login")

        sourcePanel(browser("web-editing", "https://example.org")) { get ->
            fieldShowing("https://example.org").performTextReplacement("https://elsewhere.example")
            waitForIdle()

            onNodeWithText("https://example.org/login")
                .assertExists("where the page still is, until it navigates")
            assertEquals("https://elsewhere.example", (get() as SceneSource.BrowserSource).url)
        }
    }
}
