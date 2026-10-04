package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.core.models.text.TextOutline
import kotlin.test.Test
import kotlin.test.assertNotEquals

class TextLookPerturbTest {

    private val plain = TextLook(
        fontType = "Arial", fontSize = 40, autoFit = null, color = "#FFFFFF", chordColor = null,
        bold = false, italic = false, underline = false, strikethrough = false,
        alignment = "center", transform = "none", letterSpacing = 0, wordSpacing = 0,
        outline = TextOutline(), backdrop = TextBackdrop(), shadow = false,
        shadowColor = "#000000", shadowSize = 0, shadowOpacity = 0,
    )

    private val styled = plain.copy(
        autoFit = true, chordColor = "#FFCC00", bold = true, italic = true, underline = true,
        strikethrough = true, outline = TextOutline(enabled = true),
        backdrop = TextBackdrop(lineBackground = true), shadow = true,
    )

    @Test
    fun `every field of a plain look can be changed on its own`() {
        TextLookField.entries.filterNot { it == TextLookField.AUTO_FIT || it == TextLookField.CHORD_COLOR }
            .forEach { field -> assertNotEquals(plain, plain.perturbed(field), field.name) }
    }

    @Test
    fun `every field of a fully styled look can be changed on its own`() {
        TextLookField.entries.forEach { field -> assertNotEquals(styled, styled.perturbed(field), field.name) }
    }
}
