@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.composables.LocalFontPreviewFace
import org.churchpresenter.app.churchpresenter.dialogs.tabs.BIBLE_SOURCE_TRIGGER_TAG
import org.churchpresenter.app.churchpresenter.dialogs.tabs.CustomizePane
import org.churchpresenter.app.churchpresenter.dialogs.tabs.PROFILE_CONTENT_TOGGLE_TAG
import org.churchpresenter.app.churchpresenter.dialogs.tabs.ProfilesSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.previewShapeTag
import org.churchpresenter.app.churchpresenter.dialogs.tabs.railTag
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.theme.ChurchPresenterTheme
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * The Profiles tab of the settings dialog, in both themes.
 *
 * Down the left, every profile with its mode and what uses it; to the right of it the editor --
 * display mode, the content this profile shows, its Bible and song sources, its scaling -- then the
 * Style tabs and the picture beside them. What changes the shape of the tab rather than a value in
 * it is shot here: each Style tab (the Bible, Songs and Background panes, the whole-form STT,
 * Subtitles, Q&A and Dictionary tabs, the stage monitor's zone editor), the content section open,
 * the Bible source menu, a custom preview shape, a lower third, and a profile with nothing to style.
 *
 * Font names are drawn in one face ([LocalFontPreviewFace]) so the picker rows are not the
 * recording machine's font book.
 */
class ProfilesTabScreenshotTest {

    /** The picker's "Recent" row is JVM-wide state — see [PinnedRecentColors]. */
    private val recents = PinnedRecentColors()

    @BeforeTest
    fun pinRecentColors() = recents.clear()

    @AfterTest
    fun unpinRecentColors() = recents.restore()

    // ── The tab ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `as it opens`() = shoot("defaults")

    @Test
    fun `the content section open`() = shoot("content_open") {
        onNodeWithTag(PROFILE_CONTENT_TOGGLE_TAG).performClick()
    }

    @Test
    fun `the Bible source menu open`() = shoot("bible_source_menu", rootIndex = 1) {
        onNodeWithTag(BIBLE_SOURCE_TRIGGER_TAG).performClick()
    }

    @Test
    fun `a custom preview shape`() = shoot("custom_shape") {
        onNodeWithTag(previewShapeTag("CUSTOM")).performClick()
    }

    @Test
    fun `a lower third`() = shoot("lower_third") { displayMode("Lower Third") }

    @Test
    fun `nothing left to style`() = shoot(
        "nothing_to_style",
        settings = library(
            OutputProfile(
                bibleMode = Constants.SONG_LANG_OFF,
                songMode = Constants.SONG_LANG_OFF,
                showFullscreenBackground = false,
                showBibleBackground = false,
                showSongsBackground = false,
                showSTT = false,
                showSubtitles = false,
                showQA = false,
                showDictionary = false,
            ),
        ),
    )

    // ── The Style tabs ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `the Songs tab`() = shoot("style_songs") { tab(CustomizePane.SONGS) }

    @Test
    fun `the Background tab`() = shoot("style_background") { tab(CustomizePane.BACKGROUND) }

    @Test
    fun `the STT tab`() = shoot("style_stt") { tab(CustomizePane.CAPTIONS) }

    @Test
    fun `the Subtitles tab`() = shoot("style_subtitles") { tab(CustomizePane.SUBTITLES) }

    @Test
    fun `the Q&A tab`() = shoot("style_qa") { tab(CustomizePane.QA) }

    @Test
    fun `the Dictionary tab`() = shoot("style_dictionary") { tab(CustomizePane.DICTIONARY) }

    @Test
    fun `a stage monitor`() = shoot("stage_monitor") { displayMode("Stage Monitor") }

    // ── Harness ─────────────────────────────────────────────────────────────────────────────────

    private fun ComposeUiTest.tab(pane: CustomizePane) {
        onNodeWithTag(railTag(pane.name)).performClick()
    }

    /** A display-mode segment, told apart by role from the Lower Third content switch. */
    private fun ComposeUiTest.displayMode(label: String) {
        onNode(hasTextExactly(label) and hasClickAction() and !isToggleable()).performClick()
    }

    private fun shoot(
        name: String,
        settings: AppSettings = library(),
        rootIndex: Int = 0,
        drive: ComposeUiTest.() -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        TestSingletons.latchSkikoHostOs()
        runSkikoComposeUiTest(size = Size(WIDTH, HEIGHT), density = Density(1f)) {
            setContent {
                CompositionLocalProvider(LocalFontPreviewFace provides { FontFamily.Default }) {
                    ChurchPresenterTheme(themeMode = mode) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Box(Modifier.fillMaxSize()) {
                                var current by remember { mutableStateOf(settings) }
                                ProfilesSettingsTab(
                                    settings = current,
                                    onSettingsChange = { transform -> current = transform(current) },
                                )
                            }
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

    /**
     * Three translations and three profiles: the one on screen 1, a lower third nothing uses yet,
     * and a stage monitor -- so the list shows every mode's badge and both kinds of "used by".
     */
    private fun library(main: OutputProfile = OutputProfile()): AppSettings {
        val bible = BibleSettings(
            translations = listOf(
                translation("kjv.spb", "KJV", "King James Version"),
                translation("rst.spb", "RST", "Russian Synodal"),
                translation("niv.spb", "NIV", "New International"),
            ),
        )
        return AppSettings(
            bibleSettings = bible,
            projectionSettings = ProjectionSettings(
                outputProfiles = listOf(
                    main.copy(id = "main", name = "Sanctuary", bibleSettings = bible),
                    OutputProfile(
                        id = "stream",
                        name = "Livestream",
                        displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
                    ),
                    OutputProfile(
                        id = "stage",
                        name = "Choir monitor",
                        displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR,
                    ),
                ),
                screenAssignments = listOf(ScreenAssignment(activeProfileId = "main")),
            ),
        )
    }

    private fun translation(fileName: String, abbreviation: String, name: String) =
        BibleTranslationSettings(fileName = fileName, customAbbreviation = abbreviation, customName = name)

    private companion object {
        const val SECTION = "profilesTab"

        /** The size the settings dialog opens at, which the editor's columns are laid out against. */
        const val WIDTH = 1400f
        const val HEIGHT = 900f
    }
}
