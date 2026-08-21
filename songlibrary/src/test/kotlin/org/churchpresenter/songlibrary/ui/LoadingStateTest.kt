@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.songlibrary.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.core.models.songs.SongLibrary
import kotlinx.coroutines.CoroutineDispatcher
import org.churchpresenter.theme.AppThemeWrapper
import org.churchpresenter.theme.ThemeMode
import java.nio.file.Files
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the window shows while the folder is still being read.
 *
 * A real library takes seconds to walk and parse, and for that whole time the window is the only
 * thing on screen — so "reading" has to be distinguishable from "empty", or the operator is told
 * their library is gone. Only reachable because [SongLibraryApp] takes the dispatcher it reads on:
 * [Gate] holds the load open for as long as the assertions need.
 */
class LoadingStateTest {

    @Test
    fun `a library still being read says so instead of saying it is empty`() {
        val folder = Files.createTempDirectory("songlibrary-loading").toFile()
        val gate = Gate()
        try {
            SongLibrary(folder).writeNew(STOCK.first())
            runComposeUiTest {
                setContent {
                    AppThemeWrapper(theme = ThemeMode.LIGHT) {
                        SongLibraryApp(libraryFolder = folder, onClose = {}, io = gate)
                    }
                }

                assertTrue(isShowing("Reading library…"), "the subhead says what is happening")
                assertFalse(isShowing(Text.EMPTY_LIBRARY), "an unread library is not an empty one")
                assertFalse(isShowing("Amazing Grace"), "and no row is on screen yet")
                assertTrue(isShowing("TITLE"), "but the columns are, so the grid does not jump")

                gate.release()
                awaitRow("Amazing Grace")
                assertFalse(isShowing("Reading library…"), "and it stops saying it when the read lands")
            }
        } finally {
            gate.release()
            folder.deleteRecursively()
        }
    }

    /** Queues instead of running, so work handed to it finishes only when [release] is called. */
    private class Gate : CoroutineDispatcher() {
        private val queued = mutableListOf<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            synchronized(queued) { queued += block }
        }

        fun release() {
            val pending = synchronized(queued) { queued.toList().also { queued.clear() } }
            pending.forEach { it.run() }
        }
    }
}
