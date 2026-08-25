@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What each editor does when the operator switches theme with the panel open.
 *
 * Every per-kind editor reads `MaterialTheme.colorScheme` straight in its own body, so a theme
 * change invalidates each of them individually and re-runs the whole body against a source that has
 * not moved. That is a different path from the panel being handed a new modifier — there the editor
 * is skipped wholesale — and it is the only one that re-evaluates every `if` in an editor without an
 * edit having happened.
 *
 * The rule is the same either way: a redraw the operator did not ask for may not change the source,
 * fold a section away, or lose what a control is showing.
 */
class SourcePropertiesThemeSwitchTest {

    @Test
    fun `switching theme edits no source of any kind`() {
        Fixture.everyKind("theme").forEach { source ->
            withOsName(OS_WITHOUT_ENUMERATOR) {
                rethemedPanel(source) { get, retheme ->
                    retheme()

                    assertEquals(
                        source, get(),
                        "a theme change must not edit a ${source::class.simpleName}",
                    )
                }
            }
        }
    }

    @Test
    fun `switching theme leaves every label in place`() {
        Fixture.everyKind("theme-text").forEach { source ->
            withOsName(OS_WITHOUT_ENUMERATOR) {
                rethemedPanel(source) { _, retheme ->
                    val before = renderedText()

                    retheme()

                    assertEquals(
                        before, renderedText(),
                        "a theme change must not alter what a ${source::class.simpleName} shows",
                    )
                }
            }
        }
    }

    @Test
    fun `switching theme twice returns to exactly the first render`() {
        // Light -> dark -> light. Anything that differs is state the editors rebuilt rather than kept.
        rethemedPanel(Fixture.shape()) { get, retheme ->
            val first = renderedText()

            retheme()
            retheme()

            assertEquals(first, renderedText())
            assertEquals(Fixture.shape(), get())
        }
    }

    // ── Sections that are open stay open ──────────────────────────────────────

    @Test
    fun `a shape's stroke controls survive a theme change`() {
        rethemedPanel(Fixture.shape()) { _, retheme ->
            val strokeWidths = countOf("Stroke Width")

            retheme()

            assertEquals(true, strokeWidths > 0, "the stroke section must be open to begin with")
            assertEquals(strokeWidths, countOf("Stroke Width"), "and must stay open")
        }
    }

    @Test
    fun `a shape's gradient controls stay unfolded across a theme change`() {
        rethemedPanel(Fixture.shape()) { _, retheme ->
            toggleCheckbox(1) // Gradient on
            onNodeWithText("COLOR 2").assertExists("the gradient controls must be showing to begin with")

            retheme()

            onNodeWithText("COLOR 2").assertExists("and must not fold away when the theme changes")
            checkboxes()[1].assertIsOn()
        }
    }

    @Test
    fun `a colour source's gradient controls stay unfolded across a theme change`() {
        rethemedPanel(Fixture.color()) { _, retheme ->
            toggleCheckbox(0) // Gradient on
            onNodeWithText("COLOR 2").assertExists()

            retheme()

            onNodeWithText("COLOR 2").assertExists("the gradient controls must not fold away")
            checkboxes()[0].assertIsOn()
        }
    }

    @Test
    fun `a QR code's wifi fields stay showing across a theme change`() {
        val wifi = (Fixture.qr() as SceneSource.QRCodeSource).copy(contentType = "wifi", wifiSsid = "Sanctuary")
        rethemedPanel(wifi) { get, retheme ->
            onNodeWithText("Sanctuary").assertExists("the wifi fields must be showing to begin with")

            retheme()

            onNodeWithText("Sanctuary").assertExists("and must survive the theme change")
            assertEquals(wifi, get())
        }
    }

    @Test
    fun `a browser source keeps its transparency box across a theme change`() {
        val transparent = (Fixture.browser() as SceneSource.BrowserSource).copy(forceTransparent = true)
        rethemedPanel(transparent) { get, retheme ->
            retheme()

            checkboxes()[0].assertIsOn()
            assertEquals(transparent, get())
        }
    }

    @Test
    fun `a video source keeps its loop box across a theme change`() {
        val looping = (Fixture.video() as SceneSource.VideoSource).copy(loop = true)
        rethemedPanel(looping) { get, retheme ->
            retheme()

            checkboxes()[0].assertIsOn()
            assertEquals(looping, get())
        }
    }

    // ── Uncommitted text is not a source edit, and must not be lost ───────────

    @Test
    fun `a half-typed value survives a theme change uncommitted`() {
        // The interval input holds its text locally until the field is confirmed, so re-running the
        // editor's body against an unchanged source is exactly what would throw that text away.
        val intervalField = 10
        withOsName(OS_WITHOUT_ENUMERATOR) {
            rethemedPanel(Fixture.capture()) { get, retheme ->
                typeField(intervalField, "500")

                retheme()

                assertFieldShows("500", "the interval input after a theme change")
                assertEquals(
                    Fixture.capture(), get(),
                    "and typing into it must still not have edited the source",
                )
            }
        }
    }
}
