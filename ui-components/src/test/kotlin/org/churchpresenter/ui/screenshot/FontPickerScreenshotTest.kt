@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.ui.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import org.churchpresenter.ui.FONT_PANEL_WIDTH
import org.churchpresenter.ui.FontPickerPanel
import org.churchpresenter.ui.RecentFonts
import org.churchpresenter.ui.FontCatalogSnapshot
import org.churchpresenter.ui.FontCategory
import org.churchpresenter.ui.FontFace
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * The font panel, state by state.
 *
 * Shot against a catalog written here rather than the machine's own: what is installed differs from
 * box to box, and a picture of "the fonts this laptop happens to have" is one no reviewer can check.
 * The families below are named after what they demonstrate, not after anything real.
 */
class FontPickerScreenshotTest {

    private val section = "fontPicker"

    private val faces = listOf(
        FontFace("Arial", FontCategory.SANS, cyrillic = true, hebrew = true, recommended = true),
        FontFace("Georgia", FontCategory.SERIF, cyrillic = true, hebrew = false, recommended = true),
        FontFace("Verdana", FontCategory.SANS, cyrillic = true, hebrew = false, recommended = true),
        FontFace("Academy Engraved", FontCategory.DISPLAY, cyrillic = false, hebrew = false, recommended = false),
        FontFace("Courier New", FontCategory.MONO, cyrillic = true, hebrew = true, recommended = false),
        FontFace("Papyrus", FontCategory.DISPLAY, cyrillic = false, hebrew = false, recommended = false),
        FontFace("Zapfino", FontCategory.DISPLAY, cyrillic = false, hebrew = false, recommended = false),
    )

    private val verses = listOf("In the beginning God created the heaven and the earth.", "В начале сотворил Бог")

    @AfterTest
    fun forgetRecents() = RecentFonts.clear()

    private fun panel(
        name: String,
        value: String = "Georgia",
        catalog: FontCatalogSnapshot = FontCatalogSnapshot(faces, hiddenCount = 14, measured = true),
        previewLines: List<String> = verses,
        drive: ComposeUiTest.() -> Unit = {},
    ) = captureComponent(section, name, drive = drive) {
        Box(Modifier.width(FONT_PANEL_WIDTH)) {
            FontPickerPanel(
                value = value,
                catalog = catalog,
                previewLines = previewLines,
                onDismiss = {},
                onPick = {},
            )
        }
    }

    /** Types into the search box, which is the only control in the panel that carries a name. */
    private fun ComposeUiTest.search(text: String) {
        onNodeWithContentDescription("Search fonts…").performTextInput(text)
        waitForIdle()
    }

    @Test
    fun `the panel as it opens`() = panel("panel")

    /** A family picked earlier this session leads the list under its own heading. */
    @Test
    fun `with something used this session`() {
        RecentFonts.record("Papyrus")
        RecentFonts.record("Courier New")
        panel("panel_recent")
    }

    /** Typing collapses the three headings into one run of matches. */
    @Test
    fun `searching`() = panel("panel_search", drive = { search("ar") })

    @Test
    fun `a search that matches nothing`() = panel("panel_no_results", drive = { search("kiwi") })

    /**
     * The warning that is the point of the preview: the verse is Cyrillic, the family is not, so the
     * line is drawn in the fallback the projector would really use and the panel says why.
     */
    @Test
    fun `a family that cannot draw the verse`() = panel("panel_missing_script", value = "Papyrus")

    /** English-only translations ask nothing of the family, so no warning is raised. */
    @Test
    fun `a preview the family can draw`() =
        panel("panel_latin_only", value = "Papyrus", previewLines = listOf(verses.first()))

    /** Before the glyph scan lands: same list, no claims about coverage either way. */
    @Test
    fun `while the fonts are still being measured`() = panel(
        "panel_unmeasured",
        value = "Papyrus",
        catalog = FontCatalogSnapshot(faces, hiddenCount = 0, measured = false),
    )

    /** A machine's real set runs to hundreds: the panel stops at its height and scrolls. */
    @Test
    fun `a list longer than the panel`() = panel(
        "panel_scrolling",
        value = "Family 3",
        catalog = FontCatalogSnapshot(
            List(30) { FontFace("Family ${it + 1}", FontCategory.SANS, false, false, it < 2) },
            hiddenCount = 9,
            measured = true,
        ),
        previewLines = listOf(verses.first()),
    )

    /** Nothing installed at all — a state the footer still accounts for. */
    @Test
    fun `an empty catalog`() = panel(
        "panel_empty",
        catalog = FontCatalogSnapshot(emptyList(), hiddenCount = 0, measured = true),
    )
}
