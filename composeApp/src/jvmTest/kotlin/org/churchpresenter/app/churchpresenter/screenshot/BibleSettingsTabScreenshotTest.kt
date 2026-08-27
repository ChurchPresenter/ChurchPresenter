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
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.app.churchpresenter.dialogs.tabs.BibleSettingsTab
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.settings.utils.Constants
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * The Bible tab of the settings dialog, in both themes.
 *
 * The tab is a rail of stack-wide settings on the left and, on the right, a live preview over one
 * set of styling controls. That one set stands for four stored profiles — verse and reference,
 * full screen and lower third — chosen by the two segmented switches above it, so the axes worth
 * shooting are the translation count (reorder controls appear past two, the Add picker goes at six)
 * and each position of those switches.
 *
 * The preview renders `BiblePresenter` itself rather than reproducing its layout, so these images
 * also catch a presenter change that alters what the operator is shown.
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

    // ── What the one control set is pointed at ──────────────────────────────────────────────────

    /** The lower third: a band on the floor of the screen, and its own styling profile. */
    @Test
    fun `the lower third`() = shoot("lower_third", settings = withTranslations(2)) {
        onNodeWithText("Lower Third").performClick()
        waitForIdle()
    }

    /** The reference, which is the one element with a position of its own to set. */
    @Test
    fun `the reference element`() = shoot("element_reference") {
        onNodeWithText("Reference").performClick()
        waitForIdle()
    }

    /** The reference on the lower third — the fourth of the four stored profiles. */
    @Test
    fun `the reference on the lower third`() = shoot("element_reference_lower_third") {
        onNodeWithText("Lower Third").performClick()
        waitForIdle()
        onNodeWithText("Reference").performClick()
        waitForIdle()
    }

    /** A later translation selected: the chips point the one control set at it. */
    @Test
    fun `a later translation selected`() = shoot("translation_second", settings = withTranslations(3)) {
        onAllNodesWithText("2 · ", substring = true).onFirst().performClick()
        waitForIdle()
    }

    /** Shadow on: three more controls fold out beside the checkbox, on the transform's own row. */
    @Test
    fun `shadow switched on`() = shoot("shadow_on") {
        onNodeWithText("Shadow").performClick()
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

    /** The typography the redesign added: tracking, word spacing, a case transform, strikethrough. */
    @Test
    fun `a translation using the new typography controls`() = shoot(
        "styled_typography",
        settings = withTranslations(1) { _, t ->
            t.copy(
                textLetterSpacing = 6,
                textWordSpacing = 12,
                textStrikethrough = true,
                textTransform = Constants.TEXT_TRANSFORM_UPPERCASE,
                referenceTransform = Constants.TEXT_TRANSFORM_LOWERCASE,
            )
        },
    )

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
