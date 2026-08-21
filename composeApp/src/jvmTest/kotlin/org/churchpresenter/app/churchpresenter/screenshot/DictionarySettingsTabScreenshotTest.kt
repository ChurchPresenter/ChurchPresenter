@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.DictionarySettings
import org.churchpresenter.app.churchpresenter.dialogs.tabs.DictionarySettingsTab
import org.churchpresenter.theme.ChurchPresenterTheme
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * The Dictionary tab of the settings dialog, in both themes.
 *
 * Six sections over two columns in one scroll container — Word, Definition and the card background
 * down the left, Reference, KJV Usage and Transitions down the right — styling what a Strong's entry
 * looks like on the output.
 *
 * What changes the shape of the tab rather than a value in it:
 *
 *  - **The two shadow toggles.** Word and Reference each unfold a color/size/opacity row when
 *    switched on; the other four sections have no shadow at all. Off is the default, so the shape
 *    with them on is a different tab and is shot separately.
 *  - **Seven color pickers** — one per section, plus one inside each unfolded shadow row. Each is
 *    shot from a fixture color of its own, both so the field a picker opened from is legible and so
 *    no two of the images are the same picture.
 *
 * The four Show switches gate what reaches the *output*, not what this tab draws, so switching them
 * off moves nothing but the switches themselves — one image covers all four.
 *
 * The font dropdowns are never opened: their list is whatever `GraphicsEnvironment` reports on the
 * recording machine, so an image of one would belong to whoever recorded it.
 */
class DictionarySettingsTabScreenshotTest {

    /** The picker's "Recent" row is JVM-wide state — see [PinnedRecentColors]. */
    private val recents = PinnedRecentColors()

    @BeforeTest
    fun pinRecentColors() = recents.clear()

    @AfterTest
    fun unpinRecentColors() = recents.restore()

    // ── The tab ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `as it opens`() = shoot("defaults")

    /** Both shadows on, which unfolds a color/size/opacity row under each. */
    @Test
    fun `the shadow rows unfolded`() = shoot(
        "shadows_on",
        settings = dictionary { copy(wordShadow = true, referenceShadow = true) },
    )

    /**
     * Every value off its default.
     *
     * Bold and italic lit on the word, a color and size per section, the card dimmed most of the
     * way and a long transition — so the two sliders sit somewhere other than where they open.
     */
    @Test
    fun `styled away from the defaults`() = shoot("styled", settings = styled())

    /** All four Show switches off — the only thing on the tab they change. */
    @Test
    fun `nothing shown on the output`() = shoot(
        "all_hidden",
        settings = dictionary {
            copy(showWord = false, showDefinition = false, showReference = false, showKjvUsage = false)
        },
    )

    /** Both fades off, which is what leaves the entry to cut in and out. */
    @Test
    fun `the fades switched off`() = shoot(
        "fades_off",
        settings = dictionary { copy(fadeIn = false, fadeOut = false, transitionDuration = 100f) },
    )

    // ── Every color picker ─────────────────────────────────────────────────────────────────────

    @Test
    fun `the word color picker`() = picker("picker_word", WORD)

    @Test
    fun `the word shadow color picker`() = picker("picker_word_shadow", WORD_SHADOW)

    @Test
    fun `the definition color picker`() = picker("picker_definition", DEFINITION)

    @Test
    fun `the card background color picker`() = picker("picker_card_background", CARD)

    @Test
    fun `the reference color picker`() = picker("picker_reference", REFERENCE)

    @Test
    fun `the reference shadow color picker`() = picker("picker_reference_shadow", REFERENCE_SHADOW)

    @Test
    fun `the KJV usage color picker`() = picker("picker_kjv_usage", KJV)

    // ── Driving ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Opens the picker on the field showing [hex].
     *
     * Every color on the tab is given a distinct value first: the defaults repeat `#FFFFFF` on the
     * word and the reference and `#000000` on both shadows, so a field could not be addressed by
     * what it displays, and two pairs of these images would have come out identical.
     */
    private fun picker(name: String, hex: String) = shoot(
        name,
        settings = distinctColours(),
        rootIndex = 1,
    ) {
        onAllNodesWithText(hex)[0].performScrollTo().performClick()
        waitForIdle()
    }

    // ── Harness ─────────────────────────────────────────────────────────────────────────────────

    private fun shoot(
        name: String,
        settings: AppSettings = AppSettings(),
        rootIndex: Int = 0,
        drive: ComposeUiTest.() -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        runComposeUiTest {
            setContent {
                ChurchPresenterTheme(themeMode = mode) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize()) {
                            var current by remember { mutableStateOf(settings) }
                            DictionarySettingsTab(
                                settings = current,
                                onSettingsChange = { transform -> current = transform(current) },
                            )
                        }
                    }
                }
            }
            waitForIdle()
            drive()
            waitForIdle()
            captureTo(file, rootIndex)
        }
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────

    private fun dictionary(edit: DictionarySettings.() -> DictionarySettings) =
        AppSettings(dictionarySettings = DictionarySettings().edit())

    private fun styled() = dictionary {
        copy(
            wordColor = "#FFD54F",
            wordFontSize = 96,
            wordBold = true,
            wordItalic = true,
            definitionColor = "#B3E5FC",
            definitionFontSize = 44,
            referenceColor = "#7BE38F",
            referenceFontSize = 36,
            kjvUsageColor = "#F48FB1",
            kjvUsageFontSize = 30,
            cardBackgroundColor = "#123A6B",
            cardBackgroundOpacity = 0.35f,
            transitionDuration = 1600f,
        )
    }

    /** Both shadows unfolded and all seven colors distinct, so each picker can be addressed. */
    private fun distinctColours() = dictionary {
        copy(
            wordShadow = true,
            referenceShadow = true,
            wordColor = WORD,
            wordShadowColor = WORD_SHADOW,
            definitionColor = DEFINITION,
            cardBackgroundColor = CARD,
            referenceColor = REFERENCE,
            referenceShadowColor = REFERENCE_SHADOW,
            kjvUsageColor = KJV,
        )
    }

    private companion object {
        const val SECTION = "dictionarySettingsTab"

        // One color per picker, none of them repeated anywhere else on the tab.
        const val WORD = "#FFD54F"
        const val WORD_SHADOW = "#8B1E3F"
        const val DEFINITION = "#B3E5FC"
        const val CARD = "#123A6B"
        const val REFERENCE = "#7BE38F"
        const val REFERENCE_SHADOW = "#5C2E91"
        const val KJV = "#F48FB1"
    }
}
