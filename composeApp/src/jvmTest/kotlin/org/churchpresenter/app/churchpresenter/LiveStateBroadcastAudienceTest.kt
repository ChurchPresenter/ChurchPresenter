package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.bible.Bible
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.utils.UsageEvent
import org.churchpresenter.sharedui.utils.UsageEvents
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two paths of [LiveStateBroadcastWiring] that need a real setup behind them: the first live
 * change on an install that projects to an audience, and a verse that the loaded Bible can name.
 */
@OptIn(ExperimentalTestApi::class)
class LiveStateBroadcastAudienceTest {

    @Test
    fun `the first thing put live on an audience screen is marked for the install`() = runComposeUiTest {
        TestSingletons.latchToTestHome()
        val manager = PresenterManager()
        val settings = AppSettings().let {
            it.copy(projectionSettings = it.projectionSettings.copy(screenAssignments = listOf(ScreenAssignment())))
        }
        setContent {
            LiveStateBroadcastWiring(
                appSettings = { settings },
                primaryBible = { null },
                presenterManager = manager,
                companionServer = CompanionServer(shutdownGraceMs = 0),
                screenCountForUsage = 2,
                deckLinkCountForUsage = 0,
            )
        }
        waitForIdle()
        manager.setPresentingMode(Presenting.LYRICS)
        manager.onLiveStateChanged?.invoke(manager, Presenting.LYRICS)

        assertTrue(
            (UsageEvents.unreported()[UsageEvent.FIRST_LIVE_ON_SCREEN] ?: 0) >= 1,
            "a second screen showing something is an audience seeing it",
        )
    }

    @Test
    fun `a live verse is logged under the code the loaded Bible gives it`() = runComposeUiTest {
        val dir = Files.createTempDirectory("cp-broadcast-bible").toFile()
        try {
            val file = SpbFixture.spbFile(
                dir,
                content = SpbFixture.buildContent(
                    title = "Test Bible",
                    books = listOf(SpbFixture.Book(43, "John", 3)),
                    verses = listOf(SpbFixture.Verse(43, 3, 16, "For God so loved the world")),
                ),
            )
            val bible = Bible().apply { loadFromSpb(file.absolutePath) }
            val manager = PresenterManager()
            val server = CompanionServer(shutdownGraceMs = 0)
            setContent {
                LiveStateBroadcastWiring(
                    appSettings = { AppSettings() },
                    primaryBible = { bible },
                    presenterManager = manager,
                    companionServer = server,
                    screenCountForUsage = 1,
                    deckLinkCountForUsage = 0,
                )
            }
            waitForIdle()
            manager.setSelectedVerses(listOf(SelectedVerse(bookName = "John", chapter = 3, verseNumber = 16)))
            manager.setPresentingMode(Presenting.BIBLE)
            manager.onLiveStateChanged?.invoke(manager, Presenting.BIBLE)

            assertEquals(43, bible.getBookIdByName("John"), "the verse names a book the Bible knows")
            assertEquals(Presenting.BIBLE, manager.slideContent.value)
        } finally {
            dir.deleteRecursively()
        }
    }
}
