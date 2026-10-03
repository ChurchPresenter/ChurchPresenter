@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.STTSettings
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.stt.STTManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.sharedui.testing.renderedText
import org.churchpresenter.sharedui.testing.showsContainingText
import org.churchpresenter.stt.SILENT_STT_URL

/**
 * What the Bible tab does with the optional collaborators the host hands it.
 *
 * Each of these is null in the tab's own suites, so none of this ran: mirroring a go-live to a linked
 * primary, releasing Bible Hold so the verse actually reaches the screen, recording the display for
 * the CCLI report, the mic button that reconnects STT, and the reorder dropdown that only appears
 * once a third translation is in the stack.
 *
 * The Bible Hold one is the one that would be noticed live: an operator who left the output held and
 * then goes live from the tab expects the verse to appear, so going live has to clear the hold — and
 * in Controller mode has to clear it on the primary too, not just locally.
 */
class BibleTabWiringTest {

    private val managers = mutableListOf<STTManager>()

    @AfterTest
    fun cleanUp() {
        managers.forEach { runCatching { it.dispose() } }
        managers.clear()
    }

    private fun stt(connected: Boolean) = STTManager().also {
        managers.add(it)
        if (connected) it.applyConnected()
    }

    /** Selects Genesis 1:1 and goes live with it. */
    private fun ComposeUiTest.goLive() {
        onNodeWithText("1. In the beginning God created the heaven and the earth.").performClick()
        waitForIdle()
        actionButton(BibleLabel.GO_LIVE).performClick()
        waitForIdle()
    }

    // ── Instance Link ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `in controller mode a go-live is mirrored to the primary`() {
        val sent = mutableListOf<SelectedVerse>()

