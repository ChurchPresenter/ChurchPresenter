@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Bilingual song output — the four layouts a second language can be drawn in.
 *
 * `side_by_side` and `top_bottom` are separate branches, and each splits again on whether the output
 * is a full screen (each language gets its own half) or a lower third (compact, no height splitting).
 * All four had gone untested, so a language silently vanishing from one of them looked identical on
 * the other three.
 *
 * What matters in every case is that **both languages reach the screen**: a congregation reading the
 * secondary language sees nothing at all if that column stops rendering, and the operator watching
 * the primary would not notice. The language-display setting is the deliberate exception, and is
 * asserted in both directions.
 *
 * Assertions are on the lyric text that lands on screen rather than on positions or sizes — font
 * metrics differ across the three target platforms, and which half a line is drawn in is a layout
 * detail the tests should not pin.
 */
class SongPresenterBilingualRenderTest {

    private val screen = Modifier.size(1920.dp, 1080.dp)

    private fun bilingual(
        primary: List<String> = listOf("Amazing grace how sweet the sound"),
        secondary: List<String> = listOf("Chudnaya blagodat"),
        isLast: Boolean = false,
        header: String = "[Verse 1]",
    ) = LyricSection(
        header = header,
        title = "Amazing Grace",
        secondaryTitle = "Chudnaya blagodat",
        songNumber = 42,
        type = Constants.SECTION_TYPE_VERSE,
        lines = primary,
        secondaryLines = secondary,
        isLastSection = isLast,
    )

    private fun settings(
        layout: String = Constants.BILINGUAL_SIDE_BY_SIDE,
        fullscreenLanguage: String = Constants.SONG_LANG_BOTH,
        lowerThirdLanguage: String = Constants.SONG_LANG_BOTH,
        lookAheadLanguage: String = Constants.SONG_LANG_BOTH,
    ) = AppSettings(
        songSettings = SongSettings(
            bilingualLayout = layout,
            fullscreenLanguageDisplay = fullscreenLanguage,
            lowerThirdLanguageDisplay = lowerThirdLanguage,
            lookAheadLanguageDisplay = lookAheadLanguage,
        )
    )

