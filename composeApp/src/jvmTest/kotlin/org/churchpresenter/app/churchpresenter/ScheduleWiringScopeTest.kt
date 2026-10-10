@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.Tabs
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ScheduleWiringScopeTest : WiringScopeHarness() {

    @AfterTest
    fun forgetScenes() {
        File(System.getProperty("user.home"), ".churchpresenter/scenes.json").delete()
    }

    private val songsIndex = Tabs.entries.indexOf(Tabs.SONGS)

    @Test
    fun `a label row opens its editor and leaves the tab where it was`() {
        val label = ScheduleItem.LabelItem("label", "Welcome", "#FFFFFF", "#000000")
        scoped(ScopeInputs(settings(), selectedTabIndex = songsIndex)) { scope ->
            runOnIdle { scope.openScheduleItem(label) }
            assertEquals(label, scope.state.editingLabelItem)
            assertTrue(scope.state.showAddLabelDialog)
            assertEquals(songsIndex, scope.state.selectedTabIndex)
        }
    }

    @Test
    fun `a song row is handed to the Songs tab and opens no editor`() {
        val song = ScheduleItem.SongItem("song", 1, "A Test Song", "Hymnal")
        scoped { scope ->
            runOnIdle { scope.openScheduleItem(song) }
            assertEquals(song, scope.state.selectedSongItem)
            assertEquals(songsIndex, scope.state.selectedTabIndex)
            assertNull(scope.state.editingLabelItem)
            assertFalse(scope.state.showAddLabelDialog)
        }
    }

    @Test
    fun `an announcement row loads its text into the settings`() {
        val changes = mutableListOf<(AppSettings) -> AppSettings>()
        val announcement = ScheduleItem.AnnouncementItem("announcement", "Coffee after the service")
        scoped(ScopeInputs(settings(), onSettingsChange = { changes += it })) { scope ->
            runOnIdle { scope.openScheduleItem(announcement) }
            assertEquals(1, changes.size)
            assertEquals("Coffee after the service", changes.single()(settings()).announcementsSettings.text)
            assertEquals(Tabs.entries.indexOf(Tabs.ANNOUNCEMENTS), scope.state.selectedTabIndex)
        }
    }

    @Test
    fun `a scene row selects its scene without putting it live`() {
        val manager = PresenterManager()
        scoped(ScopeInputs(settings(), presenterManager = manager)) { scope ->
            val first = runOnIdle { scope.sceneViewModel.addScene("First") }
            runOnIdle { scope.sceneViewModel.addScene("Second") }
            runOnIdle { scope.openScheduleItem(ScheduleItem.SceneItem("row", first.id, first.name)) }
            assertEquals(first.id, scope.sceneViewModel.currentSceneId.value)
            assertNull(manager.activeScene.value)
            assertEquals(Tabs.entries.indexOf(Tabs.CANVAS), scope.state.selectedTabIndex)
        }
    }

    @Test
    fun `a dictionary row selects its entry`() {
        scoped { scope ->
            runOnIdle {
                scope.openScheduleItem(ScheduleItem.DictionaryItem("row", "g26", "agape", "agape", "love"))
                // What the Dictionary tab does when it comes on screen.
                scope.dictionaryViewModel.load()
            }
            waitUntil(timeoutMillis = 5_000) { scope.dictionaryViewModel.selectedEntry?.number == "G26" }
            assertEquals(Tabs.entries.indexOf(Tabs.DICTIONARY), scope.state.selectedTabIndex)
        }
    }

    @Test
    fun `a cue row opens nothing and changes nothing`() {
        val changes = mutableListOf<(AppSettings) -> AppSettings>()
        scoped(ScopeInputs(settings(), onSettingsChange = { changes += it }, selectedTabIndex = songsIndex)) { scope ->
            runOnIdle { scope.openScheduleItem(ScheduleItem.CueItem("cue", "clear")) }
            assertEquals(songsIndex, scope.state.selectedTabIndex)
            assertTrue(changes.isEmpty())
            assertNull(scope.state.editingLabelItem)
            assertFalse(scope.state.showAddLabelDialog)
        }
    }

    @Test
    fun `presenting a scene puts that one live, not its neighbor`() {
        val manager = PresenterManager()
        val presented = mutableListOf<Presenting>()
        val live = LiveOutputCallbacks(presenting = { presented += it }, onVerseSelected = {}, onSongItemSelected = {})
        scoped(ScopeInputs(settings(), presenterManager = manager, live = live)) { scope ->
            runOnIdle { scope.sceneViewModel.addScene("First") }
            val second = runOnIdle { scope.sceneViewModel.addScene("Second") }
            runOnIdle { scope.presentScene(second.id) }
            assertEquals(second.id, manager.activeScene.value?.id)
            assertEquals(second.id, scope.sceneViewModel.currentSceneId.value)
            assertEquals(Tabs.entries.indexOf(Tabs.CANVAS), scope.state.selectedTabIndex)
            assertEquals(listOf(Presenting.CANVAS), presented)
        }
    }

    @Test
    fun `presenting a scene that no longer exists clears the active scene`() {
        val manager = PresenterManager()
        scoped(ScopeInputs(settings(), presenterManager = manager)) { scope ->
            val kept = runOnIdle { scope.sceneViewModel.addScene("Kept") }
            runOnIdle { scope.presentScene(kept.id) }
            runOnIdle { scope.presentScene("deleted-scene") }
            assertNull(manager.activeScene.value)
        }
    }

    @Test
    fun `a cue's slideshow starts the loaded clip`() {
        var actions = ScheduleActions()
        val media = MediaViewModel()
        val clip = File(dir, "clip.mp4").absolutePath
        val inputs = ScopeInputs(
            settings(),
            publish = MainDesktopPublishers(onScheduleActionsReady = { actions = it }),
            media = media,
        )
        scoped(inputs, content = { ScheduleTabPane() }) {
            runOnIdle { media.loadMediaFromSchedule(clip, "Clip", "local") }
            assertFalse(media.isPlaying)
            runOnIdle { actions.playSlideshow(ScheduleItem.MediaItem("row", clip, "Clip", "local"), 2) }
            assertTrue(media.isPlaying)
        }
    }

    @Test
    fun `a clip's slideshow without a media player, or a song's, starts nothing`() {
        var actions = ScheduleActions()
        val inputs = ScopeInputs(settings(), publish = MainDesktopPublishers(onScheduleActionsReady = { actions = it }))
        scoped(inputs, content = { ScheduleTabPane() }) { scope ->
            val clip = File(dir, "clip.mp4").absolutePath
            runOnIdle {
                actions.playSlideshow(ScheduleItem.MediaItem("row", clip, "Clip", "local"), 1)
                actions.playSlideshow(ScheduleItem.SongItem("song", 1, "A Test Song", "Hymnal"), 1)
            }
            assertFalse(scope.picturesViewModel.isPlaying)
            assertFalse(scope.presentationViewModel.isPlaying)
        }
    }
}
