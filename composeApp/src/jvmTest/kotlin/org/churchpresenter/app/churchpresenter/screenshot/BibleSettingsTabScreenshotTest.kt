@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleTranslationSettings
import org.churchpresenter.app.churchpresenter.dialogs.tabs.BibleSettingsTab
import org.churchpresenter.app.churchpresenter.ui.theme.ChurchPresenterTheme
import org.churchpresenter.app.churchpresenter.utils.Constants
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * The Bible tab of the settings dialog, in both themes.
 *
 * The tab grows with the library: up to [Constants.MAX_BIBLE_TRANSLATIONS] translations can be
 * presented at once, each arriving as a row in the selection list on the left *and* a style block of
 * its own on the right. So the count is the axis worth shooting — reorder controls only appear past
 * two, and the Add button goes away at six — and the states below walk it.
 *
 * Only the first style block is open on arrival; the rest are a header row each until asked for,
 * which is what keeps a six-translation setup readable at all.
 *
 * The font dropdowns are deliberately never opened: their list is whatever `GraphicsEnvironment`
 * reports, so an image of one would differ by the machine that recorded it.
 */
class BibleSettingsTabScreenshotTest {

    /** The picker's "Recent" row is JVM-wide state — see [PinnedRecentColors]. */
    private val recents = PinnedRecentColors()

    @BeforeTest
    fun pinRecentColors() = recents.clear()

    @AfterTest
    fun unpinRecentColors() = recents.restore()

