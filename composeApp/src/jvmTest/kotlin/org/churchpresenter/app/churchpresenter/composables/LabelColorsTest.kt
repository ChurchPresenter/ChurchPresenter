@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.ui.theme.ChurchPresenterTheme
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The colours a schedule label can be started from: the theme's own pairs, and this user's history.
 *
 * A label carries two colours that only mean anything together — a band and the text on it — so
 * neither the presets nor the history are lists of colours; they are lists of *pairs*, which is why
 * the existing per-colour [RecentColors] could not serve this.
 *
 * `user.home` is swapped per test because [RecentLabelColors] persists there. It resolves its file
 * per call rather than latching one at class-init, which is what makes that swap work — the
 * `PicturesTab` recent-folders singleton is the counter-example, and is recorded as untestable for
 * exactly the latching this one avoids.
 */
class LabelColorsTest {

    private lateinit var tempHome: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        TestSingletons.latchToTestHome()
        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-label-colors").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        RecentLabelColors.load()
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
        RecentLabelColors.load()
    }

    // ── The theme's own pairs ───────────────────────────────────────────────────

    @Test
    fun `the first preset is the tone an ordinary schedule card is drawn in`() = runComposeUiTest {
        // The point of the default: a label is a heading *in* the list, not a slab across it. Its
        // own bold, letter-spaced text and accent bar are what mark it as a heading.
        var expected = ""
        var actual = ""
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.DARK) {
                expected = cpColorToHex(MaterialTheme.colorScheme.surfaceContainer)
                actual = themeLabelPresets().first().background
            }
        }

        assertEquals(expected, actual)
    }

    @Test
    fun `the presets offer every theme the app ships`() = runComposeUiTest {
        // The point of the column: an operator on Dark still wants an Ocean-blue or Forest-green
        // section band. SYSTEM contributes nothing -- it is not a palette, it resolves to Light or
        // Dark -- and duplicates are dropped, so the count is the distinct palettes plus the
        // current theme's card tone at the head.
        var presets: List<LabelColors> = emptyList()
        var ocean = ""
        var forest = ""
        var mocha = ""
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.DARK) { presets = themeLabelPresets() }
            ChurchPresenterTheme(themeMode = ThemeMode.OCEAN) {
                ocean = cpColorToHex(MaterialTheme.colorScheme.primaryContainer)
            }
            ChurchPresenterTheme(themeMode = ThemeMode.FOREST) {
                forest = cpColorToHex(MaterialTheme.colorScheme.primaryContainer)
            }
            ChurchPresenterTheme(themeMode = ThemeMode.MOCHA) {
                mocha = cpColorToHex(MaterialTheme.colorScheme.primaryContainer)
            }
        }

        val bands = presets.map { it.background }
        listOf("Ocean" to ocean, "Forest" to forest, "Mocha" to mocha).forEach { (name, band) ->
            assertTrue(band in bands, "$name is missing from the presets: $bands")
        }
        assertEquals(bands.distinct(), bands, "two swatches doing the same thing is one too many")
    }

    // ── The history ─────────────────────────────────────────────────────────────

    @Test
    fun `a pair used is remembered, newest first`() {
        RecentLabelColors.add(LabelColors("#111111", "#EEEEEE"))
        RecentLabelColors.add(LabelColors("#222222", "#DDDDDD"))

        assertEquals(
            listOf(LabelColors("#222222", "#DDDDDD"), LabelColors("#111111", "#EEEEEE")),
            RecentLabelColors.combos.toList(),
        )
    }

    @Test
    fun `using a pair again moves it up rather than duplicating it`() {
        RecentLabelColors.add(LabelColors("#111111", "#EEEEEE"))
        RecentLabelColors.add(LabelColors("#222222", "#DDDDDD"))
        RecentLabelColors.add(LabelColors("#111111", "#EEEEEE"))

        assertEquals(2, RecentLabelColors.combos.size, "a repeat is not a new entry")
        assertEquals(LabelColors("#111111", "#EEEEEE"), RecentLabelColors.combos.first())
    }

    @Test
    fun `the history survives a restart`() {
        RecentLabelColors.add(LabelColors("#123456", "#ABCDEF"))

        RecentLabelColors.load()   // what a fresh process does

        assertEquals(listOf(LabelColors("#123456", "#ABCDEF")), RecentLabelColors.combos.toList())
    }

    @Test
    fun `the history is bounded`() {
        repeat(12) { RecentLabelColors.add(LabelColors("#%06X".format(it), "#FFFFFF")) }

        assertTrue(RecentLabelColors.combos.size <= 8, "was ${RecentLabelColors.combos.size}")
        assertEquals("#00000B", RecentLabelColors.combos.first().background, "the newest is kept")
        assertEquals("#000004", RecentLabelColors.combos.last().background, "and the oldest fell off")
    }

    @Test
    fun `a differently-cased hex is the same pair`() {
        RecentLabelColors.add(LabelColors("#abcdef", "#123456"))
        RecentLabelColors.add(LabelColors("#ABCDEF", "#123456"))

        assertEquals(1, RecentLabelColors.combos.size)
    }

    // ── The columns ─────────────────────────────────────────────────────────────

    @Test
    fun `clicking a swatch picks both of its colours at once`() = runComposeUiTest {
        var picked: LabelColors? = null
        val pairs = listOf(LabelColors("#111111", "#EEEEEE"), LabelColors("#222222", "#DDDDDD"))
        setContent {
            MaterialTheme {
                LabelColorColumns(presets = pairs, recents = emptyList(), onPick = { picked = it })
            }
        }

        onNodeWithTag("${LABEL_PRESET_TAG}_1").performClick()

        // Both, not one: picking a band and leaving the old text behind is how a label ends up
        // unreadable.
        assertEquals(LabelColors("#222222", "#DDDDDD"), picked)
    }

    @Test
    fun `the recent column is absent until there is history`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                LabelColorColumns(
                    presets = listOf(LabelColors("#111111", "#EEEEEE")),
                    recents = emptyList(),
                    onPick = {},
                )
            }
        }

        onNodeWithTag("${LABEL_RECENT_TAG}_0").assertDoesNotExist()
        onNodeWithTag("${LABEL_PRESET_TAG}_0").assertExists()
    }

    @Test
    fun `both columns are shown once history exists`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                LabelColorColumns(
                    presets = listOf(LabelColors("#111111", "#EEEEEE")),
                    recents = listOf(LabelColors("#222222", "#DDDDDD")),
                    onPick = {},
                )
            }
        }

        onNodeWithTag("${LABEL_PRESET_TAG}_0").assertExists()
        onNodeWithTag("${LABEL_RECENT_TAG}_0").assertExists()
    }
}
