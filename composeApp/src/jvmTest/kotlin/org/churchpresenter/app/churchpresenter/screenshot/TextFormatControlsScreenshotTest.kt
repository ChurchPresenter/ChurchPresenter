@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import org.churchpresenter.app.churchpresenter.composables.HorizontalAlignmentButtons
import org.churchpresenter.app.churchpresenter.composables.TextStyleButtons
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test

/**
 * The B / I / U / S toggles and the left-centre-right group they sit beside, shared by the
 * Announcements formatting bar, the canvas text source panel and the song/Bible settings tabs.
 *
 * The four style toggles are independent rather than exclusive, so each is shot on its own as well
 * as all four together — a single "everything on" image would not show which button is which.
 */
class TextFormatControlsScreenshotTest {

    private fun styles(
        name: String,
        bold: Boolean = false,
        italic: Boolean = false,
        underline: Boolean = false,
        shadow: Boolean = false,
    ) = captureComponent(SECTION, name) {
        TextStyleButtons(
            bold = bold,
            italic = italic,
            underline = underline,
            shadow = shadow,
            onBoldChange = {},
            onItalicChange = {},
            onUnderlineChange = {},
            onShadowChange = {},
        )
    }

    @Test
    fun `no style applied`() = styles("style_none")

    @Test
    fun `bold on`() = styles("style_bold", bold = true)

    @Test
    fun `italic on`() = styles("style_italic", italic = true)

    @Test
    fun `underline on`() = styles("style_underline", underline = true)

    @Test
    fun `shadow on`() = styles("style_shadow", shadow = true)

    @Test
    fun `every style on`() =
        styles("style_all", bold = true, italic = true, underline = true, shadow = true)

    private fun alignment(name: String, selected: String) = captureComponent(SECTION, name) {
        HorizontalAlignmentButtons(
            selectedAlignment = selected,
            onAlignmentChange = {},
            leftValue = Constants.LEFT,
            centerValue = Constants.CENTER,
            rightValue = Constants.RIGHT,
        )
    }

    @Test
    fun `aligned left`() = alignment("align_left", Constants.LEFT)

    @Test
    fun `aligned centre`() = alignment("align_center", Constants.CENTER)

    @Test
    fun `aligned right`() = alignment("align_right", Constants.RIGHT)

    private companion object {
        const val SECTION = "textFormatControls"
    }
}
