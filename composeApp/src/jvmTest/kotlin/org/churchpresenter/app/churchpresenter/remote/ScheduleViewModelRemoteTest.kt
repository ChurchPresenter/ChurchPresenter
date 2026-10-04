package org.churchpresenter.app.churchpresenter.remote

import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.schedule.ScheduleViewModel
import org.churchpresenter.server.ScheduleItemDto
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Instance Link follower mode: an overflow-room or second-campus instance mirrors whatever the
 * primary broadcasts, and the flat companion DTO has to map back onto the sealed [ScheduleItem]
 * hierarchy without losing items. What a follower does with the mirror is `:schedule`'s
 * `ScheduleViewModelFollowingTest`.
 *
 * Same `user.home` isolation as the `:schedule` suite's `ScheduleViewModelTest` — the ViewModel
 * resolves its autosave path at construction.
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

    @Test
    fun `each sync notifies listeners with the mapped schedule`() {
        val vm = newViewModel()
        val before = notifications.size
        vm.applyRemoteSchedule(listOf(dto("s1", "song") { title = "Remote" }, dto("x", "unknown")))
        assertEquals(before + 1, notifications.size, "the companion server and presenter need the new schedule")
        assertEquals(1, notifications.last().size, "only what mapped reaches the listeners")
    }
}
