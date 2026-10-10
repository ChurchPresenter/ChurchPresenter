@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import org.churchpresenter.presenter.BibleStyleElement
import org.churchpresenter.presenter.bibleBoxKey
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BibleBoxRowsDirectTest {

    private val stack = BibleSettings(
        translations = listOf(BibleTranslationSettings(fileName = "kjv.spb"), BibleTranslationSettings(fileName = "rst.spb")),
    )

    @Test
    fun `the box rows switch a translation's box on where it starts, and write its options`() =
        runSkikoComposeUiTest(size = Size(900f, 2000f), density = Density(1f)) {
            var doc by mutableStateOf(AppSettings(bibleSettings = stack))
            var index by mutableStateOf(1)
            setContent {
                val edit = BibleEdit(
                    doc.bibleSettings,
                    OutputProfile(id = "p", bibleMode = Constants.SONG_LANG_BOTH),
                    index,
                    CustomizeElement.BIBLE_TEXT,
                    lowerThird = false,
                ) { t -> doc = t(doc) }
                MaterialTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) { BibleBoxRows(edit.boxTarget(), edit) }
                }
            }
            onNodeWithText("Text box").performClick()
            waitForIdle()
            val key = stack.bibleBoxKey(BibleStyleElement.TEXT, lowerThird = false, fileName = "rst.spb")
            val box = doc.bibleSettings.textBoxes.getValue(key)
            assertTrue(box.enabled)
            assertEquals(defaultBibleBox(BibleStyleElement.TEXT, slot = 1, slots = 2).copy(enabled = true), box)
            onNodeWithText("Snap to guides").performClick()
            waitForIdle()
            assertEquals(!stack.textBoxOptions.snap, doc.bibleSettings.textBoxOptions.snap)
            index = ALL_TRANSLATIONS
            waitForIdle()
            onNodeWithText("Pick a translation above", substring = true).assertExists()
        }
}
