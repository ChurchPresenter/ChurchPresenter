@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SystemSettingsTab
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import org.churchpresenter.ui.screenshot.captureTo
import org.churchpresenter.ui.screenshot.stackedThemes

/**
 * The System tab of the settings dialog, in both themes.
 *
 * **Most of this tab is below the fold.** It is one scrolling column of eight sections inside a
 * 1400x900 dialog, so what opens is the first two and nothing else. Shooting only the top would
 * leave six sections — every storage folder past Songs, and the whole General block with the theme,
 * the language, analytics, launch-at-login, import/export and reset — unreviewed. So each section
 * each section gets its own image, scrolled to by the same `performScrollTo` the behaviour tests use.
 *
 * Directories are real temp folders holding real files: the tab scans whatever path it is given and
 * draws what it finds, so a made-up path would only ever show the empty state.
 */
class SystemSettingsTabScreenshotTest {

    private val dirs = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        dirs.forEach { it.deleteRecursively() }
        dirs.clear()
    }

    /**
     * The tab as it opens, at the top of its scroll.
     *
     * The window a capture comes from is a fixed size, so "the fold" here is that window's rather
     * than the options dialog's 1400x900 — the sections below it are reached by scrolling, exactly
     * as an operator reaches them.
     */
    private fun shoot(
        name: String,
        settings: AppSettings = AppSettings(),
        drive: ComposeUiTest.() -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        systemSettingsTab(settings = settings, themeMode = mode) {
            drive()
            waitForIdle()
            captureTo(file)
        }
    }

    /** Scrolls [label]'s section into view and shoots it. */
    private fun section(name: String, label: String, settings: AppSettings = filledSettings()) =
        shoot(name, settings = settings) {
            onAllNodesWithText(label)[0].performScrollTo()
            waitForIdle()
        }

    // ── What opens ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `as it opens, with nothing configured`() = shoot("top_empty")

    @Test
    fun `as it opens, with folders already set`() = shoot("top_filled", settings = filledSettings())

    // ── Below the fold ──────────────────────────────────────────────────────────────────────────
    // Three distinct scroll positions, no more: the folder sections from Bible through Media all sit
    // in the first viewport, so scrolling to any one of them lands where the tab already was and
    // produces the same picture as `top_filled`. Only the General block and the destructive controls
    // under it are genuinely below the fold.

    @Test
    fun `scrolled to the general block`() = section("general", ANALYTICS)

    @Test
    fun `scrolled to the bottom`() = section("bottom", RESET)

    // Not shot: the whole column in one tall image. A capture is the *window* root, and the test
    // window is a fixed size whatever the composable is given — a 2600dp box produced a picture
    // identical to the 820dp one. Below-the-fold content is reachable only by scrolling to it, which
    // is what every section shot above does.

    // ── Folder states ───────────────────────────────────────────────────────────────────────────

    /** A folder with nothing in it: the picker is set, the detected list says so. */
    @Test
    fun `a folder with no files in it`() = shoot("folder_empty", settings = AppSettings().withBibleDir(emptyDir()))

    /** A path that is not there any more — the status pill turns red. */
    @Test
    fun `a folder that has gone`() = shoot(
        "folder_missing",
        settings = AppSettings().withBibleDir(File(emptyDir(), "gone")),
    )

    // Not shot: the chosen theme. `SystemSettingsTab` takes `currentTheme` and `onThemeChange` and
    // reads neither — the theme picker is on the Appearance tab — so passing Studio or Warm produces
    // a picture identical to the default. The two parameters look live from the call site but are not.

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────

    /** Every folder pointed at a real directory holding real files, so each scan finds something. */
    private fun filledSettings(): AppSettings {
        val bibles = dirWith("bibles", "kjv.spb", "rst.spb", "nkjv.spb")
        val songs = dirWith("songs", "001 Amazing Grace.sps", "002 It Is Well.sps")
        val pictures = dirWith("pictures", "01 Welcome.png", "02 Sunrise.jpg")
        val lowerThirds = dirWith("lower-thirds", "Welcome.json", "Speaker Name.json")
        val decks = dirWith("decks", "Sermon.pptx", "Notices.pdf")
        val media = dirWith("media", "Welcome Loop.mp4")
        return AppSettings().let { s ->
            s.copy(
                bibleSettings = s.bibleSettings.copy(storageDirectory = bibles.absolutePath),
                songSettings = s.songSettings.copy(storageDirectory = songs.absolutePath),
                pictureSettings = s.pictureSettings.copy(storageDirectory = pictures.absolutePath),
                streamingSettings = s.streamingSettings.copy(lowerThirdFolder = lowerThirds.absolutePath),
                presentationStorageDirectory = decks.absolutePath,
                mediaStorageDirectory = media.absolutePath,
            )
        }
    }

    private fun AppSettings.withBibleDir(dir: File) =
        copy(bibleSettings = bibleSettings.copy(storageDirectory = dir.absolutePath))

    private fun emptyDir(): File = dirWith("empty")

    /**
     * A fixed directory under a neutral root.
     *
     * The tab prints each folder's **absolute** path into the image, which constrains it twice. It
     * has to be stable — a `createTempDirectory` name carries a random suffix, so every recording
     * would rewrite every one of these images. And it must carry nothing personal: a repo-relative
     * `build/` fixture resolves through the developer's home directory, which on most machines is
     * their name, and that would be committed into the PNGs for ever.
     */
    private fun dirWith(name: String, vararg files: String): File {
        val dir = File(FIXTURES, name).absoluteFile
        dir.deleteRecursively()
        dir.mkdirs()
        dirs += dir
        files.forEach { File(dir, it).writeText("fixture") }
        return dir
    }

    /** Composes the tab at [height] on the dialog's own ground and runs [block]. */
    private fun systemSettingsTab(
        settings: AppSettings,
        themeMode: ThemeMode,
        block: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(themeMode = themeMode) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) {
                        SystemSettingsTab(
                            settings = settings,
                            onSettingsChange = {},
                        )
                    }
                }
            }
        }
        block()
    }

    private companion object {
        const val SECTION = "systemSettingsTab"

        /**
         * `/tmp` where there is one, the JVM's temp directory otherwise (Windows).
         *
         * Both are free of anything identifying; `/tmp` is preferred because it also renders short
         * enough to read in the image.
         */
        val FIXTURES: File = File("/tmp")
            .takeIf { it.isDirectory }
            ?.let { File(it, "churchpresenter-screenshots/system") }
            ?: File(System.getProperty("java.io.tmpdir"), "churchpresenter-screenshots/system")

        /** As the tab renders them — the scroll targets for each section below the fold. */
        const val ANALYTICS = "Share anonymous crash & analytics reports"
        const val RESET = "Reset All Settings"
    }
}
