@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.DictionarySettings
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlin.test.assertEquals
import kotlin.test.Test
import org.churchpresenter.ui.SENTINEL_FONT
import org.churchpresenter.ui.assertColorFieldShows
import org.churchpresenter.ui.assertFontFieldShows
import org.churchpresenter.ui.assertNumberFieldShows
import org.churchpresenter.ui.colorFields

/**
 * Drives the tab from its **input** rather than from its controls: the settings object is replaced
 * from outside and the rendered tab must follow.
 *
 * This is the direction the behaviour tests cannot cover. They change a setting by clicking, so a
 * control that displayed its own local state instead of the value it was given would still look
 * right to them. Here nothing is clicked at all — every assertion is about a value the tab was
 * handed — which is what says the tab is a function of its settings.
 *
 * It also covers the paths a settings import or an Instance Link update takes: those replace the
 * whole object under a tab that is already on screen.
 */
class DictionarySettingsTabRecompositionTest {

    /** Renders the tab over a settings object [block] can swap out, then re-asserts. */
    private fun rerenderable(
        initial: AppSettings = AppSettings(),
        block: ComposeUiTest.(set: (DictionarySettings.() -> DictionarySettings) -> Unit) -> Unit,
    ) = runComposeUiTest {
        var state by mutableStateOf(initial)
        setContent {
            MaterialTheme {
                DictionarySettingsTab(
                    settings = state,
                    onSettingsChange = { transform -> state = transform(state) },
                )
            }
        }
        block { change -> state = state.copy(dictionarySettings = state.dictionarySettings.change()); waitForIdle() }
    }

    @Test
    fun `the tab survives a recomposition that changes none of its inputs`() = rerenderable { set ->
        for (section in Section.all) onAllNodesWithText(section).assertCountEquals(1)
        set { this }
        for (section in Section.all) onAllNodesWithText(section).assertCountEquals(1)
        switches().assertCountEquals(Switches.COUNT)
        colorFields().assertCountEquals(5)
    }

    @Test
    fun `a stored switch change reaches its switch without any interaction`() = rerenderable { set ->
        switch(Switches.SHOW_WORD).assertIsOn()
        set { copy(showWord = false) }
        switch(Switches.SHOW_WORD).assertIsOff()
        switch(Switches.SHOW_DEFINITION).assertIsOn()

        set { copy(showWord = true) }
        switch(Switches.SHOW_WORD).assertIsOn()
    }

    @Test
    fun `a stored colour change reaches its field without any interaction`() = rerenderable { set ->
        assertColorFieldShows("#DDDDDD", "the definition colour out of the box")
        set { copy(definitionColor = "#FF00FF") }
        assertColorFieldShows("#FF00FF", "the definition colour after the settings changed")
        onAllNodesWithText("#DDDDDD").assertCountEquals(0)
    }

    @Test
    fun `a stored font size change reaches its field without any interaction`() = rerenderable { set ->
        assertNumberFieldShows(70, "the word font size out of the box")
        set { copy(wordFontSize = 144) }
        assertNumberFieldShows(144, "the word font size after the settings changed")
        onAllNodesWithText("70").assertCountEquals(0)
    }

    @Test
    fun `a stored font family change reaches its dropdown without any interaction`() = rerenderable { set ->
        assertFontFieldShows("Arial", "the word font out of the box")
        set { copy(wordFontType = SENTINEL_FONT) }
        assertFontFieldShows(SENTINEL_FONT, "the word font after the settings changed")
    }

    @Test
    fun `a stored slider change reaches both readouts without any interaction`() = rerenderable { set ->
        assertOpacityReads(0.92f)
        assertDurationReads(500f)

        set { copy(cardBackgroundOpacity = 0.25f, transitionDuration = 1800f) }

        assertOpacityReads(0.25f)
        assertDurationReads(1800f)
    }

    @Test
    fun `switching a shadow on in settings adds its row and switching it off removes it`() = rerenderable { set ->
        onAllNodesWithText("SIZE (%)").assertCountEquals(0)

        set { copy(wordShadow = true) }
        onAllNodesWithText("SIZE (%)").assertCountEquals(1)
        colorFields().assertCountEquals(6)

        set { copy(referenceShadow = true) }
        onAllNodesWithText("SIZE (%)").assertCountEquals(2)
        colorFields().assertCountEquals(7)

        set { copy(wordShadow = false, referenceShadow = false) }
        onAllNodesWithText("SIZE (%)").assertCountEquals(0)
        colorFields().assertCountEquals(5)
    }

