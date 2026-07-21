package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.server.ScheduleItemDto
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Instance Link follower mode: an overflow-room or second-campus instance mirrors whatever the
 * primary broadcasts. Two things have to hold — the flat companion DTO has to map back onto the
 * sealed [ScheduleItem] hierarchy without losing items, and every local mutation has to be
 * redirected at the primary rather than applied here, or the two rooms silently drift apart.
 *
 * Same `user.home` isolation as [ScheduleViewModelTest] — the ViewModel resolves its autosave path
 * at construction.
 */
class ScheduleViewModelRemoteTest {

    private lateinit var tempHome: File
    private var realHome: String? = null
    private val created = mutableListOf<ScheduleViewModel>()
    private val notifications = mutableListOf<List<ScheduleItem>>()

    @BeforeTest
    fun isolateHome() {
        // Pin the JVM-wide log path to the real test home before swapping user.home below.
        TestSingletons.latchToTestHome()

        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-schedule-remote-test").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        notifications.clear()
    }

    @AfterTest
    fun restoreHome() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    private fun newViewModel(): ScheduleViewModel =
        ScheduleViewModel(onScheduleChanged = { notifications.add(it) }).also { created.add(it) }

    private fun dto(id: String, type: String, displayText: String = id, build: DtoBuilder.() -> Unit = {}) =
        DtoBuilder(id, type, displayText).apply(build).build()

    /** Keeps each test's DTO to the handful of fields that type actually carries. */
    private class DtoBuilder(val id: String, val type: String, val displayText: String) {
        var songNumber: Int? = null
        var title: String? = null
        var songbook: String? = null
        var bookName: String? = null
        var chapter: Int? = null
        var verseNumber: Int? = null
        var verseRange: String? = null
        var text: String? = null
        var textColor: String? = null
        var backgroundColor: String? = null
        var folderPath: String? = null
        var folderName: String? = null
        var imageCount: Int? = null
        var filePath: String? = null
        var fileName: String? = null
        var slideCount: Int? = null
        var fileType: String? = null
        var mediaUrl: String? = null
        var mediaTitle: String? = null
        var mediaType: String? = null
        var presetId: String? = null
        var presetLabel: String? = null
        var url: String? = null

        fun build() = ScheduleItemDto(
            id = id, type = type, displayText = displayText,
            songNumber = songNumber, title = title, songbook = songbook,
            bookName = bookName, chapter = chapter, verseNumber = verseNumber, verseRange = verseRange,
            text = text, textColor = textColor, backgroundColor = backgroundColor,
            folderPath = folderPath, folderName = folderName, imageCount = imageCount,
            filePath = filePath, fileName = fileName, slideCount = slideCount, fileType = fileType,
            mediaUrl = mediaUrl, mediaTitle = mediaTitle, mediaType = mediaType,
            presetId = presetId, presetLabel = presetLabel, url = url
        )
    }

    // ── DTO → ScheduleItem mapping ──────────────────────────────────────────────

    @Test
    fun `a song mirrors with its number, title and songbook`() {
        val vm = newViewModel()
        vm.applyRemoteSchedule(listOf(dto("s1", "song") {
            songNumber = 42; title = "Amazing Grace"; songbook = "Hymnal"
        }))
        val song = vm.scheduleItems.single() as ScheduleItem.SongItem
        assertEquals("s1", song.id, "ids must survive so remove/reorder commands still address the same item")
        assertEquals(42, song.songNumber)
        assertEquals("Amazing Grace", song.title)
        assertEquals("Hymnal", song.songbook)
    }

    @Test
    fun `a verse mirrors with its reference, text and range`() {
        val vm = newViewModel()
        vm.applyRemoteSchedule(listOf(dto("b1", "bible") {
            bookName = "John"; chapter = 3; verseNumber = 16; verseRange = "16-18"; text = "For God so loved…"
        }))
        val verse = vm.scheduleItems.single() as ScheduleItem.BibleVerseItem
        assertEquals("John", verse.bookName)
        assertEquals(3, verse.chapter)
        assertEquals(16, verse.verseNumber)
        assertEquals("16-18", verse.verseRange)
        assertEquals("For God so loved…", verse.verseText)
    }

    @Test
    fun `every mirrored type lands on the matching item class`() {
        val vm = newViewModel()
        val dtos = listOf(
            dto("a", "song"),
            dto("b", "bible"),
            dto("c", "label"),
            dto("d", "picture"),
            dto("e", "presentation"),
            dto("f", "media"),
            dto("g", "lower_third"),
            dto("h", "announcement"),
            dto("i", "website"),
            dto("j", "scene"),
            dto("k", "dictionary")
        )
        vm.applyRemoteSchedule(dtos)

        assertEquals(
            listOf(
                "SongItem", "BibleVerseItem", "LabelItem", "PictureItem", "PresentationItem",
                "MediaItem", "LowerThirdItem", "AnnouncementItem", "WebsiteItem", "SceneItem", "DictionaryItem"
            ),
            vm.scheduleItems.map { it::class.simpleName }
        )
        assertEquals(dtos.map { it.id }, vm.scheduleItems.map { it.id }, "mirrored order must match the primary's")
    }

