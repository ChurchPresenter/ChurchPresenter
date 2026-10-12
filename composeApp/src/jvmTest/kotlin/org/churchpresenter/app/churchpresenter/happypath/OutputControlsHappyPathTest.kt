@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.happypath

import androidx.compose.ui.test.ComposeUiTest
import org.churchpresenter.bibletab.bibleFixture
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.songs.verseMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val LINE = "amazing grace how sweet the sound"

class OutputControlsHappyPathTest : HappyPathHarness() {

    private fun library() = run {
        song(1, "Amazing Grace", listOf("Verse 1" to LINE))
        bible("test.spb", bibleFixture)
        settings(Tabs.BIBLE, Tabs.SONGS, songSettings = verseMode(), bibleFile = "test.spb")
    }

    private fun ComposeUiTest.songLive(app: HappyApp) {
        selectTab("Songs")
        clickText("Amazing Grace")
        goLive()
        await("the song is on air") { app.presenter.slideContent.value == Presenting.LYRICS }
        awaitOnOutput(app, LINE)
    }

    @Test
    fun `clear display blanks the output`() = happyPath(library()) { app ->
        songLive(app)

        clickTabControl("Clear Display")

        await("nothing is on air") { !app.presenter.anythingLive }
        assertFalse(LINE in outputText(app), "the song is gone from the output")
    }

    @Test
    fun `switching tabs leaves the live content on air`() = happyPath(library()) { app ->
        songLive(app)

        selectTab("Bible")

        assertTrue(app.presenter.isLive(Presenting.LYRICS), "the song stays live")
        assertTrue(LINE in outputText(app), "and on the output")
    }
}