    private fun present(
        section: LyricSection,
        appSettings: AppSettings,
        isLowerThird: Boolean = false,
        lookAheadEnabled: Boolean = false,
        allSections: List<LyricSection> = emptyList(),
        displaySectionIndex: Int = -1,
        block: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(screen) {
                    SongPresenter(
                        lyricSection = section,
                        appSettings = appSettings,
                        isLowerThird = isLowerThird,
                        lookAheadEnabled = lookAheadEnabled,
                        allLyricSections = allSections,
                        displaySectionIndex = displaySectionIndex,
                    )
                }
            }
        }
        block()
    }

    // ── The four bilingual layouts ──────────────────────────────────────────────────────────────

    @Test
    fun `side by side on a full screen draws both languages`() {
        present(bilingual(), settings(layout = Constants.BILINGUAL_SIDE_BY_SIDE)) {
            onNodeWithText("Amazing grace how sweet the sound", substring = true).assertExists()
            onNodeWithText("Chudnaya blagodat", substring = true).assertExists()
        }
    }

    @Test
    fun `side by side on a lower third draws both languages`() {
        present(
            bilingual(),
            settings(layout = Constants.BILINGUAL_SIDE_BY_SIDE),
            isLowerThird = true,
        ) {
            onNodeWithText("Amazing grace how sweet the sound", substring = true).assertExists()
            onNodeWithText("Chudnaya blagodat", substring = true).assertExists()
        }
    }

    @Test
    fun `top and bottom on a full screen draws both languages`() {
        present(bilingual(), settings(layout = Constants.BILINGUAL_TOP_BOTTOM)) {
            onNodeWithText("Amazing grace how sweet the sound", substring = true).assertExists()
            onNodeWithText("Chudnaya blagodat", substring = true).assertExists()
        }
    }

    @Test
    fun `top and bottom on a lower third draws both languages`() {
        present(
            bilingual(),
            settings(layout = Constants.BILINGUAL_TOP_BOTTOM),
            isLowerThird = true,
        ) {
            onNodeWithText("Amazing grace how sweet the sound", substring = true).assertExists()
            onNodeWithText("Chudnaya blagodat", substring = true).assertExists()
        }
    }

    @Test
    fun `a multi-line section keeps every line of both languages`() {
        present(
            bilingual(
                primary = listOf("first english line", "second english line"),
                secondary = listOf("first other line", "second other line"),
            ),
            settings(layout = Constants.BILINGUAL_TOP_BOTTOM),
        ) {
            listOf(
                "first english line", "second english line",
                "first other line", "second other line",
            ).forEach { onNodeWithText(it, substring = true).assertExists("$it went missing") }
        }
    }

    // ── Choosing one language ───────────────────────────────────────────────────────────────────

    @Test
    fun `primary only drops the second language`() {
        present(bilingual(), settings(fullscreenLanguage = Constants.SONG_LANG_PRIMARY)) {
            onNodeWithText("Amazing grace how sweet the sound", substring = true).assertExists()
            onNodeWithText("Chudnaya blagodat", substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun `secondary only drops the first language`() {
        present(bilingual(), settings(fullscreenLanguage = Constants.SONG_LANG_SECONDARY)) {
            // Twice over, now that the title draws by default: the slide's title switches to the
            // secondary one too, and the fixture gives both the same words.
            onAllNodesWithText("Chudnaya blagodat", substring = true).onFirst().assertExists()
            onNodeWithText("Amazing grace how sweet the sound", substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun `the lower third has its own language choice`() {
        // Full screen shows both; the band's lower third is set to the primary only. The two settings
        // are independent, so the lower third must not pick up the full-screen answer.
        present(
            bilingual(),
            settings(
                fullscreenLanguage = Constants.SONG_LANG_BOTH,
                lowerThirdLanguage = Constants.SONG_LANG_PRIMARY,
            ),
            isLowerThird = true,
        ) {
            onNodeWithText("Amazing grace how sweet the sound", substring = true).assertExists()
            onNodeWithText("Chudnaya blagodat", substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun `a section with no second language still draws the first`() {
        present(
            bilingual(secondary = emptyList()),
            settings(fullscreenLanguage = Constants.SONG_LANG_BOTH),
        ) {
            onNodeWithText("Amazing grace how sweet the sound", substring = true).assertExists()
        }
    }

    // ── Bilingual look-ahead ────────────────────────────────────────────────────────────────────

    @Test
    fun `bilingual look-ahead previews the next section in both languages`() {
        val current = bilingual()
        val next = bilingual(
            primary = listOf("That saved a wretch like me"),
            secondary = listOf("Spasla menya"),
            header = "[Verse 2]",
        )
        present(
            current,
            settings(layout = Constants.BILINGUAL_TOP_BOTTOM),
            lookAheadEnabled = true,
            allSections = listOf(current, next),
            displaySectionIndex = 0,
        ) {
            onNodeWithText("That saved a wretch like me", substring = true)
                .assertExists("the band's preview must carry the primary")
            onNodeWithText("Spasla menya", substring = true)
                .assertExists("and the second language too")
        }
    }

    @Test
    fun `look-ahead on a lower third previews the next section`() {
        val current = bilingual()
        val next = bilingual(primary = listOf("That saved a wretch like me"), header = "[Verse 2]")
        present(
            current,
            settings(layout = Constants.BILINGUAL_SIDE_BY_SIDE),
            isLowerThird = true,
            lookAheadEnabled = true,
            allSections = listOf(current, next),
            displaySectionIndex = 0,
        ) {
            onNodeWithText("That saved a wretch like me", substring = true).assertExists()
        }
    }

    @Test
    fun `on the last section the look-ahead space is reserved, not filled`() {
        // With no next section the preview would collapse and the lyrics would jump, so the presenter
        // reserves the space by re-drawing the current lines at zero alpha. That is visible here as
        // the same line appearing more than once — and no other section's text appearing at all.
        val only = bilingual(isLast = true)
        present(
            only,
            settings(),
            lookAheadEnabled = true,
            allSections = listOf(only),
            displaySectionIndex = 0,
        ) {
            assertTrue(
                textOnScreen().count { it.contains("Amazing grace how sweet the sound") } > 1,
                "the reserved placeholder should repeat the line: ${textOnScreen()}",
            )
            assertEquals(
                0,
                textOnScreen().count { it.contains("That saved") },
                "there is no next section to preview",
            )
        }
    }

    // ── The end-of-song indicator ───────────────────────────────────────────────────────────────

    @Test
    fun `the last section marks the end of the song`() {
        present(bilingual(isLast = true), settings()) {
            assertTrue(
                textOnScreen().any { it.contains('*') },
                "the operator's end-of-song marker: ${textOnScreen()}",
            )
        }
    }

    @Test
    fun `the marker's space is reserved on earlier sections too`() {
        // Drawn at zero alpha rather than omitted, so the lyrics don't shift on the last section.
        // Both cases must therefore render the same set of text nodes.
        var earlier = 0
        var last = 0
        present(bilingual(isLast = false), settings()) { earlier = textOnScreen().size }
        present(bilingual(isLast = true), settings()) { last = textOnScreen().size }

        assertEquals(earlier, last, "reserving the space is the whole point — the count must match")
    }

    // ── Background layers ───────────────────────────────────────────────────────────────────────

    @Test
    fun `a gradient overlay does not cover the lyrics`() {
        val withGradient = AppSettings(
            songSettings = SongSettings(bilingualLayout = Constants.BILINGUAL_TOP_BOTTOM),
            backgroundSettings = BackgroundSettings(
                songBackground = BackgroundConfig(
                    gradientEnabled = true,
                    gradientTopColor = "#000000",
                    gradientBottomColor = "#FFFFFF",
                    gradientTopOpacity = 0.8f,
                    gradientBottomOpacity = 0.2f,
                    gradientPosition = 0.5f,
                ),
            ),
        )
        present(bilingual(), withGradient) {
            onNodeWithText("Amazing grace how sweet the sound", substring = true).assertExists()
            onNodeWithText("Chudnaya blagodat", substring = true).assertExists()
        }
    }

    @Test
    fun `a key output still draws the lyrics`() {
        // The fill/key pair has to agree on where the text is, so the key signal renders the same
        // lyrics — only the colours differ.
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(screen) {
                        SongPresenter(
                            lyricSection = bilingual(),
                            appSettings = settings(),
                            outputRole = Constants.OUTPUT_ROLE_KEY,
                        )
                    }
                }
            }
            onNodeWithText("Amazing grace how sweet the sound", substring = true).assertExists()
            onNodeWithText("Chudnaya blagodat", substring = true).assertExists()
        }
    }

    @Test
    fun `a vertical lower third draws both languages`() {
        present(
            bilingual(),
            settings(layout = Constants.BILINGUAL_TOP_BOTTOM),
            isLowerThird = true,
        ) {
            onNodeWithText("Amazing grace how sweet the sound", substring = true).assertExists()
        }
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(screen) {
                        SongPresenter(
                            lyricSection = bilingual(),
                            appSettings = settings(),
                            isLowerThird = true,
                            isLowerThirdVertical = true,
                        )
                    }
                }
            }
            onNodeWithText("Amazing grace how sweet the sound", substring = true).assertExists()
            onNodeWithText("Chudnaya blagodat", substring = true).assertExists()
        }
    }
}

/** Every string the presenter drew. */
private fun ComposeUiTest.textOnScreen(): List<String> =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { t -> t.text } }
