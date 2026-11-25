@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.bibleeditor.screenshot

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.churchpresenter.bibleedits.EditEntry
import org.churchpresenter.bibleeditor.EditorFixture
import org.churchpresenter.bibleeditor.ui.BibleEditorApp
import org.churchpresenter.bibleeditor.ui.LocalScriptureFont
import org.churchpresenter.bibleeditor.ui.WINDOW_HEIGHT
import org.churchpresenter.bibleeditor.ui.WINDOW_WIDTH
import org.churchpresenter.bibleeditor.ui.awaitText
import org.churchpresenter.bibleeditor.ui.goToChapter
import org.churchpresenter.sharedui.screenshot.PinnedFonts
import org.churchpresenter.sharedui.screenshot.captureTo
import org.churchpresenter.sharedui.screenshot.bothThemes
import org.churchpresenter.theme.ChurchPresenterTheme
import java.io.File
import java.util.Locale
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick

private const val EMPTY_SHELF = "build/screenshot-empty-bible-folder"

/** How the window is opened for one shot. */
internal data class Shot(
    val history: List<EditEntry> = EditorFixture.savedFixes(),
    /** What the RST on the shelf holds. */
    val rst: String = EditorFixture.rst(),
    /** Makes the dispatcher the window reads on, once per theme; a `GateDispatcher` holds some reads back. */
    val io: () -> CoroutineDispatcher = { Dispatchers.Unconfined },
    /** Opens Matthew 17 once the window has its Bible; off for a window caught before that. */
    val openMatthew17: Boolean = true,
    /** Points the window at an empty folder. */
    val emptyShelf: Boolean = false,
    val rootIndex: Int = 0,
)

/**
 * The Bible editor at its own size, in both themes, under [section].
 *
 * Fonts, the clock and the locale are pinned, so the picture is the same on any Mac: verse text is
 * drawn in the pinned sans face (the system serif differs between macOS versions, and no serif ships
 * with the test fixtures), and times read as US English.
 */
internal fun shootEditor(section: String, name: String, shot: Shot = Shot(), drive: ComposeUiTest.() -> Unit = {}) =
    bothThemes(section, name) { mode, file ->
        val fixture = EditorFixture(shot.rst)
        val locale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            if (shot.history.isNotEmpty()) fixture.saveHistory(shot.history)
            val io = shot.io()
            // Relative to the module, so the path the notice prints is the same on every run.
            val folder = if (shot.emptyShelf) File(EMPTY_SHELF).also { it.mkdirs() } else fixture.bibles
            runDesktopComposeUiTest(width = WINDOW_WIDTH, height = WINDOW_HEIGHT) {
                setContent {
                    PinnedFonts {
                        ChurchPresenterTheme(themeMode = mode) {
                            CompositionLocalProvider(LocalScriptureFont provides FontFamily.SansSerif) {
                                BibleEditorApp(
                                    bibleFolder = folder,
                                    dataFolder = fixture.data,
                                    onClose = {},
                                    preferred = listOf(fixture.rst),
                                    io = io,
                                    clock = { EditorFixture.NOW },
                                    tickClock = false,
                                )
                            }
                        }
                    }
                }
                if (shot.openMatthew17) {
                    awaitText("Matthew 1")
                    goToChapter(17)
                }
                waitForIdle()
                drive()
                waitForIdle()
                captureTo(file, shot.rootIndex)
            }
        } finally {
            Locale.setDefault(locale)
            fixture.delete()
            File(EMPTY_SHELF).deleteRecursively()
        }
    }

/** Presses the button reading [text], brought into view first: the window is shorter than the chapter. */
internal fun ComposeUiTest.pressInView(text: String) {
    onAllNodesWithText(text).onLast().performScrollTo().performClick()
    waitForIdle()
}
