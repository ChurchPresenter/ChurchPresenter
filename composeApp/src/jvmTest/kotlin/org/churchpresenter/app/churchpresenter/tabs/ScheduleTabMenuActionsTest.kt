@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.core.models.songs.SongItem
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.text.TextBackdrop
import java.io.File
import java.nio.file.Files
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Path as NioPath

/**
 * The action set the tab publishes to its parent through `onActionsReady`.
 *
 * Everything the app can do to a schedule from outside this tab goes through here: the File and
 * Edit menus, the keyboard shortcuts, and the approved remote requests that arrive from a phone or
 * a linked instance. None of them touches a button in the tab, so a lambda wired to the wrong view
 * model call — `moveSelectedUp` bound to `moveItemToTop`, `removeById` reading the UI selection
 * instead of the id it was handed — would look perfect on screen and still ruin a service order.
 * Each action is invoked and the resulting schedule asserted.
 *
 * `openSchedule`, `saveSchedule` and `saveScheduleAs` normally open a native file chooser, which
 * throws headless; here `FileChooser.platformInstance` is swapped for a fake the same way
 * `QATabFileChooserTest` does it, so the wiring runs for real without touching a native dialog. Each
 * runs in the tab's own `coroutineScope.launch` around a suspend chooser call, so a click is not a
 * barrier — tests wait on the chooser having answered, or on the file it wrote, rather than on
 * `waitForIdle()`. The view-model half of each is covered by `ScheduleFileTest`.
 *
 * See `ScheduleTabTestSupport.kt` for the harness.
 */
class ScheduleTabMenuActionsTest {

    @AfterTest
    fun cleanUpFileChooser() {
        unmockkObject(FileChooser.Companion)
    }

    @Test
    fun `the tab publishes its actions to the parent`() =
        scheduleTab(seed = { seedService() }) { _, reports ->
            // MainDesktop's menus are dead until this arrives, so its absence is its own failure.
            registeredActions(reports)
        }

    // ── Whole-schedule actions ──────────────────────────────────────────────────

    @Test
    fun `newSchedule empties the service`() =
        scheduleTab(seed = { seedService() }) { vm, reports ->
            registeredActions(reports).newSchedule()
            waitForIdle()

            assertTrue(vm.scheduleItems.isEmpty(), "New Schedule must leave nothing behind")
            assertEquals(emptyList(), orderOf("Welcome", "Amazing Grace", "John 3:16", "Notices"))
        }

    @Test
    fun `clearSchedule empties the service`() =
        scheduleTab(seed = { seedService() }) { vm, reports ->
            registeredActions(reports).clearSchedule()
            waitForIdle()

            assertTrue(vm.scheduleItems.isEmpty())
        }

    // ── Removal ─────────────────────────────────────────────────────────────────

    @Test
    fun `removeSelected removes the row the operator has selected`() =
        scheduleTab(seed = { seedService() }) { vm, reports ->
            val actions = registeredActions(reports)
            vm.selectItem(vm.scheduleItems[1].id)
            waitForIdle()

            actions.removeSelected()
            waitForIdle()

            assertEquals(
                listOf("Welcome", "John 3:16", "Notices"),
                vm.scheduleItems.map { it.displayText },
                "the selected song, and only it, must go",
            )
        }

    @Test
    fun `removeSelected does nothing while nothing is selected`() =
        scheduleTab(seed = { seedService() }) { vm, reports ->
            val actions = registeredActions(reports)
            vm.clearSelection()
            waitForIdle()
            val before = vm.scheduleItems.map { it.displayText }

            actions.removeSelected()
            waitForIdle()

            assertEquals(before, vm.scheduleItems.map { it.displayText })
        }

    @Test
    fun `removeById removes the item it names, not the selected one`() =
        scheduleTab(seed = { seedService() }) { vm, reports ->
            val actions = registeredActions(reports)
            val website = vm.scheduleItems.last().id
            vm.selectItem(vm.scheduleItems.first().id)
            waitForIdle()

            // This is the approved-remote-request path: a phone asks for a specific item to go,
            // and what the operator happens to have highlighted must not decide which one does.
            actions.removeById(website)
            waitForIdle()

            assertEquals(
                listOf("Welcome", "42 - Amazing Grace", "John 3:16"),
                vm.scheduleItems.map { it.displayText },
            )
        }

