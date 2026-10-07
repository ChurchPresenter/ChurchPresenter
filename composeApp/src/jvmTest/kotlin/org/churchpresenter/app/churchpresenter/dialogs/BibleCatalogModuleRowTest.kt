@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.material3.Text
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.bibleformats.catalog.BibleModule
import org.churchpresenter.bibleformats.catalog.BibleSourceId
import org.churchpresenter.bibleformats.catalog.InstallPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BibleCatalogModuleRowTest {

    private fun module(ot: Int, nt: Int, date: String = "2024-05-01") = BibleModule(
        sourceId = BibleSourceId.EBIBLE,
        downloadKey = "engkjv",
        language = "eng",
        identifier = "engkjv",
        displayName = "King James Version",
        releaseDate = date,
        otBookCount = ot,
        ntBookCount = nt,
        sizeBytes = 4_500_000,
        fileStem = "engkjv",
    )

    @Test
    fun `each testament gets its own badge, and the date shows only where asked`() {
        listOf(module(39, 0), module(0, 27), module(39, 27)).forEachIndexed { i, m ->
            runComposeUiTest {
                setContent {
                    MaterialTheme {
                        Column {
                            ModuleRow(
                                module = m,
                                showDate = i == 0,
                                isInstalled = i == 1,
                                isInstalling = i == 2,
                                phase = if (i == 2) InstallPhase.DOWNLOADING else null,
                                progress = 0.4f,
                                anyInstallRunning = i == 2,
                                onInstall = {},
                            )
                        }
                    }
                }
                waitForIdle()
                assertTrue(onAllNodesWithText("King James Version").fetchSemanticsNodes().isNotEmpty())
                val dated = onAllNodesWithText("2024-05-01", substring = true).fetchSemanticsNodes().isNotEmpty()
                assertTrue(dated == (i == 0), "row $i shows the date: $dated")
            }
        }
    }

    /** Renders [moduleSubtitle] for [module] and returns what it said. */
    private fun subtitleOf(module: BibleModule, showDate: Boolean): String {
        var subtitle = ""
        runComposeUiTest {
            setContent { MaterialTheme { Text(moduleSubtitle(module, showDate).also { subtitle = it }) } }
            waitForIdle()
        }
        return subtitle
    }

    @Test
    fun `the subtitle lists identifier, language, size and date in that order`() {
        // The size is formatted in the default locale, so the expectation is too.
        val megabytes = "%.1f".format(4_500_000 / (1024.0 * 1024.0))
        assertEquals("engkjv · eng · $megabytes MB · 2024-05-01", subtitleOf(module(39, 27), showDate = true))
    }

    @Test
    fun `whatever the source did not publish is left out of the subtitle`() {
        val bare = module(39, 27, date = "").copy(identifier = "", language = "", sizeBytes = 0)

        assertEquals("", subtitleOf(bare, showDate = true))
    }

    @Test
    fun `a blank identifier and size leave the language and date standing alone`() {
        val partial = module(39, 27).copy(identifier = "", sizeBytes = 0)

        assertEquals("eng · 2024-05-01", subtitleOf(partial, showDate = true))
        assertEquals("eng", subtitleOf(partial, showDate = false))
    }
}
