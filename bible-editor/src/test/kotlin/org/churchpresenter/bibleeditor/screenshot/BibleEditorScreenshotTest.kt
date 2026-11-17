@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.bibleeditor.screenshot

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import org.churchpresenter.bibleedits.EditEntry
import org.churchpresenter.bibleeditor.EditorFixture
import org.churchpresenter.bibleeditor.EditorFixture.Companion.KJV_17
import org.churchpresenter.bibleeditor.ui.BibleEditorApp
import org.churchpresenter.bibleeditor.ui.LocalScriptureFont
import org.churchpresenter.bibleeditor.ui.WINDOW_HEIGHT
import org.churchpresenter.bibleeditor.ui.WINDOW_WIDTH
import org.churchpresenter.bibleeditor.ui.awaitText
import org.churchpresenter.bibleeditor.ui.click
import org.churchpresenter.bibleeditor.ui.draftField
import org.churchpresenter.bibleeditor.ui.goToChapter
import org.churchpresenter.bibleeditor.ui.openChapterPicker
import org.churchpresenter.bibleeditor.ui.press
import org.churchpresenter.sharedui.screenshot.PinnedFonts
import org.churchpresenter.sharedui.screenshot.captureTo
import org.churchpresenter.sharedui.screenshot.stackedThemes
import org.churchpresenter.theme.ChurchPresenterTheme
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * The Bible editor window at its own size, in both themes.
 *
 * Matthew 17 of a Bible missing verse 11, with two typos fixed and saved. The verse text is drawn in
 * the pinned sans face: the system serif the window asks for differs between macOS versions, and no
 * serif ships with the test fixtures.
 */
class BibleEditorScreenshotTest {

    private var locale: Locale = Locale.getDefault()

    @BeforeTest
    fun pinLocale() {
        locale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @AfterTest
    fun restoreLocale() = Locale.setDefault(locale)

    @Test
    fun `a chapter with a missing verse and its history`() = shoot("chapter")

    @Test
    fun `a chapter with nothing changed and History closed`() = shoot("chapter_history_closed", history = emptyList()) {
        press("Close history")
    }

    @Test
    fun `a typo being fixed`() = shoot("fixing_typo", history = emptyList()) {
        press("Edit verse 2")
        draftField().performTextReplacement(KJV_17[1])
        waitForIdle()
    }

    @Test
    fun `a missing verse being typed in`() = shoot("typing_missing_verse") {
        click("Validate")
        click("Type the next one")
        draftField().performTextReplacement(KJV_17[10])
        waitForIdle()
    }

    @Test
    fun `the missing verses dialog`() = shoot("missing_verses") { click("Validate") }

    @Test
    fun `the Bible picker`() = shoot("bible_picker", rootIndex = 1) { click("Russian Synodal") }

    @Test
    fun `the chapter picker`() = shoot("chapter_picker", rootIndex = 1) { openChapterPicker() }

    @Test
    fun `the book picker`() = shoot("book_picker", rootIndex = 1) { click("Matthew") }

    private fun shoot(
        name: String,
        history: List<EditEntry> = EditorFixture.savedFixes(),
        rootIndex: Int = 0,
        drive: ComposeUiTest.() -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        val fixture = EditorFixture()
        try {
            if (history.isNotEmpty()) fixture.saveHistory(history)
            runDesktopComposeUiTest(width = WINDOW_WIDTH, height = WINDOW_HEIGHT) {
                setContent {
                    PinnedFonts {
                        ChurchPresenterTheme(themeMode = mode) {
                            CompositionLocalProvider(LocalScriptureFont provides FontFamily.SansSerif) {
                                BibleEditorApp(
                                    bibleFolder = fixture.bibles,
                                    dataFolder = fixture.data,
                                    onClose = {},
                                    preferred = listOf(fixture.rst),
                                    io = Dispatchers.Unconfined,
                                    clock = { EditorFixture.NOW },
                                    tickClock = false,
                                )
                            }
                        }
                    }
                }
                awaitText("Matthew 1")
                goToChapter(17)
                drive()
                waitForIdle()
                captureTo(file, rootIndex)
            }
        } finally {
            fixture.delete()
        }
    }

    private companion object {
        const val SECTION = "bibleEditor"
    }
}
