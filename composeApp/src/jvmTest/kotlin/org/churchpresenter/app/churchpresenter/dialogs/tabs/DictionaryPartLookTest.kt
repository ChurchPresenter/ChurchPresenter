package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.core.models.text.TextOutline
import org.churchpresenter.settings.DictionarySettings
import kotlin.test.Test
import kotlin.test.assertEquals

class DictionaryPartLookTest {

    private val defaults = DictionarySettings()

    private val restyled = DictionarySettings(
        wordColor = "#111111", wordFontSize = 71, wordFontType = "Georgia", wordBold = !defaults.wordBold,
        wordItalic = !defaults.wordItalic, wordShadow = !defaults.wordShadow, wordShadowColor = "#222222",
        wordShadowSize = 7, wordShadowOpacity = 33, wordBackdrop = TextBackdrop(lineBackground = true),
        wordOutline = TextOutline(enabled = true),
        referenceColor = "#333333", referenceFontSize = 31, referenceFontType = "Verdana",
        referenceShadow = !defaults.referenceShadow, referenceShadowColor = "#444444", referenceShadowSize = 4,
        referenceShadowOpacity = 44, referenceBackdrop = TextBackdrop(lineBackground = true),
        referenceOutline = TextOutline(enabled = true),
        definitionColor = "#555555", definitionFontSize = 27,
        definitionBackdrop = TextBackdrop(lineBackground = true), definitionOutline = TextOutline(enabled = true),
        kjvUsageColor = "#666666", kjvUsageFontSize = 19,
    )

    @Test
    fun `each part's look carries over to other settings whole`() {
        DictionaryPart.entries.forEach { part ->
            val moved = part.withLook(defaults, part.look(restyled))
            assertEquals(part.look(restyled), part.look(moved), part.name)
        }
    }

    @Test
    fun `a look with no face or style keeps what the settings already had`() {
        DictionaryPart.entries.forEach { part ->
            val bare = DictionaryLook(part.names, color = "#ABCDEF", fontSize = 12)

            val written = part.withLook(restyled, bare)

            val look = part.look(written)
            assertEquals("#ABCDEF", look.color, part.name)
            assertEquals(12, look.fontSize, part.name)
            assertEquals(part.look(restyled).fontType, look.fontType, part.name)
            assertEquals(part.look(restyled).bold, look.bold, part.name)
            assertEquals(part.look(restyled).italic, look.italic, part.name)
            assertEquals(part.look(restyled).shadow, look.shadow, part.name)
            assertEquals(part.look(restyled).backdrop, look.backdrop, part.name)
            assertEquals(part.look(restyled).outline, look.outline, part.name)
        }
    }
}
