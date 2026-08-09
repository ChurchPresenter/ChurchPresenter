@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.app.churchpresenter.data.VerseSequenceLog
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Going live teaches the sequence log what followed what.
 *
 * `VerseSequenceLogTest` covers the learning rules themselves. What this file is for is the wiring:
 * the log sits on the one go-live funnel in `BibleTab`, so every route to the screen feeds it, and
 * what it is fed is the *canonical* reference rather than the displayed one.
 *
 * The clock is injected and never advanced, so every go-live here lands inside one notional
 * service — the session boundary is `VerseSequenceLogTest`'s business, not this file's.
 *
 * See `BibleTabTestSupport.kt` for the harness.
 */
class BibleTabSequenceLogTest {

    private lateinit var tempDir: File
    private lateinit var log: VerseSequenceLog

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("cp-verse-sequences").toFile()
        log = VerseSequenceLog(File(tempDir, "verse_sequences.json")) { FIXED_NOW }
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `going live records the verse against canonical numbering`() = bibleTab(sequenceLog = log) { _, _ ->
        onNodeWithText("3. And God said, Let there be light.").performClick()
        waitForIdle()
        actionButton(BibleLabel.GO_LIVE).performClick()
        waitForIdle()

        // Genesis is canonical book 1, so Genesis 1:3 is 001001003 whatever the module calls it.
        assertEquals("001001003", log.snapshot().last)
    }

    @Test
    fun `two go-lives in one service become a learned transition`() = bibleTab(sequenceLog = log) { _, _ ->
        onNodeWithText("1. In the beginning God created the heaven and the earth.").performClick()
        waitForIdle()
        actionButton(BibleLabel.GO_LIVE).performClick()
        waitForIdle()

        // Genesis 2 — a different chapter, so a deliberate move rather than reading on.
        onNodeWithText("2").performClick()
        waitForIdle()
        onNodeWithText("1. Thus the heavens were finished.").performClick()
        waitForIdle()
        actionButton(BibleLabel.GO_LIVE).performClick()
        waitForIdle()

        assertEquals(mapOf("001002001" to 1), log.snapshot().pairs["001001001"])
    }

    @Test
    fun `reading straight on through a chapter is not learned`() = bibleTab(sequenceLog = log) { _, _ ->
        onNodeWithText("1. In the beginning God created the heaven and the earth.").performClick()
        waitForIdle()
        actionButton(BibleLabel.GO_LIVE).performClick()
        waitForIdle()
        onNodeWithText("3. And God said, Let there be light.").performClick()
        waitForIdle()
        actionButton(BibleLabel.GO_LIVE).performClick()
        waitForIdle()

        assertTrue(
            log.snapshot().pairs.isEmpty(),
            "verse 3 is two on from verse 1 — the arrow keys already reach it",
        )
        assertEquals("001001003", log.snapshot().last, "it still anchors whatever comes next")
    }

    @Test
    fun `merely selecting a verse teaches it nothing`() = bibleTab(sequenceLog = log) { _, _ ->
        onNodeWithText("3. And God said, Let there be light.").performClick()
        waitForIdle()

        assertEquals(null, log.snapshot().last, "browsing is not a choice about what to show")
    }

    @Test
    fun `a tab with no log still goes live`() = bibleTab { vm, reports ->
        onNodeWithText("3. And God said, Let there be light.").performClick()
        waitForIdle()
        actionButton(BibleLabel.GO_LIVE).performClick()
        waitForIdle()

        assertTrue(reports.live != null, "the log is optional wiring, not a precondition")
        assertEquals(1, vm.history.size)
    }

    private companion object {
        /** Any fixed instant: these tests care about what is recorded, not when. */
        const val FIXED_NOW = 1_700_000_000_000L
    }
}
