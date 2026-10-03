package org.churchpresenter.bibletab

import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleEngineSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.sharedui.utils.TrainingDataLogger
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BibleViewModelDetectionPathsTest {

    private lateinit var dir: File
    private lateinit var vm: BibleViewModel

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-bible-detect-branch").toFile()
        SpbFixture.spbFile(dir, name = "test.spb")
        vm = loaded()
    }

    @AfterTest
    fun tearDown() {
        TrainingDataLogger.sessionId = null
        dir.deleteRecursively()
    }

    private fun loaded(autoFollow: Boolean = false): BibleViewModel {
        val model = BibleViewModel(
            AppSettings(
                bibleSettings = BibleSettings(storageDirectory = dir.absolutePath, primaryBible = "test.spb"),
                bibleEngineSettings = BibleEngineSettings(autoFollow = autoFollow),
            ),
        )
        awaitUntil { model.books.value.isNotEmpty() && model.isFullyLoaded }
        return model
    }

    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out")
            Thread.sleep(10)
        }
    }

    private fun ref(book: Int, chapter: Int, verse: Int?, key: String = "$book|$chapter|$verse|null") =
        DetectedReference(book, chapter, verse, null, label = key, key = key)

    private val empty get() = BibleViewModel(AppSettings())

    @Test
    fun `a display index maps to its book id, or one past it with no bible`() {
        assertEquals(43, vm.canonicalBookIdForDisplayIndex(2))
        assertEquals(3, empty.canonicalBookIdForDisplayIndex(2))
    }

    @Test
    fun `a canonical reference needs a bible and a verse it knows`() {
        assertEquals(Triple(43, 3, 16), vm.canonicalRefForDisplay(2, 3, 16))
        assertEquals(Triple(1, 1, null), vm.canonicalRefForDisplay(0, 1, null))
        assertNull(vm.canonicalRefForDisplay(2, 3, 999))
        assertNull(empty.canonicalRefForDisplay(0, 1, 1))
    }

    @Test
    fun `a book name resolves only when the book and verse exist`() {
        assertEquals(Triple(43, 3, 16), vm.canonicalRefForBookName("john", 3, 16))
        assertNull(vm.canonicalRefForBookName("Habakkuk", 3, 16))
        assertNull(vm.canonicalRefForBookName("John", 3, 999))
    }

    @Test
    fun `live references log with and without an end verse or canonical match`() {
        vm.logLiveReference(LiveReference(2, 3, 16, 17, source = "manual", autoFollow = false))
        vm.logLiveReference(LiveReference(2, 3, 16, null, source = "manual", autoFollow = true, matchType = "explicit"))
        vm.logLiveReference(LiveReference(2, 3, 999, 1000, source = "manual", autoFollow = false))
        empty.logLiveReference(LiveReference(0, 1, null, null, source = "manual", autoFollow = false))
    }

    @Test
    fun `operator flags log for every combination of what is known`() {
        vm.logOperatorFlag("missed")
        vm.logOperatorFlag("missed", bookName = "Habakkuk", chapter = 1, verseStart = 1)
        vm.logOperatorFlag("missed", bookName = "John", chapter = null, verseStart = 16)
        vm.logOperatorFlag("missed", bookName = "John", chapter = 3, verseStart = 16, verseEnd = 17)
        vm.logOperatorFlag("missed", bookName = "John", chapter = 3, verseStart = 999, verseEnd = 1000)
        vm.logOperatorFlag("wrong", bookName = "John", chapter = 3, verseStart = 16, matchType = "reverse")
    }

    @Test
    fun `engine scripture with no bible only records the ids it carries`() {
        val model = empty
        model.onEngineScripture(EngineScripture(43, 3, 16, null, "", "explicit", segmentId = "seg-1"))
        assertEquals("seg-1", model.lastDetectionSegmentId)
        assertTrue(model.detectedReferences.value.isEmpty())
    }

    @Test
    fun `engine scripture for a book the bible lacks is dropped`() {
        vm.onEngineScripture(EngineScripture(35, 1, 1, null, "text", "explicit"))
        assertTrue(vm.detectedReferences.value.isEmpty())
    }

    @Test
    fun `canonical codes place a detection at the module's own numbering`() {
        vm.onEngineScripture(EngineScripture(
            bookId = 1, chapter = 9, verseStart = 9, verseEnd = 9, verseText = "", matchType = "chapter-scan",
            canonicalCodeStart = "B043C003V016", canonicalCodeEnd = "B043C003V017",
            tracks = listOf("transcription", "translation", "other"), detectedVersion = "KJV",
        ))
        val top = vm.detectedReferences.value.single()
        assertEquals(2, top.bookIndex)
        assertEquals(3, top.chapter)
        assertEquals(16, top.verseStart)
        assertEquals(17, top.verseEnd)
        assertEquals(setOf(DetectionTrack.TRANSCRIPTION, DetectionTrack.TRANSLATION), top.tracks)
        assertEquals("For God so loved the world.", top.verseText)
        assertEquals("John 3:16-17", top.label)
    }

    @Test
    fun `a code the module lacks falls back to what the engine sent`() {
        vm.onEngineScripture(EngineScripture(
            bookId = 43, chapter = 3, verseStart = 17, verseEnd = null, verseText = "spoken",
            matchType = "chapter-history", canonicalCodeStart = "B043C009V009", canonicalCodeEnd = "B043C009V010",
        ))
        val top = vm.detectedReferences.value.single()
        assertEquals(17, top.verseStart)
        assertNull(top.verseEnd)
    }

    @Test
    fun `an unknown verse keeps the spoken text, or none when it was blank`() {
        vm.onEngineScripture(EngineScripture(43, 3, 40, null, "spoken words", "reverse"))
        vm.onEngineScripture(EngineScripture(43, 3, 41, null, "  ", "continuation"))
        val byVerse = vm.detectedReferences.value.associateBy { it.verseStart }
        assertEquals("spoken words", byVerse.getValue(40).verseText)
        assertNull(byVerse.getValue(41).verseText)
    }

    @Test
    fun `a session id from the engine becomes the training log's session`() {
        vm.onEngineScripture(EngineScripture(43, 3, 16, null, "", "explicit", sessionId = "detect-branch-session"))
        assertEquals("detect-branch-session", TrainingDataLogger.sessionId)
    }

    @Test
    fun `auto-follow navigates to a detection, going live only for the clear sources`() {
        val model = loaded(autoFollow = true)
        model.onEngineScripture(EngineScripture(43, 3, 17, null, "", "reverse"))
        model.onEngineScripture(EngineScripture(1, 1, 2, null, "", "explicit"))
        assertEquals(2, model.detectedReferences.value.size)
        awaitUntil { model.selectedBookIndex.value == 0 }
    }

    @Test
    fun `the same detection again merges its sources, text and version`() {
        vm.addDetection(ref(2, 3, 16).copy(sources = setOf(DetectionSource.REVERSE)))
        vm.addDetection(
            ref(2, 3, 16).copy(
                sources = setOf(DetectionSource.EXPLICIT),
                tracks = setOf(DetectionTrack.TRANSLATION),
                verseText = "text",
                detectedVersion = "NIV",
            ),
        )
        val merged = vm.detectedReferences.value.single()
        assertEquals(setOf(DetectionSource.REVERSE, DetectionSource.EXPLICIT), merged.sources)
        assertEquals("text", merged.verseText)
        assertEquals("NIV", merged.detectedVersion)
    }

    @Test
    fun `an identical repeat changes nothing`() {
        val first = ref(2, 3, 16).copy(sources = setOf(DetectionSource.REVERSE), verseText = "t", detectedVersion = "v")
        assertTrue(vm.addDetection(first))
        val before = vm.detectedReferences.value
        assertEquals(false, vm.addDetection(first.copy(verseText = null, detectedVersion = null)))
        assertTrue(before === vm.detectedReferences.value)
    }

    @Test
    fun `only a version arriving later still updates the entry`() {
        vm.addDetection(ref(2, 3, 16))
        vm.addDetection(ref(2, 3, 16).copy(detectedVersion = "ESV"))
        assertEquals("ESV", vm.detectedReferences.value.single().detectedVersion)
    }

    @Test
    fun `a recently seen key is not added back once it has left the list`() {
        repeat(BibleViewModel.MAX_DETECTED + 1) { vm.addDetection(ref(0, 1, it + 1)) }
        assertTrue(vm.detectedReferences.value.none { it.verseStart == 1 })
        assertEquals(false, vm.addDetection(ref(0, 1, 1)))
    }

    @Test
    fun `eviction logs ignored detections but not the ones acted on`() {
        vm.addDetection(ref(0, 1, 1))
        vm.actedDetectionKeys.add(ref(0, 1, 1).key)
        repeat(BibleViewModel.MAX_DETECTED) { vm.addDetection(ref(1, 23, it + 1)) }
        assertEquals(BibleViewModel.MAX_DETECTED, vm.detectedReferences.value.size)
        assertTrue(ref(0, 1, 1).key !in vm.actedDetectionKeys)
    }

    @Test
    fun `the dedupe window forgets the oldest keys`() {
        repeat(BibleViewModel.DETECTION_DEDUPE_WINDOW + 2) { vm.addDetection(ref(0, 2, it + 1)) }
        assertEquals(BibleViewModel.DETECTION_DEDUPE_WINDOW, vm.recentDetectionKeys.size)
        assertTrue(vm.addDetection(ref(0, 2, 1)), "the first key aged out of the window")
    }

    @Test
    fun `a go-live correction is logged only when the shown verse differs from the top detection`() {
        vm.logGoLiveCorrection(0, 1, 1)
        vm.addDetection(ref(2, 3, 16))
        vm.logGoLiveCorrection(2, 3, 16)
        assertTrue(ref(2, 3, 16).key !in vm.actedDetectionKeys)
        vm.logGoLiveCorrection(2, 3, 17)
        assertTrue(ref(2, 3, 16).key in vm.actedDetectionKeys)
        vm.logGoLiveCorrection(0, 1, 1)
        vm.logGoLiveCorrection(2, 4, 16)
    }

    @Test
    fun `clearing skips acted detections and leaves an empty list alone`() {
        vm.clearDetectedReferences()
        vm.addDetection(ref(2, 3, 16))
        vm.addDetection(ref(2, 3, 17))
        vm.applyDetectedReference(vm.detectedReferences.value.first(), goLiveSource = "click")
        vm.clearDetectedReferences("cleared")
        assertTrue(vm.detectedReferences.value.isEmpty())
        assertTrue(vm.actedDetectionKeys.isEmpty())
    }

    @Test
    fun `an engine version fills only the detections that lack one`() {
        vm.onEngineVersion(null)
        vm.onEngineVersion("KJV")
        vm.addDetection(ref(2, 3, 16).copy(detectedVersion = "NIV"))
        vm.onEngineVersion("KJV")
        assertEquals("NIV", vm.detectedReferences.value.single().detectedVersion)
        vm.addDetection(ref(2, 3, 17))
        vm.onEngineVersion("KJV")
        assertEquals(listOf("KJV", "NIV"), vm.detectedReferences.value.map { it.detectedVersion })
    }

    @Test
    fun `verse text needs a verse and a bible`() {
        assertNull(vm.verseTextFor(2, 3, null))
        assertNull(empty.verseTextFor(0, 1, 1))
        assertEquals("For God so loved the world.", vm.verseTextFor(2, 3, 16))
    }

    @Test
    fun `a detection label covers ranges, single verses, chapters and unknown books`() {
        assertEquals("John 3:16-17", vm.buildDetectionLabel(2, 3, 16, 17))
        assertEquals("John 3:16", vm.buildDetectionLabel(2, 3, 16, 16))
        assertEquals("John 3:16", vm.buildDetectionLabel(2, 3, 16, null))
        assertEquals("John 3", vm.buildDetectionLabel(2, 3, null, 17))
        assertEquals("3", vm.buildDetectionLabel(99, 3, 16, null))
    }
}
