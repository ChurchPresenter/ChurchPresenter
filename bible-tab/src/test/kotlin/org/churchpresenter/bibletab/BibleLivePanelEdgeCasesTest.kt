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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.utils.ShortcutMap
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BibleLivePanelEdgeCasesTest {

    private lateinit var dir: File
    private lateinit var vm: BibleViewModel
    private val presenting = mutableListOf<Presenting>()

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-bible-live-edges").toFile()
        SpbFixture.spbFile(dir, name = "test.spb")
        vm = BibleViewModel(settings(), dispatcher = Dispatchers.Unconfined, ioDispatcher = Dispatchers.Unconfined)
        val deadline = System.currentTimeMillis() + 5_000
        while (!(vm.isFullyLoaded && vm.verses.value.isNotEmpty())) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out")
            Thread.yield()
        }
    }

    @AfterTest
    fun tearDown() {
        vm.dispose()
        dir.deleteRecursively()
    }

    private fun settings() = AppSettings(
        bibleSettings = BibleSettings(
            storageDirectory = dir.absolutePath, primaryBible = "test.spb", splitBrowseMode = true,
        ),
    )

    private fun ComposeUiTest.scope(output: BibleOutput? = null): BibleTabScope {
        var built: BibleTabScope? = null
        setContent {
            val focus = remember { FocusRequester() }
            val coroutines = rememberCoroutineScope()
            val tab = remember {
                BibleTabScope(
                    states = vm.tabStates(),
                    appSettings = settings(),
                    onSettingsChange = {},
                    onAddToSchedule = null,
                    onVerseSelected = {},
                    onInstanceLinkSendVerse = null,
                    onInstanceLinkSendBibleHold = null,
                    onPresenting = { presenting += it },
                    bibleOutput = output,
                    verseStatistics = null,
                    onVerseWentLive = {},
                    verseSequenceLog = null,
                    sttManager = null,
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

    private fun ComposeUiTest.press(key: Key) {
        onNodeWithTag(TAG).requestFocus()
        onNodeWithTag(TAG).performKeyInput { pressKey(key) }
        waitForIdle()
    }

    @Test
    fun `a live panel of unnumbered lines takes the key without moving`() = runComposeUiTest {
        val tab = scope()
        tab.liveChapterVerses = listOf("A heading with no verse number")

        press(Key.DirectionDown)

        assertEquals(0, tab.liveNavToken, "there is no numbered line to step to")
        assertEquals(0, tab.liveNavTargetVerse)
    }

    @Test
    fun `a live panel with nothing live steps from verse one`() = runComposeUiTest {
        val tab = scope()
        tab.liveChapterVerses = vm.verses.value

        press(Key.DirectionDown)

        assertEquals(2, tab.liveNavTargetVerse)
        assertEquals(1, tab.liveNavToken)
    }

    @Test
    fun `a held live-panel verse is released with no controller to tell`() = runComposeUiTest {
        val output = FakeBibleOutput().apply { setBibleHold(true) }
        val tab = scope(output)
        tab.liveBookName = "Genesis"
        tab.liveChapterNum = 1

        runOnIdle { tab.liveVerseClicked(vm, 1) }
        waitUntil { presenting.isNotEmpty() }

        assertFalse(output.bibleHold.value)
        assertTrue(presenting.contains(Presenting.BIBLE))
    }

    private companion object {
        const val TAG = "live_panel_keys"
    }
}
