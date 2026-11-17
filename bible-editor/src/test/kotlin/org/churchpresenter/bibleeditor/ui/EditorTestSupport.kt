@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.bibleeditor.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.churchpresenter.bibleedits.EditEntry
import org.churchpresenter.bibleeditor.EditorFixture
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode

internal const val WINDOW_WIDTH = 1240
internal const val WINDOW_HEIGHT = 820

/** Tall enough that every verse of the fixture's chapter is laid out at once: a lazy list composes only what shows. */
private const val TALL_WINDOW_HEIGHT = 1800

/** What a test sees of the window's callbacks. */
internal class EditorCalls {
    var closed = 0
    var saved = 0
}

/** The editor opened on a fresh [EditorFixture], with [history] already saved, at the window's size. */
internal fun withEditor(
    history: List<EditEntry> = emptyList(),
    withReference: Boolean = true,
    openMatthew17: Boolean = true,
    io: CoroutineDispatcher = Dispatchers.Unconfined,
    awaitOpen: Boolean = true,
    body: ComposeUiTest.(fixture: EditorFixture, calls: EditorCalls) -> Unit,
) {
    val fixture = EditorFixture(withReference)
    val calls = EditorCalls()
    try {
        if (history.isNotEmpty()) fixture.saveHistory(history)
        runDesktopComposeUiTest(width = WINDOW_WIDTH, height = TALL_WINDOW_HEIGHT) {
            setContent {
                ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                    // Unconfined so every read finishes inline: the window is under test, not the thread.
                    BibleEditorApp(
                        bibleFolder = fixture.bibles,
                        dataFolder = fixture.data,
                        onClose = { calls.closed++ },
                        preferred = listOf(fixture.rst),
                        onSaved = { calls.saved++ },
                        io = io,
                        clock = { EditorFixture.NOW },
                        tickClock = false,
                    )
                }
            }
            if (awaitOpen) {
                awaitText("Matthew 1")
                if (openMatthew17) goToChapter(17)
            }
            body(fixture, calls)
        }
    } finally {
        fixture.delete()
    }
}

internal fun ComposeUiTest.awaitText(text: String, substring: Boolean = false) {
    waitUntil("'$text' showed", 10_000L) {
        onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }
}

internal fun ComposeUiTest.isShowing(text: String, substring: Boolean = false): Boolean =
    onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

internal fun ComposeUiTest.isShowingDescription(description: String): Boolean =
    onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()

/** Clicks the last node with [text]: a menu's row is drawn after the button that opened it. */
internal fun ComposeUiTest.click(text: String, substring: Boolean = false) {
    onAllNodesWithText(text, substring = substring).let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
    waitForIdle()
}

internal fun ComposeUiTest.press(description: String) {
    onAllNodesWithContentDescription(description)[0].performClick()
    waitForIdle()
}

/** The "Go to" box inside the chapter picker. */
internal fun ComposeUiTest.chapterField(): SemanticsNodeInteraction =
    onNode(hasSetTextAction() and hasContentDescription("Chapter number"))

/** Opens the chapter picker from the header's chapter button. */
internal fun ComposeUiTest.openChapterPicker() {
    onNode(hasText("Chapter") and hasClickAction()).performClick()
    waitForIdle()
}

internal fun ComposeUiTest.goToChapter(chapter: Int) {
    openChapterPicker()
    chapterField().performTextReplacement(chapter.toString())
    chapterField().performKeyInput { pressKey(Key.Enter) }
    waitForIdle()
}

/** The field a verse is typed in, open in the editor card. */
internal fun ComposeUiTest.draftField(): SemanticsNodeInteraction =
    onNode(hasSetTextAction() and hasContentDescription("Chapter number").not())
