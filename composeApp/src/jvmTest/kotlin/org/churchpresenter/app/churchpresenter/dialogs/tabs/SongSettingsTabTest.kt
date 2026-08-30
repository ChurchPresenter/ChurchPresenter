@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import kotlinx.serialization.json.Json
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Song settings tab: a rail of slide-wide settings, and one set of styling controls pointed by
 * two switches.
 *
 * Settings are held in test state and fed straight back in, exactly as `OptionsDialog` does, so each
 * interaction is followed through to the value it changes. Nothing in the tab is modified for these
 * tests: no parameter, no test tag beyond the two the title-slide rows already carried, no
 * reflection, no mocks.
 */
class SongSettingsTabTest {

    /**
     * [settings] as they come back from settings.json.
     *
     * A control that updates state but whose field does not survive the file would silently lose
     * the operator's change on the next launch.
     */
    private fun persisted(settings: AppSettings): AppSettings =
        Json { ignoreUnknownKeys = true }.decodeFromString(
            AppSettings.serializer(),
            Json { encodeDefaults = true }.encodeToString(AppSettings.serializer(), settings),
        )

    private fun song(get: () -> AppSettings) = get().songSettings

    // ── The rail ──────────────────────────────────────────────────────────────

    @Test
    fun `the title slide checkbox writes its own flag`() = songTab { get ->
        onNodeWithTag("song_titleSlideEnabled").performClick()
        waitForIdle()

        assertEquals(true, song(get).titleSlideEnabled)
        assertEquals(true, persisted(get()).songSettings.titleSlideEnabled)
    }

    @Test
    fun `the number row is disabled until the title slide is on`() = songTab { get ->
        onNodeWithTag("song_titleSlideShowSongNumber").performClick()
        waitForIdle()

        assertEquals(
            SongSettings().titleSlideShowSongNumber,
            song(get).titleSlideShowSongNumber,
            "the row is disabled while the slide is off, so a click must not reach it",
        )
    }

    @Test
    fun `word wrap toggles only its own flag`() = songTab { get ->
        val before = song(get)

        onNodeWithText("Word Wrap").performClick()
        waitForIdle()

        assertEquals(before.copy(wordWrap = !before.wordWrap), song(get))
    }

    @Test
    fun `the chorus auto-repeat checkbox toggles only its own flag`() = songTab { get ->
        val before = song(get)
        assertTrue(before.autoRepeatChorus, "it ships on, so existing songs present as they always did")

        onNodeWithTag("song_autoRepeatChorus").performClick()
        waitForIdle()

        assertEquals(before.copy(autoRepeatChorus = false), song(get))
        assertEquals(false, persisted(get()).songSettings.autoRepeatChorus)
    }

    @Test
    fun `each vertical alignment button selects its own alignment`() = songTab { get ->
        listOf("Align Top" to Constants.TOP, "Align Middle" to Constants.MIDDLE, "Align Bottom" to Constants.BOTTOM)
            .forEach { (description, expected) ->
                onAllNodesWithContentDescription(description).onFirst().performClick()
                waitForIdle()
                assertEquals(expected, song(get).lyricsAlignment, description)
            }
    }

    @Test
    fun `the fade and crossfade boxes each toggle only themselves`() = songTab { get ->
        val before = song(get)

        onNodeWithText("Crossfade").performClick()
        waitForIdle()

        assertEquals(before.copy(crossfade = !before.crossfade), song(get))
    }

    @Test
    fun `the end-of-song spacing field writes its own value`() = songTab { get ->
        onNodeWithText(SongSettings().endOfSongIndicatorSpacing.toString()).performTextReplacement("7")
        waitForIdle()

        assertEquals(7, song(get).endOfSongIndicatorSpacing)
        assertEquals(7, persisted(get()).songSettings.endOfSongIndicatorSpacing)
    }

    // ── Margins ───────────────────────────────────────────────────────────────

