@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import org.churchpresenter.canvas.SceneViewModel
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.schedule.SCHEDULE_ROW_CARD_TAG
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.Tabs
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MainDesktopPaneCallbacksTest : MainDesktopComposeHarness() {

    private fun Wiring.applied(base: AppSettings): AppSettings = settingsChanges.fold(base) { s, change -> change(s) }

    private fun ComposeUiTest.chooseOption(tag: String) {
        if (onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) {
            onNodeWithTag("schedule_options").performClick()
            waitForIdle()
        }
        onNodeWithTag(tag).performClick()
        waitForIdle()
    }

    @Test
    fun `the schedule options switch the thumbnails and the live highlight`() {
        val wiring = Wiring()
        val base = withOneSong()
        root(base, wiring = wiring) { _ ->
            chooseOption("schedule_options_thumbnails")
            assertEquals(!base.scheduleThumbnails, wiring.settingsChanges.last()(base).scheduleThumbnails)
            chooseOption("schedule_options_highlight_live")
            assertEquals(!base.scheduleHighlightLive, wiring.settingsChanges.last()(base).scheduleHighlightLive)
        }
        val applied = wiring.applied(base)
        assertEquals(!base.scheduleThumbnails, applied.scheduleThumbnails)
        assertEquals(!base.scheduleHighlightLive, applied.scheduleHighlightLive)
    }

    @Test
    fun `a picture added from its menu becomes a schedule row of its own`() {
        val folder = pictureFolder()
        val wiring = Wiring()
        root(withOneSong(), wiring = wiring) { actions ->
            actions.addPicture(folder.absolutePath, folder.name, 3)
            waitForIdle()
            onAllNodes(hasText(folder.name, substring = true) and hasAnyAncestor(hasTestTag(SCHEDULE_ROW_CARD_TAG)))[0]
                .performMouseInput { click(Offset(4f, centerY)) }
            waitUntil("the folder's pictures drawn", 5_000) {
                onAllNodesWithContentDescription("image-1.png").fetchSemanticsNodes().isNotEmpty()
            }
            val rowsBefore = wiring.scheduleChanged.last()
            onAllNodesWithContentDescription("image-1.png")[0].performMouseInput { rightClick(center) }
            waitForIdle()
            onAllNodesWithText("Add to Schedule").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
            waitForIdle()
            waitUntil("the picture scheduled", 5_000) { wiring.scheduleChanged.last() == rowsBefore + 1 }
            onAllNodes(hasText("image-1", substring = true) and hasAnyAncestor(hasTestTag(SCHEDULE_ROW_CARD_TAG)))
                .fetchSemanticsNodes().let { assertTrue(it.isNotEmpty(), "the row reads the picture's name") }
        }
    }

    private fun ComposeUiTest.clickRow(label: String) {
        onAllNodes(hasText(label, substring = true) and hasAnyAncestor(hasTestTag(SCHEDULE_ROW_CARD_TAG)))[0]
            .performMouseInput { click(Offset(4f, centerY)) }
        waitForIdle()
    }

    @Test
    fun `a row opened again on its own tab stays on that tab`() {
        val folder = pictureFolder()
        val wiring = Wiring()
        root(withOneSong(), wiring = wiring) { actions ->
            actions.addSong(1, "A Test Song", "Hymnal", "Hymnal::1")
            actions.addBibleVerse("John", 3, 16, "For God so loved", "", 43)
            actions.addPicture(folder.absolutePath, "Pictures", 3)
            actions.addPresentation(File(dir, "deck.pptx").absolutePath, "deck", 0, "pptx")
            actions.addMedia(File(dir, "clip.mp4").toURI().toString(), "Clip", "local", "")
            actions.addLowerThird("pastor", "Pastor", false, 0)
            actions.addWebsite("http://example.invalid", "A Site")
            waitForIdle()
            val visible = computeVisibleTabs(withOneSong().hiddenTabs, false, false)
            listOf(
                "A Test Song" to Tabs.SONGS,
                "John" to Tabs.BIBLE,
                "Pictures" to Tabs.PICTURES,
                "deck" to Tabs.PRESENTATION,
                "Clip" to Tabs.MEDIA,
                "Pastor" to Tabs.LOWER_THIRD,
                "A Site" to Tabs.WEB,
            ).forEach { (label, tab) ->
                clickRow(label)
                clickRow(label)
                assertEquals(tab, visible[wiring.tabChanges.last()], label)
            }
        }
    }

    @Test
    fun `with the live highlight on, a row taken live and then left marks where the service left off`() {
        val base = withOneSong().copy(scheduleHighlightLive = true)
        root(base) { actions ->
            actions.addSong(1, "A Test Song", "Hymnal", "Hymnal::1")
            waitForIdle()
            takeLive("A Test Song")
            waitUntil("the row is marked", 5_000) {
                onAllNodesWithText("Left off", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun `presenting a scene that is gone takes nothing live but still opens the canvas`() {
        val scenes = File(System.getProperty("user.home"), ".churchpresenter/scenes.json")
        scenes.delete()
        val scene = SceneViewModel().addScene("Opening")
        val manager = PresenterManager()
        val wiring = Wiring()
        try {
            root(withOneSong(), wiring = wiring, presenterManager = manager) { actions ->
                actions.presentScene(scene.id)
                waitForIdle()
                assertEquals(scene.id, manager.activeScene.value?.id)
                actions.presentScene("no-such-scene")
                waitForIdle()
                assertNull(manager.activeScene.value)
            }
        } finally {
            scenes.delete()
        }
        val visible = computeVisibleTabs(withOneSong().hiddenTabs, false, false)
        assertEquals(Tabs.CANVAS, visible[wiring.tabChanges.last()])
    }

    @Test
    fun `a media row's slideshow waits for its clip instead of playing what is loaded`() {
        val media = MediaViewModel()
        root(withOneSong(), media = media) { actions ->
            actions.playSlideshow(
                ScheduleItem.MediaItem(id = "m", mediaUrl = File(dir, "clip.mp4").toURI().toString(),
                    mediaTitle = "Clip", mediaType = "local"),
                2,
            )
            waitForIdle()
            assertFalse(media.isPlaying)
        }
    }

    @Test
    fun `a media row's slideshow with no player is ignored`() {
        val manager = PresenterManager()
        root(withOneSong(), presenterManager = manager) { actions ->
            actions.playSlideshow(
                ScheduleItem.MediaItem(
                    id = "m", mediaUrl = "file:///clip.mp4", mediaTitle = "Clip", mediaType = "local",
                ),
                1,
            )
            waitForIdle()
            assertFalse(manager.isLive(Presenting.MEDIA))
        }
    }

    @Test
    fun `the lower third's Generate, the background and the settings keys work with no handler given`() {
        root(showingOnly(Tabs.LOWER_THIRD)) { _ ->
            onNodeWithText("Generate").performClick()
            waitForIdle()
            onNodeWithContentDescription("Background").performClick()
            waitForIdle()
            onNodeWithContentDescription("Settings").performClick()
            waitForIdle()
            onNodeWithText("Generate").assertExists()
        }
    }
}
