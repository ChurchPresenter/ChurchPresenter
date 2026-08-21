@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Browser source: a URL, the off-screen render size and frame rate, a transparency flag and a
 * custom CSS box.
 *
 * Three of its fields are numeric and each clamps to a different range — the render size to what the
 * off-screen browser will actually allocate, the frame rate to 1–60. A clamp that reads the wrong
 * bound produces a source that renders at the wrong size rather than an error, so every bound is
 * driven from both directions, and the "not a number" path — which must leave the model untouched
 * rather than store a zero — is a test of its own for each field.
 *
 * The current-URL read-out beneath the URL field only appears once `SharedBrowserFrameCache` has a
 * live browser for this source id. Nothing in a unit test can start one, so the panel's "no live
 * browser" path is what these cover; the read-out itself is asserted absent.
 */
class SourcePropertiesBrowserTest {

    /** Ordinals of the browser panel's fields — the header owns the first six. */
    private object Field {
        const val URL = 6
        const val RENDER_WIDTH = 7
        const val RENDER_HEIGHT = 8
        const val FPS = 9
        const val CUSTOM_CSS = 10
    }

    // ── What the panel displays ───────────────────────────────────────────────

    @Test
    fun `the section is headed and every control captioned`() = sourcePanel(Fixture.browser()) { _ ->
        onNodeWithText(Label.BROWSER).assertIsDisplayed()
        onNodeWithText("URL").assertIsDisplayed()
        onNodeWithText("WIDTH").assertIsDisplayed()
        onNodeWithText("HEIGHT").assertIsDisplayed()
        onNodeWithText("FPS").assertIsDisplayed()
        onNodeWithText("Transparent background").assertExists()
        onNodeWithText("CUSTOM CSS").assertExists()
    }

    @Test
    fun `the browser panel adds five fields and one checkbox to the header`() =
        sourcePanel(Fixture.browser()) { _ ->
            textFields().assertCountEquals(11)
            checkboxes().assertCountEquals(1)
        }

    @Test
    fun `every stored value is shown in its own field`() {
        val configured = Fixture.browser().copy(
            renderWidth = 1280, renderHeight = 720, fps = 24, customCss = "body { margin: 0 }"
        )
        sourcePanel(configured) { _ ->
            assertFieldShows("https://example.org", "the URL field")
            assertFieldShows("1280", "the render width field")
            assertFieldShows("720", "the render height field")
            assertFieldShows("24", "the FPS field")
            assertFieldShows("body { margin: 0 }", "the custom CSS field")
        }
    }

    @Test
    fun `no live-URL read-out is shown when no browser is running for this source`() =
        sourcePanel(Fixture.browser()) { _ ->
            // The stored URL appears exactly once — in its own field, not also as a read-out below it.
            assertEquals(1, countOf("https://example.org"))
        }

    // ── URL ───────────────────────────────────────────────────────────────────

    @Test
    fun `typing a URL stores it and nothing else`() = sourcePanel(Fixture.browser()) { get ->
        typeField(Field.URL, "https://church.example/live")

        assertEquals(
            Fixture.browser().copy(url = "https://church.example/live"), get(),
            "the URL field may write only the URL",
        )
        assertFieldShows("https://church.example/live", "the URL field after typing")
    }

    @Test
    fun `the URL can be cleared`() = sourcePanel(Fixture.browser()) { get ->
        typeField(Field.URL, "")

        assertEquals("", (get() as SceneSource.BrowserSource).url)
    }

    // ── Render width ──────────────────────────────────────────────────────────

    @Test
    fun `typing a render width stores it`() = sourcePanel(Fixture.browser()) { get ->
        typeField(Field.RENDER_WIDTH, "1600")

        assertEquals(1600, (get() as SceneSource.BrowserSource).renderWidth)
        assertFieldShows("1600", "the render width field")
    }

    @Test
    fun `a render width below the minimum is raised to it`() = sourcePanel(Fixture.browser()) { get ->
        typeField(Field.RENDER_WIDTH, "100")

        assertEquals(320, (get() as SceneSource.BrowserSource).renderWidth, "the width floor is 320")
    }

    @Test
    fun `a render width above the maximum is lowered to it`() = sourcePanel(Fixture.browser()) { get ->
        typeField(Field.RENDER_WIDTH, "99999")

        assertEquals(3840, (get() as SceneSource.BrowserSource).renderWidth, "the width ceiling is 3840")
    }