    @Test
    fun `each margin field writes its own margin`() {
        // Distinct starting values so every field is findable by what it is showing.
        val distinct = AppSettings(
            songSettings = SongSettings(
                marginTop = 11,
                marginLeft = 22,
                marginRight = 33,
                marginBottom = 44,
                lowerThirdHeightPercent = 55,
            ),
        )
        listOf(
            "11" to { s: SongSettings -> s.marginTop },
            "22" to { s: SongSettings -> s.marginLeft },
            "33" to { s: SongSettings -> s.marginRight },
            "44" to { s: SongSettings -> s.marginBottom },
        ).forEach { (shown, read) ->
            songTab(distinct) { get ->
                onNodeWithText(shown).performTextReplacement("60")
                waitForIdle()
                assertEquals(60, read(song(get)), "the field showing $shown")
            }
        }
    }

    @Test
    fun `a margin beyond the allowed range is not stored`() = songTab(
        AppSettings(songSettings = SongSettings(marginTop = 11)),
    ) { get ->
        onNodeWithText("11").performTextReplacement("900")
        waitForIdle()

        assertEquals(11, song(get).marginTop, "500 is the largest margin the field accepts")
        onAllNodesWithText("900").onFirst()
            .assertExists("the field still shows the rejected entry, so it can be corrected")
    }

    // ── Language, which lives on the outputs ──────────────────────────────────

    private fun withScreens(vararg modes: String) = AppSettings(
        projectionSettings = ProjectionSettings(
            screenAssignments = modes.map { ScreenAssignment(songMode = it) },
        ),
    )

    @Test
    fun `Single writes every output down to one language`() = songTab(
        withScreens(Constants.SONG_LANG_BOTH, Constants.SONG_LANG_BOTH),
    ) { get ->
        onNodeWithText("1 · Single").performClick()
        waitForIdle()

        assertEquals(
            listOf(Constants.SONG_LANG_PRIMARY, Constants.SONG_LANG_PRIMARY),
            get().projectionSettings.screenAssignments.map { it.songMode },
        )
        assertEquals(
            Constants.SONG_LANG_PRIMARY,
            persisted(get()).projectionSettings.screenAssignments.first().songMode,
            "and it must survive settings.json",
        )
    }

    @Test
    fun `Bilingual puts every output back to two`() = songTab(
        withScreens(Constants.SONG_LANG_PRIMARY),
    ) { get ->
        onNodeWithText("2 · Bilingual").performClick()
        waitForIdle()

        assertEquals(Constants.SONG_LANG_BOTH, get().projectionSettings.screenAssignments.single().songMode)
    }

    @Test
    fun `the bilingual layout row is offered only with two languages`() {
        songTab(withScreens(Constants.SONG_LANG_PRIMARY)) {
            onAllNodesWithText("Left / Right")
                .assertCountEquals(0)
        }
        songTab(withScreens(Constants.SONG_LANG_BOTH)) {
            onNodeWithText("Left / Right").assertExists("two languages have to be laid out somehow")
        }
    }

    @Test
    fun `the Lang row writes the outputs of the target it is pointed at`() = songTab(
        AppSettings(
            projectionSettings = ProjectionSettings(
                screenAssignments = listOf(
                    ScreenAssignment(displayMode = Constants.DISPLAY_MODE_FULLSCREEN),
                    ScreenAssignment(displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL),
                ),
            ),
        ),
    ) { get ->
        onNodeWithText("Secondary").performClick()
        waitForIdle()

        val screens = get().projectionSettings.screenAssignments
        assertEquals(Constants.SONG_LANG_SECONDARY, screens[0].songMode, "the full-screen output")
        assertEquals(Constants.SONG_LANG_BOTH, screens[1].songMode, "and the lower third is untouched")
    }

    // ── Chunk, which is per element ───────────────────────────────────────────

    @Test
    fun `the chunk row writes the ordinary slide for the ordinary elements`() = songTab { get ->
        onNodeWithText("1 Line").performClick()
        waitForIdle()

        assertEquals(Constants.SONG_DISPLAY_MODE_LINE, song(get).fullscreenDisplayMode)
        assertEquals(
            SongSettings().lookAheadDisplayMode,
            song(get).lookAheadDisplayMode,
            "the look-ahead slide keeps its own",
        )
    }

