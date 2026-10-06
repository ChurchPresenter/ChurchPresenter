@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.settingspages

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.bibleformats.catalog.BibleModule
import org.churchpresenter.bibleformats.catalog.BibleSourceId
import org.churchpresenter.bibleformats.catalog.InstallPhase
import kotlin.test.Test
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
}