    @Test
    fun `text that is not a number leaves the render width alone`() = sourcePanel(Fixture.browser()) { get ->
        typeField(Field.RENDER_WIDTH, "wide")

        assertEquals(1920, (get() as SceneSource.BrowserSource).renderWidth, "the stored width is untouched")
    }

    // ── Render height ─────────────────────────────────────────────────────────

    @Test
    fun `typing a render height stores it`() = sourcePanel(Fixture.browser()) { get ->
        typeField(Field.RENDER_HEIGHT, "900")

        assertEquals(900, (get() as SceneSource.BrowserSource).renderHeight)
        assertFieldShows("900", "the render height field")
    }

    @Test
    fun `a render height below the minimum is raised to it`() = sourcePanel(Fixture.browser()) { get ->
        typeField(Field.RENDER_HEIGHT, "10")

        assertEquals(240, (get() as SceneSource.BrowserSource).renderHeight, "the height floor is 240")
    }

    @Test
    fun `a render height above the maximum is lowered to it`() = sourcePanel(Fixture.browser()) { get ->
        typeField(Field.RENDER_HEIGHT, "5000")

        assertEquals(2160, (get() as SceneSource.BrowserSource).renderHeight, "the height ceiling is 2160")
    }

    @Test
    fun `text that is not a number leaves the render height alone`() = sourcePanel(Fixture.browser()) { get ->
        typeField(Field.RENDER_HEIGHT, "tall")

        assertEquals(1080, (get() as SceneSource.BrowserSource).renderHeight)
    }

    // ── Frame rate ────────────────────────────────────────────────────────────

    @Test
    fun `typing a frame rate stores it`() = sourcePanel(Fixture.browser()) { get ->
        typeField(Field.FPS, "25")

        assertEquals(25, (get() as SceneSource.BrowserSource).fps)
        assertFieldShows("25", "the FPS field")
    }

    @Test
    fun `a frame rate of zero is raised to one`() = sourcePanel(Fixture.browser()) { get ->
        typeField(Field.FPS, "0")

        assertEquals(1, (get() as SceneSource.BrowserSource).fps, "a source must render at least one frame")
    }

    @Test
    fun `a frame rate above the maximum is lowered to it`() = sourcePanel(Fixture.browser()) { get ->
        typeField(Field.FPS, "240")

        assertEquals(60, (get() as SceneSource.BrowserSource).fps, "the frame rate ceiling is 60")
    }

    @Test
    fun `text that is not a number leaves the frame rate alone`() = sourcePanel(Fixture.browser()) { get ->
        typeField(Field.FPS, "fast")

        assertEquals(30, (get() as SceneSource.BrowserSource).fps)
    }

    // ── Transparency ──────────────────────────────────────────────────────────

    @Test
    fun `transparency is off out of the box`() = sourcePanel(Fixture.browser()) { _ ->
        checkboxes()[0].assertIsOff()
    }

    @Test
    fun `ticking Transparent background stores the flag and shows it ticked`() =
        sourcePanel(Fixture.browser()) { get ->
            toggleCheckbox(0)

            assertEquals(
                Fixture.browser().copy(forceTransparent = true), get(),
                "ticking the box may change only that flag",
            )
            checkboxes()[0].assertIsOn()
        }

    @Test
    fun `unticking Transparent background turns it back off`() {
        sourcePanel(Fixture.browser().copy(forceTransparent = true)) { get ->
            checkboxes()[0].assertIsOn()
            toggleCheckbox(0)

            assertEquals(false, (get() as SceneSource.BrowserSource).forceTransparent)
            checkboxes()[0].assertIsOff()
        }
    }

    // ── Custom CSS ────────────────────────────────────────────────────────────

    @Test
    fun `typing custom CSS stores it and nothing else`() = sourcePanel(Fixture.browser()) { get ->
        typeField(Field.CUSTOM_CSS, ".ticker { display: none }")

        assertEquals(
            Fixture.browser().copy(customCss = ".ticker { display: none }"), get(),
            "the CSS box may write only the CSS",
        )
        assertFieldShows(".ticker { display: none }", "the custom CSS field after typing")
    }

    @Test
    fun `custom CSS can be cleared`() {
        sourcePanel(Fixture.browser().copy(customCss = "body { background: red }")) { get ->
            typeField(Field.CUSTOM_CSS, "")

            assertEquals("", (get() as SceneSource.BrowserSource).customCss)
        }
    }
}