        bibleTab(onInstanceLinkSendVerse = { sent += it }) { _, reports ->
            goLive()

            assertEquals(1, sent.size, "the primary has to be told, not just the local output")
            with(sent.single()) {
                assertEquals("Genesis", bookName)
                assertEquals(1, chapter)
                assertEquals(1, verseNumber)
                assertTrue(verseText.startsWith("In the beginning"))
            }
            // And the local path still ran. Asserted on the latest selection rather than on a
            // count: the tab reports every selection change through onVerseSelected, not only a
            // go-live, so the number of calls says nothing about how many verses went live.
            assertEquals("Genesis", reports.live?.single()?.bookName)
        }
    }

    @Test
    fun `without controller mode nothing is mirrored`() {
        bibleTab { _, reports ->
            goLive()

            // Nothing to assert but the absence — the local go-live is what still has to happen.
            assertEquals(1, reports.live?.single()?.verseNumber)
        }
    }

    // ── Bible Hold ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `going live releases a bible hold so the verse actually appears`() {
        val presenter = FakeBibleOutput()
        presenter.setBibleHold(true)

        bibleTab(presenter = presenter) { _, _ ->
            goLive()

            assertFalse(presenter.bibleHold.value, "a held output would swallow the verse")
        }
    }

    @Test
    fun `with no hold set going live leaves it alone`() {
        val presenter = FakeBibleOutput()

        bibleTab(presenter = presenter) { _, _ ->
            goLive()

            assertFalse(presenter.bibleHold.value)
        }
    }

    @Test
    fun `in controller mode releasing the hold is sent to the primary too`() {
        val presenter = FakeBibleOutput()
        presenter.setBibleHold(true)
        val holds = mutableListOf<Boolean>()

        bibleTab(presenter = presenter, onInstanceLinkSendBibleHold = { holds += it }) { _, _ ->
            goLive()

            assertEquals(listOf(false), holds, "the primary's hold has to be released as well")
        }
    }

    @Test
    fun `with no hold to release the primary is not told anything`() {
        val presenter = FakeBibleOutput()
        val holds = mutableListOf<Boolean>()

        bibleTab(presenter = presenter, onInstanceLinkSendBibleHold = { holds += it }) { _, _ ->
            goLive()

            assertTrue(holds.isEmpty(), "there was nothing to release")
        }
    }

    // ── Statistics ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `going live records the verse for the usage report`() {
        val logged = mutableListOf<String>()
        val statistics = BibleVerseStatistics { bible, book, chapter, verse ->
            logged += "$bible $book $chapter:$verse"
        }

        bibleTab(statistics = statistics) { _, _ ->
            goLive()

            assertTrue(logged.any { it.endsWith("Genesis 1:1") }, logged.toString())
        }
    }

    // ── The STT mic button ──────────────────────────────────────────────────────────────────────

    private fun sttSettings(lastConnected: String, server: String) =
        STTSettings(serverUrl = server, lastConnectedUrl = lastConnected)

    @Test
    fun `the mic button is hidden until a url has connected once`() {
        bibleTab(
            settings = { it.copy(sttSettings = sttSettings(lastConnected = "", server = STT_URL)) },
            stt = stt(connected = false),
        ) { _, _ ->
            assertFalse(hasActionButton(BibleLabel.STT_CONNECT))
            assertFalse(hasActionButton(BibleLabel.STT_DISCONNECT))
        }
    }

    @Test
    fun `the mic button is hidden when the url has been edited since`() {
        bibleTab(
            settings = {
                it.copy(sttSettings = sttSettings(lastConnected = STT_URL, server = "http://192.0.2.9:1"))
            },
            stt = stt(connected = false),
        ) { _, _ ->
            // The remembered success belongs to a different server, so it says nothing about this one.
            assertFalse(hasActionButton(BibleLabel.STT_CONNECT))
        }
    }

    @Test
    fun `the mic button offers to reconnect the url that worked before`() {
        val manager = stt(connected = false)

        bibleTab(
            settings = { it.copy(sttSettings = sttSettings(lastConnected = STT_URL, server = STT_URL)) },
            stt = manager,
        ) { _, _ ->
            actionButton(BibleLabel.STT_CONNECT).performClick()

            // connect() sets `connecting` before it launches anything, so this needs no waiting; the
            // endpoint accepts the socket and never answers, so the attempt simply stays in flight.
            assertTrue(manager.connecting.value)
        }
    }

    @Test
    fun `while connected the mic button offers to disconnect instead`() {
        val manager = stt(connected = true)

        bibleTab(
            settings = { it.copy(sttSettings = sttSettings(lastConnected = STT_URL, server = STT_URL)) },
            stt = manager,
        ) { _, _ ->
            assertFalse(hasActionButton(BibleLabel.STT_CONNECT))

            actionButton(BibleLabel.STT_DISCONNECT).performClick()

            assertFalse(manager.connected.value)
        }
    }

    // ── The translation reorder dropdown ────────────────────────────────────────────────────────

    private fun stack(vararg fileNames: String): (AppSettings) -> AppSettings =
        { app ->
            app.copy(
                bibleSettings = app.bibleSettings.copy(
                    translations = fileNames.map { BibleTranslationSettings(fileName = it) },
                )
            )
        }

    @Test
    fun `two translations keep the one-tap swap rather than the reorder list`() {
        bibleTab(settings = stack("test.spb", "second.spb")) { _, _ ->
            assertTrue(hasActionButton(BibleLabel.SWAP), "a pair is a flip, not an order")
        }
    }

    @Test
    fun `a third translation turns the swap into a reorder list`() {
        bibleTab(settings = stack("test.spb", "second.spb", "third.spb")) { _, _ ->
            assertFalse(hasActionButton(BibleLabel.SWAP), "three needs an order, not a flip")
            // The list is labelled by its own caption and shows the navigation bible's position.
            assertTrue(showsContainingText("1."), renderedText().toString())
        }
    }

    private companion object {
        /**
         * The silent loopback endpoint from `STTTabTestSupport` — accepts TCP, answers nothing, so a
         * connection attempt stays *connecting* deterministically without waiting out a route timeout.
         * See the note there for why an unroutable address was the wrong fixture on CI.
         */
        val STT_URL: String get() = SILENT_STT_URL
    }
}