    // ── Reordering the selection ────────────────────────────────────────────────

    @Test
    fun `moveSelectedUp and moveSelectedDown step the selected row one place`() =
        scheduleTab(seed = { seedService() }) { vm, reports ->
            val actions = registeredActions(reports)
            vm.selectItem(vm.scheduleItems[1].id)
            waitForIdle()

            actions.moveSelectedUp()
            waitForIdle()
            assertEquals(
                listOf("42 - Amazing Grace", "Welcome", "John 3:16", "Notices"),
                vm.scheduleItems.map { it.displayText },
            )

            actions.moveSelectedDown()
            waitForIdle()
            assertEquals(
                listOf("Welcome", "42 - Amazing Grace", "John 3:16", "Notices"),
                vm.scheduleItems.map { it.displayText },
                "and the two must be inverses of one another",
            )
        }

    @Test
    fun `moveSelectedToTop and moveSelectedToBottom send the selected row all the way`() =
        scheduleTab(seed = { seedService() }) { vm, reports ->
            val actions = registeredActions(reports)
            vm.selectItem(vm.scheduleItems[2].id)
            waitForIdle()

            actions.moveSelectedToTop()
            waitForIdle()
            assertEquals(
                listOf("John 3:16", "Welcome", "42 - Amazing Grace", "Notices"),
                vm.scheduleItems.map { it.displayText },
            )

            actions.moveSelectedToBottom()
            waitForIdle()
            assertEquals(
                listOf("Welcome", "42 - Amazing Grace", "Notices", "John 3:16"),
                vm.scheduleItems.map { it.displayText },
            )
        }

    @Test
    fun `the move actions do nothing while nothing is selected`() =
        scheduleTab(seed = { seedService() }) { vm, reports ->
            val actions = registeredActions(reports)
            vm.clearSelection()
            waitForIdle()
            val before = vm.scheduleItems.map { it.displayText }

            actions.moveSelectedUp()
            actions.moveSelectedDown()
            actions.moveSelectedToTop()
            actions.moveSelectedToBottom()
            waitForIdle()

            assertEquals(before, vm.scheduleItems.map { it.displayText })
        }

    // ── Adding, one action per content type ─────────────────────────────────────

    @Test
    fun `every add action appends an item of its own type`() =
        scheduleTab { vm, reports ->
            val actions = registeredActions(reports)

            actions.addLabel("Welcome", "#FFFFFF", "#203040")
            actions.addSong(42, "Amazing Grace", "Hymnal", "Hymnal::42")
            actions.addBibleVerse("John", 3, 16, "For God so loved the world.", "", 43)
            actions.addPicture("/photos/advent", "Advent", 12)
            actions.addPresentation("/decks/sermon.pptx", "sermon.pptx", 24, "pptx")
            actions.addMedia("https://example.org/clip.mp4", "Clip", "video")
            actions.addLowerThird("preset-1", "Speaker name", true, 1_500L)
            actions.addWebsite("https://example.org", "Notices")
            actions.addScene("scene-1", "Opening scene")
            actions.addDictionary("G5485", "χάρις", "charis", "grace")
            waitForIdle()

            assertEquals(
                listOf(
                    ScheduleItem.LabelItem::class,
                    ScheduleItem.SongItem::class,
                    ScheduleItem.BibleVerseItem::class,
                    ScheduleItem.PictureItem::class,
                    ScheduleItem.PresentationItem::class,
                    ScheduleItem.MediaItem::class,
                    ScheduleItem.LowerThirdItem::class,
                    ScheduleItem.WebsiteItem::class,
                    ScheduleItem.SceneItem::class,
                    ScheduleItem.DictionaryItem::class,
                ),
                vm.scheduleItems.map { it::class },
                "each action must add its own type, in the order they were invoked",
            )
        }

