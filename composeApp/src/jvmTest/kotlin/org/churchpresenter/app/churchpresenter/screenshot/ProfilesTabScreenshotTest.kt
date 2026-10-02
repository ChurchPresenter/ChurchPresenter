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
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.sharedui.composables.LocalFontPreviewFace
import org.churchpresenter.app.churchpresenter.dialogs.tabs.ADJUST_REFERENCE_TAG
import org.churchpresenter.app.churchpresenter.dialogs.tabs.ADJUST_SWITCH_TAG
import org.churchpresenter.app.churchpresenter.dialogs.tabs.toggleCheckbox
import org.churchpresenter.app.churchpresenter.dialogs.tabs.PreviewBackgroundMode
import org.churchpresenter.app.churchpresenter.dialogs.tabs.previewBackgroundTag
import org.churchpresenter.app.churchpresenter.dialogs.tabs.BIBLE_SOURCE_TRIGGER_TAG
import org.churchpresenter.app.churchpresenter.dialogs.tabs.CustomizePane
import org.churchpresenter.app.churchpresenter.dialogs.tabs.DETAIL_ADVANCED_TAG
import org.churchpresenter.app.churchpresenter.dialogs.tabs.ONLY_CHANGES_TAG
import org.churchpresenter.app.churchpresenter.dialogs.tabs.PREVIEW_LARGER_TAG
import org.churchpresenter.app.churchpresenter.dialogs.tabs.ProfilePage
import org.churchpresenter.app.churchpresenter.dialogs.tabs.ProfilesSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongStyleLanguage
import org.churchpresenter.app.churchpresenter.dialogs.tabs.TEXT_SIZE_FIELD_TAG
import org.churchpresenter.app.churchpresenter.dialogs.tabs.openProfilePage
import org.churchpresenter.app.churchpresenter.dialogs.tabs.pickPreviewShape
import org.churchpresenter.app.churchpresenter.dialogs.tabs.profileRowTag
import org.churchpresenter.app.churchpresenter.dialogs.tabs.railTag
import org.churchpresenter.app.churchpresenter.dialogs.tabs.songLanguageTag
import org.churchpresenter.app.churchpresenter.dialogs.tabs.translationChipTag
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.withLinksResolved
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.sharedui.screenshot.PinnedRecentColors
import org.churchpresenter.sharedui.screenshot.captureTo
import org.churchpresenter.sharedui.screenshot.stackedThemes

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
        openProfilePage(ProfilePage.Content)
    }

    @Test
    fun `the Bible source menu open`() = shoot("bible_source_menu", rootIndex = 1) {
        openProfilePage(ProfilePage.Content)
        onNodeWithTag(BIBLE_SOURCE_TRIGGER_TAG).performClick()
    }

    @Test
    fun `a custom preview shape`() = shoot("custom_shape") {
        pickPreviewShape("CUSTOM")
    }

    @Test
    fun `a lower third`() = shoot("lower_third") { displayMode("Lower third") }

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
    fun `a stage monitor`() = shoot("stage_monitor") { displayMode("Stage monitor") }

    // ── The pages of the redesign ───────────────────────────────────────────────────────────────

    @Test
    fun `the Bible page`() = shoot("page_bible") { tab(CustomizePane.BIBLE) }

    @Test
    fun `the Bible page in Advanced`() = shoot("page_bible_advanced") {
        tab(CustomizePane.BIBLE)
        onNodeWithTag(DETAIL_ADVANCED_TAG).performClick()
    }

    @Test
    fun `the Outputs page`() = shoot("page_outputs") { openProfilePage(ProfilePage.Outputs) }

    @Test
    fun `the Stage layout page`() = shoot("page_stage_layout") {
        onNodeWithTag(profileRowTag("stage")).performClick()
        waitForIdle()
        tab(CustomizePane.STAGE_MONITOR)
    }

    @Test
    fun `one translation picked, with a size of its own`() = shoot("only_kjv") {
        tab(CustomizePane.BIBLE)
        onNodeWithTag(translationChipTag(0)).performClick()
        waitForIdle()
        onAllNodes(hasSetTextAction() and hasTestTag(TEXT_SIZE_FIELD_TAG), useUnmergedTree = true)[0]
            .performTextReplacement("50")
    }

    @Test
    fun `a song language picked`() = shoot("songs_language") {
        tab(CustomizePane.SONGS)
        onNodeWithTag(songLanguageTag(SongStyleLanguage.SECONDARY)).performClick()
    }

    // ── The list ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a profile's menu`() = shoot("list_menu", rootIndex = 1) {
        onNodeWithTag(profileRowTag("stream")).performMouseInput { rightClick(center) }
    }

    @Test
    fun `renaming in place`() = shoot("list_rename") {
        onNodeWithTag(profileRowTag("stream")).performMouseInput { rightClick(center) }
        waitForIdle()
        onAllNodes(hasTextExactly("Rename")).let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
    }

    // ── Linked profiles ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a follower's Bible page`() = shoot("linked_bible", settings = linkedLibrary()) {
        onNodeWithTag(profileRowTag("youth")).performClick()
        waitForIdle()
        tab(CustomizePane.BIBLE)
    }

    @Test
    fun `a follower's own values only`() = shoot("linked_only_changes", settings = linkedLibrary()) {
        onNodeWithTag(profileRowTag("youth")).performClick()
        waitForIdle()
        tab(CustomizePane.BIBLE)
        waitForIdle()
        onNodeWithTag(ONLY_CHANGES_TAG).performClick()
    }

    @Test
    fun `a follower's General page`() = shoot("linked_general", settings = linkedLibrary()) {
        onNodeWithTag(profileRowTag("youth")).performClick()
    }

    @Test
    fun `a master's General page`() = shoot("master_general", settings = linkedLibrary())

    @Test
    fun `a follower whose Bible follows another master`() =
        shoot("linked_section_masters", settings = sectionMastersLibrary()) {
            onNodeWithTag(profileRowTag("youth")).performClick()
        }

    // ── Folded groups ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `the Songs page with groups folded`() = shoot(
        "songs_folded",
        settings = library().copy(
            profilesFoldedGroups = mapOf(railTag(CustomizePane.SONGS.name) to setOf("background", "text")),
        ),
    ) { tab(CustomizePane.SONGS) }

    // ── Adjust on preview ───────────────────────────────────────────────────────────────────────

    @Test
    fun `the handles on a full screen`() = shoot("adjust_full_screen") {
        tab(CustomizePane.BIBLE)
        waitForIdle()
        onNodeWithTag(ADJUST_SWITCH_TAG).performClick()
    }

    @Test
    fun `the handles on a lower third`() = shoot("adjust_lower_third") {
        onNodeWithTag(profileRowTag("stream")).performClick()
        waitForIdle()
        tab(CustomizePane.BIBLE)
        waitForIdle()
        onNodeWithTag(ADJUST_SWITCH_TAG).performClick()
    }

    @Test
    fun `the large preview`() = shoot("large_preview", rootIndex = 1) {
        tab(CustomizePane.BIBLE)
        waitForIdle()
        onNodeWithTag(PREVIEW_LARGER_TAG).performClick()
    }

    // ── Follow-ups: languages, moving elements, what changed, Checker ───────────────────────────

    @Test
    fun `the Songs page names every language beside All`() = shoot("songs_all_languages") {
        tab(CustomizePane.SONGS)
        waitForIdle()
        onNodeWithTag(songLanguageTag(SongStyleLanguage.PRIMARY)).performClick()
    }

    @Test
    fun `every song element a block on the preview`() = shoot("songs_adjust_blocks") {
        tab(CustomizePane.SONGS)
        waitForIdle()
        onNodeWithTag(ADJUST_SWITCH_TAG).performClick()
    }

    @Test
    fun `a caption's text box turned on, with its handles`() = shoot("box_captions_adjust") {
        tab(CustomizePane.CAPTIONS)
        waitForIdle()
        toggleCheckbox("Text box")
        onNodeWithTag(ADJUST_SWITCH_TAG).performClick()
    }

    @Test
    fun `Adjust over a page with no box on yet`() = shoot("box_none_adjust") {
        tab(CustomizePane.QA)
        waitForIdle()
        onNodeWithTag(ADJUST_SWITCH_TAG).performClick()
    }

    @Test
    fun `the reference picked on the preview`() = shoot("adjust_reference") {
        tab(CustomizePane.BIBLE)
        waitForIdle()
        onNodeWithTag(ADJUST_SWITCH_TAG).performClick()
        waitForIdle()
        onNodeWithTag(ADJUST_REFERENCE_TAG).performClick()
    }

    @Test
    fun `what the profile changes from the defaults`() = shoot("defaults_card") {
        tab(CustomizePane.BIBLE)
        waitForIdle()
        onAllNodes(hasSetTextAction() and hasTestTag(TEXT_SIZE_FIELD_TAG), useUnmergedTree = true)[0]
            .performTextReplacement("50")
        waitForIdle()
        onNodeWithTag(translationChipTag(1)).performClick()
        waitForIdle()
        onAllNodes(hasSetTextAction() and hasTestTag(TEXT_SIZE_FIELD_TAG), useUnmergedTree = true)[0]
            .performTextReplacement("40")
    }

    @Test
    fun `Checker in place of a full screen's background`() = shoot("checker_full_screen") {
        tab(CustomizePane.BIBLE)
        waitForIdle()
        onNodeWithTag(previewBackgroundTag(PreviewBackgroundMode.CHECKER)).performClick()
    }

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

