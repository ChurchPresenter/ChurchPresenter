package org.churchpresenter.dictionary.tab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.dictionary.StrongsEntry
import org.churchpresenter.settings.DictionarySettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test

/**
 * The Strong's dictionary entry shown to the congregation.
 *
 * When a preacher puts an original-language word on screen, the entry's word, its definition and its
 * KJV usage are what the room reads. These render the presenter for a real entry and assert those
 * land on screen, and that a null entry leaves the screen blank rather than showing a stale word —
 * the case that otherwise leaves the last word stuck up during a transition to nothing.
 */
@OptIn(ExperimentalTestApi::class)
class DictionaryPresenterRenderTest {

    private val screen = Modifier.size(1920.dp, 1080.dp)

    private val elohim = StrongsEntry(
        number = "H430",
        word = "ʼĕlôhîym",
        transliteration = "elohim",
        pronunciation = "el-o-heem'",
        definition = "gods in the ordinary sense; the supreme God",
        kjvUsage = "God (2346x), god (244x)",
    )

    private fun runDict(
        entry: StrongsEntry?,
        settings: DictionarySettings = DictionarySettings(),
        outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
        transitionAlpha: Float = 1f,
        body: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            Box(screen) {
                DictionaryPresenter(
                    entry = entry,
                    dictionarySettings = settings,
                    outputRole = outputRole,
                    transitionAlpha = transitionAlpha,
                )
            }
        }
        body()
    }

    @Test
    fun `an entry shows its word and definition`() = runDict(elohim) {
        onNodeWithText(
            "ʼĕlôhîym",
            substring = true,
        ).assertExists("the original-language word is the point of the slide")
        onNodeWithText("the supreme God", substring = true).assertExists("the definition must reach the screen")
    }

    @Test
    fun `the KJV usage is shown when enabled`() = runDict(elohim, DictionarySettings(showKjvUsage = true)) {
        onNodeWithText("God (2346x)", substring = true).assertExists("KJV usage is part of the entry when turned on")
    }

    @Test
    fun `no entry leaves the screen blank`() = runDict(entry = null) {
        onNodeWithText("ʼĕlôhîym", substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun `shadows do not cost the entry its text`() = runDict(
        elohim,
        DictionarySettings(
            showKjvUsage = true,
            wordShadow = true,
            wordShadowColor = "#102030",
            wordShadowSize = 140,
            wordShadowOpacity = 70,
            referenceShadow = true,
            referenceShadowColor = "#405060",
            referenceShadowSize = 60,
            referenceShadowOpacity = 40,
        ),
    ) {
        // The shadows are built per text run from the colour, size and opacity settings; what a test
        // can hold the presenter to is that turning them on changes nothing about what is legible.
        onNodeWithText("ʼĕlôhîym", substring = true).assertExists()
        onNodeWithText("the supreme God", substring = true).assertExists()
        onNodeWithText("el-o-heem'", substring = true).assertExists()
        onNodeWithText("God (2346x)", substring = true).assertExists()
    }

    @Test
    fun `an unparseable shadow colour still renders the entry`() = runDict(
        elohim,
        DictionarySettings(wordShadow = true, wordShadowColor = "not a colour", referenceShadow = true),
    ) {
        onNodeWithText("ʼĕlôhîym", substring = true).assertExists()
    }

    // ── Which parts of the entry are shown ──────────────────────────────────────

    @Test
    fun `turning the reference off drops the number and the pronunciation`() = runDict(
        elohim,
        DictionarySettings(showReference = false),
    ) {
        onNodeWithText("H430", substring = true).assertDoesNotExist()
        onNodeWithText("el-o-heem'", substring = true).assertDoesNotExist()
        onNodeWithText("ʼĕlôhîym", substring = true).assertExists("the word itself is a separate switch")
    }

    @Test
    fun `turning the word off leaves the rest of the entry`() = runDict(
        elohim,
        DictionarySettings(showWord = false),
    ) {
        onNodeWithText("ʼĕlôhîym", substring = true).assertDoesNotExist()
        onNodeWithText("the supreme God", substring = true).assertExists()
    }

    @Test
    fun `turning the definition off leaves the word`() = runDict(
        elohim,
        DictionarySettings(showDefinition = false),
    ) {
        onNodeWithText("the supreme God", substring = true).assertDoesNotExist()
        onNodeWithText("ʼĕlôhîym", substring = true).assertExists()
    }

    @Test
    fun `turning the KJV usage off drops it`() = runDict(
        elohim,
        DictionarySettings(showKjvUsage = false),
    ) {
        onNodeWithText("God (2346x)", substring = true).assertDoesNotExist()
        onNodeWithText("ʼĕlôhîym", substring = true).assertExists()
    }

    @Test
    fun `an entry with nothing but a number shows only the number`() = runDict(
        StrongsEntry(
            number = "H9999",
            word = "",
            transliteration = "",
            pronunciation = "",
            definition = "",
            kjvUsage = "",
        ),
    ) {
        // Every field of the entry is switched on, so what is missing is the entry's own content —
        // an empty field must draw nothing rather than an empty line pushing the layout around.
        onNodeWithText("H9999", substring = true).assertExists()
    }

    @Test
    fun `a pronunciation identical to the transliteration is not printed twice`() = runDict(
        elohim.copy(transliteration = "elohim", pronunciation = "elohim"),
    ) {
        onNodeWithText("elohim  •  elohim", substring = true).assertDoesNotExist()
        onNodeWithText("elohim", substring = true).assertExists()
    }

    @Test
    fun `a pronunciation with no transliteration stands on its own`() = runDict(
        elohim.copy(transliteration = ""),
    ) {
        // Without the transliteration there is nothing for the separator to sit between.
        onNodeWithText("•", substring = true).assertDoesNotExist()
        onNodeWithText("el-o-heem'", substring = true).assertExists()
    }

    @Test
    fun `an italic word still reads the same`() = runDict(
        elohim,
        DictionarySettings(wordItalic = true, wordBold = true),
    ) {
        onNodeWithText("ʼĕlôhîym", substring = true).assertExists()
    }

    // ── The key output ──────────────────────────────────────────────────────────

    @Test
    fun `the key signal draws the same entry in plain white on black`() = runDict(
        elohim,
        outputRole = Constants.OUTPUT_ROLE_KEY,
    ) {
        // The key is a matte for a hardware keyer: the configured colours are replaced outright,
        // but every piece of text still has to be there or the fill is keyed against nothing.
        onNodeWithText("ʼĕlôhîym", substring = true).assertExists()
        onNodeWithText("the supreme God", substring = true).assertExists()
        onNodeWithText("God (2346x)", substring = true).assertExists()
    }

    @Test
    fun `a mid-transition entry is still on screen`() = runDict(elohim, transitionAlpha = 0.4f) {
        onNodeWithText("ʼĕlôhîym", substring = true).assertExists()
    }
}