    private fun shoot(
        name: String,
        settings: AppSettings = withTranslations(1),
        rootIndex: Int = 0,
        drive: ComposeUiTest.() -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        runComposeUiTest {
            setContent {
                ChurchPresenterTheme(themeMode = mode) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize()) {
                            var current by remember { mutableStateOf(settings) }
                            BibleSettingsTab(
                                settings = current,
                                onSettingsChange = { transform -> current = transform(current) },
                            )
                        }
                    }
                }
            }
            drive()
            waitForIdle()
            captureTo(file, rootIndex)
        }
    }

    // ── How many bibles are in play ─────────────────────────────────────────────────────────────

    @Test
    fun `no translation chosen yet`() = shoot("none", settings = withTranslations(0))

    @Test
    fun `one translation`() = shoot("one")

    /** Two: still no reordering to do, so those controls stay away. */
    @Test
    fun `two translations`() = shoot("two", settings = withTranslations(2))

    /** Three: the order now matters, and the move-up/down controls appear with it. */
    @Test
    fun `three translations`() = shoot("three", settings = withTranslations(3))

    @Test
    fun `four translations`() = shoot("four", settings = withTranslations(4))

    /** Six is the ceiling: every style block is listed and the Add button is gone. */
    @Test
    fun `six translations, the most allowed`() =
        shoot("six", settings = withTranslations(Constants.MAX_BIBLE_TRANSLATIONS))

    // ── The style column ────────────────────────────────────────────────────────────────────────

    /** A later block opened: the one that was open closes, so only ever one is expanded. */
    @Test
    fun `a later style block opened`() = shoot("style_second_open", settings = withTranslations(3)) {
        accordionHeader(1).performClick()
        waitForIdle()
    }

    /**
     * Every block shut: clicking the one that is open closes it, and nothing opens in its place.
     *
     * With six translations this is the whole style column as six header rows — the shape an
     * operator gets once they have set each one up and stopped editing.
     */
    @Test
    fun `all style blocks collapsed`() = shoot(
        "all_collapsed",
        settings = withTranslations(Constants.MAX_BIBLE_TRANSLATIONS),
    ) {
        accordionHeader(0).performClick()
        waitForIdle()
    }

    // Not shot: a scrolled position. Nothing on this tab is below the fold at the capture window's
    // size — even six translations fit, because only the first style block is open and the rest are
    // one header row each. Scrolling to the last of them lands where the tab already was.

    // ── The pickers a style row opens ───────────────────────────────────────────────────────────

    /** The picker a colour row opens: a hue strip, a saturation square and a hex field. */
    @Test
    fun `the colour picker open`() = shoot("colour_picker", rootIndex = 1) {
        onAllNodesWithText("#FFFFFF")[0].performClick()
        waitForIdle()
    }

    /**
     * The font picker, which is also a search box.
     *
     * The list is whatever `GraphicsEnvironment` reports on the recording machine — the tab builds it
     * itself, so unlike the standalone `settingsFields` shot there is no fixed list to pass in. The
     * names in this one image therefore belong to whoever recorded it; CI renders both sides of its
     * comparison on one runner, so it still compares cleanly.
     *
     * Shown as it opens, holding its current value — which doubles as the search term, so the menu
     * is already filtered to the faces whose name contains it. Clearing it first would show the whole
     * list, but the only handle on the popup's own field from out here is "the first typed field on
     * screen", and that is one of the tab's number boxes: typing into it moves focus and shuts the
     * menu before the capture.
     */
    @Test
    fun `the font picker open`() = shoot("font_picker", rootIndex = 1) {
        onAllNodesWithText("Arial")[0].performClick()
        waitForIdle()
    }

    // ── Styling carried by a translation ────────────────────────────────────────────────────────

    @Test
    fun `a translation styled away from the defaults`() = shoot(
        "styled_translation",
        settings = withTranslations(2) { index, t ->
            if (index == 0) {
                t.copy(
                    textColor = "#FFD54F",
                    textFontSize = 96,
                    textBold = true,
                    textItalic = true,
                    referenceColor = "#90CAF9",
                    referenceFontSize = 40,
                )
            } else {
                t
            }
        },
    )

    /**
     * The style block's own header row on the right.
     *
     * Matched on the dash the header joins its parts with — "Translation 2 — rst" — because the
     * module's bare name is also the value of that translation's *selector* on the left, and a plain
     * name match hits the selector first and opens its menu instead of the accordion.
     */
    private fun ComposeUiTest.accordionHeader(index: Int) =
        onAllNodesWithText("— ${displayName(index)}", substring = true)[0]

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────

    /**
     * [count] translations configured over a folder holding all six modules.
     *
     * The folder is real and its files are real, because the tab scans it to offer what is not yet
     * chosen — with nothing on disk the Add button has nothing to add.
     */
    private fun withTranslations(
        count: Int,
        style: (Int, BibleTranslationSettings) -> BibleTranslationSettings = { _, t -> t },
    ): AppSettings {
        val dir = bibleFolder()
        return AppSettings(
            bibleSettings = BibleSettings(
                storageDirectory = dir.absolutePath,
                translations = MODULES.take(count).mapIndexed { index, file ->
                    style(index, BibleTranslationSettings(fileName = file))
                },
            ),
        )
    }

    private fun displayName(index: Int) = MODULES[index].substringBeforeLast('.')

    /**
     * A fixed folder under a neutral root.
     *
     * Not a temp directory: the tab prints the folder's absolute path, so a random name would
     * rewrite these images on every recording. Not a repo-relative `build/` one either — that
     * resolves through the developer's home directory and would commit their name into the PNGs.
     */
    private fun bibleFolder(): File {
        val dir = FIXTURES.absoluteFile
        dir.deleteRecursively()
        dir.mkdirs()
        MODULES.forEach { File(dir, it).writeText("fixture") }
        return dir
    }

    private companion object {
        const val SECTION = "bibleSettingsTab"

        val FIXTURES: File = File("/tmp")
            .takeIf { it.isDirectory }
            ?.let { File(it, "churchpresenter-screenshots/bibles") }
            ?: File(System.getProperty("java.io.tmpdir"), "churchpresenter-screenshots/bibles")

        /** Six modules — the most the tab will present at once. */
        val MODULES = listOf("kjv.spb", "rst.spb", "rvr.spb", "nkjv.spb", "elberfelder.spb", "ukr.spb")

    }
}
