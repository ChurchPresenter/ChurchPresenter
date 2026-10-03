@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.utils.ShortcutMap
import org.churchpresenter.stt.STTManager
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BibleTabScopeActionsTest {

    private lateinit var dir: File
    private lateinit var vm: BibleViewModel
    private val managers = mutableListOf<STTManager>()

    private class Calls {
        val holds = mutableListOf<Boolean>()
        val scheduled = mutableListOf<String>()
        val selected = mutableListOf<List<SelectedVerse>>()
        val linked = mutableListOf<String>()
        val presenting = mutableListOf<Presenting>()
        val counted = mutableListOf<Int>()
        var wentLive = 0
    }

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-bible-scope").toFile()
        SpbFixture.spbFile(dir, name = "test.spb")
        vm = BibleViewModel(
            settings(),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
        )
        awaitUntil { vm.isFullyLoaded && vm.verses.value.isNotEmpty() }
    }

    @AfterTest
    fun tearDown() {
        managers.forEach { runCatching { it.dispose() } }
        vm.dispose()
        dir.deleteRecursively()
    }

    private fun settings(split: Boolean = false, docked: Boolean = false) = AppSettings(
        bibleSettings = BibleSettings(
            storageDirectory = dir.absolutePath,
            primaryBible = "test.spb",
            splitBrowseMode = split,
            crossReferencesPanel = docked,
        ),
    )

    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out")
            Thread.yield()
        }
    }

    private fun ComposeUiTest.scope(
        calls: Calls = Calls(),
        appSettings: AppSettings = settings(),
        output: BibleOutput? = null,
        statistics: BibleVerseStatistics? = null,
        stt: STTManager? = null,
        withCallbacks: Boolean = true,
    ): BibleTabScope {
        var built: BibleTabScope? = null
        setContent {
            val focus = remember { FocusRequester() }
            val coroutines = rememberCoroutineScope()
            val tab = remember {
                BibleTabScope(
                    states = vm.tabStates(),
                    appSettings = appSettings,
                    onSettingsChange = {},
                    onAddToSchedule = if (withCallbacks) {
                        { book, chapter, verse, _, _, _ -> calls.scheduled += "$book $chapter:$verse" }
                    } else null,
                    onVerseSelected = { calls.selected += it },
                    onInstanceLinkSendVerse = if (withCallbacks) {
                        { book, chapter, verse, _, _ -> calls.linked += "$book $chapter:$verse" }
                    } else null,
                    onInstanceLinkSendBibleHold = if (withCallbacks) { { calls.holds += it } } else null,
                    onPresenting = { calls.presenting += it },
                    bibleOutput = output,
                    verseStatistics = statistics,
                    onVerseWentLive = { calls.wentLive++ },
                    verseSequenceLog = null,
                    sttManager = stt,
                    engineStatus = null,
                    focusRequester = focus,
                    crossRefs = BibleCrossReferenceState(),
                    live = BibleLiveNavState(),
                    ui = BibleTabUiState(),
                    widths = BibleColumnWidths(
                        mutableStateOf(100f), mutableStateOf(100f), mutableStateOf(100f), mutableStateOf(100f),
                        Density(1f), true, mutableStateOf({}),
                    ),
                    displayedVersesState = mutableStateOf(emptyList()),
                    currentIsPresentingState = mutableStateOf(false),
                    scope = coroutines,
                    shortcuts = ShortcutMap.DEFAULT,
                )
            }
            built = tab
            Box(
                Modifier.size(10.dp).testTag(TAG).focusRequester(focus).focusable()
                    .onPreviewKeyEvent { tab.handleKeyEvent(vm, it) },
            )
        }
        waitForIdle()
        return checkNotNull(built)
    }

    private val johnThreeSixteen =
        CrossRefRow(43, 3, 16, null, learned = false, label = "John 3:16", preview = "", available = true)

    /** Psalms 1: the fixture module has the book but none of that chapter's verses. */
    private fun emptyChapter() {
        vm.selectBook(1)
        awaitUntil { vm.selectedBookIndex.value == 1 }
        assertTrue(vm.verses.value.isEmpty())
    }

    private fun counting(calls: Calls) = BibleVerseStatistics { _, _, _, verse -> calls.counted += verse }

    @Test
    fun `holding with no output still tells the controller`() = runComposeUiTest {
        val calls = Calls()
        val tab = scope(calls)

        runOnIdle { tab.toggleHoldLive() }

        assertEquals(listOf(true), calls.holds)
    }

    @Test
    fun `holding turns the output's hold on and off`() = runComposeUiTest {
        val output = FakeBibleOutput()
        val tab = scope(output = output, withCallbacks = false)

        runOnIdle { tab.toggleHoldLive() }
        assertTrue(output.bibleHold.value)
        runOnIdle { tab.toggleHoldLive() }
        assertFalse(output.bibleHold.value)
    }

    @Test
    fun `the mic does nothing without a speech feed`() = runComposeUiTest {
        val tab = scope()

        runOnIdle { tab.toggleStt() }

        assertFalse(tab.sttConnected)
    }

    @Test
    fun `the mic disconnects a connected feed`() = runComposeUiTest {
        val stt = STTManager().also { managers += it; it.applyConnected() }
        val tab = scope(stt = stt)

        runOnIdle { tab.toggleStt() }

        assertFalse(stt.connected.value)
    }

    @Test
    fun `a filtered book or verse past the end of the list selects nothing`() = runComposeUiTest {
        val tab = scope()
        val book = vm.selectedBookIndex.value
        val verse = vm.selectedVerseIndex.value

        runOnIdle {
            tab.selectFilteredBook(vm, 99)
            tab.clickFilteredVerse(vm, 99)
        }

        assertEquals(book, vm.selectedBookIndex.value)
        assertEquals(verse, vm.selectedVerseIndex.value)
        assertEquals(-1, tab.crossRefs.popoverIndex)
    }

    @Test
    fun `a filtered book selects the book it names`() = runComposeUiTest {
        val tab = scope()

        runOnIdle { tab.selectFilteredBook(vm, 1) }

        assertEquals(1, vm.selectedBookIndex.value)
    }

    @Test
    fun `the refs chip opens the verse's popover and a second click closes it`() = runComposeUiTest {
        val tab = scope()

        runOnIdle { tab.refsChipClicked(vm, 1) }
        assertEquals(1, tab.crossRefs.popoverIndex)
        assertEquals(Triple(1, 1, 2), tab.crossRefs.popoverAnchor)
        assertTrue(tab.crossRefs.popoverLabel.isNotEmpty())

        runOnIdle { tab.refsChipClicked(vm, 1) }
        assertEquals(-1, tab.crossRefs.popoverIndex)
    }

    @Test
    fun `the refs chip for a verse that is not listed only closes the popover`() = runComposeUiTest {
        val tab = scope()

        runOnIdle { tab.refsChipClicked(vm, 42) }

        assertEquals(-1, tab.crossRefs.popoverIndex)
        assertNull(tab.crossRefs.popoverAnchor)
    }

    @Test
    fun `with the panel docked the refs chip opens no popover`() = runComposeUiTest {
        val tab = scope(appSettings = settings(docked = true))

        runOnIdle { tab.refsChipClicked(vm, 0) }

        assertEquals(-1, tab.crossRefs.popoverIndex)
        assertEquals(0, vm.selectedVerseIndex.value)
    }

    @Test
    fun `scheduling with nowhere to send it does nothing`() = runComposeUiTest {
        val calls = Calls()
        val tab = scope(calls, withCallbacks = false)

        runOnIdle {
            tab.scheduleCurrentVerse(vm)
            tab.scheduleCrossRef(vm, johnThreeSixteen)
        }

        assertTrue(calls.scheduled.isEmpty())
    }

    @Test
    fun `the current verse and a cross reference are scheduled by name`() = runComposeUiTest {
        val calls = Calls()
        val tab = scope(calls)
        vm.selectVerse(2)

        runOnIdle {
            tab.scheduleCurrentVerse(vm)
            tab.scheduleCrossRef(vm, johnThreeSixteen)
        }

        assertEquals("Genesis 1:3", calls.scheduled.first())
        assertEquals(2, calls.scheduled.size)
    }

    @Test
    fun `going live with nothing selected records nothing but still presents`() = runComposeUiTest {
        val calls = Calls()
        val tab = scope(calls, statistics = counting(calls))
        emptyChapter()

        runOnIdle { tab.goLiveWithHistory(vm) }

        assertEquals(0, calls.wentLive)
        assertTrue(calls.counted.isEmpty())
        assertTrue(calls.selected.isEmpty())
        assertEquals(listOf(Presenting.BIBLE), calls.presenting)
    }

    @Test
    fun `going live with a passage counts each verse and remembers the range`() = runComposeUiTest {
        val calls = Calls()
        val tab = scope(calls, statistics = counting(calls))
        vm.selectVerse(0)
        vm.ctrlClickVerse(1)
        vm.ctrlClickVerse(2)

        runOnIdle { tab.goLiveWithHistory(vm) }

        assertEquals(listOf(1, 2, 3), calls.counted)
        assertEquals("Genesis 1:1-3", vm.history.first().displayText)
        assertFalse(vm.multiVerseEnabled.value, "the passage selection is cleared once it is live")
        assertEquals(1, calls.wentLive)
    }

    @Test
    fun `going live with one verse links it and anchors the cross references`() = runComposeUiTest {
        val calls = Calls()
        val tab = scope(calls)
        vm.selectVerse(1)

        runOnIdle { tab.goLiveWithHistory(vm, source = "detection", matchType = "explicit") }

        assertEquals(listOf("Genesis 1:2"), calls.linked)
        assertEquals("Genesis 1:2", vm.history.first().displayText)
        assertEquals(listOf(Triple(1, 1, 2)), tab.crossRefs.anchors)
    }

    @Test
    fun `going live releases a hold and tells the controller`() = runComposeUiTest {
        val calls = Calls()
        val output = FakeBibleOutput().apply { setBibleHold(true) }
        val tab = scope(calls, output = output)
        vm.selectVerse(0)

        runOnIdle { tab.goLiveWithHistory(vm) }

        assertFalse(output.bibleHold.value)
        assertEquals(listOf(false), calls.holds)
    }

    @Test
    fun `a live-panel verse the chapter lacks sends nothing`() = runComposeUiTest {
        val calls = Calls()
        val tab = scope(calls)
        tab.liveBookName = "Genesis"
        tab.liveChapterNum = 1

        runOnIdle { tab.liveVerseClicked(vm, 40) }
        waitForIdle()

        assertTrue(calls.selected.isEmpty())
        assertTrue(calls.presenting.isEmpty())
    }

    @Test
    fun `a live-panel verse goes out and releases a hold`() = runComposeUiTest {
        val calls = Calls()
        val output = FakeBibleOutput().apply { setBibleHold(true) }
        val tab = scope(calls, output = output, statistics = counting(calls))
        tab.liveBookName = "Genesis"
        tab.liveChapterNum = 1

        runOnIdle { tab.liveVerseClicked(vm, 2) }
        waitUntil { calls.presenting.isNotEmpty() }

        assertEquals(listOf(2), calls.counted)
        assertEquals(listOf("Genesis 1:2"), calls.linked)
        assertFalse(output.bibleHold.value)
        assertEquals(listOf(false), calls.holds)
    }

    @Test
    fun `a live-panel verse with no hold and no link still goes out`() = runComposeUiTest {
        val calls = Calls()
        val tab = scope(calls, output = FakeBibleOutput(), withCallbacks = false)
        tab.liveBookName = "Unknown book"
        tab.liveChapterNum = 1

        runOnIdle { tab.liveVerseClicked(vm, 1) }
        waitForIdle()

        assertTrue(calls.holds.isEmpty())
    }

    @Test
    fun `copying a verse when none is selected does not fail`() = runComposeUiTest {
        val tab = scope()
        emptyChapter()

        runOnIdle { tab.copySelectedVerse() }

        assertTrue(vm.verses.value.isEmpty())
    }

    private fun ComposeUiTest.press(key: Key) {
        onNodeWithTag(TAG).requestFocus()
        onNodeWithTag(TAG).performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private fun BibleTabScope.showLiveChapter() {
        liveChapterVerses = vm.verses.value
        liveBookName = "Genesis"
        liveChapterNum = 1
        liveVerseNumbers = setOf(1)
    }

    @Test
    fun `in split mode down and up step the live panel's verse`() = runComposeUiTest {
        val tab = scope(appSettings = settings(split = true))
        tab.showLiveChapter()

        press(Key.DirectionDown)
        assertEquals(2, tab.liveNavTargetVerse)
        assertEquals(1, tab.liveNavToken)
        assertFalse(tab.liveNavPageStep)

        press(Key.DirectionUp)
        assertEquals(1, tab.liveNavTargetVerse)
        assertEquals(2, tab.liveNavToken)
    }

    @Test
    fun `in split mode the step starts from the lowest live verse`() = runComposeUiTest {
        val tab = scope(appSettings = settings(split = true))
        tab.showLiveChapter()
        tab.liveVerseNumbers = setOf(3, 2)

        press(Key.DirectionUp)

        assertEquals(1, tab.liveNavTargetVerse)
    }

    @Test
    fun `in split mode with no live chapter the keys move the browser instead`() = runComposeUiTest {
        val tab = scope(appSettings = settings(split = true))
        vm.selectVerse(0)

        press(Key.DirectionDown)

        assertEquals(1, vm.selectedVerseIndex.value)
        assertEquals(0, tab.liveNavToken)
    }

    @Test
    fun `the next-chapter key moves to the next chapter`() = runComposeUiTest {
        scope()

        press(Key.DirectionRight)

        assertEquals(2, vm.selectedChapter.value)
    }

    @Test
    fun `keys belong to the search field while it has the caret`() = runComposeUiTest {
        val tab = scope()
        vm.selectVerse(0)
        tab.searchFieldFocused = true

        press(Key.DirectionDown)

        assertEquals(0, vm.selectedVerseIndex.value)
    }

    @Test
    fun `the scope hands its pieces the state they were built from`() = runComposeUiTest {
        val calls = Calls()
        val tab = scope(calls)

        assertEquals(vm.verses.value, tab.states.verses.value)
        assertEquals(ShortcutMap.DEFAULT, tab.shortcuts)
        assertNull(tab.verseSequenceLog)
        assertTrue(tab.widths.isMaximized)
        assertEquals(1f, tab.widths.density.density)
        assertTrue(tab.ui.historyExpanded)
        assertEquals("", tab.live.liveBookName)
        tab.onVerseWentLive()
        assertEquals(1, calls.wentLive)
    }

    @Test
    fun `in split mode a split live verse steps to its other half first`() = runComposeUiTest {
        vm.dispose()
        SpbFixture.spbFile(
            dir, name = "test.spb",
            content = SpbFixture.buildContent(
                title = "Long",
                books = listOf(SpbFixture.Book(1, "Genesis", 1)),
                verses = listOf(
                    SpbFixture.Verse(1, 1, 1, List(60) { "word$it" }.joinToString(" ")),
                    SpbFixture.Verse(1, 1, 2, "And the earth was without form, and void."),
                ),
            ),
        )
        vm = BibleViewModel(
            settings(split = true).let {
                it.withBibleEverywhere(it.bibleSettings.copy(splitLongVerses = true, longVerseWordCount = 25))
            },
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
        )
        awaitUntil { vm.isFullyLoaded && vm.verses.value.isNotEmpty() }
        val tab = scope(appSettings = settings(split = true))
        tab.showLiveChapter()

        press(Key.DirectionDown)

        assertEquals(1, tab.liveNavTargetVerse)
        assertTrue(tab.liveNavPageStep)
    }

    private companion object {
        const val TAG = "scope_keys"
    }
}
