package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.data.StrongsEntry
import org.churchpresenter.app.churchpresenter.data.settings.DictionarySettings
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
        body: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            Box(screen) { DictionaryPresenter(entry = entry, dictionarySettings = settings) }
        }
        body()
    }

    @Test
    fun `an entry shows its word and definition`() = runDict(elohim) {
        onNodeWithText("ʼĕlôhîym",
            substring = true).assertExists("the original-language word is the point of the slide")
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
}