    /**
     * The parent hands the tab a **new `onSettingsChange` instance** on each recomposition, as
     * `OptionsDialog` does — its lambda closes over values that change, so it is not memoized to a
     * single instance.
     *
     * The tab must use the callback it was last given. Compose's strong skipping decides that by
     * comparing the new lambda against the old one, and a tab that skipped the update would keep
     * writing into a callback the parent has already replaced — the write would appear to succeed
     * and reach nothing. Asserted by making each generation of the callback identifiable and
     * checking a click lands in the newest one.
     */
    @Test
    fun `a click reaches the newest callback when the parent keeps replacing it`() = runComposeUiTest {
        var settings by mutableStateOf(AppSettings())
        var generation by mutableStateOf(0)
        var calledGeneration = -1

        setContent {
            MaterialTheme {
                val thisGeneration = generation
                DictionarySettingsTab(
                    settings = settings,
                    onSettingsChange = { transform ->
                        calledGeneration = thisGeneration
                        settings = transform(settings)
                    },
                )
            }
        }

        switch(Switches.SHOW_WORD).performScrollTo().performClick()
        waitForIdle()
        assertEquals(0, calledGeneration, "the first callback must be the one invoked")
        assertEquals(false, settings.dictionarySettings.showWord, "and its write must land")

        // The parent recomposes with a fresh callback; the settings object is untouched.
        generation = 1
        waitForIdle()

        switch(Switches.SHOW_WORD).performClick()
        waitForIdle()
        assertEquals(1, calledGeneration, "the replacement callback must be the one invoked, not the stale one")
        assertEquals(true, settings.dictionarySettings.showWord, "and its write must land too")
    }

    /**
     * The same round trip, but reached through [DictionaryHost] — the shape `OptionsDialog` uses.
     * Both directions are checked: a stored change must reach the controls, and a click must reach
     * the settings, with the tab's skip decision driven by the parent's forwarded change flags.
     */
    @Test
    fun `the tab tracks its inputs when reached through a parent that forwards them`() = runComposeUiTest {
        var settings by mutableStateOf(AppSettings())
        setContent {
            MaterialTheme {
                DictionaryHost(settings = settings) { transform -> settings = transform(settings) }
            }
        }

        // Inbound: a change made outside the tab reaches the controls.
        assertColorFieldShows("#DDDDDD", "the definition colour out of the box")
        settings = settings.copy(dictionarySettings = settings.dictionarySettings.copy(definitionColor = "#ABCDEF"))
        waitForIdle()
        assertColorFieldShows("#ABCDEF", "the definition colour after the parent changed it")

        // Outbound: a click reaches the settings.
        switch(Switches.SHOW_KJV).performScrollTo().performClick()
        waitForIdle()
        assertEquals(false, settings.dictionarySettings.showKjvUsage, "the click must reach the parent's callback")
        switch(Switches.SHOW_KJV).assertIsOff()

        // And an unchanged re-render leaves everything standing.
        settings = settings.copy()
        waitForIdle()
        assertColorFieldShows("#ABCDEF", "the definition colour after an unchanged re-render")
        switch(Switches.SHOW_KJV).assertIsOff()
    }

    /**
     * A whole-object replacement, as a settings import performs — every section changes at once and
     * each must pick up its own value rather than a neighbour's.
     */
    @Test
    fun `replacing every setting at once repaints every section correctly`() = rerenderable { set ->
        set {
            copy(
                wordColor = "#111111", wordFontSize = 111, wordFontType = SENTINEL_FONT,
                definitionColor = "#222222", definitionFontSize = 22,
                referenceColor = "#333333", referenceFontSize = 33,
                kjvUsageColor = "#444444", kjvUsageFontSize = 44,
                cardBackgroundColor = "#555555", cardBackgroundOpacity = 0.5f,
                transitionDuration = 1500f,
                showWord = false, showDefinition = false, showReference = false, showKjvUsage = false,
                fadeIn = false, fadeOut = false,
            )
        }

        for (hex in listOf("#111111", "#222222", "#333333", "#444444", "#555555")) {
            onAllNodesWithText(hex).assertCountEquals(1)
        }
        for (size in listOf("111", "22", "33", "44")) {
            onAllNodesWithText(size).assertCountEquals(1)
        }
        assertFontFieldShows(SENTINEL_FONT, "the word font")
        assertOpacityReads(0.5f)
        assertDurationReads(1500f)
        for (ordinal in 0 until Switches.COUNT) switch(ordinal).assertIsOff()
    }
}

/**
 * Stands in for `OptionsDialog`: a composable that takes the settings and the callback as its own
 * parameters and forwards them straight through.
 *
 * That is not a detail. Called this way the compiler propagates its caller's change flags into the
 * tab, so the tab's skip decision is made from the parent's knowledge rather than by comparing
 * values itself — a different path through the same code, and the one production actually takes.
 * Every other test here calls the tab directly from `setContent`, which exercises the other.
 */
@Composable
private fun DictionaryHost(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    DictionarySettingsTab(settings = settings, onSettingsChange = onSettingsChange)
}