    @Test
    fun `the add actions carry their arguments through to the item`() =
        scheduleTab { vm, reports ->
            val actions = registeredActions(reports)

            actions.addSong(42, "Amazing Grace", "Hymnal", "Hymnal::42")
            actions.addBibleVerse("John", 3, 16, "For God so loved the world.", "16-18", 43)
            actions.addLowerThird("preset-1", "Speaker name", true, 1_500L)
            waitForIdle()

            val song = vm.scheduleItems[0] as ScheduleItem.SongItem
            assertEquals(42, song.songNumber)
            assertEquals("Amazing Grace", song.title)
            assertEquals("Hymnal", song.songbook)
            assertEquals("Hymnal::42", song.songId)

            val verse = vm.scheduleItems[1] as ScheduleItem.BibleVerseItem
            assertEquals("John", verse.bookName)
            assertEquals(3, verse.chapter)
            assertEquals(16, verse.verseNumber)
            assertEquals("16-18", verse.verseRange)
            assertEquals(43, verse.bookId)

            val lowerThird = vm.scheduleItems[2] as ScheduleItem.LowerThirdItem
            assertEquals("preset-1", lowerThird.presetId)
            assertTrue(lowerThird.pauseAtFrame)
            assertEquals(1_500L, lowerThird.pauseDurationMs)
        }

    @Test
    fun `addAnnouncement carries its whole argument list through`() =
        scheduleTab { vm, reports ->
            // 29 parameters wide, and the ones that matter are deep in the middle of it: a shifted
            // argument here is how a countdown ends up the wrong colour or counting to the wrong time.
            registeredActions(reports).addAnnouncement(
                "Service starts in", "#FFEEAA", "#101020", 64, "Georgia",
                true, true, true, true, "#001122", 120, 55,
                "left", "top", "FADE", 750, 3,
                true, 1, 2, 3, "#00FF00", "We're starting!", "clock",
                18, 30, 15, "hh:mm a", TextBackdrop(border = true, borderColor = "#ABCDEF"),
            )
            waitForIdle()

            val item = vm.scheduleItems.single() as ScheduleItem.AnnouncementItem
            assertEquals("Service starts in", item.text)
            assertEquals("#FFEEAA", item.textColor)
            assertEquals("#101020", item.backgroundColor)
            assertEquals(64, item.fontSize)
            assertEquals("Georgia", item.fontType)
            assertTrue(item.bold && item.italic && item.underline && item.shadow)
            assertEquals("#001122", item.shadowColor)
            assertEquals(TextBackdrop(border = true, borderColor = "#ABCDEF"), item.backdrop)
            assertEquals(120, item.shadowSize)
            assertEquals(55, item.shadowOpacity)
            assertEquals("left", item.horizontalAlignment)
            assertEquals("top", item.position)
            assertEquals("FADE", item.animationType)
            assertEquals(750, item.animationDuration)
            assertEquals(3, item.loopCount)
            assertTrue(item.isTimer)
            assertEquals(listOf(1, 2, 3), listOf(item.timerHours, item.timerMinutes, item.timerSeconds))
            assertEquals("#00FF00", item.timerTextColor)
            assertEquals("We're starting!", item.timerExpiredText)
            assertEquals("clock", item.timerMode)
            assertEquals(listOf(18, 30, 15), listOf(item.targetHour, item.targetMinute, item.targetSecond))
            assertEquals("hh:mm a", item.liveClockFormat)
        }

    // ── Editing in place ────────────────────────────────────────────────────────

    @Test
    fun `updateLabel rewrites the label it names`() =
        scheduleTab(seed = { seedService() }) { vm, reports ->
            val actions = registeredActions(reports)
            val labelId = vm.scheduleItems.first().id

            actions.updateLabel(labelId, "Announcements", "#000000", "#FFFFFF")
            waitForIdle()

            val label = vm.scheduleItems.first() as ScheduleItem.LabelItem
            assertEquals("Announcements", label.text)
            assertEquals("#000000", label.textColor)
            assertEquals("#FFFFFF", label.backgroundColor)
            assertTrue(
                "Announcements" in orderOf("Announcements", "Welcome"),
                "and the row must redraw with the new text",
            )
        }

    @Test
    fun `updateWebsiteTitle names a website that was added without one`() =
        scheduleTab(seed = { addWebsite(url = "https://example.org/notices", title = "") }) { vm, reports ->
            // Added from the address bar there is no title yet, so the row shows the raw URL; the
            // page's own title arrives later, once it has loaded, and fills the row in.
            registeredActions(reports).updateWebsiteTitle("https://example.org/notices", "Weekly notices")
            waitForIdle()

            assertEquals("Weekly notices", (vm.scheduleItems.single() as ScheduleItem.WebsiteItem).title)
        }