    @Test
    fun `an unrecognised type is dropped instead of breaking the whole sync`() {
        val vm = newViewModel()
        vm.applyRemoteSchedule(listOf(
            dto("s1", "song") { title = "Kept" },
            dto("x1", "some_future_type"),
            dto("s2", "song") { title = "Also Kept" }
        ))
        assertEquals(
            listOf("Kept", "Also Kept"),
            vm.scheduleItems.map { (it as ScheduleItem.SongItem).title },
            "one unknown item from a newer primary must not blank the follower's schedule"
        )
    }

    @Test
    fun `absent optional fields fall back to empty defaults rather than failing`() {
        val vm = newViewModel()
        vm.applyRemoteSchedule(listOf(dto("s1", "song"), dto("b1", "bible"), dto("w1", "website")))

        val song = vm.scheduleItems[0] as ScheduleItem.SongItem
        assertEquals(0, song.songNumber)
        assertEquals("", song.title)

        val verse = vm.scheduleItems[1] as ScheduleItem.BibleVerseItem
        assertEquals("", verse.bookName)
        assertEquals(0, verse.chapter)

        val site = vm.scheduleItems[2] as ScheduleItem.WebsiteItem
        assertEquals("", site.url)
    }

    @Test
    fun `a website with no title falls back to its url`() {
        val vm = newViewModel()
        vm.applyRemoteSchedule(listOf(dto("w1", "website") { url = "https://example.org" }))
        assertEquals("https://example.org", (vm.scheduleItems.single() as ScheduleItem.WebsiteItem).title)
    }

    @Test
    fun `a label keeps its colours so the follower renders it the same`() {
        val vm = newViewModel()
        vm.applyRemoteSchedule(listOf(dto("l1", "label") {
            text = "Offering"; textColor = "#000000"; backgroundColor = "#FFEB3B"
        }))
        val label = vm.scheduleItems.single() as ScheduleItem.LabelItem
        assertEquals("Offering", label.text)
        assertEquals("#000000", label.textColor)
        assertEquals("#FFEB3B", label.backgroundColor)
    }

    @Test
    fun `scene and dictionary items fall back to the display text the dto carries`() {
        val vm = newViewModel()
        vm.applyRemoteSchedule(listOf(dto("sc", "scene", "Welcome Scene"), dto("dc", "dictionary", "agathos")))
        assertEquals("Welcome Scene", (vm.scheduleItems[0] as ScheduleItem.SceneItem).sceneName)
        assertEquals("agathos", (vm.scheduleItems[1] as ScheduleItem.DictionaryItem).word)
    }

    // ── Sync semantics ──────────────────────────────────────────────────────────

    @Test
    fun `a sync replaces the previous mirror rather than appending to it`() {
        val vm = newViewModel()
        vm.applyRemoteSchedule(listOf(dto("s1", "song") { title = "Old" }))
        vm.applyRemoteSchedule(listOf(dto("s2", "song") { title = "New" }))
        assertEquals(listOf("New"), vm.scheduleItems.map { (it as ScheduleItem.SongItem).title })
    }

    @Test
    fun `an empty broadcast clears the mirrored schedule`() {
        val vm = newViewModel()
        vm.applyRemoteSchedule(listOf(dto("s1", "song") { title = "Old" }))
        vm.applyRemoteSchedule(emptyList())
        assertTrue(vm.scheduleItems.isEmpty(), "the primary clearing its schedule must clear the follower's")
    }

    @Test
    fun `the first sync discards whatever the follower had locally`() {
        val vm = newViewModel()
        vm.addSong(1, "Local", "Hymnal")
        vm.applyRemoteSchedule(listOf(dto("s1", "song") { title = "Remote" }))
        assertEquals(listOf("Remote"), vm.scheduleItems.map { (it as ScheduleItem.SongItem).title })
    }

    @Test
    fun `each sync notifies listeners with the mirrored schedule`() {
        val vm = newViewModel()
        val before = notifications.size
        vm.applyRemoteSchedule(listOf(dto("s1", "song") { title = "Remote" }))
        assertEquals(before + 1, notifications.size, "the companion server and presenter need the new schedule")
        assertEquals(1, notifications.last().size)
    }

    // ── Local mutations while following ─────────────────────────────────────────