/**
     * [library] with Youth night following Sanctuary -- KJV at its own size, Q&A off -- and Easter
     * following it with nothing of its own.
     */
    private fun linkedLibrary(): AppSettings {
        val base = library()
        val bible = base.bibleSettings
        val youth = OutputProfile(
            id = "youth", name = "Youth night", parentId = "main", showQA = false,
            bibleSettings = bible.copy(
                translations = bible.translations.map {
                    if (it.fileName == "kjv.spb") it.copy(textFontSize = 50) else it
                },
            ),
            overrides = setOf("bibleSettings.translations[kjv.spb].textFontSize", "showQA"),
        )
        val easter = OutputProfile(id = "easter", name = "Easter", parentId = "main")
        val proj = base.projectionSettings
        val profiles = proj.outputProfiles + youth + easter
        return base.copy(projectionSettings = proj.copy(outputProfiles = profiles).withLinksResolved())
    }

    /** [linkedLibrary], with Youth night's Bible following Livestream and its captions its own. */
    private fun sectionMastersLibrary(): AppSettings {
        val base = linkedLibrary()
        val proj = base.projectionSettings
        val profiles = proj.outputProfiles.map {
            if (it.id == "youth") it.copy(sectionMasters = mapOf("bible" to "stream", "captions" to "")) else it
        }
        return base.copy(projectionSettings = proj.copy(outputProfiles = profiles).withLinksResolved())
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
