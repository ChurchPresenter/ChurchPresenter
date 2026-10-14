@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.happypath

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.churchpresenter.calendar.PresetStore
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.PictureSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.sharedui.utils.UsageEvent
import org.churchpresenter.sharedui.utils.UsageEvents
import org.churchpresenter.slides.data.RecentPresentationFiles
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val CHESED = "chêçêd"

class TabPanesHappyPathTest : HappyPathHarness() {

    private var savedRecent = emptyList<String>()

    private val scenesFile get() = File(System.getProperty("user.home"), ".churchpresenter/scenes.json")

    @BeforeTest
    fun saveRecentDecks() {
        savedRecent = RecentPresentationFiles.files.toList()
        RecentPresentationFiles.files.clear()
        scenesFile.delete()
    }

    @AfterTest
    fun restoreRecentDecks() {
        RecentPresentationFiles.files.clear()
        RecentPresentationFiles.files.addAll(savedRecent)
        scenesFile.delete()
    }

    private fun calendar() = File(dir, "calendar").apply { mkdirs() }

    private fun presets(): List<ScheduleItem> = PresetStore(calendar()).load().presets.map { it.item }

    private fun withCalendar(settings: AppSettings) = settings.copy(calendarStorageDirectory = calendar().absolutePath)

    private fun ComposeUiTest.savePreset() {
        clickTabControl("Save preset")
        clickText("OK")
        await("the preset is written") { presets().isNotEmpty() }
    }

    private fun pictures(): AppSettings {
        val folder = File(dir, "Sunday Photos").apply { mkdirs() }
        repeat(2) {
            ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", File(folder, "image-$it.png"))
        }
        return withCalendar(settings(Tabs.PICTURES))
            .copy(pictureSettings = PictureSettings(storageDirectory = folder.absolutePath))
    }

    private fun deck(): File = File(dir, "Sermon.pdf").also { file ->
        PDDocument().use { doc ->
            repeat(2) { doc.addPage(PDPage(PDRectangle(720f, 405f))) }
            doc.save(file)
        }
    }