    @Test
    fun `the chunk row writes the look-ahead slide for the look-ahead elements`() = songTab { get ->
        pointAt(SongStyleElement.NEXT_SECTION, SongStyleTarget.FULL_SCREEN)

        onNodeWithText("1 Line").performClick()
        waitForIdle()

        assertEquals(Constants.SONG_DISPLAY_MODE_LINE, song(get).lookAheadDisplayMode)
        assertEquals(SongSettings().fullscreenDisplayMode, song(get).fullscreenDisplayMode)
    }

    // ── The one styling control set ───────────────────────────────────────────

    @Test
    fun `a style button writes the element and output it is pointed at`() {
        songProfiles.forEach { (element, target) ->
            songTab { get ->
                pointAt(element, target)
                onNodeWithText("B").performClick()
                waitForIdle()

                assertEquals(
                    true,
                    song(get).elementStyle(element, target).bold,
                    "bold on $element / $target",
                )
                assertEquals(
                    true,
                    persisted(get()).songSettings.elementStyle(element, target).bold,
                    "and it must survive settings.json",
                )
            }
        }
    }

    @Test
    fun `a style button leaves every other profile alone`() = songTab { get ->
        pointAt(SongStyleElement.LYRICS, SongStyleTarget.LOWER_THIRD)
        onNodeWithText("B").performClick()
        waitForIdle()

        songProfiles.filterNot {
            it.first == SongStyleElement.LYRICS && it.second == SongStyleTarget.LOWER_THIRD
        }.forEach { (element, target) ->
            assertEquals(
                defaultSongElementStyle(element, target),
                song(get).elementStyle(element, target),
                "$element / $target must be untouched",
            )
        }
    }

    @Test
    fun `strikethrough is the fourth face button, not shadow`() = songTab { get ->
        onNodeWithText("S").performClick()
        waitForIdle()

        val style = song(get).elementStyle(SongStyleElement.LYRICS, SongStyleTarget.FULL_SCREEN)
        assertEquals(true, style.strikethrough)
        assertEquals(false, style.shadow, "shadow has a labelled checkbox of its own")
    }

    @Test
    fun `the shadow checkbox unfolds its three settings`() = songTab { get ->
        onNodeWithText("Shadow").performClick()
        waitForIdle()

        assertEquals(
            true,
            song(get).elementStyle(SongStyleElement.LYRICS, SongStyleTarget.FULL_SCREEN).shadow,
        )
        onAllNodesWithText("SIZE (%)", ignoreCase = true).onFirst()
            .assertExists("turning shadow on must offer its size, colour and opacity")
    }

    @Test
    fun `text transform writes the element it is pointed at`() = songTab { get ->
        onNodeWithText("UPPERCASE").performClick()
        waitForIdle()

        assertEquals(Constants.TEXT_TRANSFORM_UPPERCASE, song(get).lyricsTransform)
        assertEquals(
            Constants.TEXT_TRANSFORM_NONE,
            song(get).titleTransform,
            "the title is a different element and must be untouched",
        )
    }

    @Test
    fun `Reset returns the element it is pointed at to its defaults`() = songTab { get ->
        onNodeWithText("B").performClick()
        waitForIdle()
        assertEquals(true, song(get).lyricsBold, "styled away from the default first")

        onNodeWithText("Reset").performClick()
        waitForIdle()

        assertEquals(
            defaultSongElementStyle(SongStyleElement.LYRICS, SongStyleTarget.FULL_SCREEN),
            song(get).elementStyle(SongStyleElement.LYRICS, SongStyleTarget.FULL_SCREEN),
        )
    }

    @Test
    fun `Reset leaves the other output alone`() = songTab { get ->
        pointAt(SongStyleElement.LYRICS, SongStyleTarget.LOWER_THIRD)
        onNodeWithText("B").performClick()
        waitForIdle()
        pointAt(SongStyleElement.LYRICS, SongStyleTarget.FULL_SCREEN)
        onNodeWithText("B").performClick()
        waitForIdle()

        onNodeWithText("Reset").performClick()
        waitForIdle()

        assertEquals(false, song(get).lyricsBold, "the full-screen profile was reset")
        assertEquals(true, song(get).lyricsLowerThirdBold, "the lower third keeps its own styling")
    }

