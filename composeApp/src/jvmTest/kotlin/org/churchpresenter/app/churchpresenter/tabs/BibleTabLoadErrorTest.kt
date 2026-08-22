@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.diagnostics.CrashReportSweep
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleTranslationSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The message that says a translation could not be read.
 *
 * A module that fails to load leaves an empty book list, which on its own is indistinguishable
 * from a Bible folder that was never set up — so the banner naming the file is the only thing
 * standing between the operator and a tab that is blank for no stated reason. These assert on the
 * file name and on the substring of the wording that carries the meaning.
 *
 * The failure driven here is a configured translation whose file is not on disk, because it needs
 * no byte-level fixture: the harness writes `test.spb` and the settings name a second module
 * beside it that was never written.
 */
class BibleTabLoadErrorTest {

    /** A failed load reports itself; these tests must not leave the report behind. */
    private val sweep = CrashReportSweep()

    @BeforeTest fun mark() = sweep.mark()
    @AfterTest fun clean() = sweep.sweep()

    private fun withMissingTranslation(app: AppSettings) = app.copy(
        bibleSettings = app.bibleSettings.copy(
            translations = listOf(
                BibleTranslationSettings(fileName = "test.spb"),
                BibleTranslationSettings(fileName = "deleted.spb"),
            ),
        ),
    )

    @Test
    fun `a translation that could not be read is named above the tab`() =
        bibleTab(settings = ::withMissingTranslation) { _, _ ->
            onNodeWithText("could not be read", substring = true).assertIsDisplayed()
            onNodeWithText("deleted.spb", substring = true).assertIsDisplayed()
        }

    @Test
    fun `the tab says nothing when every translation loaded`() =
        bibleTab { _, _ ->
            assertEquals(
                0,
                onAllNodesWithText("could not be read", substring = true).fetchSemanticsNodes().size,
                "no banner when there is nothing to report",
            )
        }

    /** The module that did load is still usable — one bad translation does not blank the tab. */
    @Test
    fun `the translations that loaded still work alongside the message`() =
        bibleTab(settings = ::withMissingTranslation) { _, _ ->
            onNodeWithText("Genesis").assertIsDisplayed()
            onNodeWithText("In the beginning God created the heaven and the earth.", substring = true)
                .assertIsDisplayed()
        }
}