    private fun ComposeUiTest.openDeck(deck: File) {
        onAllNodes(hasText(deck.name))[0].performMouseInput { doubleClick() }
        waitForIdle()
        await("the deck's slides are tiled", timeoutMs = 15_000) {
            onAllNodes(hasContentDescription("Slide 2"))
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }

    private fun announcing(announcement: AnnouncementsSettings) =
        settings(Tabs.ANNOUNCEMENTS).copy(announcementsSettings = announcement.copy(text = "Welcome"))

    private fun ComposeUiTest.findChesed() {
        onAllNodes(hasSetTextAction())[0].performTextReplacement("H2617")
        waitForIdle()
        await("H2617 is listed") {
            onAllNodes(hasText(CHESED, substring = true)).fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        onAllNodes(hasText(CHESED, substring = true))[0].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.awaitVerses() {
        await("a verse is listed", timeoutMs = 15_000) {
            onAllNodes(hasText(CHESED, substring = true))[0].performClick()
            waitForIdle()
            verseReferences().fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun ComposeUiTest.verseReferences() =
        onAllNodes(hasClickAction() and hasText(":", substring = true) and !hasText(CHESED, substring = true))

    @Test
    fun `a picture folder is saved as a preset`() = happyPath(pictures()) { _ ->
        savePreset()

        val item = presets().single() as ScheduleItem.PictureItem
        assertEquals("Sunday Photos", item.folderName)
        assertEquals(2, item.imageCount)
    }

    @Test
    fun `a deck is saved as a preset`() {
        val deck = deck()
        RecentPresentationFiles.files.add(deck.absolutePath)
        happyPath(withCalendar(settings(Tabs.PRESENTATION))) { _ ->
            openDeck(deck)
            savePreset()

            val item = presets().single() as ScheduleItem.PresentationItem
            assertEquals(deck.absolutePath, item.filePath)
            assertEquals(2, item.slideCount)
        }
    }

    @Test
    fun `a scene is added to the schedule and saved as a preset`() =
        happyPath(withCalendar(settings(Tabs.CANVAS))) { app ->
            clickText("Create Scene")
            clickTabControl("Add to Schedule")
            await("the schedule holds the scene") { app.schedule().isNotEmpty() }

            savePreset()

            val scheduled = app.schedule().single() as ScheduleItem.SceneItem
            val preset = presets().single() as ScheduleItem.SceneItem
            assertEquals(scheduled.sceneId, preset.sceneId)
        }

    @Test
    fun `a dictionary entry is added to the schedule`() = happyPath(settings(Tabs.DICTIONARY)) { app ->
        findChesed()
        clickTabControl("Add to Schedule")

        await("the schedule holds the entry") { app.schedule().isNotEmpty() }
        assertEquals("H2617", (app.schedule().single() as ScheduleItem.DictionaryItem).number)
    }

    @Test
    fun `a verse reference in the dictionary opens the Bible tab`() =
        happyPath(settings(Tabs.DICTIONARY, Tabs.BIBLE)) { _ ->
            selectTab("Dictionary")
            findChesed()
            awaitVerses()

            verseReferences()[0].performClick()
            waitForIdle()

            await("the dictionary is no longer shown") {
                onAllNodes(hasText(CHESED, substring = true)).fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isEmpty()
            }
        }

    @Test
    fun `a word in a verse looks its own entry up`() = happyPath(settings(Tabs.DICTIONARY)) { _ ->
        findChesed()
        awaitVerses()
        val before = UsageEvents.unreported()[UsageEvent.STRONGS_LOOKUP] ?: 0
        val versesTop = onAllNodes(hasText("Found in", substring = true)).fetchSemanticsNodes().first().boundsInRoot.top
        val words = onAllNodes(hasClickAction() and isEnabled())
        val other = words.fetchSemanticsNodes().indexOfFirst { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
            node.boundsInRoot.top > versesTop && text.any { it in '\u0590'..'\u05FF' }
        }

        words[other].performClick()
        waitForIdle()

        assertTrue((UsageEvents.unreported()[UsageEvent.STRONGS_LOOKUP] ?: 0) > before)
    }

    @Test
    fun `a clock announcement is scheduled as a timer`() =
        happyPath(announcing(AnnouncementsSettings(timerMode = Constants.TIMER_MODE_CLOCK))) { app ->
            assertScheduledTimer(app, expected = true)
        }

    @Test
    fun `a duration in hours is scheduled as a timer`() =
        happyPath(announcing(AnnouncementsSettings(timerHours = 1))) { app ->
            assertScheduledTimer(app, expected = true)
        }

    @Test
    fun `a duration in minutes is scheduled as a timer`() =
        happyPath(announcing(AnnouncementsSettings(timerMinutes = 1))) { app ->
            assertScheduledTimer(app, expected = true)
        }

    @Test
    fun `a duration in seconds is scheduled as a timer`() =
        happyPath(announcing(AnnouncementsSettings(timerSeconds = 1))) { app ->
            assertScheduledTimer(app, expected = true)
        }

    @Test
    fun `an announcement with no duration is not a timer`() =
        happyPath(announcing(AnnouncementsSettings())) { app ->
            assertScheduledTimer(app, expected = false)
        }

    private fun ComposeUiTest.assertScheduledTimer(app: HappyApp, expected: Boolean) {
        val adds = onAllNodes(hasContentDescription("Add to Schedule") and isEnabled())
        val shown = adds.fetchSemanticsNodes().withIndex().filter { it.value.boundsInRoot.width > 0f }
        adds[shown.last().index].performClick()
        waitForIdle()
        await("the schedule holds the announcement") { app.schedule().isNotEmpty() }
        val item = app.schedule().single() as ScheduleItem.AnnouncementItem
        assertEquals(expected, item.isTimer)
        assertTrue(item.text.contains("Welcome"))
    }
}
