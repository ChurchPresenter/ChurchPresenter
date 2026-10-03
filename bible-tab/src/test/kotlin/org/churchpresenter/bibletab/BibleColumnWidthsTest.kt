package org.churchpresenter.bibletab

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Density
import org.churchpresenter.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class BibleColumnWidthsTest {

    private var settings = AppSettings()

    private fun widths(maximized: Boolean) = BibleColumnWidths(
        mutableStateOf(150f),
        mutableStateOf(60f),
        mutableStateOf(300f),
        mutableStateOf(240f),
        Density(2f),
        maximized,
        mutableStateOf<((AppSettings) -> AppSettings) -> Unit>({ transform -> settings = transform(settings) }),
    )

    @Test
    fun `book and chapter widths are saved in dp to the maximized layout`() {
        widths(maximized = true).saveColWidths()

        assertEquals(75, settings.maximizedLayout.bibleColWidthBook)
        assertEquals(30, settings.maximizedLayout.bibleColWidthChapter)
    }

    @Test
    fun `book and chapter widths are saved to the windowed layout when floating`() {
        widths(maximized = false).saveColWidths()

        assertEquals(75, settings.windowedLayout.bibleColWidthBook)
        assertEquals(30, settings.windowedLayout.bibleColWidthChapter)
    }

    @Test
    fun `the live panel width is saved to the layout in use`() {
        widths(maximized = true).saveColWSplit()
        widths(maximized = false).also { it.colWSplit = 400f }.saveColWSplit()

        assertEquals(150, settings.maximizedLayout.splitLivePanelWidth)
        assertEquals(200, settings.windowedLayout.splitLivePanelWidth)
    }

    @Test
    fun `the cross-reference panel width is saved to the layout in use`() {
        widths(maximized = true).saveColWCrossRef()
        widths(maximized = false).saveColWCrossRef()

        assertEquals(120, settings.maximizedLayout.bibleColWidthCrossRef)
        assertEquals(120, settings.windowedLayout.bibleColWidthCrossRef)
    }
}