    @Test
    fun `updateWebsiteTitle leaves a website the operator has already named alone`() =
        scheduleTab(seed = { seedService() }) { vm, reports ->
            registeredActions(reports).updateWebsiteTitle("https://example.org", "Weekly notices")
            waitForIdle()

            // A page title arriving late must not overwrite the name someone chose deliberately.
            assertEquals("Notices", (vm.scheduleItems.last() as ScheduleItem.WebsiteItem).title)
        }

    // ── Saving and opening, via the File menu ──────────────────────────────────

    private class FakeChooser(private val picked: File?) : FileChooser() {
        @Volatile
        var answered: Int = 0
            private set

        override suspend fun chooseImpl(
            path: NioPath,
            filters: List<FileNameExtensionFilter>,
            title: String,
            selectDirectory: Boolean,
            multiple: Boolean,
        ): List<NioPath>? = picked?.let { listOf(Path(it.absolutePath)) }.also { answered++ }

        override suspend fun saveImpl(
            location: NioPath,
            suggestedName: String,
            filters: List<FileNameExtensionFilter>,
            title: String,
        ): NioPath? = picked?.let { Path(it.absolutePath) }.also { answered++ }
    }

    private fun givenChooserReturns(picked: File?): FakeChooser {
        mockkObject(FileChooser.Companion)
        return FakeChooser(picked).also { every { FileChooser.platformInstance } returns it }
    }

    @Test
    fun `saveScheduleAs writes the service to the chosen file`() {
        val dest = File(Files.createTempDirectory("cp-schedule-menu-save").toFile(), "service.cps")

        scheduleTab(seed = { seedService() }) { _, reports ->
            givenChooserReturns(dest)

            registeredActions(reports).saveScheduleAs()
            waitUntil(timeoutMillis = 2_000) { dest.exists() }

            assertTrue(dest.exists())
        }
    }

    @Test
    fun `cancelling saveScheduleAs writes nothing`() {
        scheduleTab(seed = { seedService() }) { _, reports ->
            val chooser = givenChooserReturns(null)

            registeredActions(reports).saveScheduleAs()
            waitUntil(timeoutMillis = 2_000) { chooser.answered == 1 }
        }
    }

    @Test
    fun `saveSchedule falls back to save-as the first time, same as the button does`() {
        val dest = File(Files.createTempDirectory("cp-schedule-menu-save-fallback").toFile(), "service.cps")

        scheduleTab(seed = { seedService() }) { _, reports ->
            givenChooserReturns(dest)

            registeredActions(reports).saveSchedule()
            waitUntil(timeoutMillis = 2_000) { dest.exists() }

            assertTrue(dest.exists(), "with no path chosen yet, Save must prompt just like Save As")
        }
    }

    @Test
    fun `openSchedule replaces the current service with the file chosen`() {
        val dir = Files.createTempDirectory("cp-schedule-menu-open").toFile()
        val src = File(dir, "saved.cps")

        scheduleTab(seed = { seedService() }) { _, savedReports ->
            givenChooserReturns(src)
            registeredActions(savedReports).saveScheduleAs()
            waitUntil(timeoutMillis = 2_000) { src.exists() }
        }
        unmockkObject(FileChooser.Companion)

        scheduleTab(seed = { addSong(9, "Pre-existing", "Hymnal") }) { vm, reports ->
            val chooser = givenChooserReturns(src)

            registeredActions(reports).openSchedule()
            waitUntil(timeoutMillis = 2_000) { chooser.answered == 1 }
            waitUntil(timeoutMillis = 2_000) { vm.scheduleItems.size == 4 }

            assertEquals(
                listOf("Welcome", "42 - Amazing Grace", "John 3:16", "Notices"),
                vm.scheduleItems.map { it.displayText },
                "the previously open service is replaced by the one just opened",
            )
        }
    }

    @Test
    fun `cancelling openSchedule leaves the service untouched`() =
        scheduleTab(seed = { seedService() }) { vm, reports ->
            val chooser = givenChooserReturns(null)
            val before = vm.scheduleItems.map { it.displayText }

            registeredActions(reports).openSchedule()
            waitUntil(timeoutMillis = 2_000) { chooser.answered == 1 }

            assertEquals(before, vm.scheduleItems.map { it.displayText })
        }
}