    @Test
    fun `every add type is forwarded to the primary instead of applied locally`() {
        val vm = newViewModel()
        val pushed = mutableListOf<ScheduleItem>()
        vm.onPushToRemoteSchedule = { pushed.add(it) }
        vm.applyRemoteSchedule(emptyList())

        vm.addSong(1, "Song", "Hymnal")
        vm.addBibleVerse("John", 3, 16, "text")
        vm.addLabel("Offering", "#FFF", "#000")
        vm.addPicture("/pics", "Pics", 2)
        vm.addPresentation("/d.pptx", "d.pptx", 5, "pptx")
        vm.addMedia("/clip.mp4", "Clip", "local")
        vm.addLowerThird("p", "Pastor", false, 2000L)
        vm.addAnnouncement("Welcome")
        vm.addWebsite("https://example.org", "Site")
        vm.addScene("s1", "Scene")
        vm.addDictionary("G26", "agathos", "agathos", "good")

        assertEquals(11, pushed.size, "every add* method must funnel through the same push path")
        assertTrue(vm.scheduleItems.isEmpty(), "a follower must never mutate its own schedule locally")
    }

    @Test
    fun `an add with no push wiring is dropped silently rather than applied locally`() {
        val vm = newViewModel()
        vm.applyRemoteSchedule(emptyList())
        vm.addSong(1, "Song", "Hymnal") // onPushToRemoteSchedule left null — pushing is disabled
        assertTrue(vm.scheduleItems.isEmpty())
        assertFalse(vm.canUndo, "a dropped add must not leave a phantom undo step")
    }

    @Test
    fun `a removal is forwarded to the primary by id`() {
        val vm = newViewModel()
        val removed = mutableListOf<String>()
        vm.onRemoveFromRemoteSchedule = { removed.add(it) }
        vm.applyRemoteSchedule(listOf(dto("s1", "song") { title = "Remote" }))

        vm.removeItem("s1")

        assertEquals(listOf("s1"), removed)
        assertEquals(1, vm.scheduleItems.size, "the follower waits for the primary's next broadcast to drop it")
    }

    @Test
    fun `a removal with no push wiring leaves the mirror untouched`() {
        val vm = newViewModel()
        vm.applyRemoteSchedule(listOf(dto("s1", "song") { title = "Remote" }))
        vm.removeItem("s1")
        assertEquals(1, vm.scheduleItems.size)
    }

    @Test
    fun `reordering while following is refused and reports no new index`() {
        val vm = newViewModel()
        vm.applyRemoteSchedule(listOf(dto("s1", "song") { title = "A" }, dto("s2", "song") { title = "B" }))

        assertEquals(-1, vm.moveItemUp("s2"))
        assertEquals(-1, vm.moveItemDown("s1"))
        assertEquals(-1, vm.moveItemToTop("s2"))
        assertEquals(-1, vm.moveItemToBottom("s1"))
        vm.moveItem(0, 1)

        assertEquals(listOf("s1", "s2"), vm.scheduleItems.map { it.id }, "the mirror must stay in the primary's order")
    }

    @Test
    fun `in-place edits while following are refused`() {
        val vm = newViewModel()
        vm.applyRemoteSchedule(listOf(
            dto("l1", "label") { text = "Offering"; textColor = "#FFF"; backgroundColor = "#000" },
            dto("w1", "website") { url = "https://example.org" }
        ))

        vm.updateLabel("l1", "Communion", "#000000", "#FFEB3B")
        vm.updateWebsiteTitle("https://example.org", "Example Domain")

        assertEquals("Offering", (vm.scheduleItems[0] as ScheduleItem.LabelItem).text)
        assertEquals("https://example.org", (vm.scheduleItems[1] as ScheduleItem.WebsiteItem).title)
    }

    @Test
    fun `clearing and starting a new schedule while following are refused`() {
        val vm = newViewModel()
        vm.applyRemoteSchedule(listOf(dto("s1", "song") { title = "Remote" }))

        vm.clearSchedule()
        assertEquals(1, vm.scheduleItems.size)

        vm.newSchedule()
        assertEquals(1, vm.scheduleItems.size, "a follower cannot start its own service")
    }

    @Test
    fun `selection stays local to the follower`() {
        val vm = newViewModel()
        vm.applyRemoteSchedule(listOf(dto("s1", "song") { title = "Remote" }))

        vm.selectItem("s1")
        assertEquals("s1", vm.selectedItemId, "highlighting a row is a view concern, not a schedule edit")

        vm.clearSelection()
        assertNull(vm.selectedItemId)
    }

    @Test
    fun `handing control back keeps the mirrored items as the starting point`() {
        val vm = newViewModel()
        vm.applyRemoteSchedule(listOf(dto("s1", "song") { title = "Remote" }))
        vm.stopFollowingRemote()

        vm.addSong(2, "Local", "Hymnal")

        assertEquals(
            listOf("Remote", "Local"),
            vm.scheduleItems.map { (it as ScheduleItem.SongItem).title },
            "a dropped link must leave the operator with what was on screen, not an empty list"
        )
    }
}
