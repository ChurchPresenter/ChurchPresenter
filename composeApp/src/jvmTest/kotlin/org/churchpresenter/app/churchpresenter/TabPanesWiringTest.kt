@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.dictionary.DictionaryFixture
import org.churchpresenter.dictionary.data.InterlinearVerse
import org.churchpresenter.dictionary.data.InterlinearWord
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.schedule.ScheduleTabActions
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.Tabs
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal class TabPanesWiringTest : MainDesktopScopeHarness() {

    @AfterTest
    fun forgetScenes() {
        File(System.getProperty("user.home"), ".churchpresenter/scenes.json").delete()
    }

    private fun ComposeUiTest.press(description: String) {
        onAllNodesWithContentDescription(description)[0].performClick()
        waitForIdle()
    }

    @Test
    fun `an announcement schedules as a timer only with a mode other than duration or a length`() {
        val plain = AnnouncementsSettings()
        assertFalse(announcementIsTimer(plain))
        assertTrue(announcementIsTimer(plain.copy(timerHours = 1)))
        assertTrue(announcementIsTimer(plain.copy(timerMinutes = 1)))
        assertTrue(announcementIsTimer(plain.copy(timerSeconds = 1)))
        assertTrue(announcementIsTimer(plain.copy(timerMode = Constants.TIMER_MODE_CLOCK)))
    }

    @Test
    fun `the crossword pane is the crossword tab`() = pane(content = { TabContentPane(Tabs.CROSSWORD) }) {
        val shown = listOf("Loading puzzles…", "No crossword puzzles available yet.")
            .any { onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() } ||
            onAllNodesWithContentDescription("Next level").fetchSemanticsNodes().isNotEmpty()
        assertTrue(shown)
    }

    @Test
    fun `the companion surface pane asks for a host when none is set`() =
        pane(content = { TabContentPane(Tabs.COMPANION_SURFACE) }) {
            onNodeWithText("Set a Companion host", substring = true).assertExists()
        }

    @Test
    fun `a picture folder is added to the schedule and named as a preset`() {
        val folder = pictureFolder()
        val added = mutableListOf<Pair<String, Int>>()
        pane(
            actions = ScheduleTabActions(addPicture = { _, name, count -> added += name to count }),
            content = { TabContentPane(Tabs.PICTURES) },
        ) { scope ->
            scope.state.select(ScheduleItem.PictureItem("row", folder.absolutePath, folder.name, 3))
            waitUntil(timeoutMillis = 5_000) { scope.picturesViewModel.images.isNotEmpty() }
            waitForIdle()

            press("Add to Schedule")
            assertEquals(listOf(folder.name to 3), added)

            press("Save preset")
            val preset = assertIs<ScheduleItem.PictureItem>(scope.state.presetToSave)
            assertEquals(folder.name, preset.folderName)
            assertEquals(3, preset.imageCount)
        }
    }

    @Test
    fun `a presentation is added to the schedule and named as a preset`() {
        val deck = File(dir, "deck.pdf")
        PDDocument().use { doc ->
            repeat(2) { doc.addPage(PDPage()) }
            doc.save(deck)
        }
        val added = mutableListOf<Pair<String, String>>()
        pane(
            actions = ScheduleTabActions(addPresentation = { _, name, _, type -> added += name to type }),
            content = { TabContentPane(Tabs.PRESENTATION) },
        ) { scope ->
            scope.state.select(ScheduleItem.PresentationItem("row", deck.absolutePath, "deck", 2, "pdf"))
            waitUntil(timeoutMillis = 5_000) { scope.presentationViewModel.selectedPresentation != null }
            waitForIdle()

            press("Add to Schedule")
            assertEquals(listOf("deck" to "pdf"), added)

            press("Save preset")
            val preset = assertIs<ScheduleItem.PresentationItem>(scope.state.presetToSave)
            assertEquals("deck", preset.fileName)
            assertEquals("pdf", preset.fileType)
        }
    }

    @Test
    fun `a scene is added, named as a preset and put live`() {
        val added = mutableListOf<String>()
        val manager = PresenterManager()
        pane(
            actions = ScheduleTabActions(addScene = { _, name -> added += name }),
            presenterManager = manager,
            content = { TabContentPane(Tabs.CANVAS) },
        ) { scope ->
            if (scope.sceneViewModel.currentScene == null) {
                onNodeWithText("Create Scene").performClick()
                waitForIdle()
            }
            val scene = checkNotNull(scope.sceneViewModel.currentScene)

            press("Add to Schedule")
            assertEquals(listOf(scene.name), added)

            press("Save preset")
            val preset = assertIs<ScheduleItem.SceneItem>(scope.state.presetToSave)
            assertEquals(scene.id, preset.sceneId)

            press("Go Live")
            assertEquals(scene.id, manager.activeScene.value?.id)
            assertTrue(manager.isLive(Presenting.CANVAS))
        }
    }

    @Test
    fun `a lower third is added, put live and handed to the generator`() {
        val folder = File(dir, "lower-thirds").apply { mkdirs() }
        File(folder, "Pastor.json")
            .writeText("""{"v":"5.7.4","fr":30,"ip":0,"op":30,"w":1920,"h":1080,"layers":[]}""")
        val settings = settings().let {
            it.copy(streamingSettings = it.streamingSettings.copy(lowerThirdFolder = folder.absolutePath))
        }
        val added = mutableListOf<String>()
        val generated = mutableListOf<String>()
        val manager = PresenterManager()
        pane(
            appSettings = settings,
            actions = ScheduleTabActions(addLowerThird = { id, _, _, _ -> added += id }),
            presenterManager = manager,
            onOpenLottieGen = { outputDir, _ -> generated += outputDir },
            content = { TabContentPane(Tabs.LOWER_THIRD) },
        ) { scope ->
            scope.state.select(ScheduleItem.LowerThirdItem("row", "Pastor", "Pastor", false, 0L))
            waitForIdle()

            press("Add to Schedule")
            assertEquals(listOf("Pastor"), added)

            press("Go Live")
            assertEquals("Pastor", manager.currentLowerThirdName.value)
            assertTrue(manager.isLive(Presenting.LOWER_THIRD))

            onNodeWithText("Generate").performClick()
            waitForIdle()
            assertEquals(listOf(folder.absolutePath), generated)
        }
    }

    @Test
    fun `an announcement is added as text and as a timer, and named as a preset`() {
        val timed = settings().copy(
            announcementsSettings = AnnouncementsSettings(text = "Starting soon", timerMinutes = 5),
        )
        val added = mutableListOf<Pair<String, Boolean>>()
        pane(
            appSettings = timed,
            actions = ScheduleTabActions(
                addAnnouncement = { text, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, isTimer,
                    _, _, _, _, _, _, _, _, _, _, _, _ ->
                    added += text to isTimer
                },
            ),
            content = { TabContentPane(Tabs.ANNOUNCEMENTS) },
        ) { scope ->
            val buttons = onAllNodesWithContentDescription("Add to Schedule")
            buttons[0].performClick()
            buttons[buttons.fetchSemanticsNodes().size - 1].performClick()
            waitForIdle()
            assertEquals(listOf("Starting soon" to false, "Starting soon" to true), added)

            press("Save preset")
            val preset = assertIs<ScheduleItem.AnnouncementItem>(scope.state.presetToSave)
            assertEquals("Starting soon", preset.text)
        }
    }

    @Test
    fun `a dictionary entry is added to the schedule and put live`() {
        val added = mutableListOf<String>()
        val presented = mutableListOf<Presenting>()
        val manager = PresenterManager()
        pane(
            actions = ScheduleTabActions(addDictionary = { number, _, _, _ -> added += number }),
            presenterManager = manager,
            live = noLive().copy(presenting = { presented += it }),
            content = { TabContentPane(Tabs.DICTIONARY) },
        ) { scope ->
            openAgape(scope)

            press("Add to Schedule")
            assertEquals(listOf("G26"), added)

            press("Go Live")
            assertEquals("G26", manager.displayedDictionaryEntry.value?.number)
            assertEquals(listOf(Presenting.DICTIONARY), presented)
        }
    }

    @Test
    fun `a verse the entry appears in opens the Bible tab, and a word in it is looked up`() {
        val verse = InterlinearVerse(
            ref = "043003016",
            words = listOf(
                InterlinearWord(text = "agape-word", strongsNumber = "G26"),
                InterlinearWord(text = "charis-word", strongsNumber = "G5485"),
            ),
        )
        val interlinear = Json.encodeToString(ListSerializer(InterlinearVerse.serializer()), listOf(verse))
        pane(
            dictionaryFiles = DictionaryFixture.files(interlinearGreek = interlinear),
            content = { TabContentPane(Tabs.DICTIONARY) },
        ) { scope ->
            openAgape(scope)
            waitUntil(timeoutMillis = 5_000) { scope.dictionaryViewModel.interlinearVerses.isNotEmpty() }
            waitForIdle()
            scope.state.selectedTabIndex = Tabs.entries.indexOf(Tabs.DICTIONARY)

            onNodeWithText("Book 43 3:16").performClick()
            waitForIdle()
            assertEquals(Tabs.BIBLE, scope.currentTab)

            onNodeWithText("charis-word").performClick()
            waitForIdle()
            assertEquals("G5485", scope.dictionaryViewModel.selectedEntry?.number)
        }
    }

    private fun ComposeUiTest.openAgape(scope: MainDesktopScope) {
        waitUntil(timeoutMillis = 5_000) { scope.dictionaryViewModel.entries.isNotEmpty() }
        scope.dictionaryViewModel.selectByNumber("G26")
        waitForIdle()
    }
}
