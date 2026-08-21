package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class BibleTabColumnWidthTest {

    @Test
    fun `saving column widths while windowed updates only the windowed layout`() {
        val before = AppSettings()
        val after = withBibleColumnWidths(before, isMaximized = false, bookWidthDp = 220, chapterWidthDp = 140)

        assertEquals(220, after.windowedLayout.bibleColWidthBook)
        assertEquals(140, after.windowedLayout.bibleColWidthChapter)
        assertEquals(before.maximizedLayout.bibleColWidthBook, after.maximizedLayout.bibleColWidthBook)
        assertEquals(before.maximizedLayout.bibleColWidthChapter, after.maximizedLayout.bibleColWidthChapter)
    }

    @Test
    fun `saving column widths while maximized updates only the maximized layout`() {
        val before = AppSettings()
        val after = withBibleColumnWidths(before, isMaximized = true, bookWidthDp = 220, chapterWidthDp = 140)

        assertEquals(220, after.maximizedLayout.bibleColWidthBook)
        assertEquals(140, after.maximizedLayout.bibleColWidthChapter)
        assertEquals(before.windowedLayout.bibleColWidthBook, after.windowedLayout.bibleColWidthBook)
        assertEquals(before.windowedLayout.bibleColWidthChapter, after.windowedLayout.bibleColWidthChapter)
    }

    @Test
    fun `saving column widths never touches the split panel width`() {
        val before = AppSettings()
        val after = withBibleColumnWidths(before, isMaximized = false, bookWidthDp = 220, chapterWidthDp = 140)

        assertEquals(before.windowedLayout.splitLivePanelWidth, after.windowedLayout.splitLivePanelWidth)
    }

    @Test
    fun `saving the split panel width while windowed updates only the windowed layout`() {
        val before = AppSettings()
        val after = withBibleSplitPanelWidth(before, isMaximized = false, widthDp = 360)

        assertEquals(360, after.windowedLayout.splitLivePanelWidth)
        assertEquals(before.maximizedLayout.splitLivePanelWidth, after.maximizedLayout.splitLivePanelWidth)
    }

    @Test
    fun `saving the split panel width while maximized updates only the maximized layout`() {
        val before = AppSettings()
        val after = withBibleSplitPanelWidth(before, isMaximized = true, widthDp = 360)

        assertEquals(360, after.maximizedLayout.splitLivePanelWidth)
        assertEquals(before.windowedLayout.splitLivePanelWidth, after.windowedLayout.splitLivePanelWidth)
    }

    @Test
    fun `saving the split panel width never touches the column widths`() {
        val before = AppSettings()
        val after = withBibleSplitPanelWidth(before, isMaximized = true, widthDp = 360)

        assertEquals(before.maximizedLayout.bibleColWidthBook, after.maximizedLayout.bibleColWidthBook)
        assertEquals(before.maximizedLayout.bibleColWidthChapter, after.maximizedLayout.bibleColWidthChapter)
    }

    @Test
    fun `saving the cross-reference width while windowed updates only the windowed layout`() {
        val before = AppSettings()
        val after = withBibleCrossRefPanelWidth(before, isMaximized = false, widthDp = 300)

        assertEquals(300, after.windowedLayout.bibleColWidthCrossRef)
        assertEquals(before.maximizedLayout.bibleColWidthCrossRef, after.maximizedLayout.bibleColWidthCrossRef)
    }

    @Test
    fun `saving the cross-reference width while maximized updates only the maximized layout`() {
        val before = AppSettings()
        val after = withBibleCrossRefPanelWidth(before, isMaximized = true, widthDp = 300)

        assertEquals(300, after.maximizedLayout.bibleColWidthCrossRef)
        assertEquals(before.windowedLayout.bibleColWidthCrossRef, after.windowedLayout.bibleColWidthCrossRef)
    }

    @Test
    fun `saving the cross-reference width never touches the other widths`() {
        val before = AppSettings()
        val after = withBibleCrossRefPanelWidth(before, isMaximized = false, widthDp = 300)

        assertEquals(before.windowedLayout.bibleColWidthBook, after.windowedLayout.bibleColWidthBook)
        assertEquals(before.windowedLayout.bibleColWidthChapter, after.windowedLayout.bibleColWidthChapter)
        assertEquals(before.windowedLayout.splitLivePanelWidth, after.windowedLayout.splitLivePanelWidth)
    }

    @Test
    fun `docking the cross-reference panel is remembered, and undocking it too`() {
        val docked = withBibleCrossReferencePanel(AppSettings(), docked = true)
        assertEquals(true, docked.bibleSettings.crossReferencesPanel)
        assertEquals(false, withBibleCrossReferencePanel(docked, docked = false).bibleSettings.crossReferencesPanel)
    }

    @Test
    fun `docking the cross-reference panel touches no layout width`() {
        val before = AppSettings()
        val after = withBibleCrossReferencePanel(before, docked = true)

        assertEquals(before.windowedLayout, after.windowedLayout)
        assertEquals(before.maximizedLayout, after.maximizedLayout)
        assertEquals(before.bibleSettings.splitBrowseMode, after.bibleSettings.splitBrowseMode)
    }

    @Test
    fun `the other widths never touch the cross-reference width`() {
        val before = AppSettings()

        assertEquals(
            before.windowedLayout.bibleColWidthCrossRef,
            withBibleColumnWidths(before, isMaximized = false, bookWidthDp = 220, chapterWidthDp = 140)
                .windowedLayout.bibleColWidthCrossRef,
        )
        assertEquals(
            before.windowedLayout.bibleColWidthCrossRef,
            withBibleSplitPanelWidth(before, isMaximized = false, widthDp = 360)
                .windowedLayout.bibleColWidthCrossRef,
        )
    }
}
