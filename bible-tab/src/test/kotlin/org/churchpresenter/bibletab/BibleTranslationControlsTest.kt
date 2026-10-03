@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performMouseInput
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.sharedui.testing.renderedText
import org.churchpresenter.sharedui.testing.showsContainingText
import org.churchpresenter.sharedui.testing.showsExactly
import kotlin.test.Test
import kotlin.test.assertTrue

class BibleTranslationControlsTest {

    @Test
    fun `hovering swap names both translations in order`() = bibleTab(secondContent = bibleFixture) { _, _ ->
        onAllNodesWithContentDescription("Swap")[0].performMouseInput { moveTo(center) }
        waitForIdle()
        mainClock.advanceTimeBy(2_000)
        waitForIdle()

        assertTrue(showsExactly("Swap primary and secondary Bibles"), renderedText().toString())
        assertTrue(showsExactly("1. test"), renderedText().toString())
        assertTrue(showsExactly("2. second"), renderedText().toString())
    }

    @Test
    fun `a renamed translation leads the order chip by its new name`() = bibleTab(
        extraModules = listOf("second.spb", "third.spb"),
        settings = { app ->
            app.copy(
                bibleSettings = app.bibleSettings.copy(
                    translations = listOf(
                        BibleTranslationSettings(fileName = "test.spb", customName = "My Bible"),
                        BibleTranslationSettings(fileName = "second.spb", customName = "  "),
                        BibleTranslationSettings(fileName = "third.spb"),
                    ),
                ),
            )
        },
    ) { _, _ ->
        waitUntil(timeoutMillis = 5_000) { showsContainingText("My Bible") }

        assertTrue(showsContainingText("My Bible"), renderedText().toString())
    }
}