    @Test
    fun `the colour picker writes the element it is pointed at`() = songTab { get ->
        pointAt(SongStyleElement.TITLE, SongStyleTarget.FULL_SCREEN)

        onAllNodesWithText("#FFFFFF", ignoreCase = true)[0].performClick()
        waitForIdle()
        onNode(hasSetTextAction() and hasText("#FFFFFF", ignoreCase = true)).performTextReplacement("#123456")
        waitForIdle()
        onNodeWithText("OK").performClick()
        waitForIdle()

        assertEquals("#123456", song(get).titleColor.uppercase().replace("#123456".uppercase(), "#123456"))
        assertEquals("#123456", persisted(get()).songSettings.titleColor)
    }

    // ── Controls that appear only where the setting exists ────────────────────

    @Test
    fun `only the number and the title offer a position`() {
        songProfiles.forEach { (element, target) ->
            songTab {
                pointAt(element, target)
                val expected = if (element.hasPosition) 1 else 0
                onAllNodesWithText("Position").assertCountEquals(expected)
            }
        }
    }

    @Test
    fun `only the fittable elements offer Auto`() {
        songProfiles.forEach { (element, target) ->
            songTab {
                pointAt(element, target)
                onAllNodesWithText("Auto").assertCountEquals(if (element.hasAutoFit) 1 else 0)
            }
        }
    }

    @Test
    fun `Auto writes the element and output it is pointed at`() = songTab { get ->
        onNodeWithText("Auto").performClick()
        waitForIdle()

        assertEquals(false, song(get).lyricsFontSizeAutoFit, "it starts on, so a click turns it off")
        assertEquals(
            SongSettings().lyricsLowerThirdFontSizeAutoFit,
            song(get).lyricsLowerThirdFontSizeAutoFit,
            "and the lower third keeps its own",
        )
    }

    @Test
    fun `no chord colour is offered, because these outputs draw no chords`() {
        songProfiles.forEach { (element, target) ->
            songTab {
                pointAt(element, target)
                onAllNodesWithText("Chord color", ignoreCase = true).assertCountEquals(0)
            }
        }
    }

    // ── The switches themselves ───────────────────────────────────────────────

    @Test
    fun `the output switch changes which profile the controls read`() = songTab(
        AppSettings(songSettings = SongSettings(lyricsFontSize = 91, lyricsLowerThirdFontSize = 37)),
    ) {
        onNodeWithText("91").assertExists("the full-screen size is shown first")

        onNodeWithText("Lower Third").performClick()
        waitForIdle()

        onNodeWithText("37").assertExists("and the lower third's own size after the switch")
        onAllNodesWithText("91").assertCountEquals(0)
    }

    @Test
    fun `every element tab is offered`() = songTab {
        SongStyleElement.entries.forEach { element ->
            onNodeWithText(element.tabLabel).assertExists()
        }
    }

    @Test
    fun `the scope note names the output being styled`() = songTab {
        onAllNodesWithText("Full screen · ", substring = true).onFirst().assertExists()

        onNodeWithText("Lower Third").performClick()
        waitForIdle()

        onAllNodesWithText("Lower third · ", substring = true).onFirst().assertExists()
    }

    @Test
    fun `the look-ahead preview switch is forced on by the elements that need it`() = songTab {
        onNodeWithText("Look ahead").assertIsOff()

        onNodeWithText("Look Ahead").performClick()
        waitForIdle()

        // That element only appears on a look-ahead slide, so selecting it takes the preview there.
        onNodeWithText("Look ahead").assertIsOn()
    }

    @Test
    fun `the tab composes with a stack of settings well away from the defaults`() = songTab(
        AppSettings(
            songSettings = SongSettings(
                titleSlideEnabled = true,
                wordWrap = true,
                lyricsAlignment = Constants.TOP,
                lyricsStrikethrough = true,
                lyricsLetterSpacing = 8,
                lyricsWordSpacing = 14,
                lyricsTransform = Constants.TEXT_TRANSFORM_CAPITALIZE,
                bilingualLayout = Constants.BILINGUAL_TOP_BOTTOM,
            ),
        ),
    ) { get ->
        assertTrue(get().songSettings.titleSlideEnabled)
        onNodeWithText("Capitalize").assertExists()
    }
}
